package com.sportshub.app.ui

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Scoreboard
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.LiveTv
import androidx.compose.material.icons.outlined.Scoreboard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
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
import com.sportshub.app.model.Sport
import com.sportshub.app.ui.scores.GameDetailScreen
import com.sportshub.app.ui.scores.GameDetailViewModel
import com.sportshub.app.ui.scores.ScoresScreen
import com.sportshub.app.ui.scores.ScoresViewModel
import com.sportshub.app.ui.sources.SourcesScreen
import com.sportshub.app.ui.theme.SportsHubTheme
import com.sportshub.app.ui.watch.WatchScreen
import com.sportshub.app.ui.watch.WatchViewModel

private enum class Tab(val route: String, val label: String, val icon: ImageVector, val selectedIcon: ImageVector) {
    Scores("scores", "Scores", Icons.Outlined.Scoreboard, Icons.Filled.Scoreboard),
    Watch("watch", "Watch", Icons.Outlined.LiveTv, Icons.Filled.LiveTv),
    Sources("sources", "Sources", Icons.Outlined.Link, Icons.Filled.Link),
}

private const val GAME_ROUTE = "game/{sportId}/{gameId}"
private fun isGameRoute(route: String?) = route?.startsWith("game/") == true

@Composable
fun SportsHubRoot(container: AppContainer) {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val selectedSport by container.selectedSport.collectAsStateWithLifecycle()
    val route = backStack?.destination?.route

    // The tab the user is in. A game screen belongs to the tab that opened it.
    val tabRoute = if (isGameRoute(route)) navController.previousBackStackEntry?.destination?.route else route
    val currentTab = Tab.entries.firstOrNull { it.route == tabRoute } ?: Tab.Scores

    fun openGame(game: Game) {
        container.lastOpenedGame = game
        navController.navigate("game/${game.sport.id}/${game.id}")
    }

    fun selectTab(tab: Tab) {
        if (tab == currentTab) {
            // Tapping the current tab again goes back to its top level.
            if (isGameRoute(route)) navController.popBackStack(tab.route, inclusive = false)
            return
        }
        navController.navigateToTab(tab)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        // Each screen draws its own top bar under the status bar; only the nav bar is reserved here.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            NavigationBar(containerColor = SportsHubTheme.colors.chrome, tonalElevation = 0.dp) {
                Tab.entries.forEach { tab ->
                    val selected = tab == currentTab
                    NavigationBarItem(
                        selected = selected,
                        onClick = { selectTab(tab) },
                        icon = { Icon(if (selected) tab.selectedIcon else tab.icon, contentDescription = null) },
                        label = { Text(tab.label) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Tab.Scores.route,
            modifier = Modifier.padding(padding),
            // Tabs fade through; the game screen slides in from the right.
            enterTransition = {
                if (isGameRoute(targetState.destination.route)) {
                    slideInHorizontally(tween(300)) { it / 4 } + fadeIn(tween(300))
                } else {
                    fadeIn(tween(220, delayMillis = 60)) + scaleIn(tween(220, delayMillis = 60), initialScale = 0.98f)
                }
            },
            exitTransition = { fadeOut(tween(120)) },
            popEnterTransition = { fadeIn(tween(220)) },
            popExitTransition = {
                if (isGameRoute(initialState.destination.route)) {
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
                    initialGame = container.lastOpenedGame?.takeIf { it.id == gameId },
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
                    onAddSource = { navController.navigateToTab(Tab.Sources) },
                    onOpenGame = { openGame(it) },
                )
            }
            composable(Tab.Sources.route) {
                SourcesScreen(
                    sourcesRepository = container.sourcesRepository,
                    defaultSport = selectedSport,
                    onOpenWatch = { navController.navigateToTab(Tab.Watch) },
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
