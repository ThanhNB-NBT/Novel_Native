package com.thanhnb.novelnative

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Màn khởi động trong app, nối liền với splash hệ thống (cùng nền xanh chuyển sắc, logo ở chính giữa):
 * logo GT hiện dần theo nét cọ quét từ trái sang phải, chữ "GÁC TRUYỆN" trồi lên, rồi cả màn mờ đi.
 * Chỉ chạy một lần mỗi tiến trình (xoay màn hình / tạo lại activity không chiếu lại); chạm để bỏ qua.
 */
object Splash { var done = false }

@Composable
fun SplashOverlay() {
    if (Splash.done) return
    var visible by remember { mutableStateOf(true) }
    val brush = remember { Animatable(0f) }
    val text = remember { Animatable(0f) }
    val fade = remember { Animatable(1f) }
    LaunchedEffect(Unit) {
        brush.animateTo(1f, tween(650, easing = FastOutSlowInEasing))
        text.animateTo(1f, tween(450, easing = FastOutSlowInEasing))
        kotlinx.coroutines.delay(250)
        fade.animateTo(0f, tween(320))
        Splash.done = true
        visible = false
    }
    if (!visible) return
    Box(
        Modifier.fillMaxSize().alpha(fade.value)
            .background(Brush.linearGradient(listOf(Color(0xFF5B9BFF), Color(0xFF1F48CC)), Offset.Zero, Offset.Infinite))
            .clickable(remember { MutableInteractionSource() }, null) { Splash.done = true; visible = false },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(
                painterResource(R.drawable.gt_logo), null, Modifier.width(150.dp)
                    .graphicsLayer { val s = .94f + .06f * brush.value; scaleX = s; scaleY = s }
                    // quét nét cọ: chỉ vẽ phần logo bên trái mép quét, mép hơi nghiêng cho giống nét bút
                    .drawWithContent { clipRect(right = size.width * brush.value) { this@drawWithContent.drawContent() } },
                contentScale = ContentScale.FillWidth,
            )
            Text(
                "GÁC TRUYỆN", Modifier.padding(top = 22.dp).graphicsLayer { alpha = text.value; translationY = (1f - text.value) * 18f },
                color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 6.sp, textAlign = TextAlign.Center,
            )
            Text(
                "đọc truyện dịch mỗi ngày", Modifier.padding(top = 6.dp).graphicsLayer { alpha = text.value * .7f; translationY = (1f - text.value) * 18f },
                color = Color.White, fontSize = 12.sp, textAlign = TextAlign.Center,
            )
        }
    }
}
