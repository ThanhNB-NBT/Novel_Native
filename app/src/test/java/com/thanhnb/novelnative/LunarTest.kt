package com.thanhnb.novelnative

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class LunarTest {
    private fun lunar(y: Int, m: Int, d: Int) = toLunar(LocalDate.of(y, m, d)).let { "${it.day}/${it.month}${if (it.leap) "n" else ""}/${it.year}" }

    @Test fun tetDates() {
        assertEquals("1/1/2024", lunar(2024, 2, 10))
        assertEquals("1/1/2025", lunar(2025, 1, 29))
        assertEquals("1/1/2026", lunar(2026, 2, 17))
        assertEquals("29/12/2025", lunar(2026, 2, 16)) // giao thừa: tháng Chạp Ất Tỵ thiếu, không có 30 Tết
    }

    @Test fun vietnamTimezoneNotChina() {
        // Tết Ất Sửu 1985: Việt Nam (UTC+7) 21/1, Trung Quốc (UTC+8) 20/2 — sai múi giờ là lệch cả tháng
        assertEquals("1/1/1985", lunar(1985, 1, 21))
    }

    @Test fun leapMonths() {
        assertEquals("1/2/2023", lunar(2023, 2, 20))
        assertEquals("1/2n/2023", lunar(2023, 3, 22))
        assertEquals("1/6n/2025", lunar(2025, 7, 25))
    }

    @Test fun canChi() {
        assertEquals("Bính Ngọ", toLunar(LocalDate.of(2026, 2, 17)).yearName)
        assertEquals("Mậu Ngọ", toLunar(LocalDate.of(2000, 1, 1)).dayCanChi)
        assertEquals("Tháng Hai nhuận", toLunar(LocalDate.of(2023, 3, 22)).monthName)
    }

    @Test fun specialDays() {
        assertEquals("Tết Nguyên Đán", special(toLunar(LocalDate.of(2026, 2, 17))))
        assertEquals("Tết Trung Thu", special(toLunar(LocalDate.of(2025, 10, 6))))
        assertEquals(null, special(toLunar(LocalDate.of(2023, 3, 22)))) // mùng 1 tháng nhuận không tính
    }

    @Test fun solarTerms() {
        assertEquals("Lập xuân", termStartingOn(LocalDate.of(2026, 2, 4)))
        assertEquals(null, termStartingOn(LocalDate.of(2026, 2, 5)))
        val t = solarTerm(LocalDate.of(2026, 2, 10))
        assertEquals("Lập xuân", t.name)
        assertEquals("Vũ thủy", t.nextName)
        assertTrue(t.progress(LocalDate.of(2026, 2, 10)) in 0.3f..0.5f)
    }

    @Test fun goodHoursAreSixOfTwelve() {
        var d = LocalDate.of(2026, 1, 1)
        repeat(12) { assertEquals(6, goodHours(toLunar(d)).size); d = d.plusDays(1) }
        assertFalse(napAm(toLunar(d)).isBlank())
    }
}
