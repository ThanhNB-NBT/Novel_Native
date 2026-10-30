package com.thanhnb.novelnative

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

/** Chuỗi hoặc null (JSON null / rỗng đều coi là không có). */
fun JSONObject.s(k: String): String? = if (isNull(k)) null else optString(k).ifEmpty { null }

/** Tên truyện: ưu tiên bản Việt, rồi bản Trung. */
fun JSONObject?.novelTitle(id: Int): String = this?.s("title_vi") ?: this?.s("title_zh") ?: "Truyện #$id"

private fun JSONArray.rows() = List(length()) { getJSONObject(it) }

/**
 * Truy vấn quản trị — chép đúng bảng/cột/RPC của data/admin.dart bên Flutter.
 * RLS phía máy chủ chỉ cho admin đọc/ghi; user thường nhận rỗng hoặc lỗi.
 */
object AdminApi {
    private suspend fun get(path: String) = Api.get(path).rows()

    /** Đếm bằng Content-Range (count=exact), không kéo dòng nào về. [path] chưa có select/limit. */
    private suspend fun count(path: String): Int {
        val h = mutableMapOf<String, String>()
        Api.raw("GET", "rest/v1/$path${if ('?' in path) "&" else "?"}select=id&limit=1", prefer = "count=exact", headerOut = h)
        return h["range"]?.substringAfter('/')?.toIntOrNull() ?: 0
    }

    private suspend fun patch(path: String, body: JSONObject) = Api.raw("PATCH", "rest/v1/$path", body, prefer = "return=minimal")

    private fun ago(hours: Long) = Api.enc(Instant.now().minus(hours, ChronoUnit.HOURS).toString())

    // ---------- Worker ----------

    /**
     * Mọi bộ đếm của tab Truyện + Crawl + Worker trong MỘT lượt RPC (mig 143). Trước đây 18 truy vấn đếm riêng, mỗi cái ~300ms
     * qua Tunnel + RLS gọi is_admin() từng hàng → vào màn Quản trị chờ 3-5s. Nhiều màn mở cùng lúc dùng chung kết quả 2 giây.
     */
    private var countsAt = 0L
    private var countsVal: Map<String, Int>? = null
    private suspend fun counts(): Map<String, Int> {
        countsVal?.takeIf { System.nanoTime() - countsAt < 2_000_000_000L }?.let { return it }
        val o = JSONObject(Api.rpc("admin_counts"))
        return o.keys().asSequence().associateWith { o.optInt(it) }.also { countsVal = it; countsAt = System.nanoTime() }
    }

    /** Số job pending THẬT; danh sách [jobs] chỉ là cửa sổ đầu hàng đợi. */
    suspend fun pendingJobCount() = counts()["pendingJobs"] ?: 0

    const val JOBS_WINDOW = 120

    /** Job running/failed/pending (cửa sổ đầu hàng đợi, xếp như worker claim) + cờ downloading/source_unavailable do máy chủ tính sẵn. */
    suspend fun jobs(): List<JSONObject> =
        Api.sortLikeClaim(JSONArray(Api.rpc("admin_jobs_window", "p_limit" to JOBS_WINDOW)).rows())

    /** Nhịp tim crawler/translator: name, at, note. */
    suspend fun heartbeat() = get("worker_heartbeat?select=name,at,note")

    suspend fun retryAllFailed(): Int = Api.rpc("admin_retry_all_failed").trim().toIntOrNull() ?: 0
    suspend fun requestAudit() { Api.rpc("request_audit") }
    suspend fun retryJob(id: Int) { Api.rpc("admin_retry_job", "p_job_id" to id) }
    suspend fun reprioritize(id: Int, priority: Int) { patch("translation_jobs?id=eq.$id", JSONObject().put("priority", priority)) }

    /** Huỷ job: xoá job VÀ trả chương về 'none' (trừ chương đã dịch xong). */
    suspend fun cancelJob(id: Int, chapterId: Int?) {
        Api.delete("translation_jobs?id=eq.$id")
        if (chapterId != null && chapterId > 0)
            patch("chapters?id=eq.$chapterId&translation_status=neq.done", JSONObject().put("translation_status", "none"))
    }

    /** Huỷ toàn bộ chương ĐANG CHỜ của 1 truyện. */
    suspend fun cancelNovelQueue(novelId: Int) {
        Api.delete("translation_jobs?novel_id=eq.$novelId&status=eq.pending")
        patch("chapters?novel_id=eq.$novelId&translation_status=eq.queued", JSONObject().put("translation_status", "none"))
    }

    // ---------- Đang đọc / Báo cáo ----------

    /** Người đang đọc trong 8 giờ qua (RLS admin_read_progress mới thấy của mọi user). */
    suspend fun readingNow() = get(
        "reading_progress?select=novel_id,chapter_index,updated_at,novels(title_vi,title_zh)&updated_at=gte.${ago(8)}&order=updated_at.desc&limit=100",
    )

    suspend fun reports() = get(
        "glossary_reports?select=id,reason,created_at,term_id,novel_id,glossary_terms(term_zh,wrong_vi,correct_vi),novels(title_vi,title_zh)" +
            "&resolved=eq.false&order=created_at.desc&limit=100",
    )

    suspend fun resolveReport(id: Int) { patch("glossary_reports?id=eq.$id", JSONObject().put("resolved", true)) }

    // ---------- Truyện ----------

    private const val NOVEL_COLS = "id,title_vi,title_zh,author_vi,status,hidden,source_id,genres,last_chapter_at," +
        "chapter_count_source,chapter_count_translated,cover_url,sources(name,label)"

    /** tab: 0 tất cả · 1 đang hiển thị · 2 đã ẩn. Dùng chung cho trang dữ liệu và ô đếm. */
    private fun novelWhere(q: String, tab: Int): String {
        val s = Api.boDau(q.trim()).replace(',', ' ').trim()
        return listOfNotNull(
            if (s.isNotEmpty()) "search_norm=ilike.*${Api.enc(s)}*" else null,
            when (tab) { 1 -> "hidden=eq.false"; 2 -> "hidden=eq.true"; else -> null },
        ).joinToString("&")
    }

    suspend fun novelPage(q: String, tab: Int, offset: Int, limit: Int) = get(
        "novels?select=$NOVEL_COLS&${novelWhere(q, tab)}&order=updated_at.desc,id.desc&offset=$offset&limit=$limit",
    )

    /** (tất cả, hiển thị, đã ẩn) trong cùng kết quả tìm kiếm. */
    suspend fun novelCounts(q: String): Triple<Int, Int, Int> = coroutineScope {
        val r = (0..2).map { t -> async { count("novels?${novelWhere(q, t)}") } }
        Triple(r[0].await(), r[1].await(), r[2].await())
    }

    suspend fun novel(id: Int) = get("novels?select=$NOVEL_COLS&id=eq.$id").firstOrNull()

    /** Thống kê toàn app. */
    suspend fun appStats(): Map<String, Int> = counts().filterKeys {
        it in setOf("novels", "visible", "metaPending", "completed", "chapters", "done", "doneToday", "failed")
    }

    suspend fun setHidden(id: Int, hidden: Boolean) { patch("novels?id=eq.$id", JSONObject().put("hidden", hidden)) }
    suspend fun updateNovel(id: Int, title: String, author: String, status: String) {
        patch("novels?id=eq.$id", JSONObject().put("title_vi", title).put("author_vi", author).put("status", status))
    }
    /** Xoá VĨNH VIỄN — FK cascade dọn chapters/glossary/tiến độ/tủ sách/job. */
    suspend fun deleteNovel(id: Int) { Api.delete("novels?id=eq.$id") }
    /** Dịch chương [from, to]. from ≤ 1 dùng RPC cũ (tới chương `to`); từ chương > 1 cần RPC request_translation_range (mig 139). */
    suspend fun requestRange(novelId: Int, from: Int, to: Int): Int =
        (if (from <= 1) Api.rpc("request_translation", "p_novel_id" to novelId, "p_up_to" to to, "p_priority" to 50)
        else Api.rpc("request_translation_range", "p_novel_id" to novelId, "p_from" to from, "p_to" to to, "p_priority" to 50)).trim().toIntOrNull() ?: 0
    suspend fun requestMeta(novelId: Int) { Api.rpc("request_meta", "p_novel_id" to novelId) }

    suspend fun chapterPage(novelId: Int, offset: Int, limit: Int) = get(
        "chapters?select=chapter_index,title_vi,translation_status,model_used,prompt_tokens,completion_tokens,translated_at" +
            "&novel_id=eq.$novelId&order=chapter_index.asc&offset=$offset&limit=$limit",
    )

    // ---------- Crawl ----------

    suspend fun settings() = get("worker_settings?select=key,value,note&order=key")
    suspend fun saveSetting(key: String, value: String) {
        patch("worker_settings?key=eq.${Api.enc(key)}", JSONObject().put("value", value).put("updated_at", Instant.now().toString()))
    }

    suspend fun sources() = get("sources?select=id,name,base_url,enabled,fail_count,last_ok_at&order=enabled.desc,name.asc")
    suspend fun setSourceEnabled(id: Int, on: Boolean) { patch("sources?id=eq.$id", JSONObject().put("enabled", on)) }

    suspend fun newNovels24h() = get(
        "novels?select=id,title_vi,title_zh,cover_url,created_at,source_rank,chapter_count_source,status,sources(name,label)" +
            "&created_at=gte.${ago(24)}&order=created_at.desc",
    )

    /** Nhịp 24 giờ của crawler + translator. */
    suspend fun crawlPulse(): Map<String, Int> = counts().filterKeys {
        it in setOf("novels24h", "chapters24h", "done24h", "queued", "failedChapters", "metaPending", "tracked", "failedJobs", "done1h")
    }

    // ---------- LLM / lỗi / VPS ----------

    suspend fun llmUsageDaily() = JSONObject(Api.rpc("admin_llm_usage_daily", "p_days" to 30))
    suspend fun tokenUsage() = JSONArray(Api.rpc("admin_token_usage")).rows()

    /** Lỗi app người dùng gửi về (bảng client_errors, chỉ admin đọc); user_id → tên hiển thị tra riêng. */
    suspend fun clientErrors(): List<JSONObject> {
        val rows = get("client_errors?select=user_id,created_at,app_version,platform,message,stack&order=created_at.desc&limit=100")
        val ids = rows.mapNotNull { it.s("user_id") }.toSet()
        val names = if (ids.isEmpty()) emptyMap() else get("profiles?select=id,display_name&id=in.(${ids.joinToString(",")})").associate { it.getString("id") to it.s("display_name") }
        return rows.map { it.put("who", names[it.s("user_id")] ?: "?") }
    }

    suspend fun hostMetrics() = get("host_metrics?select=*&order=updated_at.desc")
    suspend fun renameHost(host: String, label: String) =
        patch("host_metrics?host=eq.${java.net.URLEncoder.encode(host, "UTF-8")}", JSONObject().put("label", label.ifBlank { JSONObject.NULL }))
    /** Xoá dòng số liệu của host đã tắt (worker còn sống sẽ tự đẩy lại dòng mới). */
    suspend fun deleteHost(host: String) { Api.delete("host_metrics?host=eq.${java.net.URLEncoder.encode(host, "UTF-8")}") }
    suspend fun hostCommands() = get("host_commands?select=id,host,command,status,output,created_at&order=created_at.desc&limit=10")
    /** Worker trên host nhận lệnh trong ~10s. Whitelist phía worker: restart | restart_crawler | restart_translator. */
    suspend fun sendHostCommand(host: String, command: String) {
        Api.raw("POST", "rest/v1/host_commands", JSONObject().put("host", host).put("command", command), prefer = "return=minimal")
    }
}
