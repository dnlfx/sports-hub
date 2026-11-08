package com.sportshub.app.ui.team

import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.CompareArrows
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sportshub.app.model.Sport
import com.sportshub.app.model.Team
import com.sportshub.app.ui.components.EmptyState
import com.sportshub.app.ui.components.LargeHeaderBar
import com.sportshub.app.ui.components.PillTabs
import com.sportshub.app.ui.components.SectionHeader
import com.sportshub.app.ui.components.SportMenuButton
import com.sportshub.app.ui.components.TeamLogo
import com.sportshub.app.ui.components.accentColor
import com.sportshub.app.ui.components.bottomListPadding
import com.sportshub.app.ui.components.cardSurface
import com.sportshub.app.ui.components.shimmer
import com.sportshub.app.ui.theme.DisplayFamily
import com.sportshub.app.ui.theme.OverlineTextStyle
import com.sportshub.app.ui.theme.SportsHubTheme
import com.sportshub.app.ui.theme.TableNumberStyle

/**
 * Every team in the league as standings: a card per division (a conference in college),
 * teams in order with their rank, record and games behind. Followed teams lead the page.
 * Tap a team for its page.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun TeamsScreen(
    sport: Sport,
    onSelectSport: (Sport) -> Unit,
    viewModel: TeamsViewModel,
    onOpenTeam: (Team) -> Unit,
    /** Followed teams' ids, in the order they were followed. */
    favoriteIds: List<String>,
    onCompare: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(sport) { viewModel.setSport(sport) }

    // "" shows every conference.
    var conference by rememberSaveable(sport) { mutableStateOf("") }
    val listState = rememberLazyListState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeHeaderBar(
                title = "Teams",
                subtitle = "Standings, schedules, rosters and stats",
                scrollBehavior = scrollBehavior,
                actions = {
                    IconButton(onClick = onCompare) {
                        Icon(Icons.AutoMirrored.Outlined.CompareArrows, contentDescription = "Compare teams or players")
                    }
                    SportMenuButton(selected = sport, onSelect = onSelectSport)
                },
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        BoxWithConstraints(
            Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            // One card across on a phone; two or three side by side on tablets, foldables and in landscape.
            val cardColumns = (maxWidth / 420.dp).toInt().coerceIn(1, 3)
            val cardWidth = (maxWidth - 32.dp - 12.dp * (cardColumns - 1)) / cardColumns
            val wide = cardWidth >= 440.dp
            val divisions = state.divisions
            val conferences = remember(divisions) {
                divisions.orEmpty().map { it.group.conference }.filter { it.isNotEmpty() }.distinct()
            }
            // A conference picked before the list changed falls back to all of them.
            val selected = conference.takeIf { it in conferences }.orEmpty()
            val shown = remember(divisions, selected) {
                divisions?.filter { selected.isEmpty() || it.group.conference == selected }
            }
            val followed = remember(favoriteIds) { favoriteIds.toSet() }
            val open = { team: Team -> onOpenTeam(team.copy(record = state.recordOf(team.id))) }

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = bottomListPadding()),
            ) {
                when {
                    shown != null -> {
                        if (conferences.size > 1) {
                            stickyHeader(key = "filter", contentType = "filter") {
                                ConferenceFilter(conferences, selected, sport) { conference = it }
                            }
                        }
                        if (selected.isEmpty()) yourTeamsItems(divisions.orEmpty(), favoriteIds, state, open)
                        if (state.standingsFailed) {
                            item(key = "standings_failed", contentType = "notice") {
                                StandingsNotice(onRetry = viewModel::retry)
                            }
                        }
                        shown.chunked(cardColumns).forEach { row ->
                            item(key = "divisions_${cardColumns}_${row.first().group.key}", contentType = "divisions") {
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalAlignment = Alignment.Top,
                                ) {
                                    row.forEach { division ->
                                        DivisionCard(
                                            division = division,
                                            columns = statColumns(sport, wide),
                                            followed = followed,
                                            onOpenTeam = open,
                                            modifier = Modifier.weight(1f),
                                        )
                                    }
                                    repeat(cardColumns - row.size) { Spacer(Modifier.weight(1f)) }
                                }
                            }
                        }
                        item(key = "legend", contentType = "legend") {
                            Legend(sport, ranked = shown.any { it.ranked }, hasStandings = state.standings.isNotEmpty())
                        }
                    }
                    state.error -> item(key = "error") {
                        EmptyState(
                            icon = Icons.Outlined.CloudOff,
                            title = "Couldn't load teams",
                            message = "Check your connection and try again.",
                            action = { FilledTonalButton(onClick = viewModel::retry) { Text("Try again") } },
                        )
                    }
                    else -> repeat(3) { i ->
                        item(key = "skeleton$i", contentType = "divisions") { DivisionSkeleton(rows = 4) }
                    }
                }
            }
        }
    }
}

/** Pills to narrow the page to one conference, e.g. AFC or NFC. Stays in reach while scrolling. */
@Composable
private fun ConferenceFilter(conferences: List<String>, selected: String, sport: Sport, onSelect: (String) -> Unit) {
    val labels = listOf("All") + conferences.map { conferenceLabel(sport, it) }
    PillTabs(
        labels = labels,
        selected = (conferences.indexOf(selected) + 1).coerceAtLeast(0),
        onSelect = { onSelect(if (it == 0) "" else conferences[it - 1]) },
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
    )
}

/** NBA conferences read better spelled out as pills. */
private fun conferenceLabel(sport: Sport, conference: String): String = when {
    sport.isBasketball && conference == "East" -> "Eastern"
    sport.isBasketball && conference == "West" -> "Western"
    else -> conference
}

// ---------------------------------------------------------------------------
// Your teams
// ---------------------------------------------------------------------------

/** Followed teams as a strip of cards, each with its place in its division. */
private fun LazyListScope.yourTeamsItems(
    divisions: List<DivisionStandings>,
    favoriteIds: List<String>,
    state: TeamsUiState,
    onOpenTeam: (Team) -> Unit,
) {
    if (favoriteIds.isEmpty()) return
    val byId = divisions.flatMap { d -> d.rows.map { it.listing.team.id to (d to it) } }.toMap()
    val mine = favoriteIds.mapNotNull { byId[it] }
    if (mine.isEmpty()) return
    item(key = "yours_header", contentType = "header") { SectionHeader("Your teams") }
    item(key = "yours", contentType = "yours") {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(mine, key = { it.second.listing.team.id }) { (division, row) ->
                YourTeamCard(row, division.group.title, state.recordOf(row.listing.team.id)) { onOpenTeam(row.listing.team) }
            }
        }
    }
}

@Composable
private fun YourTeamCard(row: StandingRow, divisionTitle: String, record: String?, onClick: () -> Unit) {
    val team = row.listing.team
    val color = team.accentColor()
    val place = row.rank?.let { "${ordinal(it)} in $divisionTitle" } ?: divisionTitle
    Column(
        Modifier
            .width(164.dp)
            .cardSurface(MaterialTheme.shapes.large)
            .clickable(onClickLabel = "Open team", role = Role.Button, onClick = onClick)
            .background(Brush.verticalGradient(listOf(color.copy(alpha = 0.24f), Color.Transparent)))
            .clearAndSetSemantics { contentDescription = listOfNotNull(row.listing.displayName, record, place).joinToString(". ") }
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TeamLogo(team, size = 40.dp)
            Spacer(Modifier.weight(1f))
            row.rank?.let { RankBadge(it, leader = it == 1, size = 30.dp) }
        }
        Spacer(Modifier.height(12.dp))
        Text(team.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            record ?: team.abbreviation,
            style = TableNumberStyle.copy(fontWeight = FontWeight.SemiBold),
            maxLines = 1,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            place,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// ---------------------------------------------------------------------------
// Division standings
// ---------------------------------------------------------------------------

/** One number column of the standings, e.g. "GB". [spoken] is how a screen reader says it. */
private class StatColumn(val label: String, val spoken: String, val width: Dp, val value: (StandingRow) -> String?)

private val overallColumn = StatColumn("W-L", "record", 54.dp) { it.standing?.overallRecord() }
private val percentColumn = StatColumn("PCT", "win percentage", 48.dp) { row ->
    row.standing?.let { formatPercent(winPercent(it.wins, it.losses, it.ties)) }
}
private val behindColumn = StatColumn("GB", "games back", 38.dp) { it.gamesBehind }
private val divisionColumn = StatColumn("DIV", "division record", 48.dp) { it.standing?.division }
private val streakColumn = StatColumn("STRK", "streak", 42.dp) { it.standing?.streak }
private val conferenceColumn = StatColumn("CONF", "conference record", 46.dp) { it.standing?.conference }
private val collegeOverallColumn = StatColumn("OVR", "overall record", 50.dp) { it.standing?.overallRecord() }

/**
 * The numbers each sport shows, the one that decides the order first. More fit on a wide card.
 * Pro leagues rank by record and show games behind; college conferences rank by conference record.
 */
private fun statColumns(sport: Sport, wide: Boolean): List<StatColumn> = when {
    sport.isCollegeFootball -> if (wide) listOf(conferenceColumn, collegeOverallColumn, percentColumn, streakColumn)
    else listOf(conferenceColumn, collegeOverallColumn, streakColumn)
    wide -> listOf(overallColumn, percentColumn, behindColumn, divisionColumn, streakColumn)
    else -> listOf(overallColumn, behindColumn, streakColumn)
}

@Composable
private fun DivisionCard(
    division: DivisionStandings,
    columns: List<StatColumn>,
    followed: Set<String>,
    onOpenTeam: (Team) -> Unit,
    modifier: Modifier = Modifier,
) {
    val hasStandings = division.rows.any { it.standing != null }
    Column(modifier.cardSurface(MaterialTheme.shapes.large)) {
        // Title and column labels on one line, like a table header.
        Row(
            Modifier
                .fillMaxWidth()
                .background(SportsHubTheme.colors.cardHigh)
                .padding(start = 16.dp, end = 14.dp, top = 12.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                division.group.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .semantics { heading() },
            )
            if (hasStandings) {
                Row(Modifier.clearAndSetSemantics { }) {
                    columns.forEach { column ->
                        Text(
                            column.label,
                            style = OverlineTextStyle.copy(fontSize = 10.sp, letterSpacing = 0.6.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.End,
                            maxLines = 1,
                            modifier = Modifier.width(column.width),
                        )
                    }
                }
            }
        }
        division.rows.forEachIndexed { index, row ->
            if (index > 0) HorizontalDivider(Modifier.padding(start = 16.dp), color = SportsHubTheme.colors.stroke)
            StandingLine(
                row = row,
                divisionTitle = division.group.title,
                showRank = division.ranked,
                columns = if (hasStandings) columns else emptyList(),
                followed = row.listing.team.id in followed,
                onClick = { onOpenTeam(row.listing.team) },
            )
        }
    }
}

/** One team's line: rank, logo, name, then its numbers in fixed-width columns so they line up. */
@Composable
private fun StandingLine(
    row: StandingRow,
    divisionTitle: String,
    showRank: Boolean,
    columns: List<StatColumn>,
    followed: Boolean,
    onClick: () -> Unit,
) {
    val team = row.listing.team
    val color = team.accentColor()
    val followTint = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.10f)
    val spoken = buildList {
        row.rank?.let { add("${ordinal(it)} in $divisionTitle") }
        add(row.listing.displayName)
        columns.forEach { column -> column.value(row)?.let { add("${column.spoken} $it") } }
        if (followed) add("following")
    }.joinToString(", ")
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = "Open team", role = Role.Button, onClick = onClick)
            .drawBehind {
                if (followed) drawRect(followTint)
                // A wash of the team's color behind its logo.
                drawRect(Brush.horizontalGradient(0f to color.copy(alpha = 0.16f), 0.35f to Color.Transparent, endX = size.width))
            }
            .clearAndSetSemantics { contentDescription = spoken }
            .padding(start = 12.dp, end = 14.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showRank) {
            Box(Modifier.width(26.dp), contentAlignment = Alignment.Center) {
                row.rank?.let { RankBadge(it, leader = it == 1) }
            }
            Spacer(Modifier.width(10.dp))
        } else {
            Spacer(Modifier.width(4.dp))
        }
        TeamLogo(team, size = 28.dp)
        Spacer(Modifier.width(10.dp))
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Text(
                team.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = if (row.rank == 1) FontWeight.Bold else FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (followed) {
                Spacer(Modifier.width(4.dp))
                Icon(Icons.Rounded.Star, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(14.dp))
            }
        }
        columns.forEachIndexed { index, column ->
            val value = column.value(row) ?: "-"
            Text(
                value,
                // The number that decides the order stands out; the rest are supporting detail.
                style = TableNumberStyle.copy(
                    fontSize = if (index == 0) 14.sp else 13.sp,
                    fontWeight = if (index == 0) FontWeight.SemiBold else FontWeight.Normal,
                ),
                color = streakColor(column, value) ?: if (index == 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.End,
                maxLines = 1,
                modifier = Modifier.width(column.width),
            )
        }
    }
}

/** Win streaks in green, losing streaks in red. */
@Composable
private fun streakColor(column: StatColumn, value: String): Color? = when {
    column !== streakColumn -> null
    value.startsWith("W") -> SportsHubTheme.colors.win
    value.startsWith("L") -> SportsHubTheme.colors.loss
    else -> null
}

/** A team's place in its division. The leader's is filled in the accent color. */
@Composable
private fun RankBadge(rank: Int, leader: Boolean, size: Dp = 26.dp) {
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(if (leader) MaterialTheme.colorScheme.primary else SportsHubTheme.colors.cardHigh),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "$rank",
            fontFamily = DisplayFamily,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 16.sp,
            lineHeight = 16.sp,
            color = if (leader) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** Shown when the teams loaded but the standings didn't. */
@Composable
private fun StandingsNotice(onRetry: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .cardSurface(MaterialTheme.shapes.medium)
            .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.CloudOff, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(12.dp))
        Text(
            "Standings didn't load, so teams are listed by name.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onRetry) { Text("Retry") }
    }
}

/** How the order is decided and what the columns mean, under the last card. */
@Composable
private fun Legend(sport: Sport, ranked: Boolean, hasStandings: Boolean) {
    if (!hasStandings) return
    val text = when {
        !ranked -> "Teams are numbered once the season's first games are played."
        sport.isCollegeFootball ->
            "Ranked by conference record, then the conference's tie-breakers. CONF is the record in conference games, OVR the overall record."
        else ->
            "Ranked by win percentage, then the league's tie-breakers. GB is games behind the division leader; STRK is the current streak."
    }
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 4.dp),
    )
}

/** Holds a division card's place while the teams load. */
@Composable
private fun DivisionSkeleton(rows: Int) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .shimmer()
            .cardSurface(MaterialTheme.shapes.large),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(44.dp)
                .background(SportsHubTheme.colors.cardHigh),
        )
        repeat(rows) {
            Row(Modifier.padding(horizontal = 14.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(26.dp).clip(CircleShape).background(SportsHubTheme.colors.cardHigh))
                Spacer(Modifier.width(12.dp))
                Box(Modifier.size(30.dp).clip(CircleShape).background(SportsHubTheme.colors.cardHigh))
                Spacer(Modifier.width(12.dp))
                Box(Modifier.width(120.dp).height(14.dp).clip(MaterialTheme.shapes.extraSmall).background(SportsHubTheme.colors.cardHigh))
            }
        }
    }
}
