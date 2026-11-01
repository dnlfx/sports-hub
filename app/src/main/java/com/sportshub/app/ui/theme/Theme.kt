package com.sportshub.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sportshub.app.R

// ---------------------------------------------------------------------------
// Color
// ---------------------------------------------------------------------------

/** Brand blue, used for the app icon and as the base of the color scheme. */
val Accent = Color(0xFF3D7BFF)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF7FA8FF),
    onPrimary = Color(0xFF002A6B),
    primaryContainer = Color(0xFF1E3F8C),
    onPrimaryContainer = Color(0xFFDAE3FF),
    inversePrimary = Color(0xFF2A64E8),
    secondary = Color(0xFFB7C5E4),
    onSecondary = Color(0xFF1F2C45),
    secondaryContainer = Color(0xFF26324B),
    onSecondaryContainer = Color(0xFFD8E2FB),
    tertiary = Color(0xFFFFB870),
    onTertiary = Color(0xFF4A2800),
    tertiaryContainer = Color(0xFF693C00),
    onTertiaryContainer = Color(0xFFFFDCBE),
    background = Color(0xFF0D0F14),
    onBackground = Color(0xFFE6E8EE),
    surface = Color(0xFF0D0F14),
    onSurface = Color(0xFFE6E8EE),
    surfaceVariant = Color(0xFF242832),
    onSurfaceVariant = Color(0xFFA2A8B6),
    surfaceTint = Color(0xFF7FA8FF),
    surfaceDim = Color(0xFF0D0F14),
    surfaceBright = Color(0xFF33373F),
    surfaceContainerLowest = Color(0xFF08090C),
    surfaceContainerLow = Color(0xFF13161C),
    surfaceContainer = Color(0xFF181B22),
    surfaceContainerHigh = Color(0xFF1F232B),
    surfaceContainerHighest = Color(0xFF292D36),
    inverseSurface = Color(0xFFE2E4EA),
    inverseOnSurface = Color(0xFF2B2E35),
    outline = Color(0xFF7D8494),
    outlineVariant = Color(0xFF2C313B),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    scrim = Color.Black,
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF2A64E8),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDAE3FF),
    onPrimaryContainer = Color(0xFF001A43),
    inversePrimary = Color(0xFF7FA8FF),
    secondary = Color(0xFF4F5D78),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDCE3F5),
    onSecondaryContainer = Color(0xFF0B1A33),
    tertiary = Color(0xFF8A5100),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFDCBE),
    onTertiaryContainer = Color(0xFF2C1600),
    background = Color(0xFFF4F5F9),
    onBackground = Color(0xFF12141A),
    surface = Color(0xFFF4F5F9),
    onSurface = Color(0xFF12141A),
    surfaceVariant = Color(0xFFE1E4EC),
    onSurfaceVariant = Color(0xFF5A6070),
    surfaceTint = Color(0xFF2A64E8),
    surfaceDim = Color(0xFFD9DBE2),
    surfaceBright = Color(0xFFF9FAFD),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFEFF1F5),
    surfaceContainer = Color(0xFFE9EBF1),
    surfaceContainerHigh = Color(0xFFE3E6ED),
    surfaceContainerHighest = Color(0xFFDDE0E8),
    inverseSurface = Color(0xFF2B2E35),
    inverseOnSurface = Color(0xFFEFF1F6),
    outline = Color(0xFF757B8A),
    outlineVariant = Color(0xFFD7DBE4),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    scrim = Color.Black,
)

/** App colors that Material's scheme has no role for. Read them with [SportsHubTheme.colors]. */
@Immutable
data class ExtendedColors(
    /** Cards and list tiles that sit on the background. */
    val card: Color,
    /** Navigation bar and scrolled top bar. */
    val chrome: Color,
    /** Live games: badges, red-zone text. */
    val live: Color,
    val liveContainer: Color,
)

private val DarkExtended = ExtendedColors(
    card = Color(0xFF181B22),
    chrome = Color(0xFF13161C),
    live = Color(0xFFFF5A60),
    liveContainer = Color(0x33FF5A60),
)

private val LightExtended = ExtendedColors(
    card = Color.White,
    chrome = Color.White,
    live = Color(0xFFD7262E),
    liveContainer = Color(0x1FD7262E),
)

private val LocalExtendedColors = staticCompositionLocalOf { DarkExtended }

// ---------------------------------------------------------------------------
// Type
// ---------------------------------------------------------------------------

/** Condensed display face for big titles and scores. Body text stays on the system font. */
val DisplayFamily = FontFamily(
    Font(R.font.barlow_condensed_semibold, FontWeight.SemiBold),
    Font(R.font.barlow_condensed_bold, FontWeight.Bold),
    Font(R.font.barlow_condensed_extrabold, FontWeight.ExtraBold),
)

private val AppTypography = Typography().run {
    copy(
        displaySmall = displaySmall.copy(fontFamily = DisplayFamily, fontWeight = FontWeight.ExtraBold),
        headlineLarge = headlineLarge.copy(fontFamily = DisplayFamily, fontWeight = FontWeight.ExtraBold, fontSize = 34.sp, lineHeight = 40.sp),
        // Large top app bars use headlineMedium for the expanded title.
        headlineMedium = headlineMedium.copy(fontFamily = DisplayFamily, fontWeight = FontWeight.ExtraBold, fontSize = 34.sp, lineHeight = 40.sp),
        headlineSmall = headlineSmall.copy(fontFamily = DisplayFamily, fontWeight = FontWeight.Bold),
        titleLarge = titleLarge.copy(fontFamily = DisplayFamily, fontWeight = FontWeight.Bold, fontSize = 24.sp),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.SemiBold),
        titleSmall = titleSmall.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = labelLarge.copy(fontWeight = FontWeight.SemiBold),
        labelMedium = labelMedium.copy(fontWeight = FontWeight.Medium),
    )
}

/** Score digits: condensed, heavy, and tabular so numbers don't shift as they change. */
val ScoreTextStyle = TextStyle(
    fontFamily = DisplayFamily,
    fontWeight = FontWeight.ExtraBold,
    fontSize = 30.sp,
    lineHeight = 32.sp,
    fontFeatureSettings = "tnum",
)

/** Small uppercase label above a group, e.g. "LIVE NOW". */
val OverlineTextStyle = TextStyle(
    fontWeight = FontWeight.Bold,
    fontSize = 12.sp,
    lineHeight = 16.sp,
    letterSpacing = 1.sp,
)

// ---------------------------------------------------------------------------
// Shape
// ---------------------------------------------------------------------------

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun SportsHubTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val scheme: ColorScheme = if (darkTheme) DarkColors else LightColors
    CompositionLocalProvider(LocalExtendedColors provides if (darkTheme) DarkExtended else LightExtended) {
        MaterialTheme(
            colorScheme = scheme,
            typography = AppTypography,
            shapes = AppShapes,
            content = content,
        )
    }
}

object SportsHubTheme {
    val colors: ExtendedColors
        @Composable
        @ReadOnlyComposable
        get() = LocalExtendedColors.current
}
