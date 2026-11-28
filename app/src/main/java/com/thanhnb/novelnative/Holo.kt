package com.thanhnb.novelnative

import android.content.Context
import android.graphics.RuntimeShader
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * Viền hologram cho bìa: nghiêng máy thì màng óng ánh đổi màu, dải sáng chéo lướt qua, kim tuyến lóe.
 * Độ nghiêng chỉ đọc trong pha vẽ → đổi 1 uniform, không recompose/relayout.
 * Máy < Android 13 (không có AGSL), không có cảm biến xoay hoặc shader lỗi → giữ bìa thường.
 */
@Composable
fun Modifier.holo(corner: Float = 8f): Modifier {
    val shader = HoloShader ?: return this
    val tilt = rememberTilt() ?: return this
    return drawWithContent {
        drawContent()
        val t = tilt.value // đọc ở đây → chỉ vẽ lại khi nghiêng
        val r = corner.dp.toPx()
        shader.setFloatUniform("uSize", size.width, size.height)
        shader.setFloatUniform("uTilt", t.x, t.y)
        shader.setFloatUniform("uEdge", 5.dp.toPx())
        shader.setFloatUniform("uRadius", r)
        shader.setFloatUniform("uDot", 6.dp.toPx())
        // screen: cộng sáng mà không cháy trắng như Plus
        drawRoundRect(ShaderBrush(shader), cornerRadius = CornerRadius(r), blendMode = BlendMode.Screen)
    }
}

/** Độ nghiêng so với tư thế cầm máy gần đây, chuẩn hoá về -1..1; null = máy không có cảm biến xoay. */
@Composable
private fun rememberTilt(): androidx.compose.runtime.State<Offset>? {
    val ctx = LocalContext.current
    val sm = remember { ctx.getSystemService(Context.SENSOR_SERVICE) as? SensorManager }
    val sensor = remember { sm?.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR) } ?: return null
    val state = remember { androidx.compose.runtime.mutableStateOf(Offset.Zero) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val rot = FloatArray(9); val ori = FloatArray(3)
        var base: Offset? = null
        var smooth = Offset.Zero
        val l = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                SensorManager.getRotationMatrixFromVector(rot, e.values)
                SensorManager.getOrientation(rot, ori)
                val cur = Offset(ori[2], ori[1]) // roll = nghiêng ngang, pitch = nghiêng dọc
                // tư thế gốc trôi chậm theo tay → cầm máy kiểu gì cũng về giữa, chỉ phản ứng khi nghiêng
                val b = base?.let { it + (cur - it) * .015f } ?: cur
                base = b
                val raw = (cur - b) / .35f
                smooth += (Offset(raw.x.coerceIn(-1f, 1f), raw.y.coerceIn(-1f, 1f)) - smooth) * .18f // low-pass chống rung tay
                state.value = smooth
            }
            override fun onAccuracyChanged(s: Sensor?, a: Int) {}
        }
        // chỉ nghe khi màn đang hiện: app xuống nền thì tắt cảm biến
        val obs = LifecycleEventObserver { _, ev ->
            if (ev == Lifecycle.Event.ON_RESUME) sm!!.registerListener(l, sensor, SensorManager.SENSOR_DELAY_GAME)
            else if (ev == Lifecycle.Event.ON_PAUSE) { sm!!.unregisterListener(l); base = null }
        }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs); sm!!.unregisterListener(l) }
    }
    return state
}

// biên dịch 1 lần cho cả app; một shader dùng chung được vì mỗi lần vẽ đều đặt lại uniform
private val HoloShader: RuntimeShader? by lazy {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) null
    else runCatching { compileHolo() }.onFailure { Log.w("Holo", "AGSL lỗi, bỏ hologram", it) }.getOrNull()
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun compileHolo() = RuntimeShader(HoloSksl)

private const val HoloSksl = """
uniform float2 uSize;
uniform float2 uTilt;
uniform float uEdge;
uniform float uRadius;
uniform float uDot;

float hash(float2 p) { p = fract(p * float2(123.34, 456.21)); p += dot(p, p + 45.32); return fract(p.x * p.y); }
float noise(float2 p) {
  float2 i = floor(p); float2 f = fract(p); float2 u = f * f * (3.0 - 2.0 * f);
  return mix(mix(hash(i), hash(i + float2(1, 0)), u.x), mix(hash(i + float2(0, 1)), hash(i + float2(1, 1)), u.x), u.y);
}
float fbm(float2 p) { float v = 0.0; float a = 0.5; for (int k = 0; k < 4; k++) { v += a * noise(p); p *= 2.03; a *= 0.5; } return v; }

half4 main(float2 xy) {
  float2 uv = xy / uSize.y;
  float2 t = uTilt;
  // nền gồ ghề: fbm + domain warping, lệch theo độ nghiêng như khuấy sơn
  float2 q = float2(fbm(uv * 3.0 + t * 0.6), fbm(uv * 3.0 + float2(5.2, 1.3) - t * 0.6));
  // màng mỏng: độ dày 0,45–1,95 → RGB là cosin của độ dày, mỗi kênh một bước sóng
  float th = 0.45 + 1.5 * fbm(uv * 2.5 + q * 1.6 + t * 0.8);
  float3 film = 0.5 + 0.5 * cos(6.2832 * (th * float3(1.0, 1.18, 1.37) + (t.x + t.y) * 0.35));
  film = film * film; // bình phương → màu rực hơn, bớt phấn nhạt
  // dải sáng chéo trượt theo độ nghiêng
  float d = (uv.x + uv.y) * 0.7 - (0.55 + t.x * 0.8 - t.y * 0.8);
  float sheen = exp(-d * d * 30.0);
  // kim tuyến: mỗi ô lưới hash quyết định có hạt không, chỉ lóe ở đúng góc nghiêng
  // hạt tròn nhỏ giữa ô (không phải cả ô vuông) để khỏi trông như điểm ảnh chết
  float2 cell = floor(xy / uDot);
  float h = hash(cell);
  float dot = 1.0 - smoothstep(0.12, 0.32, length(fract(xy / uDot) - 0.5));
  float spark = step(0.975, h) * dot * pow(max(0.0, cos(6.2832 * (h * 7.0 + (t.x - t.y) * 1.5))), 20.0);
  // mặt nạ: khung foil đậm ở mép (SDF chữ nhật bo góc), giữa bìa chỉ phớt để vẫn thấy ảnh
  float2 k = abs(xy - uSize * 0.5) - (uSize * 0.5 - uRadius);
  float inside = -(length(max(k, 0.0)) + min(max(k.x, k.y), 0.0) - uRadius);
  float frame = 1.0 - smoothstep(uEdge - 1.0, uEdge, inside);
  float a = clamp(max(frame * 0.85, 0.07 + 0.35 * sheen) + spark, 0.0, 1.0);
  float3 c = film * 0.8 + sheen * 0.5 + spark;
  return half4(half3(min(c, 1.0) * a), half(a));
}
"""
