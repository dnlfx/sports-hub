package com.sportshub.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sportshub.app.data.TeamRepository
import com.sportshub.app.model.Sport
import com.sportshub.app.model.Team
import com.sportshub.app.model.TeamListing
import com.sportshub.app.ui.team.TeamGroup
import com.sportshub.app.ui.theme.SportsHubTheme

/** The league's teams for a picker: null while loading, with [failed] set if they couldn't be loaded. */
@Immutable
data class TeamListingsState(val listings: List<TeamListing>?, val failed: Boolean = false)

/**
 * The league's teams, straight from memory when the Teams tab has loaded them, else from
 * disk and then the network. Bump [attempt] to try again after a failure.
 */
@Composable
fun rememberTeamListings(repository: TeamRepository, sport: Sport, attempt: Int = 0): TeamListingsState {
    val state by produceState(TeamListingsState(repository.peekTeams(sport)), repository, sport, attempt) {
        if (value.listings != null && attempt == 0) return@produceState
        repository.cachedTeams(sport)?.let { value = TeamListingsState(it) }
        repository.teams(sport).fold(
            onSuccess = { value = TeamListingsState(it) },
            onFailure = { if (value.listings == null) value = TeamListingsState(null, failed = true) },
        )
    }
    return state
}

/**
 * Teams in rows of four under their division headers, for picking. Selected teams get a
 * ring in their color and a star; [disabledIds] are dimmed and can't be picked.
 */
fun LazyListScope.teamPickerItems(
    groups: List<TeamGroup>?,
    failed: Boolean,
    selectedIds: Set<String>,
    onPick: (Team) -> Unit,
    onRetry: () -> Unit,
    disabledIds: Set<String> = emptySet(),
    records: Map<String, String> = emptyMap(),
) {
    when {
        groups != null -> groups.forEach { group ->
            item(key = "pick_h_${group.key}", contentType = "header") {
                SectionHeader(group.title, padding = PaddingValues(start = 20.dp, end = 12.dp, top = 14.dp, bottom = 6.dp))
            }
            group.teams.chunked(4).forEachIndexed { row, teams ->
                item(key = "pick_${group.key}_$row", contentType = "row") {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        teams.forEach { listing ->
                            val id = listing.team.id
                            PickTile(
                                team = listing.team,
                                subtitle = records[id] ?: listing.team.abbreviation,
                                selected = id in selectedIds,
                                enabled = id !in disabledIds,
                                modifier = Modifier.weight(1f),
                                onClick = { onPick(listing.team.copy(record = records[id] ?: listing.team.record)) },
                            )
                        }
                        repeat(4 - teams.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
        failed -> item(key = "pick_failed") {
            EmptyState(
                icon = Icons.Outlined.CloudOff,
                title = "Couldn't load teams",
                message = "Check your connection and try again.",
                action = { FilledTonalButton(onClick = onRetry) { Text("Try again") } },
            )
        }
        else -> items(6, key = { "pick_skeleton$it" }) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .shimmer(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                repeat(4) {
                    Box(
                        Modifier
                            .weight(1f)
                            .aspectRatio(0.95f)
                            .clip(MaterialTheme.shapes.medium)
                            .background(SportsHubTheme.colors.cardHigh),
                    )
                }
            }
        }
    }
}

@Composable
private fun PickTile(
    team: Team,
    subtitle: String,
    selected: Boolean,
    enabled: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val color = team.accentColor()
    val ring by animateColorAsState(if (selected) color else Color.Transparent, tween(180), label = "ring")
    Box(modifier.alpha(if (enabled) 1f else 0.35f)) {
        Column(
            Modifier
                .fillMaxWidth()
                .cardSurface(MaterialTheme.shapes.medium)
                .border(2.dp, ring, MaterialTheme.shapes.medium)
                .clickable(enabled = enabled, onClick = onClick)
                .background(Brush.verticalGradient(listOf(color.copy(alpha = if (selected) 0.32f else 0.16f), Color.Transparent)))
                .padding(vertical = 12.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            TeamLogo(team, size = 40.dp)
            Spacer(Modifier.height(8.dp))
            Text(team.name, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
        if (selected) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(color),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Star, contentDescription = "Selected", tint = Color.White, modifier = Modifier.size(12.dp))
            }
        }
    }
}

/** Small star shown on a followed team's tile. */
@Composable
fun FavoriteStar(modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(18.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.tertiary),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Rounded.Star, contentDescription = "Following", tint = Color.White, modifier = Modifier.size(12.dp))
    }
}
