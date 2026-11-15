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

/**
 * Lật trang: cắt chương thành trang vừa khung bằng MỘT lần đo cả chương (tách theo dòng).
 * Chương kề (đã tải trước) nối liền hai đầu: trang cuối chương trước nằm trước trang đầu, trang đầu chương sau nằm sau trang
 * cuối; lướt tới đó thì đổi chương đúng trang ấy nên không thấy mối nối. Chưa có chương kề thì dùng trang đệm.
 * Chạm 25% trái lùi trang, 25% phải sang trang, giữa ẩn/hiện thanh.
 */
@Composable
internal fun PagedChapter(
    c: Chapter, st: Style, body: TextStyle, title: TextStyle, terms: List<String>, fg: Color, insets: WindowInsets,
    turn: PageTurn, startPct: () -> Float, onPct: (Float) -> Unit, go: (Int) -> Unit, prev: Chapter?, next: Chapter?,
    hl: Int, onEdit: (String, Int) -> Unit, gift: (@Composable () -> Unit)?, tap: () -> Unit,
) = BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(insets)) {
    val m = rememberTextMeasurer()
    val d = LocalDensity.current
    val scope = rememberCoroutineScope()
    val w = constraints.maxWidth - with(d) { st.margin.dp.roundToPx() } * 2
    val h = constraints.maxHeight - with(d) { 20.dp.toPx() } - 4f
    val gap = with(d) { 18.dp.toPx() }
    val pages = remember(c.paragraphs, body, title, w, h) { chapterPages(m, c, title, body, w, h, gap) }
    // Chương trước phải đo cả chương mới biết trang cuối → đo ngầm bằng TextMeasurer riêng (cái dùng chung không an toàn
    // đa luồng). Chưa đo xong thì trang đầu vẫn là trang đệm "Chương trước"; đo lệch với lúc mở thật thì mối nối giật.
    val resolver = LocalFontFamilyResolver.current
    val dir = LocalLayoutDirection.current
    val prevPages by produceState<List<String>?>(null, prev, body, title, w, h) {
        value = null
        value = prev?.let { withContext(Dispatchers.Default) { chapterPages(TextMeasurer(resolver, d, dir), it, title, body, w, h, gap) } }
    }
    val nextFirst = remember(next, body, title, w, h) { next?.let { chapterPages(m, it, title, body, w, h, gap, head = true).first() } }
    val lead = if (c.index > 1) 1 else 0
    val hasEnd = next == null || gift != null // trang "hết chương": cần khi có quà hoặc chưa có chương sau để nối
    val total = lead + pages.size + (if (hasEnd) 1 else 0) + 1
    val totalNow by rememberUpdatedState(total) // chương kề tải xong giữa chừng làm đổi số trang; vòng lắng nghe bên dưới sống lâu hơn
    fun pageOf(p: Float) = lead + if (pages.size > 1) (p * (pages.size - 1)).roundToInt() else 0
    val pager = rememberPagerState(pageOf(startPct())) { total }
    LaunchedEffect(pages) {
        pager.scrollToPage(pageOf(startPct())) // đổi cỡ chữ/font → giữ đúng chỗ đang đọc
        snapshotFlow { pager.currentPage to pager.isScrollInProgress }.filter { !it.second }.map { it.first }.distinctUntilChanged().collect { i ->
            when {
                lead == 1 && i == 0 -> go(c.index - 1)
                i == totalNow - 1 -> go(c.index + 1)
                i in lead until lead + pages.size -> onPct(if (pages.size > 1) (i - lead).toFloat() / (pages.size - 1) else 1f)
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
            lead == 1 && i == 0 ->
                prevPages.let { pp ->
                    if (prev == null || pp == null) PageEdge(false, fg)
                    else PageFrame(st, turn, tap, scope) { PageText(pp.last(), if (pp.size == 1) prev.title else null, terms, body, title) }
                }
            i == total - 1 ->
                if (next == null || nextFirst == null) PageEdge(true, fg)
                else PageFrame(st, turn, tap, scope) { PageText(nextFirst, next.title, terms, body, title) }
            hasEnd && i == total - 2 -> Column(Modifier.fillMaxSize().padding(24.dp), Arrangement.Center, Alignment.CenterHorizontally) {
                Text("Hết chương ${c.index}", color = fg.copy(alpha = 0.5f), fontSize = 13.sp, fontFamily = Jakarta)
                gift?.invoke() // lật trang: quà nằm ở trang cuối chương, không chen vào trang chữ
                Spacer(Modifier.height(10.dp))
                Text("vuốt tiếp để sang chương sau →", color = fg.copy(alpha = 0.4f), fontSize = 12.sp, fontFamily = Jakarta)
            }
            else -> PageFrame(st, turn, tap, scope) {
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
                            var chunkStart = 0
                            val chunk = pageText.split("\n\n").firstOrNull { val st0 = acc; acc += it.length + 2; (off < acc).also { hit -> if (hit) chunkStart = st0 } }?.trim().orEmpty()
                            c.paragraphs.firstOrNull { it.contains(chunk) }?.let { para -> onEdit(para, (para.indexOf(chunk) + off - chunkStart).coerceIn(0, para.length)) }
                        })
                    },
                    onTextLayout = { lay = it }, style = body,
                )
            }
        }
    }
}

/** Khung một trang chữ: chạm 25% trái lùi, 25% phải tiến, giữa ẩn/hiện thanh; gáy sách mờ ở mép trái. */
@Composable
private fun PageFrame(st: Style, turn: PageTurn, tap: () -> Unit, scope: kotlinx.coroutines.CoroutineScope, content: @Composable ColumnScope.() -> Unit) = Box(
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
    Box(Modifier.fillMaxHeight().width(14.dp).background(Brush.horizontalGradient(listOf(Color.Black.copy(alpha = 0.04f), Color.Transparent))))
    Column(Modifier.padding(st.margin.dp, 10.dp), content = content)
}

/** Trang chữ chỉ xem (trang của chương kề); [heading] khác null thì có tiêu đề chương ở đầu trang. */
@Composable
private fun PageText(text: String, heading: String?, terms: List<String>, body: TextStyle, title: TextStyle) {
    if (heading != null) {
        Text(heading, style = title)
        Spacer(Modifier.height(18.dp))
    }
    Text(marked(text, terms), style = body)
}

/** Các trang của một chương; [head] = chỉ cần trang đầu nên đo phần đầu văn bản cho nhanh (trang đầu không phụ thuộc phần sau). */
private fun chapterPages(m: TextMeasurer, c: Chapter, title: TextStyle, body: TextStyle, w: Int, h: Float, gap: Float, head: Boolean = false): List<String> {
    val th = m.measure(c.title, title, constraints = Constraints(maxWidth = w)).size.height
    val text = c.paragraphs.joinToString("\n\n").let { if (head) it.take(4000) else it }
    return paginate(m, text, body, w, h, (h - th - gap).coerceAtLeast(60f))
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
