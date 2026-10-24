package com.thanhnb.novelnative

import android.content.Context
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Font Sans tải từ Google Fonts khi người dùng chọn lần đầu, lưu ở filesDir/webfonts nên các lần sau (và lúc mất mạng)
 * dùng bản trên máy. Chưa tải được thì giao diện giữ font mặc định (Jakarta). Chỉ chọn font có đủ bảng chữ Việt.
 */
object WebFonts {
    /** (khoá lưu trong prefs rd_font, tên hiển thị, tên họ font trên Google Fonts) */
    val sans = listOf(
        Triple("nunito", "Nunito", "Nunito"),
        Triple("raleway", "Raleway", "Raleway"),
        Triple("sourcesans", "Source Sans 3", "Source+Sans+3"),
        Triple("plexsans", "IBM Plex Sans", "IBM+Plex+Sans"),
        Triple("firasans", "Fira Sans", "Fira+Sans"),
        Triple("barlow", "Barlow", "Barlow"),
        Triple("publicsans", "Public Sans", "Public+Sans"),
        Triple("archivo", "Archivo", "Archivo"),
        Triple("hanken", "Hanken Grotesk", "Hanken+Grotesk"),
        Triple("encode", "Encode Sans", "Encode+Sans"),
        Triple("readex", "Readex Pro", "Readex+Pro"),
        Triple("reddit", "Reddit Sans", "Reddit+Sans"),
    )

    /** Font đã có file trên máy; đọc trong composable nên tải xong là chữ tự đổi. */
    val ready = mutableStateMapOf<String, FontFamily>()
    private val weights = listOf(400, 700)
    private val busy = HashSet<String>()

    /** Nạp từ cache, thiếu thì tải. Lỗi mạng chỉ bỏ qua (lần chọn sau thử lại). */
    suspend fun ensure(ctx: Context, key: String) {
        val google = sans.firstOrNull { it.first == key }?.third ?: return
        if (key in ready || !synchronized(busy) { busy.add(key) }) return
        try {
            withContext(Dispatchers.IO) {
                val dir = File(ctx.filesDir, "webfonts").apply { mkdirs() }
                val files = weights.map { File(dir, "${key}_$it.ttf") }
                if (files.any { !it.exists() }) download(google, weights, files)
                ready[key] = FontFamily(files.mapIndexed { i, f ->
                    val w = FontWeight(weights[i])
                    Font(f, w, FontStyle.Normal, FontVariation.Settings(w, FontStyle.Normal)) // phông biến thiên cần chỉ rõ độ đậm
                })
            }
        } catch (_: Exception) {
        } finally {
            synchronized(busy) { busy.remove(key) }
        }
    }

    private fun get(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 10_000
        c.readTimeout = 20_000
        return c.inputStream.bufferedReader().use { it.readText() }
    }

    /** CSS không kèm User-Agent trình duyệt → Google trả file .ttf nguyên bộ chữ (không chia subset). */
    private fun download(family: String, weights: List<Int>, out: List<File>) {
        val css = get("https://fonts.googleapis.com/css2?family=$family:wght@${weights.joinToString(";")}")
        val urls = Regex("""font-weight:\s*(\d+);[^}]*?url\((https://[^)]+)\)""").findAll(css).associate { it.groupValues[1].toInt() to it.groupValues[2] }
        weights.forEachIndexed { i, w ->
            val url = urls[w] ?: error("thiếu độ đậm $w")
            val tmp = File(out[i].path + ".part")
            URL(url).openStream().use { inp -> tmp.outputStream().use { inp.copyTo(it) } }
            tmp.renameTo(out[i])
        }
    }
}
