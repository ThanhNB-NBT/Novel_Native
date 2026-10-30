package com.thanhnb.novelnative

import androidx.compose.ui.graphics.luminance
import android.widget.Toast
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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

/** Màu "sống" (nhịp tim, nguồn bật) — cố định xanh lá, không theo bộ màu (tertiary giờ có thể là vàng). */
val ColorScheme.live get() = if (background.luminance() < .5f) androidx.compose.ui.graphics.Color(0xFF3DBE76) else androidx.compose.ui.graphics.Color(0xFF27864D)

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

/** Hàng ô số liệu: mỗi ô là một thẻ bo tròn, số lớn mono (có màu khi cần chú ý) + nhãn nhỏ. */
@Composable
fun StatRow(vararg cells: Triple<String, String, Color?>, modifier: Modifier = Modifier) = Row(modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    val cs = MaterialTheme.colorScheme
    cells.forEach { (v, label, c) ->
        val shape = RoundedCornerShape(16.dp)
        Column(
            Modifier.weight(1f).fillMaxHeight().clip(shape).background(if (c != null) c.copy(.10f) else cs.card).border(.6.dp, (c ?: cs.outlineVariant).copy(.35f), shape).padding(vertical = 12.dp, horizontal = 2.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // "…" = đang tải → khối nhấp nháy thay cho số
            if (v == "…") Box(Modifier.padding(vertical = 4.dp).size(40.dp, 22.dp).shimmer(6.dp))
            else Text(v, style = monoStyle(22, c ?: cs.onSurface), fontWeight = FontWeight.Bold, maxLines = 1)
            Text(label, Modifier.padding(top = 2.dp), style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, maxLines = 2)
        }
    }
}

/** Dải viên thuốc "giá trị · nhãn" thay cho một dòng chữ dài ngăn bằng dấu chấm. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MetaChips(vararg items: Pair<String, String>, modifier: Modifier = Modifier) = FlowRow(modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
    val cs = MaterialTheme.colorScheme
    items.forEach { (v, label) ->
        Row(Modifier.clip(CircleShape).background(cs.onSurface.copy(.06f)).padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(v, style = monoStyle(12, cs.onSurface), fontWeight = FontWeight.Bold)
            Text(label, Modifier.padding(start = 5.dp), style = MaterialTheme.typography.labelMedium)
        }
    }
}

/** Nhịp tim crawler + translator trong MỘT thẻ: chấm đập, viên thuốc trạng thái (luôn có chữ), "5s trước". Im quá 3 phút = coi như treo/tắt. */
@Composable
fun Pulses(beats: List<JSONObject>, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(20.dp)
    Column(modifier.fillMaxWidth().clip(shape).background(cs.card).border(.6.dp, cs.outlineVariant.copy(.5f), shape)) {
        listOf("crawler" to "Crawler", "translator" to "Translator").forEachIndexed { i, (name, label) ->
            val row = beats.firstOrNull { it.optString("name") == name }
            val age = secondsSince(row?.s("at"))
            val alive = age != null && age < 180
            val color = if (alive) cs.live else cs.error
            if (i > 0) HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = cs.outlineVariant.copy(.5f))
            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                HeartDot(color, alive)
                Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                    Text(label, style = MaterialTheme.typography.titleSmall)
                    val sub = when { row == null -> "chưa từng chạy"; alive -> row.s("note") ?: "đang chạy"; else -> "không phản hồi — kiểm tra Docker" }
                    Text(sub, style = MaterialTheme.typography.labelMedium, color = if (alive) cs.onSurfaceVariant else cs.error, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Column(horizontalAlignment = Alignment.End) {
                    StatusPill(if (alive) "Hoạt động" else if (row == null) "Chưa chạy" else "Treo", color)
                    if (row != null) Text("${ago(row.s("at"))} trước", Modifier.padding(top = 3.dp), style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
                }
            }
        }
    }
}

/** Thẻ số chính của tab: một con số lớn (+ "/ tổng"), thanh tiến độ và dòng chú thích. Số "…" = đang tải. */
@Composable
fun HeroCard(label: String, value: String, of: String?, frac: Float?, caption: String?, modifier: Modifier = Modifier, tint: Color = MaterialTheme.colorScheme.primary) {
    val cs = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(20.dp)
    Column(modifier.fillMaxWidth().clip(shape).background(tint.copy(.08f)).border(.6.dp, tint.copy(.3f), shape).padding(16.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant)
        Row(Modifier.padding(top = 2.dp), verticalAlignment = Alignment.Bottom) {
            if (value == "…") Box(Modifier.padding(vertical = 6.dp).size(110.dp, 30.dp).shimmer(8.dp))
            else Text(value, style = monoStyle(34, tint), fontWeight = FontWeight.Bold, maxLines = 1)
            if (of != null) Text(" / $of", Modifier.padding(bottom = 5.dp), style = monoStyle(14, cs.onSurfaceVariant))
        }
        if (frac != null) LinearProgressIndicator(
            { frac.coerceIn(0f, 1f) }, Modifier.fillMaxWidth().padding(top = 8.dp).height(8.dp),
            color = tint, trackColor = cs.onSurface.copy(.08f), strokeCap = androidx.compose.ui.graphics.StrokeCap.Round, gapSize = 0.dp, drawStopIndicator = {},
        )
        if (caption != null) Text(caption, Modifier.padding(top = 8.dp), style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
    }
}

/** Bảng "nhãn ........ số": thay cho đống viên thuốc xuống dòng lộn xộn. Màu chỉ gán cho số cần chú ý. */
@Composable
fun KvCard(vararg rows: Triple<String, String, Color?>, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(20.dp)
    Column(modifier.fillMaxWidth().clip(shape).background(cs.card).border(.6.dp, cs.outlineVariant.copy(.5f), shape)) {
        rows.forEachIndexed { i, (label, v, c) ->
            if (i > 0) HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = cs.outlineVariant.copy(.5f))
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                if (v == "…") Box(Modifier.size(44.dp, 16.dp).shimmer(5.dp)) else Text(v, style = monoStyle(14, c ?: cs.onSurface), fontWeight = FontWeight.Bold)
            }
        }
    }
}

/** Nhãn nhóm nhỏ phía trên danh sách, kèm số đếm dạng viên thuốc. */
@Composable
fun SectionLabel(text: String, count: String? = null) {
    val cs = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 16.dp, top = 16.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant)
        if (count != null) Text(count, Modifier.clip(CircleShape).background(cs.onSurface.copy(.07f)).padding(horizontal = 9.dp, vertical = 3.dp), style = MaterialTheme.typography.labelMedium)
    }
}

/** Chấm nhịp tim: còn sống thì đập hai nhịp (lub-dub) và phát vòng sóng lan ra rồi mờ; treo/tắt thì đỏ đứng yên. */
@Composable
private fun HeartDot(color: Color, alive: Boolean) {
    val t = rememberInfiniteTransition(label = "heart")
    val ring by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1800, easing = LinearOutSlowInEasing)), label = "ring")
    val beat by t.animateFloat(
        1f, 1f,
        infiniteRepeatable(keyframes {
            durationMillis = 1800
            1f at 0; 1.35f at 120; 1f at 260; 1.22f at 380; 1f at 520; 1f at 1800
        }),
        label = "beat",
    )
    Box(Modifier.size(24.dp), Alignment.Center) {
        if (alive) Box(Modifier.size(24.dp).graphicsLayer { scaleX = .45f + ring * .55f; scaleY = scaleX; alpha = (1f - ring) * .5f }.background(color, CircleShape))
        Box(Modifier.size(16.dp).background(color.copy(.22f), CircleShape))
        Box(Modifier.size(8.dp).graphicsLayer { if (alive) { scaleX = beat; scaleY = beat } }.background(color, CircleShape))
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
    val pager = rememberPagerState(tab) { tabs.size }
    val scope = rememberCoroutineScope()
    LaunchedEffect(pager) { snapshotFlow { pager.currentPage }.collect { tab = it } } // vuốt ngang đổi tab; chạm tab thì pager trượt tới
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
                    pager.currentPage, containerColor = Color.Transparent, edgePadding = 12.dp,
                    divider = { HorizontalDivider(color = cs.outlineVariant.copy(.5f)) },
                ) {
                    tabs.forEachIndexed { i, t ->
                        Tab(pager.currentPage == i, { scope.launch { pager.animateScrollToPage(i) } }, selectedContentColor = cs.primary, unselectedContentColor = cs.onSurfaceVariant, text = { Text(t, fontWeight = if (pager.currentPage == i) FontWeight.Bold else FontWeight.Medium, fontSize = 15.sp) })
                    }
                }
                HorizontalPager(pager, Modifier.fillMaxSize(), key = { it }) { page ->
                    when (page) {
                        0 -> JobsTab()
                        1 -> CrawlTab()
                        2 -> ReadingTab()
                        3 -> NovelsTab()
                        else -> ReportsTab()
                    }
                }
            }
        }
    }
    ConfirmDialog(act, { act = null })
}
