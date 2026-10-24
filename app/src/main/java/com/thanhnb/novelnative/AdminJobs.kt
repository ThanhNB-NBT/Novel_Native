package com.thanhnb.novelnative

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.json.JSONObject

private fun JSONObject.pend() = optString("status") == "pending"
private fun JSONObject.down() = optBoolean("downloading")
private fun JSONObject.blocked() = optBoolean("source_unavailable")
private fun JSONObject.chIdx() = optJSONObject("chapters")?.optInt("chapter_index", -1)?.takeIf { it >= 0 }

private fun priorityLabel(p: Int) = if (p <= 1) "ưu tiên cao nhất" else if (p < 100) "ưu tiên cao" else "ưu tiên thường"

/** Worker: nhịp tim + số job + gộp theo truyện (bấm mở danh sách chương/job). Tự làm mới mỗi 20 giây. */
@Composable
fun JobsTab() {
    val l = load("admin.jobs") { AdminApi.jobs() }
    val beats = load("admin.beats") { AdminApi.heartbeat() }
    val total = load("admin.pending") { AdminApi.pendingJobCount() }
    LaunchedEffect(Unit) { while (true) { delay(20_000); l.reload(); beats.reload(); total.reload() } }
    var open by remember { mutableStateOf<Int?>(null) }
    AdminPane(l) { jobs ->
        val running = jobs.count { it.optString("status") == "running" }
        val crawling = jobs.count { it.pend() && it.down() && !it.blocked() }
        val blocked = jobs.count { it.blocked() }
        val failed = jobs.count { it.optString("status") == "failed" }
        val pendingHere = jobs.count { it.pend() } - crawling - blocked
        val groups = jobs.groupBy { it.getInt("novel_id") }.entries.sortedBy { (_, js) ->
            if (js.any { it.optString("status") == "running" }) 0 else if (js.any { it.pend() }) 1 else 2
        }
        item {
            val cs = MaterialTheme.colorScheme
            Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                StatRow(
                    Triple("$running", "đang dịch", cs.primary),
                    Triple("$crawling", "chờ tải gốc", if (crawling > 0) cs.live else null),
                    Triple("${total.res?.getOrNull() ?: pendingHere}", "chờ dịch", null),
                    Triple("$failed", "lỗi", if (failed > 0) cs.error else null),
                )
                if (blocked > 0) Text("$blocked job thiếu bản gốc, nguồn đang tắt", style = MaterialTheme.typography.labelMedium, color = cs.error)
                if ((total.res?.getOrNull() ?: 0) > jobs.size) Text("Danh sách hiển thị ${jobs.size} job đầu hàng đợi", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(8.dp))
                val b = beats.res?.getOrNull().orEmpty()
                Pulse(b, "crawler", "Crawler")
                Pulse(b, "translator", "Translator")
            }
            HorizontalDivider(Modifier.padding(top = 10.dp, bottom = 2.dp), color = cs.outlineVariant.copy(.5f))
        }
        if (groups.isEmpty()) item { Empty("Không có job đang chạy / chờ / lỗi.") }
        items(groups, key = { it.key }) { (id, js) ->
            val cs = MaterialTheme.colorScheme
            val r = js.count { it.optString("status") == "running" }
            val c = js.count { it.pend() && it.down() && !it.blocked() }
            val bl = js.count { it.blocked() }
            val p = js.count { it.pend() } - c - bl
            val f = js.count { it.optString("status") == "failed" }
            val have = r + p
            val parts = listOfNotNull(
                if (r > 0) "$r đang dịch" else null, if (c > 0) "nguồn $have/${have + c}" else null, if (p > 0) "$p chờ dịch" else null,
                if (bl > 0) "$bl nguồn không khả dụng" else null, if (f > 0) "$f lỗi" else null,
            )
            val (icon, color) = when {
                f > 0 || bl > 0 -> Icons.Rounded.ErrorOutline to cs.error
                r > 0 -> Icons.Rounded.Sync to cs.primary
                c > 0 -> Icons.Rounded.CloudDownload to cs.live
                else -> Icons.Rounded.Schedule to cs.onSurfaceVariant
            }
            AdminRow(icon, js.first().optJSONObject("novels").novelTitle(id), parts.joinToString(" · "), color, { open = id })
        }
    }
    open?.let { id -> JobsSheet(id, l) { open = null } }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun JobsSheet(novelId: Int, l: Loader<List<JSONObject>>, close: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val jobs = l.res?.getOrNull().orEmpty().filter { it.getInt("novel_id") == novelId }
    var act by remember { mutableStateOf<Act?>(null) }
    var err by remember { mutableStateOf<JSONObject?>(null) }
    ModalBottomSheet(close, containerColor = cs.card) {
        Text(jobs.firstOrNull()?.optJSONObject("novels").novelTitle(novelId), Modifier.padding(horizontal = 20.dp, vertical = 4.dp), style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (jobs.isEmpty()) Empty("Không còn job nào.")
        val title = jobs.firstOrNull()?.optJSONObject("novels").novelTitle(novelId)
        TextButton({
            act = Act("Xoá vĩnh viễn?", "\"$title\" cùng TOÀN BỘ chương đã dịch, glossary, tiến độ đọc và job sẽ bị xoá — không hoàn tác được.", "Xoá", true) {
                AdminApi.deleteNovel(novelId); close(); "Đã xoá \"$title\""
            }
        }, Modifier.padding(horizontal = 8.dp)) { Text("Xoá truyện này", color = cs.error) }
        LazyColumn(Modifier.heightIn(max = 560.dp)) {
            items(jobs, key = { it.getInt("id") }) { j ->
                val st = j.optString("status")
                val color = if (j.blocked() || st == "failed") cs.error else if (st == "running") cs.primary else cs.onSurfaceVariant
                val idx = j.chIdx()
                val sub = listOfNotNull(
                    j.optString("type"),
                    if (j.blocked()) "nguồn không khả dụng" else if (st == "pending" && j.down()) "chờ tải bản gốc" else null,
                    priorityLabel(j.optInt("priority")),
                    j.optInt("attempts").takeIf { it > 0 }?.let { "$it lần thử" },
                    if (st == "running") j.s("started_at")?.let { "chạy ${ago(it)}" } else null,
                ).joinToString(" · ") + (if (st == "failed") j.s("error")?.let { "\n$it" } ?: "" else "")
                var menu by remember { mutableStateOf(false) }
                val id = j.getInt("id")
                AdminRow(
                    when { j.blocked() -> Icons.Rounded.CloudOff; st == "failed" -> Icons.Rounded.ErrorOutline; st == "running" -> Icons.Rounded.Sync; else -> Icons.Rounded.Schedule },
                    if (idx != null) "Chương $idx" else "Job #$id", sub, color,
                    onClick = if (st == "failed" && j.s("error") != null) ({ err = j }) else null, subColor = if (st == "failed") cs.error else cs.onSurfaceVariant,
                    trailing = {
                        Box {
                            IconButton({ menu = true }) { Icon(Icons.Rounded.MoreVert, "Tác vụ") }
                            DropdownMenu(menu, { menu = false }) {
                                if (st == "failed") DropdownMenuItem({ Text("Chạy lại") }, {
                                    menu = false
                                    act = Act("Chạy lại job?", "Đẩy job này vào hàng đợi lần nữa.", "Chạy lại") { AdminApi.retryJob(id); "Đã đẩy lại job" }
                                })
                                DropdownMenuItem({ Text("Ưu tiên lên đầu") }, {
                                    menu = false
                                    act = Act("Ưu tiên lên đầu?", "Đặt mức ưu tiên cao nhất cho job này.", "Ưu tiên") { AdminApi.reprioritize(id, 1); "Đã đặt ưu tiên cao nhất" }
                                })
                                DropdownMenuItem({ Text("Huỷ job", color = cs.error) }, {
                                    menu = false
                                    act = Act("Huỷ job này?", "Job bị xoá khỏi hàng đợi, chương trở về \"chưa dịch\". Không mất gì đã dịch xong — có thể xếp dịch lại từ trang truyện.", "Huỷ job", true) {
                                        AdminApi.cancelJob(id, j.optInt("chapter_id")); "Đã huỷ job"
                                    }
                                })
                            }
                        }
                    },
                )
            }
        }
        Spacer(Modifier.height(24.dp))
    }
    ConfirmDialog(act, { act = null }) { l.reload() }
    err?.let { j ->
        AlertDialog(
            { err = null }, confirmButton = { TextButton({ err = null }) { Text("Đóng") } },
            title = { Text(j.chIdx()?.let { "Lỗi chương $it" } ?: "Lỗi job") },
            text = { SelectionContainer { Text(j.optString("error"), style = MaterialTheme.typography.bodySmall) } },
        )
    }
}
