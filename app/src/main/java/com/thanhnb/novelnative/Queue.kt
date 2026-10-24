package com.thanhnb.novelnative

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.HourglassBottom
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

private class Group(val novelId: Int, val jobs: List<QJob>) {
    val first get() = jobs.first()
    fun of(s: String) = jobs.filter { it.status == s }.map { it.index }.sorted()
    val rank get() = when { of("translating").isNotEmpty() -> 0; of("downloading").isNotEmpty() -> 1; of("queued").isNotEmpty() -> 2; else -> 3 }

    private fun range(idx: List<Int>, verb: String) = when {
        idx.isEmpty() -> null
        idx.size == 1 -> "$verb chương ${idx[0]}"
        else -> "$verb ${idx.size} chương (${idx.first()}–${idx.last()})"
    }

    val status get() = listOfNotNull(
        of("translating").let { if (it.size == 1) "Đang dịch chương ${it[0]}" else if (it.size > 1) "Đang dịch ${it.size} chương" else null },
        range(of("downloading"), "đang tải về"), range(of("queued"), "chờ dịch"), range(of("source_unavailable"), "nguồn không khả dụng"),
    ).joinToString(" · ").ifEmpty { "Trong hàng đợi" }
}

/** Hàng đợi dịch dùng chung mọi người dùng; tự làm mới mỗi 15 giây khi đang mở. */
@Composable
fun QueueScreen() {
    val l = load("queue") { Api.queue() }
    LaunchedEffect(Unit) { while (true) { delay(15_000); l.reload() } }
    var open by remember { mutableStateOf<Group?>(null) }
    var clearedTick by remember { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        PageHeader("Tiến độ", "Hàng đợi dịch", "譯", back = true)
        Loaded(l) { q ->
            val groups = remember(q) { q.active.groupBy { it.novelId }.values.map { Group(it.first().novelId, it) }.sortedBy { it.rank } }
            val recent = remember(q, clearedTick) { if (clearedTick > 0 && Prefs.sp.contains("queue_done_cleared_at")) emptyList() else q.recent }
            PullToRefreshBox(false, l.reload) {
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    item { Summary(q) }
                    if (groups.isEmpty() && recent.isEmpty()) item { Empty("Hàng đợi trống — chưa có chương nào đang xử lý.") }
                    if (groups.isNotEmpty()) {
                        item {
                            Row(Modifier.padding(start = 4.dp, top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.HourglassBottom, null, Modifier.size(18.dp), MaterialTheme.colorScheme.primary)
                                Text("Hàng đợi", Modifier.weight(1f).padding(start = 8.dp), style = MaterialTheme.typography.titleMedium)
                                if (q.pendingTotal > 200) Text("200 đầu / ${q.pendingTotal}", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        items(groups, key = { it.novelId }) { g -> NovelRow(g) { open = g } }
                    }
                    if (recent.isNotEmpty()) {
                        item {
                            Row(Modifier.padding(start = 4.dp, top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("Vừa dịch xong", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                                TextButton({ Api.clearQueueDone(); clearedTick++ }) { Text("Xoá") }
                            }
                        }
                        items(recent.groupBy { it.novelId }.values.toList(), key = { "d${it.first().novelId}" }) { cs ->
                            val d = cs.first()
                            Row(Modifier.fillMaxWidth().clickable { Nav.push(Screen.Detail(d.novelId)) }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Cover(d.cover, 40.dp, 54.dp, label = d.novelTitle)
                                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                                    Text(d.novelTitle, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    val idx = cs.map { it.index }.sorted()
                                    Text(if (idx.size == 1) "Xong chương ${idx[0]}" else "Xong ${idx.size} chương (${idx.first()}–${idx.last()})", style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    open?.let { g -> ChapterSheet(g) { open = null } }
}

@Composable
private fun Summary(q: Queue) {
    val cs = MaterialTheme.colorScheme
    val translating = q.active.count { it.status == "translating" }
    val live = translating > 0
    PaperCard {
        Column(Modifier.padding(16.dp)) {
            Text(if (live) "ĐANG DỊCH" else "ĐANG NGHỈ", style = MaterialTheme.typography.labelSmall, letterSpacing = 1.2.sp, color = if (live) cs.primary else cs.onSurfaceVariant)
            Row(Modifier.fillMaxWidth().padding(top = 10.dp)) {
                Cell("${q.recent.size}", "chương/giờ", cs.primary, Modifier.weight(1f))
                Cell("$translating", "đang dịch", cs.onSurface, Modifier.weight(1f))
                Cell("${q.pendingTotal}", "đang chờ", cs.onSurface, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun Cell(v: String, label: String, c: Color, m: Modifier) = Column(m, horizontalAlignment = Alignment.CenterHorizontally) {
    Text(v, style = monoStyle(24, c), fontWeight = FontWeight.Bold)
    Text(label, style = MaterialTheme.typography.labelMedium)
}

@Composable
private fun NovelRow(g: Group, onClick: () -> Unit) {
    val j = g.first
    PaperCard(onClick = onClick) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Cover(j.cover, 40.dp, 54.dp, label = j.novelTitle)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(j.novelTitle, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(g.status, style = MaterialTheme.typography.labelMedium, maxLines = 2, overflow = TextOverflow.Ellipsis, color = if (g.of("translating").isNotEmpty()) MaterialTheme.colorScheme.primary else Color.Unspecified)
            }
            Text("${j.done}/${j.total}", style = monoStyle(12))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChapterSheet(g: Group, close: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    fun rank(s: String) = when (s) { "translating" -> 0; "downloading" -> 1; "queued" -> 2; else -> 3 }
    val items = g.jobs.sortedWith(compareBy({ rank(it.status) }, { it.index }))
    ModalBottomSheet(close, containerColor = cs.card) {
        Text(g.first.novelTitle, Modifier.padding(horizontal = 20.dp, vertical = 4.dp), style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
        LazyColumn(Modifier.heightIn(max = 520.dp)) {
            items(items) { c ->
                val (label, color) = when (c.status) {
                    "translating" -> "Đang dịch" to cs.primary
                    "downloading" -> "Đang tải về" to cs.tertiary
                    "source_unavailable" -> "Nguồn không khả dụng" to cs.error
                    else -> "Chờ dịch" to cs.onSurfaceVariant
                }
                Row(Modifier.fillMaxWidth().padding(20.dp, 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (c.status == "translating") CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = color)
                    else Icon(when (c.status) { "downloading" -> Icons.Rounded.CloudDownload; "source_unavailable" -> Icons.Rounded.CloudOff; else -> Icons.Rounded.Schedule }, null, Modifier.size(18.dp), color)
                    Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                        Text("Chương ${c.index}", style = MaterialTheme.typography.bodyMedium)
                        c.title?.let { Text(it.trimStart('#', ' '), style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    }
                    Text(label, style = MaterialTheme.typography.labelMedium, color = color, fontWeight = FontWeight.SemiBold)
                }
                HorizontalDivider(color = cs.outlineVariant.copy(.6f))
            }
        }
    }
}
