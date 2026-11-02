package com.sportshub.app.ui.scores

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.SportsFootball
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.sportshub.app.model.Game
import com.sportshub.app.model.Sport
import com.sportshub.app.ui.components.EmptyState
import com.sportshub.app.ui.components.LargeHeaderBar
import com.sportshub.app.ui.components.SectionHeader
import com.sportshub.app.ui.components.SportMenuButton
import com.sportshub.app.ui.components.Pill
import com.sportshub.app.ui.components.bottomListPadding
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val headerDate = DateTimeFormatter.ofPattern("EEEE, MMM d")
private val clockTime = DateTimeFormatter.ofPattern("h:mm a")

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
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
        else -> LocalDate.now().format(headerDate)
    }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    Scaffold(
        topBar = {
            LargeHeaderBar(
                title = "Scores",
                subtitle = subtitle,
                scrollBehavior = scrollBehavior,
                actions = { SportMenuButton(selected = sport, onSelect = onSelectSport) },
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = viewModel::refresh,
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            LazyColumn(
                // The app bar's scroll connection sits inside pull-to-refresh, so pulling down
                // at the top first expands the bar, then refreshes.
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(scrollBehavior.nestedScrollConnection),
                contentPadding = PaddingValues(bottom = bottomListPadding()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (sport.hasWeeks && board != null && board.seasonType == 2) {
                    stickyHeader(key = "weeks", contentType = "weeks") {
                        WeekSelector(
                            weeks = sport.regularSeasonWeeks,
                            // The picked week lights up straight away, not once it has loaded.
                            selected = state.selectedWeek ?: board.week,
                            current = state.currentWeek,
                            onSelect = viewModel::selectWeek,
                        )
                    }
                }

                val error = state.error
                if (error != null && board != null) {
                    item(key = "error", contentType = "error") {
                        ErrorBanner(error, savedAtMs = board.fetchedAtEpochMs, onRetry = viewModel::refresh)
                    }
                }

                when {
                    state.isLoading -> items(4, key = { "skeleton$it" }, contentType = { "skeleton" }) {
                        GameCardSkeleton(Modifier.padding(horizontal = 16.dp))
                    }
                    board == null -> item(key = "empty") {
                        EmptyState(
                            icon = Icons.Outlined.CloudOff,
                            title = "Couldn't load scores",
                            message = (error ?: "Something went wrong.") + " Check your connection and try again.",
                            action = { FilledTonalButton(onClick = viewModel::refresh) { Text("Try again") } },
                        )
                    }
                    board.games.isEmpty() -> item(key = "none") {
                        EmptyState(
                            icon = Icons.Outlined.SportsFootball,
                            title = "No games",
                            message = "There are no ${sport.shortName} games on this slate.",
                        )
                    }
                    else -> gameSections(state.sections, onOpenGame)
                }
            }
        }
    }
}

private fun LazyListScope.gameSections(sections: List<GameSection>, onOpenGame: (Game) -> Unit) {
    sections.forEach { section ->
        item(key = "section_${section.key}", contentType = "section") {
            SectionHeader(
                section.title,
                count = section.games.size,
                accent = section.key == "live",
                modifier = Modifier.animateItem(),
            )
        }
        items(section.games, key = { it.id }, contentType = { "game" }) { game ->
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
private fun WeekSelector(weeks: Int, selected: Int?, current: Int?, onSelect: (Int) -> Unit) {
    val listState = rememberLazyListState()
    LaunchedEffect(selected) {
        if (selected != null) listState.animateScrollToItem((selected - 3).coerceAtLeast(0))
    }
    LazyRow(
        state = listState,
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items((1..weeks).toList(), key = { it }) { week ->
            Pill(
                label = "Week $week",
                selected = week == selected,
                onClick = { onSelect(week) },
                // A dot marks this week while browsing others.
                leading = if (week == current && week != selected) {
                    {
                        Box(
                            Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.tertiary),
                        )
                    }
                } else {
                    null
                },
            )
        }
    }
}

@Composable
private fun ErrorBanner(message: String, savedAtMs: Long, onRetry: () -> Unit) {
    val savedAt = Instant.ofEpochMilli(savedAtMs).atZone(ZoneId.systemDefault())
    val savedLabel = if (savedAt.toLocalDate() == LocalDate.now()) {
        savedAt.format(clockTime)
    } else {
        savedAt.format(DateTimeFormatter.ofPattern("MMM d, h:mm a"))
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.errorContainer)
            .padding(start = 14.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Outlined.CloudOff,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            "$message Showing scores from $savedLabel.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onRetry) { Text("Retry", color = MaterialTheme.colorScheme.onErrorContainer) }
    }
}
