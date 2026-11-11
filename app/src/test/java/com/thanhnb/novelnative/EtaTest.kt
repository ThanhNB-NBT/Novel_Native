package com.thanhnb.novelnative

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EtaTest {
    // PostgREST trả timestamptz dạng +00:00 và micro giây — Instant.parse không đọc được, OffsetDateTime thì có
    private fun at(sec: Int) = "2026-11-10T03:%02d:%02d.123456+00:00".format(sec / 60, sec % 60)

    @Test fun medianIgnoresIdleGap() {
        // 4 chương cách nhau 40s, trước đó worker nghỉ 30 phút → nhịp vẫn là 40s
        assertEquals(40L, medianGapSec(listOf(at(1920), at(1880), at(1840), at(1800), at(0))))
    }

    @Test fun tooFewSamplesGivesNoEstimate() {
        assertNull(medianGapSec(listOf(at(80), at(40), at(0))))
        assertNull(medianGapSec(listOf("rác", "")))
    }

    @Test fun etaText() {
        assertEquals("Sắp tới lượt", etaText(0, null))
        assertEquals("Còn 5 chương phía trước", etaText(5, null))
        assertEquals("Sắp tới lượt · khoảng 1 phút", etaText(0, 40))
        assertEquals("Còn 5 chương phía trước · khoảng 4 phút", etaText(5, 40))
        assertEquals("Còn 199 chương phía trước · khoảng 2 giờ", etaText(199, 40))
    }

    @Test fun chapterPosPicksNearest() {
        val toc = listOf(1, 2, 5, 6).map { ChapterRef(it, "", true) }
        assertEquals(1, chapterPos(toc, 2))
        assertEquals(2, chapterPos(toc, 3)) // thiếu chương 3 → chương kế sau
        assertEquals(3, chapterPos(toc, 99)) // quá cuối → chương cuối
        assertEquals(0, chapterPos(emptyList(), 5))
    }
}
