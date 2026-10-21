package com.thanhnb.novelnative

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
        res == null -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator(strokeWidth = 3.dp) }
        res.isFailure -> Column(
            Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(friendly(res.exceptionOrNull()), textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(16.dp))
            Button(l.reload) { Text("Thử lại") }
        }
        else -> content(res.getOrThrow())
    }
}

/** Thanh tiêu đề màn con: nút lùi + chữ đậm, nền giấy, không đổ bóng. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Bar(title: String, actions: @Composable RowScope.() -> Unit = {}) = TopAppBar(
    title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontFamily = Jakarta, fontWeight = FontWeight.Bold, fontSize = 20.sp, letterSpacing = (-0.4).sp) },
    navigationIcon = { IconButton(Nav::pop) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Quay lại") } },
    actions = actions,
)

/** Tiêu đề trang kiểu Flutter: nhãn phụ in hoa giãn chữ màu son + tên trang chữ có chân + dấu triện. */
@Composable
fun PageHeader(eyebrow: String, title: String, seal: String, back: Boolean = false, actions: @Composable RowScope.() -> Unit = {}) =
    Row(
        Modifier.statusBarsPadding().padding(start = if (back) 4.dp else 20.dp, top = 16.dp, end = 8.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (back) IconButton(Nav::pop) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Quay lại") }
        Column(Modifier.weight(1f)) {
            if (eyebrow.isNotEmpty()) {
                Text(eyebrow.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, letterSpacing = 3.sp)
                Spacer(Modifier.height(2.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, Modifier.weight(1f, fill = false), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.width(10.dp))
                Seal(seal, 22.dp)
            }
        }
        actions()
    }

/** Dấu triện son, hơi lệch như đóng tay. */
@Composable
fun Seal(char: String?, size: Dp = 20.dp) {
    val bg = MaterialTheme.colorScheme.background
    val paper = if (bg.luminance() < .5f) bg else Color.White
    Box(
        Modifier.rotate(-4f).size(size).background(LocalSeal.current, RoundedCornerShape(size * 0.16f)).padding(size * 0.09f)
            .border(size * 0.045f, paper.copy(alpha = .8f), RoundedCornerShape(size * 0.1f)),
        Alignment.Center,
    ) {
        if (char != null) Text(char, color = paper, fontSize = (size.value * 0.54f).sp, fontWeight = FontWeight.Bold, lineHeight = (size.value * 0.6f).sp)
    }
}

/** Đầu mục: chữ có chân + "Xem tất cả ›" màu son. */
@Composable
fun SectionHeader(title: String, onMore: (() -> Unit)? = null) =
    Row(Modifier.fillMaxWidth().padding(start = 20.dp, top = 18.dp, end = 12.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall)
        if (onMore != null) TextButton(onMore, contentPadding = PaddingValues(horizontal = 10.dp)) {
            Text("Xem tất cả", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
            Icon(Icons.Rounded.ChevronRight, null, Modifier.size(16.dp))
        }
    }

val Mono = FontFamily(Font(R.font.mono_medium, FontWeight.Medium))

/** Chữ số (số chương, %) dùng mono như bản Flutter. */
@Composable
fun monoStyle(size: Int = 12, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) =
    TextStyle(fontFamily = Mono, fontSize = size.sp, letterSpacing = (-0.3).sp, color = color)

@Composable
fun IconStat(icon: ImageVector, value: String) = Row(verticalAlignment = Alignment.CenterVertically) {
    Icon(icon, null, Modifier.size(14.dp), MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.width(4.dp))
    Text(value, style = monoStyle())
}

/** Bìa: bo 8, viền mảnh + bóng nhẹ; thiếu ảnh thì nền son nhạt + chữ cái đầu. */
@Composable
fun Cover(url: String?, w: Dp, h: Dp, modifier: Modifier = Modifier, label: String? = null, flat: Boolean = false) {
    val cs = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier.size(w, h).then(if (flat) Modifier else Modifier.shadow(4.dp, shape, ambientColor = Color.Black.copy(.3f), spotColor = Color.Black.copy(.3f)))
            .clip(shape).background(cs.primaryContainer.copy(alpha = .55f)).border(1.dp, cs.outlineVariant.copy(alpha = .7f), shape),
        Alignment.Center,
    ) {
        val initial = label?.trim()?.firstOrNull()
        if (initial == null) Icon(Icons.Rounded.AutoStories, null, Modifier.size(w * 0.32f), cs.primary.copy(alpha = .8f))
        else Text(initial.uppercase(), color = cs.primary, fontSize = (w.value * 0.42f).sp, fontWeight = FontWeight.ExtraBold)
        if (!url.isNullOrEmpty()) AsyncImage(Api.thumb(url, if (w > 80.dp) 320 else 160), label, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
    }
}

/** Nhãn nguồn nhỏ nền đen mờ đè lên bìa. */
@Composable
fun SourceBadge(name: String, modifier: Modifier = Modifier) = Text(
    name, modifier.background(Color.Black.copy(alpha = .55f), RoundedCornerShape(7.dp)).padding(horizontal = 7.dp, vertical = 3.dp),
    color = Color.White, fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
)

/** Một dòng truyện liền mạch: bìa 76 + tên, tác giả, thể loại, số chương/nguồn ngang chân bìa. */
@Composable
fun NovelRow(n: Novel, sub: String? = null, onClick: () -> Unit = { Nav.push(Screen.Detail(n.id)) }, trailing: @Composable () -> Unit = {}) =
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp)) {
        val t = MaterialTheme.typography
        val cs = MaterialTheme.colorScheme
        Cover(n.cover, 76.dp, 103.dp, label = n.title)
        Column(Modifier.weight(1f).heightIn(min = 103.dp).padding(start = 14.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text(n.title, style = t.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (n.author.isNotEmpty()) Text(n.author, Modifier.padding(top = 3.dp), style = t.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (n.genres.isNotEmpty()) Text(n.genres.take(3).joinToString(" · "), style = t.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (sub != null) Text(sub, style = t.labelMedium, color = cs.primary, fontWeight = FontWeight.SemiBold, maxLines = 1)
            else Row(verticalAlignment = Alignment.CenterVertically) {
                IconStat(Icons.AutoMirrored.Rounded.MenuBook, "${n.chaptersSource}")
                if (n.chaptersTranslated == 0) Text("Chưa dịch", Modifier.padding(start = 10.dp), style = t.labelSmall, color = cs.tertiary)
                if (n.source.isNotEmpty()) Text(n.source, Modifier.padding(start = 10.dp), style = t.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        trailing()
    }

/** Thẻ giấy: nền sáng hơn trang một bậc, viền mảnh, bo 18. */
@Composable
fun PaperCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val cs = MaterialTheme.colorScheme
    val colors = CardDefaults.cardColors(containerColor = cs.card)
    val border = BorderStroke(1.dp, cs.outlineVariant)
    if (onClick != null) Card(onClick, modifier, shape = RoundedCornerShape(18.dp), colors = colors, border = border, content = content)
    else Card(modifier, shape = RoundedCornerShape(18.dp), colors = colors, border = border, content = content)
}

/** Nhãn mục nhỏ in hoa giãn chữ màu son ("GIAO DIỆN", "ỨNG DỤNG"). */
@Composable
fun Eyebrow(text: String, modifier: Modifier = Modifier) =
    Text(text.uppercase(), modifier, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, letterSpacing = 2.sp, fontSize = 13.sp)

@Composable
fun NeedLogin(why: String, icon: ImageVector = Icons.AutoMirrored.Rounded.MenuBook) = Column(
    Modifier.fillMaxSize().padding(32.dp),
    verticalArrangement = Arrangement.Center,
    horizontalAlignment = Alignment.CenterHorizontally,
) {
    Icon(icon, null, Modifier.size(56.dp), MaterialTheme.colorScheme.primary.copy(alpha = .55f))
    Spacer(Modifier.height(20.dp))
    Text(why, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(20.dp))
    Button({ Nav.push(Screen.Login) }, contentPadding = PaddingValues(horizontal = 40.dp, vertical = 14.dp)) {
        Text("Đăng nhập", fontSize = 17.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun Empty(text: String) = Box(Modifier.fillMaxWidth().padding(32.dp), Alignment.Center) {
    Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium)
}

/** Khoảng chừa dưới cùng cho dock nổi ở các tab. */
val DockSpace = 110.dp
