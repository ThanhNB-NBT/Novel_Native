package com.thanhnb.novelnative

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
    val scope = rememberCoroutineScope()
    var desc by remember { mutableStateOf(false) }
    var asc by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(msg) { kotlinx.coroutines.delay(2500); msg = null }

    Scaffold(topBar = { Bar(nl.res?.getOrNull()?.title.orEmpty()) }, snackbarHost = {
        msg?.let { m -> Snackbar(Modifier.padding(16.dp), action = { TextButton({ msg = null }) { Text("OK") } }) { Text(m) } }
    }) { pad ->
        Box(Modifier.padding(pad)) {
            Loaded(nl) { n ->
                val chapters = toc.res?.getOrNull().orEmpty()
                val first = chapters.firstOrNull()?.index ?: 1
                fun read(i: Int) = Nav.push(Screen.Read(n.id, n.title, i))
                LazyColumn {
                    item {
                        Row(Modifier.padding(16.dp)) {
                            Cover(n.cover, 110.dp, 156.dp)
                            Column(Modifier.padding(start = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(n.title, style = MaterialTheme.typography.titleLarge)
                                if (n.author.isNotEmpty()) Text(n.author, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    listOf(Api.statusLabel(n.status), n.source).filter(String::isNotEmpty).joinToString(" · "),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                Text("${n.chaptersSource} chương · đã dịch ${n.chaptersTranslated}", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        if (n.genres.isNotEmpty()) FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            n.genres.forEach { AssistChip({}, { Text(it) }) }
                        }
                        Row(Modifier.padding(16.dp, 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            val cur = progress.res?.getOrNull()
                            Button({ if (uid == null) Nav.push(Screen.Login) else read(cur ?: first) }, Modifier.weight(1f)) {
                                Text(if (cur != null) "Đọc tiếp · chương $cur" else "Bắt đầu đọc")
                            }
                            val followed = inLib.res?.getOrNull() == true
                            OutlinedButton(
                                {
                                    if (uid == null) return@OutlinedButton Nav.push(Screen.Login)
                                    busy = true
                                    scope.launch {
                                        runCatching { Api.setInLibrary(n.id, !followed) }
                                            .onSuccess { msg = if (followed) "Đã bỏ theo dõi" else "Đã thêm vào Tủ truyện" }
                                            .onFailure { msg = friendly(it) }
                                        inLib.reload(); busy = false
                                    }
                                },
                                enabled = !busy,
                            ) { Text(if (followed) "Đang theo dõi" else "Theo dõi") }
                        }
                        n.description?.let {
                            Text(
                                it, Modifier.clickable { desc = !desc }.padding(16.dp, 8.dp),
                                maxLines = if (desc) Int.MAX_VALUE else 4, overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                    val bm = marks.res?.getOrNull().orEmpty()
                    if (bm.isNotEmpty()) {
                        item { Text("Dấu trang", Modifier.padding(16.dp, 12.dp, 16.dp, 4.dp), style = MaterialTheme.typography.titleMedium) }
                        items(bm, key = { "b${it.id}" }) { b ->
                            ListItem(
                                headlineContent = { Text("Chương ${b.chapter} · ${(b.percent * 100).toInt()}%") },
                                supportingContent = b.excerpt?.let { { Text(it, maxLines = 2, overflow = TextOverflow.Ellipsis) } },
                                trailingContent = {
                                    TextButton({ scope.launch { runCatching { Api.deleteBookmark(b.id) }.onFailure { msg = friendly(it) }; marks.reload() } }) {
                                        Text("Xoá")
                                    }
                                },
                                modifier = Modifier.clickable {
                                    Prefs.setPercent(n.id, b.chapter, b.percent.toFloat()) // reader tự cuộn tới %
                                    read(b.chapter)
                                },
                            )
                        }
                    }
                    item {
                        Row(Modifier.padding(16.dp, 12.dp, 8.dp, 0.dp)) {
                            Text("Mục lục · ${chapters.size} chương", Modifier.weight(1f).align(androidx.compose.ui.Alignment.CenterVertically),
                                style = MaterialTheme.typography.titleMedium)
                            TextButton({ asc = !asc }) { Text(if (asc) "Cũ → mới" else "Mới → cũ") }
                        }
                        when {
                            // migration 122: chapters chỉ đọc được khi đăng nhập
                            uid == null -> TextButton({ Nav.push(Screen.Login) }, Modifier.padding(horizontal = 8.dp)) {
                                Text("Đăng nhập để xem mục lục và đọc truyện")
                            }
                            toc.res == null -> LinearProgressIndicator(Modifier.fillMaxWidth().padding(16.dp))
                            toc.res.isFailure -> Empty(friendly(toc.res.exceptionOrNull()))
                            chapters.size < n.chaptersSource -> Text(
                                "Mục lục đầy đủ sẽ được tải khi bạn mở đọc.",
                                Modifier.padding(horizontal = 16.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    items(if (asc) chapters else chapters.asReversed(), key = { it.index }) { c ->
                        Row(Modifier.fillMaxWidth().clickable { read(c.index) }.padding(16.dp, 12.dp)) {
                            Text(
                                c.title, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                                fontWeight = if (c.index == progress.res?.getOrNull()) FontWeight.Bold else null,
                                color = if (c.done) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (!c.done) Text("chưa dịch", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                        }
                    }
                }
            }
        }
    }
}
