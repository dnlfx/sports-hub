package com.sportshub.app.ui.scores

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sportshub.app.model.Competitor
import com.sportshub.app.model.Game
import com.sportshub.app.model.GameStatus
import com.sportshub.app.ui.components.TeamLogo
import com.sportshub.app.ui.theme.LiveRed
import com.sportshub.app.ui.theme.ScoreTextStyle
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val dayTime = DateTimeFormatter.ofPattern("EEE h:mm a")

fun Game.statusLabel(): String = when (val s = status) {
    GameStatus.Scheduled -> startTime.atZone(ZoneId.systemDefault()).format(dayTime)
    is GameStatus.Live -> s.detail.ifEmpty { "${sport.periodName}${s.period} ${s.clock.orEmpty()}" }
    is GameStatus.Final -> s.detail
    is GameStatus.Postponed -> s.detail
}

@Composable
fun GameCard(game: Game, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val final = game.status is GameStatus.Final
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                TeamLine(game.away, game, dim = final && game.home.isWinner)
                TeamLine(game.home, game, dim = final && game.away.isWinner)
            }
            Spacer(Modifier.width(12.dp))
            Box(
                Modifier
                    .width(1.dp)
                    .height(56.dp)
                    .background(MaterialTheme.colorScheme.outline),
            )
            StatusColumn(game, Modifier.width(92.dp))
        }
        val situation = game.situation
        if (situation != null && (situation.downDistance != null || situation.lastPlay != null)) {
            HorizontalDivider(Modifier.padding(vertical = 10.dp), color = MaterialTheme.colorScheme.outline)
            situation.downDistance?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (situation.isRedZone) LiveRed else MaterialTheme.colorScheme.onSurface,
                )
            }
            situation.lastPlay?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun TeamLine(side: Competitor, game: Game, dim: Boolean) {
    val hasBall = game.situation?.possessionTeamId == side.team.id
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.alpha(if (dim) 0.5f else 1f),
    ) {
        TeamLogo(side.team, size = 30.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    side.team.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (hasBall) {
                    Spacer(Modifier.width(6.dp))
                    Box(
                        Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                    )
                }
            }
            side.team.record?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (game.status != GameStatus.Scheduled) {
            AnimatedContent(
                targetState = side.score ?: 0,
                transitionSpec = {
                    (slideInVertically { it } + fadeIn()) togetherWith (slideOutVertically { -it } + fadeOut())
                },
                label = "score",
            ) { score ->
                Text("$score", style = ScoreTextStyle)
            }
        }
    }
}

@Composable
private fun StatusColumn(game: Game, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        when (val s = game.status) {
            is GameStatus.Live -> {
                LiveBadge()
                Spacer(Modifier.height(6.dp))
                Text(
                    s.detail.ifEmpty { "${game.sport.periodName}${s.period}" },
                    style = MaterialTheme.typography.labelLarge,
                    textAlign = TextAlign.Center,
                )
            }
            GameStatus.Scheduled -> {
                Text(game.statusLabel(), style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
            }
            else -> {
                Text(
                    game.statusLabel(),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
        game.broadcasts.firstOrNull()?.let {
            Spacer(Modifier.height(4.dp))
            Text(
                it,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun LiveBadge(modifier: Modifier = Modifier) {
    val pulse by rememberInfiniteTransition(label = "live").animateFloat(
        initialValue = 1f,
        targetValue = 0.35f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "pulse",
    )
    Row(
        modifier
            .clip(RoundedCornerShape(50))
            .background(LiveRed.copy(alpha = 0.15f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(6.dp)
                .alpha(pulse)
                .clip(CircleShape)
                .background(LiveRed),
        )
        Spacer(Modifier.width(5.dp))
        Text("LIVE", color = LiveRed, fontSize = 11.sp, fontWeight = FontWeight.Black)
    }
}
