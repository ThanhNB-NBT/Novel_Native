package com.thanhnb.novelnative

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/** Bảng màu "giấy dó + son" chép nguyên từ theme.dart của app Flutter. */
class Accent(val name: String, val accent: Color, val deep: Color, val soft: Color, val seal: Color, val dAccent: Color, val dSoft: Color, val dSeal: Color, val extra: List<Color> = emptyList())

class Paper(
    val name: String, val bg: Color, val surface: Color, val surfaceAlt: Color, val line: Color, val ink: Color, val inkSoft: Color,
    val dBg: Color, val dSurface: Color, val dSurfaceAlt: Color, val dLine: Color, val dInk: Color, val dInkSoft: Color,
)

val accents = listOf(
    Accent("Son", Color(0xFF9A3B26), Color(0xFF7A2C1A), Color(0xFFF0DCCD), Color(0xFFB8322A), Color(0xFFE39A74), Color(0xFF33221A), Color(0xFFD0503F)),
    Accent("Ngọc", Color(0xFF2E6B55), Color(0xFF1F4D3D), Color(0xFFD7E6DC), Color(0xFF2F7A5E), Color(0xFF8DBBA3), Color(0xFF1A2A23), Color(0xFF4F9A7A)),
    Accent("Chàm", Color(0xFF34508A), Color(0xFF243A68), Color(0xFFDCE2EF), Color(0xFF3A5AA0), Color(0xFFA2B1D0), Color(0xFF1E2230), Color(0xFF6A7FB0)),
    Accent("Hoàng", Color(0xFF8A5A12), Color(0xFF6B440A), Color(0xFFF1E1C4), Color(0xFFB0761C), Color(0xFFE3B46A), Color(0xFF33281A), Color(0xFFD29A45)),
    Accent("Tử", Color(0xFF6E3A6E), Color(0xFF522A52), Color(0xFFEADAE8), Color(0xFF8A3F86), Color(0xFFC6A4C3), Color(0xFF2B1F2A), Color(0xFFA0689C)),
    Accent("Đào", Color(0xFFB04A6B), Color(0xFF8A3552), Color(0xFFF5DDE5), Color(0xFFC2476B), Color(0xFFE8A0B8), Color(0xFF35202A), Color(0xFFD0607F)),
    Accent("Lam", Color(0xFF1F7A82), Color(0xFF135A61), Color(0xFFD3E8EA), Color(0xFF22818A), Color(0xFF7FC3C9), Color(0xFF16292B), Color(0xFF3E9AA3)),
    Accent("Cam", Color(0xFFB5561C), Color(0xFF8A3F10), Color(0xFFF6E0CF), Color(0xFFC9601F), Color(0xFFEDA574), Color(0xFF35241A), Color(0xFFD97B3C)),
    Accent("Lục", Color(0xFF557A2B), Color(0xFF3E5A1E), Color(0xFFE0EBD0), Color(0xFF5E8A2E), Color(0xFFA9C98A), Color(0xFF212B19), Color(0xFF7BA24E)),
    Accent("Mực", Color(0xFF3F4A5A), Color(0xFF2B3440), Color(0xFFDFE3E9), Color(0xFF4A5A70), Color(0xFFB4BFCE), Color(0xFF222831), Color(0xFF7E8DA3)),
) + listOf(
    // màu nhấn mô phỏng tông hệ điều hành (đã hạ sáng chút để chữ trắng trên nền màu vẫn đọc được)
    acc("ColorOS", 0xFF009E60), acc("iOS", 0xFF0A74F0, 0xFFFF3B30, 0xFFFF9500, 0xFFFFCC00), acc("iOS Hồng", 0xFFE0264F, 0xFFFF3B30, 0xFFFF9500, 0xFFFFCC00), acc("Material", 0xFF6750A4, 0xFFD93025, 0xFFE37400, 0xFFF9AB00),
    acc("Google", 0xFF1A73E8, 0xFFD93025, 0xFFE37400, 0xFFF9AB00), acc("HyperOS", 0xFFE85D00), acc("iOS Chàm", 0xFF5856D6, 0xFFFF3B30, 0xFFFF9500, 0xFFFFCC00),
    // bộ "Màu nổi bật" kiểu ColorOS 16: màu chính + đỏ + cam + vàng (đo từ ảnh chụp cài đặt Màu của máy thật)
    acc("Duyên hải", 0xFF0166FF, 0xFFE32E27, 0xFFFF7701, 0xFFFFB200),
    acc("Sơn cước", 0xFF00A812, 0xFFE32E27, 0xFFFB6A35, 0xFFFEC013),
    acc("Thịnh hành", 0xFF17BFC1, 0xFFF23E5B, 0xFFFF7733, 0xFFFFCC4D),
    // iOS: lấy từ systemGreen/Orange/Teal (hạ sáng nhẹ cho chữ trắng); Windows: xanh #0078D4 là màu nhấn mặc định của Windows 11
    acc("iOS Lục", 0xFF2DB350, 0xFFFF3B30, 0xFFFF9500, 0xFFFFCC00), acc("iOS Cam", 0xFFE58600, 0xFFFF3B30, 0xFFFF9500, 0xFFFFCC00), acc("iOS Ngọc", 0xFF2FA8DC, 0xFFFF3B30, 0xFFFF9500, 0xFFFFCC00),
    acc("Windows", 0xFF0078D4, 0xFFE81123, 0xFFF7630C, 0xFFFFB900), acc("Windows Lục", 0xFF107C10, 0xFFE81123, 0xFFF7630C, 0xFFFFB900), acc("Windows Ngọc", 0xFF00A0AB, 0xFFE81123, 0xFFF7630C, 0xFFFFB900), acc("Windows Tím", 0xFF881798, 0xFFE81123, 0xFFF7630C, 0xFFFFB900),
    acc("macOS Tím", 0xFF953D96, 0xFFE0383E, 0xFFF7821B, 0xFFFFC600), acc("macOS Hồng", 0xFFE03C8A, 0xFFE0383E, 0xFFF7821B, 0xFFFFC600), acc("macOS Lục", 0xFF4FA535, 0xFFE0383E, 0xFFF7821B, 0xFFFFC600), acc("macOS Cam", 0xFFE56F0A, 0xFFE0383E, 0xFFF7821B, 0xFFFFC600), acc("macOS Than", 0xFF6E6E73, 0xFFE0383E, 0xFFF7821B, 0xFFFFC600),
    acc("Material Lục", 0xFF386A20, 0xFFD93025, 0xFFE37400, 0xFFF9AB00), acc("Material Lam", 0xFF0B57D0, 0xFFD93025, 0xFFE37400, 0xFFF9AB00),
)

/** Sinh bộ màu nhấn từ một màu gốc: đậm/nhạt/sáng-tối đều là pha với đen hoặc trắng. */
private fun acc(name: String, c: Long, vararg extra: Long): Accent {
    val b = Color(c)
    fun mix(t: Color, f: Float) = androidx.compose.ui.graphics.lerp(b, t, f)
    return Accent(name, b, mix(Color.Black, .28f), mix(Color.White, .84f), b, mix(Color.White, .38f), mix(Color.Black, .82f), mix(Color.White, .12f), extra.map { Color(it) })
}

val papers = listOf(
    Paper("Giấy dó", Color(0xFFF3EBDD), Color(0xFFFAF5EB), Color(0xFFE9DFCB), Color(0xFFE2D5BD), Color(0xFF2B2118), Color(0xFF6E5A48),
        Color(0xFF14110E), Color(0xFF1C1814), Color(0xFF26201A), Color(0xFF2E2720), Color(0xFFEDE3CF), Color(0xFFA39381)),
    Paper("Trắng ngà", Color(0xFFF7F5F0), Color(0xFFFFFFFF), Color(0xFFECE9E2), Color(0xFFE3DFD6), Color(0xFF26221D), Color(0xFF5F5850),
        Color(0xFF121212), Color(0xFF1C1C1E), Color(0xFF26262A), Color(0xFF2C2C2F), Color(0xFFE8E6E3), Color(0xFF9E9A94)),
    Paper("Tuyết", Color(0xFFF2F3F5), Color(0xFFFFFFFF), Color(0xFFE6E8EC), Color(0xFFDDE0E5), Color(0xFF1F2328), Color(0xFF5A616B),
        Color(0xFF0F151E), Color(0xFF17202B), Color(0xFF202B38), Color(0xFF26313F), Color(0xFFE1E6ED), Color(0xFF98A3B2)),
    Paper("Trúc", Color(0xFFEEF2EC), Color(0xFFF8FAF6), Color(0xFFE0E7DD), Color(0xFFD6DED2), Color(0xFF1F2A22), Color(0xFF55635A),
        Color(0xFF111612), Color(0xFF19201B), Color(0xFF222B24), Color(0xFF2A332C), Color(0xFFE0E8E1), Color(0xFF98A69B)),
    Paper("Hồng phấn", Color(0xFFF8EEEC), Color(0xFFFFF8F6), Color(0xFFF0DEDA), Color(0xFFE8D0CB), Color(0xFF2B1E1D), Color(0xFF6E5654),
        Color(0xFF17100F), Color(0xFF1F1716), Color(0xFF291F1E), Color(0xFF332827), Color(0xFFF0E2E0), Color(0xFFA89290)),
    Paper("Lam sương", Color(0xFFE9F0F5), Color(0xFFF6FAFC), Color(0xFFDAE6EE), Color(0xFFCEDCE6), Color(0xFF1C2630), Color(0xFF52626F),
        Color(0xFF0E151B), Color(0xFF151E26), Color(0xFF1E2832), Color(0xFF27323D), Color(0xFFDEE8F0), Color(0xFF93A3B2)),
    Paper("Hoa cà", Color(0xFFF1ECF5), Color(0xFFFAF7FD), Color(0xFFE4DCEC), Color(0xFFDAD0E4), Color(0xFF251E2D), Color(0xFF61566E),
        Color(0xFF130F18), Color(0xFF1B1621), Color(0xFF251E2D), Color(0xFF2E2638), Color(0xFFE8E0F0), Color(0xFF9F94AD)),
    Paper("Than", Color(0xFFEDEDED), Color(0xFFFFFFFF), Color(0xFFDEDEDE), Color(0xFFD4D4D4), Color(0xFF111111), Color(0xFF555555),
        Color(0xFF000000), Color(0xFF0C0C0C), Color(0xFF161616), Color(0xFF222222), Color(0xFFEDEDED), Color(0xFF8A8A8A)),
    Paper("ColorOS", Color(0xFFF4F5F7), Color(0xFFFFFFFF), Color(0xFFE8EAED), Color(0xFFDEE0E4), Color(0xFF17181A), Color(0xFF5F6368),
        Color(0xFF0F1011), Color(0xFF1B1C1E), Color(0xFF262729), Color(0xFF2F3033), Color(0xFFE8E9EA), Color(0xFF9AA0A6)),
    Paper("iOS", Color(0xFFF2F2F7), Color(0xFFFFFFFF), Color(0xFFE5E5EA), Color(0xFFD1D1D6), Color(0xFF1C1C1E), Color(0xFF6C6C70),
        Color(0xFF000000), Color(0xFF1C1C1E), Color(0xFF2C2C2E), Color(0xFF38383A), Color(0xFFF2F2F7), Color(0xFF98989D)),
    Paper("Material", Color(0xFFFEF7FF), Color(0xFFF7F2FA), Color(0xFFECE6F0), Color(0xFFCAC4D0), Color(0xFF1D1B20), Color(0xFF49454F),
        Color(0xFF141218), Color(0xFF211F26), Color(0xFF2B2930), Color(0xFF49454F), Color(0xFFE6E0E9), Color(0xFFCAC4D0)),
)

val Jakarta = FontFamily(
    Font(R.font.jakarta_regular, FontWeight.Normal),
    Font(R.font.jakarta_medium, FontWeight.Medium),
    Font(R.font.jakarta_semibold, FontWeight.SemiBold),
    Font(R.font.jakarta_bold, FontWeight.Bold),
    Font(R.font.jakarta_extrabold, FontWeight.ExtraBold),
)
val Serif = FontFamily(Font(R.font.noto_serif_bold, FontWeight.Bold))

/** Màu triện theo bộ màu nhấn đang chọn. */
val LocalSeal = staticCompositionLocalOf { Color(0xFFB8322A) }

private fun type(ink: Color, soft: Color): Typography {
    fun f(size: Int, w: FontWeight, sp: Double = 0.0, h: Double = 1.2, c: Color = Color.Unspecified) =
        TextStyle(fontFamily = Jakarta, fontSize = size.sp, fontWeight = w, letterSpacing = sp.sp, lineHeight = h.em, color = c)
    fun serif(size: Double, sp: Double = 0.0, h: Double = 1.2) =
        TextStyle(fontFamily = Serif, fontSize = size.sp, fontWeight = FontWeight.Bold, letterSpacing = sp.sp, lineHeight = h.em)
    return Typography(
        displayMedium = serif(36.0, -0.8, 1.05),
        displaySmall = serif(29.0, -0.6, 1.1),
        headlineMedium = serif(25.0, -0.4),
        headlineSmall = serif(20.5, -0.2),
        titleLarge = serif(18.0, -0.1, 1.25),
        titleMedium = f(15, FontWeight.SemiBold, -0.1).copy(fontSize = 15.5.sp),
        bodyLarge = f(15, FontWeight.Normal, h = 1.5).copy(fontSize = 15.5.sp),
        bodyMedium = f(14, FontWeight.Normal, h = 1.55, c = soft),
        bodySmall = f(12, FontWeight.Normal, h = 1.4, c = soft).copy(fontSize = 12.5.sp),
        labelLarge = f(14, FontWeight.SemiBold),
        labelMedium = f(13, FontWeight.Medium, c = soft),
        labelSmall = f(11, FontWeight.Bold, 0.6, c = soft).copy(fontSize = 11.5.sp),
    )
}

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val dark = when (Prefs.themeMode) { 1 -> false; 2 -> true; else -> isSystemInDarkTheme() }
    val a = accents.getOrElse(Prefs.accent) { accents[0] }
    val p = papers.getOrElse(Prefs.paper) { papers[0] }
    val ink = if (dark) p.dInk else p.ink
    val soft = if (dark) p.dInkSoft else p.inkSoft
    val line = if (dark) p.dLine else p.line
    val card = if (dark) p.dSurface else p.surface
    // màu nhấn mô phỏng hệ điều hành có bộ khá nhạt (xanh ngọc, cam, lục…): trên thẻ sáng kém 3:1 thì hạ sáng vừa đủ để icon/nhãn còn nhìn rõ
    val accent = (if (dark) a.dAccent else a.accent).let {
        if (dark) it else ensureContrast(it, card, 3f, Color.Black).let { c ->
            // màu trung tính-sáng (cam, xanh Google…) đọc kém cả với chữ kem lẫn chữ mực: hạ thêm để chữ kem trên nút đạt 4.5:1
            if (maxOf(contrast(c, Color(0xFFFAF5EB)), contrast(c, Color(0xFF1B1208))) < 4.5f) ensureContrast(c, Color(0xFFFAF5EB), 4.5f, Color.Black) else c
        }
    }
    val seal = (if (dark) a.dSeal else a.seal).let { if (dark) it else ensureContrast(it, card, 3f, Color.Black) }
    val scheme = (if (dark) darkColorScheme() else lightColorScheme()).copy(
        primary = accent,
        onPrimary = onColor(accent, dark = if (dark) Color(0xFF2A140B) else Color(0xFF1B1208)), // chữ trên nút: kem hoặc mực, theo cái đọc rõ hơn
        primaryContainer = if (dark) a.dSoft else a.soft,
        onPrimaryContainer = if (dark) a.dAccent else a.deep,
        secondary = a.extra.getOrNull(1) ?: if (dark) Color(0xFFF2A65A) else Color(0xFFE8913C),
        onSecondary = Color(0xFF2B2118),
        tertiary = a.extra.getOrNull(2) ?: if (dark) Color(0xFF3DBE76) else Color(0xFF27864D),
        onTertiary = Color.White,
        error = a.extra.getOrNull(0) ?: if (dark) Color(0xFFD1544A) else Color(0xFFCE493F),
        background = if (dark) p.dBg else p.bg,
        onBackground = ink,
        surface = if (dark) p.dBg else p.bg, // Scaffold/Surface mặc định = nền giấy
        onSurface = ink,
        surfaceVariant = if (dark) p.dSurfaceAlt else p.surfaceAlt,
        onSurfaceVariant = soft,
        // "thẻ" (Card, sheet, dialog, menu) = giấy mới hơn nền một bậc
        surfaceContainerLowest = if (dark) p.dSurface else p.surface,
        surfaceContainerLow = if (dark) p.dSurface else p.surface,
        surfaceContainer = if (dark) p.dSurface else p.surface,
        surfaceContainerHigh = if (dark) p.dSurface else p.surface,
        surfaceContainerHighest = if (dark) p.dSurfaceAlt else p.surfaceAlt,
        outline = line,
        outlineVariant = line,
        secondaryContainer = (if (dark) a.dSoft else a.soft),
        onSecondaryContainer = if (dark) a.dAccent else a.deep,
    )
    val view = androidx.compose.ui.platform.LocalView.current
    androidx.compose.runtime.SideEffect {
        val ctl = androidx.core.view.WindowCompat.getInsetsController((view.context as android.app.Activity).window, view)
        ctl.isAppearanceLightStatusBars = !dark
        ctl.isAppearanceLightNavigationBars = !dark
    }
    MaterialTheme(
        scheme,
        shapes = Shapes(
            extraSmall = RoundedCornerShape(12.dp), small = RoundedCornerShape(16.dp), medium = RoundedCornerShape(22.dp),
            large = RoundedCornerShape(28.dp), extraLarge = RoundedCornerShape(36.dp),
        ),
        typography = type(ink, soft),
    ) {
        androidx.compose.runtime.CompositionLocalProvider(LocalSeal provides seal, content = content)
    }
}

/** Tỉ lệ tương phản WCAG giữa hai màu (1–21). */
fun contrast(a: Color, b: Color): Float {
    val x = a.luminance()
    val y = b.luminance()
    return (maxOf(x, y) + .05f) / (minOf(x, y) + .05f)
}

/** Pha dần [c] về phía [toward] cho tới khi tương phản với [bg] đạt [min] (tối đa 40 bước; màu đã đạt thì giữ nguyên). */
fun ensureContrast(c: Color, bg: Color, min: Float, toward: Color): Color {
    var x = c
    var i = 0
    while (contrast(x, bg) < min && i++ < 40) x = lerp(x, toward, .05f)
    return x
}

/** Màu chữ/icon đặt trên nền [bg]: lấy trong [light] / [dark] cái tương phản hơn; [lightBias] > 1 ưu tiên chữ sáng khi hai bên xấp xỉ (nhãn in đậm, cỡ lớn). */
fun onColor(bg: Color, light: Color = Color(0xFFFAF5EB), dark: Color = Color(0xFF1B1208), lightBias: Float = 1f) = if (contrast(bg, light) * lightBias >= contrast(bg, dark)) light else dark

/** Màu thẻ (giấy mới) — nền tách khỏi nền trang. */
val ColorScheme.card get() = surfaceContainerLow

/** Cặp (màu nhấn, màu nền) dựng sẵn theo phong cách hệ điều hành: tên, chỉ số trong [accents], chỉ số trong [papers]. */
val presets = listOf(
    Triple("ColorOS", 10, 8), Triple("iOS", 11, 9), Triple("Material You", 13, 10), Triple("Google", 14, 10), Triple("HyperOS", 15, 8),
)

/** Nhóm thẻ "Màu nổi bật" hiện trong bảng màu: tên nhóm → tên các bộ trong [accents]. */
val accentGroups = listOf(
    "ColorOS" to listOf("Duyên hải", "Sơn cước", "Thịnh hành"),
    "iOS" to listOf("iOS", "iOS Chàm", "iOS Hồng", "iOS Lục", "iOS Cam", "iOS Ngọc"),
    "Windows" to listOf("Windows", "Windows Lục", "Windows Ngọc", "Windows Tím"),
    "macOS" to listOf("macOS Tím", "macOS Hồng", "macOS Lục", "macOS Cam", "macOS Than"),
    "Google · Material" to listOf("Google", "Material", "Material Lục", "Material Lam"),
)
