package com.thanhnb.novelnative

import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import android.util.Log
import android.view.HapticFeedbackConstants
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.foundation.Canvas as ComposeCanvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import org.json.JSONObject
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

// Hộp hiệu ứng lên tầng / đột phá / Phi Thăng — port 1:1 từ Flutter (dialog_advance.dart, painters_fx.dart,
// painters_lightning.dart, painters_qi.dart, shaders/*.frag). Mọi painter vẽ trên Canvas Android đã scale theo
// density nên toạ độ là dp = đúng số "logical px" bên Flutter, chép được nguyên hằng số.
// Shader AGSL cần Android 13+; máy cũ hơn rơi về mây/khói vẽ Canvas như bản Flutter khi shader lỗi.

private const val ResultAt = .86f // advanceResultStart
internal val StrikeAt = floatArrayOf(.38f, .56f, .74f) // tribulationHits
private const val CloudEnd = .18f
private const val TammaSec = 2.2f
internal const val TAU = (2 * PI).toFloat()
internal const val PIf = PI.toFloat()

// ---------------- màu / paint / easing ----------------

internal const val WHITE = 0xFFFFFFFF.toInt()
internal const val CLEAR = 0x00000000
internal fun Int.a(x: Float): Int = (this and 0xFFFFFF) or ((x.coerceIn(0f, 1f) * 255f).roundToInt() shl 24)
internal fun mix(a: Int, b: Int, t: Float): Int {
    fun ch(s: Int) = (((a shr s) and 255) + ((((b shr s) and 255) - ((a shr s) and 255)) * t)).roundToInt().coerceIn(0, 255) shl s
    return ch(24) or ch(16) or ch(8) or ch(0)
}

private val AddMode = PorterDuffXfermode(PorterDuff.Mode.ADD)

/** Paint kiểu Flutter: [blur] là sigma MaskFilter.blur (Android nhận bán kính ≈ sigma·√3). */
internal fun pt(
    color: Int = WHITE, shader: Shader? = null, stroke: Float = 0f, add: Boolean = false,
    blur: Float = 0f, round: Boolean = false, roundJoin: Boolean = false,
) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    this.color = color
    this.shader = shader
    if (stroke > 0f) { style = Paint.Style.STROKE; strokeWidth = stroke }
    if (add) xfermode = AddMode
    if (blur > 0f) maskFilter = BlurMaskFilter(blur * 1.73f, BlurMaskFilter.Blur.NORMAL)
    if (round) strokeCap = Paint.Cap.ROUND
    if (roundJoin) strokeJoin = Paint.Join.ROUND
}

internal fun radial(cx: Float, cy: Float, r: Float, colors: IntArray, stops: FloatArray? = null) =
    RadialGradient(cx, cy, max(r, .01f), colors, stops, Shader.TileMode.CLAMP)

internal fun vertical(top: Float, bottom: Float, colors: IntArray, stops: FloatArray? = null) =
    LinearGradient(0f, top, 0f, max(bottom, top + .01f), colors, stops, Shader.TileMode.CLAMP)

private val EaseInOut = CubicBezierEasing(.42f, 0f, .58f, 1f)
private val EaseOut = CubicBezierEasing(0f, 0f, .58f, 1f)
internal val EaseIn = CubicBezierEasing(.42f, 0f, 1f, 1f)
internal val EaseOutCubic = CubicBezierEasing(.215f, .61f, .355f, 1f)
internal val EaseOutBack = CubicBezierEasing(.175f, .885f, .32f, 1.275f)
internal fun Easing.at(x: Float) = transform(x.coerceIn(0f, 1f))

/** Curves.elasticOut (chu kỳ 0,4) của Flutter. */
private fun elasticOut(x: Float): Float {
    if (x <= 0f || x >= 1f) return x.coerceIn(0f, 1f)
    return (2.0.pow(-10.0 * x) * sin((x - .1) * 2 * PI / .4) + 1).toFloat()
}

internal fun Path.poly(pts: List<Offset>, close: Boolean = false) = apply {
    pts.forEachIndexed { i, p -> if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y) }
    if (close) close()
}

internal fun Canvas.oval(cx: Float, cy: Float, w: Float, h: Float, p: Paint) = drawOval(RectF(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2), p)

/** Vẽ trên Canvas Android theo dp (khớp đơn vị Flutter): [block] nhận (canvas, rộng dp, cao dp). */
private fun DrawScope.dpCanvas(block: Canvas.(Float, Float) -> Unit) = drawIntoCanvas { c ->
    val cv = c.nativeCanvas
    cv.save()
    cv.scale(density, density)
    cv.block(size.width / density, size.height / density)
    cv.restore()
}

// ---------------- nền điện ảnh (MajorBackdropPainter) ----------------

private data class RayLayer(val n: Int, val spin: Float, val a0: Float, val w: Float)

private fun Canvas.backdrop(W: Float, H: Float, t: Float, time: Float, color: Int, ok: Boolean) {
    drawRect(0f, 0f, W, H, pt(shader = vertical(0f, H, intArrayOf(0xFF03050B.toInt().a(.96f), 0xFF0A1020.toInt().a(.96f), 0xFF05070E.toInt().a(.97f)))))
    val cx = W / 2
    val cy = H * .42f
    val s = min(W, H)
    val e = EaseOut.at((t - ResultAt) / .1f)
    if (e > 0f) {
        val glow = if (ok) mix(color, WHITE, .25f) else 0xFFC92A2A.toInt()
        drawCircle(cx, cy, s * 1.1f, pt(shader = radial(cx, cy, s * 1.1f, intArrayOf(glow.a((if (ok) .55f else .3f) * e), glow.a(.12f * e), glow.a(0f)), floatArrayOf(0f, .45f, 1f))))
        if (ok) {
            for ((n, spin, a0, w) in listOf(RayLayer(12, .03f, .16f, .07f), RayLayer(18, -.02f, .09f, .04f))) {
                for (i in 0 until n) {
                    val ang = i * TAU / n + time * TAU * spin
                    val r1 = s * (.9f + .25f * sin(i * 1.7f + time * 3))
                    val ray = Path().apply {
                        moveTo(cx, cy)
                        lineTo(cx + cos(ang - w) * r1, cy + sin(ang - w) * r1)
                        lineTo(cx + cos(ang + w) * r1, cy + sin(ang + w) * r1)
                        close()
                    }
                    drawPath(ray, pt(add = true, shader = radial(cx, cy, r1, intArrayOf(glow.a(a0 * e), glow.a(0f)))))
                }
            }
            val star = pt(add = true)
            for (i in 0 until 46) {
                val tw = .5f + .5f * sin(time * TAU * (.6f + (i % 5) * .2f) + i)
                star.color = WHITE.a((.15f + .55f * tw) * e)
                drawCircle((i * 97 % 101) / 101f * W, (i * 61 % 89) / 89f * H * .8f, .6f + tw * (if (i % 3 == 0) 1.4f else .7f), star)
            }
        }
    }
    // vignette dồn mắt vào giữa (Alignment(0,-0.15), bán kính 0,95 × cạnh ngắn)
    drawRect(0f, 0f, W, H, pt(shader = radial(cx, H / 2 * .85f, .95f * s, intArrayOf(CLEAR, CLEAR, 0xFF000000.toInt().a(.55f)), floatArrayOf(0f, .55f, 1f))))
}

// ---------------- vụ nổ ánh sáng (BurstPainter) ----------------

private fun Canvas.glowDot(x: Float, y: Float, r: Float, col: Int, a: Float) {
    if (a <= .01f) return
    drawCircle(x, y, r * 3, pt(shader = radial(x, y, r * 3, intArrayOf(col.a(a * .45f), col.a(0f)))))
    drawCircle(x, y, r, pt(color = mix(col, WHITE, .45f).a(a)))
}

private fun Canvas.tail(pts: List<Offset>, w: Float, col: Int, a: Float) {
    val p = pt(round = true)
    for (k in 1 until pts.size) {
        val f = 1 - k.toFloat() / pts.size
        p.strokeWidth = w * f + .3f
        p.color = col.a(a * f * f)
        drawLine(pts[k - 1].x, pts[k - 1].y, pts[k].x, pts[k].y, p)
    }
}

internal fun deg(rad: Float) = Math.toDegrees(rad.toDouble()).toFloat()

private fun Canvas.arc(cx: Float, cy: Float, r: Float, start: Float, sweep: Float, p: Paint) =
    drawArc(RectF(cx - r, cy - r, cx + r, cy + r), deg(start), deg(sweep), false, p)

private fun Canvas.burst(W: Float, H: Float, t: Float, color: Int, ok: Boolean, loi: Boolean, major: Boolean, godray: RuntimeShader? = null) {
    val cx = W / 2
    val cy = H * .40f
    val s = min(W, H)

    // mây kiếp tụ từ hai mép vào thiên tâm
    if (loi) {
        val gather = EaseInOut.at(t / .24f)
        if (gather > 0f) {
            drawRect(0f, 0f, W, H * .34f, pt(shader = vertical(0f, H * .34f, intArrayOf(0xFF05080F.toInt().a(.82f * gather), 0xFF111827.toInt().a(.54f * gather), CLEAR))))
            val shadow = pt(color = 0xFF070A10.toInt().a(.92f * gather), blur = 14f)
            val body = pt(color = 0xFF1A2230.toInt().a(.88f * gather), blur = 10f)
            for (i in 0 until 13) {
                val tx = (i + .5f) * W / 13
                val ex = if (i % 2 == 0) -s * .28f else W + s * .28f
                val x = ex + (tx - ex) * gather
                val y = s * .02f + (i % 4) * s * .07f + gather * s * .05f
                val w = s * (.32f + (i % 3) * .07f)
                val h = w * .6f
                oval(x, y, w + s * .07f, h + s * .07f, shadow)
                oval(x, y, w, h, body)
            }
        }
    }

    if (!ok) {
        if (major && t < ResultAt) return
        val rt = if (major) ((t - ResultAt) / (1 - ResultAt)).coerceIn(0f, 1f) else t
        val a = (1 - rt) * .35f
        val top = cy - s * .18f
        drawRect(0f, top, W, top + s * .42f, pt(shader = LinearGradient(0f, 0f, W, 0f, intArrayOf(CLEAR, color.a(a), CLEAR), null, Shader.TileMode.CLAMP)))
        // tâm mạch nứt: 8 tia gãy khúc lóe ra rồi tắt
        val crack = (1 - rt * 1.8f).coerceIn(0f, 1f)
        if (crack > 0f) {
            val wide = pt(stroke = 3.5f, round = true, roundJoin = true, color = color.a(crack * .35f))
            val thin = pt(stroke = 1.1f, round = true, roundJoin = true, color = mix(color, WHITE, .5f).a(crack * .9f))
            for (i in 0 until 8) {
                val dir0 = i * PIf / 4 + .3f
                var qx = cx
                var qy = cy
                val path = Path().apply { moveTo(qx, qy) }
                for (k in 0 until 6) {
                    val ang = dir0 + ((i * 7 + k * 5) % 5 - 2) * .16f
                    qx += cos(ang) * s * (.03f + .006f * (i % 3))
                    qy += sin(ang) * s * (.03f + .006f * (i % 3))
                    path.lineTo(qx, qy)
                }
                drawPath(path, wide)
                drawPath(path, thin)
            }
        }
        // tàn đỏ bắn ra rồi rơi theo trọng lực
        for (i in 0 until 14) {
            val ang = i * TAU / 14 + i * .37f
            val v0 = s * (.18f + (i % 4) * .05f)
            fun at(u: Float) = Offset(cx + cos(ang) * v0 * u, cy + sin(ang) * v0 * u * .6f + s * .35f * u * u)
            tail(List(6) { k -> at(max(0f, rt - k * .03f)) }, 1.8f, color, (1 - rt) * .7f)
            at(rt).let { glowDot(it.x, it.y, 1.4f, color, (1 - rt) * .9f) }
        }
        return
    }

    if (major && t < ResultAt) return
    val bt = if (major) ((t - ResultAt) / (1 - ResultAt)).coerceIn(0f, 1f) else t

    // tiểu cảnh giới: linh khí xoắn ốc hút vào tâm trước va chạm
    if (!major && t < .28f) {
        val g = t / .28f
        for (i in 0 until 12) {
            fun at(gg: Float): Offset {
                val ang = gg * 3.2f + i * TAU / 12
                val r = (1 - gg) * s * .34f + 6
                return Offset(cx + cos(ang) * r, cy + sin(ang) * .8f * r)
            }
            tail(List(7) { k -> at(max(0f, g - k * .035f)) }, 2.4f, color, .25f + g * .6f)
            at(g).let { glowDot(it.x, it.y, 1.3f + g * .8f, color, .3f + g * .6f) }
        }
        glowDot(cx, cy, 3 + g * 6, color, g * .8f)
    }

    // tiểu cảnh giới: linh văn xoay khép trận + 8 quẻ bát quái + sóng tu vi dâng
    if (!major) {
        val spin = t * PIf * 2.4f
        val rune = pt(stroke = 1.5f, color = color.a((1 - t) * .8f))
        arc(cx, cy, s * .16f, spin, PIf * 1.35f, rune)
        arc(cx, cy, s * .23f, -spin, PIf * 1.35f, rune)
        val yao = pt(stroke = 1.6f, round = true, color = mix(color, WHITE, .35f).a((1 - t) * .9f))
        for (i in 0 until 8) {
            val ang = spin + i * PIf / 4
            val rr = s * (.20f + .05f * t)
            val px = cx + cos(spin + i * TAU / 8) * rr
            val py = cy + sin(spin + i * TAU / 8) * rr
            drawCircle(px, py, 9f, pt(shader = radial(px, py, 9f, intArrayOf(color.a((1 - t) * .35f), color.a(0f)))))
            save()
            translate(px, py)
            rotate(deg(ang + PIf / 2))
            val bits = 7 - i
            for (j in 0 until 3) {
                val y = (j - 1) * 2.8f
                if ((bits shr j) and 1 == 1) drawLine(-3.6f, y, 3.6f, y, yao)
                else { drawLine(-3.6f, y, -.9f, y, yao); drawLine(.9f, y, 3.6f, y, yao) }
            }
            restore()
        }
        val waveY = cy + s * .18f - t * s * .48f
        oval(cx, waveY, s * .34f, 18f, pt(stroke = 3 * (1 - t) + .5f, blur = 3f, color = color.a((1 - t) * .7f)))
    }

    // chớp va chạm + vệt lóe ngang
    if (bt < .16f) {
        val ft = bt / .16f
        val r = s * (if (major) .5f else .34f)
        val hot = mix(color, WHITE, if (major) .75f else .55f)
        drawCircle(cx, cy, r, pt(shader = radial(cx, cy, r, intArrayOf(hot.a((1 - ft) * (if (major) .85f else .7f)), color.a(0f)))))
        val sw = s * (if (major) 1.6f else 1f) * (.5f + ft)
        val sh = 6 + (1 - ft) * 6
        oval(cx, cy, sw, sh, pt(shader = radial(cx, cy, min(sw, sh) / 2, intArrayOf(WHITE.a((1 - ft) * .9f), hot.a(0f)))))
    }

    // pháp trận sau va chạm: cung lệch pha + 12 vạch
    val wheel = ((bt - .04f) / .68f).coerceIn(0f, 1f)
    if (wheel > 0f) {
        val ease = EaseOut.at(wheel)
        val radius = s * (.10f + ease * (if (major) .38f else .28f))
        val rune = pt(stroke = if (major) 1.4f else 1.1f, round = true, color = color.a((1 - wheel) * (if (major) .72f else .58f)))
        save()
        translate(cx, cy)
        rotate(deg(bt * PIf * (if (major) .9f else 1.4f)))
        arc(0f, 0f, radius, -PIf * .82f, PIf * 1.48f, rune)
        arc(0f, 0f, radius * .78f, PIf * .18f, PIf * 1.35f, rune)
        for (i in 0 until 12) {
            val a = i * TAU / 12
            val inner = radius * (.92f + if (i % 2 == 0) .02f else 0f)
            val outer = inner + if (i % 2 == 0) s * .045f else s * .022f
            drawLine(cos(a) * inner, sin(a) * inner, cos(a) * outer, sin(a) * outer, rune)
        }
        restore()
    }

    // dải khí nâng người lên
    val qi = ((bt - .12f) / .72f).coerceIn(0f, 1f)
    if (qi > 0f) {
        val qp = pt(stroke = if (major) 2f else 1.3f, round = true, blur = 2f, color = color.a((1 - qi) * (if (major) .32f else .24f)))
        for (i in 0 until (if (major) 5 else 3)) {
            val phase = bt * PIf * 2.4f + i * 1.7f
            val y = cy + s * .28f - qi * s * (.54f + i * .035f)
            val w = s * (.12f + qi * .18f)
            drawPath(Path().apply {
                moveTo(cx - w, y)
                cubicTo(cx - w * .35f, y - 9 + sin(phase) * 8, cx + w * .35f, y + 9 + cos(phase) * 8, cx + w, y)
            }, qp)
        }
    }

    // trụ sáng thon 3 lớp
    run {
        val p = (bt / (if (major) .4f else .6f)).coerceIn(0f, 1f)
        val h = (if (major) H * .85f else s * .55f) * EaseOut.at(p)
        val fade = 1 - bt
        val w0 = ((if (major) 44f else 22f) + (if (major) 14 else 7) * abs(sin(bt * 30))) * (1 - p * .3f)
        val base = cy + 20
        val k = if (major) 1f else .8f
        for ((wk, col, a) in listOf(Triple(1f, color, .22f), Triple(.55f, color, .4f), Triple(.22f, WHITE, .75f))) {
            val w = w0 * wk
            val path = Path().apply {
                moveTo(cx - w / 2, base); lineTo(cx - w * .15f, base - h); lineTo(cx + w * .15f, base - h); lineTo(cx + w / 2, base); close()
            }
            drawPath(path, pt(shader = LinearGradient(0f, base, 0f, base - max(h, 1f), intArrayOf(col.a(fade * a * k), col.a(fade * a * .6f), col.a(0f)), floatArrayOf(0f, .55f, 1f), Shader.TileMode.CLAMP)))
        }
    }

    // vòng xung kích có bề dày + vòng sóng dưới chân
    for (delay in floatArrayOf(0f, .22f)) {
        val v = ((bt - delay) / (1 - delay)).coerceIn(0f, 1f)
        if (v <= 0f || v >= 1f) continue
        val r = s * .05f + EaseOut.at(v) * s * (if (major) .52f else .36f)
        val band = 4 + (1 - v) * (if (major) 14 else 9)
        drawCircle(cx, cy, r + band, pt(shader = radial(cx, cy, r + band, intArrayOf(color.a(0f), mix(color, WHITE, .4f).a((1 - v) * .75f), color.a(0f)), floatArrayOf(((r - band) / (r + band)).coerceIn(0f, 1f), (r / (r + band)).coerceIn(0f, 1f), 1f))))
        oval(cx, cy + s * .2f, r * 2.2f, r * .5f, pt(stroke = (1 - v) * 3 + .5f, color = color.a((1 - v) * .5f)))
    }

    // tia sáng hình nêm dài/ngắn xen kẽ
    run {
        val n = if (major) 18 else 12
        for (i in 0 until n) {
            val ang = i * TAU / n + .26f
            val long = if (i % 2 == 0) 1f else .6f
            val r0 = s * .06f + bt * s * .1f
            val r1 = r0 + s * (if (major) .42f else .3f) * long * EaseOut.at(bt)
            val wid = .05f
            val ray = Path().apply {
                moveTo(cx + cos(ang - wid) * r0, cy + sin(ang - wid) * r0)
                lineTo(cx + cos(ang) * r1, cy + sin(ang) * r1)
                lineTo(cx + cos(ang + wid) * r0, cy + sin(ang + wid) * r0)
                close()
            }
            drawPath(ray, pt(shader = radial(cx, cy, max(r1, 1f), intArrayOf(mix(color, WHITE, .5f).a((1 - bt) * .8f), color.a(0f)))))
        }
    }

    // tàn quang bay lên có đuôi
    for (i in 0 until (if (major) 22 else 12)) {
        val seed = (i * 53 % 100) / 100f
        fun at(u: Float) = Offset(
            cx + ((i * 37 % 200) - 100) / 100f * s * .4f * (.4f + seed) + sin(u * 9 + i) * 6,
            cy + s * .1f - u * s * (if (major) .7f else .5f) * (.6f + seed),
        )
        val a = (1 - bt) * (if (bt > .15f) 1f else bt / .15f) * (.6f + .4f * sin(bt * 40 + i))
        tail(List(5) { k -> at(max(0f, bt - k * .02f)) }, 1.6f, color, a * .6f)
        at(bt).let { glowDot(it.x, it.y, (1 - bt) * 1.6f + .7f, color, a) }
    }

    // đại cảnh giới: thiên hoa — cánh hoa ánh sáng rơi xoay chậm
    if (major && bt > .2f) {
        val p = (bt - .2f) / .8f
        val petal = mix(color, 0xFFFFF3BF.toInt(), .6f)
        for (i in 0 until 20) {
            val seed = (i * 29 % 100) / 100f
            val x = (i + .5f) / 20 * W + sin(p * 6 + i) * 14
            val y = -10 + (p * (.7f + seed * .5f) + seed * .3f) * H * .8f
            val a = sin(min(p * 1.4f, 1f) * PIf) * .75f
            save()
            translate(x, y)
            rotate(deg(p * 8 + i))
            val k = s / 260
            scale(k, k * (.4f + .6f * abs(cos(p * 10 + i))))
            drawPath(Path().apply { moveTo(0f, -5f); quadTo(3.4f, 0f, 0f, 5f); quadTo(-3.4f, 0f, 0f, -5f) }, pt(shader = radial(0f, 0f, 4f, intArrayOf(WHITE.a(a), petal.a(a * .7f)))))
            restore()
        }
    }

    // godray + bloom cộng sáng (shader)
    if (godray != null && major && t > ResultAt && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        setGodray(godray, W, H, (t - .22f) / .78f, color, cx, cy)
        drawRect(0f, 0f, W, H, pt(shader = godray, add = true))
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun setGodray(sh: RuntimeShader, W: Float, H: Float, t: Float, color: Int, cx: Float, cy: Float) {
    sh.setFloatUniform("uSize", W, H)
    sh.setFloatUniform("uT", t)
    sh.setFloatUniform("uColor", ((color shr 16) and 255) / 255f, ((color shr 8) and 255) / 255f, (color and 255) / 255f)
    sh.setFloatUniform("uCenter", cx, cy)
}

// ---------------- rung ----------------

/** Rung theo từng đạo lôi chạm đất, đạo sau mạnh hơn (dp). */
private fun strikeShake(v: Float): Offset {
    var dx = 0f
    var dy = 0f
    StrikeAt.forEachIndexed { i, hit ->
        val d = v - hit
        if (d >= 0f && d < .09f) {
            val sh = (1 - d / .09f) * (4 + i * 2.5f)
            dx += sin(d * PIf * 90) * sh
            dy += cos(d * PIf * 76) * sh
        }
    }
    return Offset(dx, dy)
}

// ---------------- hộp thoại ----------------

/**
 * Hiệu ứng kết quả lên tầng / đột phá / Phi Thăng / thăng bậc Tiên.
 * Đại cảnh giới + Phi Thăng ("major"/"ascend") diễn 8 giây: (Tâm Ma 2,2 giây nếu có) → mây kiếp tụ → tia dẫn + ba đạo lôi
 * (38/56/74%) đánh vào nhân vật → lộ kết quả (86%): nổ sáng, godray, pháp trận, thiên hoa, tên cảnh giới nảy vào.
 * Lên tầng / thăng bậc Tiên: 1,25 giây linh khí hội tụ → bát quái → nổ sáng.
 * [kind]: "stage" | "major" | "ascend" | "tier"; [sub] ghi đè dòng phụ (thăng bậc Tiên: tên bậc mới).
 */
@Composable
internal fun AdvanceFxDialog(r: JSONObject, kind: String, figure: String, sub: String?, dismiss: () -> Unit) {
    val major = kind == "major" || kind == "ascend"
    val ascend = kind == "ascend"
    val ok = kind == "tier" || r.optBoolean("success") || r.optBoolean("ascended")
    val tamma = r.optJSONObject("tamma")
    val realm = r.optInt("realm", if (ascend) 9 else 1).coerceIn(1, 9)
    val grade = (realm + 1) / 2
    val color = if (!ok) 0xFFE03131.toInt() else (if (ascend || kind == "tier") gradeColor(5) else gradeColor(grade)).toArgb()
    val shaders = remember { loadShaders() }
    val view = LocalView.current

    var inTamma by remember { mutableStateOf(tamma != null) }
    var v by remember { mutableFloatStateOf(0f) } // timeline 0..1 (8 giây đại cảnh giới, 1,25 giây lên tầng)
    var clock by remember { mutableFloatStateOf(0f) } // giây kể từ lúc mở — đồng hồ liên tục cho linh khí/sao/Tâm Ma
    LaunchedEffect(Unit) {
        val dur = if (major) 8f else 1.25f
        if (tamma != null) view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        val t0 = withFrameNanos { it }
        var start = t0
        var done = false
        while (true) {
            val now = withFrameNanos { it }
            clock = (now - t0) / 1e9f
            if (inTamma && clock >= TammaSec) { inTamma = false; start = now }
            // pha Tâm Ma cũng chạy timeline 8 giây như Flutter (v dừng ở ~0,275 khi sang kiếp)
            v = ((now - start) / 1e9f / dur).coerceAtMost(1f)
            if (!inTamma && !done && v >= 1f) {
                done = true
                view.performHapticFeedback(if (ok && major) HapticFeedbackConstants.LONG_PRESS else HapticFeedbackConstants.VIRTUAL_KEY)
            }
        }
    }

    // tràn cả thanh trạng thái + thanh điều hướng: phông điện ảnh phải che kín UI app phía sau
    Dialog(dismiss, DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false, decorFitsSystemWindows = false)) {
        Box(Modifier.fillMaxSize().then(if (major) Modifier else Modifier.background(Color.Black.copy(.72f)))) {
            // lớp FX phủ toàn màn
            ComposeCanvas(Modifier.fillMaxSize()) {
                dpCanvas { W, H ->
                    if (inTamma) backdrop(W, H, .05f, clock / 4f, 0xFF7048E8.toInt(), false)
                    else {
                        if (major) backdrop(W, H, v, clock / 4f, color, ok)
                        burst(W, H, v, color, ok, loi = major, major = major, godray = shaders?.burst)
                        if (major) lightning(W, H, v, CloudEnd, ResultAt, Offset(W / 2, H / 2 - 58), shaders?.storm, v * 8)
                    }
                }
            }
            when {
                inTamma && tamma != null -> TammaView(tamma, v, clock, shaders)
                major && v < ResultAt -> Box(
                    Modifier.fillMaxSize().graphicsLayer { strikeShake(v).let { translationX = it.x * density; translationY = it.y * density } },
                    Alignment.Center,
                ) { CultivatorFigure(figure, realm, clock, major = true) }
                else -> ResultView(r, kind, figure, sub, ok, major, ascend, realm, grade, color, tamma, v, clock, dismiss)
            }
        }
    }
}

/** Nhân vật trong hộp thoại (AnimatedCultivator): khung 150×145, phóng 1,6× khi đại cảnh giới; lơ lửng chu kỳ 4 giây. */
@Composable
private fun CultivatorFigure(figure: String, realm: Int, clock: Float, major: Boolean, modifier: Modifier = Modifier) {
    val k = if (major) 1.6f else 1f
    Box(modifier.size((150 * k).dp, (145 * k).dp), Alignment.BottomCenter) {
        ComposeCanvas(Modifier.matchParentSize()) { drawRealmScene(realm, false, emptyList(), Color(0xFF9775FA), (clock / 8f) % 1f) }
        AsyncImage(
            "file:///android_asset/cultivators/$figure.webp", null,
            Modifier.fillMaxHeight(.92f).graphicsLayer { translationY = (sin(clock / 4f * TAU) * 4 - 6) * density },
            contentScale = ContentScale.Fit,
        )
    }
}

@Composable
private fun TammaView(tm: JSONObject, v: Float, clock: Float, shaders: FxShaders?) {
    val win = tm.optBoolean("win")
    val color = if (win) 0xFF7048E8.toInt() else 0xFFC92A2A.toInt()
    val t = MaterialTheme.typography
    Box(Modifier.fillMaxSize(), Alignment.Center) {
        Column(
            Modifier
                .graphicsLayer { translationX = (if (win) 0f else sin(v * PIf * 12) * 6 * (1 - v)) * density }
                .drawWithContent {
                    if (shaders == null) dpCanvas { W, H -> tammaSmoke(W, H, v, win) }
                    drawContent()
                    dpCanvas { W, H -> burst(W, H, v, color, win, loi = false, major = false) }
                }
                .padding(48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val p = (clock / TammaSec).coerceIn(0f, 1f)
            Box(
                Modifier.size(126.dp).drawWithContent {
                    val sh = shaders
                    if (sh != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) dpCanvas { W, H -> tammaVolume(sh.tamma, W / 2, H / 2, 130f, clock, p, win, false) }
                    drawContent()
                    if (sh != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) dpCanvas { W, H -> tammaVolume(sh.tamma, W / 2, H / 2, 130f, clock, p, win, true) }
                },
            ) {
                AsyncImage(
                    "file:///android_asset/cult_fx/heart_demon.webp", null,
                    Modifier.fillMaxSize().graphicsLayer {
                        val pulse = 1 + sin(v * PIf * 5) * .06f
                        cameraDistance = 454f * density
                        translationY = sin(v * PIf * 3) * 7 * density
                        rotationY = Math.toDegrees(sin(clock * 1.7) * .55).toFloat()
                        rotationX = Math.toDegrees(sin(clock * 1.1) * .22).toFloat()
                        scaleX = pulse; scaleY = pulse
                    },
                    contentScale = ContentScale.Fit,
                )
            }
            Spacer(Modifier.height(10.dp))
            Text("TÂM MA KHẢO NGHIỆM", style = t.titleLarge.copy(color = Color.White, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.5.sp))
            Spacer(Modifier.height(6.dp))
            Text(
                if (win) "Đạo tâm bất động — áp chế tâm ma!" else "Tâm thần chấn động, tâm ma trỗi dậy...",
                textAlign = TextAlign.Center, style = t.bodyMedium.copy(color = Color.White.copy(.7f)),
            )
            Text("Đạo tâm ${tm.optInt("chance")}%", Modifier.padding(top = 4.dp), style = t.labelMedium.copy(color = Color.White.copy(.38f)))
        }
    }
}

@Composable
private fun ResultView(
    r: JSONObject, kind: String, figure: String, sub: String?, ok: Boolean, major: Boolean, ascend: Boolean,
    realm: Int, grade: Int, color: Int, tamma: JSONObject?, v: Float, clock: Float, dismiss: () -> Unit,
) {
    val t = MaterialTheme.typography
    Box(Modifier.fillMaxSize(), Alignment.Center) {
        Column(
            Modifier
                .graphicsLayer {
                    var dx = if (ok) 0f else sin(v * PIf * 10) * 8 * (1 - v)
                    var dy = 0f
                    // đại cảnh giới thành công: cú "slam" rung mạnh tắt dần ngay khi lộ kết quả
                    val d = v - ResultAt
                    if (major && ok && d >= 0f && d < .08f) {
                        val sh = (1 - d / .08f) * 9
                        dx += sin(d * PIf * 90) * sh
                        dy += cos(d * PIf * 76) * sh
                    }
                    translationX = dx * density
                    translationY = dy * density
                }
                .padding(48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // nhân vật / mặt quỷ hiện dần sau chớp sáng (Interval 0,15–0,6)
            Box(Modifier.graphicsLayer { alpha = EaseOut.at((v - .15f) / .45f) }) {
                if (ok) {
                    val intro = if (major) (v - ResultAt) / .1f else v / .7f
                    CultivatorFigure(
                        figure, realm, clock, major,
                        Modifier.drawWithContent {
                            val k = size.width / density / 150f
                            dpCanvas { _, _ -> scale(k, k); qiVortex(clock / 4f, intro, color, false, major) }
                            drawContent()
                            dpCanvas { _, _ -> scale(k, k); qiVortex(clock / 4f, intro, color, true, major) }
                        },
                    )
                } else AsyncImage("file:///android_asset/cult_fx/heart_demon.webp", null, Modifier.size(126.dp), contentScale = ContentScale.Fit)
            }
            Spacer(Modifier.height(if (ok) 24.dp else 10.dp))
            // đại cảnh giới thành công: tên "slam" vào (1,5× → 1×, nảy) ở 90–100%
            Box(Modifier.graphicsLayer {
                if (major && ok) {
                    alpha = ((v - .90f) / .06f).coerceIn(0f, 1f)
                    val s = 1.5f - .5f * elasticOut(((v - .90f) / .10f).coerceIn(0f, 1f))
                    scaleX = s; scaleY = s
                }
            }) {
                if (major && ok && !ascend) RealmTitle(realm)
                else Text(
                    when {
                        ascend -> if (ok) "PHI THĂNG THÀNH CÔNG" else "PHI THĂNG THẤT BẠI"
                        kind == "tier" -> "THĂNG BẬC TIÊN"
                        major -> "ĐỘT PHÁ THẤT BẠI"
                        else -> if (ok) "LÊN TẦNG" else "LÊN TẦNG THẤT BẠI"
                    },
                    style = t.titleLarge.copy(color = if (ok) Color.White else Color(color), fontWeight = FontWeight.ExtraBold, letterSpacing = 1.5.sp),
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                sub ?: when {
                    ascend -> if (ok) "Vượt Tâm Ma cuối, độ kiếp phi thăng —\nđắc đạo thành Tiên Nhân!" else "Tâm ma còn vương, phi thăng bất thành.\nTĩnh tâm rồi thử lại."
                    ok -> "${RealmNames[realm - 1]} · tầng ${r.optInt("stage", 1)}"
                    major -> "Lôi kiếp đánh rớt, tâm ma quấy nhiễu — mất 30% tu vi tầng này.\nTĩnh tâm dưỡng thương rồi thử lại!"
                    else -> "Tẩu hỏa nhập ma nhẹ, mất 30% tu vi tầng này.\nTĩnh tâm tu luyện tiếp!"
                },
                textAlign = TextAlign.Center, style = t.bodyMedium.copy(color = Color.White.copy(.7f)),
            )
            if (major && !ascend && r.has("chance")) Text("Tỷ lệ lúc roll: ${r.optInt("chance")}%", Modifier.padding(top = 4.dp), style = t.labelMedium.copy(color = Color.White.copy(.38f)))
            if (!ascend && tamma?.optBoolean("win") == true) Text(
                "⚔ Áp chế Tâm Ma · +15% đột phá", Modifier.padding(top = 4.dp),
                style = t.labelMedium.copy(color = Color(0xFF9775FA), fontWeight = FontWeight.Bold),
            )
            Spacer(Modifier.height(18.dp))
            Button(
                dismiss,
                colors = ButtonDefaults.buttonColors(containerColor = Color(color), contentColor = if (ok && grade >= 4) Color.Black.copy(.87f) else Color.White),
            ) { Text(if (!ok) "Tĩnh tâm" else if (ascend) "Đắc đạo thành tiên" else "Tiếp tục tu luyện") }
        }
    }
}

/** Tiêu đề phá cảnh: dòng nhỏ giãn chữ + TÊN CẢNH GIỚI MỚI cỡ lớn vàng chuyển sắc có quầng sáng. */
@Composable
private fun RealmTitle(realm: Int) {
    val t = MaterialTheme.typography
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("VƯỢT LÔI KIẾP · ĐỘT PHÁ", style = t.labelLarge.copy(color = Color(0xFFFFE8A3), letterSpacing = 4.sp, fontWeight = FontWeight.Bold))
        Spacer(Modifier.height(4.dp))
        Text(
            RealmNames[realm - 1].uppercase(), textAlign = TextAlign.Center,
            style = t.displaySmall.copy(
                brush = Brush.verticalGradient(listOf(Color(0xFFFFF3BF), Color(0xFFFFD25A), Color(0xFFE8A80C))),
                fontWeight = FontWeight.Black, letterSpacing = 6.sp,
                shadow = Shadow(Color(0xCCFFB300), blurRadius = 36f),
            ),
        )
    }
}
