package com.thanhnb.novelnative

import android.app.ActivityManager
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.display.DisplayManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.os.Debug
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.Process
import android.os.StatFs
import android.os.SystemClock
import android.net.TrafficStats
import android.view.FrameMetrics
import android.view.Display
import android.view.Window
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

/** Một nhóm thông số: tiêu đề + các dòng (tên → giá trị). */
private class MGroup(val title: String, val rows: List<Pair<String, String>>)

private class Sample(val cpuApp: Float, val ramMb: Float, val netKbs: Float, val groups: List<MGroup>)

/** Đếm khung hình thật sự được vẽ (không ép vẽ) — chỉ có số khi màn hình đang chuyển động/cuộn. */
private class Frames {
    private var n = 0; private var jank = 0; private var worst = 0f; private var sum = 0f
    @Synchronized fun add(ms: Float, budget: Float) { n++; sum += ms; if (ms > worst) worst = ms; if (ms > budget) jank++ }
    @Synchronized fun take(): String? = if (n == 0) null else
        "$n khung · TB ${"%.1f".format(sum / n)} ms · tệ nhất ${"%.1f".format(worst)} ms · giật $jank".also { n = 0; jank = 0; worst = 0f; sum = 0f }
}

private fun fmtBytes(b: Long): String = when {
    b >= 1L shl 30 -> "%.2f GB".format(b / 1073741824.0)
    b >= 1L shl 20 -> "%.1f MB".format(b / 1048576.0)
    b >= 1L shl 10 -> "%.0f KB".format(b / 1024.0)
    else -> "$b B"
}

private fun fmtDur(ms: Long): String {
    val s = ms / 1000
    return when { s >= 86400 -> "${s / 86400}n ${s % 86400 / 3600}g"; s >= 3600 -> "${s / 3600}g ${s % 3600 / 60}p"; else -> "${s / 60}p ${s % 60}s" }
}

private fun dirSize(f: File?): Long = when {
    f == null || !f.exists() -> 0
    f.isFile -> f.length()
    else -> f.listFiles()?.sumOf(::dirSize) ?: 0
}

private val thermalNames = listOf("Bình thường", "Hơi nóng", "Vừa", "Nóng", "Rất nóng", "Nguy hiểm", "Tắt máy")

/** Lấy mẫu mọi số đo mà app thường (không cần quyền nguy hiểm) đọc được; giữ trạng thái trước đó để tính tốc độ. */
private class Sampler(val ctx: Context) {
    private val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    private val bm = ctx.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
    private val pm = ctx.getSystemService(Context.POWER_SERVICE) as PowerManager
    private val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val cores = Runtime.getRuntime().availableProcessors()
    private var lastCpu = Process.getElapsedCpuTime(); private var lastWall = SystemClock.elapsedRealtime()
    private var lastRx = TrafficStats.getTotalRxBytes(); private var lastTx = TrafficStats.getTotalTxBytes()
    private var lastSys: Pair<Long, Long>? = null // (tổng, rảnh) của /proc/stat
    val frameSink = Frames()
    var frames = "—"
    var storage = ""

    private fun sysCpu(): Float? = runCatching {
        val p = File("/proc/stat").bufferedReader().use { it.readLine() }.split(Regex("\\s+")).drop(1).map { it.toLong() }
        val total = p.sum(); val idle = p[3] + p.getOrElse(4) { 0 }
        val prev = lastSys; lastSys = total to idle
        if (prev == null || total == prev.first) null else 100f * (1 - (idle - prev.second).toFloat() / (total - prev.first))
    }.getOrNull()

    private fun freqs(): String? = (0 until cores).mapNotNull { i ->
        runCatching { File("/sys/devices/system/cpu/cpu$i/cpufreq/scaling_cur_freq").readText().trim().toLong() / 1000 }.getOrNull()
    }.takeIf { it.isNotEmpty() }?.joinToString(" · ")?.plus(" MHz")

    fun read(): Sample {
        val now = SystemClock.elapsedRealtime(); val cpu = Process.getElapsedCpuTime()
        val dt = (now - lastWall).coerceAtLeast(1)
        val cpuApp = 100f * (cpu - lastCpu) / dt / cores // % tổng sức CPU của máy
        lastCpu = cpu; lastWall = now

        val mi = ActivityManager.MemoryInfo().also(am::getMemoryInfo)
        val pmi = am.getProcessMemoryInfo(intArrayOf(Process.myPid())).first()
        val rt = Runtime.getRuntime()
        val pssMb = pmi.totalPss / 1024f
        val used = mi.totalMem - mi.availMem

        val rx = TrafficStats.getTotalRxBytes(); val tx = TrafficStats.getTotalTxBytes()
        val down = (rx - lastRx) * 1000f / dt; val up = (tx - lastTx) * 1000f / dt
        lastRx = rx; lastTx = tx
        val uid = Process.myUid()
        val caps = cm.getNetworkCapabilities(cm.activeNetwork)
        val link = when {
            caps == null -> "Không có mạng"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Dữ liệu di động"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
            else -> "Khác"
        }

        val b = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = b?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = b?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val status = when (b?.getIntExtra(BatteryManager.EXTRA_STATUS, 0)) {
            BatteryManager.BATTERY_STATUS_CHARGING -> "Đang sạc"; BatteryManager.BATTERY_STATUS_DISCHARGING -> "Đang xả"
            BatteryManager.BATTERY_STATUS_FULL -> "Đầy"; BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "Không sạc"; else -> "?"
        }
        val plug = when (b?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) { 1 -> "sạc AC"; 2 -> "USB"; 4 -> "không dây"; 0 -> "không cắm"; else -> "khác" }
        val health = when (b?.getIntExtra(BatteryManager.EXTRA_HEALTH, 0)) {
            BatteryManager.BATTERY_HEALTH_GOOD -> "Tốt"; BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Quá nóng"; BatteryManager.BATTERY_HEALTH_DEAD -> "Hỏng"
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Quá áp"; BatteryManager.BATTERY_HEALTH_COLD -> "Quá lạnh"; else -> "?"
        }
        val volt = (b?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0) / 1000f
        val ma = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW) / 1000 // dấu tuỳ hãng
        val cycles = b?.getIntExtra("android.os.extra.CYCLE_COUNT", -1) ?: -1

        val thermal = if (Build.VERSION.SDK_INT >= 29) thermalNames.getOrElse(pm.currentThermalStatus) { "?" } else "—"
        val headroom = if (Build.VERSION.SDK_INT >= 30) pm.getThermalHeadroom(10).takeIf { !it.isNaN() }?.let { "%.2f (1.0 = bắt đầu bóp xung)".format(it) } else null

        val fs = StatFs(Environment.getDataDirectory().path)
        val dm = ctx.resources.displayMetrics
        val hz = ctx.getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY)?.refreshRate ?: 0f

        return Sample(cpuApp, pssMb, down / 1024,
            listOf(
                MGroup("App này", listOf(
                    "CPU app" to "%.1f%% tổng máy · %.0f%% một nhân".format(Locale.US, cpuApp, cpuApp * cores),
                    "RAM (PSS)" to "%.0f MB".format(pssMb),
                    "Java heap" to "${fmtBytes(rt.totalMemory() - rt.freeMemory())} / ${fmtBytes(rt.maxMemory())}",
                    "Native heap" to fmtBytes(Debug.getNativeHeapAllocatedSize()),
                    "Đồ hoạ" to (pmi.getMemoryStat("summary.graphics")?.toLongOrNull()?.let { fmtBytes(it * 1024) } ?: "—"),
                    "Luồng · fd mở" to "${File("/proc/self/status").readLines().firstOrNull { it.startsWith("Threads") }?.substringAfter(':')?.trim() ?: "?"} · ${File("/proc/self/fd").list()?.size ?: "?"}",
                    "Chạy được" to fmtDur(now - Process.getStartElapsedRealtime()),
                    "CPU đã dùng" to fmtDur(cpu.toLong()),
                    "Mạng app (từ khi bật máy)" to "↓ ${fmtBytes(TrafficStats.getUidRxBytes(uid))} · ↑ ${fmtBytes(TrafficStats.getUidTxBytes(uid))}",
                    "Dung lượng app" to storage.ifEmpty { "đang tính…" },
                    "Vẽ khung hình" to frames,
                )),
                MGroup("CPU toàn máy", listOfNotNull(
                    "Số nhân" to "$cores",
                    "Tải toàn máy" to (sysCpu()?.let { "%.0f%%".format(it) } ?: "Android chặn app đọc"),
                    freqs()?.let { "Xung từng nhân" to it },
                    "Kiến trúc" to Build.SUPPORTED_ABIS.joinToString(", "),
                    "Nhiệt (hệ thống)" to thermal,
                    headroom?.let { "Dư địa nhiệt" to it },
                )),
                MGroup("RAM máy", listOf(
                    "Tổng" to fmtBytes(mi.totalMem),
                    "Đang dùng" to "${fmtBytes(used)} (${100 * used / mi.totalMem}%)",
                    "Còn trống" to fmtBytes(mi.availMem),
                    "Ngưỡng báo thiếu" to fmtBytes(mi.threshold),
                    "Trạng thái" to if (mi.lowMemory) "THIẾU RAM" else "Ổn",
                    "Giới hạn heap app" to "${am.memoryClass} MB (tối đa ${am.largeMemoryClass} MB)",
                )),
                MGroup("Pin", listOf(
                    "Mức" to "${100 * level / scale}%",
                    "Trạng thái" to "$status · $plug",
                    "Dòng" to "$ma mA (âm = đang xả trên đa số máy)",
                    "Công suất ≈" to "%.2f W".format(kotlin.math.abs(ma) * volt / 1000),
                    "Điện áp" to "%.2f V".format(volt),
                    "Nhiệt độ" to "%.1f °C".format((b?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10f),
                    "Sức khoẻ" to health,
                    "Công nghệ" to (b?.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY) ?: "?"),
                    "Điện tích còn" to "${bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER) / 1000} mAh",
                    "Số lần sạc" to if (cycles >= 0) "$cycles" else "máy không báo",
                    "Tiết kiệm pin" to if (pm.isPowerSaveMode) "Bật" else "Tắt",
                )),
                MGroup("Mạng", listOf(
                    "Kết nối" to link,
                    "Băng thông báo cáo" to (caps?.let { "↓ ${it.linkDownstreamBandwidthKbps / 1000} · ↑ ${it.linkUpstreamBandwidthKbps / 1000} Mbps" } ?: "—"),
                    "Tốc độ tức thời" to "↓ ${fmtBytes(down.toLong())}/s · ↑ ${fmtBytes(up.toLong())}/s",
                    "Tổng từ khi bật máy" to "↓ ${fmtBytes(rx)} · ↑ ${fmtBytes(tx)}",
                    "Mạng tính cước" to if (cm.isActiveNetworkMetered) "Có" else "Không",
                )),
                MGroup("Bộ nhớ trong", listOf(
                    "Tổng" to fmtBytes(fs.totalBytes),
                    "Còn trống" to "${fmtBytes(fs.availableBytes)} (${100 * fs.availableBytes / fs.totalBytes}%)",
                )),
                MGroup("Thiết bị", listOf(
                    "Máy" to "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}",
                    "Chip" to (if (Build.VERSION.SDK_INT >= 31) "${Build.SOC_MANUFACTURER} ${Build.SOC_MODEL}" else Build.HARDWARE),
                    "Android" to "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) · vá ${Build.VERSION.SECURITY_PATCH}",
                    "Kernel" to (System.getProperty("os.version") ?: "?"),
                    "Màn hình" to "${dm.widthPixels}×${dm.heightPixels} · ${dm.densityDpi} dpi · %.0f Hz".format(hz),
                    "Bật máy được" to fmtDur(now),
                    "Máy RAM thấp" to if (am.isLowRamDevice) "Có" else "Không",
                )),
            ),
        )
    }
}

@Composable
fun MonitorScreen() {
    val view = LocalView.current
    val sampler = remember { Sampler(view.context.applicationContext) }
    var cur by remember { mutableStateOf<Sample?>(null) }
    val cpuH = remember { mutableStateListOf<Float>() }
    val ramH = remember { mutableStateListOf<Float>() }
    val netH = remember { mutableStateListOf<Float>() }

    DisposableEffect(view) {
        val w = (view.context as Activity).window
        val budget = 1000f / (view.display?.refreshRate ?: 60f)
        val l = Window.OnFrameMetricsAvailableListener { _, m, _ -> sampler.frameSink.add(m.getMetric(FrameMetrics.TOTAL_DURATION) / 1e6f, budget) }
        val h = Handler(Looper.getMainLooper())
        w.addOnFrameMetricsAvailableListener(l, h)
        onDispose { w.removeOnFrameMetricsAvailableListener(l) }
    }
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            sampler.storage = "files ${fmtBytes(dirSize(sampler.ctx.filesDir))} · cache ${fmtBytes(dirSize(sampler.ctx.cacheDir))}"
        }
        while (true) {
            sampler.frames = sampler.frameSink.take() ?: "không có khung mới (màn đứng yên) — cuộn để đo"
            val s = withContext(Dispatchers.Default) { sampler.read() }
            cur = s
            fun <T> MutableList<T>.push(x: T) { add(x); if (size > SLOTS) removeAt(0) }
            cpuH.push(s.cpuApp); ramH.push(s.ramMb); netH.push(s.netKbs)
            delay(1000)
        }
    }

    Column(Modifier.fillMaxSize()) {
        PageHeader("CPU · RAM · pin · mạng · nhiệt", "Theo dõi máy", "測", back = true)
        val s = cur
        if (s == null) { CenterSpinner(); return@Column }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 0.dp, 16.dp, 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { StatusCard(s) }
            item { StreamCard("CPU app", "%.1f%%".format(s.cpuApp), cpuH, { "%.1f%%".format(it) }, 10f, MaterialTheme.colorScheme.primary, Modifier.fillMaxWidth(), 72.dp) }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StreamCard("RAM app", "%.0f MB".format(s.ramMb), ramH, { "%.0f MB".format(it) }, 64f, MaterialTheme.colorScheme.tertiary, Modifier.weight(1f))
                    StreamCard("Mạng ↓", "%.0f KB/s".format(s.netKbs), netH, { "%.0f KB/s".format(it) }, 32f, MaterialTheme.colorScheme.secondary, Modifier.weight(1f))
                }
            }
            items(s.groups) { g ->
                val cs = MaterialTheme.colorScheme
                PaperCard {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                        Text(g.title, style = MaterialTheme.typography.titleSmall, color = cs.primary, modifier = Modifier.padding(bottom = 4.dp))
                        g.rows.forEachIndexed { i, (k, v) ->
                            if (i > 0) HorizontalDivider(color = cs.outlineVariant.copy(alpha = .35f))
                            Row(Modifier.padding(vertical = 8.dp), verticalAlignment = androidx.compose.ui.Alignment.Top) {
                                Text(k, Modifier.weight(.42f), fontSize = 13.sp, color = cs.onSurfaceVariant)
                                Text(v, Modifier.weight(.58f), fontSize = 13.sp, fontWeight = FontWeight.Medium, color = cs.onSurface, textAlign = TextAlign.End)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Tình trạng một dòng: màu + chữ (không chỉ màu) theo CPU app, kèm số liệu chính. */
@Composable
private fun StatusCard(s: Sample) {
    val cs = MaterialTheme.colorScheme
    val (label, color) = when {
        s.cpuApp < 35f -> "App đang chạy nhẹ" to Color(0xFF2E9E6B)
        s.cpuApp < 70f -> "App đang bận" to Color(0xFFD9962B)
        else -> "App đang quá tải" to cs.error
    }
    PaperCard(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Box(Modifier.size(12.dp).background(color, CircleShape))
            Column(Modifier.padding(start = 12.dp)) {
                Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    "CPU %.1f%% · RAM %.0f MB · mạng %.0f KB/s".format(s.cpuApp, s.ramMb, s.netKbs),
                    style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Biểu đồ dòng: số hiện tại to nhất, vùng diện tích gradient trên trục bắt đầu từ 0, lưới mảnh, chấm ở điểm mới nhất,
 * chân thẻ ghi trung bình/đỉnh của 60 giây. 60 ô cố định nên đường chạy dần từ phải sang trái như đồng hồ.
 * [floor]: trần tối thiểu của trục (tránh nhiễu nhỏ bị phóng thành đỉnh cao).
 */
@Composable
private fun StreamCard(
    label: String, value: String, h: List<Float>, fmt: (Float) -> String, floor: Float,
    tint: Color, modifier: Modifier, chartH: androidx.compose.ui.unit.Dp = 56.dp,
) {
    val cs = MaterialTheme.colorScheme
    PaperCard(modifier) {
        Column(Modifier.padding(14.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 1)
            val grid = cs.onSurface.copy(alpha = .08f)
            Canvas(Modifier.fillMaxWidth().height(chartH).padding(top = 8.dp)) {
                for (f in listOf(0f, .5f, 1f)) drawLine(grid, Offset(0f, size.height * f), Offset(size.width, size.height * f), 1.dp.toPx())
                if (h.size < 2) return@Canvas
                val top = maxOf(h.max() * 1.2f, floor)
                fun x(i: Int) = size.width * (SLOTS - h.size + i) / (SLOTS - 1f)
                fun y(v: Float) = size.height * (1f - (v / top).coerceIn(0f, 1f))
                val line = Path().apply { h.forEachIndexed { i, v -> if (i == 0) moveTo(x(i), y(v)) else lineTo(x(i), y(v)) } }
                val area = Path().apply { addPath(line); lineTo(x(h.lastIndex), size.height); lineTo(x(0), size.height); close() }
                drawPath(area, Brush.verticalGradient(listOf(tint.copy(alpha = .32f), tint.copy(alpha = 0f))))
                drawPath(line, tint, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                val end = Offset(x(h.lastIndex), y(h.last()))
                drawCircle(tint.copy(alpha = .25f), 7.dp.toPx(), end)
                drawCircle(tint, 3.5.dp.toPx(), end)
            }
            if (h.isNotEmpty()) Text(
                "TB ${fmt(h.average().toFloat())} · đỉnh ${fmt(h.max())}",
                Modifier.padding(top = 6.dp), style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant, maxLines = 1,
            )
        }
    }
}

private const val SLOTS = 60

/** Màu theo ngưỡng dùng chung cho đồng hồ: dưới 70% = màu gốc, 70–85% = cảnh báo (vàng), từ 85% = nguy hiểm. */
@Composable
fun levelColor(frac: Float, base: Color): Color = when {
    frac >= .85f -> MaterialTheme.colorScheme.error
    frac >= .7f -> Color(0xFFD9962B)
    else -> base
}

/** Viên thuốc trạng thái: nền nhạt cùng tông với chữ, nên luôn có chữ đi kèm màu (không chỉ dựa vào màu). */
@Composable
fun StatusPill(text: String, color: Color, modifier: Modifier = Modifier) = Text(
    text, modifier.background(color.copy(alpha = .14f), androidx.compose.foundation.shape.RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 3.dp),
    style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = color, maxLines = 1,
)

/** Đồng hồ vòng: số phần trăm ở giữa, nhãn và chi tiết bên dưới. [frac] null = chưa có số liệu (vòng trống, dấu —). */
@Composable
fun RingGauge(label: String, frac: Float?, sub: String?, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    val tint = levelColor(frac ?: 0f, cs.primary)
    Column(modifier, horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
        Box(Modifier.size(84.dp), androidx.compose.ui.Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val w = 9.dp.toPx(); val inset = w / 2
                val arc = androidx.compose.ui.geometry.Size(size.width - w, size.height - w)
                drawArc(cs.onSurface.copy(alpha = .08f), 0f, 360f, false, Offset(inset, inset), arc, style = Stroke(w))
                if (frac != null) drawArc(tint, -90f, 360f * frac.coerceIn(0f, 1f), false, Offset(inset, inset), arc, style = Stroke(w, cap = StrokeCap.Round))
            }
            Text(if (frac == null) "—" else "%.0f%%".format(frac * 100), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        Text(label, Modifier.padding(top = 6.dp), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        if (sub != null) Text(sub, style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant, maxLines = 1)
    }
}
