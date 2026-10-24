package com.thanhnb.novelnative

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.Normalizer
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

data class Novel(
    val id: Int,
    val title: String,
    val author: String,
    val cover: String?,
    val status: String,
    val chaptersSource: Int,
    val chaptersTranslated: Int,
    val genres: List<String>,
    val source: String,
    val description: String? = null,
    val curChapter: Int? = null, // chỉ có ở Tủ truyện
)

/** Kết quả gợi ý AI: [thay] = cụm AI xét, [dung] = bản dịch hiện tại đã đúng, [items] = (cách dịch, ý). */
class Suggest(val thay: String, val dung: Boolean, val items: List<Pair<String, String>>)
/** Thuật ngữ của truyện (hoặc toàn cục); [approved] = false là gợi ý LLM chờ duyệt. */
class Term(
    val id: Long, val zh: String?, val wrong: String?, val correct: String, val type: String,
    val scope: String?, val approved: Boolean, val narrator: String?,
)
/** Job vá chương cũ gần nhất: [status] pending/running/done/failed, [result] "N/M chương". */
class PatchJob(val status: String, val result: String?)
data class ChapterRef(val index: Int, val title: String, val done: Boolean)
data class Chapter(val index: Int, val title: String, val status: String, val paragraphs: List<String>)
data class Bookmark(val id: Int, val chapter: Int, val percent: Double, val excerpt: String?)
data class Notif(val novelId: Int, val title: String, val cover: String?, val chapters: List<Int>, val latestAt: String)
data class Filter(
    val q: String = "",
    val minChapters: Int = 0,
    val genre: String? = null,
    val status: String? = null,
    val sources: Set<Int> = emptySet(),
)

enum class Section(val title: String) {
    Latest("Mới cập nhật"), Featured("Nổi bật"), Recommended("Đề cử"), Completed("Đã hoàn thành")
}

class ApiError(val code: Int, body: String) : Exception(
    runCatching { JSONObject(body).let { it.optString("msg").ifEmpty { it.optString("message") }.ifEmpty { it.optString("error_description") } } }
        .getOrNull().orEmpty().ifEmpty { "HTTP $code" },
)

/** Lỗi đọc được cho người dùng (thay chuỗi exception thô). */
fun friendly(e: Throwable?): String = when {
    e is ApiError && e.message.orEmpty().contains("Invalid login", true) -> "Sai email hoặc mật khẩu"
    e is ApiError -> e.message.orEmpty()
    e is java.io.IOException -> "Không kết nối được máy chủ — kiểm tra mạng"
    else -> e?.message ?: "Lỗi không rõ"
}

/** SharedPreferences + vài giá trị UI cần quan sát được (Compose state). */
object Prefs {
    lateinit var sp: SharedPreferences
    var themeMode by mutableIntStateOf(0) // 0 hệ thống · 1 sáng · 2 tối
    var notifSeenAt by mutableStateOf("")
    var accent by mutableIntStateOf(0) // chỉ số trong accents (Theme.kt)
    var paper by mutableIntStateOf(0)

    fun init(c: Context) {
        sp = c.getSharedPreferences("app", Context.MODE_PRIVATE)
        themeMode = sp.getInt("theme_mode", 0)
        accent = sp.getInt("accent", 0)
        paper = sp.getInt("paper", 0)
        notifSeenAt = sp.getString("notify_seen_at", null) ?: Instant.now().toString().also { setSeen(it) }
    }

    fun setTheme(m: Int) { themeMode = m; sp.edit().putInt("theme_mode", m).apply() }
    fun pickAccent(i: Int) { accent = i; sp.edit().putInt("accent", i).apply() }
    fun pickPaper(i: Int) { paper = i; sp.edit().putInt("paper", i).apply() }
    fun setSeen(at: String) { notifSeenAt = at; sp.edit().putString("notify_seen_at", at).apply() }

    // Cùng khoá với app Flutter (rp_<novel>_<chương>) cho dễ đối chiếu.
    fun percent(novel: Int, ch: Int) = sp.getFloat("rp_${novel}_$ch", 0f)
    fun setPercent(novel: Int, ch: Int, p: Float) = sp.edit().putFloat("rp_${novel}_$ch", p.coerceIn(0f, 1f)).apply()

    var autoAhead: Boolean
        get() = sp.getBoolean("auto_translate_ahead", true)
        set(v) = sp.edit().putBoolean("auto_translate_ahead", v).apply()

    var dismissed: Set<String>
        get() = sp.getStringSet("notify_dismissed", emptySet())!!
        set(v) = sp.edit().putStringSet("notify_dismissed", v).apply()
}

data class User(val id: String, val email: String)

/** profiles: tên hiển thị, avatar (emoji), chuỗi ngày đọc. */
/** Một chương trong hàng đợi dịch. status: translating | downloading | queued | source_unavailable. */
class QJob(val novelId: Int, val index: Int, val title: String?, val status: String, val novelTitle: String, val cover: String?, val done: Int, val total: Int)
class QDone(val novelId: Int, val index: Int, val novelTitle: String, val cover: String?)
/** [pendingTotal] = tổng job chờ THẬT; [active] chỉ là cửa sổ 200 job đầu hàng đợi. */
class Queue(val active: List<QJob>, val recent: List<QDone>, val pendingTotal: Int)

class Profile(val name: String?, val avatar: String?, val streak: Int, val lastRead: String?)

/** Phiên GoTrue (email + mật khẩu) qua REST trần — đủ cho app đọc, khỏi kéo supabase-kt. */
object Session {
    var user by mutableStateOf<User?>(null)
    var token: String? = null
    private var refreshToken: String? = null
    private var expiresAt = 0L
    private val lock = Mutex()

    fun load() {
        val sp = Prefs.sp
        val id = sp.getString("auth_uid", null) ?: return
        token = sp.getString("auth_access", null)
        refreshToken = sp.getString("auth_refresh", null)
        expiresAt = sp.getLong("auth_exp", 0)
        user = User(id, sp.getString("auth_email", "").orEmpty())
    }

    private fun save(j: JSONObject) {
        val u = j.getJSONObject("user")
        token = j.getString("access_token")
        refreshToken = j.getString("refresh_token")
        expiresAt = System.currentTimeMillis() / 1000 + j.optLong("expires_in", 3600)
        user = User(u.getString("id"), u.optString("email"))
        Prefs.sp.edit().putString("auth_uid", user!!.id).putString("auth_email", user!!.email)
            .putString("auth_access", token).putString("auth_refresh", refreshToken).putLong("auth_exp", expiresAt).apply()
    }

    fun signOut() {
        user = null; token = null; refreshToken = null
        Prefs.sp.edit().remove("auth_uid").remove("auth_email").remove("auth_access").remove("auth_refresh").remove("auth_exp").apply()
    }

    suspend fun signIn(email: String, password: String) = save(
        JSONObject(Api.raw("POST", "auth/v1/token?grant_type=password", JSONObject(mapOf("email" to email, "password" to password)), auth = false)),
    )

    /** Làm mới token khi sắp hết hạn (hoặc bị 401). Refresh token hỏng → đăng xuất. */
    suspend fun refresh(force: Boolean = false) = lock.withLock {
        val rt = refreshToken ?: return@withLock
        if (!force && System.currentTimeMillis() / 1000 < expiresAt - 60) return@withLock
        try {
            save(JSONObject(Api.raw("POST", "auth/v1/token?grant_type=refresh_token", JSONObject(mapOf("refresh_token" to rt)), auth = false)))
        } catch (e: ApiError) {
            if (e.code in 400..499) signOut() else throw e
        }
    }
}

/** PostgREST + RPC trần: cùng query với app Flutter (lib/data/ trong Flutter). */
object Api {
    private const val COLS = "id,title_vi,title_zh,author_vi,author_zh,cover_url,status,chapter_count_source," +
        "chapter_count_translated,genres,last_chapter_at,source_rank,source_id,sources(name,label)"
    private const val VISIBLE = "hidden=eq.false&is_canonical=eq.true"

    suspend fun raw(method: String, path: String, body: Any? = null, prefer: String? = null, auth: Boolean = true, retry: Boolean = true, headerOut: MutableMap<String, String>? = null): String =
        withContext(Dispatchers.IO) {
            if (auth) Session.refresh()
            val conn = URL("${BuildConfig.SUPABASE_URL}/$path").openConnection() as HttpURLConnection
            conn.requestMethod = method
            conn.connectTimeout = 15_000
            conn.readTimeout = 30_000
            conn.setRequestProperty("apikey", BuildConfig.SUPABASE_ANON_KEY)
            conn.setRequestProperty("Authorization", "Bearer ${(if (auth) Session.token else null) ?: BuildConfig.SUPABASE_ANON_KEY}")
            prefer?.let { conn.setRequestProperty("Prefer", it) }
            try {
                if (body != null) {
                    conn.doOutput = true
                    conn.setRequestProperty("Content-Type", "application/json")
                    conn.outputStream.use { it.write(body.toString().toByteArray()) }
                }
                val code = conn.responseCode
                if (code == 401 && auth && retry && Session.token != null) {
                    Session.refresh(force = true)
                    return@withContext raw(method, path, body, prefer, auth, retry = false)
                }
                if (code !in 200..299) throw ApiError(code, conn.errorStream?.bufferedReader()?.readText().orEmpty())
                headerOut?.put("range", conn.getHeaderField("Content-Range").orEmpty())
                conn.inputStream.bufferedReader().readText()
            } finally {
                conn.disconnect()
            }
        }

    internal suspend fun get(path: String) = JSONArray(raw("GET", "rest/v1/$path"))
    internal suspend fun rpc(name: String, vararg params: Pair<String, Any?>) = raw("POST", "rest/v1/rpc/$name", JSONObject(mapOf(*params)))
    private suspend fun insert(table: String, row: Map<String, Any?>, upsert: Boolean = false) = raw(
        "POST", "rest/v1/$table", JSONObject(row),
        prefer = if (upsert) "resolution=merge-duplicates,return=minimal" else "return=minimal",
    )
    internal suspend fun delete(path: String) = raw("DELETE", "rest/v1/$path")

    internal fun enc(s: String) = URLEncoder.encode(s, "UTF-8").replace("+", "%20")
    private fun JSONArray.objs() = List(length()) { getJSONObject(it) }
    private fun JSONObject.str(k: String) = optString(k).takeIf { !isNull(k) && it.isNotEmpty() }
    private fun uid() = Session.user?.id

    /** Bỏ dấu + hạ chữ thường, khớp `public.vn_norm()` (cột search_norm). */
    fun boDau(s: String) = Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "").replace('đ', 'd')

    /** Ảnh bìa thu nhỏ phía Storage; URL ngoài Storage giữ nguyên. Dump DB giữ host cũ → đổi host. */
    fun thumb(url: String?, width: Int = 240): String? {
        val marker = "/storage/v1/object/"
        val at = url?.indexOf(marker) ?: return null
        if (at < 0) return url
        return "${BuildConfig.SUPABASE_URL.trimEnd('/')}/storage/v1/render/image/${url.substring(at + marker.length)}" +
            "?width=$width&resize=contain&quality=70"
    }

    private fun sourceLabel(s: JSONObject?): String {
        s ?: return ""
        s.str("label")?.let { return it }
        return when (val code = s.str("name").orEmpty()) {
            "qidian" -> "Khởi Điểm"; "jjwxc" -> "Tấn Giang"; "shuhaige" -> "Thư Hải Các"; "ddxs" -> "Đỉnh Điểm"
            "quanben5" -> "Toàn Bản"; "faloo" -> "Phi Lư"; "ptwxz" -> "Phiêu Thiên"; "69shuba" -> "69 Thư Ba"
            "xslou" -> "Tiểu Thuyết Lâu"; "qiushubang" -> "Cầu Thư Bang"; "123bqg" -> "Bút Thú Các"
            else -> code.replaceFirstChar { it.uppercase() }
        }
    }

    fun novel(j: JSONObject) = Novel(
        id = j.getInt("id"),
        title = j.str("title_vi") ?: j.str("title_zh").orEmpty(),
        author = j.str("author_vi") ?: j.str("author_zh").orEmpty(),
        cover = j.str("cover_url"),
        status = j.str("status") ?: "ongoing",
        chaptersSource = j.optInt("chapter_count_source"),
        chaptersTranslated = j.optInt("chapter_count_translated"),
        genres = j.optJSONArray("genres")?.let { a -> List(a.length()) { a.getString(it) } }.orEmpty(),
        source = sourceLabel(j.optJSONObject("sources")),
        description = j.str("description_vi"),
    )

    fun statusLabel(s: String) = when (s) { "ongoing" -> "Đang ra"; "completed" -> "Hoàn thành"; "hiatus" -> "Tạm ngưng"; else -> s }

    // ---------- Khám phá ----------

    suspend fun section(s: Section, offset: Int, limit: Int): List<Novel> {
        var f = "$VISIBLE&meta_translated=eq.true"
        if (s == Section.Completed) f += "&status=eq.completed"
        if (s == Section.Latest || s == Section.Recommended) f += "&status=neq.completed"
        if (s == Section.Latest || s == Section.Completed) {
            return get("novels?select=$COLS&$f&order=last_chapter_at.desc.nullslast,id.desc&offset=$offset&limit=$limit").objs().map(::novel)
        }
        // Nổi bật/Đề cử: lấy "hồ" cột tối thiểu, trộn nguồn round-robin, xoay theo ngày (xem novels.dart)
        val pool = get("novels?select=id,source_id,source_rank,last_chapter_at&$f&order=source_rank.asc.nullslast,last_chapter_at.desc.nullslast,id.desc&limit=240").objs()
        var mixed = roundRobin(pool)
        if (mixed.size > 6) {
            val off = (ChronoUnit.DAYS.between(LocalDate.of(2024, 1, 1), LocalDate.now(ZoneOffset.UTC)) % mixed.size).toInt()
            mixed = mixed.drop(off) + mixed.take(off)
        }
        return hydrate(mixed.drop(offset).take(limit).map { it.getInt("id") })
    }

    private fun roundRobin(pool: List<JSONObject>): List<JSONObject> {
        val groups = pool.groupBy { it.optInt("source_id") }.values.sortedWith(
            compareBy<List<JSONObject>> { if (it[0].isNull("source_rank")) Int.MAX_VALUE else it[0].getInt("source_rank") }
                .thenByDescending { it[0].optString("last_chapter_at") },
        )
        return (0 until (groups.maxOfOrNull { it.size } ?: 0)).flatMap { i -> groups.mapNotNull { it.getOrNull(i) } }
    }

    /** Nạp đủ cột cho các id, giữ thứ tự truyền vào (PostgREST trả theo thứ tự của nó). */
    private suspend fun hydrate(ids: List<Int>): List<Novel> {
        if (ids.isEmpty()) return emptyList()
        val byId = get("novels?select=$COLS&id=in.(${ids.joinToString(",")})").objs().map(::novel).associateBy { it.id }
        return ids.mapNotNull { byId[it] }
    }

    suspend fun search(f: Filter): List<Novel> {
        val p = StringBuilder("novels?select=$COLS&$VISIBLE")
        if (f.q.isNotBlank()) p.append("&search_norm=ilike.").append(enc("*${boDau(f.q.trim()).replace(',', ' ')}*"))
        if (f.minChapters > 0) p.append("&chapter_count_source=gte.${f.minChapters}")
        f.status?.let { p.append("&status=eq.$it") }
        f.genre?.let { p.append("&genres=cs.").append(enc("{\"$it\"}")) }
        if (f.sources.isNotEmpty()) p.append("&source_id=in.(${f.sources.joinToString(",")})")
        return get("$p&order=last_chapter_at.desc.nullslast&limit=60").objs().map(::novel)
    }

    suspend fun genres(): List<String> = get("novels?select=genres&genres=not.is.null&limit=300").objs()
        .flatMap { o -> o.optJSONArray("genres")?.let { a -> List(a.length()) { a.getString(it).trim() } }.orEmpty() }
        .filter { it.isNotEmpty() }.toSortedSet().toList()

    suspend fun sources(): List<Pair<Int, String>> =
        get("sources?select=id,name,label&enabled=eq.true&order=name").objs().map { it.getInt("id") to sourceLabel(it) }

    // ---------- Truyện + chương ----------

    // Lỗi mạng (IOException) → bản offline nếu có; lỗi server (ApiError) vẫn báo như cũ.
    suspend fun novel(id: Int): Novel = novel(
        try { get("novels?select=*,sources(name,label)&id=eq.$id").getJSONObject(0) } catch (e: IOException) { Offline.novelJson(id) ?: throw e },
    )

    /** Mục lục đủ hết, kéo theo trang vượt trần 1000 dòng/query của PostgREST. */
    suspend fun toc(novelId: Int): List<ChapterRef> = try { tocOnline(novelId) } catch (e: IOException) {
        Offline.toc(novelId).ifEmpty { throw e }
    }

    private suspend fun tocOnline(novelId: Int): List<ChapterRef> {
        val all = mutableListOf<ChapterRef>()
        while (true) {
            val page = get(
                "chapters?select=chapter_index,title_vi,title_zh,translation_status&novel_id=eq.$novelId" +
                    "&order=chapter_index.asc&offset=${all.size}&limit=1000",
            ).objs()
            page.mapTo(all) {
                val i = it.getInt("chapter_index")
                ChapterRef(i, it.str("title_vi") ?: it.str("title_zh") ?: "Chương $i", it.optString("translation_status") == "done")
            }
            if (page.size < 1000) return all
        }
    }

    suspend fun chapter(novelId: Int, index: Int): Chapter? {
        val row = try {
            get("chapters?select=chapter_index,title_vi,content_vi,translation_status,updated_at&novel_id=eq.$novelId&chapter_index=eq.$index&limit=1")
                .objs().firstOrNull()
                // truyện đã tải: chương vừa đọc online (có thể đã sửa/vá) đè xuống bản offline
                ?.also { if (it.optString("translation_status") == "done" && Offline.has(novelId)) Offline.save(novelId, listOf(it)) }
        } catch (e: IOException) {
            Offline.chapter(novelId, index) ?: throw e
        }
        return row?.let {
                Chapter(
                    index = index,
                    title = it.str("title_vi")?.trimStart('#', ' ')?.trim()?.ifEmpty { null } ?: "Chương $index",
                    status = it.optString("translation_status"),
                    paragraphs = it.str("content_vi").orEmpty().split('\n').map(String::trim).filter(String::isNotEmpty),
                )
            }
    }

    /** Mở đọc = tín hiệu đọc thật → crawler tải mục lục đầy đủ cho truyện "lười". No-op nếu đã có. */
    suspend fun requestToc(novelId: Int) = rpc("request_toc", "p_novel_id" to novelId)
    suspend fun requestTranslation(novelId: Int, upTo: Int, priority: Int = 5) =
        rpc("request_translation", "p_novel_id" to novelId, "p_up_to" to upTo, "p_priority" to priority)
    suspend fun retranslateAll(novelId: Int) = rpc("retranslate_all", "p_novel_id" to novelId)
    /** Cờ profiles.is_admin — server (mig 126) chỉ cho admin dịch lại chương đã dịch. */
    suspend fun isAdmin(): Boolean {
        val u = uid() ?: return false
        return runCatching { get("profiles?select=is_admin&id=eq.$u").objs().firstOrNull()?.optBoolean("is_admin") == true }.getOrDefault(false)
    }
    suspend fun retranslate(novelId: Int, index: Int) = rpc("retranslate_chapter", "p_novel_id" to novelId, "p_index" to index)
    // ---------- Sửa bản dịch (RPC SECURITY DEFINER, cùng Flutter data/chapters.dart) ----------

    /** Thay [wrong]→[correct] CHỈ trong đoạn [para] (server từ chối nếu đoạn không có trong chương). */
    suspend fun editWord(novelId: Int, index: Int, wrong: String, correct: String, para: String) =
        rpc("edit_chapter_vi", "p_novel_id" to novelId, "p_index" to index, "p_wrong" to wrong, "p_correct" to correct, "p_para" to para)

    /** Viết lại nguyên đoạn; server báo lỗi nếu đoạn cũ đã bị ai đó đổi. */
    suspend fun editPara(novelId: Int, index: Int, old: String, new: String) =
        rpc("edit_chapter_para", "p_novel_id" to novelId, "p_index" to index, "p_old" to old, "p_new" to new)

    /** Các dòng chữ Trung của chương (để AI có câu gốc); lỗi mạng → rỗng. */
    suspend fun chapterZh(novelId: Int, index: Int): List<String> = runCatching {
        get("chapters?select=content_zh&novel_id=eq.$novelId&chapter_index=eq.$index&limit=1").objs().firstOrNull()
            ?.str("content_zh").orEmpty().split('\n').map(String::trim).filter(String::isNotEmpty)
    }.getOrDefault(emptyList())

    /** Gợi ý AI (Edge Function goi-y-dich): các cách dịch khác cho [chon] trong câu [vi] có nguồn [zh]. */
    suspend fun suggest(zh: String, vi: String, chon: String): Suggest {
        val r = JSONObject(raw("POST", "functions/v1/goi-y-dich", JSONObject(mapOf("zh" to zh, "vi" to vi, "chon" to chon))))
        val items = r.optJSONArray("goi_y")?.objs().orEmpty().map { it.optString("vi") to it.optString("y") }.filter { it.first.isNotBlank() }
        return Suggest(r.optString("thay").ifBlank { chon }, r.optBoolean("dung"), items)
    }

    /** Tên riêng đã sửa → ghi vào thuật ngữ truyện để chương dịch SAU đúng tên (chỉ sửa term đang mang đúng bản sai, như Flutter). */
    suspend fun learnTerm(novelId: Int, wrong: String, correct: String) {
        get("glossary_terms?select=id&novel_id=eq.$novelId&correct_vi=eq.${enc(wrong)}").objs().forEach {
            raw("PATCH", "rest/v1/glossary_terms?id=eq.${it.getLong("id")}", JSONObject(mapOf("correct_vi" to correct, "wrong_vi" to wrong, "approved" to true)), prefer = "return=minimal")
        }
    }

    suspend fun report(novelId: Int, index: Int, reason: String) =
        insert("glossary_reports", mapOf("novel_id" to novelId, "reason" to "Chương $index: $reason", "reported_by" to uid()))

    // ---------- Thuật ngữ truyện (bảng glossary_terms, cùng Flutter data/glossary.dart) ----------

    /** Term của truyện + term toàn cục; chưa duyệt lên trước, mới tạo lên trước. */
    suspend fun terms(novelId: Int): List<Term> =
        get("glossary_terms?select=id,term_zh,wrong_vi,correct_vi,term_type,scope,approved,narrator_term&or=(novel_id.eq.$novelId,novel_id.is.null)&order=approved.asc,created_at.desc")
            .objs().map {
                Term(it.getLong("id"), it.str("term_zh"), it.str("wrong_vi"), it.optString("correct_vi"), it.optString("term_type", "other"),
                    it.str("scope"), it.optBoolean("approved"), it.str("narrator_term"))
            }

    /** [id] null = thêm mới (đã duyệt luôn); không thì sửa. Trường rỗng ghi NULL để xoá giá trị cũ. */
    suspend fun saveTerm(novelId: Int, id: Long?, zh: String, correct: String, wrong: String, type: String, narrator: String) {
        fun v(s: String): Any = s.trim().ifEmpty { null } ?: JSONObject.NULL
        val fields = mapOf(
            "term_zh" to v(zh), "correct_vi" to correct.trim(), "wrong_vi" to v(wrong), "term_type" to type,
            "narrator_term" to if (type == "person") v(narrator) else JSONObject.NULL, // đổi khỏi nhân vật thì bỏ cách gọi
        )
        if (id == null) insert("glossary_terms", fields + mapOf("novel_id" to novelId, "approved" to true, "created_by" to uid()))
        else raw("PATCH", "rest/v1/glossary_terms?id=eq.$id", JSONObject(fields), prefer = "return=minimal")
    }

    suspend fun approveTerm(id: Long) =
        raw("PATCH", "rest/v1/glossary_terms?id=eq.$id", JSONObject(mapOf("approved" to true)), prefer = "return=minimal")

    suspend fun deleteTerms(ids: Collection<Long>) = delete("glossary_terms?id=in.(${ids.joinToString(",")})")

    /** Xếp job vá chương đã dịch bằng cặp "sai → đúng" (string-replace phía worker). */
    suspend fun requestPatch(novelId: Int) = rpc("request_patch", "p_novel_id" to novelId)

    suspend fun latestPatch(novelId: Int): PatchJob? =
        JSONArray(rpc("latest_patch_status", "p_novel_id" to novelId)).objs().firstOrNull()?.let { PatchJob(it.optString("status"), it.str("result")) }

    suspend fun reportTerm(termId: Long, novelId: Int, reason: String) =
        insert("glossary_reports", mapOf("term_id" to termId, "novel_id" to novelId, "reason" to reason, "reported_by" to uid()))

    // ---------- Tủ truyện + tiến độ ----------

    /** Tủ truyện = truyện theo dõi (library) ∪ đang đọc (reading_progress), mới động tới nhất lên đầu. */
    suspend fun library(): List<Novel> {
        val u = uid() ?: return emptyList()
        val byId = LinkedHashMap<Int, Pair<Novel, String>>()
        get("library?select=added_at,novels($COLS)&user_id=eq.$u").objs().forEach {
            val n = novel(it.getJSONObject("novels")); byId[n.id] = n to it.optString("added_at")
        }
        get("reading_progress?select=chapter_index,updated_at,novels($COLS)&user_id=eq.$u").objs().forEach {
            val n = novel(it.getJSONObject("novels")).copy(curChapter = it.getInt("chapter_index"))
            byId[n.id] = n to it.optString("updated_at")
        }
        return byId.values.sortedByDescending { it.second }.map { it.first }
    }

    suspend fun inLibrary(novelId: Int): Boolean {
        val u = uid() ?: return false
        return get("library?select=novel_id&novel_id=eq.$novelId&user_id=eq.$u&limit=1").length() > 0
    }

    suspend fun setInLibrary(novelId: Int, add: Boolean) {
        val u = uid() ?: return
        if (add) insert("library", mapOf("user_id" to u, "novel_id" to novelId), upsert = true)
        else delete("library?user_id=eq.$u&novel_id=eq.$novelId")
    }

    /** Xoá khỏi tủ = bỏ theo dõi + xoá tiến độ (cả vị trí cuộn lưu máy). */
    suspend fun removeReading(novelId: Int) {
        Prefs.sp.edit().apply { Prefs.sp.all.keys.filter { it.startsWith("rp_${novelId}_") }.forEach(::remove) }.apply()
        val u = uid() ?: return
        delete("reading_progress?user_id=eq.$u&novel_id=eq.$novelId")
        delete("library?user_id=eq.$u&novel_id=eq.$novelId")
    }

    /**
     * Hàng đợi dịch: job đang chạy (lấy riêng — worker chọn theo lane nên có thể ưu tiên cao hơn 200 job chờ đầu)
     * + 200 job chờ đầu xếp y như claim_next_job + chương vừa dịch xong trong 1 giờ.
     */
    suspend fun queue(): Queue {
        val cols = "priority,status,novel_id,chapter_id,created_at,chapters(chapter_index,title_vi,title_zh)," +
            "novels(title_vi,title_zh,cover_url,chapter_count_translated,chapter_count_source,sources(name,label,enabled))"
        val running = get("translation_jobs?select=$cols&type=eq.chapter&status=eq.running").objs()
        val pending = sortLikeClaim(
            get("translation_jobs?select=$cols&type=eq.chapter&status=eq.pending&order=priority.asc,created_at.asc&limit=200").objs(),
        )
        val h = mutableMapOf<String, String>()
        raw("GET", "rest/v1/translation_jobs?select=id&type=eq.chapter&status=eq.pending&limit=1", prefer = "count=exact", headerOut = h)
        val total = h["range"]?.substringAfter('/')?.toIntOrNull() ?: pending.size
        // chương chờ mà content_zh chưa tải = crawler đang lấy nguồn
        val ids = pending.map { it.optInt("chapter_id") }.filter { it > 0 }
        val noZh = if (ids.isEmpty()) emptySet() else get("chapters?select=id&id=in.(${ids.joinToString(",")})&content_zh=is.null").objs().map { it.getInt("id") }.toSet()
        val active = (running + pending).filter { !it.isNull("chapters") }.map { j ->
            val c = j.getJSONObject("chapters")
            val n = j.optJSONObject("novels")
            val srcOff = n?.optJSONObject("sources")?.let { it.has("enabled") && !it.optBoolean("enabled", true) } == true
            QJob(
                novelId = j.getInt("novel_id"), index = c.getInt("chapter_index"), title = c.str("title_vi"),
                status = when {
                    j.optString("status") == "running" -> "translating"
                    j.optInt("chapter_id") in noZh -> if (srcOff) "source_unavailable" else "downloading"
                    else -> "queued"
                },
                novelTitle = n?.str("title_vi") ?: n?.str("title_zh") ?: "Truyện", cover = n?.str("cover_url"),
                done = n?.optInt("chapter_count_translated") ?: 0, total = n?.optInt("chapter_count_source") ?: 0,
            )
        }
        val since = Instant.now().minus(1, ChronoUnit.HOURS).toString()
        val cleared = Prefs.sp.getString("queue_done_cleared_at", null) // user bấm "Xoá" lịch sử (chỉ ẩn trên máy)
        val lower = if (cleared != null && cleared > since) cleared else since
        val recent = get(
            "chapters?select=chapter_index,title_vi,novel_id,translated_at,novels(title_vi,title_zh,cover_url)" +
                "&translation_status=eq.done&translated_at=gte.${enc(lower)}&order=translated_at.desc&limit=60",
        ).objs().map {
            val n = it.optJSONObject("novels")
            QDone(it.getInt("novel_id"), it.getInt("chapter_index"), n?.str("title_vi") ?: n?.str("title_zh") ?: "Truyện", n?.str("cover_url"))
        }
        return Queue(active, recent, total)
    }

    fun clearQueueDone() = Prefs.sp.edit().putString("queue_done_cleared_at", Instant.now().toString()).apply()

    /** Xếp y như claim_next_job (migration 117): priority → truyện có job chờ lâu nhất ở mức đó → chapter_index → created_at. MIRROR câu ORDER BY trong SQL. */
    internal fun sortLikeClaim(jobs: List<JSONObject>): List<JSONObject> {
        val since = HashMap<Pair<Int, Int>, String>()
        jobs.forEach {
            val k = it.optInt("novel_id") to it.optInt("priority")
            val c = it.optString("created_at")
            if (since[k] == null || c < since.getValue(k)) since[k] = c
        }
        fun idx(j: JSONObject) = j.optJSONObject("chapters")?.optInt("chapter_index", -1) ?: -1
        return jobs.sortedWith(
            compareBy<JSONObject>({ it.optInt("priority") }, { since.getValue(it.optInt("novel_id") to it.optInt("priority")) }, { idx(it) }, { it.optString("created_at") }),
        )
    }

    suspend fun profile(): Profile? {
        val u = uid() ?: return null
        return get("profiles?select=display_name,avatar_url,streak,last_read_date&id=eq.$u&limit=1").objs().firstOrNull()?.let {
            Profile(it.str("display_name"), it.str("avatar_url"), it.optInt("streak"), it.str("last_read_date"))
        }
    }

    /** RLS own_profile cho sửa hàng của mình; is_admin bị trigger chặn nên không lo lộ quyền. */
    suspend fun updateProfile(name: String, avatar: String) {
        val u = uid() ?: return
        raw("PATCH", "rest/v1/profiles?id=eq.$u", JSONObject(mapOf("display_name" to name, "avatar_url" to avatar)), prefer = "return=minimal")
    }

    /** (số truyện đang đọc, tổng chương đã tiến). */
    suspend fun readStats(): Pair<Int, Int> {
        val u = uid() ?: return 0 to 0
        val rows = get("reading_progress?select=chapter_index&user_id=eq.$u").objs()
        return rows.size to rows.sumOf { it.optInt("chapter_index") }
    }

    /** Chuỗi "còn sống" = đọc gần nhất là hôm nay/hôm qua; đứt thì 0. */
    fun liveStreak(p: Profile?): Int {
        val last = p?.lastRead?.let { runCatching { LocalDate.parse(it.take(10)) }.getOrNull() } ?: return 0
        return if (p.streak > 0 && ChronoUnit.DAYS.between(last, LocalDate.now()) <= 1) p.streak else 0
    }

    suspend fun progress(novelId: Int): Int? {
        val u = uid() ?: return null
        return try {
            get("reading_progress?select=chapter_index&novel_id=eq.$novelId&user_id=eq.$u&limit=1").objs().firstOrNull()?.getInt("chapter_index")
                ?.also { Prefs.sp.edit().putInt("last_$novelId", it).apply() }
        } catch (e: IOException) {
            Prefs.sp.getInt("last_$novelId", 0).takeIf { it > 0 } ?: throw e // đọc offline vẫn nhớ chương đang đọc
        }
    }

    suspend fun saveProgress(novelId: Int, index: Int) {
        val u = uid() ?: return
        Prefs.sp.edit().putInt("last_$novelId", index).apply()
        insert(
            "reading_progress",
            mapOf("user_id" to u, "novel_id" to novelId, "chapter_index" to index, "updated_at" to Instant.now().toString()),
            upsert = true,
        )
        runCatching { rpc("touch_reading_streak") } // chuỗi ngày đọc — không chặn đọc
    }

    suspend fun bookmarks(novelId: Int): List<Bookmark> {
        val u = uid() ?: return emptyList()
        return get("bookmarks?select=id,chapter_index,percent,excerpt&user_id=eq.$u&novel_id=eq.$novelId&order=created_at").objs()
            .map { Bookmark(it.getInt("id"), it.getInt("chapter_index"), it.optDouble("percent", 0.0), it.str("excerpt")) }
    }

    suspend fun addBookmark(novelId: Int, index: Int, percent: Float, excerpt: String?) = rpc(
        "add_bookmark", "p_novel_id" to novelId, "p_chapter_index" to index, "p_percent" to percent.toDouble(),
        "p_excerpt" to excerpt, "p_note" to null,
    )

    suspend fun deleteBookmark(id: Int) = rpc("delete_bookmark", "p_id" to id)

    /** Bản dịch đúng của thuật ngữ (truyện này + chung) để gạch chân khi đọc; trần 200 như Flutter. */
    suspend fun glossary(novelId: Int): List<String> =
        get("glossary_terms?select=correct_vi&or=(novel_id.eq.$novelId,novel_id.is.null)&order=novel_id.nullslast&limit=1000").objs()
            .mapNotNull { it.str("correct_vi")?.trim()?.takeIf { v -> v.length >= 2 } }.take(200)

    // ---------- Thông báo (pull-based, trạng thái đã xem/đã xoá lưu máy) ----------

    suspend fun notifications(): List<Notif> {
        val u = uid() ?: return emptyList()
        val ids = (get("library?select=novel_id&user_id=eq.$u").objs() + get("reading_progress?select=novel_id&user_id=eq.$u").objs())
            .map { it.getInt("novel_id") }.toSet()
        if (ids.isEmpty()) return emptyList()
        val since = Instant.now().minus(7, ChronoUnit.DAYS).toString()
        val dismissed = Prefs.dismissed
        return get(
            "chapters?select=chapter_index,translated_at,novel_id,novels(title_vi,title_zh,cover_url)" +
                "&novel_id=in.(${ids.joinToString(",")})&translation_status=eq.done&translated_at=gte.${enc(since)}" +
                "&order=translated_at.desc&limit=200",
        ).objs().filter { "${it.getInt("novel_id")}:${it.getInt("chapter_index")}" !in dismissed }
            .groupBy { it.getInt("novel_id") }
            .map { (id, rows) ->
                val n = rows[0].getJSONObject("novels")
                Notif(id, n.str("title_vi") ?: n.str("title_zh").orEmpty(), n.str("cover_url"),
                    rows.map { it.getInt("chapter_index") }.sorted(), rows[0].getString("translated_at"))
            }
    }

    fun dismiss(n: Notif) { Prefs.dismissed = Prefs.dismissed + n.chapters.map { "${n.novelId}:$it" } }
}
