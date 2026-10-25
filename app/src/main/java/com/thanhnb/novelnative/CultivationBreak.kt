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
private val StrikeAt = floatArrayOf(.38f, .56f, .74f) // tribulationHits
private const val CloudEnd = .18f
private const val TammaSec = 2.2f
private const val TAU = (2 * PI).toFloat()
private const val PIf = PI.toFloat()

// ---------------- màu / paint / easing ----------------

private const val WHITE = 0xFFFFFFFF.toInt()
private const val CLEAR = 0x00000000
private fun Int.a(x: Float): Int = (this and 0xFFFFFF) or ((x.coerceIn(0f, 1f) * 255f).roundToInt() shl 24)
private fun mix(a: Int, b: Int, t: Float): Int {
    fun ch(s: Int) = (((a shr s) and 255) + ((((b shr s) and 255) - ((a shr s) and 255)) * t)).roundToInt().coerceIn(0, 255) shl s
    return ch(24) or ch(16) or ch(8) or ch(0)
}

private val AddMode = PorterDuffXfermode(PorterDuff.Mode.ADD)

/** Paint kiểu Flutter: [blur] là sigma MaskFilter.blur (Android nhận bán kính ≈ sigma·√3). */
private fun pt(
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

private fun radial(cx: Float, cy: Float, r: Float, colors: IntArray, stops: FloatArray? = null) =
    RadialGradient(cx, cy, max(r, .01f), colors, stops, Shader.TileMode.CLAMP)

private fun vertical(top: Float, bottom: Float, colors: IntArray, stops: FloatArray? = null) =
    LinearGradient(0f, top, 0f, max(bottom, top + .01f), colors, stops, Shader.TileMode.CLAMP)

private val EaseInOut = CubicBezierEasing(.42f, 0f, .58f, 1f)
private val EaseOut = CubicBezierEasing(0f, 0f, .58f, 1f)
private val EaseIn = CubicBezierEasing(.42f, 0f, 1f, 1f)
private val EaseOutCubic = CubicBezierEasing(.215f, .61f, .355f, 1f)
private val EaseOutBack = CubicBezierEasing(.175f, .885f, .32f, 1.275f)
private fun Easing.at(x: Float) = transform(x.coerceIn(0f, 1f))

/** Curves.elasticOut (chu kỳ 0,4) của Flutter. */
private fun elasticOut(x: Float): Float {
    if (x <= 0f || x >= 1f) return x.coerceIn(0f, 1f)
    return (2.0.pow(-10.0 * x) * sin((x - .1) * 2 * PI / .4) + 1).toFloat()
}

private fun Path.poly(pts: List<Offset>, close: Boolean = false) = apply {
    pts.forEachIndexed { i, p -> if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y) }
    if (close) close()
}

private fun Canvas.oval(cx: Float, cy: Float, w: Float, h: Float, p: Paint) = drawOval(RectF(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2), p)

/** Vẽ trên Canvas Android theo dp (khớp đơn vị Flutter): [block] nhận (canvas, rộng dp, cao dp). */
private fun DrawScope.dpCanvas(block: Canvas.(Float, Float) -> Unit) = drawIntoCanvas { c ->
    val cv = c.nativeCanvas
    cv.save()
    cv.scale(density, density)
    cv.block(size.width / density, size.height / density)
    cv.restore()
}

// ---------------- shader AGSL (port từ shaders/*.frag) ----------------

private const val NoiseSksl = """
float hash(float3 p) {
  p = fract(p * 0.3183099 + 0.1);
  p *= 17.0;
  return fract(p.x * p.y * p.z * (p.x + p.y + p.z));
}
float noise(float3 x) {
  float3 i = floor(x);
  float3 f = fract(x);
  f = f * f * (3.0 - 2.0 * f);
  return mix(mix(mix(hash(i), hash(i + float3(1, 0, 0)), f.x),
                 mix(hash(i + float3(0, 1, 0)), hash(i + float3(1, 1, 0)), f.x), f.y),
             mix(mix(hash(i + float3(0, 0, 1)), hash(i + float3(1, 0, 1)), f.x),
                 mix(hash(i + float3(0, 1, 1)), hash(i + float3(1, 1, 1)), f.x), f.y),
             f.z);
}
"""

/** Trần mây kiếp thể tích (storm.frag): ray-march nhìn ngửa lên, sét là nguồn sáng nằm trong mây. */
private const val StormSksl = """
uniform float2 uSize;
uniform float uTime;
uniform float uEnv;
uniform float uFlash;
uniform float uFlashX;
uniform float uSheet;
uniform float2 uSheetAt;
$NoiseSksl
float fbm(float3 p) {
  float a = 0.5; float s = 0.0;
  for (int i = 0; i < 4; i++) { s += a * noise(p); p = p * 2.02 + float3(3.1, 1.7, 5.3); a *= 0.5; }
  return s;
}
const float BASE = 1.0;
const float TOP = 2.4;
float3 ray(float2 px) {
  float2 uv = (px - float2(uSize.x * 0.5, 0.0)) / uSize.y;
  return normalize(float3(uv.x * 1.1, 0.42 - uv.y, 1.0));
}
float3 lightAt(float2 px) { float3 d = ray(px); return d * ((BASE + 0.35) / max(d.y, 0.08)); }
float density(float3 p) {
  float3 q = p * 0.55 + float3(uTime * 0.08, 0.0, uTime * 0.05);
  float n = fbm(q);
  float h = (p.y - BASE) / (TOP - BASE);
  float prof = smoothstep(0.0, 0.25, h) * smoothstep(1.0, 0.55, h);
  return clamp((n - 0.32) * 3.0, 0.0, 1.0) * prof;
}
half4 main(float2 fc) {
  float3 rd = ray(fc);
  if (rd.y < 0.06 || uEnv <= 0.0) return half4(0.0);
  float t0 = BASE / rd.y;
  float t1 = min(TOP / rd.y, t0 + 5.0);
  float dt = (t1 - t0) / 14.0;
  float3 L1 = lightAt(float2(uFlashX, uSize.y * 0.02));
  float3 L2 = lightAt(uSheetAt);
  float T = 1.0;
  float3 col = float3(0.0);
  float jit = hash(float3(fc, uTime));
  for (int i = 0; i < 14; i++) {
    float3 p = rd * (t0 + (float(i) + jit) * dt);
    float d = density(p);
    if (d > 0.002) {
      float l1 = uFlash * 3.2 / (1.0 + dot(p - L1, p - L1) * 1.6);
      float l2 = uSheet * 2.6 / (1.0 + dot(p - L2, p - L2) * 2.4);
      float h = (p.y - BASE) / (TOP - BASE);
      float3 amb = mix(float3(0.20, 0.22, 0.34), float3(0.05, 0.05, 0.10), h);
      float edge = 1.0 - 0.7 * d;
      float3 e = amb + (float3(0.62, 0.72, 1.0) * l1 + float3(0.55, 0.6, 1.0) * l2) * edge;
      float a = d * dt * 1.8;
      col += T * e * a;
      T *= exp(-a * 1.6);
      if (T < 0.03) break;
    }
  }
  float far = smoothstep(0.06, 0.2, rd.y);
  float alpha = (1.0 - T) * far * uEnv;
  col = 1.0 - exp(-col * 1.4);
  return half4(half3(col * far * uEnv), half(alpha));
}
"""

/** Godray + bloom phủ cộng sáng lúc lộ kết quả đại cảnh giới (breakthrough.frag). */
private const val BurstSksl = """
uniform float2 uSize;
uniform float uT;
uniform float3 uColor;
uniform float2 uCenter;
float hash1(float n) { return fract(sin(n) * 43758.5453); }
half4 main(float2 fc) {
  float2 d = fc - uCenter;
  float r = length(d);
  float ang = atan(d.y, d.x);
  float t = clamp(uT, 0.0, 1.0);
  float fade = 1.0 - t;
  float core = exp(-r / (36.0 + 240.0 * t)) * fade * 1.5;
  float ringR = 24.0 + t * 340.0;
  float ring = exp(-pow((r - ringR) / (16.0 + 34.0 * t), 2.0)) * fade * 1.1;
  float seed = floor((ang + 3.14159) / 6.28318 * 32.0);
  float streak = 0.5 + 0.5 * sin(ang * 32.0 + hash1(seed) * 12.0);
  streak = pow(streak, 8.0);
  float rayFall = exp(-r / (140.0 + 300.0 * t)) * smoothstep(0.0, 40.0, r);
  float glow = core + ring + streak * rayFall * fade * 1.2;
  float2 uv = fc / uSize;
  float edge = smoothstep(0.0, 0.14, uv.x) * smoothstep(0.0, 0.14, 1.0 - uv.x)
             * smoothstep(0.0, 0.14, uv.y) * smoothstep(0.0, 0.14, 1.0 - uv.y);
  glow *= edge;
  float3 col = uColor * glow + float3(1.0) * pow(core, 2.2) * 0.7 * edge;
  return half4(half3(col), half(clamp(glow, 0.0, 1.0)));
}
"""

/** Khối ma khí thể tích xoáy quanh mặt quỷ (tamma.frag); 2 lượt: sau ảnh (có lõi) và trước ảnh (khói mỏng). */
private const val TammaSksl = """
uniform float2 uCenter;
uniform float uRadius;
uniform float uTime;
uniform float uV;
uniform float uWin;
uniform float uFront;
$NoiseSksl
float fbm(float3 p) {
  float a = 0.5; float s = 0.0;
  for (int i = 0; i < 3; i++) { s += a * noise(p); p = p * 2.03 + float3(1.7, 9.2, 3.1); a *= 0.5; }
  return s;
}
float2 rot(float2 v, float a) { float c = cos(a); float s = sin(a); return float2(c * v.x + s * v.y, -s * v.x + c * v.y); }
half4 main(float2 fc) {
  float2 uv = (fc - uCenter) / uRadius;
  uv.y = -uv.y;
  float win = uWin;
  float intro = smoothstep(0.0, 0.25, uV);
  float settle = smoothstep(0.55, 1.0, uV);
  float R = mix(0.35, 1.0, intro) * mix(1.0 + 0.2 * settle, 1.0 - 0.4 * settle, win);
  float dens = mix(1.0 + 0.6 * settle, 1.0 - 0.55 * settle, win);
  float spin = mix(1.9, 1.0, win);
  float yaw = 0.45 * sin(uTime * 0.9);
  float pitch = 0.32 + 0.08 * sin(uTime * 0.7);
  float3 ro = float3(0.0, 0.0, -3.2);
  float3 rd = normalize(float3(uv * 0.62, 1.9));
  ro.yz = rot(ro.yz, pitch); rd.yz = rot(rd.yz, pitch);
  ro.xz = rot(ro.xz, yaw);   rd.xz = rot(rd.xz, yaw);
  float bR = 1.35 * max(R, 0.6);
  float b = dot(ro, rd);
  float c = dot(ro, ro) - bR * bR;
  float h = b * b - c;
  if (h <= 0.0) return half4(0.0);
  h = sqrt(h);
  float t0 = -b - h; float t1 = -b + h;
  float tm = -b;
  if (uFront > 0.5) t1 = tm; else t0 = tm;
  float3 smokeCol = mix(float3(0.34, 0.03, 0.05), float3(0.20, 0.06, 0.42), win);
  float3 coreCol = mix(float3(1.0, 0.28, 0.12), float3(0.72, 0.52, 1.0), win);
  coreCol = mix(coreCol, float3(1.0, 0.95, 1.0), settle * win);
  float flick = 0.85 + 0.15 * sin(uTime * 23.0) * sin(uTime * 7.3) * (1.0 - win);
  float dt = (t1 - t0) / 22.0;
  float T = 1.0;
  float3 col = float3(0.0);
  float frontThin = mix(1.0, 0.45, uFront);
  for (int i = 0; i < 22; i++) {
    float3 p = ro + rd * (t0 + (float(i) + 0.5) * dt);
    float r = length(p);
    float3 q = p;
    float rr = length(q.xz);
    q.xz = rot(q.xz, uTime * spin + 1.6 / (0.4 + rr) + q.y * 1.2);
    float n = fbm(q * 2.1 + float3(0.0, -uTime * 0.8, 0.0));
    float shell = 1.0 - r / R;
    float d = clamp(n * 1.5 - 0.5 + shell * 0.9, 0.0, 1.0) * smoothstep(-0.1, 0.25, shell);
    float tor = length(float2(rr - 0.95 * R, p.y + 0.15 * sin(atan(p.z, p.x) * 3.0 + uTime * 2.0)));
    d += smoothstep(0.22 * R, 0.0, tor) * (0.4 + 0.9 * n);
    d *= dens * frontThin;
    if (d > 0.001) {
      float light = exp(-r * 2.6 / max(R, 0.3)) * 3.2 * flick;
      float vein = n * n * 2.2;
      float3 e = smokeCol * (0.25 + vein) + coreCol * light * (0.4 + vein);
      float a = d * dt * 4.5;
      col += T * e * a;
      T *= exp(-a * 1.5);
      if (T < 0.02) break;
    }
  }
  if (uFront < 0.5) {
    float g = length(uv) / max(R, 0.3);
    col += coreCol * exp(-g * 5.0) * 1.3 * flick * intro * T;
  }
  col = 1.0 - exp(-col * 1.3);
  return half4(half3(col), half(clamp(1.0 - T, 0.0, 1.0)));
}
"""

private class FxShaders(val storm: RuntimeShader, val burst: RuntimeShader, val tamma: RuntimeShader)

/** null = máy < Android 13 hoặc shader biên dịch lỗi → các lớp Canvas tự thay thế. */
private fun loadShaders(): FxShaders? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return null
    return runCatching { compileShaders() }.onFailure { Log.w("CultFx", "AGSL lỗi, dùng Canvas", it) }.getOrNull()
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun compileShaders() = FxShaders(RuntimeShader(StormSksl), RuntimeShader(BurstSksl), RuntimeShader(TammaSksl))

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

private fun deg(rad: Float) = Math.toDegrees(rad.toDouble()).toFloat()

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

private fun Canvas.lightning(W: Float, H: Float, v: Float, from: Float, to: Float, hit: Offset, cloud: RuntimeShader?, seconds: Float) {
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
private fun Canvas.qiVortex(time: Float, intro: Float, color: Int, front: Boolean, major: Boolean) {
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
private fun Canvas.tammaSmoke(W: Float, H: Float, t: Float, subdued: Boolean) {
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
private fun Canvas.tammaVolume(sh: RuntimeShader, cx: Float, cy: Float, radius: Float, time: Float, v: Float, win: Boolean, front: Boolean) {
    sh.setFloatUniform("uCenter", cx, cy)
    sh.setFloatUniform("uRadius", radius)
    sh.setFloatUniform("uTime", time)
    sh.setFloatUniform("uV", v)
    sh.setFloatUniform("uWin", if (win) 1f else 0f)
    sh.setFloatUniform("uFront", if (front) 1f else 0f)
    val b = radius * 1.5f
    drawRect(cx - b, cy - b, cx + b, cy + b, pt(shader = sh))
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
