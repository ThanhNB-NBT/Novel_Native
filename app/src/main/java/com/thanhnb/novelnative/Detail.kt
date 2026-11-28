package com.thanhnb.novelnative

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.HourglassEmpty
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.animation.core.spring
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.blur.BlurRadiusSpec
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.rememberScrollableState
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.compose.ui.graphics.graphicsLayer

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DetailScreen(id: Int) {
    val uid = Session.user?.id
    val nl = load("novel", id) { Api.novel(id) }
    val toc = load("toc", id) { Api.toc(id) }
    val progress = load("progress", id, uid) { Api.progress(id) }
    val inLib = load("inlib", id, uid) { Api.inLibrary(id) }
    val marks = load("marks", id, uid) { Api.bookmarks(id) }
    val admin = load("admin", uid) { Api.isAdmin() }.res?.getOrNull() == true
    var selecting by remember { mutableStateOf(false) }
    var sel by remember { mutableStateOf(emptySet<Int>()) }
    var askTr by remember { mutableStateOf(false) }
    var askRe by remember { mutableStateOf(false) }
    var askCancelRe by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var tab by remember { mutableIntStateOf(0) }
    var desc by remember { mutableStateOf(false) }
    var asc by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(msg) { kotlinx.coroutines.delay(2500); msg = null }
    val cs = MaterialTheme.colorScheme

    // đầu trang tối (bìa mờ) → icon thanh trạng thái màu sáng; rời trang thì trả lại theo theme
    val view = LocalView.current
    val appLight = cs.background.luminance() > 0.5f
    DisposableEffect(Unit) {
        val ctl = WindowCompat.getInsetsController((view.context as Activity).window, view)
        ctl.isAppearanceLightStatusBars = false
        onDispose { ctl.isAppearanceLightStatusBars = appLight }
    }

    Box(Modifier.fillMaxSize()) {
        Loaded(nl, loading = { CenterSpinner() }) { n ->
            val chapters = toc.res?.getOrNull().orEmpty()
            val first = chapters.firstOrNull()?.index ?: 1
            val cur = progress.res?.getOrNull()
            fun read(i: Int) = if (uid == null) Nav.push(Screen.Login) else Nav.push(Screen.Read(n.id, n.title, i))
            val amb = rememberAmbient(n.cover)
            val dark = cs.background.luminance() < .5f
            val tail = amb?.copy(alpha = (if (dark) .16f else .2f) * .65f)?.compositeOver(cs.background) ?: cs.background
            // đầu trang thu lên theo cuộn (chừa thanh trạng thái): cửa sổ thấp (chia đôi, cửa sổ nổi, xoay ngang) vẫn còn chỗ cho tab + danh sách
            val density = LocalDensity.current
            val top = WindowInsets.statusBars.getTop(density)
            var headH by remember { mutableIntStateOf(0) }
            var tabsH by remember { mutableIntStateOf(0) }
            var off by remember { mutableFloatStateOf(0f) }
            val maxOff by rememberUpdatedState((headH - top).coerceAtLeast(0).toFloat())
            fun shift(dy: Float): Float { val o = off; off = (off - dy).coerceIn(0f, maxOff); return o - off } // trả về phần đã dùng
            val conn = remember {
                object : NestedScrollConnection {
                    // vuốt lên: thu đầu trang trước rồi mới cuộn danh sách · vuốt xuống: danh sách về đỉnh rồi mới kéo đầu trang ra
                    override fun onPreScroll(available: Offset, source: NestedScrollSource) = if (available.y < 0) Offset(0f, shift(available.y)) else Offset.Zero
                    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource) = if (available.y > 0) Offset(0f, shift(available.y)) else Offset.Zero
                }
            }
            BoxWithConstraints(
                Modifier.fillMaxSize().clipToBounds().nestedScroll(conn).background(
                    Brush.verticalGradient(listOf((amb ?: Color.Transparent).copy(alpha = if (dark) .16f else .2f), Color.Transparent), endY = 2200f),
                ),
            ) {
            val pageH = with(density) { (constraints.maxHeight - top - tabsH).coerceAtLeast(0).toDp() }
            // thu gần hết: dải còn sót dưới thanh trạng thái phủ dần màu giấy, icon thanh trạng thái đổi theo nền
            val collapsed by remember { derivedStateOf { maxOff > 0f && off >= maxOff * .9f } }
            LaunchedEffect(collapsed) { WindowCompat.getInsetsController((view.context as Activity).window, view).isAppearanceLightStatusBars = collapsed && appLight }
            Column(Modifier.fillMaxWidth().wrapContentHeight(Alignment.Top, unbounded = true).offset { IntOffset(0, -off.coerceAtMost(maxOff).roundToInt()) }) {
                // kéo ngay trên đầu trang cũng thu/mở được (khi cửa sổ thấp đến mức đầu trang chiếm gần hết)
                Box(Modifier.onSizeChanged { headH = it.height }.scrollable(rememberScrollableState { shift(it) }, Orientation.Vertical)) {
                    Header(n, tail)
                }
                // nội dung hai tab là pager: vuốt ngang theo tay, chạm tab thì lò xo trượt; gạch chân chạy theo currentPage
                val pager = rememberPagerState(tab) { 2 }
                LaunchedEffect(tab) { if (pager.targetPage != tab) pager.animateScrollToPage(tab, animationSpec = spring(dampingRatio = .9f, stiffness = 300f)) }
                LaunchedEffect(pager) { snapshotFlow { pager.targetPage }.collect { tab = it } }
                // kéo trên thanh tab cũng mở lại đầu trang, khỏi cuộn danh sách chương ngược về đầu
                Box(Modifier.onSizeChanged { tabsH = it.height }.scrollable(rememberScrollableState { shift(it) }, Orientation.Vertical)) {
                    SegTabs(listOf("Giới thiệu", "Danh sách chương"), pager.currentPage + pager.currentPageOffsetFraction) { tab = it }
                }
                val bottom = PaddingValues(bottom = 96.dp)
                HorizontalPager(pager, Modifier.height(pageH), key = { it }) { page ->
                if (page == 0) LazyColumn(Modifier.fillMaxSize(), contentPadding = bottom) {
                    item {
                        if (n.genres.isNotEmpty()) FlowRow(Modifier.padding(20.dp, 16.dp, 20.dp, 0.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            n.genres.forEach {
                                Text(
                                    it, Modifier.background(cs.primary.copy(.12f), RoundedCornerShape(20.dp)).padding(horizontal = 12.dp, vertical = 5.dp),
                                    color = cs.primary, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                        n.description?.let {
                            Text(
                                it, Modifier.bounceClick { desc = !desc }.padding(20.dp, 14.dp),
                                maxLines = if (desc) Int.MAX_VALUE else 6, overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 24.sp), color = cs.onSurface,
                            )
                        }
                        // thông tin + hành động gom vào thẻ giấy với ô icon, cùng khuôn với tab Tôi
                        Column(Modifier.padding(16.dp, 4.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            PaperCard(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(18.dp, 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Fact("Tác giả", n.author)
                                    Fact("Nguồn", n.source)
                                    Fact("Mã truyện", "#${n.id}")
                                }
                            }
                            SettingGroup(
                                { SettingTile(Icons.Rounded.Translate, "Thuật ngữ truyện", "Tên người, địa danh… dịch thống nhất cả truyện", { Nav.push(Screen.Glossary(n.id)) }) },
                                { OfflineCard(n) },
                            )
                        }
                    }
                    val bm = marks.res?.getOrNull().orEmpty()
                    if (bm.isNotEmpty()) {
                        item { SectionHeader("Dấu trang") }
                        items(bm, key = { "b${it.id}" }) { b ->
                            Row(
                                Modifier.fillMaxWidth().bounceClick {
                                    Prefs.setPercent(n.id, b.chapter, b.percent.toFloat()) // reader tự cuộn tới %
                                    read(b.chapter)
                                }.padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Rounded.Bookmark, null, Modifier.size(18.dp), cs.primary)
                                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                                    Text("Chương ${b.chapter} · ${(b.percent * 100).toInt()}%", style = MaterialTheme.typography.titleMedium)
                                    b.excerpt?.let { Text(it, style = MaterialTheme.typography.labelMedium, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                                }
                                IconButton({ scope.launch { runCatching { Api.deleteBookmark(b.id) }.onFailure { msg = friendly(it) }; marks.reload() } }) {
                                    Icon(Icons.Rounded.Close, "Xoá dấu trang", tint = cs.onSurfaceVariant)
                                }
                            }
                        }
                    }
                } else Column(Modifier.fillMaxSize()) {
                    val list = rememberLazyListState()
                    // vị trí trong LazyColumn: +1 vì mục đầu là dòng ghi chú
                    fun pos(ch: Int) = chapterPos(chapters, ch).let { if (asc) it else chapters.lastIndex - it } + 1
                    // mở danh sách ở chương đang đọc (một lần; vuốt qua lại tab không kéo về nữa)
                    var placed by rememberSaveable { mutableStateOf(false) }
                    LaunchedEffect(chapters.size, cur) {
                        if (!placed && cur != null && chapters.isNotEmpty()) { placed = true; list.scrollToItem((pos(cur) - 3).coerceAtLeast(0)) }
                    }
                        // hàng điều khiển ghim ngoài danh sách: danh sách tự cuộn tới chương đang đọc thì vẫn bấm được
                        Row(Modifier.padding(20.dp, 8.dp, 8.dp, 0.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (selecting) {
                                Text("Đã chọn ${sel.size}", Modifier.weight(1f), style = monoStyle(13, cs.primary))
                                IconButton({ sel = chapters.filter { it.done }.map { it.index }.toSet() }) { Icon(Icons.Rounded.DoneAll, "Chọn hết chương đã dịch") }
                                TextButton({ selecting = false }) { Text("Huỷ") }
                                Button({ askRe = true }, enabled = sel.isNotEmpty(), contentPadding = PaddingValues(horizontal = 14.dp)) { Text("Dịch lại (${sel.size})") }
                            } else {
                                JumpField("Tới chương… /${chapters.size}", Modifier.weight(1f)) { n -> scope.launch { list.scrollToItem(pos(n)) } }
                                TextButton({ if (uid == null) Nav.push(Screen.Login) else askTr = true }) {
                                    Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, null, Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Dịch")
                                }
                                if (admin) IconButton({
                                    selecting = true
                                    sel = setOfNotNull(cur?.takeIf { c -> chapters.any { it.index == c && it.done } })
                                }) { Icon(Icons.Rounded.RestartAlt, "Dịch lại — chọn chương") }
                                TextButton({ asc = !asc }) {
                                    Icon(Icons.AutoMirrored.Rounded.Sort, null, Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(if (asc) "Cũ → mới" else "Mới → cũ")
                                }
                            }
                        }
                        // lỡ bấm dịch lại: chương đang chờ (chưa vào máy dịch) huỷ được, giữ nguyên bản cũ, khỏi tốn lượt gọi LLM
                        if (admin && !selecting && chapters.any { it.queued }) Row(Modifier.padding(20.dp, 0.dp, 8.dp, 0.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("${chapters.count { it.queued }} chương đang chờ dịch", Modifier.weight(1f), style = monoStyle(12, cs.onSurfaceVariant))
                            TextButton({ askCancelRe = true }) { Text("Huỷ dịch lại", color = cs.error) }
                        }
                        if (selecting) Row(Modifier.padding(20.dp, 4.dp, 8.dp, 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            // thêm (không thay) các chương đã dịch trong khoảng → gõ nhiều khoảng nối nhau được
                            RangeField("Chọn", Modifier.weight(1f)) { r ->
                                sel = sel + chapters.filter { it.done && it.index in r }.map { it.index }
                            }
                            IconButton({ sel = emptySet() }, enabled = sel.isNotEmpty()) { Icon(Icons.Rounded.Close, "Bỏ chọn hết") }
                        }
                    // giữ một chương rồi vuốt lên/xuống: chọn liền cả đoạn từ chương giữ tới chương dưới ngón tay; sát mép thì tự cuộn
                    var dragFrom by remember { mutableStateOf<Int?>(null) }
                    var dragBase by remember { mutableStateOf(emptySet<Int>()) }
                    var dragY by remember { mutableFloatStateOf(0f) }
                    var autoScroll by remember { mutableFloatStateOf(0f) }
                    // danh sách cao bằng pageH nên lúc đầu trang còn mở, đáy của nó nằm ngoài màn hình → đo phần thấy được
                    var visH by remember { mutableFloatStateOf(Float.MAX_VALUE) }
                    fun chapterAt(y: Float) = list.layoutInfo.visibleItemsInfo.firstOrNull { y.toInt() in it.offset until it.offset + it.size }?.key as? Int
                    fun extend() {
                        val from = dragFrom ?: return
                        val to = chapterAt(dragY) ?: return
                        val r = minOf(from, to)..maxOf(from, to)
                        sel = dragBase + chapters.filter { it.done && it.index in r }.map { it.index }
                    }
                    LaunchedEffect(autoScroll) {
                        while (autoScroll != 0f) {
                            // như vuốt tay: xuống thì thu đầu trang trước; danh sách đã về đỉnh thì mở đầu trang ra
                            var d = autoScroll
                            if (d > 0) shift(-d).also { d += it; dragY -= it }
                            val left = d - list.scrollBy(d)
                            if (left < 0) dragY -= shift(-left)
                            extend(); delay(16)
                        }
                    }
                    LazyColumn(
                        Modifier.fillMaxSize().onGloballyPositioned { visH = it.boundsInWindow().bottom - it.positionInWindow().y }.then(
                            // bắt ở pass Initial (cha thấy trước con): đã giữ thì nuốt sự kiện để danh sách không cuộn, hàng không nhận bấm
                            if (admin) Modifier.pointerInput(chapters) {
                                val edge = 64.dp.toPx()
                                awaitEachGesture {
                                    val down = awaitFirstDown(requireUnconsumed = false)
                                    val c = chapterAt(down.position.y) ?: return@awaitEachGesture
                                    if (chapters.none { it.index == c && it.done }) return@awaitEachGesture
                                    // null = hết giờ mà ngón vẫn giữ yên → coi là giữ
                                    val moved = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                                        while (true) {
                                            val ch = awaitPointerEvent(PointerEventPass.Initial).changes.first()
                                            if (!ch.pressed || ch.isConsumed || (ch.position - down.position).getDistance() > viewConfiguration.touchSlop) break
                                        }
                                        true
                                    }
                                    if (moved != null) return@awaitEachGesture
                                    if (!selecting) { selecting = true; sel = emptySet() }
                                    view.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                                    dragFrom = c; dragBase = sel; dragY = down.position.y; extend()
                                    try {
                                        while (true) {
                                            val ch = awaitPointerEvent(PointerEventPass.Initial).changes.first()
                                            ch.consume()
                                            if (!ch.pressed) break
                                            dragY = ch.position.y
                                            // đáy thấy được trừ thanh "Đọc tiếp" nổi phía dưới
                                            val bot = minOf(size.height.toFloat(), visH) - 80.dp.toPx()
                                            autoScroll = when {
                                                dragY > bot - edge -> (dragY - (bot - edge)) / 4
                                                dragY < edge -> -(edge - dragY) / 4
                                                else -> 0f
                                            }
                                            extend()
                                        }
                                    } finally { dragFrom = null; autoScroll = 0f }
                                }
                            } else Modifier,
                        ),
                        state = list, contentPadding = bottom,
                    ) {
                    item {
                        when {
                            // migration 122: chapters chỉ đọc được khi đăng nhập
                            uid == null -> NeedLoginInline()
                            toc.res == null -> LinearProgressIndicator(Modifier.fillMaxWidth().padding(20.dp))
                            toc.res.isFailure -> Empty(friendly(toc.res.exceptionOrNull()))
                            chapters.size < n.chaptersSource -> Text(
                                "Mục lục đầy đủ sẽ được tải khi bạn mở đọc.",
                                Modifier.padding(horizontal = 20.dp),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                    items(if (asc) chapters else chapters.asReversed(), key = { it.index }) { c ->
                        val reading = c.index == cur
                        Row(
                            Modifier.fillMaxWidth()
                                .bounceClick { if (!selecting) read(c.index) else if (c.done) sel = if (c.index in sel) sel - c.index else sel + c.index }
                                .padding(horizontal = 8.dp)
                                .background(if (reading) cs.primary.copy(.1f) else Color.Transparent, RoundedCornerShape(14.dp))
                                .padding(12.dp, 13.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (selecting) Checkbox(c.index in sel, null, Modifier.padding(end = 12.dp).size(20.dp), enabled = c.done)
                            Text("${c.index}", Modifier.width(44.dp), style = monoStyle(12, if (reading) cs.primary else cs.onSurfaceVariant))
                            Text(
                                c.title, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (reading) FontWeight.Bold else null,
                                color = when { reading -> cs.primary; c.done -> cs.onSurface; else -> cs.onSurfaceVariant },
                            )
                            if (reading) Text("Đang đọc", Modifier.padding(start = 8.dp), style = MaterialTheme.typography.labelSmall, color = cs.primary)
                            if (!c.done) Icon(Icons.Rounded.HourglassEmpty, "chưa dịch", Modifier.size(16.dp), cs.onSurfaceVariant.copy(.6f))
                        }
                        // viên nền của chương đang đọc đã tự tách dòng, không cần gạch dưới
                        if (!reading) HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = cs.outlineVariant.copy(.6f))
                    }
                    }
                }
                }
            }
            if (maxOff > 0f) Box(Modifier.fillMaxWidth().height(with(density) { top.toDp() }).graphicsLayer { alpha = ((off / maxOff - .7f) / .3f).coerceIn(0f, 1f) }.background(tail))
            }
            // nền mờ dần sau hai nút nổi: chữ danh sách trôi xuống dưới không còn chọc qua khe giữa hai nút
            // gần đặc ngay từ mép trên nút (≈40% chiều cao) để dòng chữ không lộ quanh nút; alpha 0 của chính màu nền, không phải đen trong suốt
            Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(140.dp).background(Brush.verticalGradient(0f to cs.background.copy(alpha = 0f), .4f to cs.background.copy(alpha = .92f), 1f to cs.background)))
            // hai nút nổi ở đáy như bản Flutter
            Row(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(16.dp, 0.dp, 16.dp, 14.dp)) {
                Surface(
                    { read(cur ?: first) }, Modifier.weight(1f).height(46.dp), shape = RoundedCornerShape(23.dp),
                    color = cs.primary, shadowElevation = 6.dp,
                ) {
                    Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.AutoMirrored.Rounded.MenuBook, null, Modifier.size(18.dp), cs.onPrimary)
                        Spacer(Modifier.width(8.dp))
                        Text(if (cur != null) "Đọc tiếp chương $cur" else "Đọc truyện", color = cs.onPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp, fontFamily = Jakarta)
                    }
                }
                Spacer(Modifier.width(12.dp))
                val followed = inLib.res?.getOrNull() == true
                Surface(
                    {
                        if (uid == null) return@Surface Nav.push(Screen.Login)
                        busy = true
                        scope.launch {
                            runCatching { Api.setInLibrary(n.id, !followed) }
                                .onSuccess { msg = if (followed) "Đã bỏ theo dõi" else "Đã thêm vào Tủ truyện"; Mascot.react(if (followed) OrbMood.Sad else OrbMood.Love) }
                                .onFailure { msg = friendly(it) }
                            inLib.reload(); busy = false
                        }
                    },
                    Modifier.height(46.dp), enabled = !busy, shape = RoundedCornerShape(23.dp),
                    color = if (followed) cs.primaryContainer else cs.card, shadowElevation = 6.dp,
                    border = androidx.compose.foundation.BorderStroke(1.dp, if (followed) cs.primary else cs.outlineVariant),
                ) {
                    Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (followed) Icons.Rounded.Check else Icons.Rounded.Add, null, Modifier.size(20.dp), if (followed) cs.primary else cs.onSurfaceVariant)
                        Spacer(Modifier.width(6.dp))
                        Text(if (followed) "Đang theo dõi" else "Theo dõi", color = if (followed) cs.primary else cs.onSurface, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, fontFamily = Jakarta)
                    }
                }
            }
            if (askTr) TranslateDialog(n.chaptersTranslated, n.chaptersSource, { askTr = false }) { to ->
                askTr = false
                scope.launch {
                    runCatching { Api.requestTranslation(n.id, to, priority = 50) }
                        .onSuccess { msg = "Đã xếp chương vào hàng đợi dịch (tới chương $to)"; Mascot.say("Đã xếp hàng dịch", OrbMood.Wink) }
                        .onFailure { msg = friendly(it) }
                    nl.reload(); toc.reload()
                }
            }
            if (askRe) AlertDialog(
                { askRe = false },
                confirmButton = {
                    TextButton({
                        askRe = false
                        val picked = sel.sorted()
                        val all = chapters.count { it.done }
                        selecting = false
                        scope.launch {
                            // chọn trọn bộ chương đã dịch → một lần gọi retranslate_all
                            runCatching { if (picked.size == all) Api.retranslateAll(n.id) else picked.forEach { Api.retranslate(n.id, it) } }
                                .onSuccess { Notify.addMine(n.id, picked); msg = "Đã xếp ${picked.size} chương để dịch lại" }
                                .onFailure { msg = friendly(it) }
                            toc.reload()
                        }
                    }) { Text("Dịch lại") }
                },
                dismissButton = { TextButton({ askRe = false }) { Text("Huỷ") } },
                title = { Text("Dịch lại ${sel.size} chương?") },
                text = { Text("Bản cũ vẫn đọc được cho tới khi bản mới thay thế.") },
            )
            if (askCancelRe) AlertDialog(
                { askCancelRe = false },
                confirmButton = {
                    TextButton({
                        askCancelRe = false
                        scope.launch {
                            runCatching { AdminApi.cancelRetranslate(n.id) }
                                .onSuccess { msg = if (it == 0) "Không còn chương nào chờ dịch lại (có thể đã vào máy dịch)" else "Đã huỷ dịch lại $it chương, giữ bản cũ" }
                                .onFailure { msg = friendly(it) }
                            toc.reload()
                        }
                    }) { Text("Huỷ dịch lại", color = MaterialTheme.colorScheme.error) }
                },
                dismissButton = { TextButton({ askCancelRe = false }) { Text("Thôi") } },
                title = { Text("Huỷ các chương đang chờ dịch lại?") },
                text = { Text("Chương đã có bản dịch mà đang chờ dịch lại sẽ được rút khỏi hàng đợi và giữ bản cũ. Chương đang dịch dở hoặc chương mới yêu cầu dịch lần đầu không bị đụng tới.") },
            )
        }
        msg?.let { m ->
            Snackbar(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(16.dp, 0.dp, 16.dp, 72.dp)) { Text(m) }
        }
    }
}

/** "Yêu cầu dịch": chọn dịch thêm 50/100/200 chương, tới hết, hoặc tới một chương cụ thể. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TranslateDialog(translated: Int, source: Int, dismiss: () -> Unit, submit: (Int) -> Unit) {
    var custom by remember { mutableStateOf("") }
    fun go(to: Int) { if (to > translated) submit(to.coerceAtMost(source)) }
    AlertDialog(
        dismiss,
        title = { Text("Yêu cầu dịch") },
        text = {
            Column {
                Text("Đã dịch $translated/$source chương. Chọn dịch tới đâu:", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(12.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (translated < source) {
                        listOf(50, 100, 200).forEach { AssistChip({ go(translated + it) }, { Text("+$it chương") }) }
                        AssistChip({ go(source) }, { Text("Đến hết") })
                    }
                }
                OutlinedTextField(
                    custom, { custom = it.filter(Char::isDigit).take(6) }, Modifier.fillMaxWidth().padding(top = 12.dp),
                    label = { Text("Hoặc dịch tới chương…") }, singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                )
            }
        },
        confirmButton = { TextButton({ custom.toIntOrNull()?.let(::go) }) { Text("Dịch") } },
        dismissButton = { TextButton(dismiss) { Text("Đóng") } },
    )
}

/** Đầu trang: bìa phóng to + mờ, phủ tối pha màu nhấn, tan vào nền giấy; bìa nét + tên chữ trắng. */
private val SheetEdge = 20.dp

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Header(n: Novel, tail: Color) {
    val cs = MaterialTheme.colorScheme
    Box(Modifier.clipToBounds()) {
        // mờ lũy tiến: đỉnh còn nhận ra bìa, đáy (chỗ chữ trắng) mờ hẳn; Android 12 không có mờ theo vùng → mờ đều như cũ
        val bgBlur = if (android.os.Build.VERSION.SDK_INT >= 33) Modifier.blur { blurRadiusSpec = BlurRadiusSpec.verticalGradient(6.dp, 36.dp) } else Modifier.blur(24.dp)
        AsyncImage(Api.thumb(n.cover, 400), null, Modifier.matchParentSize().then(bgBlur), contentScale = ContentScale.Crop)
        // lớp phủ đậm dần xuống dưới theo màu nhấn: chữ trắng (kể cả hàng "Đã dịch" ở đáy) luôn nằm trên nền tối
        val deep = lerp(Color.Black, cs.primary, .4f)
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(0f to Color.Black.copy(.4f), .5f to deep.copy(.62f), 1f to deep.copy(.82f))))
        Column(Modifier.statusBarsPadding()) {
            IconButton(Nav::pop) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Quay lại", tint = Color.White) }
            Row(Modifier.padding(20.dp, 8.dp, 20.dp, 0.dp), verticalAlignment = Alignment.Bottom) {
                Cover(n.cover, 124.dp, 174.dp, Modifier.holo(), label = n.title)
                Column(Modifier.weight(1f).padding(start = 16.dp)) {
                    Text(n.title, style = MaterialTheme.typography.headlineSmall, color = Color.White, fontWeight = FontWeight.ExtraBold, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(6.dp))
                    Text(n.author, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(.85f))
                }
            }
            // thông tin dạng viên thuốc kính mờ + vạch tiến độ dịch, thay cho một dòng chữ xám
            Row(Modifier.padding(20.dp, 18.dp, 20.dp, 0.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("${n.chaptersSource} chương", Api.statusLabel(n.status), n.source).filter(String::isNotEmpty).forEach {
                    Text(it, Modifier.clip(CircleShape).background(Color.White.copy(.16f)).padding(horizontal = 11.dp, vertical = 5.dp), style = MaterialTheme.typography.labelMedium, color = Color.White, maxLines = 1)
                }
            }
            if (n.facts.isNotEmpty()) @OptIn(ExperimentalLayoutApi::class) FlowRow(
                Modifier.padding(20.dp, 8.dp, 20.dp, 0.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                n.facts.forEach {
                    Text(it, Modifier.clip(CircleShape).border(1.dp, Color.White.copy(.28f), CircleShape).padding(horizontal = 11.dp, vertical = 4.dp), style = MaterialTheme.typography.labelMedium, color = Color.White.copy(.9f), maxLines = 1)
                }
            }
            val frac = if (n.chaptersSource > 0) (n.chaptersTranslated.toFloat() / n.chaptersSource).coerceIn(0f, 1f) else 0f
            Column(Modifier.padding(20.dp, 12.dp, 20.dp, 18.dp + SheetEdge)) {
                Row {
                    Text("Đã dịch", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, color = Color.White.copy(.8f))
                    Text("${n.chaptersTranslated}/${n.chaptersSource}", style = monoStyle(12, Color.White), fontWeight = FontWeight.Bold)
                }
                LinearProgressIndicator({ frac }, Modifier.padding(top = 5.dp).fillMaxWidth().height(5.dp).clip(CircleShape), color = Color.White, trackColor = Color.White.copy(.22f), gapSize = 0.dp, drawStopIndicator = {})
            }
        }
        // mép trang giấy bo tròn đè lên ảnh: pha dần từ ảnh tối sang giấy sáng luôn ra một dải xám đục
        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(SheetEdge).background(tail, RoundedCornerShape(topStart = SheetEdge, topEnd = SheetEdge)))
    }
}

/** Thanh tab dạng viên thuốc: viên nhấn trượt liên tục theo [pos] (vị trí pager, có phần lẻ khi đang vuốt). */
@Composable
private fun SegTabs(labels: List<String>, pos: Float, select: (Int) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val outer = RoundedCornerShape(18.dp)
    val inner = RoundedCornerShape(14.dp)
    BoxWithConstraints(Modifier.padding(20.dp, 14.dp).fillMaxWidth().height(48.dp).clip(outer).background(cs.onSurface.copy(.07f)).padding(4.dp)) {
        val w = maxWidth / labels.size
        Box(
            Modifier.offset { androidx.compose.ui.unit.IntOffset((pos * w.toPx()).toInt(), 0) }.width(w).fillMaxHeight()
                .shadow(4.dp, inner, ambientColor = cs.primary, spotColor = cs.primary).clip(inner).background(cs.primary),
        )
        Row(Modifier.fillMaxSize()) {
            labels.forEachIndexed { i, t ->
                val near = (1f - kotlin.math.abs(pos - i)).coerceIn(0f, 1f)
                Box(
                    Modifier.weight(1f).fillMaxHeight().clip(inner).clickable(remember { MutableInteractionSource() }, null) { select(i) },
                    Alignment.Center,
                ) { Text(t, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = lerp(cs.onSurfaceVariant, cs.onPrimary, near)) }
            }
        }
    }
}

@Composable
private fun Fact(k: String, v: String) {
    if (v.isEmpty()) return
    Row {
        Text(k, Modifier.width(96.dp), style = MaterialTheme.typography.labelMedium)
        Text(v, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun NeedLoginInline() = Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
    Text("Đăng nhập để xem mục lục và đọc truyện", style = MaterialTheme.typography.bodyMedium)
    Spacer(Modifier.height(12.dp))
    Button({ Nav.push(Screen.Login) }) { Text("Đăng nhập") }
}
