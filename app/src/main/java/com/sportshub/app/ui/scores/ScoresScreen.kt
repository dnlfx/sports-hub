package com.sportshub.app.ui.scores

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SportsFootball
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.sportshub.app.data.ScoresRepository
import com.sportshub.app.model.Game
import com.sportshub.app.model.Sport
import com.sportshub.app.ui.components.EmptyState
import com.sportshub.app.ui.components.ScreenHeader
import com.sportshub.app.ui.components.SportSelector
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** Live scores. Step 2 replaces the placeholder data and adds score cards and game detail. */
@Composable
fun ScoresScreen(
    sport: Sport,
    onSelectSport: (Sport) -> Unit,
    scoresRepository: ScoresRepository,
) {
    val today = remember { LocalDate.now() }
    var games by remember { mutableStateOf<List<Game>>(emptyList()) }

    LaunchedEffect(sport, today) {
        games = scoresRepository.gamesOn(sport, today).getOrDefault(emptyList())
    }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Scores", today.format(DateTimeFormatter.ofPattern("EEEE, MMM d")))
        SportSelector(selected = sport, onSelect = onSelectSport)
        if (games.isEmpty()) {
            EmptyState(
                icon = Icons.Outlined.SportsFootball,
                title = "Live scores are on the way",
                message = "${sport.shortName} scores and stats will appear here once the live data feed is connected.",
            )
        } else {
            LazyColumn {
                items(games, key = { it.id }) { game ->
                    Text("${game.away.abbreviation} ${game.awayScore ?: "-"}  @  ${game.home.abbreviation} ${game.homeScore ?: "-"}",
                        style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}
