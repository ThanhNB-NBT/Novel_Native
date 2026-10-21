package com.thanhnb.novelnative

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class Novel(val id: Int, val title: String, val author: String, val cover: String?, val chapters: Int)
data class ChapterRef(val index: Int, val title: String)
data class Chapter(val index: Int, val title: String, val paragraphs: List<String>)

/** PostgREST trần: cùng query với app Flutter (lib/data/novels.dart, chapters.dart). */
object Api {
    private suspend fun get(path: String): JSONArray = withContext(Dispatchers.IO) {
        val conn = URL("${BuildConfig.SUPABASE_URL}/rest/v1/$path").openConnection() as HttpURLConnection
        conn.setRequestProperty("apikey", BuildConfig.SUPABASE_ANON_KEY)
        conn.setRequestProperty("Authorization", "Bearer ${BuildConfig.SUPABASE_ANON_KEY}")
        try {
            if (conn.responseCode !in 200..299) {
                error("HTTP ${conn.responseCode}: ${conn.errorStream?.bufferedReader()?.readText()}")
            }
            JSONArray(conn.inputStream.bufferedReader().readText())
        } finally {
            conn.disconnect()
        }
    }

    private inline fun <T> JSONArray.map(f: (JSONObject) -> T) = List(length()) { f(getJSONObject(it)) }
    private fun JSONObject.str(k: String) = optString(k).takeIf { it.isNotEmpty() && it != "null" }

    suspend fun novels(): List<Novel> =
        get(
            "novels?select=id,title_vi,title_zh,author_vi,author_zh,cover_url,chapter_count_translated" +
                "&hidden=eq.false&is_canonical=eq.true&meta_translated=eq.true" +
                "&order=last_chapter_at.desc.nullslast,id.desc&limit=60",
        ).map {
            Novel(
                id = it.getInt("id"),
                title = it.str("title_vi") ?: it.str("title_zh").orEmpty(),
                author = it.str("author_vi") ?: it.str("author_zh").orEmpty(),
                cover = it.str("cover_url"),
                chapters = it.optInt("chapter_count_translated"),
            )
        }

    suspend fun toc(novelId: Int): List<ChapterRef> =
        // ponytail: chỉ lấy 1000 chương đầu, phân trang như Flutter nếu cần truyện dài hơn
        get(
            "chapters?select=chapter_index,title_vi,title_zh&novel_id=eq.$novelId" +
                "&translation_status=eq.done&order=chapter_index.asc&limit=1000",
        ).map {
            val i = it.getInt("chapter_index")
            ChapterRef(i, it.str("title_vi") ?: it.str("title_zh") ?: "Chương $i")
        }

    suspend fun chapter(novelId: Int, index: Int): Chapter? =
        get(
            "chapters?select=chapter_index,title_vi,content_vi" +
                "&novel_id=eq.$novelId&chapter_index=eq.$index&limit=1",
        ).map {
            Chapter(
                index = index,
                title = it.str("title_vi") ?: "Chương $index",
                paragraphs = it.str("content_vi").orEmpty().split('\n').map(String::trim).filter(String::isNotEmpty),
            )
        }.firstOrNull()
}
