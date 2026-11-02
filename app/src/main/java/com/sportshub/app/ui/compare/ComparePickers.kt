package com.sportshub.app.ui.compare

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sportshub.app.data.TeamPart
import com.sportshub.app.data.TeamRepository
import com.sportshub.app.data.peekRoster
import com.sportshub.app.model.PlayerRef
import com.sportshub.app.model.RosterGroup
import com.sportshub.app.model.Sport
import com.sportshub.app.model.Team
import com.sportshub.app.model.TeamListing
import com.sportshub.app.ui.components.EmptyState
import com.sportshub.app.ui.components.PillTabs
import com.sportshub.app.ui.components.PlayerHeadshot
import com.sportshub.app.ui.components.TeamLogo
import com.sportshub.app.ui.components.groupedShape
import com.sportshub.app.ui.components.rememberTeamListings
import com.sportshub.app.ui.components.shimmer
import com.sportshub.app.ui.components.teamPickerItems
import com.sportshub.app.ui.team.TeamGroup
import com.sportshub.app.ui.team.groupTeams
import com.sportshub.app.ui.theme.SportsHubTheme

/** The league's teams by division, with the user's own teams first. */
private fun pickerGroups(sport: Sport, listings: List<TeamListing>, favorites: List<Team>): List<TeamGroup> {
    val byId = listings.associateBy { it.team.id }
    val mine = favorites.mapNotNull { byId[it.id] }
    val yours = if (mine.isEmpty()) emptyList() else listOf(TeamGroup("yours", "", "Your teams", mine))
    return yours + groupTeams(sport, listings)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PickerSheet(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.background,
    ) {
        content()
    }
}

@Composable
private fun SheetTitle(title: String, subtitle: String?, onBack: (() -> Unit)? = null) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = if (onBack == null) 20.dp else 4.dp, end = 16.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back to teams") }
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** A sheet of every team, to fill one side of a team comparison. */
@Composable
fun TeamPickSheet(
    sport: Sport,
    teamRepository: TeamRepository,
    favorites: List<Team>,
    selectedId: String?,
    otherId: String?,
    onPick: (Team) -> Unit,
    onDismiss: () -> Unit,
) {
    var attempt by rememberSaveable { mutableIntStateOf(0) }
    val listings = rememberTeamListings(teamRepository, sport, attempt)
    val groups = remember(listings.listings, favorites) { listings.listings?.let { pickerGroups(sport, it, favorites) } }
    PickerSheet(onDismiss) {
        SheetTitle("Pick a team", "Tap a team to compare it.")
        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
            teamPickerItems(
                groups = groups,
                failed = listings.failed,
                selectedIds = setOfNotNull(selectedId),
                disabledIds = setOfNotNull(otherId),
                onPick = onPick,
                onRetry = { attempt++ },
            )
        }
    }
}

/** A team's roster for the player picker: null while loading. */
@Immutable
private data class RosterState(val groups: List<RosterGroup>?, val failed: Boolean = false)

/**
 * Picks a player in two steps: their team, then them from the team's roster. The roster
 * opens on the side of the ball of the player already picked, so like is easy to match with like.
 */
@Composable
fun PlayerPickSheet(
    sport: Sport,
    teamRepository: TeamRepository,
    favorites: List<Team>,
    otherId: String?,
    otherPosition: String?,
    onPick: (PlayerRef) -> Unit,
    onDismiss: () -> Unit,
) {
    var team by remember { mutableStateOf<Team?>(null) }
    var attempt by rememberSaveable { mutableIntStateOf(0) }

    PickerSheet(onDismiss) {
        // Back from a roster goes back to the teams, not out of the sheet.
        BackHandler(enabled = team != null) { team = null }
        val chosen = team
        if (chosen == null) {
            val listings = rememberTeamListings(teamRepository, sport, attempt)
            val groups = remember(listings.listings, favorites) { listings.listings?.let { pickerGroups(sport, it, favorites) } }
            SheetTitle("Pick a player", "Start with their team.")
            LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                teamPickerItems(
                    groups = groups,
                    failed = listings.failed,
                    selectedIds = emptySet(),
                    onPick = { team = it },
                    onRetry = { attempt++ },
                )
            }
        } else {
            RosterStep(sport, teamRepository, chosen, otherId, otherPosition, onBack = { team = null }, onPick = onPick)
        }
    }
}

@Composable
private fun RosterStep(
    sport: Sport,
    teamRepository: TeamRepository,
    team: Team,
    otherId: String?,
    otherPosition: String?,
    onBack: () -> Unit,
    onPick: (PlayerRef) -> Unit,
) {
    var attempt by remember(team.id) { mutableIntStateOf(0) }
    val roster by produceState(RosterState(teamRepository.peekRoster(sport, team.id)), team.id, attempt) {
        if (value.groups != null && attempt == 0) return@produceState
        teamRepository.cached<List<RosterGroup>>(sport, TeamPart.Roster, team.id)?.let { value = RosterState(it) }
        teamRepository.load<List<RosterGroup>>(sport, TeamPart.Roster, team.id).fold(
            onSuccess = { value = RosterState(it) },
            onFailure = { if (value.groups == null) value = RosterState(null, failed = true) },
        )
    }
    val groups = roster.groups
    // Open on the side of the ball of the player already picked.
    val startTab = remember(groups, otherPosition) {
        groups?.indexOfFirst { g -> g.players.any { it.position != null && it.position == otherPosition } }?.takeIf { it >= 0 } ?: 0
    }
    var tab by remember(team.id, startTab) { mutableIntStateOf(startTab) }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f)) { SheetTitle(team.name, "Tap a player to compare them.", onBack = onBack) }
        TeamLogo(team, size = 32.dp, modifier = Modifier.padding(end = 20.dp))
    }
    when {
        groups != null && groups.isNotEmpty() -> {
            if (groups.size > 1) {
                PillTabs(
                    labels = groups.map { it.title },
                    selected = tab.coerceIn(0, groups.lastIndex),
                    onSelect = { tab = it },
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            val players = groups[tab.coerceIn(0, groups.lastIndex)].players
            LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                itemsIndexed(players, key = { _, p -> p.id }) { i, p ->
                    val disabled = p.id == otherId
                    Row(
                        Modifier
                            .padding(horizontal = 16.dp, vertical = 1.dp)
                            .fillMaxWidth()
                            .clip(groupedShape(i, players.size))
                            .background(SportsHubTheme.colors.card)
                            .clickable(enabled = !disabled) {
                                onPick(PlayerRef(p.id, p.name, p.headshotUrl, p.position, p.jersey, team))
                            }
                            .alpha(if (disabled) 0.4f else 1f)
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        PlayerHeadshot(p.headshotUrl, p.name, size = 40.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(p.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            val line = listOfNotNull(p.positionName ?: p.position, p.jersey?.let { "#$it" }).joinToString(" · ")
                            if (line.isNotEmpty()) {
                                Text(line, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        p.position?.let {
                            Text(it, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
        groups != null -> EmptyState(Icons.Outlined.CloudOff, "No roster", "ESPN hasn't listed this team's players.")
        roster.failed -> EmptyState(
            icon = Icons.Outlined.CloudOff,
            title = "Couldn't load the roster",
            message = "Check your connection and try again.",
            action = { FilledTonalButton(onClick = { attempt++ }) { Text("Try again") } },
        )
        else -> Column(
            Modifier
                .padding(16.dp)
                .shimmer(),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            repeat(8) { i ->
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(60.dp)
                        .clip(groupedShape(i, 8))
                        .background(SportsHubTheme.colors.cardHigh),
                )
            }
        }
    }
}
