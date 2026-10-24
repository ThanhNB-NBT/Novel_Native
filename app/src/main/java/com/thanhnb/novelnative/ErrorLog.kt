package com.thanhnb.novelnative

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

/**
 * Nhật ký lỗi cục bộ (như AppErrorLog của bản Flutter): bắt crash chưa xử lý, giữ 60 lỗi mới nhất
 * trong SharedPreferences để xem ở Quản trị → Nhật ký lỗi. Chỉ lưu máy này, không gửi về server.
 */
object AppErrorLog {
    private const val KEY = "app_error_log"
    private const val MAX = 60

    /** Mới nhất trước; Compose đọc được để tự vẽ lại. */
    var entries by mutableStateOf<List<JSONObject>>(emptyList())
        private set

    fun install() {
        entries = runCatching {
            JSONArray(Prefs.sp.getString(KEY, "[]")).let { a -> List(a.length()) { a.getJSONObject(it) } }
        }.getOrDefault(emptyList())
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            add(e) // tiến trình sắp chết: add() ghi đồng bộ (commit)
            prev?.uncaughtException(t, e)
        }
    }

    fun add(e: Throwable) {
        val stack = e.stackTraceToString().lines().take(8).joinToString("\n")
        val item = JSONObject().put("time", Instant.now().toString()).put("message", e.toString()).put("stack", stack)
        val list = (listOf(item) + entries).take(MAX)
        entries = list
        Prefs.sp.edit().putString(KEY, JSONArray(list).toString()).commit()
    }

    fun clear() {
        entries = emptyList()
        Prefs.sp.edit().remove(KEY).apply()
    }
}
