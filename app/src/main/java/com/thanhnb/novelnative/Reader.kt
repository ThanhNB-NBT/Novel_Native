package com.thanhnb.novelnative

import android.app.Activity
import android.view.KeyEvent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.FormatListBulleted
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.FormatAlignJustify
import androidx.compose.material.icons.rounded.FormatUnderlined
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.platform.LocalLayoutDirection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/** Màu nền/chữ khi đọc — chép readerColors của Flutter, GIỮ thứ tự vì rd_light/rd_dark lưu theo index. */
internal class RColor(val name: String, val bg: Color, val fg: Color)

internal val readerColors = listOf(
    RColor("Sáng", Color(0xFFFAF6EF), Color(0xFF2B2620)),
    RColor("Trắng", Color(0xFFFFFFFF), Color(0xFF1F1F1F)),
    RColor("Sepia", Color(0xFFF1E4CB), Color(0xFF4A3B28)),
    RColor("Xanh dịu", Color(0xFFE0EBE1), Color(0xFF23312A)),
    RColor("Xám", Color(0xFFE9E9EC), Color(0xFF2A2A2E)),
    RColor("Tối", Color(0xFF181A1B), Color(0xFFCAC7BF)),
    RColor("Đen", Color(0xFF0B0B0C), Color(0xFFB4B4B6)),
    RColor("Kem", Color(0xFFF3ECDD), Color(0xFF3E3524)),
    RColor("Hồng phấn", Color(0xFFF7EAEA), Color(0xFF4A3335)),
    RColor("Vàng nhạt", Color(0xFFF8F1D8), Color(0xFF443E27)),
    RColor("Bạc hà", Color(0xFFE4EFEA), Color(0xFF213330)),
    RColor("Dạ Lam", Color(0xFF0F151E), Color(0xFFBEC6D1)),
    RColor("Nâu trầm", Color(0xFF1B1611), Color(0xFFCDBDA7)),
    RColor("Giấy cũ", Color(0xFFEDE3CF), Color(0xFF33271C)),
    RColor("Mực đêm", Color(0xFF14110E), Color(0xFFD9CCB4)),
)

internal class RFont(val key: String, val name: String, private val own: FontFamily?, val serif: Boolean) {
    /** Font tải từ Google Fonts (own == null): chưa có trên máy thì dùng Jakarta tạm. */
    val family: FontFamily get() = own ?: WebFonts.ready[key] ?: Jakarta
    val web get() = own == null
}

private fun ff(regular: Int, bold: Int) = FontFamily(Font(regular), Font(bold, FontWeight.Bold))

// Đóng gói sẵn (Flutter tải từ Google Fonts lúc chạy): bản tĩnh 400/700 có bảng chữ Việt, ~40–70 KB mỗi file
internal val readerFonts = listOf(
    RFont("lora", "Lora", ff(R.font.lora_regular, R.font.lora_bold), true),
    RFont("merriweather", "Merriweather", ff(R.font.merriweather_regular, R.font.merriweather_bold), true),
    RFont("literata", "Literata", ff(R.font.literata_regular, R.font.literata_bold), true),
    RFont("playfair", "Playfair Display", ff(R.font.playfairdisplay_regular, R.font.playfairdisplay_bold), true),
    RFont("jakarta", "Plus Jakarta Sans", Jakarta, false),
    RFont("bevietnam", "Be Vietnam Pro", ff(R.font.bevietnampro_regular, R.font.bevietnampro_bold), false),
    RFont("roboto", "Roboto", FontFamily.SansSerif, false),
    RFont("inter", "Inter", ff(R.font.inter_regular, R.font.inter_bold), false),
    RFont("manrope", "Manrope", ff(R.font.manrope_regular, R.font.manrope_bold), false),
    RFont("nunitosans", "Nunito Sans", ff(R.font.nunitosans_regular, R.font.nunitosans_bold), false),
    RFont("mulish", "Mulish", ff(R.font.mulish_regular, R.font.mulish_bold), false),
    RFont("sarabun", "Sarabun", ff(R.font.sarabun_regular, R.font.sarabun_bold), false),
    RFont("lexend", "Lexend", ff(R.font.lexend_regular, R.font.lexend_bold), false),
    RFont("opensans", "Open Sans", ff(R.font.opensans_regular, R.font.opensans_bold), false),
    RFont("notosans", "Noto Sans", ff(R.font.notosans_regular, R.font.notosans_bold), false),
    RFont("montserrat", "Montserrat", ff(R.font.montserrat_regular, R.font.montserrat_bold), false),
    RFont("worksans", "Work Sans", ff(R.font.worksans_regular, R.font.worksans_bold), false),
    RFont("quicksand", "Quicksand", ff(R.font.quicksand_regular, R.font.quicksand_bold), false),
) + WebFonts.sans.map { (k, n, _) -> RFont(k, n, null, false) }

/** Khoá prefs giống bản Flutter (rd_*). */
internal class Style {
    val sp = Prefs.sp
    var size by mutableFloatStateOf(sp.getFloat("rd_size", 18f))
    var font by mutableStateOf(sp.getString("rd_font", null)?.takeIf { k -> readerFonts.any { it.key == k } } ?: "literata")
    var light by mutableIntStateOf(sp.getInt("rd_light", 13)) // Giấy cũ
    var dark by mutableIntStateOf(sp.getInt("rd_dark", 14)) // Mực đêm
    var mode by mutableIntStateOf(sp.getInt("rd_mode", 0)) // 0 theo app · 1 sáng · 2 tối
    var lh by mutableFloatStateOf(sp.getFloat("rd_lh", 1.7f))
    var margin by mutableFloatStateOf(sp.getFloat("rd_margin", 20f))
    var justify by mutableStateOf(sp.getBoolean("rd_justify", false))
    var page by mutableStateOf(sp.getBoolean("rd_page", false))
    var gloss by mutableStateOf(sp.getBoolean("rd_mark_gloss", false))
    var volume by mutableStateOf(sp.getBoolean("rd_volume_keys", false))
    var speed by mutableFloatStateOf(sp.getFloat("rd_auto_speed", 40f))
    val rfont get() = readerFonts.first { it.key == font }
    fun save() = sp.edit().putFloat("rd_size", size).putString("rd_font", font).putInt("rd_light", light).putInt("rd_dark", dark)
        .putInt("rd_mode", mode).putFloat("rd_lh", lh).putFloat("rd_margin", margin).putBoolean("rd_justify", justify)
        .putBoolean("rd_page", page).putBoolean("rd_mark_gloss", gloss).putBoolean("rd_volume_keys", volume)
        .putFloat("rd_auto_speed", speed).apply()
}

/** Phím âm lượng: MainActivity chuyển KeyEvent vào đây; null = để hệ thống chỉnh tiếng như thường. */
object ReaderKeys { var handler: ((KeyEvent) -> Boolean)? = null }

/** Tự cuộn hết chương → mở chương sau và cuộn tiếp. */
private var autoCarry = false
/** Vị trí cuộn mang sang chương sau khi chương sau đã hiện dần trong danh sách: (số thứ tự item, lệch px). */
private var scrollCarry: Pair<Int, Int>? = null

/** Lật trang từ ngoài pager (phím âm lượng). */
internal class PageTurn { var f: (suspend (Int) -> Unit)? = null }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(s: Screen.Read) {
    val st = remember { Style() }
    val ctx = LocalContext.current
    LaunchedEffect(st.font) { if (st.rfont.web) WebFonts.ensure(ctx, st.font) }
    val appDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val dark = when (st.mode) { 1 -> false; 2 -> true; else -> appDark }
    val col = readerColors.getOrElse(if (dark) st.dark else st.light) { readerColors[13] }
    val bg = col.bg
    val fg = col.fg
    val scope = rememberCoroutineScope()
    var auto by remember { mutableStateOf(autoCarry.also { autoCarry = false }) }
    var sheet by remember { mutableIntStateOf(0) } // 0 không · 1 mục lục · 2 cài đặt
    var report by remember { mutableStateOf(false) }
    var reportCtx by remember { mutableStateOf<Pair<String, String>?>(null) } // (từ đang chọn, đoạn chứa nó) nếu báo từ form sửa
    var editing by remember { mutableStateOf<String?>(null) } // đoạn đang sửa bản dịch
    var menuPara by remember { mutableStateOf<String?>(null) } // đoạn đang mở menu giữ-lâu (sửa dịch · trích dẫn · ghi chú)
    var hitAt by remember { mutableIntStateOf(-1) } // vị trí ký tự bị nhấn giữ trong đoạn (-1 = không biết)
    var msg by remember { mutableStateOf<String?>(null) }
    var offerPatch by remember { mutableStateOf(false) } // vừa sửa một TÊN RIÊNG → mời "Áp cả truyện" (từ thường không có gì để áp, vá chỉ tổ nhầm)
    LaunchedEffect(msg) { delay(if (offerPatch) 6000 else 2500); msg = null; offerPatch = false }
    val patchScope = rememberCoroutineScope()
    val go = { i: Int -> if (i >= 1) { if (i > s.index) Mascot.react(OrbMood.Happy); Nav.replace(s.copy(index = i)) } }
    // lật trang sang chương kế: chương sau mở ở trang đầu, chương trước mở ở trang cuối (đúng trang vừa lướt thấy)
    val goPaged = { i: Int -> Prefs.setPercent(s.novelId, i, if (i > s.index) 0f else 1f); go(i) }
    val claims = load("gift_claims", s.novelId) { Gift.claimed(s.novelId) }
    var claimedNow by remember(s.novelId) { mutableStateOf(emptySet<Int>()) } // vừa nhận trong phiên, che độ trễ của lần tải lại

    // Giữ màn hình sáng khi đang đọc
    val view = LocalView.current
    DisposableEffect(Unit) { view.keepScreenOn = true; onDispose { view.keepScreenOn = false } }
    // Icon thanh trạng thái theo nền đọc (nền tối → icon sáng); rời màn thì trả về theo theme app
    DisposableEffect(bg) {
        val ctl = WindowCompat.getInsetsController((view.context as Activity).window, view)
        ctl.isAppearanceLightStatusBars = bg.luminance() > 0.5f
        ctl.isAppearanceLightNavigationBars = bg.luminance() > 0.5f
        onDispose { ctl.isAppearanceLightStatusBars = !appDark; ctl.isAppearanceLightNavigationBars = !appDark }
    }

    // đang tự gửi yêu cầu dịch → màn chờ hiện vòng xoay thay vì nháy nút "Yêu cầu dịch" rồi mới đổi sang "hàng đợi"
    var asking by remember(s.novelId, s.index) { mutableStateOf(Session.user != null) }
    LaunchedEffect(s.novelId, s.index) {
        runCatching { Api.requestToc(s.novelId) }
        if (Session.user != null) {
            Api.aheadCount().let { n -> if (n > 0) runCatching { Api.requestTranslation(s.novelId, s.index + n, from = s.index) } }
            asking = false
        }
    }

    val l = load("ch", s.novelId, s.index) { Api.chapter(s.novelId, s.index) }
    val ch = l.res?.getOrNull()
    // chỉ ghi tiến độ khi chương có thật: lướt quá chương cuối không đẩy tiến độ vượt mục lục
    LaunchedEffect(s.novelId, s.index, ch != null) {
        if (ch != null && Session.user != null) {
            val n = peek<Novel>("novel", s.novelId) ?: peek<List<Novel>>("library", Session.user?.id)?.find { it.id == s.novelId } // không có cả hai (vào từ thông báo) → widget chỉ ẩn thanh tiến độ
            ContinueWidget.update(view.context, s.novelId, s.title, s.index, n?.chaptersSource ?: 0, n?.cover)
            runCatching { Api.saveProgress(s.novelId, s.index) }
            Mascot.fedChanged()
        }
    }
    // Chưa dịch xong → hỏi lại mỗi 5s tới khi 'done' (Flutter dùng realtime, ở đây poll cho gọn)
    // Chương chưa có (truyện "lười" đang được crawler tải mục lục) cũng poll như vậy
    val loaded = l.res?.isSuccess == true
    LaunchedEffect(loaded, ch?.status, asking) {
        if (loaded && ch?.status != "done" && (ch != null || Session.user != null)) {
            if (!asking) l.reload() // vừa gửi yêu cầu xong → lấy trạng thái mới ngay, khỏi chờ 5s
            while (true) { delay(5000); l.reload() }
        }
    }
    // chờ dịch: Think một lần khi bắt đầu chờ, Cheer + nói khi chương vừa chờ xong (không bắn lại theo mỗi lần poll)
    var waited by remember(s.novelId, s.index) { mutableStateOf(false) }
    LaunchedEffect(loaded, ch?.status, asking) {
        if (loaded && ch?.status != "done" && (ch != null || Session.user != null)) { if (!waited) { waited = true; Mascot.react(OrbMood.Think) } }
        else if (waited && ch?.status == "done") { waited = false; Mascot.say("Chương ${s.index} dịch xong rồi!", OrbMood.Cheer) }
    }
    val shown = ch?.status == "done" && ch.paragraphs.isNotEmpty()
    // đổi chương (Nav.replace) dựng bản mới trước khi bản cũ bị huỷ → chỉ tắt khi đỉnh ngăn xếp không còn là màn đọc
    DisposableEffect(shown) { Mascot.reading = shown; onDispose { if (Nav.stack.last() !is Screen.Read) Mascot.reading = false } }
    val terms = if (st.gloss) load("gloss", s.novelId) { Api.glossary(s.novelId) }.res?.getOrNull().orEmpty() else emptyList()

    var pct by remember { mutableFloatStateOf(Prefs.percent(s.novelId, s.index)) }
    val setPct = { p: Float -> pct = p.coerceIn(0f, 1f); Prefs.setPercent(s.novelId, s.index, p) }
    // Bộ nhớ chỉ giữ chương trước · hiện tại · sau của truyện đang đọc; chương xa hơn bị xoá
    LaunchedEffect(s.novelId, s.index) {
        evictMemo { k -> k.firstOrNull() == "ch" && !(k[1] == s.novelId && (k[2] as Int) in s.index - 1..s.index + 1) }
    }
    // Đọc quá 30% chương → tải sẵn chương sau (và trước) vào bộ nhớ, vuốt sang là có chữ ngay
    // derivedStateOf: đọc pct thẳng ở đây làm cả màn đọc dựng lại mỗi lần cuộn qua một đoạn
    val past30 by remember { derivedStateOf { pct >= 0.3f } }
    val warm = past30 && ch?.status == "done"
    // chương trước/sau nối liền hai đầu chương này (chỉ dùng khi đã dịch xong); lấy ngay từ bộ nhớ nếu đã có
    fun ready(c: Chapter?) = c?.takeIf { it.status == "done" && it.paragraphs.isNotEmpty() }
    var prev by remember(s.novelId, s.index) { mutableStateOf(ready(peek("ch", s.novelId, s.index - 1))) }
    var next by remember(s.novelId, s.index) { mutableStateOf(ready(peek("ch", s.novelId, s.index + 1))) }
    LaunchedEffect(s.novelId, s.index, warm) {
        if (!warm) return@LaunchedEffect
        for (i in intArrayOf(s.index + 1, s.index - 1)) if (i >= 1) {
            val c = ready(prefetch("ch", s.novelId, i) { Api.chapter(s.novelId, i)?.takeIf { it.status == "done" } })
            if (i > s.index) next = c else prev = c
        }
    }
    val carry = remember { scrollCarry.also { scrollCarry = null } }
    val lead = leadOf(prev) // số item của chương trước nằm phía trên tiêu đề chương này
    val leadNow by rememberUpdatedState(lead)
    // không dùng rememberLazyListState (tự lưu theo màn): quay lại chương đã xem sẽ khôi phục vị trí cũ (cuối chương) thay vì vị trí mang sang → bật qua lại hai chương mãi
    val list = remember { LazyListState(carry?.let { it.first + lead } ?: lead, carry?.second ?: 0) }
    val paras = ch?.paragraphs.orEmpty()
    val hl = if (TtsPlayer.novelId == s.novelId && TtsPlayer.chapter == s.index) TtsPlayer.paraAt else -1
    LaunchedEffect(hl) { if (hl >= 0 && !st.page) list.animateScrollToItem(leadNow + 1 + hl) } // cuộn theo đoạn đang đọc
    LaunchedEffect(Unit) { // máy đọc tự sang chương sau → màn đọc đi theo
        snapshotFlow { TtsPlayer.chapter }.drop(1).collect { c -> if (TtsPlayer.novelId == s.novelId && c != s.index) go(c) }
    }
    LaunchedEffect(s.index) { // tự đổi chương khi đang nghe thì dừng nghe (khác với máy đọc tự sang chương)
        if (TtsPlayer.novelId == s.novelId && TtsPlayer.chapter != s.index) TtsPlayer.stop()
    }
    // Khôi phục vị trí đọc dở trong chương (lưu theo % số đoạn, khoá giống Flutter)
    LaunchedEffect(paras.size, st.page) {
        if (paras.isEmpty() || st.page) return@LaunchedEffect
        val p = if (carry != null) 0f else Prefs.percent(s.novelId, s.index) // vào từ cuộn liền: list đã đứng đúng chỗ
        if (p > 0f) list.scrollToItem(leadNow + 1 + (p * paras.size).toInt().coerceAtMost(paras.size - 1))
        snapshotFlow { list.firstVisibleItemIndex to list.canScrollForward }.collectLatest { (i, more) ->
            setPct(if (!more) 1f else ((i - leadNow - 1).coerceAtLeast(0).toFloat() / paras.size))
        }
    }

    // Sang trang (1) / lùi (-1): lật trang thì đổi trang, cuộn dọc thì cuộn ~1 màn, sát mép thì đổi chương
    val pageTurn = remember { PageTurn() }
    val turn = { dir: Int ->
        scope.launch {
            when {
                st.page -> pageTurn.f?.invoke(dir)
                dir > 0 && !list.canScrollForward -> go(s.index + 1)
                dir < 0 && !list.canScrollBackward -> go(s.index - 1)
                else -> list.animateScrollBy(dir * list.layoutInfo.viewportSize.height * 0.9f)
            }
        }
    }
    DisposableEffect(st.volume, TtsPlayer.playing) {
        ReaderKeys.handler = if (!st.volume || TtsPlayer.playing) null else { e ->
            val dir = when (e.keyCode) { KeyEvent.KEYCODE_VOLUME_DOWN -> 1; KeyEvent.KEYCODE_VOLUME_UP -> -1; else -> 0 }
            if (dir != 0 && e.action == KeyEvent.ACTION_DOWN && e.repeatCount == 0) turn(dir)
            dir != 0 // nuốt cả DOWN lẫn UP để âm lượng không đổi
        }
        onDispose { ReaderKeys.handler = null }
    }

    // Tự cuộn: tốc độ px/giây như Flutter (đơn vị dp); chạm/kéo tay là dừng, hết chương thì sang chương sau
    val density = LocalDensity.current.density
    LaunchedEffect(auto, paras.size, st.page) {
        if (!auto || st.page || paras.isEmpty()) return@LaunchedEffect
        delay(400) // chờ khôi phục vị trí đọc xong, không thì cuộn từ đầu rồi bị nhảy
        var last = 0L
        while (true) {
            val t = withFrameNanos { it }
            if (last != 0L) {
                if (!list.canScrollForward) { autoCarry = true; go(s.index + 1); break }
                try {
                    list.scrollBy(st.speed * density * (t - last) / 1e9f)
                } catch (e: CancellationException) {
                    if (!isActive) throw e
                    auto = false; break // người dùng kéo tay giành cuộn
                }
            }
            last = t
        }
    }

    val font = st.rfont.family
    val body = TextStyle(
        fontFamily = font, fontSize = st.size.sp, lineHeight = (st.size * st.lh).sp, color = fg,
        textAlign = if (st.justify) TextAlign.Justify else TextAlign.Start,
    )
    val title = TextStyle(fontFamily = font, fontWeight = FontWeight.Bold, fontSize = (st.size + 4).sp, lineHeight = ((st.size + 4) * 1.3f).sp, color = fg)

    // Phần thanh trạng thái luôn thuộc về header (hoặc dải nền khi ẩn thanh) → nội dung không bao giờ cuộn lên dưới pin/đồng hồ
    val ttsOn = TtsPlayer.novelId == s.novelId // thanh nghe đã chừa thanh điều hướng → nội dung khỏi chừa lần nữa
    val insets = WindowInsets.safeDrawing.only(if (ttsOn) WindowInsetsSides.Horizontal else WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)
    Box(
        Modifier.fillMaxSize().background(bg).then(
            if (st.page) Modifier else Modifier.pointerInput(Unit) {
                // Chạm nhẹ → ẩn/hiện thanh (hoặc dừng tự cuộn); quan sát ở pass Initial để không giành cử chỉ cuộn/bôi chọn
                awaitEachGesture {
                    val down = awaitFirstDown(pass = PointerEventPass.Initial)
                    val up = waitForUpOrCancellation(PointerEventPass.Initial) ?: return@awaitEachGesture
                    if (up.uptimeMillis - down.uptimeMillis < 250 && (up.position - down.position).getDistance() < 24f) {
                        if (auto) auto = false
                    }
                }
            },
        ),
    ) {
        // Header ghim như Flutter (appBar của Scaffold): chữ nằm DƯỚI thanh, ẩn thanh thì chữ giãn lên
        Column(Modifier.fillMaxSize()) {
        if (auto) Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars).fillMaxWidth().background(bg))
        AnimatedVisibility(!auto) {
            Column(Modifier.background(bg).statusBarsPadding()) {
                Row(Modifier.fillMaxWidth().height(46.dp), verticalAlignment = Alignment.CenterVertically) {
                    CompositionLocalProvider(LocalContentColor provides fg.copy(alpha = 0.55f)) {
                        IconButton(Nav::pop) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Quay lại", Modifier.size(20.dp)) }
                        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                            Row(
                                Modifier.clip(RoundedCornerShape(50)).background(fg.copy(alpha = 0.07f)).clickable { sheet = 1 }.padding(start = 12.dp, end = 6.dp, top = 5.dp, bottom = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("Chương ${s.index}", fontFamily = Jakarta, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = fg.copy(alpha = 0.78f))
                                Icon(Icons.Rounded.ExpandMore, "Mục lục", Modifier.size(18.dp))
                            }
                            Orb(modifier = Modifier.padding(start = 6.dp).size(30.dp))
                        }
                        Text("${(pct * 100).roundToInt()}%", Modifier.padding(end = 4.dp), style = monoStyle(11, fg.copy(alpha = 0.45f)))
                        val toggleTts = {
                            if (TtsPlayer.active) TtsPlayer.stop()
                            else if (paras.isNotEmpty()) scope.launch {
                                // đọc từ đoạn đang đọc dở, không từ đầu; truyền đoạn đang hiện để highlight khớp
                                val from = (if (st.page) (pct * paras.size).toInt() else list.firstVisibleItemIndex - 1).coerceIn(0, paras.size - 1)
                                TtsPlayer.start(view.context, s.novelId, s.index, from, paras, ch?.title)?.let { msg = it }
                            }
                        }
                        // Header chỉ giữ Nghe truyện + Cài đặt đọc; còn lại nằm trong menu ⋮
                        IconButton({ toggleTts() }) {
                            Icon(Icons.Rounded.Headphones, if (TtsPlayer.active) "Dừng nghe" else "Nghe truyện", Modifier.size(20.dp),
                                tint = if (TtsPlayer.active) MaterialTheme.colorScheme.primary else LocalContentColor.current)
                        }
                        IconButton({ sheet = 2 }) { Icon(Icons.Rounded.TextFields, "Cài đặt đọc", Modifier.size(20.dp)) }
                        var menu by remember { mutableStateOf(false) }
                        Box {
                            IconButton({ menu = true }) { Icon(Icons.Rounded.MoreVert, "Thêm", Modifier.size(20.dp)) }
                            DropdownMenu(menu, { menu = false }) {
                                DropdownMenuItem({ Text("Thêm dấu trang") }, {
                                    menu = false
                                    val i = if (st.page) (pct * paras.size).toInt() else list.firstVisibleItemIndex - 1
                                    scope.launch {
                                        runCatching { Api.addBookmark(s.novelId, s.index, pct, paras.getOrNull(i.coerceIn(0, (paras.size - 1).coerceAtLeast(0)))?.take(120)) }
                                            .onSuccess { msg = "Đã thêm dấu trang"; Mascot.react(OrbMood.Love) }.onFailure { msg = friendly(it); Mascot.react(OrbMood.Sad) }
                                    }
                                })
                                DropdownMenuItem({ Text("Mục lục") }, { menu = false; sheet = 1 })
                                DropdownMenuItem({ Text("Báo lỗi chương") }, { menu = false; reportCtx = null; report = true })
                                DropdownMenuItem({ Text("Thông tin truyện") }, { menu = false; Nav.push(Screen.Detail(s.novelId)) })
                            }
                        }
                    }
                }
                // thanh tiến độ màu nhấn của app trên rãnh mờ
                Box(Modifier.fillMaxWidth().height(2.5.dp).background(fg.copy(alpha = 0.08f))) {
                    Box(Modifier.fillMaxWidth(pct.coerceIn(0f, 1f)).fillMaxHeight().background(MaterialTheme.colorScheme.primary, RoundedCornerShape(50)))
                }
            }
        }
        Box(Modifier.weight(1f)) {
        CompositionLocalProvider(LocalContentColor provides fg) {
            Loaded(l, loading = { TextSkeleton() }) { c ->
                when {
                    Session.user == null -> NeedLogin("Đăng nhập để đọc truyện.")
                    c == null -> {
                        // quá chương cuối của mục lục → báo hết chương thay vì xoay "đang tải mục lục" mãi
                        val last = load("toc", s.novelId) { Api.toc(s.novelId) }.res?.getOrNull()?.lastOrNull()?.index
                        if (last == null || s.index <= last) Waiting("toc", fg) {}
                        else Column(Modifier.fillMaxSize().padding(32.dp), Arrangement.Center, Alignment.CenterHorizontally) {
                            Text("Bạn đã đọc hết $last chương hiện có.\nChương mới sẽ hiện ở đây khi nguồn cập nhật.", textAlign = TextAlign.Center)
                            Spacer(Modifier.height(12.dp))
                            Button({ go(last) }) { Text("Về chương $last") }
                        }
                    }
                    c.status != "done" -> Waiting(if (asking && c.status != "queued" && c.status != "translating") "asking" else c.status, fg, s) {
                        scope.launch {
                            runCatching { Api.requestTranslation(s.novelId, s.index + 10, from = s.index) }.onFailure { msg = friendly(it); Mascot.react(OrbMood.Sad) }
                            l.reload()
                        }
                    }
                    c.paragraphs.isEmpty() -> Column(Modifier.fillMaxSize().padding(32.dp), Arrangement.Center, Alignment.CenterHorizontally) {
                        Text("Chương này chưa có nội dung dịch — bản cũ có thể bị lỗi.", textAlign = TextAlign.Center)
                        Spacer(Modifier.height(12.dp))
                        Button({
                            scope.launch { runCatching { Api.retranslate(s.novelId, s.index) }.onFailure { msg = friendly(it); Mascot.react(OrbMood.Sad) }; l.reload() }
                        }) { Text("Dịch lại chương") }
                    }
                    else -> {
                        // cơ duyên ngẫu nhiên theo (user, truyện, chương); đã nhận rồi thì ẩn nút
                        val after = Gift.after(Session.user?.id, s.novelId, c.index, c.paragraphs.size)
                        val giftUi: (@Composable () -> Unit)? = if (after < 0 || c.index in (claims.res?.getOrNull().orEmpty() + claimedNow)) null else ({
                            GiftButton(s.novelId, c.index, fg) { claimedNow = claimedNow + c.index; claims.reload() }
                        })
                        if (st.page) PagedChapter(c, st, body, title, terms, fg, insets, pageTurn, { pct }, setPct, goPaged, prev, next, hl, { p, off -> hitAt = off; menuPara = p }, giftUi) {}
                        else ScrollChapter(c, st, body, title, terms, fg, insets, list, hl, go, giftUi?.let { after to it }, prev, next, { k, off -> scrollCarry = k to off; autoCarry = auto; go(s.index + 1) }, { k, off -> scrollCarry = k to off; go(s.index - 1) }, Notes.all(ctx).filter { it.novelId == s.novelId && it.chapter == s.index }.map { it.idx }.toSet()) { p, off -> hitAt = off; menuPara = p }
                    }
                }
            }
        }
        }
        if (ttsOn) TtsBar(fg, bg) { sheet = 3 }
        }
        if (auto) Text(
            "Đang tự cuộn · chạm để dừng",
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 12.dp)
                .background(fg.copy(alpha = 0.08f), RoundedCornerShape(50)).padding(14.dp, 6.dp),
            color = fg.copy(alpha = 0.6f), fontSize = 12.sp, fontFamily = Jakarta,
        )
        msg?.let {
            Snackbar(
                Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(16.dp),
                action = if (!offerPatch) null else ({
                    TextButton({
                        offerPatch = false
                        msg = "Đang vá cả truyện ở chế độ nền…"
                        patchScope.launch { runCatching { Api.requestPatch(s.novelId) }.onFailure { msg = friendly(it); Mascot.react(OrbMood.Sad) } }
                    }) { Text("Áp cả truyện") }
                }),
            ) { Text(it) }
        }
    }

    if (sheet != 0) ModalBottomSheet({ sheet = 0 }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = MaterialTheme.colorScheme.card) {
        if (sheet == 1) TocSheet(s.novelId, s.index) { sheet = 0; go(it) }
        else if (sheet == 3) TtsSheet()
        else StyleSheet(st, dark) { sheet = 0; auto = true }
    }
    menuPara?.let { p -> ParaSheet(s.novelId, s.title, s.index, paras.indexOf(p), p, { menuPara = null; editing = p }, { menuPara = null }) { msg = it } }
    editing?.let { p ->
        ModalBottomSheet({ editing = null }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = MaterialTheme.colorScheme.card) {
            // câu gốc chỉ dùng được khi số dòng Trung khớp số đoạn Việt (dịch 1-1)
            val zh by produceState<String?>(null, s.novelId, s.index) {
                val lines = Api.chapterZh(s.novelId, s.index)
                value = if (lines.size == paras.size) lines.getOrNull(paras.indexOf(p)) else null
            }
            EditSheet(s.novelId, s.index, p, zh, hitAt, onDone = { m, isName -> editing = null; offerPatch = isName; msg = m; l.reload() }, onReport = { w, para -> editing = null; reportCtx = w to para; report = true })
        }
    }
    if (report) ReportDialog(reportCtx, { report = false }) { reason ->
        report = false
        scope.launch {
            runCatching { Api.report(s.novelId, s.index, reason) }.onSuccess { msg = "Đã gửi báo lỗi" }.onFailure { msg = friendly(it); Mascot.react(OrbMood.Sad) }
        }
    }
}


/**
 * Vị trí thuật ngữ trong [text] — luật giống glossaryRanges của Flutter: khớp theo ranh giới từ,
 * phân biệt hoa/thường, term dài thắng trước, không chồng lấn, trần [max] chỗ mỗi đoạn.
 */
internal fun glossRanges(text: String, terms: List<String>, max: Int = 60): List<Pair<Int, Int>> {
    val out = mutableListOf<Pair<Int, Int>>()
    for (t in terms.map { it.trim() }.filter { it.length >= 2 }.distinct().sortedByDescending { it.length }) {
        var from = 0
        while (out.size < max) {
            val i = text.indexOf(t, from)
            if (i < 0) break
            val end = i + t.length
            from = i + 1
            if (i > 0 && text[i - 1].isLetterOrDigit()) continue
            if (end < text.length && text[end].isLetterOrDigit()) continue
            if (out.any { i < it.second && end > it.first }) continue
            out += i to end
        }
    }
    return out.sortedBy { it.first }
}

internal fun marked(text: String, terms: List<String>): AnnotatedString {
    if (terms.isEmpty()) return AnnotatedString(text)
    return buildAnnotatedString {
        append(text)
        glossRanges(text, terms).forEach { (a, b) -> addStyle(SpanStyle(textDecoration = TextDecoration.Underline), a, b) }
    }
}

@Composable
private fun Waiting(status: String, fg: Color, s: Screen.Read? = null, request: () -> Unit) =
    Column(Modifier.fillMaxSize().padding(32.dp), Arrangement.Center, Alignment.CenterHorizontally) {
        val (label, canRequest) = when (status) {
            "queued" -> "Chương đang trong hàng đợi dịch…" to false
            "translating" -> "Đang dịch…" to false
            "asking" -> "Đang gửi yêu cầu dịch…" to false
            "toc" -> "Đang tải mục lục từ nguồn (~10–20 giây)…" to false
            "failed" -> "Dịch lỗi." to true
            else -> "Chương này chưa được dịch." to true
        }
        if (!canRequest) CircularProgressIndicator(color = fg)
        Spacer(Modifier.height(16.dp))
        Text(label, textAlign = TextAlign.Center, fontFamily = Jakarta)
        if (status == "queued" && s != null) {
            // vị trí trong hàng đợi + ước lượng; 20 giây hỏi lại một lần (lỗi mạng thì giữ dòng cũ)
            val eta by produceState<String?>(null, s.novelId, s.index) {
                while (true) { runCatching { Api.queueEta(s.novelId, s.index) }.onSuccess { value = it }; delay(20_000) }
            }
            eta?.let {
                Spacer(Modifier.height(6.dp))
                Text(it, textAlign = TextAlign.Center, fontFamily = Jakarta, style = MaterialTheme.typography.bodySmall, color = fg.copy(alpha = .7f))
            }
        }
        if (canRequest) {
            Spacer(Modifier.height(12.dp))
            Button(request) { Text(if (status == "failed") "Dịch lại" else "Yêu cầu dịch") }
        }
    }
