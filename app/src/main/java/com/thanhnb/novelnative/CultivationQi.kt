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

// ---------------- linh khí quanh người (painters_qi.dart) ----------------

private val Tilts = floatArrayOf(-.34f, .27f, -.12f, .42f, -.5f)
private val Heights = floatArrayOf(-24f, 8f, -6f, 24f, -40f)

private fun Canvas.bloomDot(x: Float, y: Float, r: Float, col: Int, a: Float, add: Boolean = true) {
    drawCircle(x, y, r * 4, pt(add = add, shader = radial(x, y, r * 4, intArrayOf(col.a(.5f * a), col.a(0f)))))
    drawCircle(x, y, r, pt(add = add, color = WHITE.a(.95f * a)))
}

/**
 * Dải linh khí quấn quanh thân — tách lớp: đoạn ở nửa sau vẽ dưới nhân vật, nửa trước vẽ đè lên. Mỗi dải có trục nghiêng,
 * cao độ, chiều và tốc độ riêng → không dải nào chạy trùng nhịp. [time] đơn vị vòng 4 giây, tích luỹ liên tục.
 * [additive] chỉ dùng trên nền tối (hộp thoại); màn Tu Tiên nền sáng vẽ thường.
 */
internal fun Canvas.qiRibbons(cx: Float, cy: Float, colors: List<Int>, time: Float, front: Boolean, scale: Float, intro: Float, width: Float, additive: Boolean = true) {
    val e = EaseOutCubic.at(intro)
    if (e <= .001f) return
    val samples = 32
    val span = 2.4f
    colors.forEachIndexed { i, col ->
        val tilt = Tilts[i % 5]
        val rx = (60 + (i % 3) * 9) * scale * (.3f + .7f * e)
        val ry = (14 + (i % 2) * 6) * scale
        val dy = 62 * scale + (Heights[i % 5] * scale - 62 * scale) * e
        val dir = if (i % 2 == 0) 1f else -1f
        val head = time * TAU * (.55f + .1f * (i % 3)) * dir + i * TAU / colors.size
        val ct = cos(tilt)
        val st = sin(tilt)
        val pts = ArrayList<Offset>(samples + 1)
        val depth = FloatArray(samples + 1)
        val wid = FloatArray(samples + 1)
        for (k in 0..samples) {
            val u = k.toFloat() / samples
            val a = head - dir * span * u
            val x = cos(a) * rx
            val y = sin(a) * ry
            pts += Offset(cx + x * ct - y * st, cy + x * st + y * ct + dy)
            depth[k] = sin(a)
            wid[k] = width * scale * (1 - u).pow(.65f) * (.7f + .3f * depth[k]) + .4f
        }
        val len = (pts[0] - pts[samples]).getDistance()
        var k0 = 0
        while (k0 < samples) {
            if ((depth[k0] > 0) != front) { k0++; continue }
            var k1 = k0
            while (k1 < samples && (depth[k1 + 1] > 0) == front) k1++
            val end = min(k1 + 1, samples)
            if (end > k0) ribbonRun(pts, wid, k0, end, col, e, pts[0], len, additive)
            k0 = end
        }
        if ((depth[0] > 0) == front) bloomDot(pts[0].x, pts[0].y, 2.2f * scale, col, e, additive)
    }
}

private fun Canvas.ribbonRun(pts: List<Offset>, wid: FloatArray, a: Int, b: Int, col: Int, alpha: Float, head: Offset, len: Float, add: Boolean) {
    fun poly(wk: Float): Path {
        val left = ArrayList<Offset>()
        val right = ArrayList<Offset>()
        for (k in a..b) {
            var t = pts[min(k + 1, pts.size - 1)] - pts[max(k - 1, 0)]
            val l = t.getDistance()
            t = if (l > 0) t / l else Offset(1f, 0f)
            val nrm = Offset(-t.y, t.x) * (wid[k] * wk / 2)
            left += pts[k] + nrm
            right += pts[k] - nrm
        }
        return Path().poly(left + right.reversed(), close = true)
    }
    fun fade(c0: Int, a0: Float) = radial(head.x, head.y, max(len, 1f), intArrayOf(c0.a(a0), c0.a(a0 * .5f), c0.a(0f)), floatArrayOf(0f, .55f, 1f))
    drawPath(poly(2.6f), pt(add = add, shader = fade(col, (if (add) .45f else .32f) * alpha), blur = 4f))
    drawPath(poly(1f), pt(add = add, shader = fade(col, (if (add) .55f else .8f) * alpha)))
    drawPath(poly(.32f), pt(add = add, shader = fade(mix(col, WHITE, if (add) .4f else .75f), (if (add) .6f else .95f) * alpha)))
}

// ---------------- đồ bay quanh người (weaponOrbit / phapbaoOrbit bên Flutter) ----------------
// Toạ độ trong khung nhân vật 150×145, tâm c. t: vòng 4 giây.

/** Khung nhân vật Flutter 150×145 đặt lên sân khấu Native: ảnh cao 240dp, tâm cách đỉnh sân khấu 144dp. */
internal val FigC = Offset(75f, 72.5f)
internal const val FigK = 240f / 145f
internal const val FigTop = 144f

/** Vẽ trong hệ toạ độ khung 150×145 của Flutter, căn vào tâm nhân vật trên sân khấu. */
internal fun DrawScope.figureSpace(block: Canvas.() -> Unit) = drawIntoCanvas { c ->
    val cv = c.nativeCanvas
    cv.save()
    cv.translate(size.width / 2, FigTop * density)
    cv.scale(FigK * density, FigK * density)
    cv.translate(-FigC.x, -FigC.y)
    cv.block()
    cv.restore()
}

/** Vũ khí: vòng ngang quanh eo, bán kính + cao độ dao động theo sin tần số lệch nhau (Lissajous) → quỹ tích bất quy tắc. */
internal fun weaponOrbit(t: Float, c: Offset): Pair<Offset, Boolean> {
    val a = t * TAU
    val r = 44 + 10 * sin(a * 3 + 1.3f)
    return Offset(c.x + cos(a) * r, c.y - 6 + sin(a) * r * .40f + sin(a * 2 + .7f) * 6) to (sin(a) > 0)
}

/** Pháp bảo: trục khác hẳn vũ khí — elip dựng đứng nghiêng chéo ~29°, nâng ngang ngực, quay ngược chiều, thở theo tần số khác. */
internal fun phapbaoOrbit(t: Float, c: Offset): Pair<Offset, Boolean> {
    val a = -t * TAU + 2.6f
    val r = 34 + 8 * sin(a * 2 + .5f)
    val rx = cos(a) * r * .55f
    val ry = sin(a) * r * .72f
    val ct = .8776f
    val st = .4794f
    return Offset(c.x + rx * ct - ry * st, c.y - 10 + rx * st + ry * ct) to (ry > 0)
}

/** Vệt kiếm quang: chuỗi đốm nhỏ mờ dần theo các pha ngay trước, chỉ vẽ khi món đang ở đúng lớp [front]. */
internal fun Canvas.orbitTail(orbit: (Float, Offset) -> Pair<Offset, Boolean>, t: Float, c: Offset, color: Int, scale: Float, front: Boolean) {
    if (orbit(t, c).second != front) return
    for (k in 6 downTo 1) {
        val q = orbit(t - k * .013f, c).first
        drawCircle(q.x, q.y, (2.4f - k * .28f) * scale, pt(color = color.a(.30f * (1 - k / 7f))))
    }
}

/** Pháp trận nằm dưới chân: vòng kép + bát giác tinh + 8 quẻ + vạch khắc, xoay ngược chiều nhau. */
private fun Canvas.runeCircle(cx: Float, cy: Float, radius: Float, color: Int, time: Float, intro: Float) {
    val e = EaseOutBack.at(intro)
    if (e <= .001f) return
    val a = intro.coerceIn(0f, 1f)
    val r = radius * e
    val hot = mix(color, WHITE, .45f)
    save()
    translate(cx, cy)
    scale(1f, .34f)
    drawCircle(0f, 0f, r * 1.2f, pt(add = true, shader = radial(0f, 0f, r * 1.2f, intArrayOf(color.a(.4f * a), color.a(.12f * a), color.a(0f)), floatArrayOf(0f, .7f, 1f))))
    val haze = { w: Float -> pt(add = true, stroke = w * 3, color = color.a(.35f * a), blur = 3f) }
    val sharp = { w: Float -> pt(add = true, stroke = w, round = true, color = hot.a(.9f * a)) }
    fun ring(rr: Float, w: Float) { drawCircle(0f, 0f, rr, haze(w)); drawCircle(0f, 0f, rr, sharp(w)) }
    ring(r, 1.8f); ring(r * .84f, 1f); ring(r * .46f, 1f)
    save()
    rotate(deg(time * TAU * .12f))
    for (off in floatArrayOf(0f, PIf / 4)) {
        val sq = Path().poly(List(4) { k -> val ang = off + k * PIf / 2; Offset(cos(ang) * r * .82f, sin(ang) * r * .82f) }, close = true)
        drawPath(sq, haze(.9f)); drawPath(sq, sharp(.9f))
    }
    restore()
    save()
    rotate(deg(-time * TAU * .08f))
    val tick = pt(add = true, stroke = 1f, color = hot.a(.6f * a))
    for (k in 0 until 32) {
        val ang = k * PIf / 16
        val o = if (k % 2 == 0) .95f else .91f
        drawLine(cos(ang) * r * .86f, sin(ang) * r * .86f, cos(ang) * r * o, sin(ang) * r * o, tick)
    }
    val yao = pt(add = true, stroke = 1.6f, round = true, color = hot.a(.95f * a))
    val sc = radius / 70
    for (i in 0 until 8) {
        save()
        rotate(deg(i * PIf / 4 + PIf / 8))
        translate(r * .65f, 0f)
        rotate(90f)
        val bits = 7 - i
        for (j in 0 until 3) {
            val y = (j - 1) * 3.2f * sc
            if ((bits shr j) and 1 == 1) drawLine(-4.5f * sc, y, 4.5f * sc, y, yao)
            else { drawLine(-4.5f * sc, y, -1.2f * sc, y, yao); drawLine(1.2f * sc, y, 4.5f * sc, y, yao) }
        }
        restore()
    }
    restore()
    restore()
}

private fun Canvas.risingMotes(cx: Float, cy: Float, color: Int, time: Float, front: Boolean, scale: Float, count: Int, intro: Float) {
    val a0 = intro.coerceIn(0f, 1f)
    if (a0 <= .001f) return
    for (i in 0 until count) {
        fun phase(tt: Float) = ((tt * .45f + i.toFloat() / count) % 1f + 1f) % 1f
        fun at(tt: Float): Offset {
            val ph = phase(tt)
            val ang = ph * TAU * 1.5f + i * 2.1f
            return Offset(cx + cos(ang) * (48 - ph * 16) * scale, cy + (56 - ph * 130) * scale)
        }
        val ph = phase(time)
        if ((sin(ph * TAU * 1.5f + i * 2.1f) > 0) != front) continue
        val a = sin(ph * PIf) * a0
        val trail = pt(add = true, round = true)
        var prev = at(time)
        for (k in 1..5) {
            val q = at(time - k * .02f)
            if ((q - prev).getDistance() > 20 * scale) break
            val f = 1 - k / 6f
            trail.strokeWidth = 2.2f * f * scale + .3f
            trail.color = color.a(.6f * a * f)
            drawLine(prev.x, prev.y, q.x, q.y, trail)
            prev = q
        }
        at(time).let { bloomDot(it.x, it.y, 1.3f * scale, color, a) }
    }
}

/** QiVortexPainter: vẽ trên khung nhân vật 150×145 (đã scale theo khung thật). */
internal fun Canvas.qiVortex(time: Float, intro: Float, color: Int, front: Boolean, major: Boolean) {
    val w = 150f
    val h = 145f
    val cx = w / 2
    val cy = h * .56f
    val s = if (major) .95f else 1.1f
    if (!front) runeCircle(cx, h - 10, 74 * s, color, time, intro)
    val cols = buildList {
        add(color); add(mix(color, WHITE, .45f)); add(mix(color, 0xFFFFD25A.toInt(), .55f))
        if (major) { add(mix(color, 0xFF74C0FC.toInt(), .4f)); add(mix(color, WHITE, .2f)) }
    }
    qiRibbons(cx, cy, cols, time, front, s, intro, 6f)
    risingMotes(cx, cy, mix(color, WHITE, .3f), time, front, s, if (major) 18 else 12, intro)
}

/** Fallback Tâm Ma khi không có shader: quầng + 6 sợi ma khí cuộn quanh. */
internal fun Canvas.tammaSmoke(W: Float, H: Float, t: Float, subdued: Boolean) {
    val cx = W / 2
    val cy = H * .43f
    val s = min(W, H)
    val color = if (subdued) 0xFF8B5CF6.toInt() else 0xFFE03131.toInt()
    val pulse = .5f + .5f * sin(t * PIf * 4)
    drawCircle(cx, cy, s * (.18f + pulse * .025f), pt(shader = radial(cx, cy, s * .23f, intArrayOf(color.a(.19f), CLEAR))))
    for (i in 0 until 6) {
        val a = i * TAU / 6 + t * (if (subdued) 1.1f else 2f)
        val sx = cx + cos(a) * s * .26f
        val sy = cy + sin(a) * .45f * s * .26f
        val ex = cx + cos(a + 1.7f) * s * .10f
        val ey = cy + sin(a + 1.7f) * .52f * s * .10f
        drawPath(Path().apply {
            moveTo(sx, sy)
            cubicTo(sx - sin(a) * s * .16f, sy + cos(a) * s * .12f, ex + cos(a) * s * .13f, ey - sin(a) * s * .10f, ex, ey)
        }, pt(stroke = 1.4f + (i % 2), round = true, blur = 3f, color = color.a(.22f + pulse * .18f)))
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
internal fun Canvas.tammaVolume(sh: RuntimeShader, cx: Float, cy: Float, radius: Float, time: Float, v: Float, win: Boolean, front: Boolean) {
    sh.setFloatUniform("uCenter", cx, cy)
    sh.setFloatUniform("uRadius", radius)
    sh.setFloatUniform("uTime", time)
    sh.setFloatUniform("uV", v)
    sh.setFloatUniform("uWin", if (win) 1f else 0f)
    sh.setFloatUniform("uFront", if (front) 1f else 0f)
    val b = radius * 1.5f
    drawRect(cx - b, cy - b, cx + b, cy + b, pt(shader = sh))
}
