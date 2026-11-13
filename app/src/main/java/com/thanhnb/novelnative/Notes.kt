package com.thanhnb.novelnative

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Một đoạn đã lưu: giữ nguyên văn đoạn để vẫn hiện đúng khi bản dịch chương đổi. */
data class SavedNote(val novelId: Int, val title: String, val chapter: Int, val idx: Int, val text: String, val note: String, val at: Long)

// shortcut: chỉ lưu trên máy, chưa đồng bộ; cần đồng bộ thì thêm bảng server có RLS theo user
object Notes {
    const val MAX_NOTE = 500
    const val MAX_ITEMS = 2000
    private val items = mutableStateListOf<SavedNote>()
    private var loaded = false

    private fun file(ctx: Context) = File(ctx.filesDir, "notes.json")

    /** Danh sách là state của Compose nên màn nào đọc nó đều tự vẽ lại khi lưu/xoá. */
    fun all(ctx: Context): List<SavedNote> {
        if (!loaded) {
            loaded = true
            runCatching {
                val a = JSONArray(file(ctx).takeIf { it.exists() }?.readText() ?: "[]")
                for (i in 0 until a.length()) a.getJSONObject(i).let {
                    items += SavedNote(it.getInt("n"), it.optString("t"), it.getInt("c"), it.getInt("i"), it.optString("x"), it.optString("m"), it.optLong("at"))
                }
            }
        }
        return items
    }

    fun find(ctx: Context, novelId: Int, chapter: Int, idx: Int) = all(ctx).firstOrNull { it.novelId == novelId && it.chapter == chapter && it.idx == idx }

    /** Thêm hoặc cập nhật theo (truyện, chương, đoạn). Trả về lời báo lỗi, null = xong. */
    fun put(ctx: Context, e: SavedNote): String? {
        val list = all(ctx)
        val at = list.indexOfFirst { it.novelId == e.novelId && it.chapter == e.chapter && it.idx == e.idx }
        if (at < 0 && list.size >= MAX_ITEMS) return "Đã đủ $MAX_ITEMS mục, hãy xoá bớt ở màn Ghi chú"
        val prev = items.toList()
        val n = e.copy(note = e.note.take(MAX_NOTE))
        if (at < 0) items.add(0, n) else items[at] = n
        return save(ctx)?.also { items.clear(); items.addAll(prev) }
    }

    fun remove(ctx: Context, e: SavedNote) {
        all(ctx)
        val prev = items.toList()
        items.removeAll { it.novelId == e.novelId && it.chapter == e.chapter && it.idx == e.idx }
        save(ctx)?.also { items.clear(); items.addAll(prev) }
    }

    // ghi file tạm rồi đổi tên: mất điện giữa chừng vẫn còn bản cũ nguyên vẹn
    private fun save(ctx: Context): String? = runCatching {
        val a = JSONArray()
        items.forEach { a.put(JSONObject().put("n", it.novelId).put("t", it.title).put("c", it.chapter).put("i", it.idx).put("x", it.text).put("m", it.note).put("at", it.at)) }
        val f = file(ctx)
        val tmp = File(f.parentFile, "notes.json.tmp")
        tmp.writeText(a.toString())
        check(tmp.renameTo(f))
    }.exceptionOrNull()?.let { "Không ghi được ghi chú vào máy" }
}

/** Menu giữ-lâu một đoạn: sửa bản dịch · lưu/bỏ lưu trích dẫn · ghi chú · chia sẻ ảnh. [onEdit] mở form sửa bản dịch sẵn có. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParaSheet(novelId: Int, title: String, chapter: Int, idx: Int, text: String, onEdit: () -> Unit, close: () -> Unit, toast: (String) -> Unit) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val cur = Notes.find(ctx, novelId, chapter, idx)
    var noting by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf(cur?.note.orEmpty()) }
    fun save(note: String, msg: String) {
        val err = Notes.put(ctx, SavedNote(novelId, title, chapter, idx, text, note, cur?.at ?: System.currentTimeMillis()))
        toast(err ?: msg); close()
    }
    ModalBottomSheet(close, containerColor = cs.card) {
        Column(Modifier.imePadding().padding(horizontal = 20.dp).padding(bottom = 16.dp)) {
            Text(text, maxLines = 3, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, modifier = Modifier.padding(bottom = 8.dp))
            if (noting) {
                OutlinedTextField(
                    draft, { if (it.length <= Notes.MAX_NOTE) draft = it }, Modifier.fillMaxWidth(), placeholder = { Text("Ghi chú của bạn") },
                    supportingText = { Text("${draft.length}/${Notes.MAX_NOTE}") }, maxLines = 5, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
                )
                Button({ save(draft.trim(), "Đã lưu ghi chú") }, Modifier.align(Alignment.End).heightIn(min = 48.dp)) { Text("Lưu ghi chú") }
            } else {
                NoteRow(Icons.Rounded.EditNote, "Sửa bản dịch") { onEdit() }
                if (cur == null) NoteRow(Icons.Rounded.BookmarkAdd, "Lưu trích dẫn") { save("", "Đã lưu trích dẫn") }
                else NoteRow(Icons.Rounded.BookmarkRemove, "Bỏ lưu trích dẫn") { Notes.remove(ctx, cur); toast("Đã bỏ lưu"); close() }
                NoteRow(Icons.Rounded.StickyNote2, if (cur?.note.isNullOrBlank()) "Thêm ghi chú" else "Sửa ghi chú") { noting = true }
                NoteRow(Icons.Rounded.Share, "Chia sẻ ảnh") { if (!shareQuote(ctx, text, title, chapter)) toast("Không tạo được ảnh"); close() }
            }
        }
    }
}

@Composable
private fun NoteRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, click: () -> Unit) =
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(onClick = click), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.padding(end = 16.dp), MaterialTheme.colorScheme.primary)
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }

/** Màn danh sách: nhóm theo truyện, chạm để mở chương, nút thùng rác xoá kèm "Hoàn tác". */
@Composable
fun NotesScreen() {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    val snack = remember { SnackbarHostState() }
    val groups = Notes.all(ctx).groupBy { it.novelId }.values.sortedByDescending { g -> g.maxOf { it.at } }
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            PageHeader("Trích dẫn và ghi chú của bạn", "Ghi chú", "记", back = true)
            if (groups.isEmpty()) Empty("Chưa có gì được lưu.\nGiữ lâu một đoạn khi đọc để lưu.")
            else LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 0.dp, 16.dp, 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                groups.forEach { g ->
                    item(key = "h${g[0].novelId}") { Eyebrow(g[0].title, Modifier.padding(start = 4.dp, top = 8.dp)) }
                    items(g.sortedBy { it.chapter * 10000 + it.idx }, key = { "${it.novelId}:${it.chapter}:${it.idx}" }) { n ->
                        PaperCard(onClick = { Nav.push(Screen.Read(n.novelId, n.title, n.chapter)) }) {
                            Row(Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(n.text, maxLines = 4, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium, fontStyle = FontStyle.Italic)
                                    if (n.note.isNotBlank()) Text(n.note, color = cs.primary, style = MaterialTheme.typography.bodyMedium)
                                    Text("Chương ${n.chapter}", style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
                                }
                                IconButton({
                                    Notes.remove(ctx, n)
                                    scope.launch {
                                        snack.currentSnackbarData?.dismiss()
                                        if (snack.showSnackbar("Đã xoá", "Hoàn tác", duration = SnackbarDuration.Short) == SnackbarResult.ActionPerformed) Notes.put(ctx, n)
                                    }
                                }, Modifier.size(48.dp)) { Icon(Icons.Rounded.DeleteOutline, "Xoá ghi chú", tint = cs.onSurfaceVariant) }
                            }
                        }
                    }
                }
            }
        }
        SnackbarHost(snack, Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
    }
}

/** Vẽ trích dẫn ra ảnh PNG 1080 rộng rồi mở share sheet; true = đã mở được. */
fun shareQuote(ctx: Context, text: String, novelTitle: String, chapter: Int): Boolean = runCatching {
    val w = 1080
    val pad = 96
    val serif = ResourcesCompat.getFont(ctx, R.font.noto_serif_bold) ?: Typeface.SERIF
    val ink = 0xFF2B2118.toInt()
    val accent = 0xFF9C3B2E.toInt()
    val body = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = serif; textSize = 46f; color = ink }
    val cut = if (text.length > 520) text.take(520).trimEnd() + "…" else text // đoạn quá dài thì cắt cho ảnh không dài lê thê
    val lay = StaticLayout.Builder.obtain(cut, 0, cut.length, body, w - pad * 2).setLineSpacing(0f, 1.45f).setAlignment(Layout.Alignment.ALIGN_NORMAL).build()
    val small = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = serif; textSize = 34f; color = ink }
    val top = 330 // chừa chỗ cho dấu ngoặc kép lớn
    val h = top + lay.height + 260
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    c.drawColor(0xFFF7F0E1.toInt())
    val mark = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = serif; textSize = 300f; color = accent }
    c.drawText("“", pad.toFloat(), 300f, mark)
    c.save(); c.translate(pad.toFloat(), top.toFloat()); lay.draw(c); c.restore()
    val y = (top + lay.height + 90).toFloat()
    c.drawRect(pad.toFloat(), y, pad + 90f, y + 4f, Paint().apply { color = accent })
    c.drawText(TextUtils.ellipsize(novelTitle, small, (w - pad * 2).toFloat(), TextUtils.TruncateAt.END).toString(), pad.toFloat(), y + 60f, small)
    small.textSize = 28f; small.alpha = 150
    c.drawText("Chương $chapter · Gác Truyện", pad.toFloat(), y + 108f, small)
    // dùng thư mục updates/ đã khai trong update_paths.xml nên khỏi sửa manifest
    val dir = File(ctx.cacheDir, "updates").apply { mkdirs() }
    val f = File(dir, "quote.png")
    f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.updates", f)
    val send = Intent(Intent.ACTION_SEND).setType("image/png").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    ctx.startActivity(Intent.createChooser(send, "Chia sẻ trích dẫn").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}.isSuccess
