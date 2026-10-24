package com.thanhnb.novelnative

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlaylistRemove
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Title
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject

/** Phân trang cuộn-tải-dần; tạo mới (remember theo bộ lọc) mỗi khi bộ lọc đổi. */
class Pager(private val size: Int, private val fetch: suspend (offset: Int) -> List<JSONObject>) {
    val items = mutableStateListOf<JSONObject>()
    var loading by mutableStateOf(false); private set
    var hasMore by mutableStateOf(true); private set
    var error by mutableStateOf<String?>(null); private set

    fun more(scope: CoroutineScope) {
        if (loading || !hasMore) return
        loading = true
        scope.launch {
            runCatching { fetch(items.size) }
                .onSuccess { items.addAll(it); hasMore = it.size == size }
                .onFailure { error = friendly(it); hasMore = false }
            loading = false
        }
    }
}

/** Gọi [Pager.more] khi cuộn gần cuối danh sách. */
@Composable
private fun LoadMoreOnScroll(state: LazyListState, pg: Pager) {
    val scope = rememberCoroutineScope()
    LaunchedEffect(pg, state) {
        snapshotFlow { state.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .collect { if (it >= state.layoutInfo.totalItemsCount - 6) pg.more(scope) }
    }
}

@Composable
private fun Spin() = Box(Modifier.fillMaxWidth().padding(16.dp), Alignment.Center) { CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp) }

private fun JSONObject.genreList() = optJSONArray("genres")?.let { a -> List(a.length()) { a.optString(it) } }.orEmpty()

/** Truyện: thống kê kho + tìm + lọc + ẩn/sửa/xoá. */
@Composable
fun NovelsTab() {
    var input by rememberSaveable { mutableStateOf("") }
    var q by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableIntStateOf(0) }
    var rev by remember { mutableIntStateOf(0) }
    LaunchedEffect(input) { if (input.trim() != q) { delay(300); q = input.trim() } }
    val pg = remember(q, filter, rev) { Pager(30) { off -> AdminApi.novelPage(q, filter, off, 30) } }
    LaunchedEffect(pg) { pg.more(this) }
    val stats = load("admin.stats", rev) { AdminApi.appStats() }.res?.getOrNull()
    val counts = load("admin.ncounts", q, rev) { AdminApi.novelCounts(q) }.res?.getOrNull()
    val state = rememberLazyListState()
    LoadMoreOnScroll(state, pg)
    var act by remember { mutableStateOf<Act?>(null) }
    var edit by remember { mutableStateOf<JSONObject?>(null) }
    val cs = MaterialTheme.colorScheme
    PullToRefreshBox(false, { rev++ }) {
        LazyColumn(Modifier.fillMaxSize(), state = state, contentPadding = PaddingValues(bottom = 32.dp)) {
            item {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    fun n(k: String) = stats?.get(k)?.let { fmtK(it) } ?: "…"
                    StatRow(
                        Triple(n("visible"), "hiện ở Khám phá", cs.primary), Triple(n("novels"), "tổng bản ghi", null),
                        Triple(n("completed"), "hoàn thành", null), Triple(n("metaPending"), "chờ dịch tên", if ((stats?.get("metaPending") ?: 0) > 0) cs.live else null),
                    )
                    StatRow(
                        Triple(n("done"), "chương đã dịch", cs.primary), Triple(n("chapters"), "mục lục lưu", null),
                        Triple(n("doneToday"), "dịch hôm nay", null), Triple(n("failed"), "chương lỗi", if ((stats?.get("failed") ?: 0) > 0) cs.error else null),
                    )
                    TextField(
                        input, { input = it }, Modifier.fillMaxWidth().padding(top = 8.dp), singleLine = true,
                        placeholder = { Text("Tìm truyện (tên Việt/Trung, tác giả)…") },
                        leadingIcon = { Icon(Icons.Rounded.Search, null) },
                        trailingIcon = { if (input.isNotEmpty()) IconButton({ input = ""; q = "" }) { Icon(Icons.Rounded.Close, "Xoá") } },
                        shape = RoundedCornerShape(14.dp),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = cs.onSurface.copy(.06f), unfocusedContainerColor = cs.onSurface.copy(.06f),
                            focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                        ),
                    )
                    Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Tất cả" to counts?.first, "Hiển thị" to counts?.second, "Đã ẩn" to counts?.third).forEachIndexed { i, (label, c) ->
                            FilterChip(filter == i, { filter = i }, { Text(if (c == null) label else "$label (${fmtK(c)})") })
                        }
                    }
                }
            }
            if (pg.items.isEmpty()) item { if (pg.loading) Spin() else Empty(pg.error ?: "Không có truyện nào khớp.") }
            itemsIndexed(pg.items, key = { _, n -> n.getInt("id") }) { _, n ->
                val id = n.getInt("id")
                val hidden = n.optBoolean("hidden")
                val title = n.novelTitle(id)
                var menu by remember { mutableStateOf(false) }
                Row(
                    Modifier.fillMaxWidth().bounceClick { Nav.push(Screen.AdminNovel(id)) }.padding(start = 16.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Cover(n.s("cover_url"), 42.dp, 63.dp, Modifier.alpha(if (hidden) .5f else 1f), label = title, flat = true)
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis, color = if (hidden) cs.onSurfaceVariant else cs.onSurface)
                        Text(
                            listOfNotNull("${n.optInt("chapter_count_translated")}/${n.optInt("chapter_count_source")} chương", n.genreList().take(3).joinToString(", ").ifEmpty { null }, if (hidden) "đã ẩn" else null).joinToString(" · "),
                            style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Box {
                        IconButton({ menu = true }) { Icon(Icons.Rounded.MoreVert, "Tác vụ") }
                        DropdownMenu(menu, { menu = false }) {
                            DropdownMenuItem({ Text(if (hidden) "Hiện lại" else "Ẩn khỏi Khám phá") }, {
                                menu = false
                                act = Act(if (hidden) "Hiện lại truyện?" else "Ẩn truyện?", "\"$title\" ${if (hidden) "sẽ hiện lại ở Khám phá" else "sẽ biến khỏi Khám phá/tìm kiếm của mọi người"}.", if (hidden) "Hiện lại" else "Ẩn") {
                                    AdminApi.setHidden(id, !hidden); if (hidden) "Đã hiện lại" else "Đã ẩn"
                                }
                            })
                            DropdownMenuItem({ Text("Sửa") }, { menu = false; edit = n })
                            DropdownMenuItem({ Text("Xoá vĩnh viễn", color = cs.error) }, {
                                menu = false
                                act = Act("Xoá vĩnh viễn?", "\"$title\" cùng TOÀN BỘ chương đã dịch, glossary, tiến độ đọc sẽ bị xoá — không hoàn tác được.\n\nNếu chỉ muốn giấu khỏi Khám phá, hãy dùng Ẩn.", "Xoá", true) {
                                    AdminApi.deleteNovel(id); "Đã xoá \"$title\""
                                }
                            })
                        }
                    }
                }
                HorizontalDivider(Modifier.padding(start = 70.dp, end = 16.dp), color = cs.outlineVariant.copy(.5f))
            }
            if (pg.loading && pg.items.isNotEmpty()) item { Spin() }
        }
    }
    ConfirmDialog(act, { act = null }) { rev++ }
    edit?.let { n -> EditNovelDialog(n, { edit = null }) { rev++ } }
}

@Composable
private fun EditNovelDialog(n: JSONObject, close: () -> Unit, saved: () -> Unit) {
    var title by remember { mutableStateOf(n.s("title_vi").orEmpty()) }
    var author by remember { mutableStateOf(n.s("author_vi").orEmpty()) }
    var status by remember { mutableStateOf(n.s("status") ?: "ongoing") }
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    AlertDialog(
        close, title = { Text("Sửa truyện") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(title, { title = it }, Modifier.fillMaxWidth(), label = { Text("Tên tiếng Việt") })
                OutlinedTextField(author, { author = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Tác giả (Việt)") })
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("ongoing" to "Đang ra", "completed" to "Hoàn thành", "hiatus" to "Tạm ngưng").forEach { (v, l) ->
                        FilterChip(status == v, { status = v }, { Text(l) })
                    }
                }
            }
        },
        dismissButton = { TextButton(close) { Text("Huỷ") } },
        confirmButton = {
            TextButton({
                close()
                scope.launch {
                    runCatching { AdminApi.updateNovel(n.getInt("id"), title.trim(), author.trim(), status) }
                        .onSuccess { toast(ctx, "Đã lưu") }.onFailure { toast(ctx, friendly(it)) }
                    saved()
                }
            }) { Text("Lưu") }
        },
    )
}

/** Quản trị 1 truyện: thông tin + từng chương kèm model/token/thời điểm dịch. */
@Composable
fun AdminNovelScreen(id: Int) {
    val novel = load("admin.novel", id) { AdminApi.novel(id) }
    var rev by remember { mutableIntStateOf(0) }
    val pg = remember(id, rev) { Pager(60) { off -> AdminApi.chapterPage(id, off, 60) } }
    LaunchedEffect(pg) { pg.more(this) }
    val state = rememberLazyListState()
    LoadMoreOnScroll(state, pg)
    var act by remember { mutableStateOf<Act?>(null) }
    var askRange by remember { mutableStateOf(false) }
    val cs = MaterialTheme.colorScheme
    val n = novel.res?.getOrNull()
    Column(Modifier.fillMaxSize()) {
        PageHeader("Quản trị truyện", n.novelTitle(id), "管", back = true) {
            IconButton({ askRange = true }, enabled = n != null) { Icon(Icons.Rounded.Translate, "Yêu cầu dịch") }
            IconButton({
                act = Act("Dịch lại tên + mô tả?", "Xếp job dịch lại tên, mô tả, thể loại theo prompt hiện tại (đè lên tên cũ).", "Dịch lại") { AdminApi.requestMeta(id); "Đã xếp dịch lại tên — vài giây nữa kéo làm mới" }
            }) { Icon(Icons.Rounded.Title, "Dịch lại tên + mô tả") }
            IconButton({
                act = Act("Huỷ hàng đợi truyện này?", "Huỷ toàn bộ chương ĐANG CHỜ dịch của truyện (chương đang dịch chạy nốt).", "Huỷ hàng đợi", true) { AdminApi.cancelNovelQueue(id); "Đã huỷ các chương đang chờ dịch" }
            }) { Icon(Icons.Rounded.PlaylistRemove, "Huỷ hàng đợi") }
        }
        PullToRefreshBox(false, { rev++; novel.reload() }, Modifier.weight(1f)) {
            LazyColumn(Modifier.fillMaxSize(), state = state, contentPadding = PaddingValues(bottom = 32.dp)) {
                if (n != null) item {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                        listOfNotNull(
                            "Nguồn" to (n.optJSONObject("sources")?.s("name") ?: "—"), "Trạng thái" to Api.statusLabel(n.s("status").orEmpty()),
                            n.genreList().joinToString(", ").ifEmpty { null }?.let { "Thể loại" to it },
                            "Chương mới" to (n.s("last_chapter_at")?.let { "${ago(it)} trước" } ?: "—"),
                            "Đã dịch" to "${n.optInt("chapter_count_translated")}/${n.optInt("chapter_count_source")} chương",
                        ).forEach { (k, v) ->
                            Row(Modifier.padding(vertical = 3.dp)) {
                                Text(k, Modifier.width(92.dp), style = MaterialTheme.typography.labelMedium)
                                Text(v, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                    HorizontalDivider(Modifier.padding(top = 8.dp), color = cs.outlineVariant.copy(.5f))
                }
                if (pg.items.isEmpty() && !pg.loading) item { Empty(pg.error ?: "Chưa có chương nào.") }
                itemsIndexed(pg.items, key = { _, c -> c.getInt("chapter_index") }) { _, c ->
                    val (label, color) = when (c.optString("translation_status")) {
                        "done" -> "Đã dịch" to cs.primary
                        "translating" -> "Đang dịch" to cs.live
                        "failed" -> "Lỗi" to cs.error
                        "queued" -> "Trong hàng đợi" to cs.secondary
                        else -> "Chưa dịch" to cs.outline
                    }
                    val tok = c.optInt("prompt_tokens") + c.optInt("completion_tokens")
                    val info = listOfNotNull(label, c.s("model_used"), if (tok > 0) "${fmtK(tok)} token" else null, c.s("translated_at")?.let { "${ago(it)} trước" }).joinToString(" · ")
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(9.dp).background(color, CircleShape))
                        Column(Modifier.padding(start = 12.dp)) {
                            Text("${c.optInt("chapter_index")}. ${c.s("title_vi") ?: "(chưa có tên)"}", style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(info, style = MaterialTheme.typography.labelSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    HorizontalDivider(Modifier.padding(start = 37.dp, end = 16.dp), color = cs.outlineVariant.copy(.4f))
                }
                if (pg.loading) item { Spin() }
            }
        }
    }
    ConfirmDialog(act, { act = null }) { rev++; novel.reload() }
    if (askRange && n != null) RangeDialog(n.optInt("chapter_count_source"), n.optInt("chapter_count_translated"), { askRange = false }) { from, to ->
        askRange = false
        act = Act("Yêu cầu dịch chương $from–$to?", "Xếp job dịch các chương CHƯA dịch hoặc lỗi trong khoảng $from–$to của \"${n.novelTitle(id)}\" (chương đã dịch giữ nguyên).", "Dịch") {
            val c = AdminApi.requestRange(id, from, to); if (c > 0) "Đã xếp $c chương vào hàng đợi" else "Không có chương nào cần xếp (đã dịch/đang chờ hết)"
        }
    }
}

/** "Yêu cầu dịch" theo khoảng chương: từ A đến B (1 ≤ A ≤ B ≤ số chương nguồn). Bấm Tiếp tục → hộp xác nhận. */
@Composable
private fun RangeDialog(source: Int, translated: Int, close: () -> Unit, next: (Int, Int) -> Unit) {
    var from by remember { mutableStateOf((translated + 1).coerceAtMost(source.coerceAtLeast(1)).toString()) }
    var to by remember { mutableStateOf("") }
    val a = from.toIntOrNull()
    val b = to.toIntOrNull()
    val ok = a != null && b != null && a >= 1 && a <= b && (source == 0 || b <= source)
    val num = KeyboardOptions(keyboardType = KeyboardType.Number)
    AlertDialog(
        close, title = { Text("Yêu cầu dịch") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Đã dịch $translated/$source chương. Chọn khoảng chương cần dịch:", style = MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(from, { from = it.filter(Char::isDigit).take(6) }, Modifier.weight(1f), label = { Text("Từ chương") }, singleLine = true, keyboardOptions = num)
                    OutlinedTextField(to, { to = it.filter(Char::isDigit).take(6) }, Modifier.weight(1f), label = { Text("Đến chương") }, singleLine = true, keyboardOptions = num)
                }
                if (b != null && source > 0 && b > source) Text("Truyện chỉ có $source chương.", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
                else if (a != null && b != null && a > b) Text("Chương đầu phải nhỏ hơn hoặc bằng chương cuối.", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(close) { Text("Huỷ") } },
        confirmButton = { TextButton({ next(a!!, b!!) }, enabled = ok) { Text("Tiếp tục") } },
    )
}
