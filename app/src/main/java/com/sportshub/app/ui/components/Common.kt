package com.sportshub.app.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sportshub.app.ui.theme.OverlineTextStyle
import com.sportshub.app.ui.theme.SportsHubTheme

/** Small uppercase heading above a group of items, with an optional count and trailing action. */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    count: Int? = null,
    accent: Boolean = false,
    padding: PaddingValues = PaddingValues(start = 20.dp, end = 12.dp, top = 16.dp, bottom = 4.dp),
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(padding)
            .semantics { heading() },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title.uppercase(),
            style = OverlineTextStyle,
            color = if (accent) SportsHubTheme.colors.live else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (count != null) {
            Spacer(Modifier.width(8.dp))
            Text(
                "$count",
                style = OverlineTextStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            )
        }
        Spacer(Modifier.weight(1f))
        trailing?.invoke()
    }
}

/** Pulsing "LIVE" pill. The pulse runs in the draw phase, so it doesn't recompose every frame. */
@Composable
fun LiveBadge(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "live")
    val pulse by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "pulse",
    )
    val colors = SportsHubTheme.colors
    Row(
        modifier
            .clip(CircleShape)
            .background(colors.liveContainer)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(6.dp)
                .graphicsLayer { alpha = pulse }
                .clip(CircleShape)
                .background(colors.live),
        )
        Spacer(Modifier.width(5.dp))
        Text("LIVE", color = colors.live, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 0.5.sp)
    }
}

/** Soft pulsing placeholder for content that's still loading. Animates in the draw phase only. */
fun Modifier.shimmer(): Modifier = composed {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val alpha by transition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(850), RepeatMode.Reverse),
        label = "alpha",
    )
    graphicsLayer { this.alpha = alpha }
}
