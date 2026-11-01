package com.sportshub.app.ui

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
import com.sportshub.app.ui.scores.ScoresScreen
import com.sportshub.app.ui.sources.SourcesScreen
import com.sportshub.app.ui.watch.WatchScreen

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
                    val selected = backStack?.destination?.hierarchy?.any { it.route == tab.route } == true
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
        ) {
            composable(Tab.Scores.route) {
                ScoresScreen(
                    sport = selectedSport,
                    onSelectSport = container::selectSport,
                    scoresRepository = container.scoresRepository,
                )
            }
            composable(Tab.Watch.route) {
                WatchScreen(
                    sport = selectedSport,
                    onSelectSport = container::selectSport,
                    sourcesRepository = container.sourcesRepository,
                    onAddSource = { navController.navigate(Tab.Sources.route) },
                )
            }
            composable(Tab.Sources.route) {
                SourcesScreen(sourcesRepository = container.sourcesRepository, defaultSport = selectedSport)
            }
        }
    }
}
