package com.sportshub.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sportshub.app.model.Team

/**
 * Team logo with a lettered badge while it loads or when there is none.
 * A logo shown anywhere before comes straight from memory on the first frame.
 *
 * Logos almost always sit next to the team's name, so screen readers skip them unless a
 * [contentDescription] is given; otherwise every team would be read out twice.
 */
@Composable
fun TeamLogo(team: Team, modifier: Modifier = Modifier, size: Dp = 32.dp, contentDescription: String? = null) {
    RetryingImage(
        url = team.logoUrl,
        contentDescription = contentDescription,
        modifier = modifier.size(size),
        placeholder = {
            Box(
                Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    team.abbreviation.take(3),
                    fontSize = (size.value * 0.3f).sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    )
}

/** Parses "#RRGGBB" into a color, or null if it isn't one. */
fun parseHexColor(hex: String?): Color? {
    val digits = hex?.removePrefix("#")?.takeIf { it.length == 6 } ?: return null
    val value = digits.toLongOrNull(16) ?: return null
    return Color(0xFF000000 or value)
}

/**
 * A team's color, nudged so it stays visible on the current background:
 * very dark colors are lifted in dark mode, very light ones deepened in light mode.
 */
@Composable
fun Team.accentColor(fallback: Color = MaterialTheme.colorScheme.outline): Color {
    val base = parseHexColor(primaryColor) ?: return fallback
    val darkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val lum = base.luminance()
    return when {
        darkTheme && lum < 0.06f -> lerp(base, Color.White, 0.45f)
        !darkTheme && lum > 0.75f -> lerp(base, Color.Black, 0.35f)
        else -> base
    }
}

/**
 * A deep version of the team's color for big headers with white text on them:
 * bright colors are darkened until white reads well on top.
 */
fun Team.heroColor(): Color {
    val base = parseHexColor(primaryColor) ?: Color(0xFF1E3F8C)
    val lum = base.luminance()
    return when {
        lum > 0.45f -> lerp(base, Color.Black, 0.55f)
        lum > 0.2f -> lerp(base, Color.Black, 0.35f)
        lum < 0.02f -> lerp(base, Color(0xFF2A2F3A), 0.6f)
        else -> lerp(base, Color.Black, 0.12f)
    }
}
