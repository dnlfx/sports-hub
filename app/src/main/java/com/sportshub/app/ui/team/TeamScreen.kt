package com.sportshub.app.ui.team

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.CompareArrows
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.EventBusy
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.QueryStats
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.sportshub.app.data.TeamPart
import com.sportshub.app.model.DepthChart
import com.sportshub.app.model.Game
import com.sportshub.app.model.GameStatus
import com.sportshub.app.model.PlayerRef
import com.sportshub.app.model.RosterPlayer
import com.sportshub.app.model.ScheduleEntry
import com.sportshub.app.model.Team
import com.sportshub.app.model.TeamProfile
import com.sportshub.app.ui.components.EmptyState
import com.sportshub.app.ui.components.LightStatusBarIcons
import com.sportshub.app.ui.components.LiveBadge
import com.sportshub.app.ui.components.PillTabs
import com.sportshub.app.ui.components.PlayerHeadshot
import com.sportshub.app.ui.components.PrefetchImages
import com.sportshub.app.ui.components.SectionHeader
import com.sportshub.app.ui.components.Tag
import com.sportshub.app.ui.components.TeamLogo
import com.sportshub.app.ui.components.bottomListPadding
import com.sportshub.app.ui.components.cardSurface
import com.sportshub.app.ui.components.groupedShape
import com.sportshub.app.ui.components.heroColor
import com.sportshub.app.ui.components.shimmer
import com.sportshub.app.ui.scores.Section
import com.sportshub.app.ui.scores.SectionSkeleton
import com.sportshub.app.ui.scores.StatTable
import com.sportshub.app.ui.theme.DisplayFamily
import com.sportshub.app.ui.theme.OnHeroMuted
import com.sportshub.app.ui.theme.OverlineTextStyle
import com.sportshub.app.ui.theme.SportsHubTheme
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

private enum class TeamTab(val label: String) {
    Schedule("Schedule"),
    Players("Player stats"),
    Stats("Team stats"),
    Roster("Roster"),
    Depth("Depth chart"),
}

private val shortDate = DateTimeFormatter.ofPattern("MMM d", Locale.US)
private val dayOfWeek = DateTimeFormatter.ofPattern("EEE", Locale.US)
private val kickoff = DateTimeFormatter.ofPattern("h:mm a", Locale.US)

/** How many players each season stat table shows before "Show all". */
private const val TABLE_PREVIEW = 8

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun TeamScreen(
    viewModel: TeamViewModel,
    onBack: () -> Unit,
    onOpenGame: (Game) -> Unit,
    onOpenPlayer: (PlayerRef) -> Unit,
    isFavorite: Boolean,
    onToggleFavorite: (Team) -> Unit,
    onCompare: (Team) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Headshots start downloading as soon as the roster arrives, before the Roster or
    // Depth tab is opened, so they're already up when it is.
    PrefetchImages(remember(state.roster) { state.roster.orEmpty().flatMap { group -> group.players.map { it.headshotUrl } } })
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) { viewModel.pollWhileVisible() }
    }
    val team = state.displayTeam
    val hero = team?.heroColor() ?: MaterialTheme.colorScheme.primaryContainer
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val listState = rememberLazyListState()
    val density = LocalDensity.current
    // The bar shows the team's name once the name in the header has scrolled under it.
    val pastHero by remember(density) {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 ||
                listState.firstVisibleItemScrollOffset > with(density) { 72.dp.roundToPx() }
        }
    }
    LightStatusBarIcons(enabled = true)

    fun openPlayer(id: String, name: String, headshot: String?, position: String? = null, jersey: String? = null) =
        onOpenPlayer(PlayerRef(id, name, headshot, position, jersey, team))

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        // Top bar in the team's color, continuing into the header below it.
        Column(Modifier.fillMaxWidth().background(hero)) {
            Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
                if (team != null) {
                    Row(
                        Modifier
                            .weight(1f)
                            .graphicsLayer { alpha = if (pastHero) 1f else 0f },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TeamLogo(team, size = 26.dp)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            state.profile?.displayName ?: team.name,
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    IconButton(onClick = { onCompare(team) }) {
                        Icon(Icons.AutoMirrored.Outlined.CompareArrows, contentDescription = "Compare with another team", tint = Color.White)
                    }
                    // A toggle, so screen readers say "Follow team, on/off", with a tick to match.
                    val haptics = LocalHapticFeedback.current
                    IconToggleButton(
                        checked = isFavorite,
                        onCheckedChange = { follow ->
                            haptics.performHapticFeedback(if (follow) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff)
                            onToggleFavorite(team)
                        },
                    ) {
                        Icon(
                            if (isFavorite) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                            contentDescription = "Follow team",
                            tint = Color.White,
                        )
                    }
                }
            }
        }

        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = viewModel::refresh,
            modifier = Modifier.fillMaxSize(),
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = bottomListPadding()),
            ) {
                item(key = "hero", contentType = "hero") { TeamHero(team, state.profile, hero) }
                stickyHeader(key = "tabs", contentType = "tabs") {
                    PillTabs(
                        labels = TeamTab.entries.map { it.label },
                        selected = tab,
                        onSelect = { tab = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.background),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    )
                }
                when (TeamTab.entries[tab]) {
                    TeamTab.Schedule -> scheduleItems(state, onOpenGame, viewModel::retry)
                    TeamTab.Players -> playerStatsItems(state, viewModel::retry) { ref -> openPlayer(ref.id, ref.name, ref.headshotUrl, jersey = ref.jersey) }
                    TeamTab.Stats -> teamStatsItems(state, viewModel::retry)
                    TeamTab.Roster -> rosterItems(state, viewModel::retry) { p -> openPlayer(p.id, p.name, p.headshotUrl, p.position, p.jersey) }
                    TeamTab.Depth -> depthItems(state, viewModel::retry) { id, name, headshot -> openPlayer(id, name, headshot) }
                }
            }
        }
    }
}

/**
 * The top of the page: the team's color, a giant faded logo, and the season at a glance.
 * Every line keeps its place while the profile loads, so nothing jumps when it arrives.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TeamHero(team: Team?, profile: TeamProfile?, hero: Color) {
    val deep = lerp(hero, Color.Black, 0.35f)
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp))
            .background(Brush.verticalGradient(listOf(hero, deep))),
    ) {
        if (team != null) {
            TeamLogo(
                team,
                size = 240.dp,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .offset(x = 70.dp)
                    .alpha(0.13f),
            )
        }
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (team != null) TeamLogo(team, size = 58.dp)
                }
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(
                        profile?.displayName ?: team?.name ?: "",
                        fontFamily = DisplayFamily,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 32.sp,
                        lineHeight = 34.sp,
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        profile?.standing ?: " ",
                        style = MaterialTheme.typography.bodyMedium,
                        color = OnHeroMuted,
                        maxLines = 1,
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
            // Wraps onto a second row when it doesn't fit, rather than scrolling sideways.
            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                HeroStat(team?.record ?: "–", "Record")
                profile?.splits?.forEach { HeroStat(it.summary, it.label) }
                HeroStat(profile?.pointsForPerGame?.let { "%.1f".format(Locale.US, it) } ?: "–", "Pts / game")
                HeroStat(profile?.pointsAgainstPerGame?.let { "%.1f".format(Locale.US, it) } ?: "–", "Allowed")
                HeroStat(profile?.pointDifferential?.let { if (it > 0) "+$it" else "$it" } ?: "–", "Point diff")
                HeroStat(
                    profile?.streak?.takeIf { it != 0 }?.let { if (it > 0) "W$it" else "L${abs(it)}" } ?: "–",
                    "Streak",
                )
            }
        }
    }
}

@Composable
private fun HeroStat(value: String, label: String) {
    Column {
        Text(
            value,
            fontFamily = DisplayFamily,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 26.sp,
            lineHeight = 28.sp,
            color = Color.White,
            maxLines = 1,
        )
        Text(
            label.uppercase(),
            style = OverlineTextStyle.copy(fontSize = 10.sp, letterSpacing = 0.8.sp),
            color = OnHeroMuted,
            maxLines = 1,
        )
    }
}

// ---------------------------------------------------------------------------
// Shared bits for each tab
// ---------------------------------------------------------------------------

private fun LazyListScope.failedItem(what: String, onRetry: () -> Unit) {
    item(key = "failed", contentType = "failed") {
        EmptyState(
            icon = Icons.Outlined.CloudOff,
            title = "Couldn't load the $what",
            message = "Check your connection and try again.",
            action = { FilledTonalButton(onClick = onRetry) { Text("Try again") } },
        )
    }
}

private fun LazyListScope.rowSkeletons(count: Int, height: Int = 64) {
    items(count, key = { "skeleton$it" }, contentType = { "skeleton" }) { i ->
        Box(
            Modifier
                .padding(horizontal = 16.dp, vertical = 1.dp)
                .fillMaxWidth()
                .height(height.dp)
                .clip(groupedShape(i, count))
                .background(SportsHubTheme.colors.card)
                .shimmer(),
        )
    }
}

/** A list row drawn as part of one rounded card with the rows around it. */
@Composable
private fun GroupedRow(
    index: Int,
    count: Int,
    onClick: (() -> Unit)?,
    highlighted: Boolean = false,
    content: @Composable () -> Unit,
) {
    val shape = groupedShape(index, count)
    Box(
        Modifier
            .padding(horizontal = 16.dp, vertical = 1.dp)
            .fillMaxWidth()
            .clip(shape)
            .background(SportsHubTheme.colors.card)
            .then(if (highlighted) Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary, shape) else Modifier)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        content()
    }
}

// ---------------------------------------------------------------------------
// Schedule
// ---------------------------------------------------------------------------

private sealed interface ScheduleItem {
    val key: String
    data class Played(val entry: ScheduleEntry) : ScheduleItem {
        override val key get() = "game_${entry.game.id}"
    }
    data class Bye(val week: Int) : ScheduleItem {
        override val key get() = "bye_$week"
    }
}

/** Games in date order, with a bye row for each week the team doesn't play. */
private fun scheduleGroups(entries: List<ScheduleEntry>, postseasonName: String): List<Pair<String, List<ScheduleItem>>> {
    val regular = entries.filter { it.seasonType == 2 }
    val playoffs = entries.filter { it.seasonType == 3 }
    val regularItems = buildList<ScheduleItem> {
        var expected = 1
        regular.forEach { entry ->
            val week = entry.week
            if (week != null) {
                while (expected < week) add(ScheduleItem.Bye(expected++))
                expected = week + 1
            }
            add(ScheduleItem.Played(entry))
        }
    }
    return listOfNotNull(
        ("Regular season" to regularItems).takeIf { regularItems.isNotEmpty() },
        (postseasonName to playoffs.map { ScheduleItem.Played(it) }).takeIf { playoffs.isNotEmpty() },
    )
}

private fun LazyListScope.scheduleItems(state: TeamUiState, onOpenGame: (Game) -> Unit, onRetry: () -> Unit) {
    val schedule = state.schedule
    val teamId = state.team?.id ?: state.profile?.team?.id
    when {
        schedule == null && TeamPart.Schedule.id in state.failed -> failedItem("schedule", onRetry)
        schedule == null -> rowSkeletons(8)
        schedule.entries.isEmpty() -> item(key = "empty") {
            EmptyState(Icons.Outlined.EventBusy, "No games yet", "The schedule hasn't been released.")
        }
        else -> {
            val nextId = schedule.entries.firstOrNull { it.game.status !is GameStatus.Final }?.game?.id
            scheduleGroups(schedule.entries, state.sport.postseasonName).forEach { (title, items) ->
                item(key = "h_$title", contentType = "header") {
                    SectionHeader(
                        listOfNotNull(schedule.season?.let { state.sport.seasonName(it) }, title).joinToString(" "),
                        count = items.count { it is ScheduleItem.Played },
                        padding = PaddingValues(start = 20.dp, end = 12.dp, top = 6.dp, bottom = 8.dp),
                    )
                }
                itemsIndexed(items, key = { _, it -> it.key }, contentType = { _, it -> if (it is ScheduleItem.Bye) "bye" else "game" }) { i, item ->
                    when (item) {
                        is ScheduleItem.Bye -> GroupedRow(i, items.size, onClick = null) { ByeRow(item.week) }
                        is ScheduleItem.Played -> {
                            val game = item.entry.game
                            GroupedRow(i, items.size, onClick = { onOpenGame(game) }, highlighted = game.id == nextId && !game.isLive) {
                                ScheduleRow(item.entry, teamId, isNext = game.id == nextId)
                            }
                        }
                    }
                }
                item(key = "gap_$title") { Spacer(Modifier.height(12.dp)) }
            }
        }
    }
}

@Composable
private fun ByeRow(week: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            "WK $week",
            style = OverlineTextStyle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(60.dp),
        )
        Text("Bye week", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ScheduleRow(entry: ScheduleEntry, teamId: String?, isNext: Boolean) {
    val game = entry.game
    val isHome = game.home.team.id == teamId
    val us = if (isHome) game.home else game.away
    val them = if (isHome) game.away else game.home
    val date = game.startTime.atZone(ZoneId.systemDefault())
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.width(60.dp)) {
            // Football: the week, then the day. Sports played by day: the weekday, then the date.
            val byDay = entry.week == null && entry.label.isBlank()
            Text(
                entry.week?.let { "WK $it" } ?: entry.label.uppercase().ifBlank { date.format(dayOfWeek).uppercase() },
                style = OverlineTextStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                if (byDay) date.format(shortDate) else "${date.format(dayOfWeek)} ${date.format(shortDate)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        Text(
            if (isHome) "vs" else "@",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(24.dp),
        )
        TeamLogo(them.team, size = 30.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                them.team.name,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (isNext && game.status == GameStatus.Scheduled) {
                Spacer(Modifier.height(3.dp))
                Tag("NEXT UP", MaterialTheme.colorScheme.primary)
            }
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            when (val status = game.status) {
                is GameStatus.Final -> {
                    val mine = us.score ?: 0
                    val theirs = them.score ?: 0
                    val (letter, color) = when {
                        us.isWinner || mine > theirs -> "W" to SportsHubTheme.colors.win
                        them.isWinner || theirs > mine -> "L" to SportsHubTheme.colors.loss
                        else -> "T" to MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(letter, fontFamily = DisplayFamily, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, color = color)
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "$mine–$theirs",
                            fontFamily = DisplayFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            style = LocalTextStyle.current.copy(fontFeatureSettings = "tnum"),
                        )
                    }
                    if (status.detail.contains("OT")) {
                        Text(status.detail, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                is GameStatus.Live -> {
                    LiveBadge()
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "${us.score ?: 0}–${them.score ?: 0}",
                        fontFamily = DisplayFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                    )
                }
                GameStatus.Scheduled -> {
                    Text(date.format(kickoff), style = MaterialTheme.typography.titleSmall)
                    game.broadcasts.firstOrNull()?.let {
                        Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    }
                }
                is GameStatus.Postponed -> Text(
                    status.detail,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End,
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Player season stats
// ---------------------------------------------------------------------------

private fun LazyListScope.playerStatsItems(state: TeamUiState, onRetry: () -> Unit, onOpenPlayer: (PlayerRef) -> Unit) {
    val stats = state.playerStats
    when {
        stats == null && PLAYERS_PART in state.failed -> failedItem("player stats", onRetry)
        stats == null -> {
            item(key = "p_caption") { Caption("Adding up this season's box scores…") }
            val titles = if (state.sport.isBasketball) listOf("Per game", "Totals") else listOf("Passing", "Rushing", "Receiving")
            items(titles.size, key = { "p_skeleton$it" }) {
                Box(Modifier.padding(bottom = 12.dp)) { SectionSkeleton(titles[it], rows = 4) }
            }
        }
        stats.gamesCounted == 0 || stats.tables.isEmpty() -> item(key = "p_empty") {
            EmptyState(Icons.Outlined.QueryStats, "No stats yet", "Player stats show up once the team has played a game.")
        }
        else -> {
            item(key = "p_caption") {
                val games = "${stats.gamesCounted} game${if (stats.gamesCounted == 1) "" else "s"}"
                Caption(
                    if (state.sport.isBasketball) {
                        "Averages and totals from $games. Tap a player for their profile."
                    } else {
                        "Season totals from $games. Tap a player for their profile."
                    },
                )
            }
            stats.tables.forEach { table ->
                item(key = "p_${table.key}", contentType = "stat_table") {
                    var expanded by rememberSaveable(table.key) { mutableStateOf(false) }
                    Box(Modifier.padding(bottom = 12.dp)) {
                        Section(table.title) {
                            StatTable(table, onOpenPlayer = onOpenPlayer, limit = if (expanded) Int.MAX_VALUE else TABLE_PREVIEW)
                            if (table.players.size > TABLE_PREVIEW) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                    TextButton(onClick = { expanded = !expanded }) {
                                        Text(if (expanded) "Show fewer" else "Show all ${table.players.size}")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Caption(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 2.dp, bottom = 10.dp),
    )
}

// ---------------------------------------------------------------------------
// Team season stats
// ---------------------------------------------------------------------------

private fun LazyListScope.teamStatsItems(state: TeamUiState, onRetry: () -> Unit) {
    val stats = state.teamStats
    when {
        stats == null && TeamPart.TeamStats.id in state.failed -> failedItem("team stats", onRetry)
        stats == null -> items(3, key = { "s_skeleton$it" }) {
            val titles = if (state.sport.isBasketball) listOf("Scoring", "Ball movement", "Rebounding") else listOf("Offense", "Downs and discipline", "Defense")
            Box(Modifier.padding(bottom = 12.dp)) { SectionSkeleton(titles[it], rows = 5) }
        }
        stats.groups.isEmpty() -> item(key = "s_empty") {
            EmptyState(Icons.Outlined.QueryStats, "No stats yet", "Team stats show up once the season starts.")
        }
        else -> {
            stats.season?.let { season ->
                item(key = "s_caption") { Caption("${state.sport.seasonName(season)} season totals, with per-game averages.") }
            }
            stats.groups.forEach { group ->
                item(key = "s_${group.key}", contentType = "stat_group") {
                    Box(Modifier.padding(bottom = 12.dp)) {
                        Section(group.title) {
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                group.stats.forEach { stat ->
                                    Row(
                                        Modifier
                                            .fillMaxWidth()
                                            .clip(MaterialTheme.shapes.small)
                                            .padding(vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            stat.label,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.weight(1f),
                                        )
                                        Column(horizontalAlignment = Alignment.End) {
                                            Text(
                                                stat.value,
                                                fontFamily = DisplayFamily,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 20.sp,
                                                style = LocalTextStyle.current.copy(fontFeatureSettings = "tnum"),
                                            )
                                            stat.perGame?.let {
                                                Text(
                                                    "$it / game",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Roster
// ---------------------------------------------------------------------------

private fun LazyListScope.rosterItems(state: TeamUiState, onRetry: () -> Unit, onOpenPlayer: (RosterPlayer) -> Unit) {
    val roster = state.roster
    when {
        roster == null && TeamPart.Roster.id in state.failed -> failedItem("roster", onRetry)
        roster == null -> rowSkeletons(10, height = 68)
        roster.isEmpty() -> item(key = "r_empty") {
            EmptyState(Icons.Outlined.Groups, "No roster", "ESPN hasn't listed this team's players.")
        }
        else -> roster.forEach { group ->
            item(key = "r_h_${group.key}", contentType = "header") {
                SectionHeader(group.title, count = group.players.size, padding = PaddingValues(start = 20.dp, end = 12.dp, top = 6.dp, bottom = 8.dp))
            }
            itemsIndexed(group.players, key = { _, p -> "r_${group.key}_${p.id}" }, contentType = { _, _ -> "player" }) { i, player ->
                GroupedRow(i, group.players.size, onClick = { onOpenPlayer(player) }) { RosterRow(player) }
            }
            item(key = "r_gap_${group.key}") { Spacer(Modifier.height(12.dp)) }
        }
    }
}

@Composable
private fun RosterRow(player: RosterPlayer) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        PlayerHeadshot(player.headshotUrl, player.name, size = 44.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    player.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                player.injury?.let {
                    Spacer(Modifier.width(6.dp))
                    Tag(it.uppercase(), injuryColor(it))
                }
            }
            val details = listOfNotNull(
                player.age?.let { "Age $it" },
                player.height,
                player.weight,
                player.experience?.let { if (it == 0) "Rookie" else "$it yr${if (it == 1) "" else "s"}" },
                player.college,
            ).joinToString(" · ")
            if (details.isNotEmpty()) {
                Text(
                    details,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                player.jersey?.let { "#$it" } ?: "",
                fontFamily = DisplayFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
            )
            Text(
                player.position.orEmpty(),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun injuryColor(status: String): Color = when {
    status.contains("out", ignoreCase = true) || status.contains("reserve", ignoreCase = true) -> SportsHubTheme.colors.live
    status.contains("susp", ignoreCase = true) -> SportsHubTheme.colors.live
    else -> MaterialTheme.colorScheme.tertiary
}

/** "Questionable" as "Q", "Injured Reserve" as "IR"... short enough for a depth chart. */
private fun injuryShort(status: String): String = when {
    status.equals("Questionable", true) -> "Q"
    status.equals("Doubtful", true) -> "D"
    status.equals("Out", true) -> "O"
    status.contains("Reserve", true) -> "IR"
    status.contains("Susp", true) -> "SUSP"
    status.contains("Probable", true) -> "P"
    else -> status.take(4).uppercase()
}

// ---------------------------------------------------------------------------
// Depth chart
// ---------------------------------------------------------------------------

private fun LazyListScope.depthItems(
    state: TeamUiState,
    onRetry: () -> Unit,
    onOpenPlayer: (id: String, name: String, headshot: String?) -> Unit,
) {
    val charts = state.depth
    when {
        charts == null && TeamPart.Depth.id in state.failed -> failedItem("depth chart", onRetry)
        charts == null -> rowSkeletons(10, height = 72)
        charts.isEmpty() -> item(key = "d_empty") {
            EmptyState(Icons.Outlined.Groups, "No depth chart", "ESPN hasn't posted one for this team.")
        }
        else -> item(key = "d_charts", contentType = "depth") {
            // One item holding the chart switcher and the chart, so switching keeps its place.
            DepthCharts(charts, state.roster, onOpenPlayer)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DepthCharts(
    charts: List<DepthChart>,
    roster: List<com.sportshub.app.model.RosterGroup>?,
    onOpenPlayer: (id: String, name: String, headshot: String?) -> Unit,
) {
    var selected by rememberSaveable { mutableIntStateOf(0) }
    val chart = charts.getOrElse(selected) { charts.first() }
    val headshots = remember(roster) { roster.orEmpty().flatMap { it.players }.associate { it.id to it.headshotUrl } }
    Column {
        if (charts.size > 1) {
            PillTabs(
                labels = charts.map { it.name },
                selected = selected.coerceIn(0, charts.lastIndex),
                onSelect = { selected = it },
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 12.dp),
            )
        }
        Column(
            Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth()
                .cardSurface(),
        ) {
            chart.positions.forEachIndexed { i, position ->
                if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(SportsHubTheme.colors.stroke))
                val starter = position.players.first()
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onOpenPlayer(starter.id, starter.name, headshots[starter.id]) }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .width(48.dp)
                            .clip(MaterialTheme.shapes.extraSmall)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                            .padding(vertical = 6.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            position.abbreviation,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    PlayerHeadshot(headshots[starter.id], starter.name, size = 36.dp)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                starter.name,
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                            starter.injury?.let {
                                Spacer(Modifier.width(6.dp))
                                Tag(injuryShort(it), injuryColor(it))
                            }
                        }
                        val backups = position.players.drop(1).take(3)
                        if (backups.isNotEmpty()) {
                            FlowRow {
                                backups.forEachIndexed { b, player ->
                                    Text(
                                        "${b + 2}. ${player.name}${player.injury?.let { " (${injuryShort(it)})" } ?: ""}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        modifier = Modifier
                                            .clip(CircleShape)
                                            .clickable { onOpenPlayer(player.id, player.name, headshots[player.id]) }
                                            .padding(end = 10.dp, top = 2.dp, bottom = 2.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
