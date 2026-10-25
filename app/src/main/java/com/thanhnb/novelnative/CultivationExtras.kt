package com.thanhnb.novelnative

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.MilitaryTech
import androidx.compose.material.icons.rounded.TempleBuddhist
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * Động Phủ · Bí Cảnh · Thành Tựu · Sưu tầm · Luyện hóa hàng loạt · Trận pháp hào quang (migration 124/068).
 * Server là chuẩn: hồi chiêu, trần bình cảnh, bảng bí cảnh đều do RPC kiểm; bảng code/minRealm bí cảnh dưới đây chỉ để HIỂN THỊ
 * (phải khớp cult_explore — docs/tu-tien.md §Động Phủ).
 */

private val DongPhuNames = listOf(
    "Thảo Lư Sơ Cấp", "Thạch Động Tụ Khí", "Linh Nhai Phúc Địa", "Động Thiên Phúc Địa", "Tử Tiêu Cung",
    "Vân Mộng Tiên Đảo", "Côn Lôn Thần Điện", "Bồng Lai Cực Lạc Phủ", "Hỗn Nguyên Tiên Phủ", "Chư Thiên Khởi Nguyên Đạo Điện",
)

private class BiCanh(val code: String, val name: String, val minRealm: Int, val hours: Int, val desc: String)

private val BiCanhs = listOf(
    BiCanh("u_minh", "U Minh Cổ Động", 1, 1, "Hang động cổ xưa ẩn chứa linh khí và yêu thú sơ cấp."),
    BiCanh("van_kiem", "Vạn Kiếm Tiên Trủng", 3, 2, "Chiến trường cổ lưu lạc ngàn vạn linh kiếm và tàn kiếm."),
    BiCanh("thai_hu", "Thái Hư Hư Không Tháp", 5, 3, "Tháp ngưng đọng dòng thời gian, ngập tràn thiên đạo tàn chương."),
    BiCanh("chu_thien", "Chư Thiên Tinh Hải", 9, 4, "Vực sâu giữa các vì sao, ẩn giấu bí mật hồng mông đại đạo."),
)

/** code allowlist khớp cult_halo_codes() (068) + tên hiển thị; ảnh assets/cult_halo/{code}.webp. */
internal val Halos = linkedMapOf(
    "thai_duong" to "Thái Dương Thần Trận", "luc_du" to "Lục Diệu Tiên Trận", "huyen_tuyet" to "Huyền Tuyết Băng Trận",
    "bach_ngan" to "Bạch Ngân Vương Miện", "hoang_kim" to "Hoàng Kim Vương Miện", "huyet_long" to "Huyết Long Vương Miện",
)

private fun vnToday(): String = java.time.LocalDate.now(java.time.ZoneOffset.ofHours(7)).toString()

private suspend fun cooldown(): JSONObject {
    val uid = Session.user?.id ?: return JSONObject()
    return Api.get("user_cultivation?select=last_harvest_at,explore_at&user_id=eq.$uid").optJSONObject(0) ?: JSONObject()
}

/** Hàng thẻ lối tắt dưới thẻ tu vi: Động Phủ · Bí Cảnh · Thành Tựu (+ Hào quang khi đã Phi Thăng). */
@Composable
internal fun CultActions(st: JSONObject, showHalo: Boolean, onChange: () -> Unit) {
    var open by remember { mutableStateOf<String?>(null) }
    val cs = MaterialTheme.colorScheme
    val tabs = buildList {
        add(Triple("dongphu", Icons.Rounded.TempleBuddhist, "Động Phủ"))
        add(Triple("bicanh", Icons.Rounded.Explore, "Bí Cảnh"))
        add(Triple("thanhtuu", Icons.Rounded.MilitaryTech, "Thành Tựu"))
        if (showHalo) add(Triple("haoquang", Icons.Rounded.WbSunny, "Hào Quang"))
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        tabs.forEach { (k, icon, title) ->
            Column(Modifier.weight(1f).cultCard(16.dp).clickable { open = k }.padding(vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(icon, null, Modifier.size(24.dp), tint = cs.primary)
                Text(title, Modifier.padding(top = 6.dp), style = MaterialTheme.typography.labelLarge, maxLines = 1)
            }
        }
    }
    CultSheet(open, st, onChange) { open = null }
}

/** Sheet phụ của Tu Tiên theo khoá [k] (null = đóng); dùng chung cho hàng thẻ lối tắt và nút Sưu tầm ở túi đồ. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CultSheet(k: String?, st: JSONObject, onChange: () -> Unit, close: () -> Unit) {
    if (k == null) return
    ModalBottomSheet(close, containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp, 0.dp, 16.dp, 28.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            when (k) {
                "dongphu" -> DongPhu(st) { onChange() }
                "bicanh" -> BiCanhSheet(st) { onChange() }
                "thanhtuu" -> ThanhTuu(onChange)
                "suutam" -> Collection()
                else -> HaloPicker(st) { close(); onChange() }
            }
        }
    }
}

/** Đầu sheet: icon tròn + tên + một dòng mô tả. */
@Composable
private fun SheetHead(icon: ImageVector, title: String, sub: String? = null) {
    val cs = MaterialTheme.colorScheme
    Row(Modifier.padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(44.dp).clip(CircleShape).background(cs.primary.copy(.12f)), Alignment.Center) { Icon(icon, null, tint = cs.primary) }
        Column(Modifier.padding(start = 12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (sub != null) Text(sub, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
        }
    }
}

/** Kết quả thao tác gần nhất trong sheet (thành công hoặc lỗi), một dòng nền nhạt. */
@Composable
private fun Notice(msg: String?) {
    if (msg == null) return
    val cs = MaterialTheme.colorScheme
    Text(msg, Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(cs.primary.copy(.1f)).padding(12.dp, 10.dp), color = cs.primary, style = MaterialTheme.typography.bodyMedium)
}

/** Một dòng lựa chọn trong sheet: nội dung bên trái, nút/nhãn trạng thái bên phải. */
@Composable
private fun SheetRow(dim: Boolean = false, content: @Composable RowScope.() -> Unit) =
    Row(Modifier.fillMaxWidth().cultCard(16.dp).graphicsLayer { alpha = if (dim) .5f else 1f }.padding(14.dp), verticalAlignment = Alignment.CenterVertically, content = content)

@Composable
private fun DongPhu(st: JSONObject, done: () -> Unit) {
    val scope = rememberCoroutineScope()
    val cd = load("cult_cd") { cooldown() }
    var busy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf<String?>(null) }
    val realm = st.optInt("realm", 1)
    val name = if (st.strOrNull("ascended_at") != null) DongPhuNames.last() else DongPhuNames[(realm - 1).coerceIn(0, DongPhuNames.lastIndex)]
    val last = cd.res?.getOrNull()?.strOrNull("last_harvest_at")?.let { runCatching { java.time.Instant.parse(it) }.getOrNull() }
    val next = last?.plusSeconds(4 * 3600)
    val ready = next == null || next.isBefore(java.time.Instant.now())
    SheetHead(Icons.Rounded.TempleBuddhist, name, "Động phủ lớn dần theo cảnh giới")
    SheetRow {
        Column(Modifier.weight(1f)) {
            Text("Thu nạp linh khí", fontWeight = FontWeight.SemiBold)
            Text("Nhận ngay 30 phút tu vi · hồi 4 giờ", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Button(
            {
                busy = true
                scope.launch {
                    try {
                        val r = JSONObject(Api.rpc("cult_harvest"))
                        msg = "Tụ linh thành công: +${gon(r.optDouble("gain"))} tu vi"
                        cd.reload(); done()
                    } catch (e: CancellationException) { throw e } catch (e: Exception) { msg = friendly(e) }
                    busy = false
                }
            },
            enabled = ready && !busy,
        ) { Text(if (ready) "Thu nạp" else "${java.time.Duration.between(java.time.Instant.now(), next).toMinutes() + 1} phút") }
    }
    Notice(msg)
}

@Composable
private fun BiCanhSheet(st: JSONObject, done: () -> Unit) {
    val scope = rememberCoroutineScope()
    val cs = MaterialTheme.colorScheme
    val cd = load("cult_cd") { cooldown() }
    var busy by remember { mutableStateOf<String?>(null) }
    var msg by remember { mutableStateOf<String?>(null) }
    val realm = st.optInt("realm", 1)
    val ascended = st.strOrNull("ascended_at") != null
    val explored = cd.res?.getOrNull()?.optJSONObject("explore_at") ?: JSONObject()
    val today = vnToday()
    SheetHead(Icons.Rounded.Explore, "Bí Cảnh", "Mỗi nơi một lần mỗi ngày · 25% nhặt thêm vật phẩm")
    Notice(msg)
    BiCanhs.forEach { b ->
        val open = ascended || realm >= b.minRealm
        val used = explored.optString(b.code) == today
        SheetRow(dim = !open) {
            Column(Modifier.weight(1f).padding(end = 10.dp)) {
                Text(b.name, fontWeight = FontWeight.SemiBold)
                Text(b.desc, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                Text(if (open) "+${b.hours} giờ tu vi" else "Mở ở ${RealmNames[b.minRealm - 1]}", Modifier.padding(top = 2.dp), style = MaterialTheme.typography.labelMedium, color = cs.primary)
            }
            if (open) FilledTonalButton(
                {
                    busy = b.code
                    scope.launch {
                        try {
                            val r = JSONObject(Api.rpc("cult_explore", "p_code" to b.code))
                            msg = "Thám hiểm xong: +${gon(r.optDouble("gain"))} tu vi" + (r.optJSONObject("item")?.let { " · nhặt được ${it.optString("name")}" } ?: "")
                            cd.reload(); done()
                        } catch (e: CancellationException) { throw e } catch (e: Exception) { msg = friendly(e) }
                        busy = null
                    }
                },
                enabled = !used && busy == null,
            ) { Text(if (used) "Đã đi" else "Đi") }
        }
    }
}

@Composable
private fun ThanhTuu(onChange: () -> Unit) {
    val scope = rememberCoroutineScope()
    val cs = MaterialTheme.colorScheme
    val l = load("cult_ach") { Api.rpc("cult_achievements").let { org.json.JSONArray(it) }.let { a -> List(a.length()) { a.getJSONObject(it) } } }
    var busy by remember { mutableStateOf<String?>(null) }
    var msg by remember { mutableStateOf<String?>(null) }
    // việc cần làm lên trước: chờ nhận → đang làm → đã nhận
    val list = l.res?.getOrNull().orEmpty().sortedBy { if (it.optBoolean("claimed")) 2 else if (it.optBoolean("achieved")) 0 else 1 }
    SheetHead(Icons.Rounded.MilitaryTech, "Thành Tựu", if (list.isEmpty()) null else "${list.count { it.optBoolean("achieved") }} / ${list.size} hoàn thành · mỗi mục thưởng tu vi + 1 bảo vật")
    Notice(msg)
    if (l.res == null) CircularProgressIndicator(Modifier.size(24.dp))
    list.forEach { a ->
        val achieved = a.optBoolean("achieved")
        val claimed = a.optBoolean("claimed")
        val goal = a.optDouble("goal")
        val prog = a.optDouble("progress")
        SheetRow(dim = claimed) {
            Column(Modifier.weight(1f).padding(end = 10.dp)) {
                Text(a.optString("title"), fontWeight = FontWeight.SemiBold)
                Text(a.optString("desc"), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                if (!achieved && goal > 1 && prog >= 0) {
                    LinearProgressIndicator({ (prog / goal).toFloat().coerceIn(0f, 1f) }, Modifier.padding(top = 6.dp).fillMaxWidth().height(4.dp).clip(CircleShape), gapSize = 0.dp, drawStopIndicator = {})
                    Text("${gon(prog)} / ${gon(goal)}", Modifier.padding(top = 2.dp), style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
                }
            }
            when {
                claimed -> Icon(Icons.Rounded.CheckCircle, "Đã nhận", tint = cs.onSurfaceVariant)
                achieved -> Button(
                    {
                        busy = a.optString("code")
                        scope.launch {
                            try {
                                val r = JSONObject(Api.rpc("cult_claim_achievement", "p_code" to a.optString("code")))
                                msg = "${a.optString("title")}: +${gon(r.optDouble("gain"))} tu vi · ${r.optJSONObject("item")?.optString("name").orEmpty()}"
                                l.reload(); onChange()
                            } catch (e: CancellationException) { throw e } catch (e: Exception) { msg = friendly(e) }
                            busy = null
                        }
                    },
                    enabled = busy == null,
                ) { Text("Nhận") }
                else -> Text("+${a.optInt("hours")} giờ", style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
            }
        }
    }
}

/** Sưu tầm: mọi vật phẩm trong catalog, món từng sở hữu sáng, chưa có thì mờ (user_cult_collection không mất khi dùng hết). */
@Composable
private fun Collection() {
    val l = load("cult_collection") {
        val uid = Session.user?.id.orEmpty()
        val cat = Api.get("cult_items?select=id,code,name,type,grade,pixel&order=type.asc,grade.asc,id.asc")
        val own = Api.get("user_cult_collection?select=item_id&user_id=eq.$uid")
        List(cat.length()) { cat.getJSONObject(it) } to List(own.length()) { own.getJSONObject(it).optInt("item_id") }.toSet()
    }
    val (cat, own) = l.res?.getOrNull() ?: (emptyList<JSONObject>() to emptySet())
    SheetHead(Icons.Rounded.GridView, "Sưu tầm", if (cat.isEmpty()) null else "${cat.count { it.optInt("id") in own }} / ${cat.size} vật phẩm từng sở hữu")
    if (l.res == null) CircularProgressIndicator(Modifier.size(24.dp))
    cat.chunked(5).forEach { row ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            row.forEach { Box(Modifier.weight(1f)) { ItemTile(it, dim = it.optInt("id") !in own) } }
            repeat(5 - row.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

@Composable
private fun HaloPicker(st: JSONObject, done: () -> Unit) {
    val scope = rememberCoroutineScope()
    val cs = MaterialTheme.colorScheme
    val owned = st.optJSONArray("halos")?.let { a -> List(a.length()) { a.optString(it) } }.orEmpty()
    val worn = st.strOrNull("halo_worn")
    var msg by remember { mutableStateOf<String?>(null) }
    fun wear(code: String?) = scope.launch {
        try { Api.rpc("cult_wear_halo", "p_code" to code); done() } catch (e: CancellationException) { throw e } catch (e: Exception) { msg = friendly(e) }
    }
    SheetHead(Icons.Rounded.WbSunny, "Hào Quang", "Rơi kèm cơ duyên sau Phi Thăng · chỉ trang trí")
    Notice(msg)
    Halos.entries.chunked(3).forEach { row ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            row.forEach { (code, name) ->
                val has = code in owned
                Column(
                    Modifier.weight(1f).cultCard(16.dp).then(if (code == worn) Modifier.border(2.dp, cs.primary, RoundedCornerShape(16.dp)) else Modifier)
                        .clickable(enabled = has) { wear(if (code == worn) null else code) }.padding(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    AsyncImage("file:///android_asset/cult_halo/$code.webp", name, Modifier.size(64.dp).graphicsLayer { alpha = if (has) 1f else .25f }, contentScale = ContentScale.Fit)
                    Text(name, Modifier.padding(top = 4.dp), style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center, minLines = 2, maxLines = 2)
                    Text(if (!has) "Chưa có" else if (code == worn) "Đang đội" else "Chạm để đội", style = MaterialTheme.typography.labelSmall, color = if (code == worn) cs.primary else cs.onSurfaceVariant)
                }
            }
            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

/** Lò Luyện Đan: 5 bản dư cùng phẩm → 1 đan dược/linh thạch phẩm kế — cult_craft (141). Server chọn món. */
@Composable
internal fun CraftDialog(dismiss: () -> Unit, done: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    var g by remember { mutableIntStateOf(1) }
    var kind by remember { mutableStateOf("danduoc") }
    var busy by remember { mutableStateOf(false) }
    AlertDialog(
        dismiss, containerColor = MaterialTheme.colorScheme.card,
        title = { Text("Lò Luyện Đan") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Dồn 5 bản dư cùng phẩm (giữ lại 1 bản mỗi món) thành 1 vật phẩm phẩm kế tiếp. Món ra ngẫu nhiên.")
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    GradeNames.take(4).forEachIndexed { i, n -> FilterChip(g == i + 1, { g = i + 1 }, { Text("$n → ${GradeNames[i + 1]}") }) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(kind == "danduoc", { kind = "danduoc" }, { Text("Đan dược") })
                    FilterChip(kind == "linhthach", { kind = "linhthach" }, { Text("Linh thạch") })
                }
            }
        },
        confirmButton = {
            TextButton(
                {
                    busy = true
                    scope.launch {
                        val m = try {
                            val it = JSONObject(Api.rpc("cult_craft", "p_grade" to g, "p_kind" to kind)).getJSONObject("item")
                            "Luyện thành công: ${it.optString("name")} (phẩm ${GradeNames.getOrElse(it.optInt("grade") - 1) { "?" }})"
                        } catch (e: CancellationException) { throw e } catch (e: Exception) { friendly(e) }
                        done(m)
                    }
                },
                enabled = !busy,
            ) { Text("Luyện") }
        },
        dismissButton = { TextButton(dismiss) { Text("Huỷ") } },
    )
}

/** Luyện hóa MỌI bản dư (giữ 1) của các món phẩm ≤ [gradeMax] — cult_recycle_all (124). */
@Composable
internal fun RecycleAllDialog(dismiss: () -> Unit, done: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    var g by remember { mutableIntStateOf(2) }
    var busy by remember { mutableStateOf(false) }
    AlertDialog(
        dismiss, containerColor = MaterialTheme.colorScheme.card,
        title = { Text("Luyện hóa hàng loạt") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Đốt mọi bản dư (giữ lại 1 bản mỗi món) của vật phẩm từ Hoàng đến phẩm chọn dưới đây, đổi lấy tu vi.")
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    GradeNames.forEachIndexed { i, n -> FilterChip(g == i + 1, { g = i + 1 }, { Text(n) }) }
                }
            }
        },
        confirmButton = {
            TextButton(
                {
                    busy = true
                    scope.launch {
                        val m = try {
                            val r = JSONObject(Api.rpc("cult_recycle_all", "p_grade_max" to g))
                            "Đã luyện hóa ${r.optInt("recycled")} bản dư của ${r.optInt("items")} món: +${gon(r.optDouble("linh_khi"))} tu vi"
                        } catch (e: CancellationException) { throw e } catch (e: Exception) { friendly(e) }
                        done(m)
                    }
                },
                enabled = !busy,
            ) { Text("Luyện hóa") }
        },
        dismissButton = { TextButton(dismiss) { Text("Huỷ") } },
    )
}
