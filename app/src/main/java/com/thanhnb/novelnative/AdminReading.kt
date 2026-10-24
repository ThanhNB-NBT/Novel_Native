package com.thanhnb.novelnative

import androidx.compose.foundation.lazy.items
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
        if (rows.isEmpty()) item { Empty("Không có ai đang đọc (8 giờ qua).") }
        items(rows) { r ->
            val id = r.getInt("novel_id")
            AdminRow(
                Icons.AutoMirrored.Rounded.MenuBook, r.optJSONObject("novels").novelTitle(id),
                "Đang đọc chương ${r.optInt("chapter_index")} · ${ago(r.s("updated_at"))} trước",
                onClick = { Nav.push(Screen.AdminNovel(id)) },
            )
        }
    }
}

/** Báo cáo term dịch sai chưa xử lý; bấm dòng mở bảng thuật ngữ của truyện. */
@Composable
fun ReportsTab() {
    val l = load("admin.reports") { AdminApi.reports() }
    var act by remember { mutableStateOf<Act?>(null) }
    AdminPane(l) { rows ->
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
