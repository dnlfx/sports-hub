package com.sportshub.app.ui.scores

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import coil.compose.AsyncImage
import com.sportshub.app.model.Competitor
import com.sportshub.app.model.Game
import com.sportshub.app.model.GameStatus
import com.sportshub.app.model.Leader
import com.sportshub.app.model.TeamStatRow
import com.sportshub.app.ui.components.EmptyState
import com.sportshub.app.ui.components.TeamLogo
import com.sportshub.app.ui.theme.ScoreTextStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameDetailScreen(
    viewModel: GameDetailViewModel,
    /** The game from the scores list, so the header shows instantly while details load. */
    initialGame: Game?,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) { viewModel.pollWhileVisible() }
    }

    val game = state.detail?.game ?: initialGame
    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(game?.let { "${it.away.team.abbreviation} @ ${it.home.team.abbreviation}" } ?: "Game") },
            navigationIcon = {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back") }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
        )
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (game != null) {
                item { ScoreHeader(game) }
                if (game.away.periodScores.isNotEmpty() || game.home.periodScores.isNotEmpty()) {
                    item { Linescore(game) }
                }
                game.situation?.lastPlay?.let { play ->
                    item { Section("Last play") { Text(play, style = MaterialTheme.typography.bodyMedium) } }
                }
            }

            val leaders = state.detail?.leaders?.takeIf { it.isNotEmpty() } ?: game?.leaders.orEmpty()
            if (leaders.isNotEmpty() && game != null) {
                item { Section("Leaders") { LeadersList(leaders, game) } }
            }

            val stats = state.detail?.teamStats.orEmpty()
            if (stats.isNotEmpty() && game != null) {
                item { Section("Team stats") { TeamStats(stats, game) } }
            }

            when {
                state.isLoading && state.detail == null -> item {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                state.error != null && state.detail == null -> item {
                    EmptyState(
                        icon = Icons.Outlined.CloudOff,
                        title = "Couldn't load details",
                        message = state.error!!,
                        action = { TextButton(onClick = viewModel::retry) { Text("Try again") } },
                    )
                }
            }
        }
    }
}

@Composable
private fun ScoreHeader(game: Game) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            HeaderTeam(game.away, Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1.2f)) {
                if (game.status == GameStatus.Scheduled) {
                    Text(game.statusLabel(), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${game.away.score ?: 0}", style = ScoreTextStyle.copy(fontSize = ScoreTextStyle.fontSize * 1.4f))
                        Text("  –  ", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${game.home.score ?: 0}", style = ScoreTextStyle.copy(fontSize = ScoreTextStyle.fontSize * 1.4f))
                    }
                    Spacer(Modifier.height(6.dp))
                    if (game.status is GameStatus.Live) LiveBadge()
                    Spacer(Modifier.height(4.dp))
                    Text(
                        game.statusLabel(),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            HeaderTeam(game.home, Modifier.weight(1f))
        }
        val footer = listOfNotNull(game.broadcasts.joinToString(", ").ifEmpty { null }, game.venue).joinToString(" · ")
        if (footer.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            Text(footer, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        game.situation?.downDistance?.let {
            Spacer(Modifier.height(6.dp))
            Text(it, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun HeaderTeam(side: Competitor, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        TeamLogo(side.team, size = 56.dp)
        Spacer(Modifier.height(8.dp))
        Text(side.team.name, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        side.team.record?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(16.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(12.dp))
        content()
    }
}

@Composable
private fun Linescore(game: Game) {
    val periods = maxOf(game.away.periodScores.size, game.home.periodScores.size)
    val labels = (1..periods).map { if (it <= 4) "${game.sport.periodName}$it" else "OT" }
    Section("Scoring") {
        LinescoreLine("", labels + "T", header = true)
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        listOf(game.away, game.home).forEach { side ->
            val cells = (0 until periods).map { side.periodScores.getOrNull(it)?.toString() ?: "-" }
            LinescoreLine(side.team.abbreviation, cells + "${side.score ?: 0}", header = false)
        }
    }
}

@Composable
private fun LinescoreLine(name: String, values: List<String>, header: Boolean) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(name, Modifier.weight(1.4f), fontWeight = FontWeight.Bold)
        values.forEachIndexed { i, v ->
            Text(
                v,
                Modifier.weight(1f),
                textAlign = TextAlign.Center,
                fontWeight = if (header || i == values.lastIndex) FontWeight.Bold else FontWeight.Normal,
                color = if (header) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun LeadersList(leaders: List<Leader>, game: Game) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        leaders.forEach { leader ->
            val team = listOf(game.away.team, game.home.team).firstOrNull { it.id == leader.teamId }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (leader.headshotUrl != null) {
                    AsyncImage(
                        model = leader.headshotUrl,
                        contentDescription = leader.athleteName,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                    )
                } else if (team != null) {
                    TeamLogo(team, size = 40.dp)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        listOfNotNull(leader.category.ifEmpty { null }, team?.abbreviation).joinToString(" · "),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(leader.athleteName, style = MaterialTheme.typography.titleSmall)
                    Text(leader.summary, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun TeamStats(rows: List<TeamStatRow>, game: Game) {
    Column {
        Row(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
            Text(game.away.team.abbreviation, Modifier.weight(1f), fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(2f))
            Text(game.home.team.abbreviation, Modifier.weight(1f), fontWeight = FontWeight.Bold, textAlign = TextAlign.End)
        }
        rows.forEach { row ->
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Text(row.away, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                Text(
                    row.label,
                    Modifier.weight(2f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(row.home, Modifier.weight(1f), textAlign = TextAlign.End, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
