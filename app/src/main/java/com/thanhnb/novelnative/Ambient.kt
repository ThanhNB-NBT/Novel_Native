package com.thanhnb.novelnative

import android.graphics.Color as AColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Màu "khí quyển" lấy từ bìa truyện (bản native của ambient.dart): màu chủ đạo, ưu tiên điểm ảnh bão hoà
 * để bìa trắng-đen không ra màu bùn. null khi chưa tải xong / lỗi → giao diện giữ nền thường.
 */
@Composable
fun rememberAmbient(cover: String?): Color? {
    val ctx = LocalContext.current
    val c by produceState<Color?>(null, cover) {
        value = null
        if (cover.isNullOrEmpty()) return@produceState
        value = runCatching {
            val req = ImageRequest.Builder(ctx).data(Api.thumb(cover, 96)).size(48).allowHardware(false).build()
            val bmp = (SingletonImageLoader.get(ctx).execute(req) as? SuccessResult)?.image?.toBitmap() ?: return@runCatching null
            withContext(Dispatchers.Default) { dominant(bmp) }
        }.getOrNull()
    }
    return c
}

private fun dominant(b: android.graphics.Bitmap): Color? {
    var r = 0.0; var g = 0.0; var bl = 0.0; var w = 0.0
    val hsv = FloatArray(3)
    for (y in 0 until b.height) for (x in 0 until b.width) {
        val p = b.getPixel(x, y)
        AColor.colorToHSV(p, hsv)
        if (hsv[2] < .15f || (hsv[1] < .08f && hsv[2] > .9f)) continue // bỏ gần đen và gần trắng
        val wt = (hsv[1] * hsv[1] + .02).toDouble()
        r += AColor.red(p) * wt; g += AColor.green(p) * wt; bl += AColor.blue(p) * wt; w += wt
    }
    if (w == 0.0) return null
    return Color((r / w).toInt(), (g / w).toInt(), (bl / w).toInt())
}
