package com.sportshub.app.ui.scores

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sportshub.app.model.Competitor
import com.sportshub.app.model.Game
import com.sportshub.app.model.GameStatus
import com.sportshub.app.ui.components.LiveBadge
import com.sportshub.app.ui.components.TeamLogo
import com.sportshub.app.ui.components.accentColor
import com.sportshub.app.ui.components.cardSurface
import com.sportshub.app.ui.components.shimmer
import com.sportshub.app.ui.theme.ScoreTextStyle
import com.sportshub.app.ui.theme.SportsHubTheme
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val dayTime = DateTimeFormatter.ofPattern("EEE h:mm a")
private val timeOnly = DateTimeFormatter.ofPattern("h:mm a")

/** Status line for a game, e.g. "Sun 1:00 PM", "Q3 4:12" or "Final/OT". */
fun Game.statusLabel(): String = when (val s = status) {
    GameStatus.Scheduled -> startTime.atZone(ZoneId.systemDefault()).format(dayTime)
    is GameStatus.Live -> s.detail.ifEmpty { "${sport.periodName}${s.period} ${s.clock.orEmpty()}".trim() }
    is GameStatus.Final -> s.detail
    is GameStatus.Postponed -> s.detail
}

/** Kickoff time only, for lists already grouped by day. */
fun Game.kickoffTime(): String = startTime.atZone(ZoneId.systemDefault()).format(timeOnly)

/** Small triangle pointing at the winning score. */
private val WinnerMarker = GenericShape { size, _ ->
    moveTo(size.width, 0f)
    lineTo(0f, size.height / 2f)
    lineTo(size.width, size.height)
    close()
}

@Composable
fun GameCard(game: Game, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val awayColor = game.away.team.accentColor()
    val homeColor = game.home.team.accentColor()
    val colors = SportsHubTheme.colors
    val live = game.isLive
    Column(
        modifier
            .fillMaxWidth()
            .cardSurface()
            .clickable(onClick = onClick)
            // A soft wash of each team's color from the left edge: away on top, home below.
            .drawBehind {
                drawRect(
                    brush = Brush.horizontalGradient(
                        0f to awayColor.copy(alpha = 0.16f),
                        0.45f to Color.Transparent,
                        endX = size.width,
                    ),
                    size = Size(size.width, size.height / 2f),
                )
                drawRect(
                    brush = Brush.horizontalGradient(
                        0f to homeColor.copy(alpha = 0.16f),
                        0.45f to Color.Transparent,
                        endX = size.width,
                    ),
                    topLeft = Offset(0f, size.height / 2f),
                    size = Size(size.width, size.height / 2f),
                )
                if (live) {
                    drawRect(colors.live, size = Size(size.width, 3.dp.toPx()))
                }
            }
            .padding(start = 16.dp, end = 14.dp, top = 16.dp, bottom = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                TeamLine(game.away, game, opponent = game.home)
                TeamLine(game.home, game, opponent = game.away)
            }
            Box(
                Modifier
                    .padding(start = 12.dp)
                    .width(1.dp)
                    .height(60.dp)
                    .background(colors.stroke),
            )
            StatusColumn(game, Modifier.width(88.dp))
        }
        SituationFooter(game)
    }
}

@Composable
private fun TeamLine(side: Competitor, game: Game, opponent: Competitor) {
    val final = game.status is GameStatus.Final
    val lost = final && opponent.isWinner
    val hasBall = game.situation?.possessionTeamId == side.team.id
    Row(verticalAlignment = Alignment.CenterVertically) {
        TeamLogo(side.team, size = 34.dp, modifier = Modifier.alpha(if (lost) 0.5f else 1f))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    side.team.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (lost) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (hasBall) {
                    Spacer(Modifier.width(6.dp))
                    Box(
                        Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.tertiary),
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
                Text(
                    "$score",
                    style = ScoreTextStyle,
                    color = if (lost) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                )
            }
            // Keeps both scores aligned whether or not the marker shows.
            Box(Modifier.padding(start = 6.dp).size(width = 6.dp, height = 10.dp)) {
                if (final && side.isWinner) {
                    Box(
                        Modifier
                            .size(width = 6.dp, height = 10.dp)
                            .background(MaterialTheme.colorScheme.onSurface, WinnerMarker),
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusColumn(game: Game, modifier: Modifier) {
    Column(modifier.padding(start = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
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
            GameStatus.Scheduled -> Text(
                game.kickoffTime(),
                style = MaterialTheme.typography.titleSmall,
                textAlign = TextAlign.Center,
            )
            else -> Text(
                game.statusLabel(),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
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
private fun SituationFooter(game: Game) {
    val situation = game.situation ?: return
    if (situation.downDistance == null && situation.lastPlay == null) return
    Spacer(Modifier.height(12.dp))
    situation.downDistance?.let {
        val redZone = situation.isRedZone
        Text(
            if (redZone) "$it · Red zone" else it,
            style = MaterialTheme.typography.labelLarge,
            color = if (redZone) SportsHubTheme.colors.live else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .clip(CircleShape)
                .background(if (redZone) SportsHubTheme.colors.liveContainer else SportsHubTheme.colors.cardHigh)
                .padding(horizontal = 10.dp, vertical = 4.dp),
        )
        Spacer(Modifier.height(6.dp))
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

/** Placeholder shaped like a [GameCard], shown while scores load. */
@Composable
fun GameCardSkeleton(modifier: Modifier = Modifier) {
    val block = SportsHubTheme.colors.cardHigh
    Box(modifier.fillMaxWidth().cardSurface()) {
        Row(
            Modifier
                .padding(start = 18.dp, end = 14.dp, top = 16.dp, bottom = 16.dp)
                .shimmer(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                repeat(2) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(32.dp).clip(CircleShape).background(block))
                        Spacer(Modifier.width(12.dp))
                        Box(Modifier.size(width = 110.dp, height = 14.dp).clip(CircleShape).background(block))
                        Spacer(Modifier.weight(1f))
                        Box(Modifier.size(width = 28.dp, height = 22.dp).clip(MaterialTheme.shapes.extraSmall).background(block))
                    }
                }
            }
            Spacer(Modifier.width(20.dp))
            Box(Modifier.size(width = 64.dp, height = 14.dp).clip(CircleShape).background(block))
            Spacer(Modifier.width(12.dp))
        }
    }
}
