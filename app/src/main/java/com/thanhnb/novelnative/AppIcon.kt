package com.thanhnb.novelnative

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.ContextCompat

/** Một kiểu biểu tượng app = một activity-alias ".Logo<alias>" trong AndroidManifest.xml (ảnh sinh bởi tools/gen_icon_styles.py). */
class IconStyle(val alias: String, val name: String, val icon: Int)

// kiểu đầu là mặc định (alias duy nhất enabled="true" trong manifest)
val iconStyles = listOf(
    IconStyle("Holo", "Hologram đêm", R.mipmap.ic_launcher),
    IconStyle("Pearl", "Ngọc trai", R.mipmap.ic_launcher_pearl),
    IconStyle("Paper", "Giấy dó ấn son", R.mipmap.ic_launcher_paper),
    IconStyle("Band", "Chữ son ánh nhũ", R.mipmap.ic_launcher_band),
    IconStyle("Son", "Bìa Son", R.mipmap.ic_launcher_son),
    IconStyle("Ngoc", "Bìa Ngọc", R.mipmap.ic_launcher_ngoc),
    IconStyle("Cham", "Bìa Chàm", R.mipmap.ic_launcher_cham),
    IconStyle("Hoang", "Bìa Hoàng", R.mipmap.ic_launcher_hoang),
    IconStyle("Classic", "Xanh cổ điển", R.mipmap.ic_launcher_classic),
)

/** Đổi biểu tượng ngoài màn hình chính bằng bật/tắt alias; trạng thái đọc thẳng từ PackageManager nên không cần lưu pref. */
object AppIcon {
    private fun cn(c: Context, s: IconStyle) = ComponentName(c, "${MainActivity::class.java.`package`!!.name}.Logo${s.alias}")

    fun current(c: Context): Int = iconStyles.indices.firstOrNull { i ->
        when (c.packageManager.getComponentEnabledSetting(cn(c, iconStyles[i]))) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
            PackageManager.COMPONENT_ENABLED_STATE_DEFAULT -> i == 0
            else -> false
        }
    } ?: 0

    fun set(c: Context, pick: Int) {
        val pm = c.packageManager
        // bật cái mới trước rồi mới tắt cái cũ: không lúc nào app mất hẳn khỏi launcher
        pm.setComponentEnabledSetting(cn(c, iconStyles[pick]), PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
        iconStyles.forEachIndexed { i, s ->
            if (i != pick) pm.setComponentEnabledSetting(cn(c, s), PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
        }
    }

    /** Ảnh xem trước: vẽ AdaptiveIconDrawable → đã cắt theo hình icon của máy (tròn, squircle...). */
    fun preview(c: Context, s: IconStyle, px: Int): ImageBitmap {
        val d = ContextCompat.getDrawable(c, s.icon)!!
        return Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888).also { d.setBounds(0, 0, px, px); d.draw(Canvas(it)) }.asImageBitmap()
    }
}
