package com.thanhnb.novelnative

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

private class Note(val tag: String, val date: String, val lines: List<String>)

private const val CACHE = "changelog_json"

/** Các release gần nhất (kể cả bản chưa có APK). Lỗi mạng thì ném. */
private suspend fun fetchNotes(): String = withContext(Dispatchers.IO) {
    val c = URL("https://api.github.com/repos/$REPO/releases?per_page=8").openConnection() as HttpURLConnection
    c.connectTimeout = 10_000
    c.readTimeout = 10_000
    c.setRequestProperty("Accept", "application/vnd.github+json")
    if (c.responseCode != 200) throw IOException("GitHub trả ${c.responseCode}")
    c.inputStream.bufferedReader().use { it.readText() }
}

/** Thân release (markdown) → các dòng chữ thường; bỏ dòng "Full Changelog" tự sinh và ký hiệu markdown. */
internal fun releaseLines(body: String): List<String> =
    body.lines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("**Full Changelog") && !it.startsWith("## What's Changed") }
        .map { l -> l.trimStart('#', ' ').let { if (l.startsWith("* ") || l.startsWith("- ")) "• " + l.drop(2) else it }.replace("**", "").replace("`", "") }

private fun parse(json: String): List<Note> {
    val a = JSONArray(json)
    return (0 until a.length()).map(a::getJSONObject).filter { !it.optBoolean("draft") }.map { r ->
        Note(r.optString("tag_name"), r.optString("published_at").take(10).split("-").reversed().joinToString("/"), releaseLines(r.optString("body")))
    }
}

/** Mục "Có gì mới": ghi chú của các release GitHub; mất mạng thì hiện bản lưu lần trước. */
@Composable
fun ChangelogScreen() {
    var notes by remember { mutableStateOf(runCatching { Prefs.sp.getString(CACHE, null)?.let(::parse) }.getOrNull()) }
    var failed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        runCatching { fetchNotes() }.onSuccess {
            notes = parse(it)
            Prefs.sp.edit().putString(CACHE, it).apply()
        }.onFailure { failed = true }
    }
    Column(Modifier.fillMaxSize()) {
        PageHeader("Các bản phát hành gần nhất", "Có gì mới", "新", back = true)
        val list = notes
        when {
            list == null && !failed -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
            list.isNullOrEmpty() -> Box(Modifier.fillMaxSize().padding(32.dp), Alignment.Center) {
                Text(if (failed) "Không tải được, thử lại khi có mạng" else "Chưa có bản phát hành nào", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(list) { NoteCard(it) }
            }
        }
    }
}

@Composable
private fun NoteCard(n: Note) {
    val cs = MaterialTheme.colorScheme
    PaperCard {
        Column(Modifier.padding(horizontal = 14.dp, vertical = if (n.lines.isEmpty()) 10.dp else 14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    n.tag, Modifier.clip(RoundedCornerShape(10.dp)).background(cs.primary.copy(.12f)).padding(horizontal = 10.dp, vertical = 3.dp),
                    style = monoStyle(13, cs.primary), fontWeight = FontWeight.Bold,
                )
                if (n.tag.removePrefix("v") == BuildConfig.VERSION_NAME) Text("  đang dùng", style = MaterialTheme.typography.labelSmall, color = cs.primary)
                Spacer(Modifier.weight(1f))
                Text(n.date, style = monoStyle(12))
            }
            n.lines.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
        }
    }
}
