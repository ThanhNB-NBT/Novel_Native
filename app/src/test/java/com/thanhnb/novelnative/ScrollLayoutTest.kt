package com.thanhnb.novelnative

import org.junit.Assert.assertEquals
import org.junit.Test

/** Thứ tự item ở chế độ cuộn — chỗ lệch một chỉ số là sang chương sớm (lỗi v0.10.0). */
class ScrollLayoutTest {
    private fun ch(n: Int) = Chapter(1, "", "done", List(n) { "đoạn $it" })

    @Test fun leadCountsPreviewParasPlusSeparator() {
        assertEquals(0, leadOf(null))
        assertEquals(6, leadOf(ch(5)))
        assertEquals(PREVIEW_PARAS + 1, leadOf(ch(200))) // chương trước dài chỉ xem trước PREVIEW_PARAS đoạn
    }

    @Test fun nextTitleSitsAfterTitleParasAndEndMark() {
        // [tiêu đề 0] [đoạn 1..40] [vạch 41] → tiêu đề chương sau ở 42
        assertEquals(42, nextTitleIndex(null, ch(40)))
    }

    @Test fun nextTitleShiftsWhenPrevArrivesLate() {
        // chương trước tải xong sau → mọi chỉ số dịch xuống `lead`; mốc cũ (42) sẽ rơi giữa chương hiện tại
        val late = nextTitleIndex(ch(200), ch(40))
        assertEquals(42 + PREVIEW_PARAS + 1, late)
    }
}
