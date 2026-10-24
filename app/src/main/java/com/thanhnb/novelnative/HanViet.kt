package com.thanhnb.novelnative

import android.content.Context

/**
 * Tra âm Hán-Việt — bảng assets/hanviet.tsv (chép từ bản Flutter, cùng nguồn với worker).
 * Dùng để gợi ý cách đọc tên riêng khi sửa thuật ngữ / sửa bản dịch.
 */
object HanViet {
    private var table: Map<String, List<String>>? = null

    /** Chữ đa âm mà âm ưu tiên trong TÊN RIÊNG khác âm đầu bảng — khớp worker hanviet._PREFERRED. */
    private val preferred = mapOf(
        "宁" to "ninh", "曾" to "tăng", "任" to "nhâm", "燕" to "yên", "仇" to "cừu", "区" to "âu",
        "菲" to "phi", "丽" to "ly",
    )

    /** Chữ Hán gồm cả CJK Extension, cùng dải ký tự với bảng tra. */
    val run = Regex("[\\u3400-\\u4DBF\\u4E00-\\u9FFF\\x{20000}-\\x{323AF}]+")

    fun load(ctx: Context) {
        if (table != null) return
        val m = HashMap<String, List<String>>(30000)
        ctx.assets.open("hanviet.tsv").bufferedReader().useLines { lines ->
            for (line in lines) {
                if (line.startsWith("#")) continue
                val tab = line.indexOf('\t')
                if (tab <= 0) continue
                val ch = line.substring(0, tab)
                val rs = line.substring(tab + 1).trim().split('|').filter(String::isNotEmpty).toMutableList()
                if (rs.isEmpty()) continue
                preferred[ch]?.let { rs.remove(it); rs.add(0, it) }
                m[ch] = rs
            }
        }
        table = m
    }

    private fun cap(s: String) = s.replaceFirstChar { it.uppercase() }
    private fun chars(s: String) = s.codePoints().toArray().map { String(Character.toChars(it)) }

    /** "猎人" → "Liệp Nhân"; null nếu có chữ ngoài bảng. */
    fun of(zh: String): String? {
        val t = table ?: return null
        if (zh.isEmpty()) return null
        val parts = ArrayList<String>()
        for (ch in chars(zh)) parts += cap(t[ch]?.first() ?: return null)
        return parts.joinToString(" ")
    }

    /** Vài cách đọc khả dĩ cho chữ đa âm (tổ hợp, tối đa [cap]); rỗng nếu có chữ ngoài bảng. */
    fun candidates(zh: String, cap: Int = 4): List<String> {
        val t = table ?: return emptyList()
        if (zh.isEmpty()) return emptyList()
        var combos: List<List<String>> = listOf(emptyList())
        for (ch in chars(zh)) {
            val rs = t[ch] ?: return emptyList()
            val next = ArrayList<List<String>>()
            outer@ for (combo in combos) for (r in rs) {
                next += combo + cap(r)
                if (next.size >= cap * 6) break@outer
            }
            combos = next
        }
        return combos.map { it.joinToString(" ") }.distinct().take(cap)
    }

    /**
     * Đoạn chữ Hán trong [zhLine] mà âm Hán-Việt (bỏ dấu, xét mọi âm) khớp từng âm của [sel] —
     * "Liệp Nhân" → 猎人. Từ thuần Việt ("thợ săn") thì null.
     */
    fun zhSpanFor(sel: String, zhLine: String): String? {
        val t = table ?: return null
        val want = Api.boDau(sel).split(Regex("[^a-z0-9]+")).filter(String::isNotEmpty)
        if (want.isEmpty() || want.size > 8) return null
        val cs = chars(zhLine)
        for (i in 0..cs.size - want.size) {
            if (want.indices.all { k -> t[cs[i + k]]?.any { Api.boDau(it) == want[k] } == true }) return cs.subList(i, i + want.size).joinToString("")
        }
        return null
    }
}
