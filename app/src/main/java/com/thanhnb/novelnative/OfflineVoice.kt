package com.thanhnb.novelnative

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.k2fsa.sherpa.onnx.GeneratedAudio
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream

/**
 * Giọng Việt offline (Piper chạy bằng sherpa-onnx ngay trên máy) — không cần mạng, không cần giọng Google.
 * Mỗi gói tải một lần từ nguồn gốc (file .onnx Piper + .json); máy tự chuyển sang dạng sherpa đọc được:
 * sinh tokens.txt từ json và nối metadata vào cuối file onnx. Bộ phát âm espeak-ng-data dùng chung mọi gói.
 * sherpa-onnx ghim 1.13.8: bản 2.0 bỏ espeak-ng nên Piper không tự phát âm được nữa.
 */
object OfflineVoice {
    class Pack(val id: String, val title: String, val sizeMb: Int, val onnx: String, val voices: List<Pair<Int, String>>)

    private const val HF = "https://huggingface.co"
    val packs = listOf(
        Pack("vais1000", "Giọng Gác Truyện", 64, "$HF/rhasspy/piper-voices/resolve/main/vi/vi_VN/vais1000/medium/vi_VN-vais1000-medium.onnx",
            listOf(0 to "Gác Truyện (nữ)")),
        Pack("csa", "Bộ 5 giọng CSA", 74, "$HF/CakeByVPBank/piper-pgl-v4-vi_VN-version39_epoch39/resolve/main/vi_VN-csa-voice-piper-v3-medium.onnx",
            listOf(0 to "Ngọc Lan (nữ)", 1 to "Minh Anh (nữ)", 2 to "Quang Huy (nam)", 3 to "Thu Hà (nữ)", 4 to "Yến Nhi (nữ)")),
    )
    private const val ESPEAK = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/espeak-ng-data.zip"

    /** Mã giọng: "offline:<gói>" (người đọc 0) hoặc "offline:<gói>:<sid>". Giữ "offline:vais1000" như bản trước. */
    fun idOf(pack: Pack, sid: Int) = if (sid == 0) "offline:${pack.id}" else "offline:${pack.id}:$sid"
    fun isOffline(id: String?) = id?.startsWith("offline:") == true
    private fun parse(id: String): Pair<Pack, Int>? {
        val p = id.removePrefix("offline:").split(":")
        val pack = packs.firstOrNull { it.id == p[0] } ?: return null
        return pack to (p.getOrNull(1)?.toIntOrNull() ?: 0)
    }
    fun ready(id: String?) = id != null && parse(id)?.first?.id in installed
    /** Giọng offline đầu tiên đã tải (dùng khi máy không có giọng Việt nào). */
    fun firstReady() = packs.firstOrNull { it.id in installed }?.let { idOf(it, 0) }

    var installed by mutableStateOf(emptySet<String>()); private set
    /** Gói đang tải (null = không) và tiến độ 0..1. */
    var downloading by mutableStateOf<String?>(null); private set
    var progress by mutableFloatStateOf(0f); private set
    var error by mutableStateOf<String?>(null); private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val gen = Dispatchers.Default.limitedParallelism(1) // engine không an toàn đa luồng
    private var engine: Pair<String, OfflineTts>? = null
    /** Khối đã/đang sinh, chờ đến lượt phát (khối hiện tại + khối kế). */
    private val pending = LinkedHashMap<String, Deferred<GeneratedAudio>>()

    private fun root(c: Context) = File(c.filesDir, "tts")
    private fun espeak(c: Context) = File(root(c), "espeak-ng-data")
    private fun dir(c: Context, p: Pack) = File(root(c), p.id)
    private fun ok(c: Context, p: Pack) = File(dir(c, p), ".ok") // chỉ ghi khi xong trọn → không dùng nhầm gói dở

    fun check(c: Context) {
        File(root(c), "vits-piper-vi_VN-vais1000-medium-int8").deleteRecursively() // gói int8 bản trước: kém hay + chậm hơn
        installed = packs.filter { ok(c, it).exists() }.map { it.id }.toSet()
    }

    fun download(c: Context, p: Pack, onDone: () -> Unit = {}) {
        if (downloading != null) return
        val app = c.applicationContext
        downloading = p.id
        progress = 0f
        error = null
        scope.launch {
            val tmp = File(root(app), "tmp-${p.id}").apply { deleteRecursively(); mkdirs() }
            try {
                if (!File(espeak(app), "phontab").exists()) {
                    ZipInputStream(open(ESPEAK).buffered()).use { zip ->
                        while (true) {
                            val e = zip.nextEntry ?: break
                            val out = File(tmp, e.name)
                            if (!out.canonicalPath.startsWith(tmp.canonicalPath + File.separator)) error("Gói phát âm hỏng") // chặn ../
                            if (e.isDirectory) out.mkdirs() else { out.parentFile?.mkdirs(); out.outputStream().use { zip.copyTo(it) } }
                        }
                    }
                    espeak(app).deleteRecursively()
                    if (!File(tmp, "espeak-ng-data").renameTo(espeak(app))) error("Không ghi được bộ phát âm")
                }
                val cfg = JSONObject(open("${p.onnx}.json").use { it.readBytes().decodeToString() })
                File(tmp, "tokens.txt").writeText(tokens(cfg))
                val model = File(tmp, "model.onnx")
                val total = p.sizeMb * 1_048_576L
                var got = 0L
                open(p.onnx).use { input ->
                    model.outputStream().use { out ->
                        val buf = ByteArray(64 * 1024)
                        while (true) {
                            val n = input.read(buf); if (n < 0) break
                            out.write(buf, 0, n); got += n
                            progress = (got.toFloat() / total).coerceAtMost(0.99f)
                        }
                    }
                }
                model.appendBytes(piperMeta(cfg))
                val d = dir(app, p)
                d.deleteRecursively()
                if (!tmp.renameTo(d)) error("Không ghi được gói giọng")
                ok(app, p).writeText("1")
                installed = installed + p.id
                withContext(Dispatchers.Main) { onDone() }
            } catch (e: Exception) {
                error = "Tải lỗi: ${e.message ?: e.javaClass.simpleName}"
            } finally {
                tmp.deleteRecursively()
                downloading = null
            }
        }
    }

    /** Xoá gói khỏi máy; hết gói thì xoá luôn bộ phát âm dùng chung. Gọi khi gói không đang đọc. */
    suspend fun delete(c: Context, p: Pack) = withContext(gen) {
        if (engine?.first == p.id) { engine?.second?.release(); engine = null }
        withContext(Dispatchers.Main) { pending.clear(); installed = installed - p.id }
        dir(c, p).deleteRecursively()
        if (packs.none { ok(c, it).exists() }) espeak(c).deleteRecursively()
    }

    private fun open(url: String) = (URL(url).openConnection() as HttpURLConnection).run {
        connectTimeout = 15_000; readTimeout = 30_000
        if (responseCode != 200) error("HTTP $responseCode")
        inputStream
    }

    private fun engineFor(c: Context, p: Pack): OfflineTts {
        engine?.let { (id, t) -> if (id == p.id) return t; t.release() } // giữ một gói trong RAM (mỗi gói ~70 MB)
        val d = dir(c, p)
        return OfflineTts(config = OfflineTtsConfig(model = OfflineTtsModelConfig(
            vits = OfflineTtsVitsModelConfig(model = File(d, "model.onnx").path, tokens = File(d, "tokens.txt").path, dataDir = espeak(c).path),
            numThreads = 2,
        ))).also { engine = p.id to it }
    }

    private fun synth(c: Context, id: String, text: String, speed: Float): Deferred<GeneratedAudio> = pending.getOrPut("$id|$speed|$text") {
        while (pending.size >= 3) pending.remove(pending.keys.first()) // bỏ khối cũ khi nhảy đoạn/đổi giọng/tốc độ
        val (p, sid) = parse(id) ?: error("Giọng lạ: $id")
        scope.async(gen) { engineFor(c, p).generate(text, sid, speed) }
    }

    /** Sinh trước câu sắp đọc trong lúc câu này đang phát → không hở giữa các đoạn. */
    fun prefetch(c: Context, id: String, text: String, speed: Float) { runCatching { synth(c, id, text, speed) } }

    /** Đọc [text] tới hết; huỷ coroutine = dừng tiếng ngay. false = engine lỗi. */
    suspend fun say(c: Context, id: String, text: String, speed: Float): Boolean {
        val key = "$id|$speed|$text"
        val audio = try { synth(c, id, text, speed).await() } catch (e: CancellationException) { throw e } catch (e: Exception) { pending.clear(); return false }
        pending.remove(key)
        if (audio.samples.isEmpty()) return true // chuỗi chỉ có dấu câu
        return play(audio)
    }

    private suspend fun play(a: GeneratedAudio): Boolean = withContext(Dispatchers.IO) {
        val fmt = AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_FLOAT).setSampleRate(a.sampleRate).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build()
        val buf = AudioTrack.getMinBufferSize(a.sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_FLOAT) * 2
        val t = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            .setAudioFormat(fmt).setTransferMode(AudioTrack.MODE_STREAM)
            .setBufferSizeInBytes(buf)
            .build()
        try {
            t.play()
            // Đệm im lặng một bộ đệm ở cuối: khối quá ngắn chưa lấp đầy bộ đệm thì loa không chạy tới cuối
            val pcm = a.samples + FloatArray(buf / 4)
            var off = 0
            while (off < pcm.size) {
                ensureActive()
                val n = t.write(pcm, off, minOf(4096, pcm.size - off), AudioTrack.WRITE_BLOCKING)
                if (n < 0) return@withContext false
                off += n
            }
            // Đợi loa phát nốt phần đệm; giới hạn thời gian để track kẹt không treo máy đọc
            val end = System.currentTimeMillis() + a.samples.size * 1000L / a.sampleRate + 2_000
            while (t.playbackHeadPosition < a.samples.size && System.currentTimeMillis() < end) delay(30)
            true
        } finally {
            runCatching { t.pause(); t.flush() }
            t.release()
        }
    }
}

/** tokens.txt của sherpa: mỗi dòng "<âm vị> <id>" lấy từ phoneme_id_map của Piper. */
internal fun tokens(cfg: JSONObject): String = buildString {
    val m = cfg.getJSONObject("phoneme_id_map")
    for (k in m.keys()) append(k).append(' ').append(m.getJSONArray(k).getInt(0)).append('\n')
}

/**
 * Metadata sherpa cần cho Piper, mã hoá thành trường 14 (metadata_props) của ModelProto.
 * Protobuf gộp trường lặp nối ở cuối → chỉ việc append vào file onnx gốc, không cần thư viện onnx.
 */
internal fun piperMeta(cfg: JSONObject): ByteArray {
    fun varint(o: ByteArrayOutputStream, n0: Int) { var n = n0; while (n >= 0x80) { o.write(n and 0x7F or 0x80); n = n ushr 7 }; o.write(n) }
    fun field(tag: Int, data: ByteArray) = ByteArrayOutputStream().apply { varint(this, tag); varint(this, data.size); write(data) }.toByteArray()
    val meta = linkedMapOf(
        "model_type" to "vits", "comment" to "piper", "language" to "Vietnamese",
        "voice" to cfg.getJSONObject("espeak").getString("voice"), "has_espeak" to "1",
        "n_speakers" to cfg.getInt("num_speakers").toString(),
        "sample_rate" to cfg.getJSONObject("audio").getInt("sample_rate").toString(),
    )
    return meta.entries.fold(ByteArray(0)) { acc, (k, v) -> acc + field(0x72, field(0x0A, k.toByteArray()) + field(0x12, v.toByteArray())) }
}
