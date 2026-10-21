package com.thanhnb.novelnative

import android.app.Activity
import androidx.core.view.WindowCompat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/** Bộ màu đọc: (tên, nền, chữ). */
private val themes = listOf(
    Triple("Trắng", Color(0xFFFFFFFF), Color(0xFF1F1F1F)),
    Triple("Giấy", Color(0xFFF4ECD8), Color(0xFF3B2F2F)),
    Triple("Lá", Color(0xFFDCEBDD), Color(0xFF1E2B1E)),
    Triple("Tối", Color(0xFF1E1E1E), Color(0xFFC8C8C8)),
    Triple("Đen", Color(0xFF000000), Color(0xFF9E9E9E)),
)

private class Style {
    val sp = Prefs.sp
    var size by mutableFloatStateOf(sp.getFloat("rd_size", 19f))
    var lh by mutableFloatStateOf(sp.getFloat("rd_lh", 1.7f))
    var margin by mutableFloatStateOf(sp.getFloat("rd_margin", 20f))
    var theme by mutableIntStateOf(sp.getInt("rd_theme", 1))
    var justify by mutableStateOf(sp.getBoolean("rd_justify", false))
    fun save() = sp.edit().putFloat("rd_size", size).putFloat("rd_lh", lh).putFloat("rd_margin", margin)
        .putInt("rd_theme", theme).putBoolean("rd_justify", justify).apply()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(s: Screen.Read) {
    val st = remember { Style() }
    val (_, bg, fg) = themes[st.theme]
    val scope = rememberCoroutineScope()
    var bars by remember { mutableStateOf(true) }
    var sheet by remember { mutableIntStateOf(0) } // 0 không · 1 mục lục · 2 cài đặt
    var report by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(msg) { delay(2500); msg = null }
    val go = { i: Int -> Nav.replace(s.copy(index = i)) }

    // Giữ màn hình sáng khi đang đọc
    val view = LocalView.current
    DisposableEffect(Unit) { view.keepScreenOn = true; onDispose { view.keepScreenOn = false } }
    // Icon thanh trạng thái theo nền đọc (nền tối → icon sáng); rời màn thì trả về theo theme app
    val appLight = MaterialTheme.colorScheme.background.luminance() > 0.5f
    DisposableEffect(bg) {
        val ctl = WindowCompat.getInsetsController((view.context as Activity).window, view)
        ctl.isAppearanceLightStatusBars = bg.luminance() > 0.5f
        ctl.isAppearanceLightNavigationBars = bg.luminance() > 0.5f
        onDispose { ctl.isAppearanceLightStatusBars = appLight; ctl.isAppearanceLightNavigationBars = appLight }
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

    val list = rememberLazyListState()
    val paras = ch?.paragraphs.orEmpty()
    // Khôi phục vị trí đọc dở trong chương (lưu theo % số đoạn, khoá giống Flutter)
    LaunchedEffect(paras.size) {
        if (paras.isEmpty()) return@LaunchedEffect
        val p = Prefs.percent(s.novelId, s.index)
        if (p > 0f) list.scrollToItem(1 + (p * paras.size).toInt().coerceAtMost(paras.size - 1))
        snapshotFlow { list.firstVisibleItemIndex to list.canScrollForward }.collectLatest { (i, more) ->
            Prefs.setPercent(s.novelId, s.index, if (!more) 1f else ((i - 1).coerceAtLeast(0).toFloat() / paras.size))
        }
    }

    Box(
        Modifier.fillMaxSize().background(bg).pointerInput(Unit) {
            // Chạm nhẹ → ẩn/hiện thanh điều khiển; quan sát ở pass Initial để không giành cử chỉ của cuộn/bôi chọn
            awaitEachGesture {
                val down = awaitFirstDown(pass = PointerEventPass.Initial)
                val up = waitForUpOrCancellation(PointerEventPass.Initial) ?: return@awaitEachGesture
                if (up.uptimeMillis - down.uptimeMillis < 250 && (up.position - down.position).getDistance() < 24f) bars = !bars
            }
        },
    ) {
        CompositionLocalProvider(LocalContentColor provides fg) {
            Loaded(l) { c ->
                when {
                    Session.user == null -> NeedLogin("Đăng nhập để đọc truyện.")
                    c == null -> Waiting("toc", fg) {}
                    c.status != "done" -> Waiting(c.status, fg) {
                        if (Session.user == null) return@Waiting Nav.push(Screen.Login)
                        scope.launch {
                            runCatching { Api.requestTranslation(s.novelId, s.index + 10) }.onFailure { msg = friendly(it) }
                            l.reload()
                        }
                    }
                    c.paragraphs.isEmpty() -> Column(Modifier.fillMaxSize().padding(32.dp), Arrangement.Center, Alignment.CenterHorizontally) {
                        Text("Chương này chưa có nội dung dịch — bản cũ có thể bị lỗi.", textAlign = TextAlign.Center)
                        Spacer(Modifier.height(12.dp))
                        Button({
                            if (Session.user == null) return@Button Nav.push(Screen.Login)
                            scope.launch { runCatching { Api.retranslate(s.novelId, s.index) }.onFailure { msg = friendly(it) }; l.reload() }
                        }) { Text("Dịch lại chương") }
                    }
                    else -> LazyColumn(Modifier.fillMaxSize(), list, contentPadding = WindowInsets.safeDrawing.asPaddingValues()) {
                        item {
                            Text(
                                c.title, Modifier.padding(st.margin.dp, 48.dp, st.margin.dp, 16.dp),
                                fontSize = (st.size * 1.3f).sp, lineHeight = (st.size * 1.7f).sp,
                            )
                        }
                        items(c.paragraphs) { p ->
                            SelectionContainer {
                                Text(
                                    p, Modifier.padding(st.margin.dp, (st.size * 0.45f).dp),
                                    fontSize = st.size.sp, lineHeight = (st.size * st.lh).sp,
                                    textAlign = if (st.justify) TextAlign.Justify else TextAlign.Start,
                                )
                            }
                        }
                        item {
                            Row(Modifier.fillMaxWidth().padding(16.dp, 24.dp, 16.dp, 64.dp), Arrangement.SpaceBetween) {
                                OutlinedButton({ go(s.index - 1) }, enabled = s.index > 1) { Text("‹ Chương trước") }
                                Button({ go(s.index + 1) }) { Text("Chương sau ›") }
                            }
                        }
                    }
                }
            }
        }

        // Thanh trên + dưới nổi đè lên nội dung
        AnimatedVisibility(bars, Modifier.align(Alignment.TopCenter)) {
            Surface(color = bg, contentColor = fg, shadowElevation = 2.dp) {
                Row(Modifier.fillMaxWidth().statusBarsPadding(), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(Nav::pop) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Quay lại") }
                    Column(Modifier.weight(1f)) {
                        Text(s.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
                        Text("Chương ${s.index}", style = MaterialTheme.typography.bodySmall)
                    }
                    IconButton({
                        if (Session.user == null) return@IconButton Nav.push(Screen.Login)
                        val i = (list.firstVisibleItemIndex - 1).coerceIn(0, (paras.size - 1).coerceAtLeast(0))
                        scope.launch {
                            runCatching { Api.addBookmark(s.novelId, s.index, Prefs.percent(s.novelId, s.index), paras.getOrNull(i)?.take(120)) }
                                .onSuccess { msg = "Đã thêm dấu trang" }.onFailure { msg = friendly(it) }
                        }
                    }) { Icon(Icons.Default.Star, "Thêm dấu trang") }
                    var menu by remember { mutableStateOf(false) }
                    Box {
                        IconButton({ menu = true }) { Icon(Icons.Default.MoreVert, "Thêm") }
                        DropdownMenu(menu, { menu = false }) {
                            DropdownMenuItem({ Text("Báo lỗi chương") }, {
                                menu = false
                                if (Session.user == null) Nav.push(Screen.Login) else report = true
                            })
                            DropdownMenuItem({ Text("Thông tin truyện") }, { menu = false; Nav.push(Screen.Detail(s.novelId)) })
                        }
                    }
                }
            }
        }
        AnimatedVisibility(bars, Modifier.align(Alignment.BottomCenter)) {
            Surface(color = bg, contentColor = fg, shadowElevation = 2.dp) {
                Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(4.dp), Arrangement.SpaceAround, Alignment.CenterVertically) {
                    TextButton({ go(s.index - 1) }, enabled = s.index > 1) { Text("‹ Trước", color = fg) }
                    IconButton({ sheet = 1 }) { Icon(Icons.AutoMirrored.Filled.List, "Mục lục") }
                    TextButton({ sheet = 2 }) { Text("Aa", color = fg, fontSize = 18.sp) }
                    TextButton({ go(s.index + 1) }) { Text("Sau ›", color = fg) }
                }
            }
        }
        msg?.let { Snackbar(Modifier.align(Alignment.BottomCenter).padding(16.dp, 96.dp)) { Text(it) } }
    }

    if (sheet != 0) ModalBottomSheet({ sheet = 0 }) {
        if (sheet == 1) TocSheet(s.novelId, s.index) { sheet = 0; go(it) } else StyleSheet(st)
    }
    if (report) ReportDialog({ report = false }) { reason ->
        report = false
        scope.launch {
            runCatching { Api.report(s.novelId, s.index, reason) }.onSuccess { msg = "Đã gửi báo lỗi" }.onFailure { msg = friendly(it) }
        }
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
        Text(label, textAlign = TextAlign.Center)
        if (canRequest) {
            Spacer(Modifier.height(12.dp))
            Button(request) { Text(if (status == "failed") "Dịch lại" else "Yêu cầu dịch") }
        }
    }

@Composable
private fun TocSheet(novelId: Int, current: Int, pick: (Int) -> Unit) {
    val toc = load("toc", novelId) { Api.toc(novelId) }
    Box(Modifier.fillMaxHeight(0.75f)) {
        Loaded(toc) { chapters ->
            val list = rememberLazyListState(chapters.indexOfFirst { it.index == current }.coerceAtLeast(0))
            LazyColumn(state = list) {
                itemsIndexed(chapters, key = { _, c -> c.index }) { _, c ->
                    Text(
                        c.title,
                        Modifier.fillMaxWidth().clickable { pick(c.index) }
                            .background(if (c.index == current) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
                            .padding(20.dp, 12.dp),
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        color = if (c.done) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
                    )
                }
            }
        }
    }
}

@Composable
private fun StyleSheet(st: Style) {
    DisposableEffect(Unit) { onDispose { st.save() } }
    Column(Modifier.padding(24.dp, 0.dp, 24.dp, 32.dp)) {
        Text("Cỡ chữ ${st.size.toInt()}")
        Slider(st.size, { st.size = it }, valueRange = 14f..32f)
        Text("Giãn dòng ${"%.1f".format(st.lh)}")
        Slider(st.lh, { st.lh = it }, valueRange = 1.2f..2.4f)
        Text("Lề hai bên ${st.margin.toInt()}")
        Slider(st.margin, { st.margin = it }, valueRange = 8f..48f)
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), Arrangement.SpaceBetween) {
            themes.forEachIndexed { i, (name, b, f) ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier.size(44.dp).clip(CircleShape).background(b).clickable { st.theme = i }
                            .then(if (st.theme == i) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, CircleShape) else Modifier),
                        Alignment.Center,
                    ) { Text("A", color = f) }
                    Text(name, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Căn đều hai bên", Modifier.weight(1f))
            Switch(st.justify, { st.justify = it })
        }
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
