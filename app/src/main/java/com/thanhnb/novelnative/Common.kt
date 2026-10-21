package com.thanhnb.novelnative

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage

sealed interface Screen {
    data object Tabs : Screen
    data class Detail(val id: Int) : Screen
    data class Read(val novelId: Int, val title: String, val index: Int) : Screen
    data class More(val section: Section) : Screen
    data object Search : Screen
    data object Login : Screen
}

// ponytail: ngăn xếp màn hình bằng state, thêm navigation-compose khi cần deep link
object Nav {
    var stack by mutableStateOf(listOf<Screen>(Screen.Tabs))
    var tab by mutableIntStateOf(0)
    fun push(s: Screen) { stack = stack + s }
    fun pop() { if (stack.size > 1) stack = stack.dropLast(1) }
    fun replace(s: Screen) { stack = stack.dropLast(1) + s }
}

class Loader<T>(val res: Result<T>?, val reload: () -> Unit)

/** Kết quả gần nhất theo key, để quay lại màn cũ thấy ngay dữ liệu (rồi mới tải lại nền). */
private val memo = HashMap<List<Any?>, Any?>()

/**
 * Gọi API theo key: hiện ngay bản đã nhớ (nếu có) rồi tải lại nền — stale-while-revalidate.
 * Lỗi lúc tải lại nền không đè dữ liệu đang hiện.
 */
@Suppress("UNCHECKED_CAST")
@Composable
fun <T> load(vararg keys: Any?, block: suspend () -> T): Loader<T> {
    val key = keys.toList()
    var tick by remember(key) { mutableIntStateOf(0) }
    var res by remember(key) { mutableStateOf(if (key in memo) Result.success(memo[key] as T) else null) }
    LaunchedEffect(key, tick) {
        val r = runCatching { block() }
        r.onSuccess { memo[key] = it }
        if (r.isSuccess || res?.isSuccess != true) res = r
    }
    return Loader(res) { tick++ }
}

@Composable
fun <T> Loaded(l: Loader<T>, content: @Composable (T) -> Unit) {
    val res = l.res
    when {
        res == null -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
        res.isFailure -> Column(
            Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(friendly(res.exceptionOrNull()), textAlign = TextAlign.Center)
            Spacer(Modifier.height(12.dp))
            Button(l.reload) { Text("Thử lại") }
        }
        else -> content(res.getOrThrow())
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Bar(title: String, actions: @Composable RowScope.() -> Unit = {}) = TopAppBar(
    title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
    navigationIcon = { IconButton(Nav::pop) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Quay lại") } },
    actions = actions,
)

@Composable
fun Cover(url: String?, w: Dp, h: Dp, modifier: Modifier = Modifier) = AsyncImage(
    Api.thumb(url, if (w > 80.dp) 320 else 160), null,
    modifier.size(w, h).clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
    contentScale = ContentScale.Crop,
)

@Composable
fun NovelRow(n: Novel, sub: String? = null, onClick: () -> Unit = { Nav.push(Screen.Detail(n.id)) }, trailing: @Composable () -> Unit = {}) =
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Cover(n.cover, 56.dp, 80.dp)
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(n.title, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (n.author.isNotEmpty()) Text(n.author, style = MaterialTheme.typography.bodySmall, maxLines = 1)
            Text(
                sub ?: listOf(Api.statusLabel(n.status), "${n.chaptersSource} chương", n.source).filter(String::isNotEmpty).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        trailing()
    }

@Composable
fun NeedLogin(why: String) = Column(
    Modifier.fillMaxSize().padding(32.dp),
    verticalArrangement = Arrangement.Center,
    horizontalAlignment = Alignment.CenterHorizontally,
) {
    Text(why, textAlign = TextAlign.Center)
    Spacer(Modifier.height(16.dp))
    Button({ Nav.push(Screen.Login) }) { Text("Đăng nhập") }
}

@Composable
fun Empty(text: String) = Box(Modifier.fillMaxWidth().padding(32.dp), Alignment.Center) {
    Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
}
