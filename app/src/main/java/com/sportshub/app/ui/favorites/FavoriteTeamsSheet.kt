package com.sportshub.app.ui.favorites

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sportshub.app.data.TeamRepository
import com.sportshub.app.model.Sport
import com.sportshub.app.model.Team
import com.sportshub.app.ui.components.rememberTeamListings
import com.sportshub.app.ui.components.teamPickerItems
import com.sportshub.app.ui.team.groupTeams

/** A sheet of every team, to pick the ones the user follows. Each tap saves straight away. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoriteTeamsSheet(
    sport: Sport,
    teamRepository: TeamRepository,
    favorites: List<Team>,
    records: Map<String, String>,
    onToggle: (Team) -> Unit,
    onDismiss: () -> Unit,
) {
    var attempt by rememberSaveable { mutableIntStateOf(0) }
    val listings = rememberTeamListings(teamRepository, sport, attempt)
    val groups = remember(listings.listings, sport) { listings.listings?.let { groupTeams(sport, it) } }
    val selected = remember(favorites) { favorites.mapTo(HashSet()) { it.id } }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.background,
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 16.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Your teams", style = MaterialTheme.typography.headlineSmall)
                Text(
                    if (favorites.isEmpty()) "Pick the teams you follow. Their games show up first on Scores." else "${favorites.size} followed. Tap a team to add or remove it.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            FilledTonalButton(onClick = onDismiss) { Text("Done") }
        }
        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
            teamPickerItems(
                groups = groups,
                failed = listings.failed,
                selectedIds = selected,
                onPick = onToggle,
                onRetry = { attempt++ },
                records = records,
            )
        }
    }
}
