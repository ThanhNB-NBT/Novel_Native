package com.thanhnb.novelnative

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage

private sealed interface Screen {
    data object Home : Screen
    data class Toc(val novel: Novel) : Screen
    data class Read(val novel: Novel, val index: Int) : Screen
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
                // ponytail: ngăn xếp màn hình bằng state, thêm navigation-compose khi có deep link
                var stack by remember { mutableStateOf(listOf<Screen>(Screen.Home)) }
                val push = { s: Screen -> stack = stack + s }
                BackHandler(stack.size > 1) { stack = stack.dropLast(1) }
                Surface(Modifier.fillMaxSize()) {
                    when (val s = stack.last()) {
                        Screen.Home -> HomeScreen { push(Screen.Toc(it)) }
                        is Screen.Toc -> TocScreen(s.novel) { push(Screen.Read(s.novel, it)) }
                        is Screen.Read -> ReaderScreen(s.novel, s.index) { stack = stack.dropLast(1) + Screen.Read(s.novel, it) }
                    }
                }
            }
        }
    }
}

/** Gọi API một lần theo key; null = đang tải. */
@Composable
private fun <T> load(key: Any, block: suspend () -> T): Result<T>? {
    var res by remember(key) { mutableStateOf<Result<T>?>(null) }
    LaunchedEffect(key) { res = runCatching { block() } }
    return res
}

@Composable
private fun <T> Loaded(res: Result<T>?, content: @Composable (T) -> Unit) {
    when {
        res == null -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
        res.isFailure -> Text(
            "Lỗi: ${res.exceptionOrNull()?.message}\n\nBật tunnel: ssh -N -L 8000:localhost:8000 box",
            Modifier.safeDrawingPadding().padding(16.dp),
        )
        else -> content(res.getOrThrow())
    }
}

@Composable
private fun HomeScreen(open: (Novel) -> Unit) {
    Loaded(load(Unit) { Api.novels() }) { novels ->
        LazyColumn(contentPadding = WindowInsets.safeDrawing.asPaddingValues()) {
            item { Text("Mới cập nhật", Modifier.padding(16.dp), style = MaterialTheme.typography.headlineSmall) }
            items(novels, key = { it.id }) { n ->
                Row(
                    Modifier.fillMaxWidth().clickable { open(n) }.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AsyncImage(
                        n.cover, null,
                        Modifier.size(56.dp, 80.dp).clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentScale = ContentScale.Crop,
                    )
                    Column(Modifier.padding(start = 12.dp)) {
                        Text(n.title, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(n.author, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                        Text("${n.chapters} chương", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun TocScreen(novel: Novel, read: (Int) -> Unit) {
    Loaded(load(novel.id) { Api.toc(novel.id) }) { toc ->
        LazyColumn(contentPadding = WindowInsets.safeDrawing.asPaddingValues()) {
            item { Text(novel.title, Modifier.padding(16.dp), style = MaterialTheme.typography.titleLarge) }
            items(toc, key = { it.index }) { c ->
                Text(c.title, Modifier.fillMaxWidth().clickable { read(c.index) }.padding(16.dp, 12.dp))
            }
        }
    }
}

@Composable
private fun ReaderScreen(novel: Novel, index: Int, go: (Int) -> Unit) {
    var fontSize by remember { mutableFloatStateOf(19f) }
    Loaded(load(novel.id to index) { Api.chapter(novel.id, index) }) { ch ->
        if (ch == null) return@Loaded Text("Không có chương $index", Modifier.safeDrawingPadding().padding(16.dp))
        val list = rememberLazyListState()
        LazyColumn(
            state = list,
            contentPadding = WindowInsets.safeDrawing.asPaddingValues(),
            modifier = Modifier.fillMaxSize(),
        ) {
            item {
                Column(Modifier.padding(20.dp, 8.dp)) {
                    Text(ch.title, style = MaterialTheme.typography.titleLarge)
                    Slider(fontSize, { fontSize = it }, valueRange = 14f..30f)
                }
            }
            // SelectionContainer: bôi chọn chữ dùng toolbar hệ thống — chỗ native khác Flutter rõ nhất
            items(ch.paragraphs) { p ->
                SelectionContainer {
                    Text(p, Modifier.padding(20.dp, 6.dp), fontSize = fontSize.sp, lineHeight = (fontSize * 1.6f).sp)
                }
            }
            item {
                Row(Modifier.fillMaxWidth().padding(16.dp), Arrangement.SpaceBetween) {
                    OutlinedButton({ go(index - 1) }, enabled = index > 1) { Text("‹ Chương trước") }
                    Button({ go(index + 1) }) { Text("Chương sau ›") }
                }
            }
        }
    }
}
