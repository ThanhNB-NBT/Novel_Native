package com.thanhnb.novelnative

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import android.view.View
import android.view.WindowManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateOffsetAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalTime
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/** Cảm xúc của linh vật. Idle = bình thường; Sleepy tự đến khi không ai chạm 25 giây; Dizzy = choáng (mắt xoáy, sao quay quanh đầu). */
enum class OrbMood { Idle, Happy, Love, Wow, Sad, Sleepy, Think, Cheer, Angry, Wink, Dizzy }

private enum class Eye { Round, Arc, Line, Heart, Star, Wink, Spiral }

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
    OrbMood.Dizzy -> Face(Eye.Spiral, 1f, -.3f, .3f, 0f, .5f)
}

// mặt phụ không phải cảm xúc công khai: được vuốt ve (mắt cười nhắm, má đỏ) và đang nghe truyện (nhắm mắt thư thái)
private val BLISS = Face(Eye.Arc, 1f, 1f, .12f, 0f, 1.5f)
private val LISTEN = Face(Eye.Arc, 1f, .75f, 0f, 0f, .8f)

/** Trạng thái dùng chung cho mọi linh vật trong app: điểm đang chạm (toạ độ cửa sổ) và sự kiện cần phản ứng. */
object Mascot {
    var gaze by mutableStateOf<Offset?>(null)
    var event by mutableStateOf<Pair<Int, OrbMood>?>(null)
    private var n = 0
    fun react(m: OrbMood) { event = ++n to m }

    /** Bậc tiến hoá 0..4 — quyết định các lớp vẽ thêm (màu lửa, sừng, đuôi, hào quang). */
    var tier by mutableIntStateOf(0)
    /** Câu đang nói trong bong bóng thoại (số thứ tự để câu trùng vẫn hiện lại). */
    var speech by mutableStateOf<Pair<Int, String>?>(null)
    /** Đang nghe truyện → lắc lư theo nhịp. */
    var listening by mutableStateOf(false)
    /** Đang ở màn đọc và chương đã hiện → mắt lướt theo dòng. */
    var reading by mutableStateOf(false)

    fun say(text: String, mood: OrbMood? = null) { speech = ++n to text; if (mood != null) react(mood) }

    /** Bậc lấy theo cảnh giới Tu Tiên, chưa tu thì theo chuỗi ngày đọc; lấy bậc cao hơn trong hai. Chỉ tăng, không tụt trong một phiên. */
    fun evolve(realm: Int = 0, ascended: Boolean = false, streak: Int = 0) {
        val byRealm = when { ascended -> 4; realm >= 7 -> 3; realm >= 5 -> 2; realm >= 3 -> 1; else -> 0 }
        val byStreak = when { streak >= 100 -> 3; streak >= 30 -> 2; streak >= 7 -> 1; else -> 0 }
        tier = maxOf(tier, byRealm, byStreak)
    }
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
private val Z_COL = Color(0xFF7E6BC4)
private val REACTIONS = listOf(OrbMood.Happy, OrbMood.Wow, OrbMood.Wink, OrbMood.Cheer, OrbMood.Love, OrbMood.Think)

// ---- Màn lúc rảnh: timeline ngắn ghi độ lệch vào Pose, Orb cộng chúng lên các thông số khuôn mặt/thân sẵn có ----

/** Độ lệch CỘNG THÊM (0 = không đổi) so với giá trị thường của khuôn mặt và thân; rot tính bằng độ, yaw bằng radian. */
private class Pose {
    var open = 0f; var smile = 0f; var mouth = 0f; var brow = 0f; var blush = 0f
    var lookX = 0f; var lookY = 0f; var arm = 0f; var flame = 0f
    var sx = 0f; var sy = 0f; var hop = 0f; var rot = 0f; var yaw = 0f
    fun clear() {
        open = 0f; smile = 0f; mouth = 0f; brow = 0f; blush = 0f
        lookX = 0f; lookY = 0f; arm = 0f; flame = 0f
        sx = 0f; sy = 0f; hop = 0f; rot = 0f; yaw = 0f
    }
}

/** [fn] nhận số giây đã trôi từ lúc bắt đầu; mọi timeline phải tự về 0 trước [dur]. [blinkEnd] = chớp mắt khi xong. */
private class IdleAct(val dur: Float, val idle: Boolean = true, val blinkEnd: Boolean = false, val fn: (Float, Pose) -> Unit)

private class Rig { var act: IdleAct? = null; var t0 = 0f }

private fun ramp(u: Float, a: Float, b: Float): Float { val x = ((u - a) / (b - a)).coerceIn(0f, 1f); return x * x * (3f - 2f * x) }
/** Lên dần trong [a,b], giữ, rồi xuống dần trong [c,d]. */
private fun pulse(u: Float, a: Float, b: Float, c: Float, d: Float) = ramp(u, a, b) * (1f - ramp(u, c, d))

private val YAWN = IdleAct(2.8f, blinkEnd = true) { u, p ->
    val y = pulse(u, 0f, .9f, 1.9f, 2.5f)
    p.mouth = 1.1f * y; p.smile = -.35f * y; p.open = -.85f * y
    p.arm = 1.3f * y; p.sy = .05f * y; p.lookY = -.5f * y
}
private val STRETCH = IdleAct(2.2f) { u, p ->
    val sq = pulse(u, 0f, .5f, .6f, .9f)
    val el = pulse(u, .7f, 1.2f, 1.5f, 2.1f)
    p.sx = .1f * sq - .09f * el; p.sy = -.14f * sq + .17f * el
    p.open = -.6f * min(1f, sq + el); p.arm = 1.1f * el; p.smile = .5f * el
}
private val LOOK = IdleAct(3.6f) { u, p ->
    p.lookX = -.95f * pulse(u, 0f, .5f, 1f, 1.5f) + .95f * pulse(u, 1.2f, 1.7f, 2.2f, 2.5f)
    p.lookY = -.9f * pulse(u, 2.4f, 2.9f, 3f, 3.5f)
}
// gật gù buồn ngủ rồi giật mình: d tụt về 0 rất nhanh ở 2,3 s nên mắt bật mở + nhảy lên
private val DOZE = IdleAct(4.2f, blinkEnd = true) { u, p ->
    val d = ramp(u, 0f, 2.1f) * (1f - ramp(u, 2.3f, 2.4f))
    val st = pulse(u, 2.3f, 2.45f, 2.9f, 3.9f)
    p.open = -d + .3f * st; p.lookY = .5f * d; p.smile = -.3f * d
    p.rot = d * 7f * sin(u * 2.6f); p.mouth = .5f * st
    p.hop = pulse(u, 2.3f, 2.4f, 2.5f, 2.9f); p.sy = .08f * st; p.arm = .9f * st
}
private val FIRE = IdleAct(3f) { u, p ->
    val a = pulse(u, 0f, .5f, 2.2f, 2.8f)
    p.flame = .8f * a * (1f + .15f * sin(u * 9f)); p.lookY = -a; p.smile = .6f * a; p.mouth = .2f * a; p.arm = .3f * a
    p.hop = pulse(u, 1.2f, 1.35f, 1.45f, 1.9f)
}
// xoay mặt một vòng quanh mặt cầu (yaw) + lắc nhẹ; bản người dùng (nhấn đúp) ngắn hơn và không bị tap huỷ
private fun spinAct(dur: Float, idle: Boolean) = IdleAct(dur, idle) { u, p ->
    val k = u / dur
    p.yaw = 2f * PI.toFloat() * ramp(k, .05f, .8f)
    p.rot = 10f * sin(k * 20f) * (1f - ramp(k, .5f, 1f))
    p.hop = .6f * pulse(k, .05f, .25f, .3f, .7f)
    p.smile = .5f * pulse(k, 0f, .2f, .8f, 1f)
}
private val SPIN_IDLE = spinAct(1.6f, true)
private val SPIN_USER = spinAct(1.1f, false)
private val ACTS = listOf(YAWN, STRETCH, LOOK, DOZE, FIRE, SPIN_IDLE)

// ---- Bong bóng thoại: nhiều Orb cùng tồn tại nên chỉ cái lên hình gần nhất còn gắn vào cây mới được hiện ----

private val bubbleOwners = mutableStateListOf<Any>()
// câu đã bắt đầu hiện và lúc hết hạn: Orb nhận quyền giữa chừng (chuyển màn) hiện nốt phần còn lại, không hiện lại từ đầu
private var spokenN = 0
private var spokenEnd = 0L

private fun isNight() = LocalTime.now().hour.let { it >= 23 || it < 5 }

private fun animationsOff(ctx: Context) = Settings.Global.getFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f

/** Đặt bong bóng cạnh linh vật (phải, không đủ chỗ thì trái, rồi dưới) và kẹp trong màn hình. */
private class BubblePos(val gap: Int, val margin: Int) : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
        val w = popupContentSize.width
        val h = popupContentSize.height
        val right = anchorBounds.right + gap
        val left = anchorBounds.left - gap - w
        val x: Int
        var y = anchorBounds.center.y - h / 2
        when {
            right + w <= windowSize.width - margin -> x = right
            left >= margin -> x = left
            else -> { x = (anchorBounds.center.x - w / 2).coerceIn(margin, max(margin, windowSize.width - w - margin)); y = anchorBounds.bottom + gap }
        }
        return IntOffset(x, y.coerceIn(margin, max(margin, windowSize.height - h - margin)))
    }
}

/** Cửa sổ popup mặc định nhận chạm trong vùng của nó; đặt cờ NOT_TOUCHABLE để chạm xuyên qua bong bóng. Trả true khi đã xong. */
private fun letTouchesThrough(v: View): Boolean {
    val lp = v.layoutParams as? WindowManager.LayoutParams ?: return false
    if (lp.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE != 0) return true
    lp.flags = lp.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
    (v.context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).updateViewLayout(v, lp)
    return true
}

@Composable
private fun SpeechBubble(text: String, level: () -> Float) {
    val density = LocalDensity.current
    val pos = remember(density) { with(density) { BubblePos(6.dp.roundToPx(), 8.dp.roundToPx()) } }
    Popup(popupPositionProvider = pos, properties = PopupProperties(focusable = false)) {
        val view = LocalView.current
        // cửa sổ có thể chưa gắn xong ở lần dựng đầu → thử lại vài nhịp
        LaunchedEffect(view) { repeat(6) { if (runCatching { letTouchesThrough(view) }.getOrDefault(true)) return@LaunchedEffect; delay(40) } }
        val shape = RoundedCornerShape(14.dp)
        Box(
            Modifier
                .graphicsLayer { alpha = level() }
                .widthIn(max = 220.dp)
                .background(MaterialTheme.colorScheme.primaryContainer, shape)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
                .padding(horizontal = 10.dp, vertical = 6.dp)
                .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        ) {
            Text(text, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}

/**
 * Quả cầu 3D nhỏ dễ thương đặt ở header, vẽ bằng Canvas (không cần GL/AGSL nên chạy từ API 26): thân là chồng gradient tròn
 * (sáng trên-trái, bóng dưới-phải, viền kính, đốm sáng) + bóng đổ dưới đất; khuôn mặt dịch và co ngang theo
 * hướng ngón tay để có cảm giác dính trên mặt cầu. Chạm = phản ứng đổi cảm xúc, giữ lâu = yêu, chọc 5 lần = giận,
 * kéo = nghiêng theo ngón tay, vuốt qua lại > 0,8 giây = sướng, hất nhanh = văng rồi bật lại, nhấn đúp = xoay.
 * Khi ngón tay chạm ở BẤT KỲ đâu trong app ([Mascot.gaze]) mặt quay về hướng đó; [Mascot.react] gây phản ứng từ code.
 * Lúc rảnh nó tự diễn các "màn" nhỏ ([ACTS]); nghe truyện thì đung đưa theo nhịp, đang đọc thì mắt lướt theo dòng.
 */
@Composable
fun Orb(
    mood: OrbMood = OrbMood.Idle,
    modifier: Modifier = Modifier.size(32.dp),
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val moodNow by rememberUpdatedState(mood)

    val time = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        var t0 = -1L
        while (true) androidx.compose.runtime.withFrameNanos { n -> if (t0 < 0) t0 = n; time.floatValue = (n - t0) / 1e9f }
    }

    // đêm và "tắt hoạt ảnh" của hệ thống có thể đổi khi app đang mở → đọc lại mỗi phút
    var night by remember { mutableStateOf(isNight()) }
    var calm by remember { mutableStateOf(animationsOff(context)) }
    LaunchedEffect(Unit) { while (true) { delay(60_000); night = isNight(); calm = animationsOff(context) } }

    val token = remember { Any() }
    DisposableEffect(token) { bubbleOwners.add(token); onDispose { bubbleOwners.remove(token) } }
    val isOwner = bubbleOwners.lastOrNull() === token

    var reaction by remember { mutableStateOf<OrbMood?>(null) }
    var tapN by remember { mutableIntStateOf(0) }
    var asleep by remember { mutableStateOf(false) }
    var bliss by remember { mutableStateOf(false) }
    val lastTouch = remember { mutableFloatStateOf(0f) }
    var box by remember { mutableStateOf(IntSize.Zero) }
    var ctr by remember { mutableStateOf<Offset?>(null) }
    var touch by remember { mutableStateOf<Offset?>(null) }
    var lookTarget by remember { mutableStateOf(Offset.Zero) }

    val listenOn = Mascot.listening && reaction == null && mood == OrbMood.Idle
    // nghe/đọc truyện thì không ngủ gật: nó thức cùng người dùng
    val shown = reaction ?: if (!listenOn && !Mascot.reading && (asleep || night) && mood == OrbMood.Idle && Mascot.gaze == null) OrbMood.Sleepy else mood
    var seen by remember { mutableStateOf(Mascot.event?.first) }
    LaunchedEffect(Mascot.event) {
        Mascot.event?.let {
            if (it.first != seen) {
                seen = it.first; reaction = it.second; tapN++
                if (it.second == OrbMood.Angry && bubbleOwners.lastOrNull() === token) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            }
        }
    }

    val rig = remember { Rig() }
    val pose = remember { Pose() }
    fun play(a: IdleAct) { rig.act = a; rig.t0 = time.floatValue }

    // có phản ứng mới thì màn rảnh đang diễn bị cắt; Dizzy ở lại lâu hơn để kịp thấy sao quay
    LaunchedEffect(tapN) {
        if (rig.act?.idle == true) rig.act = null
        if (reaction != null) { delay(if (reaction == OrbMood.Dizzy) 3200 else 1900); reaction = null }
    }
    LaunchedEffect(Unit) { while (true) { delay(1000); asleep = time.floatValue - lastTouch.floatValue > 25f && !Mascot.reading && !Mascot.listening } }

    // được vuốt xong: giữ mặt sướng thêm chút rồi thôi
    LaunchedEffect(bliss) { if (bliss) { while (touch != null) delay(100); delay(1800); bliss = false } }

    val blinkAt = remember { mutableFloatStateOf(-9f) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(Random.nextLong(2200, 5200))
            blinkAt.floatValue = time.floatValue
            if (Random.nextInt(4) == 0) { delay(260); blinkAt.floatValue = time.floatValue }
        }
    }

    val f = if (bliss) BLISS else if (listenOn) LISTEN else shown.face()
    val spec = spring<Float>(dampingRatio = .6f, stiffness = 260f)
    val open = animateFloatAsState(f.open, spec, label = "open")
    val smile = animateFloatAsState(f.smile, spec, label = "smile")
    val mouth = animateFloatAsState(f.mouth, spec, label = "mouth")
    val brow = animateFloatAsState(f.brow, spec, label = "brow")
    val blush = animateFloatAsState(f.blush, spec, label = "blush")
    val lookY = animateFloatAsState(f.lookY, spec, label = "lookY")
    val fxLove = animateFloatAsState(if (shown == OrbMood.Love || bliss) 1f else 0f, tween(400), label = "fxLove")
    val fxCheer = animateFloatAsState(if (shown == OrbMood.Cheer) 1f else 0f, tween(400), label = "fxCheer")
    val fxSad = animateFloatAsState(if (shown == OrbMood.Sad) 1f else 0f, tween(400), label = "fxSad")
    val fxAngry = animateFloatAsState(if (shown == OrbMood.Angry) 1f else 0f, tween(300), label = "fxAngry")
    val fxSleepy = animateFloatAsState(if (shown == OrbMood.Sleepy) 1f else 0f, tween(500), label = "fxSleepy")
    val fxDizzy = animateFloatAsState(if (shown == OrbMood.Dizzy) 1f else 0f, tween(300), label = "fxDizzy")
    val fxListen = animateFloatAsState(if (listenOn) 1f else 0f, tween(400), label = "fxListen")
    val fxBliss = animateFloatAsState(if (bliss) 1f else 0f, tween(300), label = "fxBliss")
    val fxRead = animateFloatAsState(if (Mascot.reading && reaction == null && touch == null && Mascot.gaze == null) 1f else 0f, tween(350), label = "fxRead")

    // mỗi lần đổi cảm xúc: nảy thạch (squash) và nhảy lên nếu đang phấn khích
    val squash = remember { Animatable(0f) }
    val hop = remember { Animatable(0f) }
    LaunchedEffect(shown) { squash.snapTo(1f); squash.animateTo(0f, spring(.3f, 260f)) }
    LaunchedEffect(shown) {
        if (shown == OrbMood.Wow || shown == OrbMood.Cheer || shown == OrbMood.Happy) {
            hop.snapTo(0f); hop.animateTo(1f, tween(130)); hop.animateTo(0f, spring(.45f, 320f))
        }
    }

    // hất văng: toạ độ chuẩn hoá -1..1, vẽ nhân với khoảng trống thật trong canvas nên không bao giờ lọt ra ngoài
    val flyX = remember { Animatable(0f) }
    val flyY = remember { Animatable(0f) }

    // lên bậc khi đang hiện: nổ tia sáng + Cheer
    var lastTier by remember { mutableIntStateOf(Mascot.tier) }
    val burst = remember { Animatable(0f) }
    LaunchedEffect(Mascot.tier) {
        val tr = Mascot.tier
        val up = tr > lastTier
        lastTier = tr
        if (up) { reaction = OrbMood.Cheer; tapN++; burst.snapTo(0f); burst.animateTo(1f, tween(1300)) }
    }

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
    val primary by animateColorAsState((if (bliss) OrbMood.Love else shown).tint() ?: MaterialTheme.colorScheme.primary, tween(450), label = "tint")
    val bodyTop = lerp(primary, Color.White, if (darkTheme) .86f else .8f)
    val bodyBot = lerp(primary, Color.White, if (darkTheme) .55f else .42f)
    val rimCol = if (darkTheme) lerp(primary, Color.White, .25f) else lerp(primary, Color.Black, .3f)
    val faceCol = INK
    val armUp = animateFloatAsState(
        when (shown) { OrbMood.Cheer -> 1f; OrbMood.Wow -> .8f; OrbMood.Happy, OrbMood.Wink, OrbMood.Dizzy -> .55f; OrbMood.Love -> .3f; OrbMood.Sad, OrbMood.Sleepy -> -.35f; else -> 0f },
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

    // rút một màn ngẫu nhiên, không lặp liền nhau; đang đọc thì thưa ra (≥ 25 giây) để không xao nhãng
    LaunchedEffect(Unit) {
        var last = -1
        var lastEnd = -99f
        while (true) {
            delay(if (Mascot.reading) Random.nextLong(26_000, 40_000) else Random.nextLong(6000, 13000))
            val quiet = reaction == null && touch == null && Mascot.gaze == null && moodNow == OrbMood.Idle && !asleep && !night && !calm && !Mascot.listening && !bliss
            if (!quiet || (Mascot.reading && time.floatValue - lastEnd < 25f)) continue
            var i = ACTS.indices.random()
            if (i == last) i = (i + 1 + Random.nextInt(ACTS.size - 1)) % ACTS.size
            last = i
            val a = ACTS[i]
            play(a)
            delay((a.dur * 1000).toLong())
            if (a.blinkEnd) blinkAt.floatValue = time.floatValue
            lastEnd = time.floatValue
        }
    }

    // bong bóng thoại: chỉ Orb sở hữu mới hiện; thời gian hiện theo độ dài câu
    var bubble by remember { mutableStateOf(false) }
    var bubbleText by remember { mutableStateOf("") }
    val sp = Mascot.speech
    LaunchedEffect(sp, isOwner) {
        if (!isOwner || sp == null) { bubble = false; return@LaunchedEffect }
        val now = SystemClock.uptimeMillis()
        if (sp.first != spokenN) { spokenN = sp.first; spokenEnd = now + (2400L + sp.second.length * 35L).coerceIn(3200L, 6000L) }
        val left = spokenEnd - now
        if (left > 0) { bubbleText = sp.second; bubble = true; delay(left) }
        bubble = false
    }
    val bubbleA by animateFloatAsState(if (bubble) 1f else 0f, tween(220), label = "bubble")

    var next by remember { mutableIntStateOf(0) }

    Box(modifier) {
        Canvas(
            Modifier
                .fillMaxSize()
                .semantics { contentDescription = "Linh thú" }
                .onSizeChanged { box = it }
                .onGloballyPositioned { ctr = it.positionInWindow() + Offset(it.size.width / 2f, it.size.height / 2f) }
                .pointerInput(Unit) {
                    var lastUp = -9f
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
                        val vt = VelocityTracker()
                        vt.addPosition(down.uptimeMillis, down.position)
                        // vuốt qua lại: đếm lần đổi chiều ngang, cách nhau < 0,6 giây thì tính là cùng một cú vuốt
                        var dir = 0; var rev = 0; var strokeT0 = 0f; var lastRev = -9f
                        do {
                            val ch = awaitPointerEvent().changes.first()
                            if ((ch.position - down.position).getDistance() > slop) moved = true
                            touch = ch.position; aim(ch.position)
                            vt.addPosition(ch.uptimeMillis, ch.position)
                            val dx = ch.positionChange().x
                            if (abs(dx) > 1.5f) {
                                val d = if (dx > 0f) 1 else -1
                                if (dir != 0 && d != dir) {
                                    val tn = time.floatValue
                                    if (tn - lastRev > .6f) { strokeT0 = tn; rev = 0 }
                                    rev++; lastRev = tn
                                }
                                dir = d
                            }
                            if (!bliss && rev >= 2 && time.floatValue - strokeT0 > .8f) { bliss = true; haptic.performHapticFeedback(HapticFeedbackType.LongPress) }
                            ch.consume()
                        } while (ch.pressed)
                        touch = null; lookTarget = Offset.Zero
                        if (bliss) return@awaitEachGesture
                        if (moved) {
                            val v = vt.calculateVelocity()
                            val sp2 = sqrt(v.x * v.x + v.y * v.y)
                            if (sp2 > 900.dp.toPx()) {
                                val nx = v.x / sp2
                                val ny = v.y / sp2
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                reaction = OrbMood.Wow; tapN++
                                scope.launch { flyX.animateTo(nx * .9f, tween(110)); flyX.animateTo(0f, spring(.28f, 160f)) }
                                scope.launch { flyY.animateTo(ny * .9f, tween(110)); flyY.animateTo(0f, spring(.28f, 160f)) }
                            }
                            return@awaitEachGesture
                        }
                        val held = time.floatValue - tDown
                        val now = time.floatValue
                        taps.addLast(now); while (taps.isNotEmpty() && now - taps.first() > 3f) taps.removeFirst()
                        val dbl = held < .3f && now - lastUp < .35f
                        lastUp = now
                        reaction = when {
                            wasAsleep -> OrbMood.Wow
                            held > .5f -> OrbMood.Love
                            taps.size >= 5 -> OrbMood.Angry.also { taps.clear() }
                            else -> REACTIONS[next++ % REACTIONS.size].let { if (it == shown) REACTIONS[next++ % REACTIONS.size] else it }
                        }
                        haptic.performHapticFeedback(if (held > .5f || reaction == OrbMood.Angry) HapticFeedbackType.LongPress else HapticFeedbackType.TextHandleMove)
                        tapN++
                        if (dbl) play(SPIN_USER)
                    }
                },
        ) {
            val t = time.floatValue
            // chuyển động trang trí đóng băng khi hệ thống tắt hoạt ảnh
            val te = if (calm) 0f else t
            val w = this.size.width
            val h = this.size.height
            val r = min(w, h) * .38f
            val cy0 = h * .55f

            val act = rig.act
            pose.clear()
            if (act != null) { val u = t - rig.t0; if (u > act.dur) rig.act = null else act.fn(u, pose) }

            // đang đọc: mắt lướt chậm trái → phải theo dòng rồi bật về đầu dòng kế (3 dòng thấp dần)
            val rd = fxRead.value
            val cyc = t % 3.4f
            val sweep = when { cyc < 2.5f -> -.8f + 1.6f * cyc / 2.5f; cyc < 2.65f -> .8f - 1.6f * (cyc - 2.5f) / .15f; else -> -.8f }
            val lk = lookNow(t)
            val lx = (lk.x * (1f - rd) + sweep * rd + pose.lookX).coerceIn(-1f, 1f)
            val ly = lk.y + rd * (.05f + .1f * ((t / 3.4f).toInt() % 3)) + pose.lookY

            val fx = flyX.value.coerceIn(-1f, 1f)
            val fy = flyY.value.coerceIn(-1f, 1f)
            val ox = fx * (w / 2f - r * BODY_RX).coerceAtLeast(0f) * .9f
            val oy = if (fy < 0f) fy * (cy0 - r * BODY_RY).coerceAtLeast(0f) * .9f else fy * (h - cy0 - r * BODY_RY - r * .25f).coerceAtLeast(0f)

            val beat = sin(te * 9.42f) * fxListen.value // 1,5 nhịp/giây, đều tăm tắp
            val purr = sin(te * 22f) * fxBliss.value
            val dz = fxDizzy.value
            val cx = w / 2f + ox + beat * r * .09f + sin(te * 3f) * r * .05f * dz
            val hopT = hop.value + pose.hop
            val cy = cy0 + oy + (if (calm) 0f else sin(t * 1.7f) * r * .035f) - hopT * r * .22f
            val c = Offset(cx, cy)
            val s = squash.value
            val breath = 1f + .012f * sin(te * 2.3f)
            val lean = lx * 4f + pose.rot + beat * 7f + purr * 1.2f + sin(te * 6f) * 9f * dz
            val tier = Mascot.tier

            // bóng đổ: nhỏ và nhạt đi khi nhảy lên
            val sh = 1f - hopT.coerceIn(0f, 1.5f) * .45f
            drawOval(Color.Black.copy(alpha = .13f * sh), Offset(cx - r * .8f * sh, cy0 + r * BODY_RY * .98f), Size(r * 1.6f * sh, r * .2f))
            scale((1f + .10f * s + pose.sx) * breath, (1f - .16f * s + pose.sy) * breath, pivot = Offset(w / 2f, cy0 + r)) {
                rotate(lean, pivot = c) {
                    if (tier >= 2) drawTierBack(c, r, te, tier, bodyBot, rimCol)
                    drawFlame(c, r, t, sin(te * 2.2f) * .15f - lx * .4f - s * .4f - hopT * .3f, flameK.value + pose.flame, fxAngry.value, tier, calm)
                    drawArms(c, r, t, armUp.value, bodyBot, rimCol, pose.arm)
                    drawBody(c, r, bodyTop, bodyBot, rimCol, t)
                    drawFace(
                        c, r, lx, ly + lookY.value, t, f.eye,
                        (open.value + pose.open).coerceAtLeast(0f), smile.value + pose.smile, (mouth.value + pose.mouth).coerceAtLeast(0f),
                        brow.value + pose.brow, blush.value + pose.blush,
                        blink = blinkFactor(t - blinkAt.floatValue), tear = fxSad.value, angry = fxAngry.value, ink = faceCol, yawAdd = pose.yaw,
                    )
                }
            }

            // hiệu ứng bay ngoài thân (không bị bóp méo theo squash)
            if (fxLove.value > .01f) for (i in 0 until 5) {
                val p = (t * .55f + i / 5f) % 1f
                val fade = sin(PI.toFloat() * p)
                val hx = cx + sin(p * 6.28f + i * 1.7f) * r * .45f + (i - 2) * r * .22f
                val hy = cy - r * .75f - p * r * 1.1f
                drawPath(heart(hx, hy, r * .11f * (.6f + .6f * fade), scratch), Color(0xFFFF5C8A).copy(alpha = fxLove.value * fade))
            }
            if (fxCheer.value > .01f) for (i in 0 until 7) {
                val a = i * 2f * PI.toFloat() / 7f + t * .6f
                val d = r * (1.3f + .1f * sin(t * 3f + i))
                val sz = r * .13f * abs(sin(t * 4f + i * 1.3f))
                drawPath(sparkle(cx + cos(a) * d, cy + sin(a) * d * .85f, sz, scratch), (if (i % 2 == 0) Color(0xFFFFD54F) else Color.White).copy(alpha = fxCheer.value))
            }
            if (fxSleepy.value > .01f) for (i in 0 until 3) {
                val p = (te * .3f + i / 3f) % 1f
                drawZ(cx + r * (.6f + .5f * p) + sin(p * 6f + i) * r * .05f, cy - r * (.45f + .9f * p), r * (.07f + .09f * p), Z_COL.copy(alpha = fxSleepy.value * sin(PI.toFloat() * p)))
            }
            if (fxListen.value > .01f) for (i in 0 until 3) {
                val p = (te * .5f + i / 3f) % 1f
                val col = (if (i == 0) Color(0xFF7E57C2) else if (i == 1) Color(0xFF26A69A) else Color(0xFFFF7043)).copy(alpha = fxListen.value * sin(PI.toFloat() * p))
                drawNote(cx + (i - 1) * r * .7f + sin(p * 5f + i) * r * .15f, cy - r * .55f - p * r * 1.1f, r * .09f, col)
            }
            if (dz > .01f) for (i in 0 until 3) {
                val a = te * 4f + i * 2.094f
                drawPath(sparkle(cx + cos(a) * r * .85f, cy - r * BODY_RY * .95f + sin(a) * r * .2f, r * .12f, scratch), Color(0xFFFFD54F).copy(alpha = dz))
            }
            if (tier >= 4) for (i in 0 until 3) {
                val a = te * .7f + i * 2.094f
                val sz = r * .09f * (.6f + .4f * sin(te * 3f + i * 2f))
                drawPath(sparkle(cx + cos(a) * r * 1.25f, cy + sin(a) * r, sz, scratch), (if (i == 1) Color.White else Color(0xFFFFD54F)).copy(alpha = .9f))
            }
            val bu = burst.value
            if (bu > 0f && bu < 1f) for (i in 0 until 10) {
                val a = i * 0.6283f + .3f
                val d = r * (.7f + 1.1f * bu)
                drawPath(sparkle(cx + cos(a) * d, cy + sin(a) * d * .9f, r * .2f * sin(PI.toFloat() * bu), scratch), (if (i % 2 == 0) Color(0xFFFFD54F) else Color.White).copy(alpha = 1f - bu * bu))
            }
        }
        if (bubbleA > .01f) SpeechBubble(bubbleText) { bubbleA }
    }
}

private fun blinkFactor(d: Float) = if (d in 0f..0.16f) 1f - sin(PI.toFloat() * d / 0.16f) else 1f

/** Path dùng chung cho các hạt/lớp vẽ thêm mỗi khung hình (vẽ xong là Canvas đã sao chép nên tái dùng an toàn, vẽ tuần tự trên một luồng). */
private val scratch = Path()

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

/** Hai tay nhỏ thò ra hai bên (vẽ trước thân nên chỉ lộ nửa ngoài): rủ khi buồn/ngủ, giơ và vẫy khi vui. [extra] giơ thêm mà không vẫy (màn vươn vai, ngáp). */
private fun DrawScope.drawArms(c: Offset, r: Float, t: Float, up: Float, fill: Color, rim: Color, extra: Float = 0f) {
    val wave = if (up > .5f) sin(t * 14f) * 14f * (up - .5f) * 2f else sin(t * 2f) * 4f
    for (side in intArrayOf(-1, 1)) {
        val sh = Offset(c.x + side * r * BODY_RX * .9f, c.y + r * BODY_RY * .48f)
        rotate(side * (40f - (up + extra) * 110f + wave * side), pivot = sh) {
            val o = Offset(sh.x - r * .15f + side * r * .14f, sh.y - r * .1f)
            drawOval(fill, o, Size(r * .3f, r * .2f))
            drawOval(rim, o, Size(r * .3f, r * .2f), style = Stroke(r * .06f))
        }
    }
}

/**
 * Các lớp tiến hoá nằm sau thân (vẽ trước thân nên gốc sừng/đuôi bị che): bậc 2 sừng, bậc 3 đuôi vẫy + vòng sáng mềm,
 * bậc 4 thêm quầng trên đầu. Màu lửa/vòng sáng đồng bộ với [drawFlame].
 */
private fun DrawScope.drawTierBack(c: Offset, r: Float, te: Float, tier: Int, body: Color, rim: Color) {
    val rx = r * BODY_RX
    val ry = r * BODY_RY
    val fire = if (tier >= 4) Color(0xFFFFC400) else Color(0xFF6EC6FF)
    if (tier >= 4) {
        // bối quang: đĩa sáng sau lưng kiểu tranh tiên, viền kim và chuỗi hạt xoay chậm
        val hc = Offset(c.x, c.y - ry * .3f)
        val hr = r * 1.42f
        drawCircle(Brush.radialGradient(listOf(Color(0x66FFE082), Color(0x22FFD54F), Color.Transparent), hc, hr * 1.15f), hr * 1.15f, hc)
        drawCircle(Color(0xFFFFC94D), hr, hc, style = Stroke(r * .055f))
        drawCircle(Color(0x99FFE9A8), hr * .86f, hc, style = Stroke(r * .025f))
        for (i in 0 until 8) {
            val a = te * .35f + i * PI.toFloat() / 4f
            drawCircle(Color(0xFFFFF3C4), r * .05f, Offset(hc.x + cos(a) * hr, hc.y + sin(a) * hr))
        }
    } else if (tier >= 3) {
        // linh khí toả mềm quanh thân, thở theo nhịp
        val k = .8f + .2f * sin(te * 2.4f)
        drawCircle(Brush.radialGradient(listOf(fire.copy(alpha = .55f * k), fire.copy(alpha = .22f * k), Color.Transparent), c, r * 1.75f), r * 1.75f, c)
    }
    if (tier >= 3) {
        // đuôi thon vẫy, chóp là đốm lửa có quầng
        val root = Offset(c.x + rx * .62f, c.y + ry * .74f)
        val tip = Offset(root.x + r * .66f, root.y - r * .5f + sin(te * 5f) * r * .14f)
        scratch.rewind()
        scratch.moveTo(root.x, root.y)
        scratch.quadraticTo(root.x + r * .72f, root.y + r * .2f, tip.x, tip.y)
        drawPath(scratch, rim, style = Stroke(r * .26f, cap = StrokeCap.Round))
        drawPath(scratch, body, style = Stroke(r * .15f, cap = StrokeCap.Round))
        drawCircle(Brush.radialGradient(listOf(fire.copy(alpha = .6f), Color.Transparent), tip, r * .32f), r * .32f, tip)
        drawCircle(fire, r * .13f, tip)
        drawCircle(Color.White.copy(alpha = .85f), r * .06f, tip)
    }
    // sừng non: thuôn cong ra ngoài, ngà ở chóp và hổ phách ở gốc
    for (side in intArrayOf(-1, 1)) {
        val b = Offset(c.x + side * rx * .5f, c.y - ry * .7f)
        val tip = Offset(b.x + side * r * .3f, b.y - r * .52f)
        val wd = r * .15f
        val horn = Path().apply {
            moveTo(b.x - side * wd, b.y)
            quadraticTo(b.x - side * wd * .1f, b.y - r * .4f, tip.x, tip.y)
            quadraticTo(b.x + side * r * .42f, b.y - r * .16f, b.x + side * wd * 1.4f, b.y + r * .08f)
            close()
        }
        drawPath(horn, Brush.verticalGradient(listOf(Color(0xFFFFF8E1), Color(0xFFE2A862)), startY = tip.y, endY = b.y))
        drawPath(horn, rim, style = Stroke(r * .06f, join = StrokeJoin.Round))
    }
}

/** Đốm linh hoả trên đỉnh đầu: lập loè liên tục (đứng yên khi [calm]), ngả theo [sway] (radian), [k] = độ lớn theo cảm xúc, giận thì đỏ lên; bậc 1+ xanh trắng, bậc 4 vàng kim. */
private fun DrawScope.drawFlame(c: Offset, r: Float, t: Float, sway: Float, k: Float, angry: Float, tier: Int, calm: Boolean) {
    val base = Offset(c.x, c.y - r * BODY_RY * .82f)
    fun flame(len: Float, w: Float, col: Brush) {
        val flick = if (calm) 1f else 1f + .08f * sin(t * 11f) + .05f * sin(t * 17f + 1f)
        val l = len * flick
        val tip = base + Offset(sin(sway) * l, -cos(sway) * l)
        drawPath(Path().apply {
            moveTo(base.x - w, base.y)
            cubicTo(base.x - w * 1.25f, base.y - l * .55f, tip.x - w * .35f, tip.y + l * .35f, tip.x, tip.y)
            cubicTo(tip.x + w * .55f, tip.y + l * .4f, base.x + w * 1.3f, base.y - l * .5f, base.x + w, base.y)
            close()
        }, col)
    }
    val o0 = when { tier >= 4 -> Color(0xFFFFB300); tier >= 1 -> Color(0xFF4FC3F7); else -> Color(0xFFFF9A3C) }
    val m0 = when { tier >= 4 -> Color(0xFFFFE082); tier >= 1 -> Color(0xFFA5E6FF); else -> Color(0xFFFFC94D) }
    val core = when { tier >= 4 -> Color(0xFFFFFDE7); tier >= 1 -> Color(0xFFF1FBFF); else -> Color(0xFFFFF6D6) }
    val outer = lerp(o0, Color(0xFFE53935), angry)
    val mid = lerp(m0, Color(0xFFFF7043), angry)
    if (tier >= 1) (base - Offset(0f, r * .35f * k)).let { g -> drawCircle(Brush.radialGradient(listOf(mid.copy(alpha = .55f), Color.Transparent), g, r * .75f * k), r * .75f * k, g) }
    flame(r * .9f * k, r * .24f * k, Brush.verticalGradient(listOf(mid, outer), startY = base.y - r * .9f * k, endY = base.y))
    flame(r * .5f * k, r * .12f * k, Brush.verticalGradient(listOf(core, mid), startY = base.y - r * .5f * k, endY = base.y))
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
 * khi vòng ra sau — nhờ vậy "quay đầu" nhìn rất rõ chỉ bằng nét vẽ phẳng. [yawAdd] quay thêm (màn xoay một vòng).
 */
private fun DrawScope.drawFace(
    c: Offset, r: Float, lx: Float, ly: Float, t: Float, eye: Eye,
    open: Float, smile: Float, mouth: Float, brow: Float, blush: Float, blink: Float, tear: Float, angry: Float, ink: Color,
    yawAdd: Float = 0f,
) {
    val yaw = lx * .95f + yawAdd
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
        Eye.Spiral -> {
            // xoắn ~1,75 vòng từ tâm ra ngoài, quay liên tục; chớp mắt thu nhỏ xoáy lại
            val rr = ew * 1.75f * open.coerceIn(.2f, 1.2f)
            scratch.rewind()
            for (i in 0..28) {
                val k = i / 28f
                val a = k * 11f + t * 8f
                val px = x + cos(a) * rr * k
                val py = y + sin(a) * rr * k
                if (i == 0) scratch.moveTo(px, py) else scratch.lineTo(px, py)
            }
            drawPath(scratch, col, style = sw)
        }
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

/** Chữ "z" nét thẳng (không cần vẽ chữ) bay lên khi ngủ. */
private fun DrawScope.drawZ(x: Float, y: Float, s: Float, col: Color) {
    val w = s * .5f
    drawLine(col, Offset(x - s, y - s), Offset(x + s, y - s), w, StrokeCap.Round)
    drawLine(col, Offset(x + s, y - s), Offset(x - s, y + s), w, StrokeCap.Round)
    drawLine(col, Offset(x - s, y + s), Offset(x + s, y + s), w, StrokeCap.Round)
}

/** Nốt nhạc móc đơn: đầu nốt, cán, cờ. */
private fun DrawScope.drawNote(x: Float, y: Float, s: Float, col: Color) {
    drawOval(col, Offset(x - s, y - s * .7f), Size(s * 2f, s * 1.4f))
    drawLine(col, Offset(x + s * .9f, y), Offset(x + s * .9f, y - s * 3f), s * .35f, StrokeCap.Round)
    drawLine(col, Offset(x + s * .9f, y - s * 3f), Offset(x + s * 2f, y - s * 2.3f), s * .35f, StrokeCap.Round)
}

private fun heart(cx: Float, cy: Float, s: Float, p: Path = Path()) = p.apply {
    rewind()
    moveTo(cx, cy + s * .9f)
    cubicTo(cx - s * 1.4f, cy - s * .1f, cx - s * .9f, cy - s * 1.1f, cx, cy - s * .35f)
    cubicTo(cx + s * .9f, cy - s * 1.1f, cx + s * 1.4f, cy - s * .1f, cx, cy + s * .9f)
    close()
}

private fun sparkle(cx: Float, cy: Float, s: Float, p: Path = Path()) = p.apply {
    rewind()
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
    OrbMood.Dizzy -> Color(0xFF7CB342)
}
