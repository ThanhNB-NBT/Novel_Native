package com.thanhnb.novelnative

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

private const val WEEKS = 18

/** Thống kê đọc cá nhân. [onBack] != null khi mở dạng hộp thoại toàn màn hình (chưa có Screen riêng); mặc định dùng nút quay lại của ngăn xếp. */
@Composable
fun StatsScreen(onBack: (() -> Unit)? = null) {
    val cs = MaterialTheme.colorScheme
    val uid = Session.user?.id
    val prof = load("profile", uid) { Api.profile() }.res?.getOrNull()
    val stats = load("stats", uid) { Api.readStats() }.res?.getOrNull()
    val shelf = load("library", uid) { Api.library() }.res?.getOrNull().orEmpty()
    val days = remember { ReadLog.days() }
    val today = LocalDate.now()
    Column(Modifier.fillMaxSize()) {
        PageHeader("Thống kê", "Nhật ký đọc", "數", back = onBack == null, plain = onBack != null) {
            if (onBack != null) IconButton(onBack) { Icon(Icons.Rounded.Close, "Đóng") }
        }
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp, 4.dp, 16.dp, 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            PaperCard(Modifier.fillMaxWidth()) { StatsCard(stats?.first ?: 0, stats?.second ?: 0, Api.liveStreak(prof)) }
            if (days.isEmpty()) Empty("Đọc vài chương để bắt đầu có số liệu")
            else {
                PaperCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) { Heatmap(days, today) } }
                PaperCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) { Week(days, today) } }
            }
            val top = Api.topGenres(shelf, 5)
            if (top.isNotEmpty()) PaperCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Eyebrow("Thể loại trong tủ truyện")
                    top.forEach { (g, n) ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(g, Modifier.width(92.dp), style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Box(Modifier.weight(1f).height(10.dp).clip(RoundedCornerShape(5.dp)).background(cs.surfaceVariant.copy(.5f))) {
                                Box(Modifier.fillMaxWidth(n / top[0].second.toFloat()).fillMaxHeight().clip(RoundedCornerShape(5.dp)).background(cs.primary))
                            }
                            Text("$n", Modifier.width(32.dp), style = monoStyle(), textAlign = TextAlign.End)
                        }
                    }
                }
            }
        }
    }
}

/** Mức đậm 0..4 theo số chương trong ngày. */
private fun level(n: Int) = when { n <= 0 -> 0; n <= 2 -> 1; n <= 5 -> 2; n <= 10 -> 3; else -> 4 }

@Composable
private fun levelColor(l: Int): Color {
    val cs = MaterialTheme.colorScheme
    return if (l == 0) cs.surfaceVariant.copy(.55f) else cs.primary.copy(alpha = 0.25f + 0.25f * (l - 1))
}

/** Lưới ô vuông kiểu đóng góp: cột = tuần (thứ Hai đầu tuần), hàng = thứ trong tuần. */
@Composable
private fun Heatmap(days: Map<LocalDate, Int>, today: LocalDate) {
    val start = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).minusWeeks(WEEKS - 1L)
    val shown = days.filterKeys { !it.isBefore(start) }
    val colors = (0..4).map { levelColor(it) }
    Eyebrow("$WEEKS tuần gần nhất")
    Canvas(
        Modifier.fillMaxWidth().padding(top = 10.dp).aspectRatio(WEEKS / 7f)
            .semantics { contentDescription = "Lịch đọc $WEEKS tuần gần nhất: ${shown.values.sum()} chương trong ${shown.size} ngày" },
    ) {
        val gap = 3.dp.toPx()
        val cell = (size.width - gap * (WEEKS - 1)) / WEEKS
        for (w in 0 until WEEKS) for (d in 0 until 7) {
            val day = start.plusDays(w * 7L + d)
            if (day.isAfter(today)) continue
            drawRoundRect(colors[level(days[day] ?: 0)], Offset(w * (cell + gap), d * (cell + gap)), Size(cell, cell), CornerRadius(cell * .22f))
        }
    }
    Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Ít", style = MaterialTheme.typography.labelSmall)
        (0..4).forEach { Box(Modifier.size(12.dp).clip(RoundedCornerShape(3.dp)).background(levelColor(it))) }
        Text("Nhiều", style = MaterialTheme.typography.labelSmall)
    }
}

/** Cột nhỏ số chương 7 ngày qua. */
@Composable
private fun Week(days: Map<LocalDate, Int>, today: LocalDate) {
    val cs = MaterialTheme.colorScheme
    val week = (6 downTo 0).map { today.minusDays(it.toLong()) }
    val max = week.maxOf { days[it] ?: 0 }.coerceAtLeast(1)
    Eyebrow("7 ngày qua · ${week.sumOf { days[it] ?: 0 }} chương")
    Row(
        Modifier.fillMaxWidth().padding(top = 10.dp).semantics { contentDescription = "Số chương 7 ngày qua: " + week.joinToString(", ") { "${days[it] ?: 0}" } },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        week.forEach { d ->
            val n = days[d] ?: 0
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("$n", style = monoStyle(11))
                Box(Modifier.height(56.dp).fillMaxWidth(), Alignment.BottomCenter) {
                    Box(Modifier.fillMaxWidth(.6f).height((56 * n / max).coerceAtLeast(3).dp).clip(RoundedCornerShape(4.dp)).background(if (n > 0) cs.primary else cs.surfaceVariant))
                }
                Text(if (d.dayOfWeek == DayOfWeek.SUNDAY) "CN" else "T${d.dayOfWeek.value + 1}", Modifier.padding(top = 4.dp), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
