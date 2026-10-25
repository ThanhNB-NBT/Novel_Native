package com.thanhnb.novelnative

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.text.TextStyle
import kotlin.math.pow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import org.json.JSONObject
import java.time.Instant
import java.time.OffsetDateTime
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Phần "hồn" của Tu Tiên: phân tích tốc độ, đếm ngược buff, hiệu ứng đột phá/Tâm Ma/Phi Thăng, cảnh nhân vật theo cảnh giới,
 * hào quang khi nhận bảo vật. Các công thức linh căn MIRROR lib/cultivation.dart của Flutter (= cult_linhcan_mult, mig 078)
 * và chỉ để HIỂN THỊ — số thật do server tính.
 */

/** Màu hệ ngũ hành (mirror elemAura bên Flutter). */
private val ElemColor = mapOf(
    "hoa" to Color(0xFFFF7043), "thuy" to Color(0xFF74C0FC), "moc" to Color(0xFF69DB7C),
    "kim" to Color(0xFFFFC94D), "tho" to Color(0xFFB08968), "all" to Color(0xFFB197FC),
)

internal fun JSONObject.elements(): List<String> = optJSONArray("elements")?.let { a -> List(a.length()) { a.optString(it) } }.orEmpty()

/** Màu các dải linh khí theo bộ hệ linh căn; đơn hệ thêm một sắc sáng hơn cho đầy. */
internal fun elemColor(e: String) = ElemColor[e] ?: Color.Gray

internal fun qiColors(els: List<String>, fallback: Color): List<Color> {
    val cols = els.mapNotNull { ElemColor[it] }.ifEmpty { listOf(fallback) }
    return if (cols.size == 1) cols + lerpColor(cols[0], Color.White, .45f) else cols
}

private fun lerpColor(a: Color, b: Color, f: Float) = androidx.compose.ui.graphics.lerp(a, b, f)

private val LinhCanTierNames = listOf("Ngũ Hành Tạp Căn", "Tứ Linh Căn", "Tam Linh Căn", "Song Linh Căn", "Đơn Linh Căn", "Dị Linh Căn", "Tiên Linh Căn")

/** Bậc gốc trời định: thiên/hỗn độn → Tiên (7), dị căn khác → Dị (6), còn lại theo số hệ (rỗng coi như 5 hệ, khớp SQL). */
private fun bornTier(count: Int, variant: String?) = when {
    variant == "thien" || variant == "hon" -> 7
    variant != null -> 6
    else -> 6 - (if (count < 1) 5 else count.coerceIn(1, 5))
}

/** (bậc, điểm dư) sau khi tiêu điểm luyện căn; thăng b→b+1 tốn 5×2^(b−1). */
private fun linhCanTier(count: Int, variant: String?, linhCan: Int): Pair<Int, Int> {
    var b = bornTier(count, variant)
    var rem = (linhCan - 1).coerceAtLeast(0)
    while (b < 7) {
        val cost = 5 * (1 shl (b - 1))
        if (rem < cost) break
        rem -= cost; b++
    }
    return b to rem
}

private fun linhCanMult(count: Int, variant: String?, linhCan: Int): Double {
    val (b, rem) = linhCanTier(count, variant, linhCan)
    return if (b < 7) (1 shl (b - 1)) * (1 + rem / (5.0 * (1 shl (b - 1)))) else 64 * (1 + 0.1 * rem)
}

/** Tên bậc linh căn: dị căn giữ tên riêng khi còn ở bậc bẩm sinh, thăng vượt lên thì lấy tên thang bậc. */
internal fun rootName(s: JSONObject): String {
    val variant = s.strOrNull("variant")
    val count = s.elements().size
    val (b, _) = linhCanTier(count, variant, s.optInt("linh_can", 1))
    if (variant != null && b == bornTier(count, variant)) return Variants[variant] ?: "Dị Linh Căn"
    return LinhCanTierNames[b - 1]
}

private fun JSONObject.until(k: String): Instant? = strOrNull(k)?.let { runCatching { OffsetDateTime.parse(it).toInstant() }.getOrNull() }

private fun num(d: Double) = (if (d % 1.0 == 0.0) "%.0f" else "%.1f").format(d).replace('.', ',')

/** Popup các yếu tố ảnh hưởng tốc độ tu luyện (mirror _showSpeedBreakdown / cult_base_rate 067). Tổng là 'rate' server trả. */
@Composable
internal fun SpeedDialog(s: JSONObject, dismiss: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val els = s.elements()
    val variant = s.strOrNull("variant")
    val lc = s.optInt("linh_can", 1)
    val tier = s.optInt("tien_tier", 0)
    val eq = s.optJSONObject("equipped") ?: JSONObject()
    val cp = eq.optJSONObject("congphap")
    val cpElem = cp?.optJSONObject("effect")?.strOrNull("element")
    val match = cpElem != null && (cpElem == "all" || variant == "hon" || cpElem in els)
    val now = Instant.now()
    val ratePct = listOf("vukhi", "phapbao").sumOf { eq.optJSONObject(it)?.optJSONObject("effect")?.optDouble("rate_pct", 0.0) ?: 0.0 }
    val rows = buildList {
        if (cp != null) add(Triple("Công pháp (phẩm ${GradeNames.getOrElse(cp.optInt("grade") - 1) { "?" }})", "×" + listOf("1,5", "3", "6", "12", "24").getOrElse(cp.optInt("grade") - 1) { "?" }, true))
        else add(Triple("Chưa học công pháp", "×1", false))
        add(Triple("Hợp linh căn" + if (match) "" else " (không hợp)", if (match) "×1,3" else "×1", match))
        add(Triple("Linh căn (${rootName(s)})", "×" + num(linhCanMult(els.size, variant, lc)), variant != null))
        if (lc > 1) add(Triple("Luyện căn", "${lc - 1} điểm (đã gộp vào bậc)", true))
        if (s.optString("race") == "ma") add(Triple("Tà tốc Ma tộc", "×1,1", true))
        if (s.strOrNull("ascended_at") != null && tier > 0) add(Triple("Tiên uy (${TienTierNames.getOrElse(tier) { "" }})", "×" + num(1 + .2 * tier), true))
        if (ratePct > 0) add(Triple("Pháp khí (vũ khí · pháp bảo)", "+${num(ratePct)}%", true))
        if (s.until("buff_until")?.isAfter(now) == true && s.optInt("buff_pct") > 0) add(Triple("Đan tăng tốc", "+${s.optInt("buff_pct")}%", true))
        if (s.until("stone_until")?.isAfter(now) == true && s.optInt("stone_pct") > 0) add(Triple("Linh thạch", "+${s.optInt("stone_pct")}%", true))
    }
    AlertDialog(
        dismiss, containerColor = cs.card,
        title = { Text("Tốc độ tu luyện") },
        text = {
            Column {
                rows.forEach { (label, value, on) ->
                    Row(Modifier.padding(vertical = 4.dp)) {
                        Text(label, Modifier.weight(1f))
                        Text(value, fontWeight = FontWeight.Bold, color = if (on) cs.primary else cs.onSurfaceVariant)
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = 10.dp))
                Row {
                    Text("Tổng", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                    Text("${num(s.optDouble("rate"))}/giây", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.ExtraBold, color = cs.primary)
                }
            }
        },
        confirmButton = { TextButton(dismiss) { Text("Đóng") } },
    )
}

/** Chip đếm ngược hiệu ứng có thời hạn (đan dược / linh thạch), tự cập nhật mỗi giây, hết hạn thì ẩn. */
@Composable
internal fun BuffChips(s: JSONObject) {
    val cs = MaterialTheme.colorScheme
    var now by remember { mutableStateOf(Instant.now()) }
    LaunchedEffect(Unit) { while (true) { delay(1000); now = Instant.now() } }
    val buffs = listOfNotNull(
        s.until("buff_until")?.let { Triple("Đan lực", s.optInt("buff_pct"), it) },
        s.until("stone_until")?.let { Triple("Linh thạch", s.optInt("stone_pct"), it) },
    ).filter { it.third.isAfter(now) }
    if (buffs.isEmpty()) return
    Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        buffs.forEach { (label, pct, until) ->
            val left = java.time.Duration.between(now, until).seconds
            val h = left / 3600; val m = left / 60 % 60; val sec = left % 60
            Row(Modifier.clip(RoundedCornerShape(9.dp)).background(cs.secondaryContainer).padding(horizontal = 9.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Bolt, null, Modifier.size(12.dp), tint = cs.onSecondaryContainer)
                Text(
                    " $label +$pct% · ${if (h > 0) "${h}g " else ""}$m′${sec.toString().padStart(2, '0')}″",
                    fontSize = 11.sp, fontWeight = FontWeight.Bold, color = cs.onSecondaryContainer,
                )
            }
        }
    }
}

private val FxGold = Color(0xFFFFD25A)
private val FxRed = Color(0xFFFF5252)

/**
 * Cảnh theo cảnh giới (vẽ sau nhân vật): sao trời lấp lánh từ Hóa Thần (5); đom đóm linh khí mang màu bộ hệ linh căn bốc lên.
 * [t] 0..1 là đồng hồ 8 giây của sân khấu; mọi tần số là bội nguyên của vòng nên lặp liền mạch.
 */
internal fun DrawScope.drawRealmScene(realm: Int, ascended: Boolean, qi: List<Color>, accent: Color, t: Float) {
    val tau = 2f * PI.toFloat()
    val cx = size.width / 2f
    val ground = size.height * .92f
    val r = if (ascended) 9 else realm
    if (r >= 5) for (i in 0 until 16) {
        val x = ((i * 61 + 13) % 100) / 100f * size.width
        val y = ((i * 37 + 7) % 100) / 100f * size.height * .5f
        val tw = .25f + .75f * abs(sin(t * tau * 2f + i * 1.3f))
        drawCircle(Color.White.copy(.7f * tw), (1f + i % 3 * .6f) * density, Offset(x, y))
    }
    // như Flutter: không bệ/kiếm dưới chân, không vòng quỹ đạo đều — vật bay quanh người đi theo đường Lissajous riêng
    if (qi.isNotEmpty()) for (i in 0 until 10) {
        val pr = (t + i / 10f) % 1f
        val x = cx + ((i * 53 % 100) / 100f - .5f) * size.width * .55f + sin(pr * tau + i) * 8 * density
        val y = ground - pr * size.height * .8f
        drawCircle(qi[i % qi.size].copy(sin(PI.toFloat() * pr) * .85f), 2.2f * density, Offset(x, y))
    }
}

/** Ảnh vật phẩm bật ra giữa vầng hào quang màu phẩm xoay chậm — dùng khi nhận bảo vật (cơ duyên). */
@Composable
internal fun ItemGlory(item: JSONObject, size: Dp = 88.dp) {
    val g = gradeColor(item.optInt("grade", 1))
    val rot by rememberInfiniteTransition(label = "glory").animateFloat(0f, 360f, infiniteRepeatable(tween(12000, easing = LinearEasing)), label = "rot")
    val pop = remember { Animatable(.3f) }
    LaunchedEffect(Unit) { pop.animateTo(1f, spring(.45f, 300f)) }
    Box(Modifier.size(size * 1.8f), Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val c = center
            val rr = this.size.minDimension / 2f
            rotate(rot, c) {
                for (i in 0 until 12) {
                    val a = i * PI.toFloat() / 6f
                    val w = .13f
                    drawPath(Path().apply {
                        moveTo(c.x, c.y)
                        lineTo(c.x + cos(a - w) * rr, c.y + sin(a - w) * rr)
                        lineTo(c.x + cos(a + w) * rr, c.y + sin(a + w) * rr)
                        close()
                    }, Brush.radialGradient(listOf(g.copy(.35f), Color.Transparent), c, rr))
                }
            }
            drawCircle(Brush.radialGradient(listOf(g.copy(.5f * pop.value), Color.Transparent), c, rr * .7f), rr * .7f, c)
        }
        Box(Modifier.size(size).graphicsLayer { scaleX = pop.value; scaleY = pop.value }) { ItemArt(item) }
    }
}

/** Admin: đổi tộc/giới tính tự do (server chỉ cho profiles.is_admin; user thường chỉ chọn một lần). */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun AdminAvatarDialog(
    s: JSONObject, dismiss: () -> Unit, onPreview: (String, JSONObject) -> Unit,
    onDev: (Int, Int) -> Unit, onGrantHalos: () -> Unit, onPick: (String, String) -> Unit,
) {
    var gender by remember { mutableStateOf(s.optString("gender", "nam")) }
    AlertDialog(
        dismiss, containerColor = MaterialTheme.colorScheme.card,
        title = { Text("Công cụ admin") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Đổi xuất thân", style = MaterialTheme.typography.labelLarge)
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    listOf("nam" to "Nam", "nu" to "Nữ").forEachIndexed { i, (k, v) -> SegmentedButton(gender == k, { gender = k }, SegmentedButtonDefaults.itemShape(i, 2)) { Text(v) } }
                }
                RaceNames.forEach { (k, name) ->
                    Text(
                        name + if (k == s.optString("race")) " · hiện tại" else "",
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh).clickable { onPick(k, gender) }.padding(14.dp),
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                // công cụ test — CHỈ bản debug (như kDebugMode bên Flutter); cult_debug_set server tự chặn người không phải admin
                if (BuildConfig.DEBUG) {
                HorizontalDivider(Modifier.padding(top = 6.dp))
                Text("DEV · test hiệu ứng bậc", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.tertiary, fontWeight = FontWeight.Bold)
                val r0 = s.optInt("realm", 1)
                val st0 = s.optInt("stage", 1)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("Về Luyện Khí 1" to (1 to 1), "Đầy tu vi bậc này" to (r0 to st0), "Sẵn sàng đại cảnh giới" to (r0 to 9), "Độ Kiếp 9 (Phi Thăng)" to (9 to 9))
                        .forEach { (label, rs) -> AssistChip({ onDev(rs.first, rs.second) }, { Text(label, fontSize = 12.sp) }) }
                    AssistChip(onGrantHalos, { Text("Nhận đủ hào quang", fontSize = 12.sp) })
                }
                // xem thử hiệu ứng bằng kết quả giả — không gọi server, không đổi tài khoản
                Text("Xem thử hiệu ứng (kết quả giả)", Modifier.padding(top = 4.dp), style = MaterialTheme.typography.labelLarge)
                val realm = s.optInt("realm", 1)
                val demos = listOf(
                    "Lên tầng" to ("stage" to JSONObject().put("success", true).put("realm", realm).put("stage", 2)),
                    "Đột phá" to ("major" to JSONObject().put("success", true).put("realm", (realm + 1).coerceAtMost(9)).put("stage", 1).put("chance", 62)),
                    "Thất bại" to ("major" to JSONObject().put("success", false).put("chance", 38)),
                    "Phi Thăng" to ("ascend" to JSONObject().put("ascended", true).put("tamma", JSONObject().put("win", true).put("chance", 71))),
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    demos.forEach { (label, d) -> AssistChip({ onPreview(d.first, d.second) }, { Text(label, fontSize = 12.sp) }) }
                }
                }
            }
        },
        confirmButton = { TextButton(dismiss) { Text("Đóng") } },
    )
}
