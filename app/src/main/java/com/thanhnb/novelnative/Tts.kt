package com.thanhnb.novelnative

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.os.IBinder
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import kotlinx.coroutines.*
import java.util.Locale

/**
 * Nghe truyện (như tts.dart): TTS hệ thống đọc tuần tự từng đoạn, hết chương tự sang chương sau (nếu đã dịch).
 * Singleton sống NGOÀI màn đọc; [TtsService] giữ tiến trình để tắt màn hình vẫn đọc tiếp.
 * Tạm dừng = dừng utterance + nhớ đoạn đang đọc (Android không pause được giữa câu) → tiếp đọc lại đoạn đó.
 */
object TtsPlayer {
    class VoiceInfo(val name: String, val quality: Int, val network: Boolean) {
        val label get() = when {
            quality >= 500 -> "Rất cao"; quality >= 400 -> "Cao"; quality >= 300 -> "Tiêu chuẩn"; else -> "Cơ bản"
        } + if (network) " · cần mạng" else " · offline"
    }

    var novelId by mutableStateOf<Int?>(null); private set
    var chapter by mutableIntStateOf(0); private set
    var playing by mutableStateOf(false); private set
    var paused by mutableStateOf(false); private set
    /** Đoạn NỘI DUNG đang đọc (khớp chỉ số đoạn màn đọc; -1 = đang đọc tiêu đề / chưa đọc). */
    var paraAt by mutableIntStateOf(-1); private set
    val active get() = novelId != null
    var rate by mutableFloatStateOf(1f) // 1 = tốc độ chuẩn của máy
        private set
    var voice by mutableStateOf<String?>(null); private set
    /** Hẹn giờ tắt: 0 = tắt · -1 = hết chương này · n = n phút. [sleepLeft] = số ms còn lại (chỉ khi hẹn theo phút). */
    var sleepSel by mutableIntStateOf(0); private set
    var sleepLeft by mutableLongStateOf(0L); private set
    private var sleepJob: Job? = null

    private var ctx: Context? = null
    private var tts: TextToSpeech? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var job: Job? = null
    private var paras = emptyList<String>() // gồm tiêu đề ở đầu (nếu có) rồi các đoạn nội dung
    private var lead = 0 // 1 nếu paras[0] là tiêu đề
    private var at = 0 // đoạn đang/sắp đọc (chỉ số trong paras)
    private var waiting: CompletableDeferred<Boolean>? = null
    private var curId = ""
    private var seq = 0

    fun changeRate(r: Float) {
        rate = r
        Prefs.sp.edit().putFloat("tts_rate", r).apply()
    }

    fun sleep(sel: Int) {
        sleepJob?.cancel()
        sleepSel = sel
        sleepLeft = 0
        if (sel <= 0) return
        val until = System.currentTimeMillis() + sel * 60_000L
        sleepJob = scope.launch {
            while (true) {
                sleepLeft = until - System.currentTimeMillis()
                if (sleepLeft <= 0) { stop(); return@launch }
                delay(5_000)
            }
        }
    }

    /** Giọng tiếng Việt của máy, tốt nhất lên đầu. Rỗng nếu máy chưa có/chưa khởi tạo xong. */
    fun voices(): List<VoiceInfo> = tts?.voices.orEmpty().filter { it.locale.language == "vi" }
        .sortedWith(compareByDescending<android.speech.tts.Voice> { it.quality }.thenBy { it.isNetworkConnectionRequired }.thenBy { it.name })
        .map { VoiceInfo(it.name, it.quality, it.isNetworkConnectionRequired) }

    fun pick(name: String) {
        if (name != OfflineVoice.ID) tts?.voices?.firstOrNull { it.name == name }?.let { tts?.voice = it }
        voice = name
        Prefs.sp.edit().putString("tts_voice", name).apply()
    }

    /** Nghe thử một giọng (đang đọc truyện thì tạm dừng trước). */
    fun preview(name: String) {
        if (playing) pause()
        pick(name)
        if (name == OfflineVoice.ID) {
            val c = ctx ?: return
            job?.cancel()
            job = scope.launch { OfflineVoice.say(c, PREVIEW, rate) }
            return
        }
        tts?.setSpeechRate(rate)
        tts?.speak(PREVIEW, TextToSpeech.QUEUE_FLUSH, null, "preview")
    }

    /** Khởi tạo engine (lần đầu). Trả cảnh báo nếu máy thiếu giọng Việt — vẫn đọc bằng giọng mặc định. */
    private suspend fun ensure(c: Context): String? {
        ctx = c.applicationContext
        rate = Prefs.sp.getFloat("tts_rate", 1f)
        OfflineVoice.check(c)
        val saved = Prefs.sp.getString("tts_voice", null)
        if (OfflineVoice.ready && saved == OfflineVoice.ID) { voice = OfflineVoice.ID; return null }
        if (tts == null) {
            val ok = CompletableDeferred<Boolean>()
            val t = TextToSpeech(ctx) { ok.complete(it == TextToSpeech.SUCCESS) }
            if (!ok.await()) {
                t.shutdown()
                if (OfflineVoice.ready) { voice = OfflineVoice.ID; return null }
                return "Máy không có công cụ đọc văn bản — hãy tải Giọng Gác Truyện (offline) ở nút Giọng đọc"
            }
            t.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            t.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(id: String?) {}
                override fun onDone(id: String?) { if (id == curId) waiting?.complete(true) }
                override fun onStop(id: String?, interrupted: Boolean) { if (id == curId) waiting?.complete(true) }
                @Deprecated("Deprecated in Java")
                override fun onError(id: String?) { if (id == curId) waiting?.complete(false) }
            })
            tts = t
        }
        val t = tts!!
        val v = t.voices?.firstOrNull { it.name == saved && it.locale.language == "vi" }
        if (v != null) { t.voice = v; voice = v.name; return null }
        if (t.setLanguage(Locale("vi", "VN")) < TextToSpeech.LANG_AVAILABLE) {
            if (OfflineVoice.ready) { voice = OfflineVoice.ID; return null }
            return "Máy chưa có giọng tiếng Việt — hãy tải Giọng Gác Truyện (offline) ở nút Giọng đọc"
        }
        voice = t.voice?.name
        return null
    }

    /** Bắt đầu đọc từ đoạn nội dung [from]; [seed]/[title] là đoạn màn đọc đang hiện để highlight khớp tuyệt đối. */
    suspend fun start(c: Context, novelId: Int, chapter: Int, from: Int, seed: List<String>, title: String?): String? {
        val warn = ensure(c)
        stop()
        this.novelId = novelId
        this.chapter = chapter
        playing = true
        launchLoop(novelId, chapter, from, seed, title, null)
        sync()
        return warn
    }

    fun pause() {
        if (!active) return
        job?.cancel()
        tts?.stop()
        playing = false
        paused = true
        sync()
    }

    fun resume() {
        val n = novelId ?: return
        if (!paused) return
        paused = false
        playing = true
        launchLoop(n, chapter, 0, null, null, at)
        sync()
    }

    fun stop() {
        sleep(0)
        job?.cancel()
        tts?.stop()
        novelId = null
        playing = false
        paused = false
        paraAt = -1
        ctx?.let { it.stopService(Intent(it, TtsService::class.java)) }
    }

    private fun launchLoop(nid: Int, ch: Int, from: Int, seed: List<String>?, title: String?, resumeAt: Int?) {
        job?.cancel()
        job = scope.launch { loop(nid, ch, from, seed, title, resumeAt) }
    }

    private suspend fun loop(nid: Int, ch: Int, from: Int, seed0: List<String>?, title0: String?, resumeAt: Int?) {
        var index = ch
        var contentFrom = from
        var seed = seed0
        var seedTitle = title0
        var startAt = resumeAt
        while (true) {
            if (startAt == null) {
                val content: List<String>
                val title: String?
                if (seed != null) {
                    content = seed; title = seedTitle; seed = null; seedTitle = null
                } else {
                    val c = runCatching { Api.chapter(nid, index) }.getOrNull()
                    if (c == null || c.status != "done") { stop(); return } // chương kế chưa dịch → dừng gọn
                    content = c.paragraphs; title = c.title
                }
                lead = if (!title.isNullOrBlank()) 1 else 0
                paras = (if (lead == 1) listOf(title!!) else emptyList()) + content
                startAt = contentFrom.coerceIn(0, content.size) + lead
                contentFrom = 0 // chỉ chương đầu tôn trọng vị trí bắt đầu
            }
            chapter = index
            sync()
            for (i in startAt until paras.size) {
                at = i
                paraAt = i - lead
                val offline = voice == OfflineVoice.ID
                val max = if (offline) 300 else 3000 // offline sinh xong cả khối mới phát → khối ngắn cho đỡ chờ
                val cs = chunks(paras[i], max)
                for ((k, chunk) in cs.withIndex()) {
                    if (offline) (cs.getOrNull(k + 1) ?: paras.getOrNull(i + 1)?.let { chunks(it, max).first() })
                        ?.let { OfflineVoice.prefetch(ctx!!, it, rate) }
                    if (!say(chunk)) { stop(); return } // engine lỗi → tắt gọn thay vì kẹt im
                }
            }
            paraAt = -1
            if (sleepSel == -1) { stop(); return } // hẹn giờ "hết chương"
            startAt = null
            index++ // hết chương → đọc tiếp chương sau
        }
    }

    private suspend fun say(text: String): Boolean {
        if (voice == OfflineVoice.ID) return OfflineVoice.say(ctx ?: return false, text, rate)
        val t = tts ?: return false
        t.setSpeechRate(rate)
        val d = CompletableDeferred<Boolean>()
        waiting = d
        curId = "u${++seq}"
        if (t.speak(text, TextToSpeech.QUEUE_FLUSH, null, curId) != TextToSpeech.SUCCESS) return false
        return try { d.await() } catch (e: CancellationException) { t.stop(); throw e }
    }

    /** Engine từ chối chuỗi quá dài (~4000 ký tự) → cắt ở dấu câu. */
    private fun chunks(s: String, max: Int = 3000): List<String> {
        if (s.length <= max) return listOf(s)
        val out = mutableListOf<String>()
        var rest = s
        while (rest.length > max) {
            val cut = rest.take(max).lastIndexOfAny(charArrayOf('.', '!', '?', '…', '”', ';')).let { if (it < max / 2) max - 1 else it }
            out += rest.take(cut + 1)
            rest = rest.drop(cut + 1)
        }
        return out + rest
    }

    private const val PREVIEW = "Bạn đang nghe thử giọng đọc tiếng Việt của Gác Truyện."

    private fun sync() {
        val c = ctx ?: return
        runCatching { c.startForegroundService(Intent(c, TtsService::class.java)) } // gọi lại = cập nhật thông báo
    }
}

/** Foreground service: giữ tiến trình sống khi tắt màn hình / rời app, có thông báo Tạm dừng · Dừng. */
class TtsService : Service() {
    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(i: Intent?, flags: Int, startId: Int): Int {
        when (i?.action) {
            "pause" -> TtsPlayer.pause()
            "resume" -> TtsPlayer.resume()
            "stop" -> { TtsPlayer.stop(); stopSelf(); return START_NOT_STICKY }
        }
        if (!TtsPlayer.active) { stopSelf(); return START_NOT_STICKY }
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("tts", "Nghe truyện", NotificationManager.IMPORTANCE_LOW))
        fun act(a: String) = PendingIntent.getService(this, a.hashCode(), Intent(this, TtsService::class.java).setAction(a), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_IMMUTABLE)
        val n: Notification = NotificationCompat.Builder(this, "tts")
            .setSmallIcon(android.R.drawable.ic_lock_silent_mode_off)
            .setContentTitle("Gác Truyện · đang nghe truyện")
            .setContentText("Chương ${TtsPlayer.chapter}" + if (TtsPlayer.paused) " · tạm dừng" else "")
            .setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true).setSilent(true)
            .addAction(0, if (TtsPlayer.paused) "Tiếp tục" else "Tạm dừng", act(if (TtsPlayer.paused) "resume" else "pause"))
            .addAction(0, "Dừng", act("stop"))
            .build()
        ServiceCompat.startForeground(this, 71, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        return START_NOT_STICKY
    }
}

/** Thanh điều khiển đáy màn đọc — chỉ dựng khi máy đọc đang chạy cho truyện này. */
@Composable
fun TtsBar(fg: Color, bg: Color, openSheet: () -> Unit) {
    val dim = fg.copy(alpha = 0.6f)
    Row(Modifier.fillMaxWidth().background(bg).navigationBarsPadding().padding(horizontal = 8.dp).height(52.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton({ if (TtsPlayer.playing) TtsPlayer.pause() else TtsPlayer.resume() }) {
            Icon(if (TtsPlayer.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (TtsPlayer.playing) "Tạm dừng" else "Tiếp tục", tint = fg)
        }
        Text(
            when {
                TtsPlayer.sleepSel == -1 -> "Chương ${TtsPlayer.chapter} · tắt hết chương"
                TtsPlayer.sleepSel > 0 -> "Chương ${TtsPlayer.chapter} · tắt sau ${(TtsPlayer.sleepLeft + 59_999) / 60_000}′"
                else -> (if (TtsPlayer.playing) "Đang đọc" else "Tạm dừng") + " · chương ${TtsPlayer.chapter}"
            },
            Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, color = dim, maxLines = 1,
        )
        Text("%.1f×".format(TtsPlayer.rate), Modifier.clickable(onClick = openSheet).padding(8.dp), style = monoStyle(13, dim))
        IconButton(openSheet) { Icon(Icons.Rounded.Tune, "Giọng đọc và tốc độ", tint = dim) }
        IconButton(TtsPlayer::stop) { Icon(Icons.Rounded.Stop, "Dừng nghe", tint = dim) }
    }
}

/** Chọn giọng + tốc độ. Giọng chỉ hiện khi máy đã có (tải thêm ở cài đặt TTS của máy). */
@Composable
fun TtsSheet() {
    val cs = MaterialTheme.colorScheme
    val voices = remember { TtsPlayer.voices() }
    val ctx = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(Unit) { OfflineVoice.check(ctx) }
    Column(Modifier.verticalScroll(rememberScrollState()).padding(20.dp, 0.dp, 20.dp, 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Giọng đọc", style = MaterialTheme.typography.titleMedium)
        SliderRow("Tốc độ", "%.1f×".format(TtsPlayer.rate), TtsPlayer.rate, 0.5f..2f) { TtsPlayer.changeRate((it * 10).toInt() / 10f) }
        Text("Hẹn giờ tắt", Modifier.padding(top = 4.dp), style = MaterialTheme.typography.labelMedium)
        @OptIn(ExperimentalLayoutApi::class)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(0 to "Không", 15 to "15 phút", 30 to "30 phút", 45 to "45 phút", 60 to "60 phút", -1 to "Hết chương").forEach { (v, l) ->
                Opt(l, TtsPlayer.sleepSel == v) { TtsPlayer.sleep(v) }
            }
        }
        Row(Modifier.fillMaxWidth().clickable(enabled = OfflineVoice.ready) { TtsPlayer.pick(OfflineVoice.ID) }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            RadioButton(TtsPlayer.voice == OfflineVoice.ID, { TtsPlayer.pick(OfflineVoice.ID) }, enabled = OfflineVoice.ready)
            Column(Modifier.weight(1f)) {
                Text("Giọng Gác Truyện", style = MaterialTheme.typography.titleMedium, maxLines = 1)
                Text(
                    when {
                        OfflineVoice.ready -> "Offline · đọc được ở mọi nơi"
                        OfflineVoice.progress >= 0 -> "Đang tải… ${(OfflineVoice.progress * 100).toInt()}%"
                        else -> OfflineVoice.error ?: "Tải một lần ~20 MB, sau đó không cần mạng"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = if (OfflineVoice.error != null && !OfflineVoice.ready) cs.error else Color.Unspecified,
                )
            }
            when {
                OfflineVoice.ready -> IconButton({ TtsPlayer.preview(OfflineVoice.ID) }) { Icon(Icons.Rounded.PlayCircleOutline, "Nghe thử", tint = cs.primary) }
                OfflineVoice.progress >= 0 -> CircularProgressIndicator({ OfflineVoice.progress }, Modifier.size(24.dp))
                else -> IconButton({ OfflineVoice.download(ctx) { TtsPlayer.pick(OfflineVoice.ID) } }) { Icon(Icons.Rounded.Download, "Tải giọng offline", tint = cs.primary) }
            }
        }
        if (voices.isEmpty()) Text(
            "Máy chưa có giọng tiếng Việt riêng — dùng Giọng Gác Truyện ở trên.",
            style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant,
        ) else voices.forEach { v ->
            Row(Modifier.fillMaxWidth().clickable { TtsPlayer.pick(v.name) }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                RadioButton(TtsPlayer.voice == v.name, { TtsPlayer.pick(v.name) })
                Column(Modifier.weight(1f)) {
                    Text(v.name, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                    Text(v.label, style = MaterialTheme.typography.labelMedium)
                }
                IconButton({ TtsPlayer.preview(v.name) }) { Icon(Icons.Rounded.PlayCircleOutline, "Nghe thử", tint = cs.primary) }
            }
        }
    }
}
