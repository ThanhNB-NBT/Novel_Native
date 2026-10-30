package com.thanhnb.novelnative

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.CheckCircleOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*

/** Đang đọc: truyện có người đọc trong 8 giờ qua (bám ưu tiên dịch). */
@Composable
fun ReadingTab() {
    val l = load("admin.reading") { AdminApi.readingNow() }
    AdminPane(l) { rows ->
        item { SectionLabel("8 giờ qua", "${rows.size} lượt · ${rows.map { it.getInt("novel_id") }.distinct().size} truyện") }
        if (rows.isEmpty()) item { Empty("Không có ai đang đọc (8 giờ qua).") }
        items(rows) { r ->
            val id = r.getInt("novel_id")
            val n = r.optJSONObject("novels")
            val title = n.novelTitle(id)
            val total = n?.optInt("chapter_count_source") ?: 0
            val at = r.optInt("chapter_index")
            val fresh = (secondsSince(r.s("updated_at")) ?: Long.MAX_VALUE) < 600
            Row(Modifier.fillMaxWidth().bounceClick { Nav.push(Screen.AdminNovel(id)) }.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Cover(n?.s("cover_url"), 38.dp, 52.dp, label = title, flat = true)
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("Chương $at${if (total > 0) "/$total" else ""} · ${ago(r.s("updated_at"))} trước", style = MaterialTheme.typography.labelMedium, maxLines = 1)
                    if (total > 0) LinearProgressIndicator(
                        { (at.toFloat() / total).coerceIn(0f, 1f) }, Modifier.fillMaxWidth().padding(top = 5.dp).height(4.dp),
                        trackColor = MaterialTheme.colorScheme.onSurface.copy(.08f), strokeCap = StrokeCap.Round, gapSize = 0.dp, drawStopIndicator = {},
                    )
                }
                if (fresh) StatusPill("Vừa đọc", MaterialTheme.colorScheme.live)
            }
            HorizontalDivider(Modifier.padding(start = 66.dp, end = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(.5f))
        }
    }
}

/** Báo cáo term dịch sai chưa xử lý; bấm dòng mở bảng thuật ngữ của truyện. */
@Composable
fun ReportsTab() {
    val l = load("admin.reports") { AdminApi.reports() }
    var act by remember { mutableStateOf<Act?>(null) }
    AdminPane(l) { rows ->
        if (rows.isNotEmpty()) item { SectionLabel("Term dịch sai chờ xử lý", "${rows.size}") }
        if (rows.isEmpty()) item { Empty("Không có báo cáo nào chờ xử lý.") }
        items(rows, key = { it.getInt("id") }) { r ->
            val t = r.optJSONObject("glossary_terms")
            val head = if (t == null) "(term đã bị xoá)" else "${t.s("term_zh") ?: "(?)"} → ${t.s("correct_vi") ?: ""}"
            val nid = r.optInt("novel_id")
            val sub = listOfNotNull(r.s("reason")?.let { "Lý do: $it" }, r.optJSONObject("novels")?.let { it.s("title_vi") ?: it.s("title_zh") }).joinToString("\n")
            AdminRow(
                null, head, sub, onClick = if (nid > 0) ({ Nav.push(Screen.Glossary(nid)) }) else null,
                trailing = {
                    IconButton({
                        act = Act("Đánh dấu đã xử lý?", "Báo cáo \"$head\" sẽ biến khỏi danh sách chờ.", "Đã xử lý") { AdminApi.resolveReport(r.getInt("id")); "Đã đánh dấu xử lý" }
                    }) { Icon(Icons.Rounded.CheckCircleOutline, "Đánh dấu đã xử lý", tint = MaterialTheme.colorScheme.primary) }
                },
            )
        }
    }
    ConfirmDialog(act, { act = null }) { l.reload() }
}
