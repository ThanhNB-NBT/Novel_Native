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

// ---------------- lôi kiếp thủ tục (LightningStormPainter) ----------------

private const val LCore = WHITE
private const val LMid = 0xFFBFD9FF.toInt()
private const val LGlow = 0xFF6E8BFF.toInt()

/** Độ sáng sau cú đánh: 3 cú trên cùng kênh trong ~250ms + tàn sáng dài. */
private fun intensity(d: Float): Float {
    if (d < 0f) return 0f
    var a = 0f
    listOf(0f to 1f, .012f to .9f, .027f to .75f).forEachIndexed { k, (at, amp) ->
        if (d >= at) a = max(a, amp * exp(-(d - at) / (.009f + k * .002f)))
    }
    return min(1f, a + .35f * exp(-d / .05f))
}

private fun rootX(i: Int, W: Float, s: Float): Float {
    val rnd = Random(7919 * (i + 1))
    return W * floatArrayOf(.30f, .72f, .5f)[i] + (rnd.nextFloat() - .5f) * s * .1f
}

/** Kênh sét 2D: đi bộ ngẫu nhiên ngang, kéo về hai đầu. */
private fun bolt(a: Offset, b: Offset, r: Random, segments: Int, rough: Float): List<Offset> {
    val dir = b - a
    val len = dir.getDistance()
    val u = dir / len
    val nrm = Offset(-u.y, u.x)
    val pts = mutableListOf(a)
    var lateral = 0f
    for (k in 1 until segments) {
        val t = k.toFloat() / segments
        lateral += (r.nextFloat() - .5f) * rough
        lateral *= .8f
        val pull = min(1f, min(t, 1 - t) * 7)
        val along = t + (r.nextFloat() - .5f) * .6f / segments
        pts += a + u * (len * along) + nrm * (lateral * pull * 2.2f)
    }
    pts += b
    return pts
}

/** Kênh sét 3D chiếu phối cảnh: gốc sâu trong mây, đoạn gần to và vặn mạnh; kèm hệ số gần/xa mỗi điểm. */
private fun bolt3d(top: Offset, hit: Offset, r: Random, s: Float): Pair<List<Offset>, List<Float>> {
    val z0 = 2.2f
    val n = 28
    val vp = Offset(top.x * .6f + hit.x * .4f, -s * .35f)
    val w0 = vp + (top - vp) * (1 + z0)
    val pts = ArrayList<Offset>(n + 1)
    val ks = ArrayList<Float>(n + 1)
    var lx = 0f
    var lz = 0f
    var zPrev = z0
    for (k in 0..n) {
        val t = (k.toFloat() / n).pow(.6f)
        val pull = min(1f, min(t, 1 - t) * 7)
        if (k in 1 until n) {
            lx = (lx + (r.nextFloat() - .5f) * s * .16f) * .8f
            lz = (lz + (r.nextFloat() - .5f) * .5f) * .8f
        }
        val z = min(zPrev, max(0f, z0 * (1 - t).pow(1.6f) + lz * pull))
        zPrev = z
        val w = w0 + (hit - w0) * t + Offset(lx * pull * 2.2f, 0f)
        val kk = 1 / (1 + z)
        pts += vp + (w - vp) * kk
        ks += kk
    }
    return pts to ks
}

private fun branches(main: List<Offset>, r: Random, s: Float, depth: Int, count: Int, ks: List<Float>? = null): List<Pair<Int, List<Offset>>> {
    val out = mutableListOf<Pair<Int, List<Offset>>>()
    repeat(count) {
        val k = 3 + r.nextInt(max(1, main.size * 2 / 3))
        if (k >= main.size - 2) return@repeat
        val p = main[k]
        val d = main[k + 1] - main[k - 1]
        val ang = atan2(d.y, d.x) + (if (r.nextBoolean()) 1 else -1) * (.35f + r.nextFloat() * .6f)
        val len = s * (.12f + r.nextFloat() * .2f) / depth * (if (ks == null) 1f else .4f + ks[k])
        val pts = bolt(p, p + Offset(cos(ang), sin(ang)) * len, r, 10, s * .03f)
        out += k to pts
        if (depth == 1 && r.nextFloat() < .5f) branches(pts, r, s, 2, 1).forEach { (sk, sp) -> out += (k + sk) to sp }
    }
    return out
}

/** 3 lớp cộng sáng: quầng rộng mờ → thân lam → lõi trắng. */
private fun Canvas.zap(pts: List<Offset>, w: Float, a: Float, cool: Float = 0f) {
    if (pts.size < 2 || a <= .005f) return
    val path = Path().poly(pts)
    drawPath(path, pt(add = true, stroke = w * 7, roundJoin = true, color = LGlow.a(.28f * a), blur = w * 3))
    drawPath(path, pt(add = true, stroke = w * 2.2f, roundJoin = true, color = mix(LMid, 0xFF9C8CFF.toInt(), cool).a(.75f * a), blur = w * .8f))
    drawPath(path, pt(add = true, stroke = w, roundJoin = true, round = true, color = mix(LCore, 0xFFD0C4FF.toInt(), cool).a(a)))
}

private fun Canvas.zapDepth(pts: List<Offset>, ks: List<Float>, w: Float, a: Float, cool: Float = 0f) {
    var i = 0
    while (i < pts.size - 1) {
        val j = min(i + 5, pts.size - 1)
        val k = (ks[i] + ks[j]) / 2
        zap(pts.subList(i, j + 1), w * (.3f + k * 1.1f), a * (.55f + .45f * k), cool)
        i += 5
    }
}

internal fun Canvas.lightning(W: Float, H: Float, v: Float, from: Float, to: Float, hit: Offset, cloud: RuntimeShader?, seconds: Float) {
    if (v < from || v >= to) return
    val s = min(W, H)
    val env = ((v - from) / .05f).coerceIn(0f, 1f) * ((to - v) / .03f).coerceIn(0f, 1f)

    var flash = 0f
    var flashX = W / 2
    StrikeAt.forEachIndexed { i, h ->
        val a = intensity(v - h) * (.75f + i * .12f)
        if (a > flash) { flash = a; flashX = rootX(i, W, s) }
    }
    var sheet = 0f
    var sheetAt = Offset.Zero
    floatArrayOf(.30f, .47f, .51f, .66f, .80f).forEachIndexed { i, h ->
        val d = v - h
        if (d < 0f || d > .03f) return@forEachIndexed
        val a = exp(-d / .006f) * (.55f + .45f * abs(sin(d * 900)))
        if (a > sheet) { sheet = a; sheetAt = Offset(W * (.18f + (i * .37f) % .7f), s * (.06f + (i % 3) * .05f)) }
    }
    sky(W, H, s, env, flash, sheet, sheetAt, flashX, cloud, seconds, v)

    StrikeAt.forEachIndexed { i, h ->
        val d = v - h
        if (d < -.022f || d > .12f) return@forEachIndexed
        val rnd = Random(7919 * (i + 1))
        val x0 = rootX(i, W, s)
        rnd.nextFloat()
        val (main, ks) = bolt3d(Offset(x0, -s * .05f), hit, rnd, s)
        val br = branches(main, rnd, s, 1, 4 + i, ks)
        val width = 2.2f + i * .7f
        if (d < 0f) {
            // tia dẫn bò xuống, mờ, đầu tia nhấp nháy
            val g = 1 + d / .022f
            val n = ceil(main.size * EaseIn.at(g)).toInt().coerceIn(2, main.size)
            val a = .28f + .2f * abs(sin(v * 3000))
            zapDepth(main.subList(0, n), ks, width * .45f, a * env)
            for ((k, pts) in br) {
                if (k >= n) continue
                val bn = ceil((n - k) * 1.3f).toInt().coerceIn(2, pts.size)
                zap(pts.subList(0, bn), width * .25f, a * .6f * env)
            }
            val tip = main[n - 1]
            drawCircle(tip.x, tip.y, s * .036f, pt(add = true, shader = radial(tip.x, tip.y, s * .036f, intArrayOf(LMid.a(.8f * env), LGlow.a(0f)))))
            return@forEachIndexed
        }
        val inten = intensity(d) * env
        if (inten < .01f) return@forEachIndexed
        zapDepth(main, ks, width, inten, (d / .09f).coerceIn(0f, 1f))
        if (i == 2 && d < .05f) zapDepth(main.mapIndexed { j, p -> p + Offset(s * .012f * ks[j], 0f) }, ks, width * .5f, inten * .6f)
        for ((_, pts) in br) zap(pts, width * .42f, inten * .7f, (d / .04f).coerceIn(0f, 1f))
        impact(hit, s, d, inten, i, v)
    }
}

private fun Canvas.sky(W: Float, H: Float, s: Float, env: Float, flash: Float, sheet: Float, sheetAt: Offset, flashX: Float, cloud: RuntimeShader?, seconds: Float, v: Float) {
    if (cloud != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        setStorm(cloud, W, H, seconds, env, flash, flashX, sheet, sheetAt)
        drawRect(0f, 0f, W, H * .4f, pt(shader = cloud))
    } else {
        val base = mix(0xFF0B1020.toInt(), 0xFF3A4A7A.toInt(), max(flash, sheet * .6f))
        for (i in 0 until 16) {
            val x = (i + .5f) * W / 16 * 1.1f - W * .05f + sin(v * 9 + i * 1.3f) * s * .03f
            val y = s * (.02f + (i % 4) * .04f) + cos(v * 7 + i) * s * .01f
            val r = s * (.13f + (i % 3) * .04f)
            drawCircle(x, y, r, pt(shader = radial(x, y, r, intArrayOf(base.a(.95f * env), base.a(0f)))))
        }
    }
    if (sheet > .01f) {
        drawCircle(sheetAt.x, sheetAt.y, s * .3f, pt(add = true, shader = radial(sheetAt.x, sheetAt.y, s * .3f, intArrayOf(LMid.a(.45f * sheet * env), LGlow.a(0f)))))
        val r = Random((sheetAt.x * 13).toInt())
        zap(bolt(sheetAt - Offset(s * .18f, 0f), sheetAt + Offset(s * .2f, s * .03f), r, 16, s * .03f), 1.2f, .55f * sheet * env)
    }
    if (flash > .01f) {
        drawRect(0f, 0f, W, H, pt(add = true, shader = vertical(0f, H, intArrayOf(LMid.a((if (cloud == null) .5f else .22f) * flash * env), LGlow.a(.18f * flash * env), LGlow.a(.05f * flash * env)))))
    }
    drawRect(0f, 0f, W, H * .55f, pt(shader = vertical(0f, H * .55f, intArrayOf(0xFF000000.toInt().a(.5f * env * (1 - flash * .7f)), CLEAR))))
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun setStorm(sh: RuntimeShader, W: Float, H: Float, seconds: Float, env: Float, flash: Float, flashX: Float, sheet: Float, at: Offset) {
    sh.setFloatUniform("uSize", W, H)
    sh.setFloatUniform("uTime", seconds)
    sh.setFloatUniform("uEnv", env)
    sh.setFloatUniform("uFlash", flash)
    sh.setFloatUniform("uFlashX", flashX)
    sh.setFloatUniform("uSheet", sheet)
    sh.setFloatUniform("uSheetAt", at.x, at.y)
}

private fun Canvas.impact(p: Offset, s: Float, d: Float, inten: Float, i: Int, v: Float) {
    val r = s * (.18f + i * .04f) * (.6f + inten * .6f)
    drawCircle(p.x, p.y, r, pt(add = true, shader = radial(p.x, p.y, r, intArrayOf(WHITE.a(.9f * inten), LMid.a(.5f * inten), LGlow.a(0f)), floatArrayOf(0f, .25f, 1f))))
    val u = (d / .07f).coerceIn(0f, 1f)
    if (u < 1f) oval(p.x, p.y + s * .2f, s * (.1f + u * .9f), s * (.03f + u * .22f), pt(add = true, stroke = 3 * (1 - u) + .5f, color = LMid.a(.7f * (1 - u))))
    // tia lửa điện bắn theo đường đạn
    val rnd = Random(31 * (i + 3))
    if (u < 1f) {
        val spark = pt(add = true, round = true, stroke = 1.6f * (1 - u) + .3f, color = mix(LCore, LMid, u).a(1 - u))
        repeat(16) {
            val ang = -PIf * (.1f + rnd.nextFloat() * .8f)
            val sp = s * (.25f + rnd.nextFloat() * .35f)
            fun at(q: Float) = Offset(p.x + cos(ang) * sp * q, p.y + sin(ang) * sp * q + s * .5f * q * q)
            val a0 = at(max(0f, u - .08f))
            val a1 = at(u)
            drawLine(a0.x, a0.y, a1.x, a1.y, spark)
        }
    }
    // hồ quang bò quanh người, đổi hình ~40ms
    val crawl = (d / .1f).coerceIn(0f, 1f)
    if (crawl < 1f) {
        val r2 = Random((v * 200).toInt() * 17 + i)
        repeat(3) {
            val a0 = r2.nextFloat() * TAU
            val a1 = a0 + .8f + r2.nextFloat() * 1.2f
            val rr = s * (.1f + r2.nextFloat() * .12f)
            val pa = p + Offset(cos(a0) * rr, sin(a0) * rr * 1.4f + s * .05f)
            val pb = p + Offset(cos(a1) * rr, sin(a1) * rr * 1.4f + s * .05f)
            zap(bolt(pa, pb, r2, 10, s * .02f), .9f, (1 - crawl) * .9f)
        }
    }
}
