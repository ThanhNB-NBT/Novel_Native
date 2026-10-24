package com.thanhnb.novelnative

import java.time.LocalDate

/**
 * Thông tin ngoài ngày tháng: tiết khí, giờ/ngày hoàng đạo, nạp âm, lễ.
 * Tiết khí tính từ kinh độ Mặt Trời (cùng thuật toán Hồ Ngọc Đức, múi giờ +7); các bảng còn lại là bảng tra cổ truyền.
 */

private val TERMS = listOf(
    "Xuân phân", "Thanh minh", "Cốc vũ", "Lập hạ", "Tiểu mãn", "Mang chủng", "Hạ chí", "Tiểu thử", "Đại thử", "Lập thu", "Xử thử", "Bạch lộ",
    "Thu phân", "Hàn lộ", "Sương giáng", "Lập đông", "Tiểu tuyết", "Đại tuyết", "Đông chí", "Tiểu hàn", "Đại hàn", "Lập xuân", "Vũ thủy", "Kinh trập",
) // chỉ số i ứng với kinh độ Mặt Trời 15° × i

/** Tiết khí của một ngày: tiết đang diễn ra, ngày bắt đầu của nó, và tiết kế tiếp. */
class SolarTerm(val name: String, val start: LocalDate, val nextName: String, val next: LocalDate) {
    /** Tiến độ từ tiết này sang tiết kế tiếp, 0..1. */
    fun progress(d: LocalDate): Float {
        val total = (next.toEpochDay() - start.toEpochDay()).coerceAtLeast(1)
        return ((d.toEpochDay() - start.toEpochDay()).toFloat() / total).coerceIn(0f, 1f)
    }
}

/** Tên tiết khí nếu [d] là ngày tiết bắt đầu, ngược lại null. */
fun termStartingOn(d: LocalDate): String? {
    val jd = toLunar(d).jd
    val a = sunTerm15(jd)
    val b = sunTerm15(jd + 1)
    return if (a != b) TERMS[b] else null
}

fun solarTerm(d: LocalDate): SolarTerm {
    var s = d
    while (termStartingOn(s) == null) s = s.minusDays(1)
    var n = d.plusDays(1)
    while (termStartingOn(n) == null) n = n.plusDays(1)
    return SolarTerm(termStartingOn(s)!!, s, termStartingOn(n)!!, n)
}

// --- Hoàng đạo ---

private val GOOD_HOURS = listOf( // theo nhóm chi của ngày: Tý/Ngọ, Sửu/Mùi, Dần/Thân, Mão/Dậu, Thìn/Tuất, Tỵ/Hợi
    setOf(0, 1, 3, 6, 8, 9),
    setOf(2, 3, 5, 8, 10, 11),
    setOf(0, 1, 4, 5, 7, 10),
    setOf(0, 2, 3, 6, 7, 9),
    setOf(2, 4, 5, 8, 9, 11),
    setOf(1, 4, 6, 7, 10, 11),
)

/** 12 canh giờ: tên chi + khung giờ. */
val HOURS = listOf(
    "Tý" to "23–1", "Sửu" to "1–3", "Dần" to "3–5", "Mão" to "5–7", "Thìn" to "7–9", "Tỵ" to "9–11",
    "Ngọ" to "11–13", "Mùi" to "13–15", "Thân" to "15–17", "Dậu" to "17–19", "Tuất" to "19–21", "Hợi" to "21–23",
)

private fun dayChi(l: LunarDate) = (l.jd + 1) % 12

/** Chỉ số (0..11, 0 = Tý) các canh giờ hoàng đạo của ngày. */
fun goodHours(l: LunarDate): Set<Int> = GOOD_HOURS[dayChi(l) % 6]

/** Ngày hoàng đạo theo tháng âm: sáu chi cách khởi điểm 0,1,4,5,7,10 (tháng Giêng/Bảy khởi từ Tý, mỗi tháng sau dời hai chi). */
fun isGoodDay(l: LunarDate): Boolean {
    val start = 2 * ((l.month - 1) % 6)
    return setOf(0, 1, 4, 5, 7, 10).any { (start + it) % 12 == dayChi(l) }
}

// --- Nạp âm ---

private val NAP_AM = listOf(
    "Hải trung kim", "Lô trung hỏa", "Đại lâm mộc", "Lộ bàng thổ", "Kiếm phong kim", "Sơn đầu hỏa",
    "Giản hạ thủy", "Thành đầu thổ", "Bạch lạp kim", "Dương liễu mộc", "Tuyền trung thủy", "Ốc thượng thổ",
    "Tích lịch hỏa", "Tùng bách mộc", "Trường lưu thủy", "Sa trung kim", "Sơn hạ hỏa", "Bình địa mộc",
    "Bích thượng thổ", "Kim bạch kim", "Phúc đăng hỏa", "Thiên hà thủy", "Đại trạch thổ", "Thoa xuyến kim",
    "Tang đố mộc", "Đại khê thủy", "Sa trung thổ", "Thiên thượng hỏa", "Thạch lựu mộc", "Đại hải thủy",
)

/** Nạp âm của ngày (60 Giáp Tý, mỗi cặp can chi chung một nạp âm). */
fun napAm(l: LunarDate): String = NAP_AM[((l.jd + 49) % 60) / 2]

/** Hành của nạp âm: kim / mộc / thủy / hỏa / thổ. */
fun napAmElement(l: LunarDate): String = napAm(l).substringAfterLast(' ')

// --- Lễ ---

class DayEvent(val name: String, val lunar: Boolean)

private val SOLAR_EVENTS = mapOf(
    (1 to 1) to "Tết Dương lịch", (14 to 2) to "Valentine", (8 to 3) to "Quốc tế Phụ nữ", (30 to 4) to "Giải phóng miền Nam",
    (1 to 5) to "Quốc tế Lao động", (1 to 6) to "Quốc tế Thiếu nhi", (2 to 9) to "Quốc khánh", (20 to 10) to "Ngày Phụ nữ Việt Nam",
    (20 to 11) to "Ngày Nhà giáo Việt Nam", (22 to 12) to "Quân đội Nhân dân Việt Nam", (24 to 12) to "Đêm Giáng sinh", (25 to 12) to "Giáng sinh",
)

private val LUNAR_EVENTS = mapOf(
    (1 to 1) to "Tết Nguyên Đán", (2 to 1) to "Mùng 2 Tết", (3 to 1) to "Mùng 3 Tết", (15 to 1) to "Rằm tháng Giêng",
    (3 to 3) to "Tết Hàn thực", (10 to 3) to "Giỗ Tổ Hùng Vương", (15 to 4) to "Lễ Phật Đản", (5 to 5) to "Tết Đoan Ngọ",
    (15 to 7) to "Rằm tháng Bảy · Vu Lan", (15 to 8) to "Tết Trung Thu", (9 to 9) to "Tết Trùng Cửu", (23 to 12) to "Ông Táo chầu trời",
)

/** Các dịp của ngày: lễ âm lịch trước, rồi lễ dương lịch; mùng 1 / rằm chỉ báo khi không trùng lễ nào. */
fun events(d: LocalDate, l: LunarDate): List<DayEvent> {
    val out = ArrayList<DayEvent>()
    if (!l.leap) {
        LUNAR_EVENTS[l.day to l.month]?.let { out += DayEvent(it, true) }
        if (l.month == 12 && toLunar(d.plusDays(1)).let { it.day == 1 && it.month == 1 }) out += DayEvent("Giao thừa", true)
        if (out.isEmpty()) when (l.day) {
            1 -> out += DayEvent("Mùng 1", true)
            15 -> out += DayEvent("Ngày Rằm", true)
        }
    }
    SOLAR_EVENTS[d.dayOfMonth to d.monthValue]?.let { out += DayEvent(it, false) }
    return out
}
