package com.thanhnb.novelnative

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

/** Màu dị tượng mỗi loại dị căn (mig 159). */
internal val VariantColor = mapOf(
    "kiem" to Color(0xFFDDE6F5), "loi" to Color(0xFFA48CFF), "bang" to Color(0xFF9BE4FF),
    "phong" to Color(0xFF7DE3B4), "am" to Color(0xFFCB7DFF), "thien" to Color(0xFFFFD36B), "hon" to Color(0xFFFF9EDB),
)

/**
 * Chạy màu: dải [cols] (vòng kín, màu cuối nối về màu đầu) trượt theo đồng hồ [t] — 2 vòng / 8 giây.
 * [ph] 0..1 là vị trí trên hoạ tiết, nên màu chạy dọc vòng chứ không đổi đồng loạt.
 */
internal fun flowColor(cols: List<Color>, t: Float, ph: Float = 0f): Color {
    if (cols.size == 1) return cols[0]
    val u = ((ph - t * 2) % 1f + 1f) % 1f * cols.size
    val i = u.toInt() % cols.size
    return lerp(cols[i], cols[(i + 1) % cols.size], u - floor(u))
}

private fun hash(i: Int) = (sin(i * 12.9898f) * 43758.547f).let { it - floor(it) }

/**
 * Dị tượng sau lưng nhân vật, mỗi loại dị căn một hoạ tiết. [t] là đồng hồ 0..1 (8 giây) của HeroStage —
 * mọi chuyển động dùng bội số nguyên của t nên vòng lặp liền mạch. [c] tâm, [r] bán kính vùng vẽ.
 */
internal fun DrawScope.drawVariantAura(v: String, c: Offset, r: Float, t: Float, custom: List<Color>? = null) {
    val cols = custom ?: listOf(VariantColor[v] ?: return)
    fun k(ph: Float) = flowColor(cols, t, ph)
    drawCircle(Brush.radialGradient(listOf(k(0f).copy(.28f), Color.Transparent), c, r), r, c)
    val px = r / 120f
    when (v) {
        // Kiếm: 8 đạo kiếm khí xoay vòng elip quanh người
        "kiem" -> repeat(8) { i ->
            val a = (t * 2 + i / 8f) * TAU
            val p = Offset(c.x + cos(a) * r * .95f, c.y + sin(a) * r * .38f)
            val tail = Offset(sin(a) * r * .3f, -cos(a) * r * .38f * .3f)
            val back = sin(a) < 0
            val col = k(i / 8f)
            drawLine(Brush.linearGradient(listOf(Color.Transparent, col.copy(if (back) .35f else .9f)), p + tail, p), p + tail, p, 3.2f * px, StrokeCap.Round)
            drawCircle(Color.White.copy(if (back) .3f else .85f), 2.2f * px, p)
        }
        // Lôi: tia sét chớp giật quanh người, đổi hình 32 lần mỗi chu kỳ
        "loi" -> {
            val frame = floor(t * 32).toInt()
            repeat(3) { k ->
                val seed = frame * 7 + k * 131
                if (hash(seed) < .55f) {
                    val a0 = hash(seed + 1) * TAU
                    val path = Path()
                    var p = Offset(c.x + cos(a0) * r * .35f, c.y + sin(a0) * r * .35f)
                    path.moveTo(p.x, p.y)
                    repeat(6) { s ->
                        val d = r * .11f
                        val a = a0 + (hash(seed + 10 + s) - .5f) * 1.4f
                        p = Offset(p.x + cos(a) * d, p.y + sin(a) * d)
                        path.lineTo(p.x, p.y)
                    }
                    drawPath(path, k(hash(seed + 3)).copy(.35f), style = Stroke(7f * px, cap = StrokeCap.Round))
                    drawPath(path, Color.White.copy(.9f), style = Stroke(1.8f * px, cap = StrokeCap.Round))
                }
            }
        }
        // Băng: tinh thể sáu cánh rơi chậm và xoay
        "bang" -> repeat(14) { i ->
            val ph = (t + hash(i)) % 1f
            val x = c.x + (hash(i + 50) - .5f) * r * 2f + sin((ph + i) * TAU) * 6 * px
            val y = c.y - r + ph * r * 2f
            val a = abs(sin(ph * PI.toFloat()))
            val s = (3f + hash(i + 90) * 4f) * px
            rotate((t * 360 + i * 40), Offset(x, y)) {
                repeat(3) { k ->
                    val ang = k * PI.toFloat() / 3
                    drawLine(k(hash(i + 7)).copy(.85f * a), Offset(x - cos(ang) * s, y - sin(ang) * s), Offset(x + cos(ang) * s, y + sin(ang) * s), 1.4f * px, StrokeCap.Round)
                }
            }
        }
        // Phong: ba dải gió xoáy quanh người
        "phong" -> repeat(3) { i ->
            val rr = r * (.55f + i * .17f)
            val start = (t * (3 - i) + i / 3f) * 360f
            drawArc(
                Brush.sweepGradient(listOf(Color.Transparent, k(i / 3f).copy(.75f), k(i / 3f + .3f).copy(.75f), Color.Transparent), c), start, 130f, false,
                Offset(c.x - rr, c.y - rr * .45f), Size(rr * 2, rr * .9f), style = Stroke((4f - i) * px, cap = StrokeCap.Round),
            )
        }
        // Huyễn: vòng ảo ảnh lan ra rồi tan, kèm đốm sáng chập chờn
        "am" -> {
            repeat(3) { i ->
                val ph = (t * 2 + i / 3f) % 1f
                val rr = r * (.3f + ph * .75f)
                drawOval(k(i / 3f).copy(.55f * (1 - ph)), Offset(c.x - rr, c.y - rr * .5f), Size(rr * 2, rr), style = Stroke(2.2f * px))
            }
            repeat(10) { i ->
                val a = hash(i) * TAU + t * TAU
                val blink = abs(sin((t * 3 + hash(i + 20)) * PI.toFloat()))
                drawCircle(k(hash(i + 40)).copy(.8f * blink), 2.6f * px, Offset(c.x + cos(a) * r * .8f, c.y + sin(a) * r * .5f))
            }
        }
        // Thiên / Hỗn Độn: hào quang toả tia xoay chậm
        else -> rotate(t * 45f, c) {
            repeat(12) { i ->
                val a = i / 12f * TAU
                val len = r * (.75f + .2f * sin((t * 2 + i / 12f) * TAU))
                val tint = if (v == "hon" && custom == null) qiColorsAll[i % qiColorsAll.size] else k(i / 12f)
                drawLine(Brush.linearGradient(listOf(tint.copy(.55f), Color.Transparent), c, c + Offset(cos(a) * len, sin(a) * len)), c, c + Offset(cos(a) * len, sin(a) * len), 5f * px, StrokeCap.Round)
            }
        }
    }
}

/** Bảng màu cho người chơi tự chỉnh dị tượng / trận pháp (lưu trên máy, Prefs). Xếp theo vòng sắc độ. */
internal val AuraSwatches = listOf(
    0xFFCB7DFF, 0xFF9B5CFF, 0xFF6A4CFF, 0xFFE07CFF, 0xFFFF7EB6, 0xFFFF4F8B, 0xFFFF5A5A, 0xFFC92A2A,
    0xFFFF9A3C, 0xFFFFB86B, 0xFFFFD36B, 0xFFF2E27A, 0xFFB8F06A, 0xFF7DE3B4, 0xFF2BC48A, 0xFF3ED6D0,
    0xFF9BE4FF, 0xFF4FC3FF, 0xFF5B8CFF, 0xFF2F55D4, 0xFFFFFFFF, 0xFFDDE6F5, 0xFFB0A89A, 0xFF3A3A4A,
).map { Color(it) }

/** Dải màu chạy dựng sẵn (Prefs.auraGrad / formGrad = chỉ số + 1). */
internal val AuraGradients = listOf(
    "Cầu vồng" to listOf(0xFFFF5A5A, 0xFFFF9A3C, 0xFFFFD36B, 0xFF7DE3B4, 0xFF4FC3FF, 0xFF9B5CFF),
    "Lửa băng" to listOf(0xFFFF5A5A, 0xFFFFB86B, 0xFF9BE4FF, 0xFF4FC3FF),
    "Hoàng hôn" to listOf(0xFFFF4F8B, 0xFFFF9A3C, 0xFFFFD36B),
    "Cực quang" to listOf(0xFF2BC48A, 0xFF3ED6D0, 0xFF9B5CFF),
    "Đại dương" to listOf(0xFF2F55D4, 0xFF4FC3FF, 0xFF3ED6D0),
    "Tinh hà" to listOf(0xFF2F55D4, 0xFF9B5CFF, 0xFFFFFFFF),
    "Hoa đào" to listOf(0xFFFF7EB6, 0xFFFFFFFF, 0xFFE07CFF),
    "Tử kim" to listOf(0xFF9B5CFF, 0xFFFFD36B),
    "Lục bảo" to listOf(0xFF2BC48A, 0xFFB8F06A, 0xFFF2E27A),
    "Huyết nguyệt" to listOf(0xFFC92A2A, 0xFF3A3A4A, 0xFFFF5A5A),
    "Ma diễm" to listOf(0xFF6A4CFF, 0xFFFF4F8B, 0xFF3A3A4A),
    "Bạch ngọc" to listOf(0xFFFFFFFF, 0xFFDDE6F5, 0xFF9BE4FF),
).map { (n, c) -> n to c.map(::Color) } + ("Ngũ hành" to listOf("kim", "moc", "thuy", "hoa", "tho").map { elemColor(it) })

/** Màu người chơi chọn → dải để vẽ: dải dựng sẵn > (màu chính, màu phụ); null = để mặc định. */
internal fun fxColors(c1: Int, c2: Int, grad: Int, def: Color): List<Color>? = when {
    grad in 1..AuraGradients.size -> AuraGradients[grad - 1].second
    c1 == 0 && c2 == 0 -> null
    else -> listOfNotNull(if (c1 != 0) Color(c1) else def, if (c2 != 0) Color(c2) else null)
}

private val qiColorsAll = listOf("kim", "moc", "thuy", "hoa", "tho").map { elemColor(it) }
