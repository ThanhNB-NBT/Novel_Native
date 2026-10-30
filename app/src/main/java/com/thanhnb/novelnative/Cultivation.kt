package com.thanhnb.novelnative

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Air
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Backpack
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.ShieldMoon
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Tu Tiên (bản native): trạng thái + thẻ tu vi tick sống + chọn xuất thân + đột phá/phi thăng + trang bị + túi đồ.
 * Sheet phụ ở CultivationExtras.kt, hiệu ứng/cảnh/phân tích tốc độ ở CultivationFx.kt, cơ duyên khi đọc ở CultivationGift.kt.
 * Server là chuẩn (docs/tu-tien.md ở Novel_Backend): client chỉ gọi RPC và hiện số server trả, KHÔNG tự tính công thức.
 */

internal val RealmNames = listOf("Luyện Khí", "Trúc Cơ", "Kim Đan", "Nguyên Anh", "Hóa Thần", "Luyện Hư", "Hợp Thể", "Đại Thừa", "Độ Kiếp")
private val HanNum = listOf("一", "二", "三", "四", "五", "六", "七", "八", "九", "十", "十一", "十二", "十三", "十四", "十五")
private val StatIcons = mapOf("atk" to Icons.Rounded.Bolt, "def" to Icons.Rounded.ShieldMoon, "hp" to Icons.Rounded.Favorite, "agi" to Icons.Rounded.Air, "than_thuc" to Icons.Rounded.AutoAwesome)
private val RealmHan = listOf("煉氣", "築基", "金丹", "元嬰", "化神", "煉虛", "合體", "大乘", "渡劫")
internal val TienTierNames = listOf(
    "Tiên Nhân", "Địa Tiên", "Thiên Tiên", "Kim Tiên", "Thái Ất Kim Tiên", "Đại La Kim Tiên", "Đạo Tổ",
    "Hỗn Nguyên Thánh Nhân", "Hồng Mông Chí Tôn", "Hư Vô Đại Đạo Tổ", "Siêu Thoát Giả", "Vĩnh Hằng Chúa Tể",
    "Nguyên Sơ Thủy Tổ", "Vô Lượng Đạo Chủ", "Vô Thượng Chí Tôn",
) // = cult_tien_max()+1 ở SQL
internal val RaceNames = linkedMapOf("nhan" to "Nhân tộc", "yeu" to "Yêu tộc", "ma" to "Ma tộc", "linh" to "Linh tộc")
private val RaceDescs = mapOf(
    "nhan" to "Đạo tâm kiên định — tỷ lệ đột phá +5%.",
    "yeu" to "Thể phách cường hãn — công kích & khí huyết ×1.3.",
    "ma" to "Tu luyện tà tốc — tốc độ ×1.10, đột phá −5%.",
    "linh" to "Linh hồn thanh tịnh — thần thức ×1.3, thất bại đột phá chỉ mất nửa tu vi.",
)
private val RaceFile = mapOf("nhan" to "human", "yeu" to "fox", "ma" to "demon", "linh" to "spirit")
private val StatNames = linkedMapOf("atk" to "Công Kích", "def" to "Phòng Ngự", "hp" to "Khí Huyết", "agi" to "Thân Pháp", "than_thuc" to "Thần Thức")
private val ElementNames = mapOf("kim" to "Kim", "moc" to "Mộc", "thuy" to "Thủy", "hoa" to "Hỏa", "tho" to "Thổ", "all" to "Vạn Pháp")
internal val Variants = mapOf(
    "thien" to "Thiên Linh Căn", "hon" to "Hỗn Độn Linh Căn", "kiem" to "Kiếm Linh Căn", "loi" to "Lôi Linh Căn",
    "bang" to "Băng Linh Căn", "phong" to "Phong Linh Căn", "am" to "Huyễn Linh Căn",
)
private val SlotTypes = linkedMapOf(
    "congphap" to "Công pháp", "vukhi" to "Vũ khí", "phapbao" to "Pháp bảo", "phapchu" to "Pháp chú", "yphuc" to "Y phục", "giay" to "Giày",
)
private val Consumable = setOf("danduoc", "linhthach")
internal val GradeNames = listOf("Hoàng", "Huyền", "Địa", "Thiên", "Tiên")
private val GradeColors = listOf(Color(0xFF8D8D8D), Color(0xFF4CAF50), Color(0xFF2196F3), Color(0xFF9C27B0), Color(0xFFFFA000))

internal fun gradeColor(g: Int) = GradeColors[(g - 1).coerceIn(0, GradeColors.lastIndex)]

/** 431580831 → "431,6M" · 25300 → "25,3K" (cùng cách rút gọn bên Flutter). */
internal fun gon(v: Double): String {
    fun so(x: Double) = "%.1f".format(x).replace('.', ',')
    return when {
        v >= 1_000_000_000_000 -> so(v / 1_000_000_000_000) + "T"
        v >= 1_000_000_000 -> so(v / 1_000_000_000) + "B"
        v >= 1_000_000 -> so(v / 1_000_000) + "M"
        v >= 10_000 -> so(v / 1_000) + "K"
        else -> v.toLong().toString()
    }
}

internal fun JSONObject.strOrNull(k: String) = if (isNull(k)) null else optString(k).ifEmpty { null }

private fun JSONObject.items(k: String): List<JSONObject> = optJSONArray(k)?.let { a -> List(a.length()) { a.getJSONObject(it) } }.orEmpty()

internal object CultApi {
    suspend fun state() = JSONObject(Api.rpc("cult_state"))
    suspend fun inventory(): List<JSONObject> {
        val uid = Session.user?.id ?: return emptyList()
        return Api.get("user_cult_items?select=qty,cult_items(*)&user_id=eq.$uid&qty=gt.0").let { a -> List(a.length()) { a.getJSONObject(it) } }
    }
    suspend fun setAvatar(race: String, gender: String) = JSONObject(Api.rpc("cult_set_avatar", "p_race" to race, "p_gender" to gender))
    suspend fun advance() = JSONObject(Api.rpc("cult_advance"))
    suspend fun ascend() = JSONObject(Api.rpc("cult_ascend"))
    suspend fun ascendTier() = JSONObject(Api.rpc("cult_ascend_tier"))
    suspend fun use(id: Int) = JSONObject(Api.rpc("cult_use_item", "p_item_id" to id))
    suspend fun equip(id: Int) = JSONObject(Api.rpc("cult_equip", "p_item_id" to id))
    suspend fun recycle(id: Int) = JSONObject(Api.rpc("cult_recycle", "p_item_id" to id))
}

/** Diễn giải effect jsonb của vật phẩm (mirror cultEffectText bên Flutter). */
private fun effectText(it: JSONObject): String {
    val e = it.optJSONObject("effect") ?: JSONObject()
    return when {
        e.has("rate_pct") -> "+${e.optInt("rate_pct")}% tốc độ tu luyện"
        e.has("bt_pct") -> "+${e.optInt("bt_pct")}% tỷ lệ đột phá"
        e.has("atk") -> "công kích +${e.optInt("atk")}"
        e.has("def") -> "phòng ngự +${e.optInt("def")}" + if (e.has("hp")) " · khí huyết +${e.optInt("hp")}" else ""
        e.has("agi") -> "thân pháp +${e.optInt("agi")}"
        else -> when (e.optString("kind")) {
            "linhcan" -> "luyện căn +${e.optInt("add")} điểm vĩnh viễn"
            "buff", "stone" -> "+${e.optInt("pct")}% tốc độ trong ${e.optInt("hours")} giờ"
            "hothan" -> "+${e.optInt("pct")}% đột phá cho lần kế tiếp"
            "element" -> "tráo bộ hệ linh căn (ngẫu nhiên, giữ nguyên bậc)"
            else -> "hệ số tu luyện theo phẩm (×1.5 → ×24)" + (e.strOrNull("element")?.let { " · hệ ${ElementNames[it] ?: it}" } ?: "")
        }
    }
}

/** size chỉ định = khung cố định; bỏ trống = lấp đầy ô cha (ô lưới túi đồ / trang bị). */
@Composable
internal fun ItemArt(item: JSONObject, size: Dp = Dp.Unspecified) {
    val pixel = item.optString("pixel")
    val key = ItemArt[item.optString("code")] ?: if (pixel == "gourd_big") "gourd" else pixel
    AsyncImage(
        "file:///android_asset/cult_items/$key.webp", item.optString("name"),
        if (size.isSpecified) Modifier.size(size) else Modifier.fillMaxSize(), contentScale = ContentScale.Fit,
    )
}

/** Thẻ chung của khu Tu Tiên: kính mờ trên nền tranh (tranh vẫn hiện qua) + viền tóc mảnh, không hoạ tiết. */
@Composable
internal fun Modifier.cultCard(radius: Dp = 20.dp): Modifier {
    val cs = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(radius)
    return clip(shape).background(cs.surface.copy(.62f)).border(1.dp, cs.onSurface.copy(.08f), shape)
}

/** Dải nền giấy đặc hơn thẻ kính, dành cho khối có chữ nhỏ/danh sách dài nằm trên tranh; nhiều dải liền nhau ghép thành một khối (top/bottom = bo góc đầu/cuối). */
@Composable
private fun Modifier.band(top: Boolean, bottom: Boolean): Modifier {
    val t = if (top) 22.dp else 0.dp
    val b = if (bottom) 22.dp else 0.dp
    return clip(RoundedCornerShape(t, t, b, b)).background(MaterialTheme.colorScheme.surface.copy(.9f))
}

/** Header Tu Tiên: không chữ, chỉ linh vật ở giữa (nút lùi bên trái khi mở như trang con). */
@Composable
private fun MascotHeader(back: Boolean, onAdmin: (() -> Unit)? = null) {
    Box(Modifier.fillMaxWidth().statusBarsPadding().height(64.dp)) {
        if (onAdmin != null) IconButton(onAdmin, Modifier.align(Alignment.CenterEnd).padding(end = 4.dp)) { Icon(Icons.Rounded.Tune, "Công cụ admin", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
        if (back) IconButton(Nav::pop, Modifier.align(Alignment.CenterStart).padding(start = 4.dp)) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Quay lại") }
        Orb(modifier = Modifier.align(Alignment.Center).size(52.dp))
    }
}

@Composable
private fun SectionTitle(title: String, trailing: @Composable (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp).heightIn(min = 36.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontFamily = Serif, fontWeight = FontWeight.Bold)
        trailing?.invoke()
    }
}

@Composable
fun CultivationScreen(tab: Boolean = false) {
    val cs = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    val st = load("cult_state") { CultApi.state() }
    val inv = load("cult_inv") { CultApi.inventory() }
    var busy by remember { mutableStateOf(false) }
    var info by remember { mutableStateOf<String?>(null) }
    var picked by remember { mutableStateOf<JSONObject?>(null) }
    var recycleAll by remember { mutableStateOf(false) }
    var craft by remember { mutableStateOf(false) }
    var sheet by remember { mutableStateOf<String?>(null) }
    var fx by remember { mutableStateOf<Pair<String, JSONObject>?>(null) }
    var speed by remember { mutableStateOf(false) }
    var adminEdit by remember { mutableStateOf(false) }
    val admin = load("admin", Session.user?.id) { Api.isAdmin() }.res?.getOrNull() == true

    fun refresh() { st.reload(); inv.reload() }
    fun act(job: suspend () -> String?) {
        if (busy) return
        busy = true
        scope.launch {
            try { info = job() } catch (e: CancellationException) { throw e } catch (e: Exception) { info = friendly(e) }
            busy = false
            refresh()
        }
    }

    val dark = cs.background.luminance() < .5f
    Box(Modifier.fillMaxSize().background(cs.background)) {
        // tranh thuỷ mặc cố định toàn màn (ngày/đêm), phủ nhẹ để chữ trên thẻ kính vẫn đọc rõ
        AsyncImage(
            "file:///android_asset/bg/${if (dark) "cultivation_bg_night" else "cultivation_bg"}.webp", null,
            Modifier.fillMaxSize(), contentScale = ContentScale.Crop, alignment = Alignment.TopCenter,
        )
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color.Transparent, .5f to cs.background.copy(.2f), 1f to cs.background.copy(.55f))))
    Column(Modifier.fillMaxSize()) {
        val header = @Composable { MascotHeader(back = !tab, onAdmin = if (admin) ({ adminEdit = true }) else null) }
        // có nhân vật thì header nằm trong danh sách và cuộn đi cùng
        if (st.res?.getOrNull()?.strOrNull("race") == null) header()
        if (Session.user == null) { Empty("Đăng nhập để bắt đầu tu hành."); return@Column }
        Loaded(st) { s ->
            if (s.strOrNull("race") == null) {
                AvatarPicker { race, gender -> act { CultApi.setAvatar(race, gender); "Đã nhập ${RaceNames[race]} — bắt đầu tu hành!" } }
                return@Loaded
            }
            val ascended = s.strOrNull("ascended_at") != null
            val realm = s.optInt("realm", 1)
            val req = s.optDouble("req", 1.0).coerceAtLeast(1.0)
            val rate = s.optDouble("rate", 0.0)
            val exp0 = s.optDouble("exp", 0.0)
            // tick sống: server đã tính bù offline, client chỉ cộng mượt giữa hai lần gọi
            var exp by remember(s) { mutableDoubleStateOf(exp0.coerceIn(0.0, req)) }
            LaunchedEffect(s) { while (true) { delay(1000); exp = (exp + rate).coerceIn(0.0, req) } }
            val realmCol = if (ascended) gradeColor(5) else gradeColor((realm + 1) / 2)
            val equipped = s.optJSONObject("equipped") ?: JSONObject()
            val wearing = SlotTypes.keys.mapNotNull { equipped.optJSONObject(it)?.optInt("id") }.toSet()
            val bag = inv.res?.getOrNull().orEmpty().filter { it.optJSONObject("cult_items")?.optInt("id") !in wearing }
                .sortedWith(compareByDescending<JSONObject> { it.getJSONObject("cult_items").optInt("grade") })
            val pad = Modifier.padding(horizontal = 16.dp)

            PullToRefreshBox(false, ::refresh) {
                val gap = Modifier.padding(bottom = 12.dp)
                val rows = bag.chunked(5)
                val bagMsg = if (inv.res?.isFailure == true) "Không tải được túi đồ — kéo xuống để thử lại." else if (bag.isEmpty()) "Chưa có vật phẩm — đọc truyện để nhận cơ duyên." else null
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = if (tab) DockSpace else 32.dp)) {
                    item { Box(gap) { HeroStage(s, realmCol, header) } }
                    item {
                        RealmCard(s, exp, req, rate, realmCol, busy, pad.then(gap), onSpeed = { speed = true }) {
                            if (!busy) {
                                busy = true
                                val stage = s.optInt("stage", 1)
                                scope.launch {
                                    // kết quả hiện qua hộp hiệu ứng; màn giữ snapshot cũ tới khi đóng hộp (không thấy số đổi dưới lớp kiếp lôi)
                                    try {
                                        fx = when {
                                            ascended -> "tier" to CultApi.ascendTier()
                                            realm >= 9 && stage >= 9 -> "ascend" to CultApi.ascend()
                                            else -> (if (stage >= 9) "major" else "stage") to CultApi.advance()
                                        }
                                    } catch (e: CancellationException) { throw e } catch (e: Exception) { info = friendly(e) }
                                    busy = false
                                }
                            }
                        }
                    }
                    item { Box(pad.then(gap)) { CultActions(s, showHalo = ascended || (admin && BuildConfig.DEBUG)) { refresh() } } }
                    item {
                        Column(pad.then(gap).band(true, true).padding(start = 10.dp, end = 10.dp, top = 8.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(Modifier.padding(start = 6.dp)) { SectionTitle("Trang bị") }
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                SlotTypes.forEach { (type, label) ->
                                    val it = equipped.optJSONObject(type)
                                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                        ItemTile(it) { picked = it }
                                        Text(label, Modifier.padding(top = 4.dp), fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = cs.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                    item {
                        Box(pad.band(true, false).padding(start = 16.dp, end = 8.dp, top = 8.dp)) {
                            SectionTitle("Túi đồ · ${bag.size}") {
                                TextButton({ sheet = "suutam" }) { Text("Sưu tầm") }
                                TextButton({ craft = true }) { Text("Luyện đan") }
                                TextButton({ recycleAll = true }) { Text("Luyện hóa") }
                            }
                        }
                    }
                    // tải túi lỗi thì nói rõ, đừng hiện "0 món" như thể mất hết đồ
                    if (bagMsg != null) item { Box(pad.band(false, true).padding(bottom = 12.dp)) { Empty(bagMsg) } }
                    itemsIndexed(rows) { i, row ->
                        Row(
                            pad.fillMaxWidth().band(false, i == rows.lastIndex).padding(start = 12.dp, end = 12.dp, top = 4.dp, bottom = if (i == rows.lastIndex) 16.dp else 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            row.forEach { r ->
                                val it = r.getJSONObject("cult_items")
                                Box(Modifier.weight(1f)) { ItemTile(it, r.optInt("qty")) { picked = it.put("qty", r.optInt("qty")) } }
                            }
                            repeat(5 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
            CultSheet(sheet, s, ::refresh) { sheet = null }
            fx?.let { (kind, r) ->
                val figure = "${RaceFile[s.optString("race")] ?: "human"}_${if (s.optString("gender") == "nu") "female" else "male"}"
                AdvanceFxDialog(r, kind, figure, if (kind == "tier") TienTierNames.getOrNull(s.optInt("tien_tier", 0) + 1) else null) { fx = null; refresh() }
            }
            if (speed) SpeedDialog(s) { speed = false }
            if (adminEdit) AdminAvatarDialog(
                s, { adminEdit = false },
                onPreview = { k, r -> adminEdit = false; fx = k to r },
                onDev = { rl, sg -> act { Api.rpc("cult_debug_set", "p_realm" to rl, "p_stage" to sg, "p_fill" to true); "Đã đặt: ${RealmNames[rl - 1]} · tầng $sg" } },
                onGrantHalos = { act { Api.rpc("cult_admin_grant_halos"); "Đã nhận đủ trận pháp hào quang" } },
            ) { race, gender -> adminEdit = false; act { CultApi.setAvatar(race, gender); "Đã đổi sang ${RaceNames[race]}" } }
        }
    }
        // dải mờ sau thanh trạng thái: cuộn xuống thì thẻ không chạy chồng lên giờ/pin
        Box(Modifier.fillMaxWidth().windowInsetsTopHeight(WindowInsets.statusBars).background(Brush.verticalGradient(listOf(cs.background, cs.background.copy(.6f)))))
    }

    picked?.let { it ->
        val id = it.optInt("id")
        val type = it.optString("type")
        val worn = type in SlotTypes && st.res?.getOrNull()?.optJSONObject("equipped")?.optJSONObject(type)?.optInt("id") == id
        val qty = it.optInt("qty", 1)
        val g = it.optInt("grade")
        AlertDialog(
            { picked = null }, containerColor = cs.card,
            icon = { Box(Modifier.size(80.dp).clip(RoundedCornerShape(20.dp)).background(gradeColor(g).copy(.14f)).padding(10.dp)) { ItemArt(it) } },
            title = { Text(it.optString("name"), textAlign = TextAlign.Center) },
            text = {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("${GradeNames.getOrElse(g - 1) { "?" }} phẩm · ${typeLabel(type)}" + if (qty > 1) " · ×$qty" else "", color = gradeColor(g), style = MaterialTheme.typography.labelLarge)
                    Text(effectText(it), textAlign = TextAlign.Center)
                    it.strOrNull("description")?.let { d -> Text(d, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, textAlign = TextAlign.Center) }
                }
            },
            confirmButton = {
                if (!worn) TextButton({
                    picked = null
                    act { if (type in Consumable) { CultApi.use(id); "Đã dùng ${it.optString("name")}" } else { CultApi.equip(id); "Đã trang bị ${it.optString("name")}" } }
                }) { Text(if (type in Consumable) "Dùng" else "Trang bị") }
            },
            dismissButton = {
                Row {
                    if (qty > 1) TextButton({ picked = null; act { CultApi.recycle(id).let { r -> "Luyện hóa +${gon(r.optDouble("linh_khi"))} tu vi" } } }) { Text("Luyện hóa bản dư") }
                    TextButton({ picked = null }) { Text("Đóng") }
                }
            },
        )
    }
    if (craft) CraftDialog({ craft = false }) { m -> craft = false; info = m; refresh() }
    if (recycleAll) RecycleAllDialog({ recycleAll = false }) { m -> recycleAll = false; info = m; refresh() }
    info?.let { m ->
        AlertDialog({ info = null }, containerColor = cs.card, text = { Text(m) }, confirmButton = { TextButton({ info = null }) { Text("OK") } })
    }
}

/**
 * Trận pháp dưới chân: vòng nét đứt chạy quanh, vạch khắc xoay, vòng trong chạy ngược, chín nút = chín tầng (đã qua thì sáng, tầng hiện tại đập nhẹ).
 * Mọi chu kỳ là bội nguyên của vòng 8 giây [t] nên lặp liền mạch; ellipse dẹt 0,24 để ra phối cảnh nhìn từ trên xuống.
 */
private fun DrawScope.drawFormation(c: Offset, rx: Float, col: Color, t: Float, lit: Int) {
    val tau = 2f * PI.toFloat()
    val d = density
    val ry = rx * .24f
    fun pt(a: Float, k: Float) = Offset(c.x + rx * k * cos(a), c.y + ry * k * sin(a))
    withTransform({ scale(1f, .24f, c) }) {
        drawCircle(Brush.radialGradient(listOf(col.copy(.34f), col.copy(.1f), Color.Transparent), c, rx * 1.25f), rx * 1.25f, c)
    }
    fun ring(k: Float, alpha: Float, w: Float, dash: Float = 0f, dir: Float = 1f) = drawOval(
        col.copy(alpha), Offset(c.x - rx * k, c.y - ry * k), Size(rx * k * 2, ry * k * 2),
        style = Stroke(w * d, pathEffect = if (dash > 0f) PathEffect.dashPathEffect(floatArrayOf(dash * d, dash * .7f * d), dir * t * dash * 1.7f * 6f * d) else null),
    )
    ring(1f, .8f, 1.5f, 9f)
    ring(.8f, .45f, 1f)
    ring(.5f, .6f, 1f, 6f, -1f)
    for (i in 0 until 16) {
        val a = i * tau / 16 + t * tau
        val long = i % 4 == 0
        drawLine(col.copy(.45f + .4f * (.5f + .5f * sin(a))), pt(a, .83f), pt(a, if (long) .97f else .91f), strokeWidth = (if (long) 1.8f else 1f) * d, cap = StrokeCap.Round)
    }
    for (i in 0 until 9) {
        val a = PI.toFloat() + i * tau / 9
        val p = pt(a, 1f)
        val depth = 1f + .3f * sin(a)
        if (i < lit) {
            val pulse = if (i == lit - 1) 1f + .35f * sin(t * tau * 2f) else 1f
            drawCircle(col.copy(.3f), 8f * d * depth * pulse, p)
            drawCircle(col, 4f * d * depth, p)
            drawCircle(Color.White, 2.2f * d * depth, p)
        } else drawCircle(col.copy(.55f), 3f * d * depth, p, style = Stroke(1f * d))
    }
}

/** Nút đột phá: đủ tu vi thì chuyển sắc theo màu cảnh giới, quầng sáng đập nhịp và vệt sáng quét ngang; chưa đủ thì xám phẳng. */
@Composable
private fun AdvanceButton(label: String, enabled: Boolean, ready: Boolean, col: Color, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val tr = rememberInfiniteTransition(label = "adv")
    val pulse by tr.animateFloat(0f, 1f, infiniteRepeatable(tween(1600, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "p")
    val sweep by tr.animateFloat(-.4f, 1.4f, infiniteRepeatable(tween(2400, easing = LinearEasing)), label = "s")
    val fg = if (ready) onColor(col) else cs.onSurfaceVariant
    val fill: Brush = if (ready) Brush.horizontalGradient(listOf(lerp(col, Color.White, .25f), col, lerp(col, Color.Black, .15f))) else SolidColor(cs.onSurface.copy(.06f))
    Box(
        Modifier.padding(top = 14.dp).fillMaxWidth().height(50.dp)
            .then(if (ready) Modifier.drawBehind {
                drawRoundRect(col.copy(.18f + .22f * pulse), Offset(-5.dp.toPx(), -3.dp.toPx()), Size(size.width + 10.dp.toPx(), size.height + 6.dp.toPx()), CornerRadius(20.dp.toPx()))
            } else Modifier)
            .clip(RoundedCornerShape(16.dp)).background(fill)
            .then(if (ready) Modifier.drawWithContent {
                drawContent()
                val x = sweep * size.width
                drawRect(Brush.horizontalGradient(listOf(Color.Transparent, Color.White.copy(.4f), Color.Transparent), x - size.width * .2f, x + size.width * .2f))
            } else Modifier)
            .clickable(enabled = enabled, onClick = onClick),
        Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (ready) Icon(Icons.Rounded.Bolt, null, Modifier.padding(end = 6.dp).size(18.dp), tint = fg)
            Text(label, fontWeight = FontWeight.Bold, color = fg)
        }
    }
}

/**
 * Phần đầu màn: tranh thuỷ mặc (ngày/đêm) tan dần vào nền app, tiêu đề nằm trên tranh, nhân vật bồng bềnh trên một vòng
 * sáng màu cảnh giới, vài hạt linh khí bay lên. Dưới cùng là tên cảnh giới và một dòng thông tin.
 */
@Composable
private fun HeroStage(s: JSONObject, realmCol: Color, header: @Composable () -> Unit) {
    val cs = MaterialTheme.colorScheme
    // một đồng hồ 8 giây; nhún 2 lần và hạt bay 1 vòng mỗi chu kỳ nên lặp liền mạch
    val t by rememberInfiniteTransition(label = "hero").animateFloat(0f, 1f, infiniteRepeatable(tween(8000, easing = LinearEasing)), label = "t")
    val file = "${RaceFile[s.optString("race")] ?: "human"}_${if (s.optString("gender") == "nu") "female" else "male"}"
    val ascended = s.strOrNull("ascended_at") != null
    val tier = s.optInt("tien_tier", 0)
    val realm = s.optInt("realm", 1)
    val eq = s.optJSONObject("equipped") ?: JSONObject()
    // quầng mang màu hệ công pháp đang tu (vô hệ thì màu cảnh giới); hạt linh khí mang màu bộ hệ linh căn
    val aura = eq.optJSONObject("congphap")?.optJSONObject("effect")?.strOrNull("element")?.let { qiColors(listOf(it), realmCol)[0] } ?: realmCol
    val qi = qiColors(s.elements(), realmCol)
    val ribbons = remember(qi) { if (s.elements().isEmpty()) emptyList() else qi.map { it.toArgb() } }
    // đồng hồ liên tục (vòng 4 giây) cho dải linh khí: tần số lệch nhau, không được reset theo vòng 8 giây
    val clock by produceState(0f) { val t0 = withFrameNanos { it }; while (true) value = (withFrameNanos { it } - t0) / 4e9f }
    val ot = (t * 2f) % 1f // quỹ đạo đồ bay: vòng 4 giây như Flutter
    val orbiters = listOf(Triple("vukhi", ::weaponOrbit, 1f), Triple("phapbao", ::phapbaoOrbit, .82f))
        .mapNotNull { (k, orbit, sc) -> eq.optJSONObject(k)?.let { Triple(it, orbit, sc) } }
    // một lớp của mọi thứ bay quanh người: dải linh khí + vệt đuôi (Canvas) và ảnh món đồ — gọi 2 lần quanh ảnh nhân vật
    @Composable fun BoxScope.orbitLayer(front: Boolean) {
        Canvas(Modifier.matchParentSize()) {
            figureSpace {
                qiRibbons(FigC.x, FigC.y + 6, ribbons, clock, front, 1f, 1f, 4.2f, additive = false)
                orbiters.forEach { (_, orbit, sc) -> orbitTail(orbit, ot, FigC, aura.toArgb(), sc, front) }
            }
        }
        orbiters.forEach { (item, orbit, sc) ->
            Box(Modifier.align(Alignment.TopCenter).size((26 * sc * FigK).dp).graphicsLayer {
                val (p, isFront) = orbit(ot, FigC)
                alpha = if (isFront != front) 0f else if (front) 1f else .88f
                scaleX = if (isFront) 1f else 21f / 26f; scaleY = scaleX
                translationX = (p.x - FigC.x) * FigK * density
                translationY = (FigTop + (p.y - FigC.y) * FigK - 13 * sc * FigK) * density
            }) { ItemArt(item) }
        }
    }
    Box(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            header()
            Box(Modifier.fillMaxWidth().height(280.dp), Alignment.BottomCenter) {
                Canvas(Modifier.matchParentSize()) {
                    val cx = size.width / 2f
                    val ground = size.height * .92f
                    val ac = Offset(cx, size.height * .55f)
                    drawCircle(Brush.radialGradient(listOf(aura.copy(.34f), Color.Transparent), ac, size.height * .45f), size.height * .45f, ac)
                    drawFormation(Offset(cx, size.height * .86f), size.height * .38f, aura, t, if (ascended) 9 else s.optInt("stage", 1))
                    // bóng đổ mềm dưới chân, co lại khi nhân vật nhún lên
                    val rx = size.height * .2f * (1f - .08f * sin(t * 4f * PI.toFloat()))
                    drawOval(Brush.radialGradient(listOf(Color.Black.copy(.35f), Color.Transparent), Offset(cx, ground), rx), Offset(cx - rx, ground - rx * .2f), Size(rx * 2, rx * .4f))
                    drawRealmScene(realm, ascended, qi, aura, t)
                }
                orbitLayer(front = false)
                Box(Modifier.padding(bottom = 16.dp).graphicsLayer { translationY = sin(t * 4f * PI.toFloat()) * 5.dp.toPx() }, Alignment.Center) {
                    s.strOrNull("halo_worn")?.let { h -> AsyncImage("file:///android_asset/cult_halo/$h.webp", null, Modifier.size(250.dp), contentScale = ContentScale.Fit) }
                    // kiếm luân xoay sau đầu từ Kim Đan
                    if (ascended || realm >= 3) AsyncImage(
                        "file:///android_asset/cult_fx/sword_wheel.webp", null,
                        Modifier.align(Alignment.TopCenter).size(120.dp + (realm * 4).dp).graphicsLayer { rotationZ = t * 360f; alpha = .5f },
                        contentScale = ContentScale.Fit,
                    )
                    AsyncImage("file:///android_asset/cultivators/$file.webp", null, Modifier.height(240.dp), contentScale = ContentScale.Fit)
                }
                orbitLayer(front = true)
            }
            Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.size(24.dp, 1.dp).background(realmCol))
                Text(
                    if (ascended) "仙界" else RealmHan.getOrElse(realm - 1) { "" },
                    style = TextStyle(fontSize = 15.sp, letterSpacing = 6.sp, color = cs.onSurface.copy(.8f), shadow = Shadow(cs.background, blurRadius = 10f)),
                )
                Box(Modifier.size(24.dp, 1.dp).background(realmCol))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (ascended) TienTierNames.getOrElse(tier) { "Tiên Nhân" } else RealmNames.getOrElse(s.optInt("realm", 1) - 1) { "?" },
                    style = TextStyle(fontFamily = Serif, fontSize = 28.sp, fontWeight = FontWeight.Bold, color = cs.onSurface, shadow = Shadow(cs.background, blurRadius = 12f)),
                )
                // ấn triện: tầng (cảnh giới thường) hoặc bậc (tiên giới), cùng màu ấn với tờ Lịch âm
                val seal = LocalSeal.current
                val onSeal = onColor(seal, lightBias = 1.4f)
                Column(
                    Modifier.padding(start = 10.dp).clip(RoundedCornerShape(4.dp)).background(seal).padding(horizontal = 7.dp, vertical = 3.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(HanNum.getOrElse(if (ascended) tier else s.optInt("stage", 1) - 1) { "" }, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = onSeal, lineHeight = 14.sp)
                    Text(if (ascended) "階" else "層", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = onSeal, lineHeight = 14.sp)
                }
            }
            RealmBadges(s, realmCol)
        }
    }
}

/** Thẻ tu vi: thanh tu vi mảnh, nút đột phá, 5 chỉ số. */
@Composable
private fun RealmCard(s: JSONObject, exp: Double, req: Double, rate: Double, realmCol: Color, busy: Boolean, modifier: Modifier, onSpeed: () -> Unit, onAdvance: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val ascended = s.strOrNull("ascended_at") != null
    val tier = s.optInt("tien_tier", 0)
    val realm = s.optInt("realm", 1)
    val stage = s.optInt("stage", 1)
    val full = exp >= req - 0.5
    val maxed = ascended && tier >= TienTierNames.lastIndex
    val frac = (exp / req).toFloat().coerceIn(0f, 1f)
    val sheen by rememberInfiniteTransition(label = "sheen").animateFloat(-.4f, 1.4f, infiniteRepeatable(tween(2800, easing = LinearEasing)), label = "sw")
    val shape = RoundedCornerShape(20.dp)
    Column(modifier.fillMaxWidth().clip(shape).background(cs.surface.copy(.86f)).border(1.dp, Brush.verticalGradient(listOf(realmCol.copy(.65f), cs.onSurface.copy(.08f))), shape).padding(16.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text("${gon(exp)} / ${gon(req)}", fontWeight = FontWeight.Bold)
            Text(" · ${"%.1f".format(frac * 100).replace('.', ',')}%", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
            Text(
                "+${"%.1f".format(rate).replace('.', ',')}/giây ⓘ", Modifier.clip(CircleShape).clickable(onClick = onSpeed).padding(horizontal = 6.dp, vertical = 2.dp),
                style = MaterialTheme.typography.labelMedium, color = cs.primary,
            )
        }
        Box(Modifier.padding(top = 10.dp).fillMaxWidth().height(12.dp).clip(CircleShape).background(cs.onSurface.copy(.1f))) {
            Box(
                Modifier.fillMaxWidth(frac.coerceAtLeast(.03f)).fillMaxHeight().clip(CircleShape).background(Brush.horizontalGradient(listOf(realmCol.copy(.7f), realmCol)))
                    .drawWithContent {
                        drawContent()
                        val x = sheen * size.width
                        drawRect(Brush.horizontalGradient(listOf(Color.Transparent, Color.White.copy(.55f), Color.Transparent), x - size.width * .3f, x + size.width * .3f))
                    },
            )
        }
        BuffChips(s)
        val label = when {
            maxed -> "Đã đạt cực hạn"
            ascended -> "Độ Thiên Kiếp · ${TienTierNames[tier + 1]}"
            realm >= 9 && stage >= 9 -> "Phi Thăng"
            stage >= 9 -> "Đột phá cảnh giới"
            else -> "Lên tầng ${stage + 1}"
        }
        AdvanceButton(label, full && !busy && !maxed, full && !maxed, realmCol, onAdvance)
        if (!full && !maxed) Text(
            "Còn ${gon(req - exp)} tu vi" + (if (rate > 0) " · khoảng ${eta((req - exp) / rate)}" else ""),
            Modifier.fillMaxWidth().padding(top = 6.dp), style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant, textAlign = TextAlign.Center,
        )
        HorizontalDivider(Modifier.padding(vertical = 14.dp), color = cs.outlineVariant.copy(.5f))
        val stats = s.optJSONObject("stats") ?: JSONObject()
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            StatNames.forEach { (k, name) ->
                Column(Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(realmCol.copy(.12f)).padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(StatIcons.getValue(k), null, Modifier.size(14.dp), tint = ensureContrast(realmCol, cs.surface, 3f, if (cs.background.luminance() < .5f) Color.White else Color.Black))
                    Text(if (stats.has(k)) gon(stats.optDouble(k)) else "—", fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1)
                    Text(name, fontSize = 10.sp, color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

/** 3725 giây → "1 giờ 2 phút"; dưới 1 phút → "vài giây". Chỉ để người chơi ước lượng, server mới là chuẩn. */
private fun eta(sec: Double): String {
    val m = (sec / 60).toLong()
    return when {
        m < 1 -> "vài giây"
        m < 60 -> "$m phút"
        m < 60 * 24 -> "${m / 60} giờ ${m % 60} phút"
        else -> "${m / 1440} ngày ${(m % 1440) / 60} giờ"
    }
}

/** Ô vật phẩm vuông: nền nhuốm nhẹ màu phẩm, số lượng ở góc; [item] null = ô trống; [dim] = chưa sở hữu (Sưu tầm). */
@Composable
internal fun ItemTile(item: JSONObject?, qty: Int = 1, dim: Boolean = false, onClick: (() -> Unit)? = null) {
    val cs = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(14.dp)
    val grade = if (item != null && !dim) gradeColor(item.optInt("grade")) else null
    Box(
        Modifier.fillMaxWidth().aspectRatio(1f).clip(shape).background(cs.surface.copy(.62f)).border(1.dp, cs.onSurface.copy(.08f), shape)
            .then(if (grade != null) Modifier.background(grade.copy(.09f)) else Modifier)
            .then(if (grade != null && item != null && item.optInt("grade") >= 4) Modifier.background(Brush.radialGradient(listOf(grade.copy(.32f), Color.Transparent))) else Modifier)
            .then(if (grade != null) Modifier.drawBehind {
                val h = 3.dp.toPx()
                drawRect(grade, Offset(size.width * .3f, size.height - h), Size(size.width * .4f, h))
            } else Modifier)
            .then(if (item != null && onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        Alignment.Center,
    ) {
        if (item != null) Box(Modifier.padding(8.dp).graphicsLayer { alpha = if (dim) .22f else 1f }) { ItemArt(item) }
        if (qty > 1) Text(
            "$qty", Modifier.align(Alignment.BottomEnd).padding(4.dp).clip(CircleShape).background(cs.inverseSurface.copy(.8f)).padding(horizontal = 5.dp),
            fontSize = 9.sp, fontWeight = FontWeight.Bold, color = cs.inverseOnSurface,
        )
    }
}

/** Dưới tên cảnh giới: 9 vạch tầng (đã qua tô màu cảnh giới) + nhãn tộc, bậc linh căn và từng hệ có chấm màu riêng. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RealmBadges(s: JSONObject, realmCol: Color) {
    val cs = MaterialTheme.colorScheme
    val stage = s.optInt("stage", 1)
    Box(Modifier.padding(top = 8.dp)) { Pill {
        if (s.strOrNull("ascended_at") == null) {
            repeat(9) { i ->
                Box(Modifier.padding(horizontal = 2.dp).size(if (i < stage) 16.dp else 10.dp, 5.dp).clip(CircleShape).background(if (i < stage) realmCol else cs.onSurface.copy(.15f)))
            }
            Text("Tầng $stage", Modifier.padding(start = 8.dp), fontSize = 12.sp, fontWeight = FontWeight.Bold)
        } else Text("Bậc ${s.optInt("tien_tier", 0) + 1} / ${TienTierNames.size} · Tiên giới", fontSize = 12.sp, fontWeight = FontWeight.Bold)
    } }
    FlowRow(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Pill { Text(RaceNames[s.optString("race")].orEmpty(), fontSize = 12.sp, fontWeight = FontWeight.Medium) }
        // bậc linh căn + các hệ chung một nhãn: tên bậc đậm, mỗi hệ một chấm màu
        Pill {
            Text(rootName(s), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = cs.primary)
            s.elements().forEach {
                Box(Modifier.padding(start = 8.dp, end = 4.dp).size(7.dp).clip(CircleShape).background(elemColor(it)))
                Text(ElementNames[it] ?: it, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun Pill(content: @Composable RowScope.() -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier.clip(CircleShape).background(cs.surface.copy(.78f)).border(1.dp, cs.onSurface.copy(.08f), CircleShape).padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically, content = content,
    )
}

/** Mời chọn xuất thân (race null): giới tính + chủng tộc lưới 2×2. User thường chỉ chọn MỘT lần (server chặn đổi). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AvatarPicker(onPick: (String, String) -> Unit) {
    val cs = MaterialTheme.colorScheme
    var gender by remember { mutableStateOf("nam") }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Chọn xuất thân", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text("Chủng tộc định thiên hướng cả đời tu — chọn rồi không đổi được.", color = cs.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            listOf("nam" to "Nam", "nu" to "Nữ").forEachIndexed { i, (k, v) ->
                SegmentedButton(gender == k, { gender = k }, SegmentedButtonDefaults.itemShape(i, 2)) { Text(v) }
            }
        }
        RaceNames.entries.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { (k, name) ->
                    Column(Modifier.weight(1f).cultCard().clickable { onPick(k, gender) }.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        AsyncImage("file:///android_asset/cultivators/${RaceFile[k]}_${if (gender == "nu") "female" else "male"}.webp", null, Modifier.height(110.dp), contentScale = ContentScale.Fit)
                        Text(name, Modifier.padding(top = 6.dp), fontWeight = FontWeight.Bold)
                        Text(RaceDescs[k].orEmpty(), fontSize = 11.sp, color = cs.onSurfaceVariant, textAlign = TextAlign.Center, lineHeight = 15.sp, minLines = 3)
                    }
                }
            }
        }
    }
}

internal fun typeLabel(type: String) = SlotTypes[type] ?: if (type in Consumable) "Tiêu hao" else type
