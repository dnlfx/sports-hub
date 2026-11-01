package com.sportshub.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Accent = Color(0xFF3D7BFF)
val LiveRed = Color(0xFFFF3B47)

private val DarkColors = darkColorScheme(
    primary = Accent,
    onPrimary = Color.White,
    secondary = Color(0xFF8AA4D6),
    background = Color(0xFF0B0D12),
    onBackground = Color(0xFFF2F4F8),
    surface = Color(0xFF0B0D12),
    onSurface = Color(0xFFF2F4F8),
    surfaceVariant = Color(0xFF1A1E27),
    onSurfaceVariant = Color(0xFF9AA3B5),
    surfaceContainer = Color(0xFF13161D),
    surfaceContainerHigh = Color(0xFF1A1E27),
    outline = Color(0xFF2A303C),
    error = LiveRed,
)

private val LightColors = lightColorScheme(
    primary = Accent,
    onPrimary = Color.White,
    secondary = Color(0xFF4A5E85),
    background = Color(0xFFF6F7FA),
    onBackground = Color(0xFF0B0D12),
    surface = Color(0xFFF6F7FA),
    onSurface = Color(0xFF0B0D12),
    surfaceVariant = Color(0xFFE6E9F0),
    onSurfaceVariant = Color(0xFF5A6273),
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFEFF1F6),
    outline = Color(0xFFD5DAE3),
    error = LiveRed,
)

private val AppTypography = Typography().run {
    copy(
        headlineLarge = headlineLarge.copy(fontWeight = FontWeight.Black, letterSpacing = (-0.5).sp),
        headlineMedium = headlineMedium.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.25).sp),
        titleLarge = titleLarge.copy(fontWeight = FontWeight.Bold),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = labelLarge.copy(fontWeight = FontWeight.SemiBold),
    )
}

/** Tabular figures keep scores from jumping around as digits change. */
val ScoreTextStyle = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.Black,
    fontSize = 28.sp,
    fontFeatureSettings = "tnum",
)

@Composable
fun SportsHubTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AppTypography,
        content = content,
    )
}
