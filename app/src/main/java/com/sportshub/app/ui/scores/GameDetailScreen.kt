package com.sportshub.app.ui.scores

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Tv
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
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
import com.sportshub.app.model.PlayerRef
import com.sportshub.app.model.Team
import com.sportshub.app.model.TeamStatRow
import com.sportshub.app.ui.components.EmptyState
import com.sportshub.app.ui.components.LightStatusBarIcons
import com.sportshub.app.ui.components.LiveBadge
import com.sportshub.app.ui.components.PrefetchImages
import com.sportshub.app.ui.components.TeamLogo
import com.sportshub.app.ui.components.accentColor
import com.sportshub.app.ui.components.bottomListPadding
import com.sportshub.app.ui.components.cardSurface
import com.sportshub.app.ui.components.heroColor
import com.sportshub.app.ui.components.shimmer
import com.sportshub.app.ui.theme.OnHeroMuted
import com.sportshub.app.ui.theme.OverlineTextStyle
import com.sportshub.app.ui.theme.ScoreTextStyle
import com.sportshub.app.ui.theme.SportsHubTheme

private val TopBarHeight = 56.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameDetailScreen(
    viewModel: GameDetailViewModel,
    /** The game from the scores list, so the header shows instantly while details load. */
    initialGame: Game?,
    onBack: () -> Unit,
    onOpenTeam: (Team) -> Unit,
    onOpenPlayer: (PlayerRef) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Leader and box score headshots start downloading as soon as the details arrive.
    PrefetchImages(
        remember(state.detail) {
            val detail = state.detail
            detail?.leaders.orEmpty().map { it.headshotUrl } +
                detail?.playerStats.orEmpty().flatMap { team -> team.tables.flatMap { t -> t.players.map { it.headshotUrl } } }
        },
    )
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) { viewModel.pollWhileVisible() }
    }

    val latest = state.detail?.game
    val game = remember(latest, initialGame) { mergeGame(latest, initialGame) }
    // Which team's box score is showing. Kept across refreshes and rotation.
    var selectedTeamId by rememberSaveable { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()
    // Past the header, the top bar fills in and names the game.
    val scrolled by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 420 } }
    LightStatusBarIcons(enabled = game != null && !scrolled)

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = viewModel::refresh,
            modifier = Modifier.fillMaxSize(),
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = bottomListPadding()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val detail = state.detail
                // Still waiting on the first load: hold each section's place with a placeholder
                // of the right kind, so nothing is shown that will be swapped out a moment later.
                val waiting = detail == null && state.error == null
                if (game != null) {
                    item(key = "header") { ScoreHero(game, onOpenTeam) }
                    if (game.away.periodScores.isNotEmpty() || game.home.periodScores.isNotEmpty()) {
                        item(key = "linescore") { Linescore(game) }
                    }
                    game.situation?.lastPlay?.let { play ->
                        item(key = "lastplay") {
                            Section("Last play") { Text(play, style = MaterialTheme.typography.bodyMedium) }
                        }
                    }

                    val winProbability = detail?.winProbability
                    if (game.status == GameStatus.Scheduled) {
                        winProbability?.pregameHomePct?.let { pre ->
                            item(key = "predictor") { MatchupPredictor(pre, game) }
                        }
                    } else {
                        when {
                            winProbability != null && winProbability.points.size >= 2 ->
                                item(key = "winprob") { WinProbabilitySection(winProbability, game) }
                            // ESPN charts every NFL and NBA game, so hold its place while the details load.
                            waiting && game.sport.hasWinProbability -> item(key = "winprob") { WinProbabilitySkeleton() }
                        }
                    }

                    val leaders = detail?.leaders.orEmpty()
                    val playerStats = detail?.playerStats.orEmpty()
                    if (game.status == GameStatus.Scheduled) {
                        // Before the game, ESPN's leaders are each team's season leaders.
                        when {
                            leaders.isNotEmpty() -> item(key = "leaders") {
                                Section("Season leaders") { TeamLeadersContent(leaders, game, onOpenTeam, onOpenPlayer) }
                            }
                            waiting -> item(key = "leaders") { SectionSkeleton("Season leaders", rows = 6) }
                        }
                    } else {
                        // Once the game is on, show each team's full box score.
                        when {
                            playerStats.isNotEmpty() ->
                                playerStatsItems(playerStats, game, selectedTeamId, { selectedTeamId = it }, onOpenPlayer)
                            leaders.isNotEmpty() -> item(key = "players") {
                                Section("Leaders") { TeamLeadersContent(leaders, game, onOpenTeam, onOpenPlayer) }
                            }
                            waiting -> item(key = "players") { SectionSkeleton("Player stats", rows = 6) }
                        }
                    }

                    val stats = detail?.teamStats.orEmpty()
                    when {
                        stats.isNotEmpty() -> item(key = "stats") { Section("Team stats") { TeamStats(stats, game, onOpenTeam) } }
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

        DetailTopBar(
            title = game?.let { "${it.away.team.abbreviation} @ ${it.home.team.abbreviation}" } ?: "Game",
            solid = scrolled || game == null,
            onBack = onBack,
        )
    }
}

/**
 * Back button and title over the top of a detail page. Clear over the colored header,
 * then filled with the page background (and showing the title) once scrolled.
 */
@Composable
internal fun DetailTopBar(title: String, solid: Boolean, onBack: () -> Unit) {
    val background by animateColorAsState(
        if (solid) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.background.copy(alpha = 0f),
        tween(200),
        label = "topbar",
    )
    val content by animateColorAsState(
        if (solid) MaterialTheme.colorScheme.onSurface else Color.White,
        tween(200),
        label = "topbarContent",
    )
    Column(Modifier.fillMaxWidth().background(background)) {
        Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
        Row(
            Modifier
                .fillMaxWidth()
                .height(TopBarHeight)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Box(
                    Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(if (solid) Color.Transparent else Color.Black.copy(alpha = 0.22f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back", tint = content)
                }
            }
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                color = content,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .padding(start = 4.dp)
                    .graphicsLayer { alpha = if (solid) 1f else 0f },
            )
        }
    }
}

/**
 * The game's header: a full-width wash of both teams' colors, logos you can tap to open
 * each team, and the score in big numbers.
 */
@Composable
private fun ScoreHero(game: Game, onOpenTeam: (Team) -> Unit) {
    val away = game.away.team.heroColor()
    val home = game.home.team.heroColor()
    val background = MaterialTheme.colorScheme.background
    val wash = remember(away, home) {
        Brush.linearGradient(listOf(away, lerp(away, home, 0.5f), home))
    }
    val fade = remember(background) {
        Brush.verticalGradient(0f to Color.Transparent, 0.75f to Color.Transparent, 1f to background.copy(alpha = 0.35f))
    }
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp))
            .background(wash)
            .background(fade),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(top = TopBarHeight, start = 12.dp, end = 12.dp, bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                HeroTeam(game.away, Modifier.weight(1f), onOpenTeam)
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1.4f)) {
                    if (game.status == GameStatus.Scheduled) {
                        Text(
                            game.statusLabel(),
                            style = MaterialTheme.typography.headlineSmall,
                            color = Color.White,
                            textAlign = TextAlign.Center,
                        )
                    } else {
                        ScoreLine(game)
                        Spacer(Modifier.height(6.dp))
                        if (game.status is GameStatus.Live) {
                            LiveBadge(onDark = true)
                            Spacer(Modifier.height(6.dp))
                        }
                        Text(
                            game.statusLabel(),
                            style = MaterialTheme.typography.labelLarge,
                            color = OnHeroMuted,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
                HeroTeam(game.home, Modifier.weight(1f), onOpenTeam)
            }

            val situation = game.situation
            situation?.downDistance?.let {
                Spacer(Modifier.height(16.dp))
                val redZone = situation.isRedZone
                Text(
                    if (redZone) "$it · Red zone" else it,
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(if (redZone) SportsHubTheme.colors.live.copy(alpha = 0.85f) else Color.Black.copy(alpha = 0.28f))
                        .padding(horizontal = 14.dp, vertical = 7.dp),
                )
            }

            val broadcasts = game.broadcasts.joinToString(", ")
            if (broadcasts.isNotEmpty() || game.venue != null) {
                Spacer(Modifier.height(18.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    if (broadcasts.isNotEmpty()) InfoLabel(Icons.Outlined.Tv, broadcasts, Modifier.weight(1f, fill = false))
                    game.venue?.let { InfoLabel(Icons.Outlined.LocationOn, it, Modifier.weight(1f, fill = false)) }
                }
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
    val dim = Color.White.copy(alpha = 0.6f)
    val text = buildAnnotatedString {
        withStyle(SpanStyle(color = if (final && game.home.isWinner) dim else Color.White)) {
            append("${game.away.score ?: 0}")
        }
        withStyle(SpanStyle(color = Color.White.copy(alpha = 0.55f), fontSize = 0.55.em)) { append("  –  ") }
        withStyle(SpanStyle(color = if (final && game.away.isWinner) dim else Color.White)) {
            append("${game.home.score ?: 0}")
        }
    }
    val measurer = rememberTextMeasurer()
    BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val maxPx = constraints.maxWidth
        val style = remember(text, maxPx) {
            var size = 58f
            fun styleAt(sp: Float) = ScoreTextStyle.copy(fontSize = sp.sp, lineHeight = (sp * 1.05f).sp)
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
        Icon(icon, contentDescription = null, tint = OnHeroMuted, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(6.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = OnHeroMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** One team in the header. Tapping it opens the team's page. */
@Composable
private fun HeroTeam(side: Competitor, modifier: Modifier, onOpenTeam: (Team) -> Unit) {
    Column(
        modifier
            .clip(MaterialTheme.shapes.large)
            .clickable { onOpenTeam(side.team) }
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(84.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            TeamLogo(side.team, size = 60.dp)
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            side.rank?.let { rank ->
                Text("$rank", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = OnHeroMuted)
                Spacer(Modifier.width(4.dp))
            }
            Text(
                side.team.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Icon(
                Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = OnHeroMuted,
                modifier = Modifier.size(16.dp),
            )
        }
        side.team.record?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = OnHeroMuted)
        }
    }
}

/** A titled card on a detail page. */
@Composable
internal fun Section(
    title: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Column(
        modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .cardSurface()
            .padding(18.dp),
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
        Spacer(Modifier.height(14.dp))
        content()
    }
}

/** A section's title with shimmering lines where its content will go. */
@Composable
internal fun SectionSkeleton(title: String, rows: Int) {
    val block = SportsHubTheme.colors.cardHigh
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
    // Q1 to Q4, then OT, 2OT... for each overtime.
    val labels = (1..periods).map { periodLabel(it, game.sport.periodName) }
    Section("Scoring") {
        LinescoreLine(null, labels + "T", header = true)
        listOf(game.away, game.home).forEach { side ->
            val cells = (0 until periods).map { side.periodScores.getOrNull(it)?.toString() ?: "-" }
            LinescoreLine(side, cells + "${side.score ?: 0}", header = false)
        }
    }
}

@Composable
private fun LinescoreLine(side: Competitor?, values: List<String>, header: Boolean) {
    Row(
        Modifier
            .fillMaxWidth()
            .then(
                if (header) {
                    Modifier
                } else {
                    Modifier
                        .padding(top = 6.dp)
                        .clip(MaterialTheme.shapes.small)
                        .background(SportsHubTheme.colors.cardHigh)
                },
            )
            .padding(horizontal = 10.dp, vertical = if (header) 2.dp else 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(Modifier.weight(1.6f), verticalAlignment = Alignment.CenterVertically) {
            if (side != null) {
                TeamLogo(side.team, size = 22.dp)
                Spacer(Modifier.width(8.dp))
                Text(side.team.abbreviation, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
            }
        }
        values.forEachIndexed { i, v ->
            val total = i == values.lastIndex
            Text(
                v,
                Modifier.weight(1f),
                textAlign = TextAlign.Center,
                style = when {
                    header -> OverlineTextStyle
                    total -> ScoreTextStyle.copy(fontSize = 20.sp, lineHeight = 22.sp)
                    else -> MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum")
                },
                color = if (header) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun TeamStats(rows: List<TeamStatRow>, game: Game, onOpenTeam: (Team) -> Unit) {
    val awayColor = game.away.team.accentColor(MaterialTheme.colorScheme.primary)
    val homeColor = game.home.team.accentColor(MaterialTheme.colorScheme.tertiary)
    Column {
        Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier.clip(CircleShape).clickable { onOpenTeam(game.away.team) }.padding(end = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TeamLogo(game.away.team, size = 24.dp)
                Spacer(Modifier.width(6.dp))
                Text(game.away.team.abbreviation, style = MaterialTheme.typography.labelLarge)
            }
            Spacer(Modifier.weight(1f))
            Row(
                Modifier.clip(CircleShape).clickable { onOpenTeam(game.home.team) }.padding(start = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(game.home.team.abbreviation, style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.width(6.dp))
                TeamLogo(game.home.team, size = 24.dp)
            }
        }
        rows.forEach { row ->
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(row.away, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall.copy(fontFeatureSettings = "tnum"))
                Text(
                    row.label,
                    Modifier.weight(2f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    row.home,
                    Modifier.weight(1f),
                    textAlign = TextAlign.End,
                    style = MaterialTheme.typography.titleSmall.copy(fontFeatureSettings = "tnum"),
                )
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
    Row(Modifier.fillMaxWidth().height(6.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
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
    val block = SportsHubTheme.colors.cardHigh
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.shimmer()) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(300.dp)
                .clip(RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp))
                .background(block),
        )
        repeat(2) {
            Box(
                Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth()
                    .height(140.dp)
                    .clip(MaterialTheme.shapes.large)
                    .background(block),
            )
        }
    }
}
