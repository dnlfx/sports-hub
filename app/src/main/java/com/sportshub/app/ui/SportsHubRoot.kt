package com.sportshub.app.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Scoreboard
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.LiveTv
import androidx.compose.material.icons.outlined.Scoreboard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.sportshub.app.AppContainer
import com.sportshub.app.model.Game
import com.sportshub.app.model.PlayerRef
import com.sportshub.app.model.Sport
import com.sportshub.app.model.Team
import com.sportshub.app.ui.athlete.PlayerProfileScreen
import com.sportshub.app.ui.athlete.PlayerProfileViewModel
import com.sportshub.app.ui.compare.CompareKind
import com.sportshub.app.ui.compare.CompareScreen
import com.sportshub.app.ui.compare.CompareViewModel
import com.sportshub.app.ui.components.LightStatusBarIcons
import com.sportshub.app.ui.scores.GameDetailScreen
import com.sportshub.app.ui.scores.GameDetailViewModel
import com.sportshub.app.ui.scores.ScoresScreen
import com.sportshub.app.ui.scores.ScoresViewModel
import com.sportshub.app.ui.sources.SourcesScreen
import com.sportshub.app.ui.team.TeamScreen
import com.sportshub.app.ui.team.TeamViewModel
import com.sportshub.app.ui.team.TeamsScreen
import com.sportshub.app.ui.team.TeamsViewModel
import com.sportshub.app.ui.theme.SportsHubTheme
import com.sportshub.app.ui.watch.WatchScreen
import com.sportshub.app.ui.watch.WatchViewModel

private enum class Tab(val route: String, val label: String, val icon: ImageVector, val selectedIcon: ImageVector) {
    Scores("scores", "Scores", Icons.Outlined.Scoreboard, Icons.Filled.Scoreboard),
    Teams("teams", "Teams", Icons.Outlined.Groups, Icons.Filled.Groups),
    Watch("watch", "Watch", Icons.Outlined.LiveTv, Icons.Filled.LiveTv),
    Sources("sources", "Sources", Icons.Outlined.Link, Icons.Filled.Link),
}

private const val GAME_ROUTE = "game/{sportId}/{gameId}"
private const val TEAM_ROUTE = "team/{sportId}/{teamId}"
private const val PLAYER_ROUTE = "player/{sportId}/{athleteId}"
private const val COMPARE_ROUTE = "compare/{sportId}/{kind}/{firstId}"
private fun isDetailRoute(route: String?) = route != null &&
    listOf("game/", "team/", "player/", "compare/").any { route.startsWith(it) }

/** Height of the bottom navigation bar, not counting the system navigation bar below it. */
private val NavBarHeight = 68.dp

@Composable
fun SportsHubRoot(container: AppContainer) {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val selectedSport by container.selectedSport.collectAsStateWithLifecycle()
    val favorites by container.favorites.teams.collectAsStateWithLifecycle()
    val promptDismissed by container.favorites.promptDismissed.collectAsStateWithLifecycle()
    val route = backStack?.destination?.route

    // The tab the user is in. Game and team pages belong to the tab they were opened from.
    var currentTab by rememberSaveable { mutableStateOf(Tab.Scores) }
    LaunchedEffect(route) {
        Tab.entries.firstOrNull { it.route == route }?.let { currentTab = it }
    }

    fun openGame(game: Game) {
        container.lastOpenedGame = game
        navController.navigate("game/${game.sport.id}/${game.id}")
    }

    fun openTeam(team: Team, sport: Sport) {
        container.lastOpenedTeam = team
        navController.navigate("team/${sport.id}/${team.id}")
    }

    fun openPlayer(player: PlayerRef, sport: Sport) {
        container.lastOpenedPlayer = player
        navController.navigate("player/${sport.id}/${player.id}")
    }

    /** Opens the compare page with [team] or [player] already in the first slot, or empty. */
    fun openCompare(sport: Sport, kind: CompareKind, team: Team? = null, player: PlayerRef? = null) {
        container.lastCompareTeam = team
        container.lastComparePlayer = player
        navController.navigate("compare/${sport.id}/${kind.id}/${team?.id ?: player?.id ?: "_"}")
    }

    fun selectTab(tab: Tab) {
        if (tab == currentTab) {
            // Tapping the current tab again goes back to its top level.
            if (isDetailRoute(route)) navController.popBackStack(tab.route, inclusive = false)
            return
        }
        currentTab = tab
        navController.navigateToTab(tab)
    }

    // Tab screens use the theme's usual status bar icons; detail pages set their own.
    if (!isDetailRoute(route)) LightStatusBarIcons(enabled = false)

    val systemNavBar = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    // Pages sit above the navigation bar instead of scrolling under it. The bar covers the
    // system navigation area too, so the gesture pill or the back/home buttons sit on it.
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                // The bar already makes room for the system navigation bar (and the keyboard
                // covers both), so pages don't pad for it again.
                .consumeWindowInsets(PaddingValues(bottom = NavBarHeight + systemNavBar)),
        ) {
            NavHost(
                navController = navController,
                startDestination = Tab.Scores.route,
                modifier = Modifier.fillMaxSize(),
                // Tabs fade through; game and team pages slide in from the right.
                enterTransition = {
                    if (isDetailRoute(targetState.destination.route)) {
                        slideInHorizontally(tween(300)) { it / 4 } + fadeIn(tween(300))
                    } else {
                        fadeIn(tween(220, delayMillis = 60)) + scaleIn(tween(220, delayMillis = 60), initialScale = 0.98f)
                    }
                },
                exitTransition = { fadeOut(tween(120)) },
                popEnterTransition = { fadeIn(tween(220)) },
                popExitTransition = {
                    if (isDetailRoute(initialState.destination.route)) {
                        slideOutHorizontally(tween(240)) { it / 4 } + fadeOut(tween(240))
                    } else {
                        fadeOut(tween(120))
                    }
                },
            ) {
                composable(Tab.Scores.route) {
                    val scoresViewModel: ScoresViewModel = viewModel(
                        factory = viewModelFactory { initializer { ScoresViewModel(container.scoresRepository, container.favorites) } },
                    )
                    ScoresScreen(
                        sport = selectedSport,
                        onSelectSport = container::selectSport,
                        viewModel = scoresViewModel,
                        onOpenGame = { openGame(it) },
                        onOpenTeam = { openTeam(it, selectedSport) },
                        favoriteTeams = favorites[selectedSport.id].orEmpty(),
                        promptDismissed = promptDismissed,
                        onDismissPrompt = container.favorites::dismissPrompt,
                        onToggleFavorite = { container.favorites.toggle(selectedSport, it) },
                        teamRepository = container.teamRepository,
                    )
                }
                composable(Tab.Teams.route) {
                    val teamsViewModel: TeamsViewModel = viewModel(
                        factory = viewModelFactory {
                            initializer { TeamsViewModel(container.teamRepository, container.scoresRepository) }
                        },
                    )
                    TeamsScreen(
                        sport = selectedSport,
                        onSelectSport = container::selectSport,
                        viewModel = teamsViewModel,
                        onOpenTeam = { openTeam(it, selectedSport) },
                        favoriteIds = favorites[selectedSport.id].orEmpty().map { it.id },
                        onCompare = { openCompare(selectedSport, CompareKind.Teams) },
                    )
                }
                composable(GAME_ROUTE) { entry ->
                    val sport = Sport.fromId(entry.arguments?.getString("sportId"))
                    val gameId = entry.arguments?.getString("gameId").orEmpty()
                    val detailViewModel: GameDetailViewModel = viewModel(
                        factory = viewModelFactory {
                            initializer { GameDetailViewModel(container.scoresRepository, sport, gameId) }
                        },
                    )
                    GameDetailScreen(
                        viewModel = detailViewModel,
                        initialGame = remember(gameId) { container.lastOpenedGame?.takeIf { it.id == gameId } },
                        onBack = { navController.popBackStack() },
                        onOpenTeam = { openTeam(it, sport) },
                        onOpenPlayer = { openPlayer(it, sport) },
                    )
                }
                composable(TEAM_ROUTE) { entry ->
                    val sport = Sport.fromId(entry.arguments?.getString("sportId"))
                    val teamId = entry.arguments?.getString("teamId").orEmpty()
                    val initialTeam = remember(teamId) { container.lastOpenedTeam?.takeIf { it.id == teamId } }
                    val teamViewModel: TeamViewModel = viewModel(
                        factory = viewModelFactory {
                            initializer { TeamViewModel(container.teamRepository, sport, teamId, initialTeam) }
                        },
                    )
                    TeamScreen(
                        viewModel = teamViewModel,
                        onBack = { navController.popBackStack() },
                        onOpenGame = { openGame(it) },
                        onOpenPlayer = { openPlayer(it, sport) },
                        isFavorite = favorites[sport.id].orEmpty().any { it.id == teamId },
                        onToggleFavorite = { container.favorites.toggle(sport, it) },
                        onCompare = { openCompare(sport, CompareKind.Teams, team = it) },
                    )
                }
                composable(PLAYER_ROUTE) { entry ->
                    val sport = Sport.fromId(entry.arguments?.getString("sportId"))
                    val athleteId = entry.arguments?.getString("athleteId").orEmpty()
                    val ref = remember(athleteId) { container.lastOpenedPlayer?.takeIf { it.id == athleteId } }
                    val playerViewModel: PlayerProfileViewModel = viewModel(
                        factory = viewModelFactory {
                            initializer { PlayerProfileViewModel(container.teamRepository, sport, athleteId, ref) }
                        },
                    )
                    PlayerProfileScreen(
                        viewModel = playerViewModel,
                        onBack = { navController.popBackStack() },
                        onOpenTeam = { openTeam(it, sport) },
                        onCompare = { openCompare(sport, CompareKind.Players, player = it) },
                    )
                }
                composable(COMPARE_ROUTE) { entry ->
                    val sport = Sport.fromId(entry.arguments?.getString("sportId"))
                    val kind = CompareKind.fromId(entry.arguments?.getString("kind"))
                    val firstId = entry.arguments?.getString("firstId")
                    val firstTeam = remember(firstId) {
                        container.lastCompareTeam?.takeIf { it.id == firstId }
                            ?: firstId?.takeIf { kind == CompareKind.Teams }?.let { container.teamRepository.teamInfo(sport, it) }
                    }
                    val firstPlayer = remember(firstId) { container.lastComparePlayer?.takeIf { it.id == firstId } }
                    val compareViewModel: CompareViewModel = viewModel(
                        factory = viewModelFactory {
                            initializer { CompareViewModel(container.teamRepository, sport, kind, firstTeam, firstPlayer) }
                        },
                    )
                    CompareScreen(
                        viewModel = compareViewModel,
                        sport = sport,
                        teamRepository = container.teamRepository,
                        favoriteTeams = favorites[sport.id].orEmpty(),
                        onBack = { navController.popBackStack() },
                    )
                }
                composable(Tab.Watch.route) {
                    val watchViewModel: WatchViewModel = viewModel(
                        factory = viewModelFactory {
                            initializer {
                                WatchViewModel(container.sourcesRepository, container.scoresRepository, container.pageScanner)
                            }
                        },
                    )
                    WatchScreen(
                        sport = selectedSport,
                        onSelectSport = container::selectSport,
                        viewModel = watchViewModel,
                        onAddSource = { selectTab(Tab.Sources) },
                        onOpenGame = { openGame(it) },
                    )
                }
                composable(Tab.Sources.route) {
                    SourcesScreen(
                        sourcesRepository = container.sourcesRepository,
                        defaultSport = selectedSport,
                        onOpenWatch = { selectTab(Tab.Watch) },
                    )
                }
            }
        }

        BottomNavBar(current = currentTab, onSelect = ::selectTab)
    }
}

/**
 * The tab bar, docked to the bottom edge. The selected tab gets a tinted capsule behind
 * its icon and label.
 */
@Composable
private fun BottomNavBar(current: Tab, onSelect: (Tab) -> Unit) {
    val colors = SportsHubTheme.colors
    val haptics = LocalHapticFeedback.current
    Column(
        Modifier
            .fillMaxWidth()
            .background(colors.chrome)
            .navigationBarsPadding(),
    ) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.stroke))
        Row(
            Modifier
                .align(Alignment.CenterHorizontally)
                .widthIn(max = 560.dp)
                .fillMaxWidth()
                // Grows with large text sizes instead of clipping the labels.
                .heightIn(min = NavBarHeight - 1.dp)
                .selectableGroup()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Tab.entries.forEach { tab ->
                val selected = tab == current
                val tint by animateColorAsState(
                    if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    tween(200),
                    label = "tabTint",
                )
                val fill by animateColorAsState(
                    if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f) else MaterialTheme.colorScheme.primary.copy(alpha = 0f),
                    tween(200),
                    label = "tabFill",
                )
                Column(
                    Modifier
                        .weight(1f)
                        .heightIn(min = NavBarHeight - 17.dp)
                        .clip(CircleShape)
                        .background(fill)
                        // A tab with its selected state for screen readers, a ripple inside the
                        // capsule, and a light tick under the finger when switching.
                        .selectable(
                            selected = selected,
                            role = Role.Tab,
                            interactionSource = remember { MutableInteractionSource() },
                            indication = ripple(color = MaterialTheme.colorScheme.primary),
                            onClick = {
                                if (!selected) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                onSelect(tab)
                            },
                        )
                        .padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        if (selected) tab.selectedIcon else tab.icon,
                        contentDescription = null,
                        tint = tint,
                        modifier = Modifier.size(22.dp),
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        tab.label,
                        color = tint,
                        fontSize = 12.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/** Switches tabs, keeping each tab's own back stack and scroll position. */
private fun NavHostController.navigateToTab(tab: Tab) {
    navigate(tab.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
