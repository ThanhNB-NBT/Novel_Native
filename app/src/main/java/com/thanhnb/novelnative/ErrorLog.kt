package com.thanhnb.novelnative

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

/**
 * Nhật ký lỗi (như AppErrorLog của bản Flutter): bắt crash chưa xử lý, giữ 60 lỗi mới nhất trong SharedPreferences
 * để xem ở Quản trị → Nhật ký lỗi. Bản release còn gửi lỗi về server (RPC log_client_error, server giới hạn 30 lỗi/giờ/user)
 * để admin thấy lỗi trên máy người khác. Crash làm tiến trình chết trước khi gửi kịp → lỗi chưa gửi được gửi bù ở lần mở sau.
 */
object AppErrorLog {
    private const val KEY = "app_error_log"
    private const val MAX = 60
    private val io = CoroutineScope(Dispatchers.IO)

    /** Mới nhất trước; Compose đọc được để tự vẽ lại. */
    var entries by mutableStateOf<List<JSONObject>>(emptyList())
        private set

    fun install() {
        entries = runCatching {
            JSONArray(Prefs.sp.getString(KEY, "[]")).let { a -> List(a.length()) { a.getJSONObject(it) } }
        }.getOrDefault(emptyList())
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            add(e) // tiến trình sắp chết: add() ghi đồng bộ (commit), không gửi được ngay
            prev?.uncaughtException(t, e)
        }
        uploadPending()
    }

    fun add(e: Throwable) {
        val stack = e.stackTraceToString().lines().take(8).joinToString("\n")
        val item = JSONObject().put("time", Instant.now().toString()).put("message", e.toString()).put("stack", stack).put("up", false)
        entries = (listOf(item) + entries).take(MAX)
        persist()
    }

    fun clear() {
        entries = emptyList()
        Prefs.sp.edit().remove(KEY).apply()
    }

    private fun persist() { Prefs.sp.edit().putString(KEY, JSONArray(entries).toString()).commit() }

    /** Gửi các lỗi chưa gửi (tối đa 5 mới nhất mỗi lần mở) — chỉ bản release và khi đã đăng nhập. */
    private fun uploadPending() {
        if (BuildConfig.DEBUG) return
        io.launch {
            var last: String? = null
            for (e in entries.filter { !it.optBoolean("up") }.take(5)) {
                if (Session.user == null) return@launch
                val msg = e.optString("message")
                if (msg != last) {
                    runCatching {
                        Api.rpc("log_client_error", "p_message" to msg, "p_stack" to e.optString("stack"), "p_version" to BuildConfig.VERSION_NAME, "p_platform" to "android")
                    }.onFailure { return@launch } // mất mạng: để lần sau
                }
                last = msg
                e.put("up", true)
            }
            persist()
        }
    }
}
