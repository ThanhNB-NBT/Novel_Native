package com.thanhnb.novelnative

import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.runtime.*
import androidx.compose.material3.*
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import kotlin.math.abs
import kotlin.math.cos
import java.time.LocalDate
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.sin

/** Âm lịch Việt Nam (múi giờ +7) — thuật toán Hồ Ngọc Đức, y hệt lunar.dart của bản Flutter. */
class LunarDate(val day: Int, val month: Int, val year: Int, val leap: Boolean, val jd: Int) {
    val yearName get() = "${CAN[(year + 6) % 10]} ${CHI[(year + 8) % 12]}"
    val monthCanChi get() = "${CAN[(year * 12 + month + 3) % 10]} ${CHI[(month + 1) % 12]}"
    val dayCanChi get() = "${CAN[(jd + 9) % 10]} ${CHI[(jd + 1) % 12]}"
    val monthName get() = "Tháng ${MONTHS[month - 1]}${if (leap) " nhuận" else ""}"
}

private val CAN = listOf("Giáp", "Ất", "Bính", "Đinh", "Mậu", "Kỷ", "Canh", "Tân", "Nhâm", "Quý")
private val CHI = listOf("Tý", "Sửu", "Dần", "Mão", "Thìn", "Tỵ", "Ngọ", "Mùi", "Thân", "Dậu", "Tuất", "Hợi")
private val CAN_HAN = listOf("甲", "乙", "丙", "丁", "戊", "己", "庚", "辛", "壬", "癸")
private val CHI_HAN = listOf("子", "丑", "寅", "卯", "辰", "巳", "午", "未", "申", "酉", "戌", "亥")

/** "Bính Ngọ" → "丙午". */
private fun han(canChi: String): String {
    val (can, chi) = canChi.split(' ')
    return CAN_HAN[CAN.indexOf(can).coerceAtLeast(0)] + CHI_HAN[CHI.indexOf(chi).coerceAtLeast(0)]
}

private val MONTHS = listOf("Giêng", "Hai", "Ba", "Tư", "Năm", "Sáu", "Bảy", "Tám", "Chín", "Mười", "Mười Một", "Chạp")
private const val TZ = 7.0
private const val DR = PI / 180

private fun jdFromDate(dd: Int, mm: Int, yy: Int): Int {
    val a = (14 - mm) / 12
    val y = yy + 4800 - a
    val m = mm + 12 * a - 3
    return dd + (153 * m + 2) / 5 + 365 * y + y / 4 - y / 100 + y / 400 - 32045
}

private fun newMoonDay(k: Int): Int {
    val t = k / 1236.85
    val t2 = t * t
    val t3 = t2 * t
    var jd1 = 2415020.75933 + 29.53058868 * k + 0.0001178 * t2 - 0.000000155 * t3
    jd1 += 0.00033 * sin((166.56 + 132.87 * t - 0.009173 * t2) * DR)
    val m = 359.2242 + 29.10535608 * k - 0.0000333 * t2 - 0.00000347 * t3
    val mpr = 306.0253 + 385.81691806 * k + 0.0107306 * t2 + 0.00001236 * t3
    val f = 21.2964 + 390.67050646 * k - 0.0016528 * t2 - 0.00000239 * t3
    var c1 = (0.1734 - 0.000393 * t) * sin(m * DR) + 0.0021 * sin(2 * DR * m)
    c1 = c1 - 0.4068 * sin(mpr * DR) + 0.0161 * sin(DR * 2 * mpr)
    c1 = c1 - 0.0004 * sin(DR * 3 * mpr)
    c1 = c1 + 0.0104 * sin(DR * 2 * f) - 0.0051 * sin(DR * (m + mpr))
    c1 = c1 - 0.0074 * sin(DR * (m - mpr)) + 0.0004 * sin(DR * (2 * f + m))
    c1 = c1 - 0.0004 * sin(DR * (2 * f - m)) - 0.0006 * sin(DR * (2 * f + mpr))
    c1 = c1 + 0.0010 * sin(DR * (2 * f - mpr)) + 0.0005 * sin(DR * (2 * mpr + m))
    val deltat = if (t < -11) 0.001 + 0.000839 * t + 0.0002261 * t2 - 0.00000845 * t3 - 0.000000081 * t * t3
    else -0.000278 + 0.000265 * t + 0.000262 * t2
    return floor(jd1 + c1 - deltat + 0.5 + TZ / 24).toInt()
}

private fun sunLongitude(jdn: Int): Int {
    val t = (jdn - 0.5 - TZ / 24 - 2451545.0) / 36525
    val t2 = t * t
    val m = 357.52910 + 35999.05030 * t - 0.0001559 * t2 - 0.00000048 * t * t2
    val l0 = 280.46645 + 36000.76983 * t + 0.0003032 * t2
    var dl = (1.914600 - 0.004817 * t - 0.000014 * t2) * sin(DR * m)
    dl += (0.019993 - 0.000101 * t) * sin(DR * 2 * m) + 0.000290 * sin(DR * 3 * m)
    var l = (l0 + dl) * DR
    l -= PI * 2 * floor(l / (PI * 2))
    return floor(l / PI * 6).toInt()
}

/** Chỉ số nửa-cung (mỗi bước 15° kinh độ Mặt Trời, 0 = Xuân phân) tại 0h ngày [jdn] — dùng cho tiết khí. */
internal fun sunTerm15(jdn: Int): Int {
    val t = (jdn - 0.5 - TZ / 24 - 2451545.0) / 36525
    val t2 = t * t
    val m = 357.52910 + 35999.05030 * t - 0.0001559 * t2 - 0.00000048 * t * t2
    val l0 = 280.46645 + 36000.76983 * t + 0.0003032 * t2
    var dl = (1.914600 - 0.004817 * t - 0.000014 * t2) * sin(DR * m)
    dl += (0.019993 - 0.000101 * t) * sin(DR * 2 * m) + 0.000290 * sin(DR * 3 * m)
    var l = (l0 + dl) * DR
    l -= PI * 2 * floor(l / (PI * 2))
    return floor(l / PI * 12).toInt()
}

private fun lunarMonth11(yy: Int): Int {
    val off = jdFromDate(31, 12, yy) - 2415021
    val k = floor(off / 29.530588853).toInt()
    val nm = newMoonDay(k)
    return if (sunLongitude(nm) >= 9) newMoonDay(k - 1) else nm
}

private fun leapMonthOffset(a11: Int): Int {
    val k = floor((a11 - 2415021.076998695) / 29.530588853 + 0.5).toInt()
    var i = 1
    var arc = sunLongitude(newMoonDay(k + i))
    var last: Int
    do {
        last = arc
        i++
        arc = sunLongitude(newMoonDay(k + i))
    } while (arc != last && i < 14)
    return i - 1
}

fun toLunar(d: LocalDate): LunarDate {
    val dayNumber = jdFromDate(d.dayOfMonth, d.monthValue, d.year)
    val k = floor((dayNumber - 2415021.076998695) / 29.530588853).toInt()
    var monthStart = newMoonDay(k + 1)
    if (monthStart > dayNumber) monthStart = newMoonDay(k)
    var a11 = lunarMonth11(d.year)
    var b11 = a11
    var lunarYear: Int
    if (a11 >= monthStart) {
        lunarYear = d.year
        a11 = lunarMonth11(d.year - 1)
    } else {
        lunarYear = d.year + 1
        b11 = lunarMonth11(d.year + 1)
    }
    val lunarDay = dayNumber - monthStart + 1
    val diff = (monthStart - a11) / 29
    var leap = false
    var lunarMonth = diff + 11
    if (b11 - a11 > 365) {
        val leapDiff = leapMonthOffset(a11)
        if (diff >= leapDiff) {
            lunarMonth = diff + 10
            if (diff == leapDiff) leap = true
        }
    }
    if (lunarMonth > 12) lunarMonth -= 12
    if (lunarMonth >= 11 && diff < 4) lunarYear -= 1
    return LunarDate(lunarDay, lunarMonth, lunarYear, leap, dayNumber)
}

private val WEEKDAYS = listOf("Thứ Hai", "Thứ Ba", "Thứ Tư", "Thứ Năm", "Thứ Sáu", "Thứ Bảy", "Chủ Nhật")

fun special(l: LunarDate): String? = when {
    l.leap -> null
    l.day == 1 && l.month == 1 -> "Tết Nguyên Đán"
    l.day == 15 && l.month == 1 -> "Rằm tháng Giêng"
    l.day == 15 && l.month == 7 -> "Rằm tháng Bảy"
    l.day == 15 && l.month == 8 -> "Tết Trung Thu"
    l.day == 1 -> "Mùng 1"
    l.day == 15 -> "Ngày Rằm"
    else -> null
}

private const val SYNODIC = 29.53
private val MOON_LIT = Color(0xFFFFE2A0)
private val FULL_DOT = Color(0xFFE5A91A)
private val GOOD_GREEN = Color(0xFF1F9D63)
private val TERM_TEAL = Color(0xFF14A3A3)

/** Tên pha trăng theo ngày âm (1 = trăng non, 15 = trăng tròn). */
private fun moonName(d: Int) = when (d) {
    1 -> "Trăng non"
    in 2..6 -> "Trăng lưỡi liềm đầu tháng"
    in 7..8 -> "Trăng thượng huyền"
    in 9..13 -> "Trăng khuyết đầy dần"
    in 14..16 -> "Trăng tròn"
    in 17..21 -> "Trăng khuyết vơi dần"
    in 22..23 -> "Trăng hạ huyền"
    in 24..28 -> "Trăng lưỡi liềm cuối tháng"
    else -> "Trăng tàn"
}

/** Phần sáng của trăng theo ngày âm (null = trăng tối hẳn; rằm = cả đĩa). Đĩa tâm [c] bán kính [r]. */
private fun litPath(lunarDay: Int, c: Offset, r: Float): Path? {
    val p = ((lunarDay - 1) / SYNODIC).coerceIn(0.0, 0.999)
    val k = (1 - cos(2 * PI * p)) / 2 // phần đĩa được chiếu sáng
    val outer = Rect(c.x - r, c.y - r, c.x + r, c.y + r)
    if (k > 0.985) return Path().apply { addOval(outer) }
    if (k <= 0.015) return null
    val waxing = p < 0.5
    val crescent = cos(2 * PI * p) > 0
    val rx = (r * abs(cos(2 * PI * p))).toFloat()
    val term = Rect(c.x - rx, c.y - r, c.x + rx, c.y + r)
    return Path().apply {
        arcTo(outer, -90f, if (waxing) 180f else -180f, true)
        // đường phân sáng tối: về lại đỉnh qua nửa elip, lồi về phía sáng (lưỡi liềm) hoặc phía tối (khuyết)
        val throughRight = waxing == crescent
        arcTo(term, 90f, if (throughRight) -180f else 180f, false)
        close()
    }
}

/**
 * Mặt trăng vẽ theo ngày âm: nền tối, phần sáng = nửa đĩa phía được chiếu + nửa elip làm đường phân sáng tối.
 * Trăng trước rằm (đầy dần) sáng bên phải, sau rằm (vơi dần) sáng bên trái — như nhìn từ bán cầu Bắc.
 */
@Composable
private fun MoonPhase(lunarDay: Int, size: Dp, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    val dark = cs.onSurface.copy(alpha = .14f)
    val ring = cs.onSurface.copy(alpha = .22f)
    Canvas(modifier.size(size)) {
        val r = this.size.minDimension / 2f
        val c = Offset(this.size.width / 2f, this.size.height / 2f)
        drawCircle(dark, r, c)
        litPath(lunarDay, c, r)?.let { drawPath(it, MOON_LIT) }
        drawCircle(ring, r - .5f, c, style = Stroke(1.dp.toPx()))
    }
}

private val STARS = listOf( // chỉ rải nửa phải: bên trái là chữ, sao chen vào làm rối
    Triple(.47f, .62f, 1.0f), Triple(.55f, .22f, 1.2f), Triple(.62f, .9f, 1.7f), Triple(.68f, .12f, 1.0f), Triple(.74f, .5f, 1.4f), Triple(.8f, .82f, 1.0f),
    Triple(.86f, .16f, 1.8f), Triple(.91f, .55f, 1.2f), Triple(.95f, .86f, 1.5f), Triple(.58f, .45f, .9f), Triple(.98f, .3f, 1.0f), Triple(.5f, .08f, 1.3f),
)

/** Nền vũ trụ: những chấm sao rải cố định (không ngẫu nhiên, để tờ lịch không nhảy sao mỗi lần vẽ lại). */
@Composable
private fun Stars(modifier: Modifier) = Canvas(modifier) {
    STARS.forEach { (x, y, r) -> drawCircle(Color.White.copy(alpha = .3f + r * .2f), r.dp.toPx(), Offset(x * size.width, y * size.height)) }
}

/** Mặt trăng trần lơ lửng giữa nền vũ trụ: quầng sáng, phần tối có ánh đất, phần sáng chuyển sắc kèm hố trăng; nhấp nhô nhẹ lên xuống. */
@Composable
private fun FloatingMoon(lunarDay: Int, size: Dp) {
    val t = rememberInfiniteTransition(label = "moonFloat")
    val dy by t.animateFloat(-4f, 4f, infiniteRepeatable(tween(3600, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "dy")
    Canvas(Modifier.size(size).graphicsLayer { translationY = dy.dp.toPx() }) {
        val s = this.size.minDimension
        val c = Offset(s / 2, s / 2)
        val r = s * .3f
        drawCircle(Brush.radialGradient(listOf(MOON_LIT.copy(.30f), MOON_LIT.copy(.10f), Color.Transparent), c, r * 2.1f), r * 2.1f, c) // quầng sáng
        drawCircle(Color(0xFF3B4870).copy(.6f), r, c) // phần tối: ánh đất
        litPath(lunarDay, c, r)?.let { lit ->
            drawPath(lit, Brush.radialGradient(listOf(Color(0xFFFFF4D6), MOON_LIT, Color(0xFFE6C070)), Offset(c.x - r * .3f, c.y - r * .3f), r * 1.5f))
            clipPath(lit) {
                listOf(Triple(-.38f, -.28f, .2f), Triple(.28f, -.5f, .12f), Triple(.34f, .22f, .22f), Triple(-.2f, .42f, .14f), Triple(.6f, -.08f, .09f), Triple(-.55f, .1f, .1f))
                    .forEach { (x, y, k) -> drawCircle(Color(0xFFB08A4A).copy(.28f), r * k, Offset(c.x + x * r, c.y + y * r)) }
            }
        }
        drawCircle(MOON_LIT.copy(.3f), r, c, style = Stroke(1.dp.toPx()))
    }
}

/** Thẻ lịch âm hôm nay ở tab Tôi: tờ lịch (ngày dương) + ngày âm + trăng của ngày. */
@Composable
fun LunarCard() {
    val cs = MaterialTheme.colorScheme
    val now = LocalDate.now()
    val l = toLunar(now)
    val ev = events(now, l)
    Row(Modifier.fillMaxWidth().bounceClick { Nav.push(Screen.Lunar) }, verticalAlignment = Alignment.CenterVertically) {
        DateTile(now, Modifier.size(64.dp))
        Column(Modifier.weight(1f).padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                "${l.day} ${l.monthName.replaceFirst("Tháng", "tháng")} âm lịch",
                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text("Năm ${l.yearName} · ngày ${l.dayCanChi}${if (isGoodDay(l)) " · hoàng đạo" else ""}", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            ev.firstOrNull()?.let { EventPill(it.name) }
        }
        MoonPhase(l.day, 40.dp)
    }
}

/** Ô "tờ lịch": dải màu nhấn ghi thứ, số ngày dương to ở dưới. */
@Composable
private fun DateTile(d: LocalDate, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(18.dp)
    val wd = if (d.dayOfWeek.value == 7) "CN" else "T${d.dayOfWeek.value + 1}"
    Column(modifier.clip(shape).background(cs.primary.copy(alpha = .1f)).border(1.dp, cs.primary.copy(alpha = .25f), shape), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.fillMaxWidth().background(cs.primary).padding(vertical = 3.dp), Alignment.Center) {
            Text(wd, style = MaterialTheme.typography.labelSmall, color = cs.onPrimary, fontWeight = FontWeight.Bold)
        }
        Box(Modifier.weight(1f).fillMaxWidth(), Alignment.Center) {
            Text("${d.dayOfMonth}", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = cs.primary, lineHeight = 30.sp)
        }
    }
}

@Composable
private fun EventPill(text: String) {
    val cs = MaterialTheme.colorScheme
    Text(
        text, Modifier.padding(top = 4.dp).clip(RoundedCornerShape(50)).background(cs.primary.copy(alpha = .12f)).padding(horizontal = 10.dp, vertical = 3.dp),
        style = MaterialTheme.typography.labelSmall, color = cs.primary, fontWeight = FontWeight.SemiBold,
    )
}

private fun solarLine(d: LocalDate) = "%s, %02d/%02d/%d".format(WEEKDAYS[d.dayOfWeek.value - 1], d.dayOfMonth, d.monthValue, d.year)

/** Lịch tháng: thẻ lưới ngày dương kèm ngày âm, thẻ chi tiết ngày đang chọn; vuốt ngang hoặc mũi tên đổi tháng. */
@Composable
fun LunarScreen(tab: Boolean = false) {
    val cs = MaterialTheme.colorScheme
    val today = remember { LocalDate.now() }
    var month by remember { mutableStateOf(today.withDayOfMonth(1)) }
    var sel by remember { mutableStateOf(today) }
    var drag by remember { mutableFloatStateOf(0f) }
    fun go(delta: Long) { month = month.plusMonths(delta) }
    Column(Modifier.fillMaxSize()) {
        PageHeader("Âm lịch Việt Nam", "Lịch âm", "曆", back = !tab)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, bottom = if (tab) DockSpace else 0.dp) // tab: chừa chỗ cho dock kính nổi
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(onDragEnd = {
                        if (abs(drag) > 80f) go(if (drag < 0) 1 else -1)
                        drag = 0f
                    }) { _, d -> drag += d }
                },
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Thanh tháng: tháng dương to, dòng nhỏ ghi các tháng âm nằm trong tháng này
            val first = toLunar(month)
            val last = toLunar(month.withDayOfMonth(month.lengthOfMonth()))
            val lunarSpan = if (first.month == last.month && first.leap == last.leap) first.monthName else "${first.monthName} – ${last.monthName.removePrefix("Tháng ")}"
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton({ go(-1) }) { Icon(Icons.Rounded.ChevronLeft, "Tháng trước") }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Tháng ${month.monthValue}, ${month.year}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("$lunarSpan · ${last.yearName}", style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
                }
                IconButton({ go(1) }) { Icon(Icons.Rounded.ChevronRight, "Tháng sau") }
            }
            if (month.year != today.year || month.month != today.month) TextButton(
                { month = today.withDayOfMonth(1); sel = today }, Modifier.align(Alignment.CenterHorizontally),
            ) { Text("Về hôm nay") }

            DayHero(sel)

            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(cs.card).padding(horizontal = 8.dp, vertical = 12.dp)) {
                Row(Modifier.padding(bottom = 6.dp)) {
                    listOf("T2", "T3", "T4", "T5", "T6", "T7", "CN").forEachIndexed { i, w ->
                        Text(w, Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = if (i == 6) cs.error else cs.onSurfaceVariant)
                    }
                }
                AnimatedContent(
                    month,
                    transitionSpec = {
                        val dir = if (targetState > initialState) 1 else -1
                        (slideInHorizontally(tween(260)) { it / 3 * dir } + fadeIn(tween(220))) togetherWith
                            (slideOutHorizontally(tween(260)) { -it / 3 * dir } + fadeOut(tween(160)))
                    },
                    label = "month",
                ) { m ->
                    val lead = m.dayOfWeek.value - 1 // tuần bắt đầu Thứ Hai
                    val days = m.lengthOfMonth()
                    val cells = (lead + days + 6) / 7 * 7
                    Column {
                        for (r in 0 until cells / 7) Row {
                            for (c in 0 until 7) {
                                val i = r * 7 + c
                                if (i < lead || i >= lead + days) Spacer(Modifier.weight(1f).aspectRatio(.82f))
                                else DayCell(m.withDayOfMonth(i - lead + 1), today, sel, Modifier.weight(1f)) { sel = it }
                            }
                        }
                    }
                }
            }

            Legend()
            DayExtras(sel, today)
            Spacer(Modifier.height(32.dp))
        }
    }
}

private val GOLD = Color(0xFFC49A3A)
private val CREAM = Color(0xFFFBF3E4)
private val MONTH_HAN = listOf("正", "二", "三", "四", "五", "六", "七", "八", "九", "十", "冬", "臘")

/** Ngày âm theo cách ghi trên lịch Hán: 初一…初十, 十一…十九, 二十, 廿一…廿九, 三十. */
private fun hanDay(n: Int): String {
    val num = "〇一二三四五六七八九十"
    return when {
        n <= 10 -> "初" + num[n]
        n < 20 -> "十" + num[n - 10]
        n == 20 -> "二十"
        n < 30 -> "廿" + num[n - 20]
        else -> "三十"
    }
}

/**
 * Tờ lịch bóc của ngày đang chọn: giấy kem + gáy son đỏ (tên tháng, năm Can Chi), số ngày âm cỡ lớn kèm ấn triện ghi tháng,
 * trăng trên nền đêm viền vàng đồng, Can Chi chữ Hán ngăn bằng vạch vàng. Nền giấy chuyển nâu ấm ở chế độ tối.
 */
@Composable
private fun DayHero(d: LocalDate) {
    val cs = MaterialTheme.colorScheme
    val seal = LocalSeal.current // son của ấn triện/gáy lịch = màu triện của bộ màu đang chọn
    val onSeal = onColor(seal, lightBias = 1.4f)
    val paper = lerp(cs.card, GOLD, .07f)
    val ink = cs.onSurface
    val soft = cs.onSurfaceVariant
    val l = toLunar(d)
    val ev = events(d, l)
    val good = isGoodDay(l)
    val e = napAmElement(l)
    val shape = RoundedCornerShape(24.dp)
    Column(Modifier.fillMaxWidth().clip(shape).background(paper).border(1.dp, GOLD.copy(.55f), shape)) {
        // gáy lịch
        Row(
            Modifier.fillMaxWidth().background(Brush.horizontalGradient(listOf(seal, lerp(seal, Color.Black, .3f)))).padding(horizontal = 20.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("${l.monthName.uppercase()} · ${l.yearName.uppercase()}", Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, color = onSeal, letterSpacing = 1.5.sp, maxLines = 1)
            Text(han(l.yearName) + "年", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = onSeal)
        }
        // dải trời đêm: sao rải khắp, số ngày + ấn triện bên trái, trăng lơ lửng bên phải
        Box(Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color(0xFF0F1830), Color(0xFF243360))))) {
            Stars(Modifier.matchParentSize())
            Row(Modifier.padding(start = 20.dp, end = 8.dp, top = 14.dp, bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(solarLine(d), style = MaterialTheme.typography.labelLarge, color = Color.White.copy(.75f))
                    Row(verticalAlignment = Alignment.Bottom) {
                        AnimatedContent(l.day, transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(120)) }, label = "lunarDay") { n ->
                            Text("$n", style = MaterialTheme.typography.headlineLarge.copy(fontSize = 88.sp, lineHeight = 92.sp), fontWeight = FontWeight.ExtraBold, color = Color.White)
                        }
                        // ấn triện: tháng âm
                        Column(
                            Modifier.padding(start = 12.dp, bottom = 16.dp).clip(RoundedCornerShape(5.dp)).background(seal).padding(horizontal = 9.dp, vertical = 5.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(MONTH_HAN[(l.month - 1).coerceIn(0, 11)], style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = onSeal, lineHeight = 20.sp)
                            Text(if (l.leap) "閏" else "月", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = onSeal, lineHeight = 20.sp)
                        }
                    }
                    Text("${hanDay(l.day)} · ${moonName(l.day)}", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(.7f))
                }
                FloatingMoon(l.day, 128.dp)
            }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (good) HeroTag("Ngày hoàng đạo", seal, onSeal) else HeroTag("Ngày hắc đạo", Color.Transparent, soft, soft.copy(.5f))
            ev.forEach { HeroTag(it.name, GOLD.copy(.2f), ink) }
        }
        Box(Modifier.padding(horizontal = 20.dp).fillMaxWidth().height(1.dp).background(GOLD.copy(.45f)))
        Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            listOf("Năm" to l.yearName, "Tháng" to l.monthCanChi, "Ngày" to l.dayCanChi).forEachIndexed { i, (label, v) ->
                if (i > 0) Box(Modifier.width(1.dp).height(56.dp).background(GOLD.copy(.45f)))
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = soft, letterSpacing = 1.sp)
                    Text(han(v), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = ink)
                    Text(v, style = MaterialTheme.typography.labelMedium, color = soft, maxLines = 1)
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).background(elementColor(e), CircleShape))
            Text("Nạp âm", Modifier.padding(start = 10.dp).width(64.dp), style = MaterialTheme.typography.labelMedium, color = soft)
            Text(napAm(l).replaceFirstChar { it.uppercase() }, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = ink)
            Text("Hành ${e.replaceFirstChar { it.uppercase() }}", style = MaterialTheme.typography.labelMedium, color = elementColor(e), fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun HeroTag(text: String, bg: Color, fg: Color, border: Color? = null) = Text(
    text, Modifier.clip(RoundedCornerShape(50)).background(bg).then(if (border != null) Modifier.border(1.dp, border, RoundedCornerShape(50)) else Modifier).padding(horizontal = 12.dp, vertical = 5.dp),
    style = MaterialTheme.typography.labelMedium, color = fg, fontWeight = FontWeight.SemiBold, maxLines = 1,
)

/** Phần dưới tờ lịch: tiết khí + đồng hồ 12 canh giờ. */
@Composable
private fun DayExtras(d: LocalDate, today: LocalDate) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        TermCard(d)
        HoursCard(toLunar(d), isToday = d == today)
    }
}

/** Màu theo ngũ hành của nạp âm. */
private fun elementColor(e: String) = when (e) {
    "kim" -> Color(0xFFB8941F)
    "mộc" -> Color(0xFF2E9D5B)
    "thủy" -> Color(0xFF2F7BE0)
    "hỏa" -> Color(0xFFE5532D)
    else -> Color(0xFF9A6B3F) // thổ
}

/** Tiết khí: tiết đang diễn ra, thanh tiến độ tới tiết kế tiếp (số ngày còn lại). */
@Composable
private fun TermCard(d: LocalDate) {
    val cs = MaterialTheme.colorScheme
    val t = remember(d) { solarTerm(d) }
    val daysIn = java.time.temporal.ChronoUnit.DAYS.between(t.start, d).toInt()
    val left = java.time.temporal.ChronoUnit.DAYS.between(d, t.next).toInt()
    fun fmt(x: LocalDate) = "%02d/%02d".format(x.dayOfMonth, x.monthValue)
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(cs.card).padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Eyebrow("Tiết khí")
                Text(t.name, Modifier.padding(top = 2.dp), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
                Text(
                    if (daysIn == 0) "Bắt đầu từ hôm nay (${fmt(t.start)})" else "Từ ${fmt(t.start)} · đã qua $daysIn ngày",
                    style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(t.nextName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = cs.primary)
                Text("${fmt(t.next)} · còn $left ngày", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            }
        }
        Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(50)).background(cs.primary.copy(alpha = .12f))) {
            Box(Modifier.fillMaxWidth(t.progress(d).coerceAtLeast(.04f)).fillMaxHeight().clip(RoundedCornerShape(50)).background(cs.primary))
        }
    }
}

/** Đồng hồ 12 canh giờ: Tý ở đỉnh, đi theo chiều kim đồng hồ; giờ hoàng đạo tô xanh, giờ hiện tại (xem hôm nay) có vòng ngoài. */
@Composable
private fun HoursCard(l: LunarDate, isToday: Boolean) {
    val cs = MaterialTheme.colorScheme
    val good = remember(l.jd) { goodHours(l) }
    val nowIdx = if (isToday) ((java.time.LocalTime.now().hour + 1) / 2) % 12 else -1
    val ringDp = 248f
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(cs.card).padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Eyebrow("Giờ hoàng đạo", Modifier.weight(1f))
            Text("${good.size} giờ tốt", style = MaterialTheme.typography.labelMedium, color = GOOD_GREEN, fontWeight = FontWeight.SemiBold)
        }
        Box(Modifier.align(Alignment.CenterHorizontally).size(ringDp.dp), Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val w = 30.dp.toPx()
                val r = size.minDimension / 2 - w / 2 - 6.dp.toPx()
                val tl = Offset(center.x - r, center.y - r)
                for (i in 0 until 12) {
                    val start = -90f + i * 30f - 15f + 2f // lệch nửa cung để Tý nằm giữa đỉnh; chừa khe 4° giữa các cung
                    drawArc(if (i in good) GOOD_GREEN else cs.onSurface.copy(alpha = .09f), start, 26f, false, tl, Size(2 * r, 2 * r), style = Stroke(w))
                    if (i == nowIdx) {
                        val ro = r + w / 2 + 4.dp.toPx()
                        drawArc(cs.primary, start, 26f, false, Offset(center.x - ro, center.y - ro), Size(2 * ro, 2 * ro), style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
                    }
                }
            }
            val rl = ringDp / 2 - 15f - 6f
            for (i in 0 until 12) {
                val a = Math.toRadians(-90.0 + i * 30.0)
                Text(
                    HOURS[i].first, Modifier.offset(x = (rl * cos(a)).dp, y = (rl * sin(a)).dp),
                    style = MaterialTheme.typography.labelLarge, fontWeight = if (i in good) FontWeight.ExtraBold else FontWeight.Medium,
                    color = if (i in good) Color.White else cs.onSurfaceVariant,
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (nowIdx >= 0) {
                    Text("Bây giờ", style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
                    Text("Giờ ${HOURS[nowIdx].first}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold, color = if (nowIdx in good) GOOD_GREEN else cs.onSurface)
                    Text(HOURS[nowIdx].second, style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
                } else {
                    Text("${good.size}/12", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold, color = GOOD_GREEN)
                    Text("giờ hoàng đạo", style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
                }
            }
        }
        Text(good.joinToString(" · ") { "${HOURS[it].first} ${HOURS[it].second}" }, Modifier.fillMaxWidth(), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

@Composable
private fun Legend() {
    val cs = MaterialTheme.colorScheme
    val seal = LocalSeal.current
    @Composable fun item(mark: @Composable () -> Unit, text: String) = Row(verticalAlignment = Alignment.CenterVertically) {
        mark(); Text(text, Modifier.padding(start = 5.dp), style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
    }
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        item({ Box(Modifier.size(6.dp).border(1.2.dp, seal, CircleShape)) }, "Mùng 1")
        item({ Box(Modifier.size(6.dp).background(FULL_DOT, CircleShape)) }, "Rằm")
        item({ Box(Modifier.size(6.dp).background(TERM_TEAL, CircleShape)) }, "Tiết khí")
        item({ Box(Modifier.size(6.dp).background(GOOD_GREEN, CircleShape)) }, "Hoàng đạo")
    }
}

@Composable
private fun DayCell(d: LocalDate, today: LocalDate, sel: LocalDate, modifier: Modifier, pick: (LocalDate) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val seal = LocalSeal.current
    val onSeal = onColor(seal, lightBias = 1.4f)
    val l = toLunar(d)
    val isToday = d == today
    val isSel = d == sel
    val sunday = d.dayOfWeek.value == 7
    val mark = l.day == 1 || l.day == 15
    val term = remember(d) { termStartingOn(d) != null }
    val good = isGoodDay(l)
    val holiday = remember(d) { events(d, l).any { !(it.name == "Mùng 1" || it.name == "Ngày Rằm") } }
    val lunarText = if (l.day == 1) "${l.day}/${l.month}${if (l.leap) "*" else ""}" else "${l.day}"
    val fg = if (isToday) onSeal else if (sunday || holiday) cs.error else cs.onSurface
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier.aspectRatio(.82f).padding(2.dp).clip(shape)
            .background(if (isToday) seal else if (isSel) seal.copy(.12f) else Color.Transparent)
            .then(if (isSel && !isToday) Modifier.border(1.5.dp, seal, shape) else Modifier)
            .bounceClick { pick(d) },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("${d.dayOfMonth}", color = fg, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Row(verticalAlignment = Alignment.CenterVertically) {
                // mùng 1 = vòng rỗng (trăng non), rằm = chấm đặc (trăng tròn)
                if (mark) Box(
                    Modifier.padding(end = 3.dp).size(5.dp).then(
                        if (l.day == 15) Modifier.background(if (isToday) onSeal else FULL_DOT, CircleShape)
                        else Modifier.border(1.2.dp, if (isToday) onSeal else seal, CircleShape),
                    ),
                )
                Text(
                    lunarText, style = MaterialTheme.typography.labelSmall,
                    color = if (isToday) onSeal.copy(.85f) else if (mark) seal else cs.onSurfaceVariant,
                    fontWeight = if (mark) FontWeight.Bold else null,
                )
            }
        }
        // góc trên phải: tiết khí; góc dưới: dải xanh mảnh cho ngày hoàng đạo
        if (term) Box(Modifier.align(Alignment.TopEnd).padding(5.dp).size(6.dp).background(if (isToday) onSeal else TERM_TEAL, CircleShape))
        if (good) Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp).width(14.dp).height(2.5.dp).background(if (isToday) onSeal.copy(.8f) else GOOD_GREEN, RoundedCornerShape(50)))
    }
}
