package com.thanhnb.novelnative

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlin.math.absoluteValue

private fun open(n: Novel) = Nav.push(Screen.Detail(n.id))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExploreTab() {
    val l = load("home") { coroutineScope { Section.entries.map { async { Api.section(it, 0, 15) } }.awaitAll() } }
    Loaded(l) { lists ->
        val (latest, featured, recommended, completed) = lists
        PullToRefreshBox(false, l.reload) {
            // Mỗi mục một kiểu bày riêng như Flutter: băng chuyền → bìa nhỏ → thẻ dọc → bảng xếp hạng → poster
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = DockSpace)) {
                item("hero") {
                    Box {
                        if (recommended.isNotEmpty()) HeroCarousel(recommended.take(6)) else Spacer(Modifier.statusBarsPadding().height(80.dp))
                        Brand()
                    }
                }
                if (latest.isNotEmpty()) item("latest") { Spotlight(Section.Latest, latest.take(8)) }
                if (recommended.size > 1) item("rec") { Rail(Section.Recommended, recommended.drop(1)) }
                if (featured.isNotEmpty()) item("feat") { Ranking(Section.Featured, featured.take(6)) }
                if (completed.isNotEmpty()) item("done") { PosterRail(Section.Completed, completed) }
            }
        }
    }
}

/** Logo + "— KHÁM PHÁ / Gác truyện" nổi trên băng chuyền. */
@Composable
private fun Brand() {
    val cs = MaterialTheme.colorScheme
    Row(Modifier.statusBarsPadding().padding(start = 16.dp, end = 8.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Image(painterResource(R.drawable.gt_logo), null, Modifier.height(44.dp), colorFilter = ColorFilter.tint(cs.onSurface))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(14.dp).height(2.dp).background(cs.primary))
                Spacer(Modifier.width(6.dp))
                Text("KHÁM PHÁ", style = MaterialTheme.typography.labelSmall, color = cs.primary, letterSpacing = 3.sp)
            }
            Text("Gác truyện", style = MaterialTheme.typography.displaySmall)
        }
        IconButton({ Nav.push(Screen.Search) }) { Icon(Icons.Rounded.Search, "Tìm truyện") }
        IconButton({ Nav.push(Screen.Search) }) { Icon(Icons.Rounded.Tune, "Lọc truyện") }
    }
}

/** Băng chuyền đề cử: nền là chính bìa phóng to + mờ, tan dần vào giấy; bìa nét đứng giữa như poster. */
@Composable
private fun HeroCarousel(items: List<Novel>) {
    val pager = rememberPagerState { items.size }
    val cs = MaterialTheme.colorScheme
    val bg = cs.background
    Column {
        HorizontalPager(pager, Modifier.fillMaxWidth().height(400.dp + WindowInsets.statusBars.asPaddingValues().calculateTopPadding())) { page ->
            val n = items[page]
            val delta = ((pager.currentPage - page) + pager.currentPageOffsetFraction).coerceIn(-1f, 1f)
            Box(Modifier.fillMaxSize().clickable { open(n) }) {
                AsyncImage(Api.thumb(n.cover, 300), null, Modifier.fillMaxSize().blur(16.dp), contentScale = ContentScale.Crop)
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to bg.copy(.78f), 0.3f to bg.copy(.25f), 0.72f to bg.copy(.86f), 1f to bg)))
                Column(Modifier.fillMaxSize().statusBarsPadding().padding(top = 78.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Cover(
                        n.cover, 132.dp, 185.dp, label = n.title, flat = true,
                        modifier = Modifier
                            .graphicsLayer { translationX = delta * 70.dp.toPx(); val s = 1 - .12f * delta.absoluteValue; scaleX = s; scaleY = s }
                            .shadow(24.dp, RoundedCornerShape(10.dp), ambientColor = cs.primary, spotColor = cs.primary),
                    )
                    Spacer(Modifier.weight(1f))
                    Column(
                        Modifier.padding(horizontal = 20.dp).padding(bottom = 10.dp)
                            .graphicsLayer { alpha = (1 - delta.absoluteValue * 1.4f).coerceIn(0f, 1f); translationX = delta * 26.dp.toPx() },
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(n.title, style = MaterialTheme.typography.titleLarge.copy(lineHeight = 1.15.em), maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(3.dp))
                        Text(
                            listOf(n.author, "${n.chaptersSource} chương", Api.statusLabel(n.status)).filter(String::isNotEmpty).joinToString(" · "),
                            style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(8.dp))
                        Button(
                            { if (Session.user == null) Nav.push(Screen.Login) else Nav.push(Screen.Read(n.id, n.title, 1)) },
                            contentPadding = PaddingValues(horizontal = 26.dp, vertical = 8.dp),
                        ) { Text("Đọc ngay", fontSize = 13.sp) }
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.align(Alignment.CenterHorizontally), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(items.size) { i ->
                val on = i == pager.currentPage
                Box(Modifier.height(6.dp).width(if (on) 22.dp else 6.dp).clip(CircleShape).background(if (on) cs.primary else cs.outlineVariant))
            }
        }
    }
}

private val Double.em get() = androidx.compose.ui.unit.TextUnit(this.toFloat(), androidx.compose.ui.unit.TextUnitType.Em)

/** "Mới cập nhật": dải bìa nhỏ phẳng + tên. */
@Composable
private fun Spotlight(s: Section, items: List<Novel>) = Column {
    SectionHeader(s.title) { Nav.push(Screen.More(s)) }
    LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(items, key = { it.id }) { n ->
            Column(Modifier.width(76.dp).clickable { open(n) }) {
                Cover(n.cover, 76.dp, 106.dp, label = n.title, flat = true)
                Spacer(Modifier.height(6.dp))
                Text(n.title, style = MaterialTheme.typography.labelMedium, maxLines = 2, overflow = TextOverflow.Ellipsis, minLines = 2)
            }
        }
    }
}

/** "Đề cử": thẻ dọc 132 — số chương ở chân bìa, nguồn ở góc. */
@Composable
private fun Rail(s: Section, items: List<Novel>) = Column {
    SectionHeader(s.title) { Nav.push(Screen.More(s)) }
    LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        items(items, key = { it.id }) { n ->
            Column(Modifier.width(132.dp).clickable { open(n) }) {
                Box {
                    Cover(n.cover, 132.dp, 185.dp, label = n.title)
                    Row(
                        Modifier.align(Alignment.BottomStart).padding(6.dp).background(Color.Black.copy(.55f), RoundedCornerShape(7.dp)).padding(horizontal = 6.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.MenuBook, null, Modifier.size(12.dp), Color.White)
                        Spacer(Modifier.width(4.dp))
                        Text("${n.chaptersSource}", style = monoStyle(11, Color.White))
                    }
                    if (n.source.isNotEmpty()) SourceBadge(n.source, Modifier.align(Alignment.TopEnd).padding(6.dp))
                }
                Spacer(Modifier.height(8.dp))
                Text(n.title, style = MaterialTheme.typography.titleMedium.copy(fontSize = 14.5.sp, lineHeight = 17.sp), maxLines = 2, minLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** "Nổi bật": bảng xếp hạng số nghiêng, top 3 màu son. */
@Composable
private fun Ranking(s: Section, items: List<Novel>) = Column {
    val cs = MaterialTheme.colorScheme
    SectionHeader(s.title) { Nav.push(Screen.More(s)) }
    items.forEachIndexed { i, n ->
        Row(Modifier.fillMaxWidth().clickable { open(n) }.padding(start = 20.dp, end = 20.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${i + 1}", Modifier.width(34.dp), style = MaterialTheme.typography.headlineMedium,
                fontStyle = FontStyle.Italic, fontWeight = FontWeight.ExtraBold, color = if (i < 3) cs.primary else cs.onSurfaceVariant.copy(.45f),
            )
            Cover(n.cover, 46.dp, 64.dp, label = n.title)
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(n.title, style = MaterialTheme.typography.titleMedium.copy(fontSize = 14.5.sp), maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(3.dp))
                Text((listOf(n.source) + n.genres.take(3)).filter(String::isNotEmpty).joinToString(" · "), style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** "Đã hoàn thành": poster lớn, tên đè lên chân bìa. */
@Composable
private fun PosterRail(s: Section, items: List<Novel>) = Column {
    SectionHeader(s.title) { Nav.push(Screen.More(s)) }
    LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        items(items, key = { it.id }) { n ->
            Box(Modifier.clickable { open(n) }) {
                Cover(n.cover, 130.dp, 182.dp, label = n.title)
                Box(Modifier.matchParentSize().clip(RoundedCornerShape(8.dp)).background(Brush.verticalGradient(0.5f to Color.Transparent, 1f to Color.Black.copy(.75f))))
                if (n.source.isNotEmpty()) SourceBadge(n.source, Modifier.align(Alignment.TopStart).padding(8.dp))
                Column(Modifier.align(Alignment.BottomStart).width(130.dp).padding(8.dp)) {
                    Text(n.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp, lineHeight = 15.sp, fontFamily = Jakarta, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text("${n.chaptersSource} chương", color = Color.White.copy(.85f), fontSize = 11.sp, fontFamily = Jakarta)
                }
            }
        }
    }
}

/** "Xem tất cả" một mục — cuộn tới cuối thì tải thêm 30. */
@Composable
fun MoreScreen(s: Section) {
    val items = remember { mutableStateListOf<Novel>() }
    var done by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<Throwable?>(null) }
    val list = rememberLazyListState()
    val atEnd by remember { derivedStateOf { list.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 >= items.size - 5 } }
    LaunchedEffect(atEnd, done, err) {
        if (!atEnd || done || err != null) return@LaunchedEffect
        runCatching { Api.section(s, items.size, 30) }
            .onSuccess { items += it.filter { n -> items.none { o -> o.id == n.id } }; if (it.size < 30) done = true }
            .onFailure { err = it }
    }
    Scaffold(topBar = { Bar(s.title) }) { pad ->
        LazyColumn(Modifier.padding(pad), list) {
            items(items, key = { it.id }) { NovelRow(it) }
            item {
                when {
                    err != null -> Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(friendly(err), style = MaterialTheme.typography.bodyMedium)
                        TextButton({ err = null }) { Text("Thử lại") }
                    }
                    !done -> Box(Modifier.fillMaxWidth().padding(16.dp), Alignment.Center) { CircularProgressIndicator(strokeWidth = 3.dp) }
                    items.isEmpty() -> Empty("Chưa có truyện")
                }
            }
        }
    }
}

@Composable
fun SearchScreen() {
    var q by rememberSaveable { mutableStateOf("") }
    var f by remember { mutableStateOf(Filter()) }
    val genres = load("genres") { Api.genres() }.res?.getOrNull().orEmpty()
    val sources = load("sources") { Api.sources() }.res?.getOrNull().orEmpty()
    var res by remember { mutableStateOf<Result<List<Novel>>?>(null) }
    LaunchedEffect(q, f) {
        delay(350) // gõ liền tay thì chỉ hỏi server lần cuối
        res = null
        res = runCatching { Api.search(f.copy(q = q)) }
    }
    val cs = MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.padding(start = 4.dp, end = 16.dp, top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(Nav::pop) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Quay lại") }
            TextField(
                q, { q = it }, Modifier.weight(1f),
                placeholder = { Text("Tên truyện, tác giả…") },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                trailingIcon = { if (q.isNotEmpty()) IconButton({ q = "" }) { Icon(Icons.Rounded.Clear, "Xoá") } },
                singleLine = true,
                shape = RoundedCornerShape(28.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = cs.card, unfocusedContainerColor = cs.card,
                    focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                ),
            )
        }
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pick("Trạng thái", f.status?.let(Api::statusLabel), listOf("ongoing", "completed").map { it to Api.statusLabel(it) }) {
                f = f.copy(status = it)
            }
            Pick("Thể loại", f.genre, genres.map { it to it }) { f = f.copy(genre = it) }
            Pick(
                "Số chương", f.minChapters.takeIf { it > 0 }?.let { "≥ $it" },
                listOf(100, 500, 1000, 2000, 3000, 5000).map { it to "≥ $it chương" },
            ) { f = f.copy(minChapters = it ?: 0) }
            Pick(
                "Nguồn", f.sources.firstOrNull()?.let { id -> sources.firstOrNull { it.first == id }?.second },
                sources,
            ) { f = f.copy(sources = setOfNotNull(it)) }
        }
        val r = res
        when {
            r == null -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator(strokeWidth = 3.dp) }
            r.isFailure -> Empty(friendly(r.exceptionOrNull()))
            r.getOrThrow().isEmpty() -> Empty("Không tìm thấy truyện nào")
            else -> LazyColumn(contentPadding = WindowInsets.navigationBars.asPaddingValues()) { items(r.getOrThrow(), key = { it.id }) { NovelRow(it) } }
        }
    }
}

/** Chip lọc dạng viên thuốc, chọn thì nhuộm màu son; mở menu chọn một giá trị, "Tất cả" để bỏ lọc. */
@Composable
private fun <T> Pick(label: String, current: String?, options: List<Pair<T, String>>, onPick: (T?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val cs = MaterialTheme.colorScheme
    Box {
        FilterChip(
            current != null, { open = true }, { Text(current ?: label, fontWeight = FontWeight.SemiBold) },
            shape = CircleShape,
            colors = FilterChipDefaults.filterChipColors(
                containerColor = Color.Transparent, labelColor = cs.onSurface,
                selectedContainerColor = cs.primary.copy(alpha = .14f), selectedLabelColor = cs.primary,
            ),
            border = FilterChipDefaults.filterChipBorder(true, current != null, borderColor = cs.outlineVariant, selectedBorderColor = cs.primary.copy(.4f)),
        )
        DropdownMenu(open, { open = false }) {
            DropdownMenuItem({ Text("Tất cả") }, { onPick(null); open = false })
            options.forEach { (v, text) -> DropdownMenuItem({ Text(text) }, { onPick(v); open = false }) }
        }
    }
}
