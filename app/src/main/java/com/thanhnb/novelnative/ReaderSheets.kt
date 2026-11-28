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

@Composable
internal fun TocSheet(novelId: Int, current: Int, pick: (Int) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val toc = load("toc", novelId) { Api.toc(novelId) }
    Column(Modifier.fillMaxHeight(0.75f)) {
        Loaded(toc, loading = { TextSkeleton() }) { chapters ->
            val list = rememberLazyListState((chapters.indexOfFirst { it.index == current } - 3).coerceAtLeast(0))
            val scope = rememberCoroutineScope()
            Row(Modifier.padding(20.dp, 0.dp, 20.dp, 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Mục lục", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                JumpField("Tới chương… /${chapters.size}", Modifier.width(150.dp)) { n -> scope.launch { list.scrollToItem(chapterPos(chapters, n)) } }
            }
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
internal fun StyleSheet(st: Style, dark: Boolean, startAuto: () -> Unit) {
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
            Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(col.bg).border(1.dp, cs.outlineVariant, RoundedCornerShape(18.dp))
                .padding((st.margin / 1.6f).dp, 14.dp),
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
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            readerColors.forEachIndexed { i, rc ->
                val on = i == (if (dark) st.dark else st.light)
                Box(
                    Modifier.size(44.dp).border(2.dp, if (on) cs.primary else Color.Transparent, CircleShape).padding(4.dp)
                        .clip(CircleShape).background(rc.bg).border(1.dp, cs.outlineVariant, CircleShape)
                        .clickable { if (dark) st.dark = i else st.light = i },
                    Alignment.Center,
                ) { Text("A", color = rc.fg, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
            }
        }

        // thanh chọn loại cùng rộng với thanh "Màu nền"; hàng font bên dưới lọc theo loại
        val serif = st.rfont.serif
        SheetLabel("Phông chữ") {
            Seg(listOf("Có chân", "Không chân"), if (serif) 0 else 1, Modifier.width(216.dp)) {
                if ((it == 0) != serif) st.font = readerFonts.first { f -> f.serif == (it == 0) }.key
            }
        }
        FontRow(readerFonts.filter { it.serif == serif }, st)

        SheetLabel("Bố cục")
        Column(Modifier.clip(RoundedCornerShape(18.dp)).background(cs.background.copy(alpha = 0.7f)).padding(horizontal = 14.dp, vertical = 4.dp)) {
        SliderRow("Cỡ chữ", "${st.size.roundToInt()}", st.size, 15f..28f) { st.size = it }
        SliderRow("Giãn dòng", "%.1f×".format(st.lh), st.lh, 1.3f..2.2f) { st.lh = (it * 10).roundToInt() / 10f }
        SliderRow("Viền 2 bên", "${st.margin.roundToInt()}", st.margin, 8f..48f) { st.margin = it }
        if (!st.page) SliderRow("Tự cuộn", "${st.speed.roundToInt()}", st.speed, 15f..120f, startAuto) { st.speed = it }
        }

        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Tile("Căn đều", Icons.Rounded.FormatAlignJustify, st.justify, Modifier.weight(1f)) { st.justify = it }
            Tile("Gạch thuật ngữ", Icons.Rounded.FormatUnderlined, st.gloss, Modifier.weight(1f)) { st.gloss = it }
            Tile("Phím âm lượng", Icons.AutoMirrored.Rounded.VolumeUp, st.volume, Modifier.weight(1f)) { st.volume = it }
        }
    }
}

/** Hàng font vuốt ngang; mở sheet hoặc đổi loại thì cuộn tới font đang chọn. */
@Composable
private fun FontRow(fonts: List<RFont>, st: Style) {
    val cs = MaterialTheme.colorScheme
    androidx.compose.foundation.lazy.LazyRow(
        state = androidx.compose.runtime.key(fonts.first().serif) { rememberLazyListState(fonts.indexOfFirst { it.key == st.font }.coerceAtLeast(0)) },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(fonts) { f ->
            val on = st.font == f.key
            Text(
                if (f.web && f.key !in WebFonts.ready) f.name + " ↓" else f.name, // ↓ = sẽ tải khi chọn
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
    // material3 1.5 bỏ bản Slider(value, …, thumb, track) → giữ SliderState, cập nhật value mỗi lần như thư viện tự làm
    val state = remember(range) { SliderState(v, trackRange = range) }
    state.value = v
    Slider(
        state, set, Modifier.weight(1f),
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
internal fun ReportDialog(ctx: Pair<String, String>?, dismiss: () -> Unit, send: (String) -> Unit) {
    val types = listOf("Sai nghĩa", "Xưng hô/giọng", "Chính tả", "Cảm thán/chữ đệm", "Khác")
    var type by remember { mutableStateOf(types[0]) }
    var note by remember { mutableStateOf("") }
    val sel = ctx?.first?.trim().orEmpty()
    // có từ đang chọn thì ghi kèm ngữ cảnh, không bắt buộc ghi chú; báo cả chương thì phải mô tả
    val canSend = sel.isNotEmpty() || note.isNotBlank()
    AlertDialog(
        dismiss,
        confirmButton = {
            TextButton({
                val head = "[$type]"
                val body = if (sel.isNotEmpty()) {
                    val flat = ctx!!.second.replace(Regex("\\s+"), " ").trim()
                    val excerpt = if (flat.length <= 400) flat else flat.take(400) + "…"
                    " Chọn: “$sel”. Ngữ cảnh: “$excerpt”. ${note.trim()}"
                } else " ${note.trim()}"
                send((head + body).trim())
            }, enabled = canSend) { Text("Gửi báo lỗi") }
        },
        dismissButton = { TextButton(dismiss) { Text("Huỷ") } },
        title = { Text(if (sel.isNotEmpty()) "Báo lỗi bản dịch" else "Báo lỗi chương") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (sel.isNotEmpty()) Text("Đoạn chọn: “$sel”", maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                Text("Loại lỗi", style = MaterialTheme.typography.labelMedium)
                @OptIn(ExperimentalLayoutApi::class)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    types.forEach { t -> FilterChip(type == t, { type = t }, { Text(t) }) }
                }
                OutlinedTextField(
                    note, { note = it }, Modifier.fillMaxWidth(), minLines = 2, maxLines = 4,
                    label = { Text(if (sel.isNotEmpty()) "Ghi chú (không bắt buộc)" else "Mô tả lỗi") },
                    placeholder = { Text(if (sel.isNotEmpty()) "Không sửa nội dung chương" else "Sai tên, sót đoạn, lẫn chữ Trung…") },
                )
            }
        },
    )
}
