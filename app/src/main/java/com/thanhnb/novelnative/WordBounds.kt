package com.thanhnb.novelnative

/** Ranh giới từ cho "nhấn giữ một từ để sửa" (port từ reader_text.dart): dấu ngoặc, nháy, phẩy, hai chấm… không thuộc từ. */
object WordBounds {
    fun isWordChar(s: String, i: Int): Boolean {
        val c = s[i].code
        return c in 0x30..0x39 || c in 0x41..0x5a || c in 0x61..0x7a || c in 0x00c0..0x024f || c in 0x1e00..0x1eff || c in 0x3400..0x9fff || c == 0x5f
    }

    private fun wordIndex(s: String, offset: Int): Int {
        var i = offset.coerceIn(0, s.length)
        if (i < s.length && isWordChar(s, i)) return i
        if (i > 0 && isWordChar(s, i - 1)) return i - 1
        while (i < s.length && !isWordChar(s, i)) i++
        return if (i == s.length) -1 else i
    }

    fun wordLeft(s: String, offset: Int): Int {
        var i = wordIndex(s, offset)
        if (i < 0) return s.length
        while (i > 0 && isWordChar(s, i - 1)) i--
        return i
    }

    fun wordRight(s: String, offset: Int): Int {
        var i = wordIndex(s, offset)
        if (i < 0) return s.length
        while (i < s.length && isWordChar(s, i)) i++
        return i
    }

    private fun gap(s: String, i: Int) = s[i] == ' ' || s[i] == '\t'

    /** Nới sang PHẢI một từ, chỉ khi cách bởi khoảng trắng; gặp dấu câu thì dừng (không nuốt dấu vào vùng sửa). */
    fun extendRight(s: String, end: Int): Int {
        var j = end
        while (j < s.length && gap(s, j)) j++
        if (j >= s.length || !isWordChar(s, j)) return end
        while (j < s.length && isWordChar(s, j)) j++
        return j
    }

    fun extendLeft(s: String, start: Int): Int {
        var j = start
        while (j > 0 && gap(s, j - 1)) j--
        if (j <= 0 || !isWordChar(s, j - 1)) return start
        while (j > 0 && isWordChar(s, j - 1)) j--
        return j
    }

    /** Trúng một âm tiết VIẾT HOA (tên riêng Hán-Việt "Trần Đại Chinh") → lấy trọn cụm viết hoa liền nhau; từ thường giữ 1 từ. */
    fun nameRun(s: String, a: Int, b: Int): Pair<Int, Int> {
        fun cap(i: Int) = i in s.indices && s[i].isUpperCase()
        if (!cap(a)) return a to b
        var start = a
        var end = b
        while (true) { val na = extendLeft(s, start); if (na == start || !cap(na)) break; start = na }
        while (true) {
            val nb = extendRight(s, end)
            if (nb == end) break
            var w = end
            while (w < nb && !isWordChar(s, w)) w++
            if (!cap(w)) break
            end = nb
        }
        return start to end
    }

    /** Vùng chọn khi nhấn giữ tại [offset]: từ dưới ngón tay (hoặc trọn tên riêng); null nếu chạm chỗ trống. */
    fun selectAt(s: String, offset: Int): Pair<Int, Int>? {
        val a = wordLeft(s, offset)
        val b = wordRight(s, offset)
        if (b <= a) return null
        return nameRun(s, a, b)
    }
}
