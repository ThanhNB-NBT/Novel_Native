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
import androidx.compose.material.icons.rounded.BookmarkBorder
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/** Màu nền/chữ khi đọc — chép readerColors của Flutter, GIỮ thứ tự vì rd_light/rd_dark lưu theo index. */
private class RColor(val name: String, val bg: Color, val fg: Color)

private val readerColors = listOf(
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

private class RFont(val key: String, val name: String, val family: FontFamily, val serif: Boolean)

private fun ff(regular: Int, bold: Int) = FontFamily(Font(regular), Font(bold, FontWeight.Bold))

// Đóng gói sẵn (Flutter tải từ Google Fonts lúc chạy): bản tĩnh 400/700 có bảng chữ Việt, ~40–70 KB mỗi file
private val readerFonts = listOf(
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
)

/** Khoá prefs giống bản Flutter (rd_*). */
private class Style {
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

/** Lật trang từ ngoài pager (phím âm lượng). */
private class PageTurn { var f: (suspend (Int) -> Unit)? = null }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(s: Screen.Read) {
    val st = remember { Style() }
    val appDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val dark = when (st.mode) { 1 -> false; 2 -> true; else -> appDark }
    val col = readerColors.getOrElse(if (dark) st.dark else st.light) { readerColors[13] }
    val bg = col.bg
    val fg = col.fg
    val scope = rememberCoroutineScope()
    var auto by remember { mutableStateOf(autoCarry.also { autoCarry = false }) }
    var sheet by remember { mutableIntStateOf(0) } // 0 không · 1 mục lục · 2 cài đặt
    var report by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<String?>(null) } // đoạn đang sửa bản dịch
    var msg by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(msg) { delay(2500); msg = null }
    val go = { i: Int -> if (i >= 1) Nav.replace(s.copy(index = i)) }

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

    LaunchedEffect(s.novelId, s.index) {
        runCatching { Api.requestToc(s.novelId) }
        if (Session.user != null) {
            if (Prefs.autoAhead) runCatching { Api.requestTranslation(s.novelId, s.index + 15) }
            runCatching { Api.saveProgress(s.novelId, s.index) }
        }
    }

    val l = load("ch", s.novelId, s.index) { Api.chapter(s.novelId, s.index) }
    val ch = l.res?.getOrNull()
    // Chưa dịch xong → hỏi lại mỗi 5s tới khi 'done' (Flutter dùng realtime, ở đây poll cho gọn)
    // Chương chưa có (truyện "lười" đang được crawler tải mục lục) cũng poll như vậy
    val loaded = l.res?.isSuccess == true
    LaunchedEffect(loaded, ch?.status) {
        if (loaded && ch?.status != "done" && (ch != null || Session.user != null)) while (true) { delay(5000); l.reload() }
    }
    val terms = if (st.gloss) load("gloss", s.novelId) { Api.glossary(s.novelId) }.res?.getOrNull().orEmpty() else emptyList()

    var pct by remember { mutableFloatStateOf(Prefs.percent(s.novelId, s.index)) }
    val setPct = { p: Float -> pct = p.coerceIn(0f, 1f); Prefs.setPercent(s.novelId, s.index, p) }
    val list = rememberLazyListState()
    val paras = ch?.paragraphs.orEmpty()
    val hl = if (TtsPlayer.novelId == s.novelId && TtsPlayer.chapter == s.index) TtsPlayer.paraAt else -1
    LaunchedEffect(hl) { if (hl >= 0 && !st.page) list.animateScrollToItem(1 + hl) } // cuộn theo đoạn đang đọc
    LaunchedEffect(Unit) { // máy đọc tự sang chương sau → màn đọc đi theo
        snapshotFlow { TtsPlayer.chapter }.drop(1).collect { c -> if (TtsPlayer.novelId == s.novelId && c != s.index) go(c) }
    }
    LaunchedEffect(s.index) { // tự đổi chương khi đang nghe thì dừng nghe (khác với máy đọc tự sang chương)
        if (TtsPlayer.novelId == s.novelId && TtsPlayer.chapter != s.index) TtsPlayer.stop()
    }
    // Khôi phục vị trí đọc dở trong chương (lưu theo % số đoạn, khoá giống Flutter)
    LaunchedEffect(paras.size, st.page) {
        if (paras.isEmpty() || st.page) return@LaunchedEffect
        val p = Prefs.percent(s.novelId, s.index)
        if (p > 0f) list.scrollToItem(1 + (p * paras.size).toInt().coerceAtMost(paras.size - 1))
        snapshotFlow { list.firstVisibleItemIndex to list.canScrollForward }.collectLatest { (i, more) ->
            setPct(if (!more) 1f else ((i - 1).coerceAtLeast(0).toFloat() / paras.size))
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
                        Text("Chương ${s.index}", Modifier.weight(1f), fontFamily = Jakarta, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        Text("${(pct * 100).roundToInt()}%", Modifier.padding(end = 4.dp), style = monoStyle(11, fg.copy(alpha = 0.45f)))
                        IconButton({
                            val i = if (st.page) (pct * paras.size).toInt() else list.firstVisibleItemIndex - 1
                            scope.launch {
                                runCatching { Api.addBookmark(s.novelId, s.index, pct, paras.getOrNull(i.coerceIn(0, (paras.size - 1).coerceAtLeast(0)))?.take(120)) }
                                    .onSuccess { msg = "Đã thêm dấu trang" }.onFailure { msg = friendly(it) }
                            }
                        }) { Icon(Icons.Rounded.BookmarkBorder, "Thêm dấu trang", Modifier.size(20.dp)) }
                        IconButton({ sheet = 1 }) { Icon(Icons.AutoMirrored.Rounded.FormatListBulleted, "Mục lục", Modifier.size(20.dp)) }
                        IconButton({ sheet = 2 }) { Icon(Icons.Rounded.TextFields, "Cài đặt đọc", Modifier.size(20.dp)) }
                        var menu by remember { mutableStateOf(false) }
                        Box {
                            IconButton({ menu = true }) { Icon(Icons.Rounded.MoreVert, "Thêm", Modifier.size(20.dp)) }
                            DropdownMenu(menu, { menu = false }) {
                                DropdownMenuItem({ Text(if (TtsPlayer.active) "Dừng nghe" else "Nghe truyện") }, {
                                    menu = false
                                    if (TtsPlayer.active) TtsPlayer.stop()
                                    else if (paras.isNotEmpty()) scope.launch {
                                        // đọc từ đoạn đang đọc dở, không từ đầu; truyền đoạn đang hiện để highlight khớp
                                        val from = (if (st.page) (pct * paras.size).toInt() else list.firstVisibleItemIndex - 1).coerceIn(0, paras.size - 1)
                                        TtsPlayer.start(view.context, s.novelId, s.index, from, paras, ch?.title)?.let { msg = it }
                                    }
                                })
                                DropdownMenuItem({ Text("Báo lỗi chương") }, { menu = false; report = true })
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
                    c == null -> Waiting("toc", fg) {}
                    c.status != "done" -> Waiting(c.status, fg) {
                        scope.launch {
                            runCatching { Api.requestTranslation(s.novelId, s.index + 10) }.onFailure { msg = friendly(it) }
                            l.reload()
                        }
                    }
                    c.paragraphs.isEmpty() -> Column(Modifier.fillMaxSize().padding(32.dp), Arrangement.Center, Alignment.CenterHorizontally) {
                        Text("Chương này chưa có nội dung dịch — bản cũ có thể bị lỗi.", textAlign = TextAlign.Center)
                        Spacer(Modifier.height(12.dp))
                        Button({
                            scope.launch { runCatching { Api.retranslate(s.novelId, s.index) }.onFailure { msg = friendly(it) }; l.reload() }
                        }) { Text("Dịch lại chương") }
                    }
                    st.page -> PagedChapter(c, st, body, title, terms, fg, insets, pageTurn, { pct }, setPct, go, hl, { editing = it }) {}
                    else -> ScrollChapter(c, st, body, title, terms, fg, insets, list, hl, go) { editing = it }
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
        msg?.let { Snackbar(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(16.dp)) { Text(it) } }
    }

    if (sheet != 0) ModalBottomSheet({ sheet = 0 }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = MaterialTheme.colorScheme.card) {
        if (sheet == 1) TocSheet(s.novelId, s.index) { sheet = 0; go(it) }
        else if (sheet == 3) TtsSheet()
        else StyleSheet(st, dark) { sheet = 0; auto = true }
    }
    editing?.let { p ->
        ModalBottomSheet({ editing = null }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = MaterialTheme.colorScheme.card) {
            // câu gốc chỉ dùng được khi số dòng Trung khớp số đoạn Việt (dịch 1-1)
            val zh by produceState<String?>(null, s.novelId, s.index) {
                val lines = Api.chapterZh(s.novelId, s.index)
                value = if (lines.size == paras.size) lines.getOrNull(paras.indexOf(p)) else null
            }
            EditSheet(s.novelId, s.index, p, zh, onDone = { editing = null; msg = it; l.reload() }, onReport = { editing = null; report = true })
        }
    }
    if (report) ReportDialog({ report = false }) { reason ->
        report = false
        scope.launch {
            runCatching { Api.report(s.novelId, s.index, reason) }.onSuccess { msg = "Đã gửi báo lỗi" }.onFailure { msg = friendly(it) }
        }
    }
}

/** Cuộn dọc. Cuộn hết rồi kéo tiếp quá mép → đổi chương (đo khoảng vượt mép lúc nhấc tay). */
@Composable
private fun ScrollChapter(c: Chapter, st: Style, body: TextStyle, title: TextStyle, terms: List<String>, fg: Color, insets: WindowInsets, list: LazyListState, hl: Int, go: (Int) -> Unit, onEdit: (String) -> Unit) {
    val haptic = LocalHapticFeedback.current
    val limit = with(LocalDensity.current) { 72.dp.toPx() }
    var over by remember { mutableFloatStateOf(0f) } // >0 kéo quá đáy · <0 kéo quá đỉnh
    val conn = remember(c.index) {
        object : NestedScrollConnection {
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput) over = if (consumed.y != 0f) 0f else over - available.y
                return Offset.Zero // để hiệu ứng kéo giãn mặc định vẫn chạy
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                val o = over
                over = 0f
                if (o > limit) go(c.index + 1) else if (o < -limit && c.index > 1) go(c.index - 1)
                return Velocity.Zero
            }
        }
    }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize().nestedScroll(conn), list, contentPadding = insets.asPaddingValues()) {
            item { Text(c.title, Modifier.padding(st.margin.dp, 24.dp, st.margin.dp, 20.dp), style = title) }
            itemsIndexed(c.paragraphs) { i, p ->
                // nhấn giữ một đoạn → sửa bản dịch (thay cho bôi chọn chữ của hệ thống)
                Text(
                    marked(p, terms),
                    Modifier.pointerInput(p) { detectTapGestures(onLongPress = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onEdit(p) }) }
                        .background(if (i == hl) fg.copy(alpha = 0.10f) else Color.Transparent) // đoạn đang được đọc
                        .padding(st.margin.dp, (st.size * 0.45f).dp),
                    style = body,
                )
            }
            item {
                Column(Modifier.fillMaxWidth().padding(st.margin.dp, 28.dp, st.margin.dp, 80.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Hết chương ${c.index} · kéo tiếp để sang chương sau", color = fg.copy(alpha = 0.45f), fontSize = 12.sp, fontFamily = Jakarta)
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(
                            { go(c.index - 1) }, Modifier.weight(1f).height(44.dp), enabled = c.index > 1,
                            border = BorderStroke(1.dp, fg.copy(alpha = 0.25f)), colors = ButtonDefaults.outlinedButtonColors(contentColor = fg),
                        ) { Text("‹ Chương trước") }
                        Button({ go(c.index + 1) }, Modifier.weight(1f).height(44.dp)) { Text("Chương sau ›") }
                    }
                }
            }
        }
        if (abs(over) > 8f && (over > 0 || c.index > 1)) {
            val next = over > 0
            val ready = abs(over) > limit
            Text(
                (if (ready) "Thả tay để sang " else "Kéo tiếp để sang ") + (if (next) "chương sau" else "chương trước"),
                Modifier.align(if (next) Alignment.BottomCenter else Alignment.TopCenter).safeDrawingPadding().padding(20.dp)
                    .background(if (ready) MaterialTheme.colorScheme.primary else fg.copy(alpha = 0.1f), RoundedCornerShape(50)).padding(16.dp, 8.dp),
                color = if (ready) MaterialTheme.colorScheme.onPrimary else fg, fontSize = 13.sp, fontFamily = Jakarta, fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

/**
 * Lật trang: cắt chương thành trang vừa khung bằng MỘT lần đo cả chương (tách theo dòng).
 * Trang đệm hai đầu: vuốt qua trang cuối → chương sau, vuốt ngược trước trang đầu → chương trước.
 * Chạm 25% trái lùi trang, 25% phải sang trang, giữa ẩn/hiện thanh.
 */
@Composable
private fun PagedChapter(
    c: Chapter, st: Style, body: TextStyle, title: TextStyle, terms: List<String>, fg: Color, insets: WindowInsets,
    turn: PageTurn, startPct: () -> Float, onPct: (Float) -> Unit, go: (Int) -> Unit, hl: Int, onEdit: (String) -> Unit, tap: () -> Unit,
) = BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(insets)) {
    val m = rememberTextMeasurer()
    val d = LocalDensity.current
    val scope = rememberCoroutineScope()
    val w = constraints.maxWidth - with(d) { st.margin.dp.roundToPx() } * 2
    val h = constraints.maxHeight - with(d) { 20.dp.toPx() } - 4f
    val gap = with(d) { 18.dp.toPx() }
    val pages = remember(c.paragraphs, body, title, w, h) {
        val th = m.measure(c.title, title, constraints = Constraints(maxWidth = w)).size.height
        paginate(m, c.paragraphs.joinToString("\n\n"), body, w, h, (h - th - gap).coerceAtLeast(60f))
    }
    val lead = if (c.index > 1) 1 else 0
    val total = pages.size + lead + 2 // + trang "hết chương" + trang đệm sau
    fun pageOf(p: Float) = lead + if (pages.size > 1) (p * (pages.size - 1)).roundToInt() else 0
    val pager = rememberPagerState(pageOf(startPct())) { total }
    LaunchedEffect(pages) {
        pager.scrollToPage(pageOf(startPct())) // đổi cỡ chữ/font → giữ đúng chỗ đang đọc
        snapshotFlow { pager.currentPage to pager.isScrollInProgress }.filter { !it.second }.map { it.first }.distinctUntilChanged().collect { i ->
            when {
                lead == 1 && i == 0 -> go(c.index - 1)
                i == total - 1 -> go(c.index + 1)
                i < total - 2 -> onPct(if (pages.size > 1) (i - lead).toFloat() / (pages.size - 1) else 1f)
            }
        }
    }
    // Trang là các lát liên tiếp của văn bản nối ("\n\n" giữa đoạn) → tính vùng từng trang/đoạn để tô nền đoạn đang nghe
    val joined = remember(c.paragraphs) { c.paragraphs.joinToString("\n\n") }
    val pageStart = remember(pages, joined) { var from = 0; pages.map { p -> joined.indexOf(p, from).also { if (it >= 0) from = it + p.length } } }
    val hlRange = remember(hl, c.paragraphs) {
        if (hl !in c.paragraphs.indices) null
        else (0 until hl).sumOf { c.paragraphs[it].length + 2 }.let { it until it + c.paragraphs[hl].length }
    }
    LaunchedEffect(hl, pages) { // đoạn đang đọc không còn trên trang hiện tại → lật tới trang đầu của đoạn
        val r = hlRange ?: return@LaunchedEffect
        fun overlaps(i: Int) = pageStart[i] >= 0 && pageStart[i] <= r.last && pageStart[i] + pages[i].length > r.first
        if (pager.currentPage - lead in pages.indices && overlaps(pager.currentPage - lead)) return@LaunchedEffect
        pages.indices.firstOrNull(::overlaps)?.let { pager.animateScrollToPage(lead + it) }
    }
    SideEffect { turn.f = { dir -> pager.animateScrollToPage((pager.currentPage + dir).coerceIn(0, total - 1)) } }
    HorizontalPager(pager, Modifier.fillMaxSize(), beyondViewportPageCount = 1) { i ->
        when {
            lead == 1 && i == 0 -> PageEdge(false, fg)
            i == total - 1 -> PageEdge(true, fg)
            i == total - 2 -> Column(Modifier.fillMaxSize().padding(24.dp), Arrangement.Center, Alignment.CenterHorizontally) {
                Text("Hết chương ${c.index}", color = fg.copy(alpha = 0.5f), fontSize = 13.sp, fontFamily = Jakarta)
                Spacer(Modifier.height(10.dp))
                Text("vuốt tiếp để sang chương sau →", color = fg.copy(alpha = 0.4f), fontSize = 12.sp, fontFamily = Jakarta)
            }
            else -> Box(
                Modifier.fillMaxSize().pointerInput(Unit) {
                    detectTapGestures { o ->
                        val x = o.x / size.width
                        when {
                            x < 0.25f -> scope.launch { turn.f?.invoke(-1) }
                            x > 0.75f -> scope.launch { turn.f?.invoke(1) }
                            else -> tap()
                        }
                    }
                },
            ) {
                // gáy sách mờ ở mép trái cho cảm giác trang giấy
                Box(Modifier.fillMaxHeight().width(14.dp).background(Brush.horizontalGradient(listOf(Color.Black.copy(alpha = 0.04f), Color.Transparent))))
                Column(Modifier.padding(st.margin.dp, 10.dp)) {
                    if (i == lead) {
                        Text(c.title, style = title)
                        Spacer(Modifier.height(18.dp))
                    }
                    val pageText = pages[i - lead]
                    var lay by remember(pageText) { mutableStateOf<TextLayoutResult?>(null) }
                    Text(
                        marked(pageText, terms).let { m ->
                            val ps = pageStart.getOrElse(i - lead) { -1 }
                            val r = hlRange
                            if (r == null || ps < 0) m else buildAnnotatedString {
                                append(m)
                                val a = (r.first - ps).coerceIn(0, m.length)
                                val b = (r.last + 1 - ps).coerceIn(0, m.length)
                                if (b > a) addStyle(SpanStyle(background = fg.copy(alpha = 0.10f)), a, b)
                            }
                        },
                        // nhấn giữ → tìm đoạn chứa chỗ chạm (một đoạn có thể bị cắt dở giữa hai trang)
                        Modifier.pointerInput(pageText) {
                            detectTapGestures(onLongPress = { o ->
                                val off = lay?.getOffsetForPosition(o)?.coerceIn(0, pageText.length) ?: return@detectTapGestures
                                var acc = 0
                                val chunk = pageText.split("\n\n").firstOrNull { acc += it.length + 2; off < acc }?.trim().orEmpty()
                                c.paragraphs.firstOrNull { it.contains(chunk) }?.let(onEdit)
                            })
                        },
                        onTextLayout = { lay = it }, style = body,
                    )
                }
            }
        }
    }
}

@Composable
private fun PageEdge(next: Boolean, fg: Color) = Column(Modifier.fillMaxSize(), Arrangement.Center, Alignment.CenterHorizontally) {
    Icon(if (next) Icons.AutoMirrored.Rounded.ArrowForward else Icons.AutoMirrored.Rounded.ArrowBack, null, tint = fg.copy(alpha = 0.5f))
    Spacer(Modifier.height(8.dp))
    Text(if (next) "Chương sau" else "Chương trước", color = fg)
}

/** Cắt [text] thành trang: dòng nào tràn quá chiều cao trang thì mở trang mới từ đầu dòng đó. */
private fun paginate(m: TextMeasurer, text: String, style: TextStyle, w: Int, h: Float, firstH: Float): List<String> {
    val lay = m.measure(text, style, constraints = Constraints(maxWidth = w))
    val out = mutableListOf<String>()
    var start = 0
    var top = 0f
    var limit = firstH
    for (i in 0 until lay.lineCount) {
        if (lay.getLineBottom(i) - top > limit) {
            val s = lay.getLineStart(i)
            if (s > start) out += text.substring(start, s).trim()
            start = s
            top = lay.getLineTop(i)
            limit = h
        }
    }
    out += text.substring(start).trim()
    return out.filter { it.isNotEmpty() }.ifEmpty { listOf("") }
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

private fun marked(text: String, terms: List<String>): AnnotatedString {
    if (terms.isEmpty()) return AnnotatedString(text)
    return buildAnnotatedString {
        append(text)
        glossRanges(text, terms).forEach { (a, b) -> addStyle(SpanStyle(textDecoration = TextDecoration.Underline), a, b) }
    }
}

@Composable
private fun Waiting(status: String, fg: Color, request: () -> Unit) =
    Column(Modifier.fillMaxSize().padding(32.dp), Arrangement.Center, Alignment.CenterHorizontally) {
        val (label, canRequest) = when (status) {
            "queued" -> "Chương đang trong hàng đợi dịch…" to false
            "translating" -> "Đang dịch…" to false
            "toc" -> "Đang tải mục lục từ nguồn (~10–20 giây)…" to false
            "failed" -> "Dịch lỗi." to true
            else -> "Chương này chưa được dịch." to true
        }
        if (!canRequest) CircularProgressIndicator(color = fg)
        Spacer(Modifier.height(16.dp))
        Text(label, textAlign = TextAlign.Center, fontFamily = Jakarta)
        if (canRequest) {
            Spacer(Modifier.height(12.dp))
            Button(request) { Text(if (status == "failed") "Dịch lại" else "Yêu cầu dịch") }
        }
    }

@Composable
private fun TocSheet(novelId: Int, current: Int, pick: (Int) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val toc = load("toc", novelId) { Api.toc(novelId) }
    Column(Modifier.fillMaxHeight(0.75f)) {
        Text("Mục lục", Modifier.padding(20.dp, 0.dp, 20.dp, 8.dp), style = MaterialTheme.typography.titleLarge)
        Loaded(toc, loading = { TextSkeleton() }) { chapters ->
            val list = rememberLazyListState((chapters.indexOfFirst { it.index == current } - 3).coerceAtLeast(0))
            LazyColumn(state = list) {
                itemsIndexed(chapters, key = { _, c -> c.index }) { _, c ->
                    val on = c.index == current
                    Row(
                        Modifier.fillMaxWidth().clickable { pick(c.index) }.background(if (on) cs.primaryContainer else Color.Transparent).padding(20.dp, 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("${c.index}", Modifier.width(48.dp), style = monoStyle(12, if (on) cs.primary else cs.onSurfaceVariant))
                        Text(
                            c.title, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, fontFamily = Jakarta,
                            fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                            color = when { on -> cs.primary; c.done -> cs.onSurface; else -> cs.onSurfaceVariant.copy(alpha = 0.6f) },
                        )
                    }
                }
            }
        }
    }
}

/** Cài đặt đọc: gói gọn một màn hình (không cuộn) — màu/font là một hàng vuốt ngang, công tắc là chip. */
@Composable
private fun StyleSheet(st: Style, dark: Boolean, startAuto: () -> Unit) {
    DisposableEffect(Unit) { onDispose { st.save() } }
    val cs = MaterialTheme.colorScheme
    val col = readerColors.getOrElse(if (dark) st.dark else st.light) { readerColors[13] }
    Column(Modifier.verticalScroll(rememberScrollState()).padding(20.dp, 0.dp, 20.dp, 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Cài đặt đọc", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            Seg(listOf("Cuộn", "Lật trang"), if (st.page) 1 else 0, Modifier.width(172.dp)) { st.page = it == 1 }
        }
        Spacer(Modifier.height(8.dp))
        // khung xem trước: nền/chữ/font/cỡ/giãn/lề đều là giá trị THẬT đang chọn
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(col.bg).border(1.dp, cs.outlineVariant, RoundedCornerShape(12.dp))
                .padding((st.margin / 1.6f).dp, 10.dp),
        ) {
            Text(
                "Lục Trầm khẽ nhắm mắt, linh khí quanh thân tụ lại như sương sớm. “Đạo hữu, mời.”", maxLines = 2, overflow = TextOverflow.Ellipsis,
                style = TextStyle(
                    fontFamily = st.rfont.family, fontSize = st.size.sp, lineHeight = (st.size * st.lh).sp, color = col.fg,
                    textAlign = if (st.justify) TextAlign.Justify else TextAlign.Start,
                ),
            )
        }

        SheetLabel("Màu nền", if (dark) "nền tối" else "nền sáng") {
            Seg(listOf("Tự động", "Sáng", "Tối"), st.mode, Modifier.width(216.dp)) { st.mode = it }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            readerColors.forEachIndexed { i, rc ->
                val on = i == (if (dark) st.dark else st.light)
                Box(
                    Modifier.size(38.dp).clip(CircleShape).background(rc.bg)
                        .border(if (on) 2.4.dp else 1.dp, if (on) cs.primary else cs.outlineVariant, CircleShape)
                        .clickable { if (dark) st.dark = i else st.light = i },
                    Alignment.Center,
                ) { Text("A", color = rc.fg, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
            }
        }

        // hai hàng có nhãn: font có chân / không chân (mỗi hàng tự cuộn tới font đang chọn)
        SheetLabel("Phông chữ")
        FontRow("Có chân", readerFonts.filter { it.serif }, st)
        Spacer(Modifier.height(10.dp))
        FontRow("Không chân", readerFonts.filter { !it.serif }, st)

        SheetLabel("Bố cục")
        SliderRow("Cỡ chữ", "${st.size.roundToInt()}", st.size, 15f..28f) { st.size = it }
        SliderRow("Giãn dòng", "%.1f×".format(st.lh), st.lh, 1.3f..2.2f) { st.lh = (it * 10).roundToInt() / 10f }
        SliderRow("Viền 2 bên", "${st.margin.roundToInt()}", st.margin, 8f..48f) { st.margin = it }
        if (!st.page) SliderRow("Tự cuộn", "${st.speed.roundToInt()}", st.speed, 15f..120f, startAuto) { st.speed = it }

        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Tile("Căn đều", Icons.Rounded.FormatAlignJustify, st.justify, Modifier.weight(1f)) { st.justify = it }
            Tile("Gạch thuật ngữ", Icons.Rounded.FormatUnderlined, st.gloss, Modifier.weight(1f)) { st.gloss = it }
            Tile("Phím âm lượng", Icons.AutoMirrored.Rounded.VolumeUp, st.volume, Modifier.weight(1f)) { st.volume = it }
        }
    }
}

/** Một hàng font có nhãn nhỏ phía trên; hàng tự cuộn để font đang chọn (nếu thuộc hàng này) hiện ra. */
@Composable
private fun FontRow(label: String, fonts: List<RFont>, st: Style) {
    val cs = MaterialTheme.colorScheme
    Text(label, Modifier.padding(start = 2.dp, bottom = 6.dp), style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
    androidx.compose.foundation.lazy.LazyRow(
        state = rememberLazyListState(fonts.indexOfFirst { it.key == st.font }.coerceAtLeast(0)),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(fonts) { f ->
            val on = st.font == f.key
            Text(
                f.name,
                Modifier.height(36.dp).clip(RoundedCornerShape(18.dp))
                    .background(if (on) cs.primary.copy(alpha = 0.10f) else Color.Transparent)
                    .border(if (on) 1.4.dp else 1.dp, if (on) cs.primary.copy(alpha = 0.65f) else cs.outlineVariant, RoundedCornerShape(18.dp))
                    .clickable { st.font = f.key }.wrapContentHeight().padding(horizontal = 14.dp),
                fontFamily = f.family, fontSize = 14.sp, color = if (on) cs.primary else cs.onSurface,
            )
        }
    }
}

/** Ô bật/tắt: icon + nhãn ngắn, ba ô rộng bằng nhau; bật = nền nhấn nhạt + viền nhấn. */
@Composable
private fun Tile(label: String, icon: ImageVector, on: Boolean, modifier: Modifier, set: (Boolean) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier.height(62.dp).clip(shape).background(if (on) cs.primary.copy(alpha = 0.10f) else Color.Transparent)
            .border(if (on) 1.4.dp else 1.dp, if (on) cs.primary.copy(alpha = 0.65f) else cs.outlineVariant, shape)
            .clickable { set(!on) },
        Arrangement.Center, Alignment.CenterHorizontally,
    ) {
        Icon(icon, null, Modifier.size(22.dp), tint = if (on) cs.primary else cs.onSurfaceVariant)
        Spacer(Modifier.height(3.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1, color = if (on) cs.primary else cs.onSurfaceVariant, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium)
    }
}

@Composable
private fun SheetLabel(text: String, hint: String? = null, end: @Composable () -> Unit = {}) =
    Row(Modifier.padding(start = 2.dp, top = 16.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text.uppercase(), style = MaterialTheme.typography.labelSmall, letterSpacing = 1.2.sp, color = MaterialTheme.colorScheme.primary)
        hint?.let { Text(it, Modifier.padding(start = 8.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium, maxLines = 1) }
        Spacer(Modifier.weight(1f))
        end()
    }

/** Segmented: viên màu nhấn trượt giữa các ô (đồng bộ với Flutter). */
@Composable
private fun Seg(labels: List<String>, value: Int, modifier: Modifier = Modifier, pick: (Int) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(12.dp)
    BoxWithConstraints(modifier.fillMaxWidth().height(36.dp).clip(shape).background(cs.background).border(1.dp, cs.outlineVariant, shape).padding(4.dp)) {
        val w = maxWidth / labels.size
        val x by animateDpAsState(w * value, tween(260), label = "seg")
        Box(Modifier.offset(x = x).width(w).fillMaxHeight().background(cs.primary, RoundedCornerShape(9.dp)))
        Row(Modifier.fillMaxSize()) {
            labels.forEachIndexed { i, l ->
                Box(Modifier.weight(1f).fillMaxHeight().clickable(remember { MutableInteractionSource() }, null) { pick(i) }, Alignment.Center) {
                    Text(
                        l, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, maxLines = 1,
                        color = if (i == value) cs.onPrimary else cs.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SliderRow(
    label: String, value: String, v: Float, range: ClosedFloatingPointRange<Float>, play: (() -> Unit)? = null, set: (Float) -> Unit,
) = Row(Modifier.height(44.dp), verticalAlignment = Alignment.CenterVertically) {
    val cs = MaterialTheme.colorScheme
    Text(label, Modifier.width(78.dp), style = MaterialTheme.typography.labelMedium)
    Slider(
        v, set, Modifier.weight(1f), valueRange = range,
        thumb = { Box(Modifier.size(20.dp).shadow(2.dp, CircleShape).background(cs.primary, CircleShape).border(3.dp, cs.card, CircleShape)) },
        track = { SliderDefaults.Track(it, Modifier.height(4.dp), drawStopIndicator = null, thumbTrackGapSize = 0.dp, trackInsideCornerSize = 0.dp) },
    )
    Text(value, Modifier.width(40.dp), textAlign = TextAlign.End, style = monoStyle(13, cs.onSurface))
    if (play != null) Box(Modifier.padding(start = 4.dp).size(30.dp).clip(CircleShape).background(cs.primary).clickable(onClick = play), Alignment.Center) {
        Icon(Icons.Rounded.PlayArrow, "Bắt đầu tự cuộn", Modifier.size(20.dp), tint = cs.onPrimary)
    }
}

@Composable
internal fun Opt(label: String, on: Boolean, set: (Boolean) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(17.dp)
    Row(
        Modifier.height(34.dp).clip(shape).background(if (on) cs.primary.copy(alpha = 0.12f) else cs.background)
            .border(1.dp, if (on) cs.primary.copy(alpha = 0.65f) else cs.outlineVariant, shape)
            .clickable { set(!on) }.padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (on) Icon(Icons.Rounded.Check, null, Modifier.padding(end = 6.dp).size(15.dp), tint = cs.primary)
        Text(label, style = MaterialTheme.typography.labelMedium, color = if (on) cs.primary else cs.onSurfaceVariant)
    }
}

@Composable
private fun ReportDialog(dismiss: () -> Unit, send: (String) -> Unit) {
    var reason by remember { mutableStateOf("") }
    AlertDialog(
        dismiss,
        confirmButton = { TextButton({ send(reason.trim()) }, enabled = reason.isNotBlank()) { Text("Gửi") } },
        dismissButton = { TextButton(dismiss) { Text("Huỷ") } },
        title = { Text("Báo lỗi chương") },
        text = { OutlinedTextField(reason, { reason = it }, placeholder = { Text("Sai tên, sót đoạn, lẫn chữ Trung…") }) },
    )
}
