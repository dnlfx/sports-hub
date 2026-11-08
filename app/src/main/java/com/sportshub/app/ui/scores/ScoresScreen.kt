package com.sportshub.app.ui.scores

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.sportshub.app.data.TeamRepository
import com.sportshub.app.model.Game
import com.sportshub.app.model.Scoreboard
import com.sportshub.app.model.Sport
import com.sportshub.app.model.Team
import com.sportshub.app.ui.components.EmptyState
import com.sportshub.app.ui.components.LargeHeaderBar
import com.sportshub.app.ui.components.Pill
import com.sportshub.app.ui.components.PrefetchImages
import com.sportshub.app.ui.components.SectionHeader
import com.sportshub.app.ui.components.SportMenuButton
import com.sportshub.app.ui.components.bottomListPadding
import com.sportshub.app.ui.components.icon
import com.sportshub.app.ui.favorites.FavoriteTeamsSheet
import com.sportshub.app.ui.favorites.FollowPrompt
import com.sportshub.app.ui.favorites.IdleTeamsCard
import com.sportshub.app.ui.favorites.idleTeams
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Widest the list of games gets on big screens. */
private val MaxContentWidth = 720.dp

private val headerDate = DateTimeFormatter.ofPattern("EEEE, MMM d")
private val clockTime = DateTimeFormatter.ofPattern("h:mm a")
private val pillDate = DateTimeFormatter.ofPattern("EEE, MMM d")

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ScoresScreen(
    sport: Sport,
    onSelectSport: (Sport) -> Unit,
    viewModel: ScoresViewModel,
    onOpenGame: (Game) -> Unit,
    onOpenTeam: (Team) -> Unit,
    favoriteTeams: List<Team>,
    promptDismissed: Boolean,
    onDismissPrompt: () -> Unit,
    onToggleFavorite: (Team) -> Unit,
    teamRepository: TeamRepository,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var picking by rememberSaveable { mutableStateOf(false) }
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(sport) { viewModel.setSport(sport) }
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) { viewModel.pollWhileVisible() }
    }

    val board = state.board
    // Followed teams' logos start downloading on launch, alongside the scores, then every
    // logo on the slate, so cards further down are ready before they scroll into view.
    PrefetchImages(remember(favoriteTeams) { favoriteTeams.map { it.logoUrl } })
    PrefetchImages(remember(board?.games) { board?.games.orEmpty().flatMap { listOf(it.home.team.logoUrl, it.away.team.logoUrl) } })
    val subtitle = when {
        board?.week != null && sport.hasWeeks -> if (board.seasonType == 3) sport.postseasonName else "Week ${board.week}"
        // Sports played by day: the day being shown, which follows ESPN's game day.
        else -> (state.selectedDate ?: board?.date ?: LocalDate.now()).format(headerDate)
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
            BoxWithConstraints(Modifier.fillMaxSize()) {
                // On tablets, foldables and in landscape the cards keep a comfortable reading
                // width in the middle instead of stretching edge to edge.
                val side = ((maxWidth - MaxContentWidth) / 2).coerceAtLeast(0.dp)
                LazyColumn(
                    // The app bar's scroll connection sits inside pull-to-refresh, so pulling down
                    // at the top first expands the bar, then refreshes.
                    modifier = Modifier
                        .fillMaxSize()
                        .nestedScroll(scrollBehavior.nestedScrollConnection),
                    contentPadding = PaddingValues(start = side, end = side, bottom = bottomListPadding()),
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

                    if (!sport.hasWeeks && board != null) {
                        stickyHeader(key = "days", contentType = "days") {
                            DaySelector(
                                days = remember(state.calendar, state.currentDate, state.selectedDate) {
                                    stripDays(state.calendar, state.currentDate ?: board.date, state.selectedDate)
                                },
                                // The picked day lights up straight away, not once it has loaded.
                                selected = state.selectedDate ?: state.currentDate ?: board.date,
                                current = state.currentDate,
                                onSelect = viewModel::selectDate,
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
                                icon = sport.icon,
                                title = "No games",
                                message = if (sport.hasWeeks) {
                                    "There are no ${sport.shortName} games on this slate."
                                } else {
                                    "There are no ${sport.shortName} games on this day. Pick another day above."
                                },
                            )
                        }
                        else -> {
                            if (favoriteTeams.isEmpty() && !promptDismissed) {
                                item(key = "follow_prompt", contentType = "prompt") {
                                    FollowPrompt(
                                        onChoose = { picking = true },
                                        onDismiss = onDismissPrompt,
                                        byWeek = sport.hasWeeks,
                                        modifier = Modifier.animateItem(),
                                    )
                                }
                            }
                            gameSections(state.sections, board, favoriteTeams, onOpenGame, onOpenTeam, onEditFavorites = { picking = true })
                        }
                    }
                }
            }
        }
    }

    if (picking) {
        val records = remember(board) { recordsOf(board) }
        FavoriteTeamsSheet(
            sport = sport,
            teamRepository = teamRepository,
            favorites = favoriteTeams,
            records = records,
            onToggle = onToggleFavorite,
            onDismiss = { picking = false },
        )
    }
}

/** Team id to record, e.g. "6-2", from the games on a scoreboard. */
private fun recordsOf(board: Scoreboard?): Map<String, String> = board?.games.orEmpty()
    .flatMap { listOf(it.home.team, it.away.team) }
    .mapNotNull { t -> t.record?.let { t.id to it } }
    .toMap()

private fun LazyListScope.gameSections(
    sections: List<GameSection>,
    board: Scoreboard,
    favorites: List<Team>,
    onOpenGame: (Game) -> Unit,
    onOpenTeam: (Team) -> Unit,
    onEditFavorites: () -> Unit,
) {
    // The user's teams lead, each one once: a full card for every game they play, then
    // one compact card for any team that isn't playing (a bye, or no game that day).
    if (favorites.isNotEmpty()) {
        item(key = "section_$YOUR_TEAMS_SECTION", contentType = "section") {
            SectionHeader(
                "Your teams",
                modifier = Modifier.animateItem(),
                trailing = { TextButton(onClick = onEditFavorites) { Text("Edit") } },
            )
        }
        val mine = sections.firstOrNull { it.key == YOUR_TEAMS_SECTION }?.games.orEmpty()
        items(mine, key = { it.id }, contentType = { "game" }) { game ->
            GameCard(
                game = game,
                onClick = { onOpenGame(game) },
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .animateItem(),
            )
        }
        val idle = idleTeams(favorites, board)
        if (idle.isNotEmpty()) {
            item(key = "your_teams_idle", contentType = "idle_teams") {
                IdleTeamsCard(
                    idle,
                    onOpenTeam = onOpenTeam,
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .animateItem(),
                )
            }
        }
    }
    sections.filter { it.key != YOUR_TEAMS_SECTION }.forEach { section ->
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
private fun DaySelector(days: List<LocalDate>, selected: LocalDate?, current: LocalDate?, onSelect: (LocalDate) -> Unit) {
    val listState = rememberLazyListState()
    LaunchedEffect(selected, days.size) {
        val index = days.indexOf(selected)
        if (index >= 0) listState.animateScrollToItem((index - 2).coerceAtLeast(0))
    }
    val today = LocalDate.now()
    LazyRow(
        state = listState,
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(days, key = { it.toEpochDay() }) { day ->
            Pill(
                label = when (day) {
                    today -> "Today"
                    today.minusDays(1) -> "Yesterday"
                    today.plusDays(1) -> "Tomorrow"
                    else -> day.format(pillDate)
                },
                selected = day == selected,
                onClick = { onSelect(day) },
                // A dot marks today's slate while browsing other days.
                leading = if (day == current && day != selected) {
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
