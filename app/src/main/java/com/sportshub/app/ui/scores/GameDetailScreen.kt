package com.sportshub.app.ui.scores

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Tv
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.sportshub.app.model.Competitor
import com.sportshub.app.model.Game
import com.sportshub.app.model.GameStatus
import com.sportshub.app.model.TeamStatRow
import com.sportshub.app.ui.components.EmptyState
import com.sportshub.app.ui.components.LiveBadge
import com.sportshub.app.ui.components.TeamLogo
import com.sportshub.app.ui.components.accentColor
import com.sportshub.app.ui.components.shimmer
import com.sportshub.app.ui.theme.ScoreTextStyle
import com.sportshub.app.ui.theme.SportsHubTheme

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

    val latest = state.detail?.game
    val game = remember(latest, initialGame) { mergeGame(latest, initialGame) }
    // Which team's box score is showing. Kept across refreshes and rotation.
    var selectedTeamId by rememberSaveable { mutableStateOf<String?>(null) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        game?.let { "${it.away.team.abbreviation} @ ${it.home.team.abbreviation}" } ?: "Game",
                        style = MaterialTheme.typography.titleLarge,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
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
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val detail = state.detail
                // Still waiting on the first load: hold each section's place with a placeholder
                // of the right kind, so nothing is shown that will be swapped out a moment later.
                val waiting = detail == null && state.error == null
                if (game != null) {
                    item(key = "header") { ScoreHeader(game) }
                    if (game.away.periodScores.isNotEmpty() || game.home.periodScores.isNotEmpty()) {
                        item(key = "linescore") { Linescore(game) }
                    }
                    game.situation?.lastPlay?.let { play ->
                        item(key = "lastplay") {
                            Section("Last play") { Text(play, style = MaterialTheme.typography.bodyMedium) }
                        }
                    }

                    val leaders = detail?.leaders.orEmpty()
                    val playerStats = detail?.playerStats.orEmpty()
                    if (game.status == GameStatus.Scheduled) {
                        // Before kickoff, ESPN's leaders are each team's season leaders.
                        when {
                            leaders.isNotEmpty() -> item(key = "leaders") {
                                Section("Season leaders") { TeamLeadersContent(leaders, game) }
                            }
                            waiting -> item(key = "leaders") { SectionSkeleton("Season leaders", rows = 6) }
                        }
                    } else {
                        // Once the game is on, show each team's full box score.
                        when {
                            playerStats.isNotEmpty() ->
                                playerStatsItems(playerStats, game, selectedTeamId) { selectedTeamId = it }
                            leaders.isNotEmpty() -> item(key = "players") {
                                Section("Leaders") { TeamLeadersContent(leaders, game) }
                            }
                            waiting -> item(key = "players") { SectionSkeleton("Player stats", rows = 6) }
                        }
                    }

                    val stats = detail?.teamStats.orEmpty()
                    when {
                        stats.isNotEmpty() -> item(key = "stats") { Section("Team stats") { TeamStats(stats, game) } }
                        waiting -> item(key = "stats") { SectionSkeleton("Team stats", rows = 5) }
                    }
                } else if (waiting) {
                    item(key = "loading") { DetailSkeleton() }
                }

                val error = state.error
                if (error != null && detail == null) {
                    item(key = "error") {
                        EmptyState(
                            icon = Icons.Outlined.CloudOff,
                            title = "Couldn't load details",
                            message = error,
                            action = { FilledTonalButton(onClick = viewModel::retry) { Text("Try again") } },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ScoreHeader(game: Game) {
    val awayColor = game.away.team.accentColor()
    val homeColor = game.home.team.accentColor()
    val card = SportsHubTheme.colors.card
    // A wash of each team's color behind its side of the header.
    val wash = remember(awayColor, homeColor, card) {
        Brush.horizontalGradient(
            0f to awayColor.copy(alpha = 0.30f),
            0.5f to Color.Transparent,
            1f to homeColor.copy(alpha = 0.30f),
        )
    }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.extraLarge)
            .background(card)
            .background(wash)
            .padding(horizontal = 16.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            HeaderTeam(game.away, Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1.5f)) {
                if (game.status == GameStatus.Scheduled) {
                    Text(
                        game.statusLabel(),
                        style = MaterialTheme.typography.titleLarge,
                        textAlign = TextAlign.Center,
                    )
                } else {
                    ScoreLine(game)
                    Spacer(Modifier.height(6.dp))
                    if (game.status is GameStatus.Live) {
                        LiveBadge()
                        Spacer(Modifier.height(4.dp))
                    }
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

        val situation = game.situation
        situation?.downDistance?.let {
            Spacer(Modifier.height(14.dp))
            val redZone = situation.isRedZone
            Text(
                if (redZone) "$it · Red zone" else it,
                style = MaterialTheme.typography.labelLarge,
                color = if (redZone) SportsHubTheme.colors.live else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.7f))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }

        val broadcasts = game.broadcasts.joinToString(", ")
        if (broadcasts.isNotEmpty() || game.venue != null) {
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                if (broadcasts.isNotEmpty()) InfoLabel(Icons.Outlined.Tv, broadcasts, Modifier.weight(1f, fill = false))
                game.venue?.let { InfoLabel(Icons.Outlined.LocationOn, it, Modifier.weight(1f, fill = false)) }
            }
        }
    }
}

/**
 * "15 – 19" on a single line. Starts big and shrinks to fit the space between the
 * two teams, so a two-digit score never wraps a digit onto the next line. The size is
 * worked out before drawing, so the score shows at its final size on the first frame.
 */
@Composable
private fun ScoreLine(game: Game) {
    val final = game.status is GameStatus.Final
    val dim = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
    val dashColor = MaterialTheme.colorScheme.onSurfaceVariant
    val text = buildAnnotatedString {
        withStyle(SpanStyle(color = if (final && game.home.isWinner) dim else Color.Unspecified)) {
            append("${game.away.score ?: 0}")
        }
        withStyle(SpanStyle(color = dashColor, fontSize = 0.6.em)) { append("  –  ") }
        withStyle(SpanStyle(color = if (final && game.away.isWinner) dim else Color.Unspecified)) {
            append("${game.home.score ?: 0}")
        }
    }
    val measurer = rememberTextMeasurer()
    BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val maxPx = constraints.maxWidth
        val style = remember(text, maxPx) {
            var size = 48f
            fun styleAt(sp: Float) = ScoreTextStyle.copy(fontSize = sp.sp, lineHeight = (sp * 1.1f).sp)
            if (maxPx != Constraints.Infinity) {
                while (size > 22f && measurer.measure(text, styleAt(size), maxLines = 1, softWrap = false).size.width > maxPx) {
                    size *= 0.9f
                }
            }
            styleAt(size)
        }
        Text(text, style = style, maxLines = 1, softWrap = false, textAlign = TextAlign.Center)
    }
}

@Composable
private fun InfoLabel(icon: ImageVector, text: String, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun HeaderTeam(side: Competitor, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        TeamLogo(side.team, size = 64.dp)
        Spacer(Modifier.height(8.dp))
        Text(
            side.team.name,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        side.team.record?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun Section(
    title: String,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(SportsHubTheme.colors.card)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier
                    .weight(1f)
                    .semantics { heading() },
            )
            trailing?.invoke()
        }
        Spacer(Modifier.height(12.dp))
        content()
    }
}

/** A section's title with shimmering lines where its content will go. */
@Composable
private fun SectionSkeleton(title: String, rows: Int) {
    val block = MaterialTheme.colorScheme.surfaceContainerHigh
    Section(title) {
        Column(Modifier.shimmer(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            repeat(rows) { i ->
                Box(
                    Modifier
                        .fillMaxWidth(if (i % 3 == 2) 0.6f else 1f)
                        .height(18.dp)
                        .clip(MaterialTheme.shapes.small)
                        .background(block),
                )
            }
        }
    }
}

@Composable
private fun Linescore(game: Game) {
    val periods = maxOf(game.away.periodScores.size, game.home.periodScores.size)
    val labels = (1..periods).map { if (it <= 4) "${game.sport.periodName}$it" else "OT" }
    Section("Scoring") {
        LinescoreLine("", labels + "T", header = true)
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        listOf(game.away, game.home).forEach { side ->
            val cells = (0 until periods).map { side.periodScores.getOrNull(it)?.toString() ?: "-" }
            LinescoreLine(side.team.abbreviation, cells + "${side.score ?: 0}", header = false)
        }
    }
}

@Composable
private fun LinescoreLine(name: String, values: List<String>, header: Boolean) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(name, Modifier.weight(1.4f), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
        values.forEachIndexed { i, v ->
            val total = i == values.lastIndex
            Text(
                v,
                Modifier.weight(1f),
                textAlign = TextAlign.Center,
                style = if (total && !header) {
                    ScoreTextStyle.copy(fontSize = 18.sp, lineHeight = 20.sp)
                } else {
                    MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum")
                },
                fontWeight = if (header) FontWeight.SemiBold else null,
                color = if (header) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun TeamStats(rows: List<TeamStatRow>, game: Game) {
    val awayColor = game.away.team.accentColor(MaterialTheme.colorScheme.primary)
    val homeColor = game.home.team.accentColor(MaterialTheme.colorScheme.secondary)
    Column {
        Row(Modifier.fillMaxWidth().padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            TeamLogo(game.away.team, size = 22.dp)
            Spacer(Modifier.width(6.dp))
            Text(game.away.team.abbreviation, style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.weight(1f))
            Text(game.home.team.abbreviation, style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.width(6.dp))
            TeamLogo(game.home.team, size = 22.dp)
        }
        rows.forEach { row ->
            HorizontalDivider(Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(row.away, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                Text(
                    row.label,
                    Modifier.weight(2f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(row.home, Modifier.weight(1f), textAlign = TextAlign.End, style = MaterialTheme.typography.titleSmall)
            }
            val share = remember(row) { statShare(row.away, row.home) }
            if (share != null) {
                Spacer(Modifier.height(6.dp))
                StatBar(share, awayColor, homeColor)
            }
        }
    }
}

/** Split bar: the away team's share of the stat on the left, the home team's on the right. */
@Composable
private fun StatBar(awayShare: Float, awayColor: Color, homeColor: Color) {
    Row(Modifier.fillMaxWidth().height(4.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        if (awayShare > 0f) {
            Box(Modifier.weight(awayShare).fillMaxSize().clip(CircleShape).background(awayColor))
        }
        if (awayShare < 1f) {
            Box(Modifier.weight(1f - awayShare).fillMaxSize().clip(CircleShape).background(homeColor))
        }
    }
}

@Composable
private fun DetailSkeleton() {
    val block = MaterialTheme.colorScheme.surfaceContainerHigh
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.shimmer()) {
        repeat(2) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .clip(MaterialTheme.shapes.large)
                    .background(block),
            )
        }
    }
}
