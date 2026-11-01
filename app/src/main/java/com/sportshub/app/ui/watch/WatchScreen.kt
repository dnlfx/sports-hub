package com.sportshub.app.ui.watch

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.LiveTv
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Tv
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sportshub.app.data.SourcesRepository
import com.sportshub.app.data.scan.FoundVideo
import com.sportshub.app.data.scan.HtmlMediaExtractor
import com.sportshub.app.data.scan.ScanResult
import com.sportshub.app.data.scan.VideoKind
import com.sportshub.app.model.Competitor
import com.sportshub.app.model.Game
import com.sportshub.app.model.GameStatus
import com.sportshub.app.model.Sport
import com.sportshub.app.model.StreamSource
import com.sportshub.app.ui.components.EmptyState
import com.sportshub.app.ui.components.LargeHeaderBar
import com.sportshub.app.ui.components.LiveBadge
import com.sportshub.app.ui.components.SectionHeader
import com.sportshub.app.ui.components.SportMenuButton
import com.sportshub.app.ui.components.TeamLogo
import com.sportshub.app.ui.player.PlayerActivity
import com.sportshub.app.ui.scores.statusLabel
import com.sportshub.app.ui.theme.ScoreTextStyle
import com.sportshub.app.ui.theme.SportsHubTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WatchScreen(
    sport: Sport,
    onSelectSport: (Sport) -> Unit,
    viewModel: WatchViewModel,
    onAddSource: () -> Unit,
    onOpenGame: (Game) -> Unit,
) {
    val context = LocalContext.current
    val allSources by viewModel.sources.collectAsStateWithLifecycle()
    val scans by viewModel.scans.collectAsStateWithLifecycle()
    val games by viewModel.games.collectAsStateWithLifecycle()
    val sources = remember(allSources, sport) { allSources.filter { it.sportId == sport.id } }

    LaunchedEffect(sources) { viewModel.ensureScanned(sources) }
    LaunchedEffect(sport) { viewModel.loadGames(sport) }

    fun open(video: FoundVideo) {
        val intent = if (video.isPlayable) {
            PlayerActivity.intent(context, video.url, video.title, video.referer, video.kind)
        } else {
            Intent(Intent.ACTION_VIEW, Uri.parse(video.url))
        }
        runCatching { context.startActivity(intent) }
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeHeaderBar(
                title = "Watch",
                subtitle = "Games on now and your sources",
                scrollBehavior = scrollBehavior,
                actions = { SportMenuButton(selected = sport, onSelect = onSelectSport) },
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (games.isNotEmpty()) {
                item(key = "games_header") { SectionHeader("On now and up next", count = games.size) }
                item(key = "games", contentType = "games") {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(games, key = { it.id }) { game -> GameTile(game, onClick = { onOpenGame(game) }) }
                    }
                }
            }

            item(key = "sources_header") {
                SectionHeader(
                    "Your sources",
                    count = sources.size.takeIf { it > 0 },
                    trailing = if (sources.isNotEmpty()) {
                        { TextButton(onClick = onAddSource) { Text("Add") } }
                    } else {
                        null
                    },
                )
            }
            if (sources.isEmpty()) {
                item(key = "no_sources") {
                    EmptyState(
                        icon = Icons.Outlined.LiveTv,
                        title = "No sources yet",
                        message = "Add a page on the Sources tab and the videos on it will show up here.",
                        action = {
                            Button(onClick = onAddSource) {
                                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Add a source")
                            }
                        },
                    )
                }
            } else {
                items(sources, key = { it.id }, contentType = { "source" }) { source ->
                    SourceCard(
                        source = source,
                        state = scans[source.id],
                        onRescan = { viewModel.scan(source) },
                        onOpen = { open(it) },
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }
    }
}

/** Compact game tile for the horizontal "on now" row. */
@Composable
private fun GameTile(game: Game, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.width(184.dp),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = SportsHubTheme.colors.card),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (game.status is GameStatus.Live) {
                    LiveBadge()
                } else {
                    Text(
                        game.statusLabel(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            TileTeam(game.away, showScore = game.status != GameStatus.Scheduled)
            Spacer(Modifier.height(6.dp))
            TileTeam(game.home, showScore = game.status != GameStatus.Scheduled)
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.Tv,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    game.broadcasts.joinToString(", ").ifEmpty { "Network TBA" },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun TileTeam(side: Competitor, showScore: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        TeamLogo(side.team, size = 24.dp)
        Spacer(Modifier.width(8.dp))
        Text(side.team.abbreviation, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
        if (showScore) {
            Text("${side.score ?: 0}", style = ScoreTextStyle.copy(fontSize = MaterialTheme.typography.titleLarge.fontSize))
        }
    }
}

@Composable
private fun SourceCard(
    source: StreamSource,
    state: ScanState?,
    onRescan: () -> Unit,
    onOpen: (FoundVideo) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scanning = state == null || state == ScanState.Scanning
    Card(
        modifier = modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = SportsHubTheme.colors.card),
    ) {
        Column(Modifier.animateContentSize()) {
            Row(
                Modifier.padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SourceAvatar(source)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(source.label, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        SourcesRepository.displayUrl(source.url),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = onRescan, enabled = !scanning) {
                    Icon(Icons.Outlined.Refresh, contentDescription = "Scan again")
                }
            }

            if (scanning) {
                LinearProgressIndicator(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .clip(CircleShape),
                )
                Text(
                    "Looking for videos…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                )
            } else if (state is ScanState.Done) {
                when (val r = state.result) {
                    is ScanResult.Found ->
                        if (r.videos.isEmpty()) {
                            Notice(Icons.Outlined.ErrorOutline, "No videos found on this page.")
                        } else {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            r.videos.forEach { video -> VideoRow(video, onClick = { onOpen(video) }) }
                        }
                    is ScanResult.Blocked -> Notice(Icons.Outlined.Block, r.reason)
                    is ScanResult.Failed -> Notice(Icons.Outlined.ErrorOutline, r.message)
                }
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
private fun SourceAvatar(source: StreamSource) {
    Box(
        Modifier
            .size(40.dp)
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            source.label.firstOrNull()?.uppercase() ?: "?",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

@Composable
private fun VideoRow(video: FoundVideo, onClick: () -> Unit) {
    val (label, icon) = when (video.kind) {
        VideoKind.HLS -> "Live stream" to Icons.Rounded.PlayArrow
        VideoKind.DASH -> "Stream" to Icons.Rounded.PlayArrow
        VideoKind.FILE -> "Video" to Icons.Rounded.PlayArrow
        VideoKind.EMBED -> "Embedded player · opens in browser" to Icons.AutoMirrored.Outlined.OpenInNew
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(
                    if (video.isPlayable) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (video.isPlayable) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(22.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(video.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Notice(icon: ImageVector, text: String) {
    Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
