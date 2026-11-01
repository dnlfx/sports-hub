package com.sportshub.app.ui.watch

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LiveTv
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sportshub.app.data.SourcesRepository
import com.sportshub.app.model.Sport
import com.sportshub.app.ui.components.EmptyState
import com.sportshub.app.ui.components.ScreenHeader
import com.sportshub.app.ui.components.SportSelector

/**
 * Watch tab. For now it lists the user's saved sources for the selected sport.
 * Step 3 adds "where to watch" info per game, page scanning, and the player.
 */
@Composable
fun WatchScreen(
    sport: Sport,
    onSelectSport: (Sport) -> Unit,
    sourcesRepository: SourcesRepository,
    onAddSource: () -> Unit,
) {
    val allSources by sourcesRepository.sources.collectAsStateWithLifecycle(initialValue = emptyList())
    val sources = allSources.filter { it.sportId == sport.id }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Watch", "Your ${sport.shortName} sources")
        SportSelector(selected = sport, onSelect = onSelectSport)
        if (sources.isEmpty()) {
            EmptyState(
                icon = Icons.Outlined.LiveTv,
                title = "No sources yet",
                message = "Add a page URL on the Sources tab and its videos will show up here.",
                action = { Button(onClick = onAddSource) { Text("Add a source") } },
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(sources, key = { it.id }) { source ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text(source.label, style = MaterialTheme.typography.titleMedium)
                            Text(
                                source.url,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}
