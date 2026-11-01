package com.sportshub.app.ui.scores

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.SportsFootball
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.sportshub.app.model.Game
import com.sportshub.app.model.GameStatus
import com.sportshub.app.model.Scoreboard
import com.sportshub.app.model.Sport
import com.sportshub.app.ui.components.EmptyState
import com.sportshub.app.ui.components.ScreenHeader
import com.sportshub.app.ui.components.SportSelector
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScoresScreen(
    sport: Sport,
    onSelectSport: (Sport) -> Unit,
    viewModel: ScoresViewModel,
    onOpenGame: (Game) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(sport) { viewModel.setSport(sport) }
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) { viewModel.pollWhileVisible() }
    }

    val board = state.board
    val subtitle = when {
        board?.week != null && sport.hasWeeks -> if (board.seasonType == 3) "Playoffs" else "Week ${board.week}"
        else -> LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, MMM d"))
    }

    PullToRefreshBox(
        isRefreshing = state.isRefreshing,
        onRefresh = viewModel::refresh,
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "header") {
                Column {
                    ScreenHeader("Scores", subtitle)
                    SportSelector(selected = sport, onSelect = onSelectSport)
                    if (sport.hasWeeks && board != null && board.seasonType == 2) {
                        WeekSelector(
                            weeks = sport.regularSeasonWeeks,
                            selected = board.week,
                            onSelect = viewModel::selectWeek,
                        )
                    }
                }
            }

            if (state.error != null && board != null) {
                item(key = "error") {
                    ErrorBanner(state.error!!, onRetry = viewModel::refresh)
                }
            }

            when {
                state.isLoading -> items(4, key = { "skeleton$it" }) { SkeletonCard() }
                board == null -> item(key = "empty") {
                    EmptyState(
                        icon = Icons.Outlined.CloudOff,
                        title = "Couldn't load scores",
                        message = state.error ?: "Something went wrong.",
                        action = { TextButton(onClick = viewModel::refresh) { Text("Try again") } },
                    )
                }
                board.games.isEmpty() -> item(key = "none") {
                    EmptyState(
                        icon = Icons.Outlined.SportsFootball,
                        title = "No games",
                        message = "There are no ${sport.shortName} games scheduled for this slate.",
                    )
                }
                else -> gameSections(board, onOpenGame)
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.gameSections(
    board: Scoreboard,
    onOpenGame: (Game) -> Unit,
) {
    val sections = listOf(
        "Live now" to board.games.filter { it.status is GameStatus.Live },
        "Upcoming" to board.games.filter { it.status == GameStatus.Scheduled },
        "Final" to board.games.filter { it.status is GameStatus.Final },
        "Postponed" to board.games.filter { it.status is GameStatus.Postponed },
    )
    sections.forEach { (title, games) ->
        if (games.isEmpty()) return@forEach
        item(key = "section_$title") {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 20.dp, top = 12.dp),
            )
        }
        items(games, key = { it.id }) { game ->
            GameCard(
                game = game,
                onClick = { onOpenGame(game) },
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .animateItem(),
            )
        }
    }
}

@Composable
private fun WeekSelector(weeks: Int, selected: Int?, onSelect: (Int) -> Unit) {
    val listState = rememberLazyListState()
    LaunchedEffect(selected) {
        if (selected != null) listState.animateScrollToItem((selected - 2).coerceAtLeast(0))
    }
    LazyRow(
        state = listState,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items((1..weeks).toList()) { week ->
            FilterChip(
                selected = week == selected,
                onClick = { onSelect(week) },
                label = { Text("Wk $week") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.secondary,
                    selectedLabelColor = MaterialTheme.colorScheme.background,
                ),
            )
        }
    }
}

@Composable
private fun ErrorBanner(message: String, onRetry: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.error.copy(alpha = 0.12f))
            .padding(start = 14.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "$message Showing saved scores.",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onRetry) { Text("Retry") }
    }
}

@Composable
private fun SkeletonCard() {
    val alpha by rememberInfiniteTransition(label = "skeleton").animateFloat(
        initialValue = 0.4f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
        label = "alpha",
    )
    Column(
        Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .height(96.dp)
            .alpha(alpha)
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {}
}
