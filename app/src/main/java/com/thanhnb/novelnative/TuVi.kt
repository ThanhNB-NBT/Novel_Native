package com.thanhnb.novelnative

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** Thông tin sinh để lập lá số. [gio] là timeIndex của iztro (0 = Tý sớm … 12 = Tý muộn), [gioi] "nam"/"nu". */
data class HoSoSinh(val ngay: LocalDate, val gio: Int, val gioi: String)

private val GIO = listOf(
    "Tý sớm" to "0h–1h", "Sửu" to "1h–3h", "Dần" to "3h–5h", "Mão" to "5h–7h", "Thìn" to "7h–9h", "Tỵ" to "9h–11h", "Ngọ" to "11h–13h",
    "Mùi" to "13h–15h", "Thân" to "15h–17h", "Dậu" to "17h–19h", "Tuất" to "19h–21h", "Hợi" to "21h–23h", "Tý muộn" to "23h–24h",
)

private fun hoaColor(h: String) = when (h) {
    "Lộc" -> Color(0xFF2E9D5B)
    "Quyền" -> Color(0xFF8A5A2B)
    "Khoa" -> Color(0xFF2F7BE0)
    else -> Color(0xFFE5532D) // Kỵ
}

private fun JSONArray.objList() = List(length()) { getJSONObject(it) }
private fun JSONArray.strList() = List(length()) { getString(it) }
private fun fmtDate(d: LocalDate) = "%02d/%02d/%d".format(d.dayOfMonth, d.monthValue, d.year)

private const val HO_SO = "tuvi-hoso"

/** Thẻ tử vi trên trang Lịch âm, theo ngày đang chọn [d]. Lỗi chỉ nằm trong thẻ, lịch vẫn chạy. */
@Composable
fun TuViCard(d: LocalDate) {
    val cs = MaterialTheme.colorScheme
    val uid = Session.user?.id
    var sheet by remember { mutableStateOf(false) }
    var ver by remember { mutableIntStateOf(0) } // tăng sau khi lưu → khoá mới → tải lại hồ sơ
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(cs.card).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Eyebrow("Tử vi")
        if (uid == null) {
            Text("Đăng nhập để lưu ngày giờ sinh và xem tử vi mỗi ngày", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
            FilledTonalButton({ Nav.push(Screen.Login) }) { Text("Đăng nhập") }
            return@Column
        }
        val hs = load(HO_SO, uid, ver) { Api.tuViHoSo() }
        val r = hs.res
        when {
            r == null -> CardSkeleton()
            r.isFailure -> Retry("Không tải được thông tin sinh (cần mạng)", hs.reload)
            r.getOrNull() == null -> {
                Text("Nhập ngày giờ sinh một lần để xem Tứ Hóa của từng ngày và lá số 12 cung.", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                Button({ sheet = true }) { Text("Nhập ngày giờ sinh") }
            }
            else -> DayFortune(r.getOrThrow()!!, d)
        }
    }
    if (sheet) {
        val scope = rememberCoroutineScope()
        var err by remember { mutableStateOf<String?>(null) }
        BirthSheet(null, "Thông tin sinh của bạn", "Lưu", err, { sheet = false }) { h ->
            scope.launch {
                runCatching { Api.luuTuViHoSo(h) }
                    .onSuccess { evictMemo { it.firstOrNull() == HO_SO }; ver++; sheet = false }
                    .onFailure { err = "Chưa lưu được, thử lại sau" }
            }
        }
    }
}

@Composable
private fun DayFortune(h: HoSoSinh, d: LocalDate) {
    val cs = MaterialTheme.colorScheme
    val f = load("tuvi", h, d) { Api.tuVi(h, d) }
    val r = f.res ?: return CardSkeleton()
    val j = r.getOrNull() ?: return Retry("Không tải được tử vi (cần mạng)", f.reload)
    val ngay = j.optJSONObject("ngay")
    if (ngay == null) {
        Text("Ngày này trước ngày sinh", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
    } else {
        Text("Ngày ${ngay.getString("can_chi")}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
        ngay.getJSONArray("tu_hoa").objList().forEach { t ->
            val hoa = t.getString("hoa")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                HoaBadge(hoa)
                Column(Modifier.weight(1f)) {
                    Text("${t.getString("sao")} → cung ${t.getString("cung")}", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    Text(t.getString("loi"), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Mang tính tham khảo", Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
        TextButton({ Nav.push(Screen.LaSo(h, mine = true)) }) { Text("Xem lá số") }
    }
}

@Composable
private fun HoaBadge(hoa: String, size: Dp = 34.dp) = Box(
    Modifier.size(size * 1.4f, size).clip(RoundedCornerShape(10.dp)).background(hoaColor(hoa).copy(alpha = .14f)), // đủ rộng cho "Quyền"
    contentAlignment = Alignment.Center,
) { Text(hoa, color = MaterialTheme.colorScheme.legible(hoaColor(hoa)), fontWeight = FontWeight.Bold, fontSize = if (size < 30.dp) 9.sp else 11.sp, maxLines = 1, softWrap = false) }

@Composable
private fun CardSkeleton() = Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    val c = MaterialTheme.colorScheme.onSurface.copy(alpha = .08f)
    repeat(3) { Box(Modifier.fillMaxWidth(if (it == 0) .5f else 1f).height(16.dp).clip(RoundedCornerShape(8.dp)).background(c)) }
}

@Composable
private fun Retry(msg: String, retry: () -> Unit) = Row(verticalAlignment = Alignment.CenterVertically) {
    Text(msg, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
    TextButton(retry) { Text("Thử lại") }
}

/** Form ngày giờ sinh. Dùng cho cả lưu hồ sơ của mình lẫn xem lá số người khác (không lưu). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BirthSheet(init: HoSoSinh?, title: String, confirm: String, err: String?, close: () -> Unit, done: (HoSoSinh) -> Unit) {
    val cs = MaterialTheme.colorScheme
    var ngay by remember { mutableStateOf(init?.ngay ?: LocalDate.of(2000, 1, 1)) }
    var gio by remember { mutableStateOf(init?.gio) }
    var gioi by remember { mutableStateOf(init?.gioi) }
    var picking by remember { mutableStateOf(false) }
    ModalBottomSheet(close, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = cs.card) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Eyebrow("Ngày sinh (dương lịch)")
            OutlinedButton({ picking = true }, Modifier.fillMaxWidth()) { Text(fmtDate(ngay)) }
            val l = remember(ngay) { toLunar(ngay) }
            Text("Âm lịch: ngày ${l.day} ${l.monthName.lowercase()} năm ${l.yearName}", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            Eyebrow("Giờ sinh")
            GIO.withIndex().chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { (i, g) ->
                        FilterChip(gio == i, { gio = i }, {
                            Column { Text(g.first, fontWeight = FontWeight.SemiBold); Text(g.second, fontSize = 11.sp) }
                        }, Modifier.weight(1f))
                    }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
            Eyebrow("Giới tính")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(gioi == "nam", { gioi = "nam" }, { Text("Nam") })
                FilterChip(gioi == "nu", { gioi = "nu" }, { Text("Nữ") })
            }
            err?.let { Text(it, color = cs.error, style = MaterialTheme.typography.bodySmall) }
            Button({ done(HoSoSinh(ngay, gio!!, gioi!!)) }, Modifier.fillMaxWidth(), enabled = gio != null && gioi != null) { Text(confirm) }
        }
    }
    if (picking) {
        val ps = rememberDatePickerState(ngay.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(), yearRange = 1900..2100)
        DatePickerDialog({ picking = false }, {
            TextButton({
                ps.selectedDateMillis?.let { ngay = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                picking = false
            }) { Text("Chọn") }
        }, dismissButton = { TextButton({ picking = false }) { Text("Huỷ") } }) { DatePicker(ps) }
    }
}

// Vị trí 12 cung trên lưới 4×4 theo kiểu truyền thống: Tỵ ở góc trên trái, đi theo chiều kim đồng hồ.
private val TOP = listOf(5, 6, 7, 8)        // Tỵ Ngọ Mùi Thân
private val BOTTOM = listOf(2, 1, 0, 11)    // Dần Sửu Tý Hợi
private val CELL = 112.dp

/** Màn lá số 12 cung. [mine] = hồ sơ của chính mình (cho sửa); lá số người khác thì chỉ xem. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LaSoScreen(h: HoSoSinh, mine: Boolean) {
    val cs = MaterialTheme.colorScheme
    val today = remember { LocalDate.now() }
    val f = load("tuvi", h, today) { Api.tuVi(h, today) }
    var open by remember { mutableStateOf<JSONObject?>(null) }
    var sheet by remember { mutableStateOf(0) } // 1 = sửa của mình, 2 = xem lá số khác
    Column(Modifier.fillMaxSize()) {
        PageHeader(if (mine) "Lá số của bạn" else "Lá số tham khảo", "Lá số", "命", back = true)
        val r = f.res
        val j = r?.getOrNull()
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            when {
                r == null -> CardSkeleton()
                j == null -> Retry("Không tải được lá số (cần mạng)", f.reload)
                else -> {
                    val ls = j.getJSONObject("la_so")
                    val cung = ls.getJSONArray("cung").objList()
                    @Composable fun C(b: Int, m: Modifier) = PalaceCell(cung[b], ls.getString("menh") == cung[b].getString("chi"), m) { open = cung[b] }
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { TOP.forEach { C(it, Modifier.weight(1f)) } }
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) { C(4, Modifier); C(3, Modifier) }
                            Center(h, ls, Modifier.weight(2f).height(CELL * 2 + 4.dp))
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) { C(9, Modifier); C(10, Modifier) }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { BOTTOM.forEach { C(it, Modifier.weight(1f)) } }
                    }
                    Text("Bấm vào một cung để xem đủ các sao.", style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
                    Patterns(j.getJSONArray("cach_cuc").objList())
                    PhuThe(j.getJSONObject("phu_the"))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (mine) OutlinedButton({ sheet = 1 }, Modifier.weight(1f)) { Text("Sửa thông tin sinh") }
                Button({ sheet = 2 }, Modifier.weight(1f)) { Text("Xem lá số khác") }
            }
            Text("Lá số tính theo iztro; lời giải mang tính tham khảo.", style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
            Spacer(Modifier.height(32.dp))
        }
    }
    open?.let { c ->
        ModalBottomSheet({ open = null }, containerColor = cs.card) { PalaceDetail(c) }
    }
    if (sheet != 0) {
        val scope = rememberCoroutineScope()
        var err by remember { mutableStateOf<String?>(null) }
        BirthSheet(if (sheet == 1) h else null, if (sheet == 1) "Sửa thông tin sinh" else "Xem lá số khác", if (sheet == 1) "Lưu" else "Xem", err, { sheet = 0 }) { n ->
            if (sheet == 2) { sheet = 0; Nav.push(Screen.LaSo(n, mine = false)); return@BirthSheet }
            scope.launch {
                runCatching { Api.luuTuViHoSo(n) }
                    .onSuccess { evictMemo { it.firstOrNull() == HO_SO }; sheet = 0; Nav.replace(Screen.LaSo(n, mine = true)) }
                    .onFailure { err = "Chưa lưu được, thử lại sau" }
            }
        }
    }
}

@Composable
private fun PalaceCell(c: JSONObject, menh: Boolean, modifier: Modifier, tap: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val now = c.optBoolean("dai_han_hien_tai")
    Column(
        modifier.height(CELL).clip(RoundedCornerShape(12.dp)).background(if (menh) cs.primary.copy(alpha = .10f) else cs.card)
            .then(if (now) Modifier.border(1.5.dp, cs.primary, RoundedCornerShape(12.dp)) else Modifier)
            .clickable(onClick = tap).padding(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(c.getString("ten"), Modifier.weight(1f), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (menh) cs.primary else cs.onSurface, maxLines = 1)
            if (c.optBoolean("than")) Text("Thân", fontSize = 9.sp, color = cs.tertiary, fontWeight = FontWeight.Bold)
        }
        Column(Modifier.weight(1f).padding(top = 2.dp)) {
            c.getJSONArray("chinh_tinh").objList().forEach { s ->
                val hoa = s.optString("hoa")
                Text(
                    s.getString("ten") + if (hoa.isNotEmpty()) " ·$hoa" else "",
                    fontSize = 11.sp, lineHeight = 13.sp, fontWeight = FontWeight.SemiBold,
                    color = if (hoa.isNotEmpty()) MaterialTheme.colorScheme.legible(hoaColor(hoa)) else cs.onSurface,
                )
            }
        }
        val dh = c.getJSONArray("dai_han")
        Row {
            Text(c.getString("chi"), Modifier.weight(1f), fontSize = 10.sp, color = cs.onSurfaceVariant)
            Text("${dh.getInt(0)}–${dh.getInt(1)}", fontSize = 10.sp, color = if (now) cs.primary else cs.onSurfaceVariant)
        }
    }
}

@Composable
private fun Center(h: HoSoSinh, ls: JSONObject, modifier: Modifier) {
    val cs = MaterialTheme.colorScheme
    Column(
        modifier.clip(RoundedCornerShape(12.dp)).background(cs.primary.copy(alpha = .06f)).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("命盤", fontSize = 22.sp, color = cs.primary, fontWeight = FontWeight.Bold)
        @Composable fun L(t: String, bold: Boolean = false) = Text(t, fontSize = 12.sp, textAlign = TextAlign.Center, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal)
        L("${ls.getString("gioi")} · năm ${ls.getString("nam_sinh")}", bold = true)
        L("DL ${fmtDate(h.ngay)}")
        L("ÂL ${ls.getString("am_lich")}")
        L("Giờ ${GIO[h.gio].first} (${GIO[h.gio].second})")
        L(ls.getString("cuc"), bold = true)
        L("Mệnh ${ls.getString("menh")} · Thân ${ls.getString("than")}")
    }
}

@Composable
private fun PalaceDetail(c: JSONObject) = Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
    val cs = MaterialTheme.colorScheme
    val dh = c.getJSONArray("dai_han")
    Text("Cung ${c.getString("ten")} · ${c.getString("chi")}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    Text("Đại hạn ${dh.getInt(0)}–${dh.getInt(1)} tuổi" + if (c.optBoolean("dai_han_hien_tai")) " (đang đi)" else "", color = cs.onSurfaceVariant)
    @Composable fun Stars(label: String, a: JSONArray) {
        if (a.length() == 0) return
        Eyebrow(label)
        a.objList().forEach { s ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(s.getString("ten"), fontWeight = FontWeight.SemiBold)
                if (s.optString("sang").isNotEmpty()) Text(s.getString("sang"), color = cs.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                if (s.optString("hoa").isNotEmpty()) HoaBadge(s.getString("hoa"), 26.dp)
            }
        }
    }
    Stars("Chính tinh", c.getJSONArray("chinh_tinh"))
    if (c.getJSONArray("chinh_tinh").length() == 0) Text("Vô chính diệu (cung không có chính tinh)", color = cs.onSurfaceVariant)
    Stars("Phụ tinh", c.getJSONArray("phu_tinh"))
    val tap = c.getJSONArray("tap_tinh").strList()
    if (tap.isNotEmpty()) { Eyebrow("Tạp tinh"); Text(tap.joinToString(" · "), style = MaterialTheme.typography.bodyMedium) }
}

private fun levelLabel(l: String) = when (l) {
    "excellent" -> "Đại cát" to Color(0xFFC49A3A)
    "good" -> "Cát" to Color(0xFF2E9D5B)
    "caution" -> "Cần lưu ý" to Color(0xFFE5532D)
    else -> "Bình" to Color(0xFF8A8A8A)
}

@Composable
private fun Patterns(list: List<JSONObject>) = Column(
    Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(MaterialTheme.colorScheme.card).padding(18.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
) {
    Eyebrow("Cách cục")
    if (list.isEmpty()) Text("Lá số không thành cách cục đặc biệt nào.", color = MaterialTheme.colorScheme.onSurfaceVariant)
    list.forEach { p ->
        val (label, color) = levelLabel(p.getString("loai"))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(p.getString("ten"), Modifier.weight(1f, fill = false), fontWeight = FontWeight.Bold)
                Text(label, Modifier.clip(RoundedCornerShape(50)).background(color.copy(alpha = .14f)).padding(horizontal = 8.dp, vertical = 2.dp), color = MaterialTheme.colorScheme.legible(color), fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
            Text(p.getString("mo_ta"), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun PhuThe(pt: JSONObject) {
    val sao = pt.getJSONArray("sao").objList()
    val hoa = pt.getJSONArray("hoa").objList()
    if (sao.isEmpty() && hoa.isEmpty()) return
    val cs = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(cs.card).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Eyebrow("Cung Phu Thê")
        sao.forEach { s ->
            Text(s.getString("sao"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(s.getString("tom_tat"), style = MaterialTheme.typography.bodyMedium)
            @Composable fun P(k: String, v: String) = Text("$k: $v", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            P("Thuận", s.getString("tot")); P("Nghịch", s.getString("xau")); P("Người ấy", s.getString("nguoi_ay"))
        }
        hoa.forEach { Text("${it.getString("hoa")}: ${it.getString("loi")}", style = MaterialTheme.typography.bodyMedium) }
    }
}
