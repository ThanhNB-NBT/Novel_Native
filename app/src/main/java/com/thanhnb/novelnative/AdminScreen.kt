package com.thanhnb.novelnative

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FactCheck
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime

// ---------- Tiện ích dùng chung cho các màn quản trị ----------

fun toast(c: android.content.Context, m: String) = Toast.makeText(c, m, Toast.LENGTH_SHORT).show()

fun secondsSince(iso: String?): Long? =
    iso?.let { runCatching { Duration.between(OffsetDateTime.parse(it).toInstant(), Instant.now()).seconds.coerceAtLeast(0) }.getOrNull() }

/** "12s", "5 phút", "1 giờ 5 phút", "3 ngày" từ mốc ISO tới giờ. */
fun ago(iso: String?): String {
    val s = secondsSince(iso) ?: return "?"
    val m = s / 60
    val h = m / 60
    return when { s < 60 -> "${s}s"; h == 0L -> "$m phút"; h >= 24 -> "${h / 24} ngày"; else -> "$h giờ ${m % 60} phút" }
}

/** 12345 → 12.345 */
fun fmtK(n: Number): String = "%,d".format(n.toLong()).replace(',', '.')

/** Màu "sống" (nhịp tim, nguồn bật) — tertiary của theme là xanh lá. */
val ColorScheme.live get() = tertiary

/** Việc ghi dữ liệu thật: luôn qua hộp thoại xác nhận trước khi [run]. [run] trả câu báo thành công. */
class Act(val title: String, val text: String, val yes: String, val danger: Boolean = false, val run: suspend () -> String)

@Composable
fun ConfirmDialog(act: Act?, close: () -> Unit, after: () -> Unit = {}) {
    if (act == null) return
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    AlertDialog(
        close,
        title = { Text(act.title) },
        text = { Text(act.text) },
        dismissButton = { TextButton(close) { Text("Huỷ") } },
        confirmButton = {
            TextButton({
                close()
                scope.launch {
                    runCatching { act.run() }.onSuccess { toast(ctx, it) }.onFailure { toast(ctx, friendly(it)) }
                    after()
                }
            }) { Text(act.yes, color = if (act.danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary) }
        },
    )
}

/** Hàng số liệu: số lớn mono + nhãn nhỏ, không khung. */
@Composable
fun StatRow(vararg cells: Triple<String, String, Color?>, modifier: Modifier = Modifier) = Row(modifier.fillMaxWidth().padding(vertical = 8.dp)) {
    cells.forEach { (v, label, c) ->
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(v, style = monoStyle(22, c ?: MaterialTheme.colorScheme.onSurface), fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
        }
    }
}

/** Một dòng nhịp tim worker: chấm màu + "hoạt động 12s trước". Im quá 3 phút = coi như treo/tắt. */
@Composable
fun Pulse(beats: List<JSONObject>, name: String, label: String) {
    val cs = MaterialTheme.colorScheme
    val row = beats.firstOrNull { it.optString("name") == name }
    val age = secondsSince(row?.s("at"))
    val alive = age != null && age < 180
    val color = if (alive) cs.live else cs.error
    val text = when {
        row == null -> "$label: chưa từng chạy"
        alive -> "$label: hoạt động ${ago(row.s("at"))} trước" + (row.s("note")?.let { " — $it" } ?: "")
        else -> "$label: KHÔNG phản hồi (${ago(row.s("at"))}) — kiểm tra Docker"
    }
    Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).background(color, CircleShape))
        Text(text, Modifier.padding(start = 8.dp), style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Dòng danh sách phẳng: icon + tiêu đề + phụ đề, vạch mảnh thụt lề bên dưới. */
@Composable
fun AdminRow(
    icon: ImageVector?, title: String, sub: String?, tint: Color = MaterialTheme.colorScheme.primary,
    onClick: (() -> Unit)? = null, subColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    trailing: (@Composable () -> Unit)? = null,
) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().then(if (onClick != null) Modifier.bounceClick(onClick = onClick) else Modifier).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) Icon(icon, null, Modifier.size(22.dp), tint)
        Column(Modifier.weight(1f).padding(start = if (icon != null) 14.dp else 0.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (!sub.isNullOrEmpty()) Text(sub, style = MaterialTheme.typography.labelMedium, color = subColor, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
        trailing?.invoke()
    }
    HorizontalDivider(Modifier.padding(start = if (icon != null) 52.dp else 16.dp, end = 16.dp), color = cs.outlineVariant.copy(.5f))
}

/** Thanh tiến độ mảnh (CPU/RAM/lượt API); đỏ khi gần đầy. */
@Composable
fun Gauge(label: String, frac: Float?, value: String, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    Column(modifier) {
        Row(Modifier.fillMaxWidth()) {
            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
            Text(value, style = monoStyle(12, cs.onSurface))
        }
        LinearProgressIndicator(
            { (frac ?: 0f).coerceIn(0f, 1f) }, Modifier.fillMaxWidth().padding(top = 4.dp).height(6.dp),
            color = if (frac != null && frac > .9f) cs.error else cs.primary, trackColor = cs.onSurface.copy(.08f), strokeCap = androidx.compose.ui.graphics.StrokeCap.Round, gapSize = 0.dp,
            drawStopIndicator = {},
        )
    }
}

/** Khung tab: kéo để làm mới + danh sách cuộn. [content] dựng các item. */
@Composable
fun <T> AdminPane(l: Loader<T>, empty: String? = null, content: LazyListScope.(T) -> Unit) = Loaded(l) { data ->
    PullToRefreshBox(false, l.reload) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) { content(data) }
    }
}

private val tabs = listOf("Worker", "Crawl", "Đang đọc", "Truyện", "Báo cáo")

/** Màn Quản trị (chỉ admin; RLS phía server cũng chặn). 5 tab, bỏ Tu Tiên. */
@Composable
fun AdminScreen() {
    val ok = load("admin", Session.user?.id) { Api.isAdmin() }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var act by remember { mutableStateOf<Act?>(null) }
    var menu by remember { mutableStateOf(false) }
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize()) {
        PageHeader("Hệ thống", "Quản trị", "管", back = true) {
            IconButton({
                act = Act("Chạy lại job lỗi?", "Đẩy lại TOÀN BỘ job dịch lỗi vào hàng đợi và trả chương tải lỗi về hàng tải.", "Chạy lại") {
                    AdminApi.retryAllFailed().let { if (it > 0) "Đã đẩy lại $it job lỗi vào hàng đợi" else "Không có job lỗi nào" }
                }
            }) { Icon(Icons.Rounded.RestartAlt, "Chạy lại toàn bộ job lỗi") }
            IconButton({
                act = Act("Quét lỗi dịch?", "Worker quét chương đã dịch còn tiếng Trung / cụt / mất đoạn và tự xếp lại dịch. Xem kết quả ở tab Worker.", "Bắt đầu quét") {
                    AdminApi.requestAudit(); "Đã bắt đầu quét — chương lỗi sẽ tự xếp lại dịch"
                }
            }) { Icon(Icons.Rounded.FactCheck, "Quét lỗi dịch") }
            Box {
                IconButton({ menu = true }) { Icon(Icons.Rounded.MoreVert, "Thêm") }
                DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem({ Text("Thống kê API") }, { menu = false; Nav.push(Screen.AdminLlm) })
                    DropdownMenuItem({ Text("Theo dõi VPS") }, { menu = false; Nav.push(Screen.AdminVps) })
                    DropdownMenuItem({ Text("Nhật ký lỗi") }, { menu = false; Nav.push(Screen.AdminErrors) })
                }
            }
        }
        Loaded(ok) { admin ->
            if (!admin) return@Loaded Empty("Bạn không có quyền quản trị.")
            Column(Modifier.fillMaxSize()) {
                ScrollableTabRow(
                    tab, containerColor = Color.Transparent, edgePadding = 12.dp,
                    divider = { HorizontalDivider(color = cs.outlineVariant.copy(.5f)) },
                ) {
                    tabs.forEachIndexed { i, t ->
                        Tab(tab == i, { tab = i }, text = { Text(t, fontWeight = if (tab == i) FontWeight.Bold else FontWeight.Medium, fontSize = 15.sp) })
                    }
                }
                when (tab) {
                    0 -> JobsTab()
                    1 -> CrawlTab()
                    2 -> ReadingTab()
                    3 -> NovelsTab()
                    else -> ReportsTab()
                }
            }
        }
    }
    ConfirmDialog(act, { act = null })
}
