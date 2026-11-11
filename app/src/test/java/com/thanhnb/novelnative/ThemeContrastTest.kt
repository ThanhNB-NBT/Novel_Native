package com.thanhnb.novelnative

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import org.junit.Assert.assertTrue
import org.junit.Test

/** Quét mọi tổ hợp (màu nhấn × màu giấy × sáng/tối) người dùng chọn được: chữ ≥ 4.5:1, icon/vạch ≥ 3:1 (WCAG AA). */
class ThemeContrastTest {
    @Test fun everyPaletteIsReadable() {
        val fails = mutableListOf<String>()
        for (a in accents) for (p in papers) for (dark in listOf(false, true)) {
            val (cs, seal) = schemeFor(a, p, dark)
            val card = cs.surfaceContainerLow
            val sealPaper = if (dark) cs.background else Color.White // chữ trong triện, như Seal() ở Common.kt
            listOf(
                Triple("chữ/nền", cs.onBackground to cs.background, 7f),
                Triple("chữ phụ/nền", cs.onSurfaceVariant to cs.background, 4.5f),
                Triple("chữ phụ/thẻ", cs.onSurfaceVariant to card, 4.5f),
                Triple("chữ phụ/nền chìm", cs.onSurfaceVariant to cs.surfaceVariant, 4.5f),
                Triple("chữ trên nút", cs.onPrimary to cs.primary, 4.5f),
                Triple("nhấn/nền", cs.primary to cs.background, 4.5f),
                Triple("nhấn/thẻ", cs.primary to card, 4.5f),
                Triple("nhấn/viên thể loại", cs.primary to cs.primary.copy(.14f).compositeOver(cs.background), 4.5f),
                Triple("nhấn/tab đang chọn", cs.primary to cs.primaryContainer, 4.5f),
                Triple("chữ/nền nhấn nhạt", cs.onPrimaryContainer to cs.primaryContainer, 4.5f),
                Triple("lỗi/thẻ", cs.error to card, 4.5f),
                Triple("lục/thẻ", cs.tertiary to card, 4.5f),
                Triple("cam/thẻ", cs.secondary to card, 4.5f),
                Triple("triện/thẻ", seal to card, 3f),
                Triple("chữ trong triện", sealPaper to seal, 3f),
            ).forEach { (name, pair, min) ->
                val c = contrast(pair.first, pair.second)
                if (c < min) fails += "%-20s %-13s %-10s %-4s %.2f < %.1f".format(name, a.name, p.name, if (dark) "tối" else "sáng", c, min)
            }
        }
        fails.groupBy { it.take(20).trim() }.forEach { (k, v) -> println("$k: ${v.size} tổ hợp, vd: ${v.first().drop(20)}") }
        assertTrue("${fails.size} cặp màu không đạt:\n" + fails.take(40).joinToString("\n"), fails.isEmpty())
    }
}
