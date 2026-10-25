package com.thanhnb.novelnative

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateOffsetAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalTime
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/** Cảm xúc của linh vật. Idle = bình thường; Sleepy tự đến khi không ai chạm 25 giây. */
enum class OrbMood { Idle, Happy, Love, Wow, Sad, Sleepy, Think, Cheer, Angry, Wink }

private enum class Eye { Round, Arc, Line, Heart, Star, Wink }

/** Bộ thông số khuôn mặt — các số được lò xo nội suy nên đổi cảm xúc là mặt "chảy" sang cảm xúc mới. */
private class Face(val eye: Eye, val open: Float, val smile: Float, val mouth: Float, val brow: Float, val blush: Float, val lookY: Float = 0f)

private fun OrbMood.face() = when (this) {
    OrbMood.Idle -> Face(Eye.Round, 1f, .35f, 0f, 0f, .35f)
    OrbMood.Happy -> Face(Eye.Arc, 1f, .9f, .5f, 0f, .8f)
    OrbMood.Love -> Face(Eye.Heart, 1f, .8f, .2f, 0f, 1f)
    OrbMood.Wow -> Face(Eye.Round, 1.25f, 0f, 1f, 0f, .2f)
    OrbMood.Sad -> Face(Eye.Round, .9f, -.7f, 0f, 1f, .15f, .2f)
    OrbMood.Sleepy -> Face(Eye.Line, 0f, .1f, .25f, 0f, .4f)
    OrbMood.Think -> Face(Eye.Round, 1f, -.1f, 0f, .3f, .2f, -.7f)
    OrbMood.Cheer -> Face(Eye.Star, 1f, 1f, 1f, 0f, 1f)
    OrbMood.Angry -> Face(Eye.Round, .75f, -.5f, 0f, -1f, .1f)
    OrbMood.Wink -> Face(Eye.Wink, 1f, .9f, .4f, 0f, .8f)
}

/** Trạng thái dùng chung cho mọi linh vật trong app: điểm đang chạm (toạ độ cửa sổ) và sự kiện cần phản ứng. */
object Mascot {
    var gaze by mutableStateOf<Offset?>(null)
    var event by mutableStateOf<Pair<Int, OrbMood>?>(null)
    private var n = 0
    fun react(m: OrbMood) { event = ++n to m }
}

/** Gắn ở gốc app: ghi lại ngón tay đang chạm ở đâu (không tiêu thụ sự kiện), nhả tay 0,9 giây thì linh vật thôi nhìn. */
fun Modifier.mascotGaze(): Modifier = pointerInput(Unit) {
    coroutineScope {
        var clear: Job? = null
        awaitPointerEventScope {
            while (true) {
                val down = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.pressed }
                if (down != null) { clear?.cancel(); Mascot.gaze = down.position }
                else if (Mascot.gaze != null) { clear?.cancel(); clear = launch { delay(900); Mascot.gaze = null } }
            }
        }
    }
}

private val INK = Color(0xFF2A1830)
private val PINK = Color(0xFFFF8FB1)
private val REACTIONS = listOf(OrbMood.Happy, OrbMood.Wow, OrbMood.Wink, OrbMood.Cheer, OrbMood.Love, OrbMood.Think)

/**
 * Quả cầu 3D nhỏ dễ thương đặt ở header, vẽ bằng Canvas (không cần GL/AGSL nên chạy từ API 26): thân là chồng gradient tròn
 * (sáng trên-trái, bóng dưới-phải, viền kính, đốm sáng) + bóng đổ dưới đất; khuôn mặt dịch và co ngang theo
 * hướng ngón tay để có cảm giác dính trên mặt cầu. Chạm = phản ứng đổi cảm xúc, giữ lâu = yêu, chọc 5 lần = giận,
 * kéo = nghiêng theo ngón tay. Khi ngón tay chạm ở BẤT KỲ đâu trong app ([Mascot.gaze]) mặt quay về hướng đó; [Mascot.react] gây phản ứng từ code.
 */
@Composable
fun Orb(
    mood: OrbMood = OrbMood.Idle,
    modifier: Modifier = Modifier.size(32.dp),
) {
    val time = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        var t0 = -1L
        while (true) androidx.compose.runtime.withFrameNanos { n -> if (t0 < 0) t0 = n; time.floatValue = (n - t0) / 1e9f }
    }

    var reaction by remember { mutableStateOf<OrbMood?>(null) }
    var tapN by remember { mutableIntStateOf(0) }
    var asleep by remember { mutableStateOf(false) }
    val lastTouch = remember { mutableFloatStateOf(0f) }
    val night = remember { LocalTime.now().hour.let { it >= 23 || it < 5 } }
    val shown = reaction ?: if ((asleep || night) && mood == OrbMood.Idle && Mascot.gaze == null) OrbMood.Sleepy else mood
    var seen by remember { mutableStateOf(Mascot.event?.first) }
    LaunchedEffect(Mascot.event) { Mascot.event?.let { if (it.first != seen) { seen = it.first; reaction = it.second; tapN++ } } }
    LaunchedEffect(tapN) { if (reaction != null) { delay(1900); reaction = null } }
    LaunchedEffect(Unit) { while (true) { delay(1000); asleep = time.floatValue - lastTouch.floatValue > 25f } }

    val blinkAt = remember { mutableFloatStateOf(-9f) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(Random.nextLong(2200, 5200))
            blinkAt.floatValue = time.floatValue
            if (Random.nextInt(4) == 0) { delay(260); blinkAt.floatValue = time.floatValue }
        }
    }

    val f = shown.face()
    val spec = spring<Float>(dampingRatio = .6f, stiffness = 260f)
    val open = animateFloatAsState(f.open, spec, label = "open")
    val smile = animateFloatAsState(f.smile, spec, label = "smile")
    val mouth = animateFloatAsState(f.mouth, spec, label = "mouth")
    val brow = animateFloatAsState(f.brow, spec, label = "brow")
    val blush = animateFloatAsState(f.blush, spec, label = "blush")
    val lookY = animateFloatAsState(f.lookY, spec, label = "lookY")
    val fxLove = animateFloatAsState(if (shown == OrbMood.Love) 1f else 0f, tween(400), label = "fxLove")
    val fxCheer = animateFloatAsState(if (shown == OrbMood.Cheer) 1f else 0f, tween(400), label = "fxCheer")
    val fxSad = animateFloatAsState(if (shown == OrbMood.Sad) 1f else 0f, tween(400), label = "fxSad")
    val fxAngry = animateFloatAsState(if (shown == OrbMood.Angry) 1f else 0f, tween(300), label = "fxAngry")

    // mỗi lần đổi cảm xúc: nảy thạch (squash) và nhảy lên nếu đang phấn khích
    val squash = remember { Animatable(0f) }
    val hop = remember { Animatable(0f) }
    LaunchedEffect(shown) { squash.snapTo(1f); squash.animateTo(0f, spring(.3f, 260f)) }
    LaunchedEffect(shown) {
        if (shown == OrbMood.Wow || shown == OrbMood.Cheer || shown == OrbMood.Happy) {
            hop.snapTo(0f); hop.animateTo(1f, tween(130)); hop.animateTo(0f, spring(.45f, 320f))
        }
    }

    var box by remember { mutableStateOf(IntSize.Zero) }
    var ctr by remember { mutableStateOf<Offset?>(null) }
    var touch by remember { mutableStateOf<Offset?>(null) }
    var lookTarget by remember { mutableStateOf(Offset.Zero) }
    // đang bị chạm thì nhìn ngón tay đó; ngón tay ở chỗ khác trong app thì nhìn về hướng ấy (xa hơn → liếc rõ hơn)
    val g = Mascot.gaze
    val aim = when {
        touch != null -> lookTarget
        g != null && ctr != null -> (g - ctr!!).let { v -> val d = v.getDistance(); if (d < 1f) Offset.Zero else v / d * min(1f, d / 360f) }
        else -> Offset.Zero
    }
    val touchA = animateFloatAsState(if (touch != null || g != null) 1f else 0f, tween(250), label = "touchA")
    val look = animateOffsetAsState(aim, spring(.55f, 380f), label = "look")
    // hướng nhìn hiện tại = hướng nhắm đã làm mượt + chút đảo mắt khi rảnh; dùng chung cho vẽ và nghiêng 3D
    // thân luôn pastel sáng (mặt mực đậm mới nổi), viền đậm cùng tông để rõ nét ở cỡ 30dp như sticker
    val darkTheme = MaterialTheme.colorScheme.background.luminance() < .5f
    // màu thân theo cảm xúc; Idle giữ màu nhấn của app. Chuyển màu trong 0,45 giây để thân "ửng" dần chứ không nháy
    val primary by animateColorAsState(shown.tint() ?: MaterialTheme.colorScheme.primary, tween(450), label = "tint")
    val bodyTop = lerp(primary, Color.White, if (darkTheme) .86f else .8f)
    val bodyBot = lerp(primary, Color.White, if (darkTheme) .55f else .42f)
    val rimCol = if (darkTheme) lerp(primary, Color.White, .25f) else lerp(primary, Color.Black, .3f)
    val faceCol = INK
    val armUp = animateFloatAsState(
        when (shown) { OrbMood.Cheer -> 1f; OrbMood.Wow -> .8f; OrbMood.Happy, OrbMood.Wink -> .55f; OrbMood.Love -> .3f; OrbMood.Sad, OrbMood.Sleepy -> -.35f; else -> 0f },
        spring(.5f, 220f), label = "armUp",
    )
    val flameK = animateFloatAsState(
        when (shown) { OrbMood.Cheer, OrbMood.Angry -> 1.35f; OrbMood.Wow, OrbMood.Happy -> 1.15f; OrbMood.Sad -> .7f; OrbMood.Sleepy -> .55f; else -> 1f },
        spring(.5f, 200f), label = "flameK",
    )
    val lookNow = { t: Float ->
        Offset(
            (look.value.x + sin(t * .6f) * .3f * (1f - touchA.value)).coerceIn(-1f, 1f),
            (look.value.y + cos(t * .43f) * .15f * (1f - touchA.value)).coerceIn(-1f, 1f),
        )
    }
    val taps = remember { ArrayDeque<Float>() }
    LaunchedEffect(Unit) {
        val idle = listOf(OrbMood.Wink, OrbMood.Happy, OrbMood.Think, OrbMood.Wow)
        while (true) {
            delay(Random.nextLong(6000, 13000))
            if (reaction == null && touch == null && Mascot.gaze == null && mood == OrbMood.Idle && !asleep && !night) { reaction = idle.random(); tapN++ }
        }
    }

    var next by remember { mutableIntStateOf(0) }

    Canvas(
        modifier
            .onSizeChanged { box = it }
            .onGloballyPositioned { ctr = it.positionInWindow() + Offset(it.size.width / 2f, it.size.height / 2f) }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val tDown = time.floatValue
                    val slop = 24.dp.toPx()
                    fun aim(p: Offset) {
                        val c = Offset(box.width / 2f, box.height * .56f)
                        val r = min(box.width, box.height) * .42f
                        var v = (p - c) / (r * 2.2f)
                        if (v.getDistance() > 1f) v /= v.getDistance()
                        lookTarget = v
                    }
                    touch = down.position; aim(down.position)
                    lastTouch.floatValue = tDown
                    val wasAsleep = asleep
                    asleep = false
                    var moved = false
                    do {
                        val ch = awaitPointerEvent().changes.first()
                        if ((ch.position - down.position).getDistance() > slop) moved = true
                        touch = ch.position; aim(ch.position)
                        ch.consume()
                    } while (ch.pressed)
                    touch = null; lookTarget = Offset.Zero
                    if (moved) return@awaitEachGesture
                    val held = time.floatValue - tDown
                    val now = time.floatValue
                    taps.addLast(now); while (taps.isNotEmpty() && now - taps.first() > 3f) taps.removeFirst()
                    reaction = when {
                        wasAsleep -> OrbMood.Wow
                        held > .5f -> OrbMood.Love
                        taps.size >= 5 -> OrbMood.Angry.also { taps.clear() }
                        else -> REACTIONS[next++ % REACTIONS.size].let { if (it == shown) REACTIONS[next++ % REACTIONS.size] else it }
                    }
                    tapN++
                }
            },
    ) {
        val t = time.floatValue
        val w = this.size.width
        val r = min(w, this.size.height) * .38f
        val cx = w / 2f
        val cy0 = this.size.height * .55f
        val cy = cy0 + sin(t * 1.7f) * r * .035f - hop.value * r * .22f
        val c = Offset(cx, cy)
        val s = squash.value
        val breath = 1f + .012f * sin(t * 2.3f)
        val lk = lookNow(t)
        val lx = lk.x
        val ly = lk.y

        // bóng đổ: nhỏ và nhạt đi khi nhảy lên
        val sh = 1f - hop.value * .45f
        drawOval(Color.Black.copy(alpha = .13f * sh), Offset(cx - r * .8f * sh, cy0 + r * BODY_RY * .98f), Size(r * 1.6f * sh, r * .2f))
        scale((1f + .10f * s) * breath, (1f - .16f * s) * breath, pivot = Offset(cx, cy0 + r)) {
            rotate(lx * 4f, pivot = c) {
                drawFlame(c, r, t, sin(t * 2.2f) * .15f - lx * .4f - s * .4f - hop.value * .3f, flameK.value, fxAngry.value)
                drawArms(c, r, t, armUp.value, bodyBot, rimCol)
                drawBody(c, r, bodyTop, bodyBot, rimCol, t)
                drawFace(
                    c, r, lx, ly + lookY.value, t, f.eye, open.value, smile.value, mouth.value, brow.value, blush.value,
                    blink = blinkFactor(t - blinkAt.floatValue), tear = fxSad.value, angry = fxAngry.value, ink = faceCol,
                )
            }
        }

        // hiệu ứng bay ngoài thân (không bị bóp méo theo squash)
        if (fxLove.value > .01f) for (i in 0 until 5) {
            val p = (t * .55f + i / 5f) % 1f
            val fade = sin(PI.toFloat() * p)
            val hx = cx + sin(p * 6.28f + i * 1.7f) * r * .45f + (i - 2) * r * .22f
            val hy = cy - r * .75f - p * r * 1.1f
            drawPath(heart(hx, hy, r * .11f * (.6f + .6f * fade)), Color(0xFFFF5C8A).copy(alpha = fxLove.value * fade))
        }
        if (fxCheer.value > .01f) for (i in 0 until 7) {
            val a = i * 2f * PI.toFloat() / 7f + t * .6f
            val d = r * (1.3f + .1f * sin(t * 3f + i))
            val sz = r * .13f * abs(sin(t * 4f + i * 1.3f))
            drawPath(sparkle(cx + cos(a) * d, cy + sin(a) * d * .85f, sz), (if (i % 2 == 0) Color(0xFFFFD54F) else Color.White).copy(alpha = fxCheer.value))
        }
    }
}

private fun blinkFactor(d: Float) = if (d in 0f..0.16f) 1f - sin(PI.toFloat() * d / 0.16f) else 1f

/**
 * Thân hạt đậu: đáy bè và phẳng hơn đỉnh (đứng vững, trông "ngồi" chứ không lơ lửng), viền chỉ dẻo rất nhẹ.
 * Gradient sáng trên → đậm dưới, viền sticker, đốm bóng kiểu đồ chơi nhựa ở trên-trái.
 */
private fun DrawScope.drawBody(c: Offset, r: Float, top: Color, bot: Color, rim: Color, t: Float) {
    val rx = r * BODY_RX
    val ry = r * BODY_RY
    val n = 64
    val path = Path()
    for (i in 0..n) {
        val th = i * 2f * PI.toFloat() / n
        val sn = sin(th)
        val k = 1f + .012f * sin(3f * th + t * 1.1f)
        val x = c.x + cos(th) * rx * (1f + .07f * sn) * k
        val y = c.y + sn * ry * (if (sn > 0f) 1f - .1f * sn * sn else 1f) * k
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    drawPath(path, Brush.verticalGradient(listOf(top, bot), startY = c.y - ry, endY = c.y + ry))
    clipPath(path) {
        // ánh dội nhẹ dưới đáy cho có khối
        drawOval(Color.White.copy(.25f), Offset(c.x - rx * .55f, c.y + ry * .62f), Size(rx * 1.1f, ry * .5f))
        drawOval(Color.White.copy(.7f), Offset(c.x - rx * .68f, c.y - ry * .78f), Size(rx * .42f, ry * .24f))
        drawCircle(Color.White.copy(.8f), r * .055f, Offset(c.x - rx * .2f, c.y - ry * .78f))
    }
    drawPath(path, rim, style = Stroke(r * .075f, join = StrokeJoin.Round))
}

/** Hai tay nhỏ thò ra hai bên (vẽ trước thân nên chỉ lộ nửa ngoài): rủ khi buồn/ngủ, giơ và vẫy khi vui. */
private fun DrawScope.drawArms(c: Offset, r: Float, t: Float, up: Float, fill: Color, rim: Color) {
    val wave = if (up > .5f) sin(t * 14f) * 14f * (up - .5f) * 2f else sin(t * 2f) * 4f
    for (side in intArrayOf(-1, 1)) {
        val sh = Offset(c.x + side * r * BODY_RX * .9f, c.y + r * BODY_RY * .48f)
        rotate(side * (40f - up * 110f + wave * side), pivot = sh) {
            val o = Offset(sh.x - r * .15f + side * r * .14f, sh.y - r * .1f)
            drawOval(fill, o, Size(r * .3f, r * .2f))
            drawOval(rim, o, Size(r * .3f, r * .2f), style = Stroke(r * .06f))
        }
    }
}

/** Đốm linh hoả trên đỉnh đầu: lập loè liên tục, ngả theo [sway] (radian), [k] = độ lớn theo cảm xúc, giận thì đỏ lên. */
private fun DrawScope.drawFlame(c: Offset, r: Float, t: Float, sway: Float, k: Float, angry: Float) {
    val base = Offset(c.x, c.y - r * BODY_RY * .82f)
    fun flame(len: Float, w: Float, col: Brush) {
        val flick = 1f + .08f * sin(t * 11f) + .05f * sin(t * 17f + 1f)
        val l = len * flick
        val tip = base + Offset(sin(sway) * l, -cos(sway) * l)
        drawPath(Path().apply {
            moveTo(base.x - w, base.y)
            cubicTo(base.x - w * 1.25f, base.y - l * .55f, tip.x - w * .35f, tip.y + l * .35f, tip.x, tip.y)
            cubicTo(tip.x + w * .55f, tip.y + l * .4f, base.x + w * 1.3f, base.y - l * .5f, base.x + w, base.y)
            close()
        }, col)
    }
    val outer = lerp(Color(0xFFFF9A3C), Color(0xFFE53935), angry)
    val mid = lerp(Color(0xFFFFC94D), Color(0xFFFF7043), angry)
    flame(r * .9f * k, r * .24f * k, Brush.verticalGradient(listOf(mid, outer), startY = base.y - r * .9f * k, endY = base.y))
    flame(r * .5f * k, r * .12f * k, Brush.verticalGradient(listOf(Color(0xFFFFF6D6), mid), startY = base.y - r * .5f * k, endY = base.y))
}

private const val BODY_RX = 1.1f
private const val BODY_RY = .94f

/** Điểm trên mặt cầu sau khi quay: toạ độ màn hình, độ co ngang (cos kinh độ) và độ hiện (mờ dần khi vòng ra sau mép). */
private class Pt(val x: Float, val y: Float, val sx: Float, val vis: Float)

private fun proj(c: Offset, r: Float, lon0: Float, lat0: Float, yaw: Float, pitch: Float): Pt {
    val lon = lon0 + yaw
    val lat = lat0 + pitch
    val facing = cos(lon) * cos(lat)
    return Pt(c.x + r * BODY_RX * .97f * sin(lon) * cos(lat), c.y + r * BODY_RY * .97f * sin(lat), cos(lon).coerceIn(0f, 1f), (facing * 5f).coerceIn(0f, 1f))
}

private inline fun DrawScope.on(p: Pt, block: DrawScope.() -> Unit) {
    if (p.vis > .01f) scale(p.sx.coerceAtLeast(.12f), 1f, pivot = Offset(p.x, p.y)) { block() }
}

/**
 * Mặt được đặt thẳng trên mặt cầu (kinh độ/vĩ độ) rồi quay theo hướng nhìn: mắt trượt về mép, bị bóp dẹt dần và mờ đi
 * khi vòng ra sau — nhờ vậy "quay đầu" nhìn rất rõ chỉ bằng nét vẽ phẳng.
 */
private fun DrawScope.drawFace(
    c: Offset, r: Float, lx: Float, ly: Float, t: Float, eye: Eye,
    open: Float, smile: Float, mouth: Float, brow: Float, blush: Float, blink: Float, tear: Float, angry: Float, ink: Color,
) {
    val yaw = lx * .95f
    val pitch = (ly * .5f).coerceIn(-.45f, .55f)
    val ew = r * .15f
    val eh = r * .27f
    for (side in intArrayOf(-1, 1)) {
        val e = proj(c, r, side * .52f, -.1f, yaw, pitch)
        val a = ink.copy(alpha = e.vis)
        on(e) {
            drawEye(if (eye == Eye.Wink) (if (side < 0) Eye.Round else Eye.Arc) else eye, e.x, e.y, ew, eh, open * blink, t, r, a)
            if (tear > .01f && side > 0) {
                val p = (t * .5f) % 1f
                drawCircle(Color(0xFF7FD3FF).copy(alpha = tear * (1f - p * .6f) * e.vis), r * .05f * (1f - .3f * p), Offset(e.x, e.y + eh * .6f + p * r * .45f))
            }
        }
        if (abs(brow) > .1f) {
            val b = proj(c, r, side * .52f, -.42f, yaw, pitch)
            val al = (abs(brow) * 2f).coerceAtMost(1f) * b.vis
            on(b) {
                drawLine(ink.copy(alpha = al), Offset(b.x - side * ew * 1.2f, b.y - brow * r * .07f), Offset(b.x + side * ew * 1.5f, b.y + brow * r * .05f), r * .075f, StrokeCap.Round)
            }
        }
        run {
            val ch = proj(c, r, side * .88f, .2f, yaw, pitch)
            on(ch) { drawOval(PINK.copy(alpha = (.3f + .5f * blush).coerceAtMost(.8f) * ch.vis), Offset(ch.x - r * .12f, ch.y - r * .07f), Size(r * .24f, r * .14f)) }
        }
    }
    val m = proj(c, r, 0f, .34f, yaw, pitch)
    on(m) { drawMouth(m.x, m.y, r, smile, mouth, ink.copy(alpha = m.vis)) }
    if (angry > .01f) {
        val o = Offset(c.x + r * .75f, c.y - r * .55f)
        val k = r * (.07f + .01f * sin(t * 12f))
        val col = Color(0xFFE53935).copy(alpha = angry)
        drawLine(col, o + Offset(-k, -k), o + Offset(k, k), r * .06f, StrokeCap.Round)
        drawLine(col, o + Offset(-k, k), o + Offset(k, -k), r * .06f, StrokeCap.Round)
    }
}

private fun DrawScope.drawEye(kind: Eye, x: Float, y: Float, ew: Float, eh: Float, open: Float, t: Float, r: Float, col: Color) {
    val sw = Stroke(r * .08f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    when (kind) {
        Eye.Round, Eye.Wink -> {
            val hh = (eh * open).coerceAtLeast(r * .03f)
            drawOval(col, Offset(x - ew, y - hh), Size(ew * 2f, hh * 2f))
            if (hh > ew * .7f) {
                drawCircle(Color.White.copy(alpha = col.alpha), ew * .42f, Offset(x - ew * .3f, y - hh * .38f))
                drawCircle(Color.White.copy(alpha = col.alpha * .8f), ew * .2f, Offset(x + ew * .35f, y + hh * .42f))
            }
        }
        Eye.Arc -> drawPath(Path().apply { moveTo(x - ew * 1.4f, y + ew * .6f); quadraticTo(x, y - ew * 1.9f, x + ew * 1.4f, y + ew * .6f) }, col, style = sw)
        Eye.Line -> drawPath(Path().apply { moveTo(x - ew * 1.4f, y); quadraticTo(x, y + ew * 1.3f, x + ew * 1.4f, y) }, col, style = sw)
        Eye.Heart -> drawPath(heart(x, y, ew * 2.1f * (1f + .1f * sin(t * 8f))), Color(0xFFFF4D7A).copy(alpha = col.alpha))
        Eye.Star -> drawPath(sparkle(x, y, ew * 2.2f), col)
    }
}

private fun DrawScope.drawMouth(x: Float, y: Float, r: Float, smile: Float, mouth: Float, col: Color) {
    val h = r * .2f
    val w = r * (.2f + .1f * smile.coerceAtLeast(0f))
    val closed = (1f - mouth * 4f).coerceIn(0f, 1f)
    val opened = (mouth * 4f).coerceIn(0f, 1f)
    if (closed > .01f) {
        drawPath(
            Path().apply { moveTo(x - w, y); quadraticTo(x, y + smile * h * 1.6f, x + w, y) },
            col.copy(alpha = col.alpha * closed), style = Stroke(r * .08f, cap = StrokeCap.Round),
        )
    }
    if (opened > .01f) {
        val top = y + h * (smile.coerceAtLeast(0f) * .3f - mouth * (1f - smile.coerceIn(0f, 1f)) * 1.2f)
        val bot = y + h * (.6f + 2.4f * mouth)
        // bậc ba với điểm điều khiển thu vào trong → miệng tròn trịa thay vì nhọn hai đầu; 2/3 giữ nguyên độ sâu như bậc hai
        val tc = y + (top - y) * 2f / 3f
        val bc = y + (bot - y) * 2f / 3f
        drawPath(Path().apply { moveTo(x - w, y); cubicTo(x - w * .6f, tc, x + w * .6f, tc, x + w, y); cubicTo(x + w * .9f, bc, x - w * .9f, bc, x - w, y); close() }, col.copy(alpha = col.alpha * opened))
        val depth = (bot - y) / 2f
        if (smile > .5f) drawOval(PINK.copy(alpha = opened * col.alpha), Offset(x - w * .5f, y + depth * .5f), Size(w, depth * .45f))
    }
}

private fun heart(cx: Float, cy: Float, s: Float) = Path().apply {
    moveTo(cx, cy + s * .9f)
    cubicTo(cx - s * 1.4f, cy - s * .1f, cx - s * .9f, cy - s * 1.1f, cx, cy - s * .35f)
    cubicTo(cx + s * .9f, cy - s * 1.1f, cx + s * 1.4f, cy - s * .1f, cx, cy + s * .9f)
    close()
}

private fun sparkle(cx: Float, cy: Float, s: Float) = Path().apply {
    moveTo(cx, cy - s)
    quadraticTo(cx + s * .15f, cy - s * .15f, cx + s, cy)
    quadraticTo(cx + s * .15f, cy + s * .15f, cx, cy + s)
    quadraticTo(cx - s * .15f, cy + s * .15f, cx - s, cy)
    quadraticTo(cx - s * .15f, cy - s * .15f, cx, cy - s)
    close()
}

/** Màu gốc của thân theo cảm xúc (sau đó được pha trắng thành pastel); null = dùng màu nhấn của app. */
private fun OrbMood.tint(): Color? = when (this) {
    OrbMood.Idle -> null
    OrbMood.Happy -> Color(0xFFFFB300)
    OrbMood.Love -> Color(0xFFFF4F8B)
    OrbMood.Wow -> Color(0xFF00B8D4)
    OrbMood.Sad -> Color(0xFF5C7A99)
    OrbMood.Sleepy -> Color(0xFF8E7CC3)
    OrbMood.Think -> Color(0xFF26A69A)
    OrbMood.Cheer -> Color(0xFFFF8F00)
    OrbMood.Angry -> Color(0xFFE53935)
    OrbMood.Wink -> Color(0xFFFF7A59)
}
