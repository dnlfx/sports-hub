package com.sportshub.app.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.sportshub.app.ui.components.LocalBottomBarSpace
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
private fun isDetailRoute(route: String?) =
    route?.startsWith("game/") == true || route?.startsWith("team/") == true || route?.startsWith("player/") == true

/** Height of the floating navigation bar, and the gap around it. */
private val NavBarHeight = 64.dp
private val NavBarMargin = 12.dp

@Composable
fun SportsHubRoot(container: AppContainer) {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val selectedSport by container.selectedSport.collectAsStateWithLifecycle()
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

    fun selectTab(tab: Tab) {
        if (tab == currentTab) {
            // Tapping the current tab again goes back to its top level.
            if (isDetailRoute(route)) navController.popBackStack(tab.route, inclusive = false)
            return
        }
        currentTab = tab
        navController.navigateToTab(tab)
    }

    val systemNavBar = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val barSpace = NavBarHeight + NavBarMargin * 2 + systemNavBar

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        CompositionLocalProvider(LocalBottomBarSpace provides barSpace) {
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
                        factory = viewModelFactory { initializer { ScoresViewModel(container.scoresRepository) } },
                    )
                    ScoresScreen(
                        sport = selectedSport,
                        onSelectSport = container::selectSport,
                        viewModel = scoresViewModel,
                        onOpenGame = { openGame(it) },
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

        FloatingNavBar(
            current = currentTab,
            onSelect = ::selectTab,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = NavBarMargin),
        )
    }
}

/**
 * The tab bar: a pill that floats above the content. The selected tab gets a tinted
 * capsule behind its icon and label.
 */
@Composable
private fun FloatingNavBar(current: Tab, onSelect: (Tab) -> Unit, modifier: Modifier = Modifier) {
    val colors = SportsHubTheme.colors
    Row(
        modifier
            .widthIn(max = 480.dp)
            .fillMaxWidth()
            .height(NavBarHeight)
            .shadow(if (colors.isDark) 0.dp else 18.dp, CircleShape, clip = false)
            .clip(CircleShape)
            .background(colors.chrome)
            .border(1.dp, colors.stroke, CircleShape)
            .padding(6.dp),
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
                    .fillMaxHeight()
                    .clip(CircleShape)
                    .background(fill)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { onSelect(tab) },
                    ),
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
                    fontSize = 11.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1,
                )
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
