package com.thanhnb.novelnative

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/** Bảng màu "giấy dó + son" chép nguyên từ theme.dart của app Flutter. */
class Accent(val name: String, val accent: Color, val deep: Color, val soft: Color, val seal: Color, val dAccent: Color, val dSoft: Color, val dSeal: Color)

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
)

val papers = listOf(
    Paper("Giấy dó", Color(0xFFF3EBDD), Color(0xFFFAF5EB), Color(0xFFE9DFCB), Color(0xFFE2D5BD), Color(0xFF2B2118), Color(0xFF6E5A48),
        Color(0xFF14110E), Color(0xFF1C1814), Color(0xFF26201A), Color(0xFF2E2720), Color(0xFFEDE3CF), Color(0xFFA39381)),
    Paper("Trắng ngà", Color(0xFFF7F5F0), Color(0xFFFFFFFF), Color(0xFFECE9E2), Color(0xFFE3DFD6), Color(0xFF26221D), Color(0xFF5F5850),
        Color(0xFF121212), Color(0xFF1C1C1E), Color(0xFF26262A), Color(0xFF2C2C2F), Color(0xFFE8E6E3), Color(0xFF9E9A94)),
    Paper("Tuyết", Color(0xFFF2F3F5), Color(0xFFFFFFFF), Color(0xFFE6E8EC), Color(0xFFDDE0E5), Color(0xFF1F2328), Color(0xFF5A616B),
        Color(0xFF0F151E), Color(0xFF17202B), Color(0xFF202B38), Color(0xFF26313F), Color(0xFFE1E6ED), Color(0xFF98A3B2)),
    Paper("Trúc", Color(0xFFEEF2EC), Color(0xFFF8FAF6), Color(0xFFE0E7DD), Color(0xFFD6DED2), Color(0xFF1F2A22), Color(0xFF55635A),
        Color(0xFF111612), Color(0xFF19201B), Color(0xFF222B24), Color(0xFF2A332C), Color(0xFFE0E8E1), Color(0xFF98A69B)),
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
    val accent = if (dark) a.dAccent else a.accent
    val scheme = (if (dark) darkColorScheme() else lightColorScheme()).copy(
        primary = accent,
        onPrimary = if (dark) Color(0xFF2A140B) else Color(0xFFFAF5EB),
        primaryContainer = if (dark) a.dSoft else a.soft,
        onPrimaryContainer = if (dark) a.dAccent else a.deep,
        secondary = if (dark) Color(0xFFF2A65A) else Color(0xFFE8913C),
        onSecondary = Color(0xFF2B2118),
        tertiary = if (dark) Color(0xFF3DBE76) else Color(0xFF27864D),
        onTertiary = Color.White,
        error = if (dark) Color(0xFFD1544A) else Color(0xFFCE493F),
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
            extraSmall = RoundedCornerShape(10.dp), small = RoundedCornerShape(10.dp), medium = RoundedCornerShape(14.dp),
            large = RoundedCornerShape(18.dp), extraLarge = RoundedCornerShape(22.dp),
        ),
        typography = type(ink, soft),
    ) {
        androidx.compose.runtime.CompositionLocalProvider(LocalSeal provides (if (dark) a.dSeal else a.seal), content = content)
    }
}

/** Màu thẻ (giấy mới) — nền tách khỏi nền trang. */
val ColorScheme.card get() = surfaceContainerLow
