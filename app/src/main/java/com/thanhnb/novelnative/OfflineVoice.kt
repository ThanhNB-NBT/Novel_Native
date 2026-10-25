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
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import java.io.File
import java.io.FilterInputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Giọng Việt offline: Piper "vais1000" chạy bằng sherpa-onnx ngay trên máy — không cần mạng, không cần giọng Google.
 * Tải ~20 MB một lần vào filesDir. sherpa-onnx ghim 1.13.8: bản 2.0 bỏ espeak-ng nên Piper không tự phát âm được nữa.
 */
object OfflineVoice {
    const val ID = "offline:vais1000"
    private const val NAME = "vits-piper-vi_VN-vais1000-medium-int8"
    private const val URL_ = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/$NAME.tar.bz2"

    /** -1 = không tải · 0..1 = đang tải. */
    var progress by mutableFloatStateOf(-1f); private set
    var error by mutableStateOf<String?>(null); private set
    var ready by mutableStateOf(false); private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val gen = Dispatchers.Default.limitedParallelism(1) // engine không an toàn đa luồng
    private var tts: OfflineTts? = null
    /** Khối đã/đang sinh, chờ đến lượt phát (khối hiện tại + khối kế). */
    private val pending = LinkedHashMap<String, Deferred<GeneratedAudio>>()

    private fun root(c: Context) = File(c.filesDir, "tts")
    private fun dir(c: Context) = File(root(c), NAME)
    private fun ok(c: Context) = File(dir(c), ".ok") // chỉ ghi khi giải nén trọn → không dùng nhầm gói dở

    fun check(c: Context) { ready = ok(c).exists() }

    fun download(c: Context, onDone: () -> Unit = {}) {
        if (progress >= 0) return
        val app = c.applicationContext
        progress = 0f
        error = null
        scope.launch {
            val tmp = File(root(app), "tmp").apply { deleteRecursively(); mkdirs() }
            try {
                val conn = URL(URL_).openConnection() as HttpURLConnection
                conn.connectTimeout = 15_000
                conn.readTimeout = 30_000
                val total = conn.contentLengthLong.coerceAtLeast(1)
                var got = 0L
                val counting = object : FilterInputStream(conn.inputStream) {
                    override fun read(b: ByteArray, off: Int, len: Int) = super.read(b, off, len).also {
                        if (it > 0) { got += it; progress = (got.toFloat() / total).coerceAtMost(0.99f) }
                    }
                }
                val base = tmp.canonicalPath + File.separator
                TarArchiveInputStream(BZip2CompressorInputStream(counting.buffered())).use { tar ->
                    while (true) {
                        val e = tar.nextEntry ?: break
                        val out = File(tmp, e.name)
                        if (!out.canonicalPath.startsWith(base)) error("Gói giọng hỏng (đường dẫn lạ)") // chặn ../ trong tar
                        if (e.isDirectory) out.mkdirs() else { out.parentFile?.mkdirs(); out.outputStream().use { tar.copyTo(it) } }
                    }
                }
                val unpacked = File(tmp, NAME)
                if (!File(unpacked, "tokens.txt").exists()) error("Gói giọng thiếu tệp")
                dir(app).deleteRecursively()
                if (!unpacked.renameTo(dir(app))) error("Không ghi được gói giọng")
                ok(app).writeText("1")
                ready = true
                withContext(Dispatchers.Main) { onDone() }
            } catch (e: Exception) {
                error = "Tải lỗi: ${e.message ?: e.javaClass.simpleName}"
            } finally {
                tmp.deleteRecursively()
                progress = -1f
            }
        }
    }

    private fun engine(c: Context): OfflineTts = tts ?: dir(c).let { d ->
        OfflineTts(config = OfflineTtsConfig(model = OfflineTtsModelConfig(
            vits = OfflineTtsVitsModelConfig(
                model = File(d, "vi_VN-vais1000-medium.onnx").path,
                tokens = File(d, "tokens.txt").path,
                dataDir = File(d, "espeak-ng-data").path,
            ),
            numThreads = 2,
        )))
    }.also { tts = it }

    private fun synth(c: Context, text: String, speed: Float): Deferred<GeneratedAudio> = pending.getOrPut("$speed|$text") {
        while (pending.size >= 3) pending.remove(pending.keys.first()) // bỏ khối cũ khi nhảy đoạn/đổi tốc độ
        scope.async(gen) { engine(c).generate(text, 0, speed) }
    }

    /** Sinh trước câu sắp đọc trong lúc câu này đang phát → không hở giữa các đoạn. */
    fun prefetch(c: Context, text: String, speed: Float) { synth(c, text, speed) }

    /** Đọc [text] tới hết; huỷ coroutine = dừng tiếng ngay. false = engine lỗi. */
    suspend fun say(c: Context, text: String, speed: Float): Boolean {
        val audio = try { synth(c, text, speed).await() } catch (e: CancellationException) { throw e } catch (e: Exception) { pending.clear(); return false }
        pending.remove("$speed|$text")
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
