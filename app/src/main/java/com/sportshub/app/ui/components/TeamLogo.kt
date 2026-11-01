package com.sportshub.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import com.sportshub.app.model.Team

/** Team logo with a lettered fallback while loading or when there is no logo. */
@Composable
fun TeamLogo(team: Team, size: Dp = 32.dp, modifier: Modifier = Modifier) {
    val fallback = @Composable {
        Box(
            modifier = Modifier
                .size(size)
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
    }
    if (team.logoUrl == null) {
        Box(modifier) { fallback() }
    } else {
        SubcomposeAsyncImage(
            model = team.logoUrl,
            contentDescription = team.name,
            modifier = modifier.size(size),
            loading = { fallback() },
            error = { fallback() },
        )
    }
}
