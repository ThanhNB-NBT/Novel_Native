package com.thanhnb.novelnative

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Bản offline: chương đã dịch lưu SQLite. Api.novel/toc/chapter tự rơi về đây khi mất mạng,
 * và chương đọc online của truyện đã tải được ghi đè xuống để bản offline không cũ.
 */
object Offline {
    private lateinit var db: SQLiteDatabase
    private lateinit var file: File
    private lateinit var app: Context
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Đổi mỗi khi dữ liệu offline đổi — làm key cho load() để màn hình tự nạp lại. */
    var version by mutableIntStateOf(0)
        private set

    /** Truyện đang tải: novelId → (đã tải, cần tải). Sống ngoài màn hình nên rời trang vẫn tải tiếp. */
    var running by mutableStateOf(mapOf<Int, Pair<Int, Int>>())
        private set

    fun init(c: Context) {
        if (::db.isInitialized) return
        app = c.applicationContext
        file = app.getDatabasePath("offline.db")
        db = object : SQLiteOpenHelper(app, "offline.db", null, 1) {
            override fun onCreate(d: SQLiteDatabase) {
                // json = nguyên dòng novels từ server, để Api.novel() dựng lại Novel khi mất mạng
                d.execSQL("create table novels(novel_id integer primary key, json text not null, downloaded_at integer not null)")
                d.execSQL(
                    "create table chapters(novel_id integer not null, chapter_index integer not null, title_vi text, " +
                        "content_vi text, updated_at text, primary key(novel_id, chapter_index))",
                )
            }
            override fun onUpgrade(d: SQLiteDatabase, old: Int, new: Int) = Unit
        }.writableDatabase
    }

    private suspend fun <T> io(block: () -> T) = withContext(Dispatchers.IO) { block() }

    suspend fun has(novelId: Int) = io {
        db.rawQuery("select 1 from novels where novel_id = ?", arrayOf("$novelId")).use { it.moveToFirst() }
    }

    suspend fun count(novelId: Int) = io {
        db.rawQuery("select count(*) from chapters where novel_id = ?", arrayOf("$novelId")).use { it.moveToFirst(); it.getInt(0) }
    }

    suspend fun novelJson(novelId: Int): JSONObject? = io {
        db.rawQuery("select json from novels where novel_id = ?", arrayOf("$novelId")).use { if (it.moveToFirst()) JSONObject(it.getString(0)) else null }
    }

    /** Một chương, cùng dạng cột với REST để Api.chapter dùng chung một đường parse. */
    suspend fun chapter(novelId: Int, index: Int): JSONObject? = io {
        db.rawQuery("select title_vi, content_vi from chapters where novel_id = ? and chapter_index = ?", arrayOf("$novelId", "$index")).use {
            if (!it.moveToFirst()) null
            else JSONObject().put("chapter_index", index).put("title_vi", it.getString(0)).put("content_vi", it.getString(1)).put("translation_status", "done")
        }
    }

    suspend fun toc(novelId: Int): List<ChapterRef> = io {
        db.rawQuery("select chapter_index, title_vi from chapters where novel_id = ? order by chapter_index", arrayOf("$novelId")).use { c ->
            buildList { while (c.moveToNext()) add(ChapterRef(c.getInt(0), c.getString(1)?.trimStart('#', ' ')?.ifBlank { null } ?: "Chương ${c.getInt(0)}", true)) }
        }
    }

    /** Ghi các dòng chương (chapter_index, title_vi, content_vi, updated_at) — đè bản cũ. */
    suspend fun save(novelId: Int, rows: List<JSONObject>) = io {
        db.beginTransaction()
        try {
            rows.forEach { r ->
                db.insertWithOnConflict("chapters", null, ContentValues().apply {
                    put("novel_id", novelId)
                    put("chapter_index", r.getInt("chapter_index"))
                    put("title_vi", r.optString("title_vi"))
                    put("content_vi", r.optString("content_vi"))
                    put("updated_at", r.optString("updated_at"))
                }, SQLiteDatabase.CONFLICT_REPLACE)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    class Item(val novel: Novel, val chapters: Int)

    suspend fun list(): List<Item> = io {
        db.rawQuery(
            "select n.json, (select count(*) from chapters c where c.novel_id = n.novel_id) from novels n order by n.downloaded_at desc", null,
        ).use { c -> buildList { while (c.moveToNext()) add(Item(Api.novel(JSONObject(c.getString(0))), c.getInt(1))) } }
    }

    fun sizeMb() = file.length() / (1024f * 1024f)

    suspend fun delete(novelId: Int) {
        io {
            db.delete("chapters", "novel_id = ?", arrayOf("$novelId"))
            db.delete("novels", "novel_id = ?", arrayOf("$novelId"))
        }
        version++
    }

    suspend fun deleteAll() {
        io { db.delete("chapters", null, null); db.delete("novels", null, null); db.execSQL("vacuum") }
        version++
    }

    private suspend fun rows(path: String) = JSONArray(Api.raw("GET", "rest/v1/$path")).let { a -> List(a.length()) { a.getJSONObject(it) } }

    /**
     * Tải/cập nhật mọi chương ĐÃ DỊCH. Hỏi danh sách (index, updated_at) trước — vài chục KB — rồi chỉ kéo thân
     * chương còn thiếu hoặc server đã đổi (dịch lại, sửa, vá thuật ngữ). Bấm lại = đồng bộ, không tải lại cả truyện.
     */
    fun start(novelId: Int) {
        if (novelId in running) return
        running = running + (novelId to (0 to 0))
        scope.launch {
            val r = runCatching {
                val novel = rows("novels?select=*,sources(name,label)&id=eq.$novelId").first()
                // ghi truyện trước: tải dở (mất mạng, tắt app) vẫn thấy phần đã có và bấm tiếp được
                io {
                    db.insertWithOnConflict("novels", null, ContentValues().apply {
                        put("novel_id", novelId); put("json", novel.toString()); put("downloaded_at", System.currentTimeMillis())
                    }, SQLiteDatabase.CONFLICT_REPLACE)
                }
                val server = mutableListOf<JSONObject>()
                while (true) {
                    val page = rows(
                        "chapters?select=chapter_index,updated_at&novel_id=eq.$novelId&translation_status=eq.done" +
                            "&order=chapter_index.asc&offset=${server.size}&limit=1000",
                    )
                    server += page
                    if (page.size < 1000) break
                }
                val local = io {
                    db.rawQuery("select chapter_index, updated_at from chapters where novel_id = ?", arrayOf("$novelId")).use { c ->
                        buildMap { while (c.moveToNext()) put(c.getInt(0), c.getString(1)) }
                    }
                }
                val need = server.filter { local[it.getInt("chapter_index")] != it.optString("updated_at") }.map { it.getInt("chapter_index") }
                need.chunked(100).forEachIndexed { i, part ->
                    running = running + (novelId to (i * 100 to need.size))
                    save(novelId, rows("chapters?select=chapter_index,title_vi,content_vi,updated_at&novel_id=eq.$novelId&chapter_index=in.(${part.joinToString(",")})"))
                }
                server.size to need.size
            }
            withContext(Dispatchers.Main) {
                running = running - novelId
                version++
                val text = r.fold(
                    { (all, got) -> if (all == 0) "Truyện chưa có chương nào dịch xong" else if (got == 0) "Bản offline đã mới nhất · $all chương" else "Đã tải $got chương · có $all chương offline" },
                    { "Tải offline dừng: ${friendly(it)}" },
                )
                Toast.makeText(app, text, Toast.LENGTH_SHORT).show()
            }
        }
    }
}

/** Thẻ trên trang chi tiết: tải / cập nhật / xoá bản offline của một truyện. */
@Composable
fun OfflineCard(n: Novel) {
    val cs = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    val have = load("offcount", n.id, Offline.version) { if (Offline.has(n.id)) Offline.count(n.id) else null }.res?.getOrNull()
    val prog = Offline.running[n.id]
    var confirm by remember { mutableStateOf(false) }
    PaperCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), onClick = { if (prog == null) Offline.start(n.id) }) {
        Row(Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(if (have != null) Icons.Rounded.DownloadDone else Icons.Rounded.Download, null, tint = cs.primary)
            Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                Text("Đọc offline", style = MaterialTheme.typography.titleMedium)
                Text(
                    when {
                        prog != null -> if (prog.second == 0) "Đang kiểm tra…" else "Đang tải ${prog.first}/${prog.second} chương…"
                        have != null -> "Đã tải $have chương · chạm để cập nhật"
                        else -> "Tải ${n.chaptersTranslated} chương đã dịch về máy"
                    },
                    style = MaterialTheme.typography.labelMedium,
                )
                if (prog != null) LinearProgressIndicator(
                    { if (prog.second == 0) 0f else prog.first.toFloat() / prog.second },
                    Modifier.fillMaxWidth().padding(top = 6.dp).height(3.dp).clip(RoundedCornerShape(2.dp)),
                    gapSize = 0.dp, drawStopIndicator = {},
                )
            }
            if (have != null && prog == null) IconButton({ confirm = true }) { Icon(Icons.Rounded.DeleteOutline, "Xoá bản offline", tint = cs.error) }
            else Spacer(Modifier.width(12.dp))
        }
    }
    if (confirm) AlertDialog(
        { confirm = false },
        confirmButton = { TextButton({ confirm = false; scope.launch { Offline.delete(n.id) } }) { Text("Xoá") } },
        dismissButton = { TextButton({ confirm = false }) { Text("Huỷ") } },
        title = { Text("Xoá bản offline?") },
        text = { Text("Xoá các chương đã tải của truyện này khỏi máy.") },
    )
}

/** Quản lý truyện đã tải: dung lượng, dọn tất cả, xoá từng truyện. */
@Composable
fun OfflineScreen() {
    val l = load("offline", Offline.version) { Offline.list() }
    val scope = rememberCoroutineScope()
    var confirm by remember { mutableStateOf(false) }
    val cs = MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize()) {
        PageHeader("Đọc không cần mạng", "Bản offline", "存", back = true)
        Loaded(l) { list ->
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
                if (list.isEmpty()) return@LazyColumn item { Empty("Chưa tải truyện nào về máy.\nMở một truyện → Đọc offline.") }
                item {
                    PaperCard(Modifier.fillMaxWidth().padding(16.dp, 4.dp, 16.dp, 8.dp)) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Storage, null, tint = cs.primary)
                            Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                                Text("Dung lượng đã tải", style = MaterialTheme.typography.labelMedium)
                                Text("%.1f MB · %d truyện".format(Offline.sizeMb(), list.size), style = MaterialTheme.typography.titleMedium)
                            }
                            OutlinedButton({ confirm = true }, colors = ButtonDefaults.outlinedButtonColors(contentColor = cs.error)) { Text("Dọn dẹp") }
                        }
                    }
                }
                items(list, key = { it.novel.id }) { it ->
                    val n = it.novel
                    Row(
                        Modifier.fillMaxWidth().clickable { Nav.push(Screen.Detail(n.id)) }.padding(start = 16.dp, top = 10.dp, bottom = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Cover(n.cover, 48.dp, 65.dp, label = n.title)
                        Column(Modifier.weight(1f).padding(start = 14.dp)) {
                            Text(n.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            val p = Offline.running[n.id]
                            Text(if (p != null) "Đang tải ${p.first}/${p.second}…" else "${it.chapters} chương đã tải", style = monoStyle(12, cs.primary))
                        }
                        IconButton({ Offline.start(n.id) }) { Icon(Icons.Rounded.Sync, "Cập nhật", tint = cs.onSurfaceVariant) }
                        IconButton({ scope.launch { Offline.delete(n.id) } }) { Icon(Icons.Rounded.DeleteOutline, "Xoá", tint = cs.error) }
                    }
                }
            }
        }
    }
    if (confirm) AlertDialog(
        { confirm = false },
        confirmButton = { TextButton({ confirm = false; scope.launch { Offline.deleteAll() } }) { Text("Xoá tất cả") } },
        dismissButton = { TextButton({ confirm = false }) { Text("Huỷ") } },
        title = { Text("Dọn dẹp bộ nhớ offline?") },
        text = { Text("Xoá toàn bộ truyện đã tải về máy để giải phóng dung lượng.") },
    )
}
