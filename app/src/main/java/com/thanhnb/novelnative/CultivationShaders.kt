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

internal class FxShaders(val storm: RuntimeShader, val burst: RuntimeShader, val tamma: RuntimeShader)

/** null = máy < Android 13 hoặc shader biên dịch lỗi → các lớp Canvas tự thay thế. */
internal fun loadShaders(): FxShaders? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return null
    return runCatching { compileShaders() }.onFailure { Log.w("CultFx", "AGSL lỗi, dùng Canvas", it) }.getOrNull()
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun compileShaders() = FxShaders(RuntimeShader(StormSksl), RuntimeShader(BurstSksl), RuntimeShader(TammaSksl))
