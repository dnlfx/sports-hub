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
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.LiveTv
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sportshub.app.data.scan.FoundVideo
import com.sportshub.app.data.scan.HtmlMediaExtractor
import com.sportshub.app.data.scan.ScanResult
import com.sportshub.app.data.scan.VideoKind
import com.sportshub.app.model.Game
import com.sportshub.app.model.GameStatus
import com.sportshub.app.model.Sport
import com.sportshub.app.model.StreamSource
import com.sportshub.app.ui.components.EmptyState
import com.sportshub.app.ui.components.ScreenHeader
import com.sportshub.app.ui.components.SportSelector
import com.sportshub.app.ui.components.TeamLogo
import com.sportshub.app.ui.player.PlayerActivity
import com.sportshub.app.ui.scores.LiveBadge
import com.sportshub.app.ui.scores.statusLabel

@Composable
fun WatchScreen(
    sport: Sport,
    onSelectSport: (Sport) -> Unit,
    viewModel: WatchViewModel,
    onAddSource: () -> Unit,
) {
    val context = LocalContext.current
    val allSources by viewModel.sources.collectAsStateWithLifecycle()
    val scans by viewModel.scans.collectAsStateWithLifecycle()
    val games by viewModel.games.collectAsStateWithLifecycle()
    val sources = allSources.filter { it.sportId == sport.id }

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

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column {
                ScreenHeader("Watch", "Games on now and your ${sport.shortName} sources")
                SportSelector(selected = sport, onSelect = onSelectSport)
            }
        }

        if (games.isNotEmpty()) {
            item { SectionTitle("On now and coming up") }
            items(games, key = { "game_${it.id}" }) { game -> WhereToWatchRow(game) }
        }

        item { SectionTitle("Your sources") }
        if (sources.isEmpty()) {
            item {
                EmptyState(
                    icon = Icons.Outlined.LiveTv,
                    title = "No sources yet",
                    message = "Add a page URL on the Sources tab and the videos on it will show up here.",
                    action = { Button(onClick = onAddSource) { Text("Add a source") } },
                )
            }
        } else {
            items(sources, key = { it.id }) { source ->
                SourceCard(
                    source = source,
                    state = scans[source.id],
                    onRescan = { viewModel.scan(source) },
                    onOpen = { open(it) },
                )
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 20.dp, top = 8.dp),
    )
}

@Composable
private fun WhereToWatchRow(game: Game) {
    Row(
        Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TeamLogo(game.away.team, size = 26.dp)
        Spacer(Modifier.width(4.dp))
        TeamLogo(game.home.team, size = 26.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "${game.away.team.abbreviation} @ ${game.home.team.abbreviation}",
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                game.broadcasts.joinToString(", ").ifEmpty { "Network not announced" },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (game.status is GameStatus.Live) {
            LiveBadge()
        } else {
            Text(game.statusLabel(), style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun SourceCard(
    source: StreamSource,
    state: ScanState?,
    onRescan: () -> Unit,
    onOpen: (FoundVideo) -> Unit,
) {
    Column(
        Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .animateContentSize()
            .padding(vertical = 8.dp),
    ) {
        Row(Modifier.padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(source.label, style = MaterialTheme.typography.titleMedium)
                Text(
                    HtmlMediaExtractor.hostOf(source.url),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (state == ScanState.Scanning) {
                CircularProgressIndicator(Modifier.padding(12.dp).size(20.dp), strokeWidth = 2.dp)
            } else {
                IconButton(onClick = onRescan) { Icon(Icons.Outlined.Refresh, contentDescription = "Scan again") }
            }
        }

        when (state) {
            null, ScanState.Scanning -> Text(
                "Looking for videos…",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            is ScanState.Done -> when (val r = state.result) {
                is ScanResult.Found ->
                    if (r.videos.isEmpty()) {
                        Notice(Icons.Outlined.ErrorOutline, "No videos found on this page.")
                    } else {
                        r.videos.forEach { video -> VideoRow(video, onClick = { onOpen(video) }) }
                    }
                is ScanResult.Blocked -> Notice(Icons.Outlined.Block, r.reason)
                is ScanResult.Failed -> Notice(Icons.Outlined.ErrorOutline, r.message)
            }
        }
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
                .background(MaterialTheme.colorScheme.primary.copy(alpha = if (video.isPlayable) 1f else 0.25f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary)
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
