package com.thanhnb.novelnative

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircleOutline
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.SettingsPower
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.json.JSONObject

private fun JSONObject.d(k: String): Double? = if (has(k) && !isNull(k)) optDouble(k) else null

private fun rate(kbps: Double) = if (kbps >= 1024) "${"%.1f".format(kbps / 1024)} MB/s" else "${"%.0f".format(kbps)} kB/s"

/** Theo dõi máy chủ chạy worker: CPU/RAM/disk/mạng/load/uptime (worker đẩy host_metrics mỗi phút); tự làm mới mỗi 15 giây. */
@Composable
fun VpsScreen() {
    val hosts = load("admin.hosts") { AdminApi.hostMetrics() }
    val cmds = load("admin.hostcmds") { AdminApi.hostCommands() }
    LaunchedEffect(Unit) { while (true) { delay(15_000); hosts.reload(); cmds.reload() } }
    var act by remember { mutableStateOf<Act?>(null) }
    var renaming by remember { mutableStateOf<Pair<String, String>?>(null) } // host to nhãn đang gõ
    val scope = rememberCoroutineScope()
    val cs = MaterialTheme.colorScheme
    renaming?.let { (host, text) ->
        AlertDialog(
            { renaming = null },
            title = { Text("Nhãn cho $host") },
            text = { OutlinedTextField(text, { renaming = host to it }, singleLine = true, label = { Text("Nhãn (để trống = dùng tên host)") }) },
            dismissButton = { TextButton({ renaming = null }) { Text("Huỷ") } },
            confirmButton = {
                TextButton({
                    renaming = null
                    scope.launch { runCatching { AdminApi.renameHost(host, text.trim()) }; hosts.reload() }
                }) { Text("Lưu") }
            },
        )
    }
    Column(Modifier.fillMaxSize()) {
        PageHeader("Quản trị", "Theo dõi VPS", "機", back = true)
        Loaded(hosts) { list ->
            PullToRefreshBox(false, { hosts.reload(); cmds.reload() }) {
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
                    if (list.isEmpty()) item { Empty("Chưa có số liệu nào.\n\nWorker trên VPS cần bản mới (có đẩy host_metrics) và đã chạy ít nhất 1 phút.") }
                    items(list, key = { it.optString("host") }) { h ->
                        val host = h.optString("host")
                        val stale = (secondsSince(h.s("updated_at")) ?: 0) > 300
                        val label = h.s("label") ?: host
                        val memT = h.d("mem_total_mb"); val memU = h.d("mem_used_mb")
                        val diskT = h.d("disk_total_gb"); val diskU = h.d("disk_used_gb")
                        val cpu = h.d("cpu_pct"); val load1 = h.d("load1"); val cores = h.d("cpu_count")?.toInt()
                        val swap = h.d("swap_used_mb") ?: 0.0
                        var menu by remember { mutableStateOf(false) }
                        PaperCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
                        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(9.dp).background(if (stale) cs.error else cs.live, androidx.compose.foundation.shape.CircleShape))
                                Text(label, Modifier.weight(1f).padding(horizontal = 10.dp), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                StatusPill(if (stale) "Mất tín hiệu" else "Sống", if (stale) cs.error else cs.live)
                                Box {
                                    IconButton({ menu = true }) { Icon(Icons.Rounded.SettingsPower, "Khởi động lại worker", tint = cs.primary) }
                                    DropdownMenu(menu, { menu = false }) {
                                        DropdownMenuItem({ Text("Sửa nhãn") }, { menu = false; renaming = host to (h.s("label") ?: "") })
                                        DropdownMenuItem({ Text("Xoá dòng host", color = cs.error) }, {
                                            menu = false
                                            act = Act("Xoá dòng host?", "Xoá số liệu của $host khỏi danh sách. Nếu worker còn chạy, dòng mới sẽ tự hiện lại sau ~1 phút.", "Xoá", true) {
                                                AdminApi.deleteHost(host); "Đã xoá $host"
                                            }
                                        })
                                        listOf("restart" to "toàn bộ worker", "restart_crawler" to "crawler", "restart_translator" to "translator").forEach { (cmd, what) ->
                                            DropdownMenuItem({ Text("Khởi động lại $what") }, {
                                                menu = false
                                                act = Act("Khởi động lại?", "Gửi lệnh restart $what cho host $host?\n\nWorker nhận lệnh trong ~10 giây. Người đang đọc có thể bị ngắt giữa chương — chỉ làm khi bị treo.", "Gửi lệnh", true) {
                                                    AdminApi.sendHostCommand(host, cmd); "Đã gửi lệnh — worker nhận trong ~10 giây"
                                                }
                                            })
                                        }
                                    }
                                }
                            }
                            h.s("address")?.let { Text("IP $it · cập nhật ${ago(h.s("updated_at"))} trước" + if (stale) " (nghi ngờ chết)" else "", style = monoStyle(11)) }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                                RingGauge("CPU", cpu?.let { (it / 100).toFloat() }, cores?.let { "$it core" }, Modifier.weight(1f))
                                RingGauge("RAM", if (memT != null && memT > 0 && memU != null) (memU / memT).toFloat() else null,
                                    if (memU == null) null else "${memU.toInt()}/${memT?.toInt() ?: "?"} MB", Modifier.weight(1f))
                                RingGauge("Disk", if (diskT != null && diskT > 0 && diskU != null) (diskU / diskT).toFloat() else null,
                                    if (diskU == null) null else "${diskU.toInt()}/${diskT?.toInt() ?: "?"} GB", Modifier.weight(1f))
                            }
                            if (swap > 0) StatusPill("Swap đang dùng ${swap.toInt()} MB", if (swap > 200) cs.error else cs.onSurfaceVariant)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("↓ " + (h.d("net_rx_kbps")?.let(::rate) ?: "—"), style = monoStyle(12, cs.primary))
                                Text("↑ " + (h.d("net_tx_kbps")?.let(::rate) ?: "—"), Modifier.padding(start = 16.dp), style = monoStyle(12, cs.live))
                                Spacer(Modifier.weight(1f))
                                h.d("net_rx_gb")?.let { rx -> Text("tổng ↓ ${"%.1f".format(rx)} · ↑ ${"%.1f".format(h.d("net_tx_gb") ?: 0.0)} GB", style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant) }
                            }
                            Text(
                                listOfNotNull(
                                    if (load1 != null && cores != null && cores > 0) "load $load1/$cores (${"%.2f".format(load1 / cores)}/core)" else null,
                                    cores?.let { "$it core" }, h.d("uptime_h")?.let { "uptime ${"%.0f".format(it)} giờ" },
                                ).joinToString(" · "),
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                        }
                    }
                    item { Eyebrow("Lệnh gần đây", Modifier.padding(start = 20.dp, top = 20.dp, bottom = 6.dp)) }
                    val cl = cmds.res?.getOrNull().orEmpty()
                    if (cl.isEmpty()) item { Text("Chưa có lệnh nào được gửi.", Modifier.padding(horizontal = 20.dp), style = MaterialTheme.typography.labelMedium) }
                    items(cl, key = { "c" + it.optInt("id") }) { c ->
                        val st = c.optString("status")
                        AdminRow(
                            when (st) { "done" -> Icons.Rounded.CheckCircleOutline; "error", "running" -> Icons.Rounded.ErrorOutline; else -> Icons.Rounded.Schedule },
                            c.optString("command"), "${c.optString("host")} · ${ago(c.s("created_at"))} trước" + (c.s("output")?.let { " · $it" } ?: ""),
                            if (st == "done") cs.live else if (st == "error") cs.error else cs.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
    ConfirmDialog(act, { act = null }) { cmds.reload() }
}

/** Nhật ký lỗi: job worker lỗi (dịch/crawl) + lỗi app người dùng gửi về (client_errors, chỉ admin đọc). Bấm dòng để xem đầy đủ. */
@Composable
fun ErrorsScreen() {
    val jobs = load("admin.jobs") { AdminApi.jobs() }
    val remote = load("admin.clienterr") { AdminApi.clientErrors() }
    var open by remember { mutableStateOf<String?>(null) }
    val cs = MaterialTheme.colorScheme
    val clip = LocalClipboardManager.current
    Column(Modifier.fillMaxSize()) {
        PageHeader("Quản trị", "Nhật ký lỗi", "錯", back = true)
        Loaded(remote) { errs ->
            val failed = jobs.res?.getOrNull().orEmpty().filter { it.optString("status") == "failed" }
            PullToRefreshBox(false, { jobs.reload(); remote.reload() }) {
                LazyColumn(
                    Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    // lỗi giống hệt nhau (cùng thông điệp) gộp một dòng "×N" — 3 lần chia cho 0 không cần 3 dòng
                    val mine = AppErrorLog.entries.groupBy { it.optString("message") }.values.toList()
                    val mineCount = AppErrorLog.entries.size
                    if (failed.isEmpty() && errs.isEmpty() && mine.isEmpty()) item { Empty("Chưa ghi nhận lỗi nào.") }
                    else item {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            CountTile(mineCount, "trên máy này", Modifier.weight(1f))
                            CountTile(failed.size, "worker", Modifier.weight(1f))
                            CountTile(errs.size, "người dùng", Modifier.weight(1f))
                        }
                    }
                    if (mine.isNotEmpty()) item {
                        ErrSection("Lỗi trên máy này", mineCount, { TextButton({ AppErrorLog.clear() }) { Text("Xoá") } }) {
                            mine.forEach { g ->
                                val e = g.first(); val k = "m" + e.optString("time"); val head = e.optString("message")
                                ErrRow(k, head + if (g.size > 1) "  ×${g.size}" else "", "${ago(e.optString("time"))} trước", open == k, head + '\n' + e.optString("stack"), { open = it }, Icons.Rounded.ErrorOutline, clip::setText)
                            }
                        }
                    }
                    if (failed.isNotEmpty()) item {
                        ErrSection("Lỗi worker (dịch / crawl)", failed.size) {
                            failed.forEach { j ->
                                val id = j.getInt("novel_id")
                                val idx = j.optJSONObject("chapters")?.optInt("chapter_index", -1)?.takeIf { it >= 0 }
                                val err = j.s("error") ?: "Không rõ lỗi"
                                ErrRow("j${j.getInt("id")}", j.optJSONObject("novels").novelTitle(id) + (idx?.let { " · chương $it" } ?: ""), err, open == "j${j.getInt("id")}", err, { open = it }, Icons.Rounded.CloudOff, clip::setText)
                            }
                        }
                    }
                    if (errs.isNotEmpty()) item {
                        ErrSection("Lỗi app (máy người dùng gửi về)", errs.size) {
                            errs.forEach { e ->
                                val k = "e" + (e.s("created_at") ?: "") + e.optString("message").hashCode()
                                val head = "[${e.optString("who")} · ${e.s("app_version") ?: "?"} · ${e.s("platform") ?: "?"}] ${e.optString("message")}"
                                ErrRow(k, head, "${ago(e.s("created_at"))} trước", open == k, head + (e.s("stack")?.let { "\n$it" } ?: ""), { open = it }, Icons.Rounded.ErrorOutline, clip::setText)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CountTile(n: Int, label: String, modifier: Modifier) {
    val cs = MaterialTheme.colorScheme
    PaperCard(modifier) {
        Column(Modifier.fillMaxWidth().padding(vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$n", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = if (n > 0) cs.error else cs.onSurface)
            Text(label, style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
        }
    }
}

@Composable
private fun ErrSection(title: String, count: Int, action: (@Composable () -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val cs = MaterialTheme.colorScheme
    PaperCard(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, color = cs.primary)
            StatusPill("$count", cs.error)
            action?.invoke()
        }
        content()
        Spacer(Modifier.height(6.dp)) // chừa chỗ cho vạch kẻ cuối của AdminRow khỏi dính góc bo
    }
}

@Composable
private fun ErrRow(key: String, title: String, sub: String, open: Boolean, full: String, setOpen: (String?) -> Unit, icon: androidx.compose.ui.graphics.vector.ImageVector, copy: (AnnotatedString) -> Unit) {
    val cs = MaterialTheme.colorScheme
    AdminRow(icon, title, if (open) null else sub, cs.error, { setOpen(if (open) null else key) })
    if (open) Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 10.dp)) {
        SelectionContainer { Text(full, style = MaterialTheme.typography.bodySmall.copy(fontFamily = Mono), color = cs.error) }
        TextButton({ copy(AnnotatedString(full)) }, Modifier.align(Alignment.End)) {
            Icon(Icons.Rounded.ContentCopy, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("Sao chép")
        }
    }
}
