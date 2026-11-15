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
 * Cuộn dọc. Chương trước và chương sau (nếu đã tải trước) nối liền hai đầu chương này; khi chương kia chiếm hết màn
 * và tay đã nhả, đổi sang chương đó với đúng vị trí đang nhìn nên không thấy mối nối. Chưa có thì kéo quá mép → đổi
 * chương (đo khoảng vượt mép lúc nhấc tay).
 */
@Composable
internal fun ScrollChapter(
    c: Chapter, st: Style, body: TextStyle, title: TextStyle, terms: List<String>, fg: Color, insets: WindowInsets, list: LazyListState, hl: Int,
    go: (Int) -> Unit, gift: Pair<Int, @Composable () -> Unit>?, prev: Chapter?, next: Chapter?,
    enterNext: (Int, Int) -> Unit, enterPrev: (Int, Int) -> Unit, saved: Set<Int>, onEdit: (String, Int) -> Unit,
) {
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
    val prevParas = prev?.paragraphs?.takeLast(PREVIEW_PARAS).orEmpty()
    val lead = leadOf(prev)
    // thứ tự item: [đoạn cuối chương trước · vạch] tiêu đề(lead) · các đoạn · vạch hết chương · [tiêu đề chương sau · đoạn đầu]
    val nextTitle = nextTitleIndex(prev, c)
    val nextParas = next?.paragraphs?.take(PREVIEW_PARAS).orEmpty()
    // khoá theo cả nextTitle: chương trước tải xong SAU chương sau sẽ chèn `lead` item lên đầu → mốc cũ lệch, sang chương sớm ~17 đoạn
    if (next != null) LaunchedEffect(next, nextTitle) {
        val at = snapshotFlow { Triple(list.firstVisibleItemIndex, list.firstVisibleItemScrollOffset, list.isScrollInProgress) }
            .first { (i, _, moving) -> i >= nextTitle && !moving }
        enterNext(at.first - nextTitle, at.second)
    }
    if (prev != null) LaunchedEffect(prev) {
        // chỉ còn thấy chương trước (mọi item hiện ra đều thuộc phần xem trước) và tay đã nhả
        val at = snapshotFlow {
            val info = list.layoutInfo
            val sep = info.visibleItemsInfo.firstOrNull { it.index == prevParas.size } // vạch hết chương trước
            val above = list.firstVisibleItemIndex < prevParas.size && (sep == null || sep.offset >= info.viewportEndOffset)
            Triple(list.firstVisibleItemIndex.takeIf { above }, list.firstVisibleItemScrollOffset, list.isScrollInProgress)
        }.first { (i, _, moving) -> i != null && !moving }
        enterPrev(1 + prev.paragraphs.size - prevParas.size + at.first!!, at.second)
    }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize().nestedScroll(conn), list, contentPadding = insets.asPaddingValues()) {
            if (prev != null) {
                itemsIndexed(prevParas, key = { i, _ -> "q$i" }) { _, p -> Para(p, st, body, terms, fg, false, null) }
                item(key = "qe") { ChapterEnd("Hết chương ${prev.index}", fg, Modifier.padding(0.dp, 28.dp, 0.dp, 8.dp)) }
            }
            item(key = "t") { ChapterTitle(c.title, title, st.margin) }
            itemsIndexed(c.paragraphs, key = { i, _ -> "p$i" }) { i, p ->
                Para(p, st, body, terms, fg, i == hl, onEdit, i in saved)
                // quà nằm NGAY DƯỚI đoạn của nó, cùng một item để chỉ số đoạn (tiến độ, TTS) không lệch
                if (gift != null && gift.first == i) gift.second()
            }
            item(key = "e") {
                ChapterEnd(
                    if (next == null) "Hết chương ${c.index} · kéo tiếp để sang chương sau" else "Hết chương ${c.index}", fg,
                    Modifier.padding(st.margin.dp, 28.dp, st.margin.dp, if (next == null) 80.dp else 8.dp),
                )
            }
            if (next != null) {
                item(key = "nt") { ChapterTitle(next.title, title, st.margin) }
                itemsIndexed(nextParas, key = { i, _ -> "n$i" }) { _, p -> Para(p, st, body, terms, fg, false, null) }
            }
        }
        if (abs(over) > 8f && (if (over > 0) next == null else prev == null && c.index > 1)) {
            val nx = over > 0
            val ready = abs(over) > limit
            Text(
                (if (ready) "Thả tay để sang " else "Kéo tiếp để sang ") + (if (nx) "chương sau" else "chương trước"),
                Modifier.align(if (nx) Alignment.BottomCenter else Alignment.TopCenter).safeDrawingPadding().padding(20.dp)
                    .background(if (ready) MaterialTheme.colorScheme.primary else fg.copy(alpha = 0.1f), RoundedCornerShape(50)).padding(16.dp, 8.dp),
                color = if (ready) MaterialTheme.colorScheme.onPrimary else fg, fontSize = 13.sp, fontFamily = Jakarta, fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

/** Tiêu đề chương (chế độ cuộn) kèm một nét son ngắn bên dưới. */
@Composable
private fun ChapterTitle(text: String, style: TextStyle, margin: Float) = Column(Modifier.padding(margin.dp, 24.dp, margin.dp, 20.dp)) {
    Text(text, style = style)
    Box(Modifier.padding(top = 14.dp).size(36.dp, 3.dp).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(50)))
}

/** Vạch hết chương: nét mảnh – dấu thoi màu nhấn – nét mảnh, dòng chữ nhỏ bên dưới. */
@Composable
private fun ChapterEnd(text: String, fg: Color, modifier: Modifier) = Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(48.dp, 1.dp).background(fg.copy(alpha = .22f)))
        Box(Modifier.padding(horizontal = 12.dp).size(7.dp).rotate(45f).background(MaterialTheme.colorScheme.primary))
        Box(Modifier.size(48.dp, 1.dp).background(fg.copy(alpha = .22f)))
    }
    Text(text, Modifier.padding(top = 12.dp), color = fg.copy(alpha = 0.45f), fontSize = 12.sp, fontFamily = Jakarta, textAlign = TextAlign.Center)
}

/** Số item của chương trước (đoạn cuối + vạch) đặt phía trên tiêu đề chương hiện tại; 0 nếu chưa có chương trước. */
internal fun leadOf(prev: Chapter?) = if (prev == null) 0 else minOf(PREVIEW_PARAS, prev.paragraphs.size) + 1
/** Chỉ số item tiêu đề chương sau trong danh sách cuộn: lead · tiêu đề · các đoạn · vạch hết chương. */
internal fun nextTitleIndex(prev: Chapter?, c: Chapter) = leadOf(prev) + c.paragraphs.size + 2

internal const val PREVIEW_PARAS = 16

/** Một đoạn văn; nhấn giữ → sửa bản dịch (thay cho bôi chọn chữ của hệ thống). [onEdit] null = chỉ xem (đoạn của chương sau). */
@Composable
private fun Para(p: String, st: Style, body: TextStyle, terms: List<String>, fg: Color, hi: Boolean, onEdit: ((String, Int) -> Unit)?, saved: Boolean = false) {
    val haptic = LocalHapticFeedback.current
    val bar = MaterialTheme.colorScheme.primary
    var lay by remember(p) { mutableStateOf<TextLayoutResult?>(null) }
    Text(
        marked(p, terms),
        (if (onEdit == null) Modifier else Modifier.pointerInput(p) {
            // toạ độ chạm tính từ mép ngoài (kể cả lề) → trừ lề để ra vị trí trong chữ
            val pad = Offset(st.margin.dp.toPx(), (st.size * 0.45f).dp.toPx())
            detectTapGestures(onLongPress = { o ->
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onEdit(p, lay?.getOffsetForPosition(o - pad)?.coerceIn(0, p.length) ?: -1)
            }, onTap = if (saved) ({ onEdit(p, -1) }) else null) // chạm lại đoạn đã lưu → mở menu bỏ lưu/sửa ghi chú
        })
            .drawBehind { if (saved) drawRect(bar, androidx.compose.ui.geometry.Offset(st.margin.dp.toPx() / 3, 6.dp.toPx()), androidx.compose.ui.geometry.Size(2.dp.toPx(), size.height - 12.dp.toPx())) } // vạch mảnh bên lề
            .background(if (hi) fg.copy(alpha = 0.10f) else Color.Transparent) // đoạn đang được đọc
            .padding(st.margin.dp, (st.size * 0.45f).dp),
        onTextLayout = { lay = it }, style = body,
    )
}
