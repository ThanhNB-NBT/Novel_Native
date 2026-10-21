package com.thanhnb.novelnative

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExploreTab() {
    val l = load("home") { coroutineScope { Section.entries.map { async { Api.section(it, 0, 15) } }.awaitAll() } }
    Column {
        TopAppBar(
            title = { Text("Khám phá") },
            actions = { IconButton({ Nav.push(Screen.Search) }) { Icon(Icons.Default.Search, "Tìm truyện") } },
        )
        Loaded(l) { lists ->
            PullToRefreshBox(false, l.reload) {
                LazyColumn(Modifier.fillMaxSize()) {
                    Section.entries.forEachIndexed { i, s ->
                        item(s) {
                            Row(Modifier.fillMaxWidth().padding(start = 16.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(s.title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                                TextButton({ Nav.push(Screen.More(s)) }) { Text("Xem tất cả") }
                            }
                            LazyRow(contentPadding = PaddingValues(horizontal = 12.dp)) {
                                items(lists[i], key = { it.id }) { n ->
                                    Column(Modifier.width(108.dp).clickable { Nav.push(Screen.Detail(n.id)) }.padding(4.dp)) {
                                        Cover(n.cover, 100.dp, 140.dp)
                                        Text(n.title, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }
                        }
                    }
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
                        Text(friendly(err))
                        TextButton({ err = null }) { Text("Thử lại") }
                    }
                    !done -> Box(Modifier.fillMaxWidth().padding(16.dp), Alignment.Center) { CircularProgressIndicator() }
                    items.isEmpty() -> Empty("Chưa có truyện")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
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
    Scaffold(topBar = {
        TopAppBar(
            title = {
                TextField(
                    q, { q = it }, Modifier.fillMaxWidth().padding(end = 8.dp),
                    placeholder = { Text("Tên truyện, tác giả…") },
                    singleLine = true,
                    trailingIcon = { if (q.isNotEmpty()) IconButton({ q = "" }) { Icon(Icons.Default.Clear, "Xoá") } },
                )
            },
            navigationIcon = { IconButton(Nav::pop) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Quay lại") } },
        )
    }) { pad ->
        Column(Modifier.padding(pad)) {
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                r == null -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
                r.isFailure -> Empty(friendly(r.exceptionOrNull()))
                r.getOrThrow().isEmpty() -> Empty("Không tìm thấy truyện nào")
                else -> LazyColumn { items(r.getOrThrow(), key = { it.id }) { NovelRow(it) } }
            }
        }
    }
}

/** Chip lọc mở menu chọn một giá trị; chọn lại "Tất cả" để bỏ lọc. */
@Composable
private fun <T> Pick(label: String, current: String?, options: List<Pair<T, String>>, onPick: (T?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        FilterChip(current != null, { open = true }, { Text(current ?: label) })
        DropdownMenu(open, { open = false }) {
            DropdownMenuItem({ Text("Tất cả") }, { onPick(null); open = false })
            options.forEach { (v, text) -> DropdownMenuItem({ Text(text) }, { onPick(v); open = false }) }
        }
    }
}
