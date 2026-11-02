package com.sportshub.app.ui.team

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sportshub.app.model.Sport
import com.sportshub.app.model.Team
import com.sportshub.app.model.TeamListing
import com.sportshub.app.ui.components.EmptyState
import com.sportshub.app.ui.components.LargeHeaderBar
import com.sportshub.app.ui.components.SectionHeader
import com.sportshub.app.ui.components.SportMenuButton
import com.sportshub.app.ui.components.TeamLogo
import com.sportshub.app.ui.components.accentColor
import com.sportshub.app.ui.components.bottomListPadding
import com.sportshub.app.ui.components.cardSurface
import com.sportshub.app.ui.components.shimmer
import com.sportshub.app.ui.theme.SportsHubTheme

/** Every team in the league, grouped by division. Tap one for its page. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TeamsScreen(
    sport: Sport,
    onSelectSport: (Sport) -> Unit,
    viewModel: TeamsViewModel,
    onOpenTeam: (Team) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(sport) { viewModel.setSport(sport) }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeHeaderBar(
                title = "Teams",
                subtitle = "Schedules, stats, rosters and depth charts",
                scrollBehavior = scrollBehavior,
                actions = { SportMenuButton(selected = sport, onSelect = onSelectSport) },
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = PaddingValues(bottom = bottomListPadding()),
        ) {
            val groups = state.groups
            when {
                groups != null -> groups.forEach { group ->
                    item(key = "header_${group.key}", contentType = "header") { SectionHeader(group.title) }
                    group.teams.chunked(4).forEachIndexed { row, teams ->
                        item(key = "row_${group.key}_$row", contentType = "row") {
                            TeamRow(teams, state.records, onOpenTeam)
                        }
                    }
                }
                state.error -> item(key = "error") {
                    EmptyState(
                        icon = Icons.Outlined.CloudOff,
                        title = "Couldn't load teams",
                        message = "Check your connection and try again.",
                        action = { FilledTonalButton(onClick = viewModel::retry) { Text("Try again") } },
                    )
                }
                else -> repeat(4) { i ->
                    item(key = "skeleton_header$i", contentType = "header") { SectionHeader(" ") }
                    item(key = "skeleton$i", contentType = "row") { TeamRowSkeleton() }
                }
            }
        }
    }
}

@Composable
private fun TeamRow(teams: List<TeamListing>, records: Map<String, String>, onOpenTeam: (Team) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        teams.forEach { listing ->
            TeamTile(listing, records[listing.team.id], Modifier.weight(1f)) { onOpenTeam(listing.team.copy(record = records[listing.team.id])) }
        }
        // Keep tiles the same width in a short last row.
        repeat(4 - teams.size) { Spacer(Modifier.weight(1f)) }
    }
}

/** A team as a tile: its logo on a wash of its color, with its name and record. */
@Composable
private fun TeamTile(listing: TeamListing, record: String?, modifier: Modifier, onClick: () -> Unit) {
    val color = listing.team.accentColor()
    Column(
        modifier
            .cardSurface(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .background(Brush.verticalGradient(listOf(color.copy(alpha = 0.22f), Color.Transparent)))
            .padding(vertical = 12.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        TeamLogo(listing.team, size = 44.dp)
        Spacer(Modifier.height(8.dp))
        Text(
            listing.team.name,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            record ?: listing.team.abbreviation,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

@Composable
private fun TeamRowSkeleton() {
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
