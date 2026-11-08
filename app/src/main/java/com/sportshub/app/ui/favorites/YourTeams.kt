package com.sportshub.app.ui.favorites

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sportshub.app.model.Scoreboard
import com.sportshub.app.model.Team
import com.sportshub.app.ui.components.TeamLogo
import com.sportshub.app.ui.components.accentColor
import com.sportshub.app.ui.components.cardSurface
import com.sportshub.app.ui.theme.SportsHubTheme

/** A followed team with no game on the slate being shown, e.g. on a bye. */
@Immutable
data class IdleTeam(val team: Team, val status: String)

/**
 * The followed teams that have no game on [board], in the order the user followed them.
 * Teams that do play show up as their full game card instead, so each team appears once.
 */
fun idleTeams(favorites: List<Team>, board: Scoreboard): List<IdleTeam> {
    val playing = board.games.flatMapTo(HashSet()) { listOf(it.home.team.id, it.away.team.id) }
    val status = when {
        board.seasonType == 3 -> "Not playing"
        board.sport.hasWeeks -> "Bye week"
        else -> "No game"
    }
    return favorites.filter { it.id !in playing }.map { IdleTeam(it, status) }
}

/**
 * One card listing the followed teams that aren't playing, a row each.
 * Tap a row for the team's page.
 */
@Composable
fun IdleTeamsCard(teams: List<IdleTeam>, onOpenTeam: (Team) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().cardSurface()) {
        teams.forEachIndexed { index, idle ->
            if (index > 0) HorizontalDivider(Modifier.padding(start = 64.dp), color = SportsHubTheme.colors.stroke)
            IdleTeamRow(idle, onClick = { onOpenTeam(idle.team) })
        }
    }
}

@Composable
private fun IdleTeamRow(idle: IdleTeam, onClick: () -> Unit) {
    val color = idle.team.accentColor()
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = "Open team", role = Role.Button, onClick = onClick)
            .drawBehind {
                drawRect(Brush.horizontalGradient(0f to color.copy(alpha = 0.14f), 0.4f to Color.Transparent, endX = size.width))
            }
            .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TeamLogo(idle.team, size = 34.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                idle.team.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            idle.team.record?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text(
            idle.status,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Icon(
            Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 2.dp).size(20.dp),
        )
    }
}

/** Card inviting the user to follow teams, at the top of Scores until they do or close it. */
@Composable
fun FollowPrompt(onChoose: () -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier, byWeek: Boolean = true) {
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
                if (byWeek) "Their games show up first here, every week." else "Their games show up first here, every game day.",
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
