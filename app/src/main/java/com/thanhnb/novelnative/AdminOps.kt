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
    val cs = MaterialTheme.colorScheme
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
                        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(9.dp).background(if (stale) cs.error else cs.live, androidx.compose.foundation.shape.CircleShape))
                                Text(label, Modifier.weight(1f).padding(horizontal = 10.dp), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(if (stale) "MẤT TÍN HIỆU" else "SỐNG", color = if (stale) cs.error else cs.live, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                Box {
                                    IconButton({ menu = true }) { Icon(Icons.Rounded.SettingsPower, "Khởi động lại worker", tint = cs.primary) }
                                    DropdownMenu(menu, { menu = false }) {
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
                            Gauge("CPU", cpu?.let { (it / 100).toFloat() }, cpu?.let { "${"%.0f".format(it)}%" } ?: "—")
                            Gauge("RAM", if (memT != null && memT > 0 && memU != null) (memU / memT).toFloat() else null, if (memU == null) "—" else "${memU.toInt()}/${memT?.toInt() ?: "?"} MB")
                            if (swap > 0) Text("Swap đang dùng: ${swap.toInt()} MB", style = MaterialTheme.typography.labelMedium, color = if (swap > 200) cs.error else cs.onSurfaceVariant)
                            Gauge("Disk", if (diskT != null && diskT > 0 && diskU != null) (diskU / diskT).toFloat() else null, if (diskU == null) "—" else "$diskU/${diskT ?: "?"} GB")
                            Row {
                                Text("↓ " + (h.d("net_rx_kbps")?.let(::rate) ?: "—"), style = monoStyle(12, cs.primary))
                                Text("↑ " + (h.d("net_tx_kbps")?.let(::rate) ?: "—"), Modifier.padding(start = 16.dp), style = monoStyle(12, cs.live))
                                Spacer(Modifier.weight(1f))
                                h.d("net_rx_gb")?.let { rx -> Text("tổng ↓ ${"%.1f".format(rx)} · ↑ ${"%.1f".format(h.d("net_tx_gb") ?: 0.0)} GB", style = MaterialTheme.typography.labelSmall) }
                            }
                            Text(
                                listOfNotNull(
                                    if (load1 != null && cores != null && cores > 0) "load $load1/$cores (${"%.2f".format(load1 / cores)}/core)" else null,
                                    cores?.let { "$it core" }, h.d("uptime_h")?.let { "uptime ${"%.0f".format(it)} giờ" },
                                ).joinToString(" · "),
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                        HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = cs.outlineVariant.copy(.5f))
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
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
                    if (failed.isEmpty() && errs.isEmpty()) item { Empty("Chưa ghi nhận lỗi nào.") }
                    if (failed.isNotEmpty()) item { Eyebrow("Lỗi worker (dịch / crawl)", Modifier.padding(start = 20.dp, top = 12.dp, bottom = 6.dp)) }
                    items(failed, key = { "j" + it.getInt("id") }) { j ->
                        val id = j.getInt("novel_id")
                        val idx = j.optJSONObject("chapters")?.optInt("chapter_index", -1)?.takeIf { it >= 0 }
                        val err = j.s("error") ?: "Không rõ lỗi"
                        ErrRow("j${j.getInt("id")}", j.optJSONObject("novels").novelTitle(id) + (idx?.let { " · chương $it" } ?: ""), err, open == "j${j.getInt("id")}", err, { open = it }, Icons.Rounded.CloudOff, clip::setText)
                    }
                    if (errs.isNotEmpty()) item { Eyebrow("Lỗi app (máy người dùng gửi về)", Modifier.padding(start = 20.dp, top = 16.dp, bottom = 6.dp)) }
                    items(errs, key = { "e" + (it.s("created_at") ?: "") + it.s("user_id") + it.optString("message").hashCode() }) { e ->
                        val k = "e" + (e.s("created_at") ?: "") + e.optString("message").hashCode()
                        val head = "[${e.optString("who")} · ${e.s("app_version") ?: "?"} · ${e.s("platform") ?: "?"}] ${e.optString("message")}"
                        ErrRow(k, head, "${ago(e.s("created_at"))} trước", open == k, head + (e.s("stack")?.let { "\n$it" } ?: ""), { open = it }, Icons.Rounded.ErrorOutline, clip::setText)
                    }
                }
            }
        }
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
