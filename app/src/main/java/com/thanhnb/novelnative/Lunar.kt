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

/** Khung lịch âm hôm nay ở tab Tôi. */
@Composable
fun LunarCard() {
    val cs = MaterialTheme.colorScheme
    val now = LocalDate.now()
    val l = toLunar(now)
    val sp = special(l)
    val solar = "%s, %02d/%02d/%d".format(WEEKDAYS[now.dayOfWeek.value - 1], now.dayOfMonth, now.monthValue, now.year)
    Column(Modifier.fillMaxWidth().bounceClick { Nav.push(Screen.Lunar) }.padding(vertical = 4.dp)) {
        Text("${l.monthName} năm ${l.yearName} · ngày ${l.day}${if (l.leap) "*" else ""} âm lịch", style = MaterialTheme.typography.bodyMedium, color = cs.onSurface)
        Text(
            if (sp == null) solar else "$sp · $solar", Modifier.padding(top = 2.dp), style = MaterialTheme.typography.bodySmall,
            color = if (sp == null) androidx.compose.ui.graphics.Color.Unspecified else cs.primary,
            fontWeight = if (sp == null) null else FontWeight.SemiBold,
        )
    }
}


private fun solarLine(d: LocalDate) = "%s, %02d/%02d/%d".format(WEEKDAYS[d.dayOfWeek.value - 1], d.dayOfMonth, d.monthValue, d.year)

/** Lịch tháng: lưới ngày dương, ngày âm nhỏ dưới mỗi ô; vuốt ngang hoặc mũi tên đổi tháng. */
@Composable
fun LunarScreen() {
    val cs = MaterialTheme.colorScheme
    val today = remember { LocalDate.now() }
    var month by remember { mutableStateOf(today.withDayOfMonth(1)) }
    var sel by remember { mutableStateOf(today) }
    val lead = month.dayOfWeek.value - 1 // tuần bắt đầu Thứ Hai
    val days = month.lengthOfMonth()
    val cells = (lead + days + 6) / 7 * 7
    var drag by remember { mutableFloatStateOf(0f) }
    Column(Modifier.fillMaxSize()) {
        PageHeader("Âm lịch Việt Nam", "Lịch âm", "曆", back = true)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp)
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(onDragEnd = {
                        if (kotlin.math.abs(drag) > 80f) month = month.plusMonths(if (drag < 0) 1 else -1)
                        drag = 0f
                    }) { _, d -> drag += d }
                },
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton({ month = month.minusMonths(1) }) { Icon(Icons.Rounded.ChevronLeft, "Tháng trước") }
                Text("Tháng ${month.monthValue}, ${month.year}", Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.titleLarge)
                IconButton({ month = month.plusMonths(1) }) { Icon(Icons.Rounded.ChevronRight, "Tháng sau") }
            }
            if (month.year != today.year || month.month != today.month) TextButton(
                { month = today.withDayOfMonth(1); sel = today }, Modifier.align(Alignment.CenterHorizontally),
            ) { Text("Về hôm nay") }
            Row(Modifier.padding(vertical = 6.dp)) {
                listOf("T2", "T3", "T4", "T5", "T6", "T7", "CN").forEachIndexed { i, w ->
                    Text(w, Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelMedium, color = if (i == 6) cs.error else cs.onSurfaceVariant)
                }
            }
            for (r in 0 until cells / 7) Row {
                for (c in 0 until 7) {
                    val i = r * 7 + c
                    if (i < lead || i >= lead + days) Spacer(Modifier.weight(1f).aspectRatio(.82f))
                    else DayCell(month.withDayOfMonth(i - lead + 1), today, sel, Modifier.weight(1f)) { sel = it }
                }
            }
            Spacer(Modifier.height(16.dp))
            val l = toLunar(sel)
            val sp = special(l)
            Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(solarLine(sel), style = MaterialTheme.typography.titleMedium)
                if (sp != null) Text(sp, style = MaterialTheme.typography.labelLarge, color = cs.primary)
                LunarRow("Âm lịch", "Ngày ${l.day} ${l.monthName.replaceFirst("Tháng", "tháng")}")
                LunarRow("Năm", l.yearName)
                LunarRow("Tháng", l.monthCanChi)
                LunarRow("Ngày", l.dayCanChi)
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun LunarRow(k: String, v: String) = Row {
    Text(k, Modifier.width(92.dp), style = MaterialTheme.typography.bodySmall)
    Text(v, style = MaterialTheme.typography.bodyMedium)
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
            .background(if (isToday) cs.primary else if (isSel) cs.primary.copy(.12f) else androidx.compose.ui.graphics.Color.Transparent)
            .bounceClick { pick(d) },
        verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("${d.dayOfMonth}", color = fg, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(
            lunarText, style = MaterialTheme.typography.labelSmall,
            color = if (isToday) cs.onPrimary.copy(.85f) else if (mark) cs.primary else cs.onSurfaceVariant,
            fontWeight = if (mark) FontWeight.Bold else null,
        )
    }
}
