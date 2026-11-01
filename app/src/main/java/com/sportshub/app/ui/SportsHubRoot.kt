package com.sportshub.app.ui

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.sportshub.app.AppContainer
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sportshub.app.model.Sport
import com.sportshub.app.ui.scores.GameDetailScreen
import com.sportshub.app.ui.scores.GameDetailViewModel
import com.sportshub.app.ui.scores.ScoresScreen
import com.sportshub.app.ui.scores.ScoresViewModel
import com.sportshub.app.ui.sources.SourcesScreen
import com.sportshub.app.ui.watch.WatchScreen
import com.sportshub.app.ui.watch.WatchViewModel

private enum class Tab(val route: String, val label: String, val icon: ImageVector) {
    Scores("scores", "Scores", Icons.Outlined.Scoreboard),
    Watch("watch", "Watch", Icons.Outlined.LiveTv),
    Sources("sources", "Sources", Icons.Outlined.Link),
}

@Composable
fun SportsHubRoot(container: AppContainer) {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val selectedSport by container.selectedSport.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                Tab.entries.forEach { tab ->
                    val route = backStack?.destination?.route
                    val selected = backStack?.destination?.hierarchy?.any { it.route == tab.route } == true ||
                        (tab == Tab.Scores && route?.startsWith("game/") == true)
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(tab.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(tab.icon, contentDescription = null) },
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
            // Tabs cross-fade; the game detail screen slides in from the right.
            enterTransition = {
                if (targetState.destination.route?.startsWith("game/") == true) {
                    slideInHorizontally(tween(280)) { it / 3 } + fadeIn(tween(280))
                } else {
                    fadeIn(tween(220))
                }
            },
            exitTransition = { fadeOut(tween(180)) },
            popEnterTransition = { fadeIn(tween(220)) },
            popExitTransition = {
                if (initialState.destination.route?.startsWith("game/") == true) {
                    slideOutHorizontally(tween(240)) { it / 3 } + fadeOut(tween(240))
                } else {
                    fadeOut(tween(180))
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
                    onOpenGame = { game ->
                        container.lastOpenedGame = game
                        navController.navigate("game/${game.sport.id}/${game.id}")
                    },
                )
            }
            composable("game/{sportId}/{gameId}") { entry ->
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
                    onAddSource = { navController.navigate(Tab.Sources.route) },
                )
            }
            composable(Tab.Sources.route) {
                SourcesScreen(sourcesRepository = container.sourcesRepository, defaultSport = selectedSport)
            }
        }
    }
}
