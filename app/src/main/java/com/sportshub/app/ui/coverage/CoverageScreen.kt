package com.sportshub.app.ui.coverage

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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Tv
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sportshub.app.data.tv.CoverageWindow
import com.sportshub.app.data.tv.TvMarket
import com.sportshub.app.data.tv.TvMarketRepository
import com.sportshub.app.model.Game
import com.sportshub.app.ui.components.EmptyState
import com.sportshub.app.ui.components.LightStatusBarIcons
import com.sportshub.app.ui.components.PillTabs
import com.sportshub.app.ui.components.SectionHeader
import com.sportshub.app.ui.components.Tag
import com.sportshub.app.ui.components.TeamLogo
import com.sportshub.app.ui.components.bottomListPadding
import com.sportshub.app.ui.components.cardSurface
import com.sportshub.app.ui.components.shimmer
import com.sportshub.app.ui.scores.statusLabel
import com.sportshub.app.ui.theme.SportsHubTheme
import kotlin.math.roundToInt

/** Where the networks' own weekly maps are collected. */
/** 506 Sports' maps for one week of the regular season, or its NFL page when the week isn't known. */
private fun officialMapsUrl(season: Int?, seasonType: Int?, week: Int?): String =
    if (season != null && week != null && seasonType == 2) "https://506sports.com/nfl.php?yr=$season&wk=$week" else "https://506sports.com/nfl.php"

/** Widest the page gets on big screens. */
private val MaxContentWidth = 720.dp

/**
 * One color per game in a window, distinct from each other in light and dark mode. Team
 * colors would clash (half the league is navy), so games get these in order of reach.
 */
private val GamePalette = listOf(
    Color(0xFF3D7BFF), Color(0xFFFF8A1F), Color(0xFF22B573), Color(0xFFE5484D),
    Color(0xFFA35BFF), Color(0xFFF5C400), Color(0xFF14B8C4), Color(0xFFEC4899),
    Color(0xFF9C7B5B), Color(0xFF7C8AA5),
)

/**
 * The NFL's TV coverage for a week: a map per network and window showing which game each
 * part of the country gets, the games with their reach, and the user's own market.
 */
@Composable
fun CoverageScreen(
    viewModel: CoverageViewModel,
    marketRepository: TvMarketRepository,
    onBack: () -> Unit,
    onOpenGame: (Game) -> Unit,
) {
    LightStatusBarIcons(enabled = false)
    val state by viewModel.state.collectAsStateWithLifecycle()
    val market by marketRepository.market.collectAsStateWithLifecycle()
    val zip by marketRepository.zip.collectAsStateWithLifecycle()
    var picking by rememberSaveable { mutableStateOf(false) }
    var windowKey by rememberSaveable { mutableStateOf<String?>(null) }
    var tapped by remember { mutableStateOf<TvMarket?>(null) }
    val mapData = rememberMapData()
    val uriHandler = LocalUriHandler.current

    val windows = state.coverage?.windows.orEmpty()
    // Opens on the first regional map (a Sunday afternoon), the one most worth seeing.
    val index = windows.indexOfFirst { it.key == windowKey }.takeIf { it >= 0 }
        ?: windows.indexOfFirst { it.kind == CoverageWindow.Kind.Regional }.takeIf { it >= 0 }
        ?: 0
    val window = windows.getOrNull(index)
    val gameColors = remember(window) {
        val w = window ?: return@remember emptyMap<String, Color>()
        w.games
            .sortedByDescending { w.share(it.id) }
            .mapIndexed { i, game -> game.id to GamePalette[i % GamePalette.size] }
            .toMap()
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
        Row(
            Modifier
                .fillMaxWidth()
                .height(64.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
            }
            Column(Modifier.weight(1f).padding(start = 4.dp)) {
                Text("TV coverage", style = MaterialTheme.typography.titleLarge, maxLines = 1)
                Text(
                    state.week?.let { if (state.seasonType == 3) "Playoffs · round $it" else "Week $it" } ?: "This week",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            val week = state.week
            IconButton(onClick = { week?.let { viewModel.showWeek(it - 1) } }, enabled = week != null && week > 1) {
                Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, contentDescription = "Previous week")
            }
            IconButton(onClick = { week?.let { viewModel.showWeek(it + 1) } }, enabled = week != null && week < viewModel.lastWeek) {
                Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = "Next week")
            }
        }

        BoxWithConstraints(Modifier.fillMaxSize()) {
            val side = ((maxWidth - MaxContentWidth) / 2).coerceAtLeast(0.dp)
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = side, end = side, bottom = bottomListPadding()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item(key = "market") {
                    MarketCard(market, zip, onChange = { picking = true }, modifier = Modifier.padding(horizontal = 16.dp))
                }
                when {
                    state.coverage == null && state.isLoading -> item(key = "loading") {
                        Box(
                            Modifier
                                .padding(16.dp)
                                .fillMaxWidth()
                                .aspectRatio(1.6f)
                                .clip(MaterialTheme.shapes.large)
                                .background(SportsHubTheme.colors.cardHigh)
                                .shimmer(),
                        )
                    }
                    state.coverage == null -> item(key = "error") {
                        EmptyState(
                            icon = Icons.Outlined.CloudOff,
                            title = "Couldn't load the week",
                            message = (state.error ?: "Something went wrong.") + " Check your connection and try again.",
                            action = { FilledTonalButton(onClick = viewModel::retry) { Text("Try again") } },
                        )
                    }
                    window == null -> item(key = "none") {
                        EmptyState(
                            icon = Icons.Outlined.Tv,
                            title = "No TV listings yet",
                            message = "The networks haven't been set for this week's games.",
                        )
                    }
                    else -> {
                        item(key = "windows") {
                            PillTabs(
                                labels = windows.map { it.title },
                                selected = index,
                                onSelect = { windowKey = windows[it].key; tapped = null },
                            )
                        }
                        item(key = "map") {
                            Column(
                                Modifier
                                    .padding(horizontal = 16.dp)
                                    .fillMaxWidth()
                                    .cardSurface()
                                    .padding(8.dp),
                            ) {
                                if (mapData == null) {
                                    Box(Modifier.fillMaxWidth().aspectRatio(1.6f).shimmer())
                                } else {
                                    CoverageMap(
                                        data = mapData,
                                        window = window,
                                        gameColors = gameColors,
                                        yourMarket = market,
                                        selected = tapped,
                                        onTapMarket = { tapped = it },
                                        description = mapDescription(window, market),
                                    )
                                }
                                WhereLine(window, tapped ?: market, isYours = tapped == null || tapped?.id == market?.id)
                            }
                        }
                        item(key = "games_header") { SectionHeader(windowSummary(window)) }
                        items(window.games.sortedByDescending { window.share(it.id) }, key = { "game_${window.key}_${it.id}" }) { game ->
                            GameReach(
                                game = game,
                                window = window,
                                color = gameColors[game.id] ?: Color.Gray,
                                yours = market?.id?.let { window.airing[it] } == game.id,
                                onClick = { onOpenGame(game) },
                                modifier = Modifier.padding(horizontal = 16.dp),
                            )
                        }
                        item(key = "about") {
                            About(onOfficialMaps = { uriHandler.openUri(officialMapsUrl(state.season, state.seasonType, state.week)) })
                        }
                    }
                }
            }
        }
    }

    if (picking) {
        MarketPickerSheet(repository = marketRepository, current = market, onDismiss = { picking = false })
    }
}

@Composable
private fun MarketCard(market: TvMarket?, zip: String?, onChange: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .cardSurface()
            .clickable(role = Role.Button, onClickLabel = "Change TV market", onClick = onChange)
            .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.Place, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("Your TV market", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                market?.label ?: "Not set",
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                if (zip != null) "ZIP $zip: exact channels on Scores" else "Add your ZIP for exact channels",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        TextButton(onClick = onChange) { Text(if (market == null) "Set" else "Change") }
    }
}

/** Under the map: what the tapped market (or the user's own) gets in this window. */
@Composable
private fun WhereLine(window: CoverageWindow, market: TvMarket?, isYours: Boolean) {
    val text = when {
        market == null -> "Tap the map to see what any market gets."
        else -> {
            val game = window.games.firstOrNull { it.id == window.airing[market.id] }
            val who = if (isYours) "${market.name} (you)" else market.label
            when {
                game != null -> "$who: ${game.away.team.abbreviation} @ ${game.home.team.abbreviation}"
                window.kind == CoverageWindow.Kind.LocalOnly -> "$who: only on ${window.network}"
                else -> "$who: no game"
            }
        }
    }
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 4.dp),
    )
}

/** One game in the window: its color on the map, the matchup, kickoff and how much of the country gets it. */
@Composable
private fun GameReach(
    game: Game,
    window: CoverageWindow,
    color: Color,
    yours: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val share = (window.share(game.id) * 100).roundToInt()
    val reach = when (window.kind) {
        CoverageWindow.Kind.National -> "Everywhere on ${window.network}"
        CoverageWindow.Kind.Regional -> "About $share% of TV homes"
        CoverageWindow.Kind.LocalOnly -> {
            val local = window.marketsFor(game.id).take(3).joinToString(", ") { it.name }
            if (local.isEmpty()) "Only on ${window.network}" else "On ${window.network}. Free over the air in $local"
        }
    }
    val matchup = "${game.away.team.name} at ${game.home.team.name}"
    Row(
        modifier
            .fillMaxWidth()
            .cardSurface()
            .clickable(role = Role.Button, onClickLabel = "Open game", onClick = onClick)
            .clearAndSetSemantics { contentDescription = "$matchup. ${game.statusLabel()}. $reach.${if (yours) " Your market." else ""}" }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(width = 6.dp, height = 40.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(color),
        )
        Spacer(Modifier.width(12.dp))
        TeamLogo(game.away.team, size = 28.dp)
        Spacer(Modifier.width(6.dp))
        TeamLogo(game.home.team, size = 28.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${game.away.team.abbreviation} @ ${game.home.team.abbreviation}",
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                )
                if (yours) {
                    Spacer(Modifier.width(8.dp))
                    Tag("YOURS", MaterialTheme.colorScheme.primary)
                }
            }
            Text(
                reach,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(game.statusLabel(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun About(onOfficialMaps: () -> Unit) {
    Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
        Text(
            "Projected from each game's network and kickoff. A team's home markets always get its game, " +
                "and its fan region usually does too; elsewhere it's a mix of the nearest game and the biggest " +
                "draw. Games on cable or streaming are shown on a local station in the teams' home markets. " +
                "Right for about 3 in 4 markets; with your ZIP set, Scores shows your real channels from the TV guide.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = onOfficialMaps, contentPadding = PaddingValues(horizontal = 0.dp)) {
            Text("Official maps at 506 Sports")
            Spacer(Modifier.width(6.dp))
            Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
        }
    }
}

private fun windowSummary(window: CoverageWindow): String = when (window.kind) {
    CoverageWindow.Kind.Regional -> "${window.games.size} games on ${window.network}"
    CoverageWindow.Kind.National -> "National game on ${window.network}"
    CoverageWindow.Kind.LocalOnly -> "On ${window.network}"
}

private fun mapDescription(window: CoverageWindow, market: TvMarket?): String = buildString {
    append("Map of ${window.title}. ")
    window.games.forEach { game ->
        val share = (window.share(game.id) * 100).roundToInt()
        append("${game.away.team.name} at ${game.home.team.name}: $share percent of TV homes. ")
    }
    market?.let { m ->
        val game = window.games.firstOrNull { it.id == window.airing[m.id] }
        append(if (game != null) "Your market gets ${game.away.team.name} at ${game.home.team.name}." else "Your market gets no game over the air.")
    }
}
