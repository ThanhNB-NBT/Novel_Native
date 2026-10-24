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
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
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
        val p = ((lunarDay - 1) / SYNODIC).coerceIn(0.0, 0.999)
        val k = (1 - cos(2 * PI * p)) / 2 // phần đĩa được chiếu sáng
        if (k > 0.985) drawCircle(MOON_LIT, r, c)
        else if (k > 0.015) {
            val waxing = p < 0.5
            val crescent = cos(2 * PI * p) > 0
            val rx = (r * abs(cos(2 * PI * p))).toFloat()
            val outer = Rect(c.x - r, c.y - r, c.x + r, c.y + r)
            val term = Rect(c.x - rx, c.y - r, c.x + rx, c.y + r)
            val path = Path().apply {
                arcTo(outer, -90f, if (waxing) 180f else -180f, true)
                // đường phân sáng tối: về lại đỉnh qua nửa elip, lồi về phía sáng (lưỡi liềm) hoặc phía tối (khuyết)
                val throughRight = waxing == crescent
                arcTo(term, 90f, if (throughRight) -180f else 180f, false)
                close()
            }
            drawPath(path, MOON_LIT)
        }
        drawCircle(ring, r - .5f, c, style = Stroke(1.dp.toPx()))
    }
}

/** Thẻ lịch âm hôm nay ở tab Tôi: tờ lịch (ngày dương) + ngày âm + trăng của ngày. */
@Composable
fun LunarCard() {
    val cs = MaterialTheme.colorScheme
    val now = LocalDate.now()
    val l = toLunar(now)
    val sp = special(l)
    Row(Modifier.fillMaxWidth().bounceClick { Nav.push(Screen.Lunar) }, verticalAlignment = Alignment.CenterVertically) {
        DateTile(now, Modifier.size(64.dp))
        Column(Modifier.weight(1f).padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                "${l.day} ${l.monthName.replaceFirst("Tháng", "tháng")} âm lịch",
                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text("Năm ${l.yearName} · ngày ${l.dayCanChi}", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (sp != null) EventPill(sp)
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
fun LunarScreen() {
    val cs = MaterialTheme.colorScheme
    val today = remember { LocalDate.now() }
    var month by remember { mutableStateOf(today.withDayOfMonth(1)) }
    var sel by remember { mutableStateOf(today) }
    var drag by remember { mutableFloatStateOf(0f) }
    fun go(delta: Long) { month = month.plusMonths(delta) }
    Column(Modifier.fillMaxSize()) {
        PageHeader("Âm lịch Việt Nam", "Lịch âm", "曆", back = true)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)
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

            DayDetail(sel)
            Spacer(Modifier.height(32.dp))
        }
    }
}

/** Chi tiết ngày đang chọn: ngày âm to + trăng, ba ô Can Chi (năm / tháng / ngày). */
@Composable
private fun DayDetail(d: LocalDate) {
    val cs = MaterialTheme.colorScheme
    val l = toLunar(d)
    val sp = special(l)
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(cs.card).padding(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(solarLine(d), style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant)
                Text(
                    "Ngày ${l.day} ${l.monthName.replaceFirst("Tháng", "tháng")}", style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold, color = cs.onSurface,
                )
                Text("${moonName(l.day)} · năm ${l.yearName}", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                if (sp != null) EventPill(sp)
            }
            MoonPhase(l.day, 64.dp)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CanChiTile("Năm", l.yearName, Modifier.weight(1f))
            CanChiTile("Tháng", l.monthCanChi, Modifier.weight(1f))
            CanChiTile("Ngày", l.dayCanChi, Modifier.weight(1f))
        }
    }
}

@Composable
private fun CanChiTile(label: String, value: String, modifier: Modifier) {
    val cs = MaterialTheme.colorScheme
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).background(cs.primary.copy(alpha = .08f)).padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant, letterSpacing = 1.sp)
        Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = cs.primary, maxLines = 1)
    }
}

@Composable
private fun DayCell(d: LocalDate, today: LocalDate, sel: LocalDate, modifier: Modifier, pick: (LocalDate) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val l = toLunar(d)
    val isToday = d == today
    val isSel = d == sel
    val sunday = d.dayOfWeek.value == 7
    val mark = l.day == 1 || l.day == 15
    val lunarText = if (l.day == 1) "${l.day}/${l.month}${if (l.leap) "*" else ""}" else "${l.day}"
    val fg = if (isToday) cs.onPrimary else if (sunday) cs.error else cs.onSurface
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier.aspectRatio(.82f).padding(2.dp).clip(shape)
            .background(if (isToday) cs.primary else if (isSel) cs.primary.copy(.12f) else Color.Transparent)
            .then(if (isSel && !isToday) Modifier.border(1.5.dp, cs.primary, shape) else Modifier)
            .bounceClick { pick(d) },
        verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("${d.dayOfMonth}", color = fg, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Row(verticalAlignment = Alignment.CenterVertically) {
            // mùng 1 = vòng rỗng (trăng non), rằm = chấm đặc (trăng tròn)
            if (mark) Box(
                Modifier.padding(end = 3.dp).size(5.dp).then(
                    if (l.day == 15) Modifier.background(if (isToday) cs.onPrimary else FULL_DOT, CircleShape)
                    else Modifier.border(1.2.dp, if (isToday) cs.onPrimary else cs.primary, CircleShape),
                ),
            )
            Text(
                lunarText, style = MaterialTheme.typography.labelSmall,
                color = if (isToday) cs.onPrimary.copy(.85f) else if (mark) cs.primary else cs.onSurfaceVariant,
                fontWeight = if (mark) FontWeight.Bold else null,
            )
        }
    }
}
