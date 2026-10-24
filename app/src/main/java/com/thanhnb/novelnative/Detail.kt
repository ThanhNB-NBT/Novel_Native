package com.thanhnb.novelnative

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
            Column(Modifier.fillMaxSize()) {
                Header(n)
                TabRow(tab, containerColor = Color.Transparent, contentColor = cs.primary, divider = { HorizontalDivider(color = cs.outlineVariant) }) {
                    listOf("Giới thiệu", "Danh sách chương").forEachIndexed { i, t ->
                        Tab(tab == i, { tab = i }, text = { Text(t, style = MaterialTheme.typography.titleMedium, color = if (tab == i) cs.primary else cs.onSurfaceVariant) })
                    }
                }
                val bottom = PaddingValues(bottom = 96.dp)
                if (tab == 0) LazyColumn(Modifier.weight(1f), contentPadding = bottom) {
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
                        Column(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(20.dp, 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Fact("Tác giả", n.author)
                                Fact("Nguồn", n.source)
                                Fact("Mã truyện", "#${n.id}")
                            }
                        }
                        HorizontalDivider(Modifier.padding(horizontal = 20.dp, vertical = 6.dp), color = cs.outlineVariant.copy(.5f))
                        Column(Modifier.fillMaxWidth().bounceClick { Nav.push(Screen.Glossary(n.id)) }) {
                            Row(Modifier.padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.Translate, null, tint = cs.primary)
                                Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                                    Text("Thuật ngữ truyện", style = MaterialTheme.typography.titleMedium)
                                    Text("Tên người, địa danh… dịch thống nhất cả truyện", style = MaterialTheme.typography.labelMedium)
                                }
                                Icon(Icons.Rounded.ChevronRight, null, tint = cs.onSurfaceVariant)
                            }
                        }
                        OfflineCard(n)
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
                } else LazyColumn(Modifier.weight(1f), contentPadding = bottom) {
                    item {
                        Row(Modifier.padding(20.dp, 8.dp, 8.dp, 0.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (selecting) {
                                Text("Đã chọn ${sel.size}", Modifier.weight(1f), style = monoStyle(13, cs.primary))
                                IconButton({ sel = chapters.filter { it.done }.map { it.index }.toSet() }) { Icon(Icons.Rounded.DoneAll, "Chọn hết chương đã dịch") }
                                TextButton({ selecting = false }) { Text("Huỷ") }
                                Button({ askRe = true }, enabled = sel.isNotEmpty(), contentPadding = PaddingValues(horizontal = 14.dp)) { Text("Dịch lại (${sel.size})") }
                            } else {
                                Text("${chapters.size} chương", Modifier.weight(1f), style = monoStyle(13))
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
                                .padding(20.dp, 13.dp),
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
                            if (!c.done) Icon(Icons.Rounded.HourglassEmpty, "chưa dịch", Modifier.size(16.dp), cs.onSurfaceVariant.copy(.6f))
                        }
                        HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = cs.outlineVariant.copy(.6f))
                    }
                }
            }
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
                                .onSuccess { msg = if (followed) "Đã bỏ theo dõi" else "Đã thêm vào Tủ truyện" }
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
                        .onSuccess { msg = "Đã xếp chương vào hàng đợi dịch (tới chương $to)" }
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
                                .onSuccess { msg = "Đã xếp ${picked.size} chương để dịch lại" }
                                .onFailure { msg = friendly(it) }
                            toc.reload()
                        }
                    }) { Text("Dịch lại") }
                },
                dismissButton = { TextButton({ askRe = false }) { Text("Huỷ") } },
                title = { Text("Dịch lại ${sel.size} chương?") },
                text = { Text("Bản cũ vẫn đọc được cho tới khi bản mới thay thế.") },
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
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Header(n: Novel) {
    val cs = MaterialTheme.colorScheme
    Box(Modifier.clipToBounds()) {
        AsyncImage(Api.thumb(n.cover, 400), null, Modifier.matchParentSize().blur(10.dp), contentScale = ContentScale.Crop)
        Box(
            Modifier.matchParentSize().background(
                Brush.verticalGradient(0f to Color.Black.copy(.45f), .55f to lerp(Color.Black, cs.primary, .35f).copy(.55f), 1f to cs.background),
            ),
        )
        Column(Modifier.statusBarsPadding()) {
            IconButton(Nav::pop) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Quay lại", tint = Color.White) }
            Row(Modifier.padding(20.dp, 8.dp, 20.dp, 0.dp), verticalAlignment = Alignment.Bottom) {
                Cover(n.cover, 124.dp, 174.dp, label = n.title)
                Column(Modifier.weight(1f).padding(start = 16.dp)) {
                    Text(n.title, style = MaterialTheme.typography.headlineSmall, color = Color.White, fontWeight = FontWeight.ExtraBold, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(6.dp))
                    Text(n.author, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(.85f))
                }
            }
            Text(
                listOf("${n.chaptersSource} chương", "${n.chaptersTranslated} đã dịch", Api.statusLabel(n.status), n.source).filter(String::isNotEmpty).joinToString(" · "),
                Modifier.padding(20.dp, 16.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
