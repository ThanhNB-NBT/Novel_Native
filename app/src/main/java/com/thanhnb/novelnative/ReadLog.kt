package com.thanhnb.novelnative

import java.time.LocalDate

/**
 * Nhật ký đọc theo ngày, chỉ lưu trên máy: "yyyy-MM-dd=n;..." + tập "truyện:chương" đã tính của hôm nay (để mở lại cùng chương không đếm đôi).
 * shortcut: chỉ ghi trên máy từ bản này, server không lưu từng ngày; cần lịch sử đa thiết bị thì thêm bảng server
 */
object ReadLog {
    private const val DAYS = "readlog_days"
    private const val SEEN_DAY = "readlog_seen_day"
    private const val SEEN = "readlog_seen"
    private const val KEEP_DAYS = 400L

    private fun read(): MutableMap<String, Int> = Prefs.sp.getString(DAYS, null).orEmpty().split(';').mapNotNull {
        val p = it.split('=')
        p.getOrNull(1)?.toIntOrNull()?.let { n -> p[0] to n }
    }.toMap().toMutableMap()

    /** Gọi từ luồng nào cũng được; Prefs chưa khởi tạo thì bỏ qua. */
    @Synchronized
    fun bump(novelId: Int, chapter: Int) {
        runCatching {
            val sp = Prefs.sp
            val day = LocalDate.now().toString()
            val key = "$novelId:$chapter"
            val seen = if (sp.getString(SEEN_DAY, null) == day) sp.getStringSet(SEEN, null).orEmpty() else emptySet()
            if (key in seen) return@runCatching
            val m = read()
            m[day] = (m[day] ?: 0) + 1
            val cutoff = LocalDate.now().minusDays(KEEP_DAYS).toString() // chuỗi ISO so sánh từ điển được
            sp.edit().putString(DAYS, m.filterKeys { it >= cutoff }.entries.joinToString(";") { "${it.key}=${it.value}" })
                .putString(SEEN_DAY, day).putStringSet(SEEN, seen + key).apply()
        }
    }

    @Synchronized
    fun days(): Map<LocalDate, Int> = runCatching { read().mapKeys { LocalDate.parse(it.key) } }.getOrDefault(emptyMap())

    fun today(): Int = days()[LocalDate.now()] ?: 0
}
