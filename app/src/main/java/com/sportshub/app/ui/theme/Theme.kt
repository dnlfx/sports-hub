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
//
// "Night game": an inky stadium-at-night base, the electric blue of the app icon
// as the main accent, and the football's ember orange for small highlights.
// ---------------------------------------------------------------------------

/** Brand blue, from the app icon. */
val Accent = Color(0xFF3D7BFF)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF6E9BFF),
    onPrimary = Color(0xFF001B4D),
    primaryContainer = Color(0xFF17306B),
    onPrimaryContainer = Color(0xFFDCE5FF),
    inversePrimary = Color(0xFF2A64E8),
    secondary = Color(0xFFB4C0DA),
    onSecondary = Color(0xFF1C2639),
    secondaryContainer = Color(0xFF1C2333),
    onSecondaryContainer = Color(0xFFD9E1F5),
    tertiary = Color(0xFFFF9A52),
    onTertiary = Color(0xFF3F1C00),
    tertiaryContainer = Color(0xFF5A2D07),
    onTertiaryContainer = Color(0xFFFFDCC6),
    background = Color(0xFF07090E),
    onBackground = Color(0xFFEDEFF5),
    surface = Color(0xFF07090E),
    onSurface = Color(0xFFEDEFF5),
    surfaceVariant = Color(0xFF1B202B),
    onSurfaceVariant = Color(0xFF8D95A7),
    surfaceTint = Color(0xFF6E9BFF),
    surfaceDim = Color(0xFF07090E),
    surfaceBright = Color(0xFF2B303B),
    surfaceContainerLowest = Color(0xFF040507),
    surfaceContainerLow = Color(0xFF0E1118),
    surfaceContainer = Color(0xFF12151D),
    surfaceContainerHigh = Color(0xFF191D27),
    surfaceContainerHighest = Color(0xFF222733),
    inverseSurface = Color(0xFFEDEFF5),
    inverseOnSurface = Color(0xFF12151D),
    outline = Color(0xFF5E6678),
    outlineVariant = Color(0xFF1D222D),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF5C1016),
    onErrorContainer = Color(0xFFFFDAD6),
    scrim = Color.Black,
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF2257E0),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE5FF),
    onPrimaryContainer = Color(0xFF00184A),
    inversePrimary = Color(0xFF6E9BFF),
    secondary = Color(0xFF4B5873),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE3E8F4),
    onSecondaryContainer = Color(0xFF111B2F),
    tertiary = Color(0xFFC85A12),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFDCC6),
    onTertiaryContainer = Color(0xFF331300),
    background = Color(0xFFF3F4F8),
    onBackground = Color(0xFF0D1018),
    surface = Color(0xFFF3F4F8),
    onSurface = Color(0xFF0D1018),
    surfaceVariant = Color(0xFFE4E7EE),
    onSurfaceVariant = Color(0xFF5D6475),
    surfaceTint = Color(0xFF2257E0),
    surfaceDim = Color(0xFFDADCE3),
    surfaceBright = Color(0xFFFAFBFD),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF6F7FA),
    surfaceContainer = Color(0xFFEEF0F4),
    surfaceContainerHigh = Color(0xFFE8EAF0),
    surfaceContainerHighest = Color(0xFFE1E4EB),
    inverseSurface = Color(0xFF151922),
    inverseOnSurface = Color(0xFFF1F3F8),
    outline = Color(0xFF7B8293),
    outlineVariant = Color(0xFFE2E5EC),
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
    /** A step up from [card]: chips, table headers, pressed rows. */
    val cardHigh: Color,
    /** Hairline border around cards. */
    val stroke: Color,
    /** The floating navigation bar. */
    val chrome: Color,
    /** Live games: badges, red-zone text. */
    val live: Color,
    val liveContainer: Color,
    /** Wins on a schedule. */
    val win: Color,
    /** Losses on a schedule. */
    val loss: Color,
    val isDark: Boolean,
)

private val DarkExtended = ExtendedColors(
    card = Color(0xFF10131A),
    cardHigh = Color(0xFF181C26),
    stroke = Color(0xFF1C212C),
    chrome = Color(0xF2131721),
    live = Color(0xFFFF4D5A),
    liveContainer = Color(0x2EFF4D5A),
    win = Color(0xFF34D399),
    loss = Color(0xFFFF6B6B),
    isDark = true,
)

private val LightExtended = ExtendedColors(
    card = Color.White,
    cardHigh = Color(0xFFF1F3F7),
    stroke = Color(0xFFE3E6EE),
    chrome = Color(0xF7FFFFFF),
    live = Color(0xFFDC2433),
    liveContainer = Color(0x1ADC2433),
    win = Color(0xFF0E9F6E),
    loss = Color(0xFFD9363E),
    isDark = false,
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
        displayLarge = displayLarge.copy(fontFamily = DisplayFamily, fontWeight = FontWeight.ExtraBold),
        displayMedium = displayMedium.copy(fontFamily = DisplayFamily, fontWeight = FontWeight.ExtraBold),
        displaySmall = displaySmall.copy(fontFamily = DisplayFamily, fontWeight = FontWeight.ExtraBold),
        headlineLarge = headlineLarge.copy(fontFamily = DisplayFamily, fontWeight = FontWeight.ExtraBold, fontSize = 38.sp, lineHeight = 42.sp),
        // Large top app bars use headlineMedium for the expanded title.
        headlineMedium = headlineMedium.copy(
            fontFamily = DisplayFamily,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 40.sp,
            lineHeight = 44.sp,
            letterSpacing = (-0.5).sp,
        ),
        headlineSmall = headlineSmall.copy(fontFamily = DisplayFamily, fontWeight = FontWeight.Bold),
        titleLarge = titleLarge.copy(fontFamily = DisplayFamily, fontWeight = FontWeight.Bold, fontSize = 23.sp, letterSpacing = 0.2.sp),
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
    fontSize = 11.sp,
    lineHeight = 16.sp,
    letterSpacing = 1.2.sp,
)

/** Numbers in stat tables: tabular so columns line up. */
val TableNumberStyle = TextStyle(
    fontSize = 14.sp,
    lineHeight = 20.sp,
    fontFeatureSettings = "tnum",
)

// ---------------------------------------------------------------------------
// Shape
// ---------------------------------------------------------------------------

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
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
