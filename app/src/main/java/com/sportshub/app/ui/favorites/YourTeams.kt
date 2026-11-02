package com.sportshub.app.ui.favorites

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sportshub.app.model.GameStatus
import com.sportshub.app.model.Scoreboard
import com.sportshub.app.model.Team
import com.sportshub.app.ui.components.TeamLogo
import com.sportshub.app.ui.components.accentColor
import com.sportshub.app.ui.components.cardSurface
import com.sportshub.app.ui.scores.statusLabel
import com.sportshub.app.ui.theme.SportsHubTheme

/** How a followed team's week looks, for its tile on the Scores tab. */
@Immutable
data class TeamWeek(val team: Team, val record: String?, val status: String, val tone: WeekTone)

enum class WeekTone { Live, Won, Lost, Upcoming, Idle }

/**
 * Each followed team's game on [board]: live score, result, kickoff time, or a bye.
 * Teams keep the order the user followed them in.
 */
fun teamWeeks(favorites: List<Team>, board: Scoreboard): List<TeamWeek> = favorites.map { team ->
    val game = board.games.firstOrNull { it.home.team.id == team.id || it.away.team.id == team.id }
    val side = game?.let { if (it.home.team.id == team.id) it.home else it.away }
    val other = game?.let { if (it.home.team.id == team.id) it.away else it.home }
    val known = side?.team
    val shown = team.copy(
        logoUrl = team.logoUrl ?: known?.logoUrl,
        primaryColor = team.primaryColor ?: known?.primaryColor,
    )
    when {
        game == null || side == null || other == null ->
            TeamWeek(shown, null, if (board.seasonType == 3) "Not playing" else "Bye week", WeekTone.Idle)
        game.status is GameStatus.Live ->
            TeamWeek(shown, known?.record, "Live ${side.score ?: 0}–${other.score ?: 0}", WeekTone.Live)
        game.status is GameStatus.Final -> {
            val won = side.isWinner || (side.score ?: 0) > (other.score ?: 0)
            val tied = !side.isWinner && !other.isWinner && side.score == other.score
            val letter = if (tied) "T" else if (won) "W" else "L"
            TeamWeek(
                shown,
                known?.record,
                "$letter ${side.score ?: 0}–${other.score ?: 0} ${if (game.home === side) "vs" else "@"} ${other.team.abbreviation}",
                if (won) WeekTone.Won else if (tied) WeekTone.Idle else WeekTone.Lost,
            )
        }
        else -> TeamWeek(
            shown,
            known?.record,
            "${if (game.home === side) "vs" else "@"} ${other.team.abbreviation} · ${game.statusLabel()}",
            WeekTone.Upcoming,
        )
    }
}

/** A sideways strip with a tile per followed team. Tap a tile for the team's page. */
@Composable
fun YourTeamsStrip(favorites: List<Team>, board: Scoreboard, onOpenTeam: (Team) -> Unit, modifier: Modifier = Modifier) {
    val weeks = remember(favorites, board) { teamWeeks(favorites, board) }
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(weeks, key = { it.team.id }) { week ->
            TeamWeekTile(week, onClick = { onOpenTeam(week.team.copy(record = week.record)) })
        }
    }
}

@Composable
private fun TeamWeekTile(week: TeamWeek, onClick: () -> Unit) {
    val color = week.team.accentColor()
    val colors = SportsHubTheme.colors
    val toneColor = when (week.tone) {
        WeekTone.Live -> colors.live
        WeekTone.Won -> colors.win
        WeekTone.Lost -> colors.loss
        WeekTone.Upcoming -> MaterialTheme.colorScheme.onSurface
        WeekTone.Idle -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Column(
        Modifier
            .width(150.dp)
            .cardSurface(MaterialTheme.shapes.large)
            .clickable(onClick = onClick)
            .background(Brush.verticalGradient(listOf(color.copy(alpha = 0.24f), Color.Transparent)))
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TeamLogo(week.team, size = 36.dp)
            Spacer(Modifier.weight(1f))
            if (week.tone == WeekTone.Live) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(colors.live))
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            week.team.name,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            week.record ?: " ",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            week.status,
            style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
            fontWeight = FontWeight.SemiBold,
            color = toneColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Card inviting the user to follow teams, at the top of Scores until they do or close it. */
@Composable
fun FollowPrompt(onChoose: () -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val accent = MaterialTheme.colorScheme.primary
    Row(
        modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .cardSurface()
            .background(Brush.horizontalGradient(listOf(accent.copy(alpha = 0.18f), Color.Transparent)))
            .padding(start = 16.dp, end = 8.dp, top = 16.dp, bottom = 8.dp),
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Star, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text("Follow your teams", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(2.dp))
            Text(
                "Their games show up first here, every week.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                FilledTonalButton(onClick = onChoose) { Text("Choose teams") }
                Spacer(Modifier.width(4.dp))
                TextButton(onClick = onDismiss) { Text("Not now") }
            }
        }
    }
}
