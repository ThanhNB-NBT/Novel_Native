package com.thanhnb.novelnative

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URI
import java.util.concurrent.TimeUnit

/**
 * Địa chỉ máy chủ động (như endpoint.dart của bản Flutter): đổi/dời máy chủ không cần phát hành bản mới.
 * Ứng viên = file cấu hình từ xa (active + fallbacks) + địa chỉ đã lưu lần trước + địa chỉ build sẵn;
 * chỉ nhận địa chỉ có Data API, Auth và Storage cùng sống. Mọi lời gọi mạng chờ [await] trước khi chạy.
 *
 * Đường nhanh: địa chỉ đã lưu còn sống (thử ≤1,5s) thì dùng luôn, dò đầy đủ chạy nền để cập nhật cho lần mở sau.
 * ponytail: phiên đăng nhập không tách theo máy chủ — dời sang máy chủ khác (khác khoá JWT) thì phải đăng nhập lại.
 */
object Endpoint {
    @Volatile var url: String = BuildConfig.SUPABASE_URL.trimEnd('/')
        private set
    @Volatile var anon: String = BuildConfig.SUPABASE_ANON_KEY
        private set

    private class Ep(val url: String, val anon: String)

    private val ready = CompletableDeferred<Unit>()
    suspend fun await() = ready.await()

    private val http = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS).readTimeout(4, TimeUnit.SECONDS).callTimeout(5, TimeUnit.SECONDS).build()
    private val LOCAL = setOf("localhost", "127.0.0.1", "[::1]", "::1", "10.0.2.2")

    private fun allowedHosts(): Set<String> =
        BuildConfig.ENDPOINT_ALLOWED_HOSTS.split(',').map { it.trim().lowercase() }.filter { it.isNotEmpty() }.toMutableSet()
            .also { s -> hostOf(BuildConfig.SUPABASE_URL)?.let(s::add) }

    private fun hostOf(u: String) = runCatching { URI(u).host?.lowercase() }.getOrNull()

    /** Hợp lệ: có khoá, https (hoặc http tới máy local), và host nằm trong danh sách cho phép (nếu [allowed] != null). */
    private fun valid(rawUrl: String?, rawKey: String?, allowed: Set<String>?): Ep? {
        val u = rawUrl.orEmpty().trim().trimEnd('/')
        val k = rawKey.orEmpty().trim()
        val uri = runCatching { URI(u) }.getOrNull() ?: return null
        val host = uri.host?.lowercase() ?: return null
        val local = host in LOCAL
        if (k.isEmpty() || (allowed != null && host !in allowed)) return null
        if (!(uri.scheme == "https" || (uri.scheme == "http" && local))) return null
        return Ep(u, k)
    }

    private fun apply(e: Ep) { url = e.url; anon = e.anon }

    fun start(scope: CoroutineScope) {
        scope.launch(Dispatchers.IO) {
            val allowed = allowedHosts()
            val cached = valid(Prefs.sp.getString("endpoint_url", null), Prefs.sp.getString("endpoint_anon", null), allowed)
            if (cached != null && withTimeoutOrNull(1500) { probe(cached) } == true) {
                apply(cached)
                ready.complete(Unit)
                runCatching { resolveFull(allowed) } // cập nhật nền cho lần mở sau
            } else {
                runCatching { resolveFull(allowed)?.let(::apply) }
                ready.complete(Unit)
            }
        }
    }

    private suspend fun resolveFull(allowed: Set<String>): Ep? {
        val cands = ArrayList<Ep>()
        fun add(e: Ep?) { if (e != null && cands.none { it.url == e.url }) cands += e }
        BuildConfig.ENDPOINT_CONFIG_URL.takeIf { it.isNotBlank() }?.let { cfg -> fetchJson(cfg)?.let { configCandidates(it, allowed).forEach(::add) } }
        add(valid(Prefs.sp.getString("endpoint_url", null), Prefs.sp.getString("endpoint_anon", null), allowed))
        add(valid(BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_ANON_KEY, null))
        if (cands.isEmpty()) return null
        val ok = coroutineScope { cands.map { async { probe(it) } }.awaitAll() }
        val pick = cands.indices.firstOrNull { ok[it] }?.let { cands[it] } ?: return null
        Prefs.sp.edit().putString("endpoint_url", pick.url).putString("endpoint_anon", pick.anon).apply()
        return pick
    }

    /** Nhận cả JSON cũ {url, anonKey} lẫn {active, fallbacks:[…]}; mục sai kiểu bị bỏ qua, không làm treo lúc khởi động. */
    private fun configCandidates(cfg: JSONObject, allowed: Set<String>): List<Ep> {
        val items = ArrayList<JSONObject>()
        (if (cfg.has("active")) cfg.optJSONObject("active") else cfg)?.let(items::add)
        cfg.optJSONArray("fallbacks")?.let { a -> for (i in 0 until a.length()) a.optJSONObject(i)?.let(items::add) }
        return items.mapNotNull { valid(it.optString("url"), it.optString("anonKey"), allowed) }
    }

    private suspend fun fetchJson(u: String): JSONObject? = withContext(Dispatchers.IO) {
        runCatching { http.newCall(Request.Builder().url(u).build()).execute().use { r -> if (r.code == 200) JSONObject(r.body?.string().orEmpty()) else null } }.getOrNull()
    }

    private suspend fun probe(e: Ep): Boolean = coroutineScope {
        listOf("/rest/v1/novels?select=id&limit=1", "/auth/v1/health", "/storage/v1/status").map { p ->
            async(Dispatchers.IO) {
                runCatching {
                    http.newCall(Request.Builder().url(e.url + p).header("apikey", e.anon).header("Authorization", "Bearer ${e.anon}").build())
                        .execute().use { it.code in 200..299 }
                }.getOrDefault(false)
            }
        }.awaitAll().all { it }
    }
}
