package com.sportshub.app.ui.scores

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListLayoutInfo
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.sportshub.app.model.Game
import com.sportshub.app.model.Leader
import com.sportshub.app.model.PlayerRef
import com.sportshub.app.model.PlayerStatTable
import com.sportshub.app.model.Team
import com.sportshub.app.model.TeamPlayerStats
import com.sportshub.app.ui.components.PlayerHeadshot
import com.sportshub.app.ui.components.TeamLogo
import com.sportshub.app.ui.components.heroColor
import com.sportshub.app.ui.theme.OverlineTextStyle
import com.sportshub.app.ui.theme.SportsHubTheme
import com.sportshub.app.ui.theme.TableNumberStyle

private val RowHeight = 40.dp
private val HeaderHeight = 32.dp
private val HeadshotSizeInTable = 26.dp

/** Room between one stat column and the next, on top of the column's widest text. */
private val ColumnGap = 11.dp
private val MinColumnWidth = 30.dp

/**
 * When the numbers scroll, the room left at the right edge for a chevron that says so.
 * It's narrower than [ColumnGap], so no sliver of the next column's text shows there.
 */
private val ScrollPeek = 10.dp

private val CardRadius = 24.dp
private val CardPadding = 18.dp

/** Height of the team switch in the box score card, and pinned under the top bar. */
private val SwitchHeight = 44.dp
private val PinnedSwitchHeight = 40.dp

/** Key of the box score's first list item: its title and the team switch. */
internal const val PLAYER_STATS_KEY = "players"
private const val TABLE_KEY_PREFIX = "table_"

/** The team whose box score shows: the one picked, else the first with stats, else the away team. */
internal fun boxScoreTeam(stats: List<TeamPlayerStats>, game: Game, selectedTeamId: String?): Team {
    val teams = listOf(game.away.team, game.home.team)
    return teams.firstOrNull { it.id == selectedTeamId }
        ?: teams.firstOrNull { t -> stats.any { it.teamId == t.id } }
        ?: game.away.team
}

/**
 * Box score for a live or finished game, as list items that together draw one card:
 * the title and a switch between the two teams, then each of the selected team's tables
 * (passing, rushing, receiving, defense, kicking), each led by a small header row that
 * names it next to its column labels. Separate items let the list build only the tables
 * on screen; [itemSpacing] is the gap the list leaves between items, which the card
 * bridges so the pieces read as one.
 *
 * Swiping sideways anywhere on the card switches teams too, and once the switch scrolls
 * under the top bar a copy pins there ([PinnedTeamSwitch]). Tables keep their place in the
 * list when the team changes, so after a swipe on the rushing table you're looking at the
 * other team's rushing.
 */
fun LazyListScope.playerStatsItems(
    stats: List<TeamPlayerStats>,
    game: Game,
    selectedTeamId: String?,
    onSelectTeam: (String) -> Unit,
    onOpenPlayer: (PlayerRef) -> Unit,
    itemSpacing: Dp,
) {
    val teams = listOf(game.away.team, game.home.team)
    val selected = boxScoreTeam(stats, game, selectedTeamId)
    val tables = stats.firstOrNull { it.teamId == selected.id }?.tables.orEmpty()
    val selectedIndex = teams.indexOf(selected)
    val onSwipe: (Int) -> Unit = { step -> teams.getOrNull(selectedIndex + step)?.let { onSelectTeam(it.id) } }

    item(key = PLAYER_STATS_KEY, contentType = "players_header") {
        Column(
            Modifier
                .joinedCard(first = true, last = tables.isEmpty(), spacingBelow = itemSpacing)
                .then(rememberTeamSwipe(onSwipe))
                .padding(start = CardPadding, end = CardPadding, top = CardPadding, bottom = if (tables.isEmpty()) CardPadding else 0.dp),
        ) {
            Text(
                "Player stats",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.height(14.dp))
            TeamSwitch(teams, selected.id, onSelectTeam)
            if (tables.isEmpty()) {
                Spacer(Modifier.height(16.dp))
                Text(
                    "No player stats yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    // Keyed by table, not team, so switching teams swaps each table in place instead of
    // replacing them all and losing the reader's spot.
    itemsIndexed(tables, key = { _, table -> TABLE_KEY_PREFIX + table.key }, contentType = { _, _ -> "stat_table" }) { i, table ->
        TeamTableCard(
            current = TeamTable(selected, table),
            teams = teams,
            last = i == tables.lastIndex,
            itemSpacing = itemSpacing,
            swipe = rememberTeamSwipe(onSwipe),
            onOpenPlayer = onOpenPlayer,
        )
    }
}

@Immutable
private data class TeamTable(val team: Team, val table: PlayerStatTable)

/** One box score table, sliding the other team's in from the side it was swiped from. */
@Composable
private fun TeamTableCard(
    current: TeamTable,
    teams: List<Team>,
    last: Boolean,
    itemSpacing: Dp,
    swipe: Modifier,
    onOpenPlayer: (PlayerRef) -> Unit,
) {
    Box(
        Modifier
            .joinedCard(first = false, last = last, spacingBelow = itemSpacing)
            .then(swipe)
            // Keep the sliding tables inside the card's border.
            .padding(horizontal = 1.dp)
            .clipToBounds(),
    ) {
        AnimatedContent(
            targetState = current,
            // Live updates to the same team's numbers swap in place; only a team change slides.
            contentKey = { it.team.id },
            transitionSpec = {
                fun position(team: Team) = teams.indexOfFirst { it.id == team.id }
                val sign = if (position(targetState.team) > position(initialState.team)) 1 else -1
                (slideInHorizontally { sign * it / 4 } + fadeIn())
                    .togetherWith(slideOutHorizontally { -sign * it / 4 } + fadeOut())
            },
            label = "team table",
        ) { (team, table) ->
            Box(Modifier.padding(start = CardPadding - 1.dp, end = CardPadding - 1.dp, top = 6.dp, bottom = if (last) 8.dp else 0.dp)) {
                StatTable(table, title = table.title, onOpenPlayer = { onOpenPlayer(it.copy(team = team)) })
            }
        }
    }
}

/**
 * The card behind the box score's list items. The first item gets the rounded top, the
 * last the rounded bottom, and every item but the last paints down across the list's
 * [spacingBelow] gap to the next one, so the pieces join into one card with one border.
 */
@Composable
private fun Modifier.joinedCard(first: Boolean, last: Boolean, spacingBelow: Dp): Modifier {
    val fill = SportsHubTheme.colors.card
    val stroke = SportsHubTheme.colors.stroke
    return this
        .padding(horizontal = 16.dp)
        .fillMaxWidth()
        .drawBehind {
            val radius = CardRadius.toPx()
            val line = 1.dp.toPx()
            val visibleBottom = if (last) size.height else size.height + spacingBelow.roundToPx()
            // A full rounded card, stretched past the ends this piece doesn't own and cut to
            // its own slice, so a middle piece shows only the straight sides of the border.
            val top = if (first) 0f else -2 * radius
            val bottom = if (last) size.height else visibleBottom + 2 * radius
            clipRect(top = 0f, bottom = visibleBottom) {
                drawRoundRect(fill, Offset(0f, top), Size(size.width, bottom - top), CornerRadius(radius))
                drawRoundRect(
                    stroke,
                    Offset(line / 2, top + line / 2),
                    Size(size.width - line, bottom - top - line),
                    CornerRadius(radius - line / 2),
                    style = Stroke(line),
                )
            }
        }
}

/**
 * Whether the team switch should pin under the top bar: the box score's own switch has
 * scrolled up under the bar, whose bottom edge is [barBottom] pixels down the list, while
 * its tables still fill the screen below. [switchInset] is how far up from the bottom of
 * the box score's first item the switch's middle sits, and [pinnedHeight] the height of
 * the pinned copy, which lets go once the last table is about to slide under it.
 */
internal fun teamSwitchPinned(info: LazyListLayoutInfo, barBottom: Int, switchInset: Int, pinnedHeight: Int): Boolean {
    val items = info.visibleItemsInfo
    val lastTable = items.lastOrNull { (it.key as? String)?.startsWith(TABLE_KEY_PREFIX) == true } ?: return false
    if (lastTable.offset + lastTable.size < barBottom + pinnedHeight) return false
    // Tables on screen but not the header above them: it has scrolled off the top.
    val header = items.firstOrNull { it.key == PLAYER_STATS_KEY } ?: return true
    return header.offset + header.size - switchInset < barBottom
}

/** Pixels from the bottom of the box score's first item up to the middle of its team switch. */
@Composable
internal fun teamSwitchInset(): Int = with(LocalDensity.current) { (SwitchHeight / 2).roundToPx() }

/** Height of the pinned team switch band, in pixels. */
@Composable
internal fun pinnedTeamSwitchHeight(): Int = with(LocalDensity.current) { (PinnedSwitchHeight + 12.dp).roundToPx() }

/**
 * The team switch again, in a band under the top bar, shown while the box score is on
 * screen but its own switch has scrolled away, so changing teams never needs a scroll back up.
 */
@Composable
internal fun PinnedTeamSwitch(visible: Boolean, teams: List<Team>, selectedId: String, onSelect: (String) -> Unit) {
    AnimatedVisibility(
        visible = visible,
        enter = expandVertically(tween(220), expandFrom = Alignment.Top) + fadeIn(tween(220)),
        exit = shrinkVertically(tween(180), shrinkTowards = Alignment.Top) + fadeOut(tween(180)),
    ) {
        Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background)) {
            TeamSwitch(
                teams,
                selectedId,
                onSelect,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 10.dp),
                height = PinnedSwitchHeight,
                // On the page background rather than a card, so the track takes the card color.
                track = SportsHubTheme.colors.card,
            )
            HorizontalDivider(color = SportsHubTheme.colors.stroke)
        }
    }
}

/**
 * A segmented switch between the two teams: one track, with the picked team's half
 * filled in its color. The fill slides across when the team changes.
 */
@Composable
private fun TeamSwitch(
    teams: List<Team>,
    selectedId: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = SwitchHeight,
    track: Color = SportsHubTheme.colors.cardHigh,
) {
    val selectedIndex = teams.indexOfFirst { it.id == selectedId }.coerceAtLeast(0)
    val thumbColor by animateColorAsState(teams[selectedIndex].heroColor(), tween(240), label = "switchColor")
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(CircleShape)
            .background(track)
            .border(1.dp, SportsHubTheme.colors.stroke, CircleShape)
            .padding(3.dp)
            .selectableGroup(),
    ) {
        val segment = maxWidth / teams.size
        val thumbOffset by animateDpAsState(
            segment * selectedIndex,
            spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow),
            label = "switchThumb",
        )
        // offset() follows the layout direction, so the fill tracks its team right to left too.
        Box(
            Modifier
                .offset(x = thumbOffset)
                .width(segment)
                .fillMaxHeight()
                .clip(CircleShape)
                .background(thumbColor),
        )
        Row(Modifier.fillMaxSize()) {
            teams.forEachIndexed { i, team ->
                val isSelected = i == selectedIndex
                val textColor by animateColorAsState(
                    if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                    tween(200),
                    label = "switchText",
                )
                Row(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(CircleShape)
                        // Read out as a tab, with its selected state.
                        .selectable(selected = isSelected, role = Role.Tab, onClick = { onSelect(team.id) })
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    TeamLogo(team, size = 22.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        team.name,
                        color = textColor,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** One box score table, with tappable rows that open the player. [title] names it in the header row. */
@Composable
internal fun StatTable(
    table: PlayerStatTable,
    title: String? = null,
    onOpenPlayer: ((PlayerRef) -> Unit)? = null,
    limit: Int = Int.MAX_VALUE,
) {
    val rows = remember(table, limit) {
        table.players.take(limit).map { p ->
            GridRow(key = p.athleteId, label = p.name, headshotUrl = p.headshotUrl, values = p.values)
        }
    }
    val byId = remember(table) { table.players.associateBy { it.athleteId } }
    val onRowClick: ((GridRow) -> Unit)? = if (onOpenPlayer == null) {
        null
    } else {
        { row ->
            val p = byId[row.key]
            if (p != null) onOpenPlayer(PlayerRef(p.athleteId, p.name, p.headshotUrl, jersey = p.jersey))
        }
    }
    StatGrid(columns = table.columns, rows = rows, showHeadshots = true, title = title, onRowClick = onRowClick)
}

/** A row of a [StatGrid]: a name (or season) on the left and its numbers on the right. */
@Immutable
data class GridRow(
    val key: String,
    val label: String,
    val sub: String? = null,
    val headshotUrl: String? = null,
    val values: List<String>,
    /** Drawn bold, like a career total. */
    val emphasized: Boolean = false,
)

/**
 * A stat table. Names stay pinned on the left; when the columns don't fit the screen
 * (passing has eight), the numbers scroll sideways under the header, and a fade on the
 * right edge shows there's more. [title], if given, sits above the names in the header row.
 *
 * Each column is as wide as its widest label or number at the current text size, so
 * labels like INT and LONG never clip, even with the phone's font size turned up.
 */
@Composable
internal fun StatGrid(
    columns: List<String>,
    rows: List<GridRow>,
    showHeadshots: Boolean,
    title: String? = null,
    nameWidth: Dp = if (showHeadshots) 136.dp else 92.dp,
    onRowClick: ((GridRow) -> Unit)? = null,
) {
    val headerStyle = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val baseWidths = remember(columns, rows, headerStyle, density) {
        val bold = TableNumberStyle.copy(fontWeight = FontWeight.Bold)
        fun widthOf(text: String, style: TextStyle) =
            measurer.measure(text, style, maxLines = 1, softWrap = false).size.width
        columns.indices.map { i ->
            // Figures are tabular, so only the longest values in a column can be its widest.
            val values = rows.mapNotNull { row -> row.values.getOrNull(i)?.let { it to row.emphasized } }
            val longest = values.maxOfOrNull { it.first.length } ?: 0
            val widest = values
                .filter { it.first.length == longest }
                .distinct()
                .maxOfOrNull { (text, emphasized) -> widthOf(text, if (emphasized) bold else TableNumberStyle) } ?: 0
            with(density) { (maxOf(widthOf(columns[i], headerStyle), widest).toDp() + ColumnGap).coerceAtLeast(MinColumnWidth) }
        }
    }
    val divider = SportsHubTheme.colors.stroke
    val strong = MaterialTheme.colorScheme.onSurface
    Row(Modifier.fillMaxWidth()) {
        Column(Modifier.width(nameWidth)) {
            Box(Modifier.height(HeaderHeight), contentAlignment = Alignment.CenterStart) {
                if (title != null) {
                    Text(
                        title.uppercase(),
                        style = OverlineTextStyle,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.semantics { heading() },
                    )
                }
            }
            rows.forEach { row ->
                HorizontalDivider(color = divider)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(RowHeight)
                        .then(if (onRowClick != null) Modifier.clickable { onRowClick(row) } else Modifier),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (showHeadshots) {
                        PlayerHeadshot(row.headshotUrl, row.label, size = HeadshotSizeInTable)
                        Spacer(Modifier.width(8.dp))
                    }
                    Column {
                        Text(
                            row.label,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (row.emphasized || !showHeadshots) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        row.sub?.let {
                            Text(
                                it,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
        BoxWithConstraints(Modifier.weight(1f)) {
            val total = baseWidths.fold(0.dp) { acc, w -> acc + w }
            val scale = when {
                total <= 0.dp -> 1f
                // They all fit: spread them across the width.
                total <= maxWidth -> maxWidth / total
                // Too many to fit, so the numbers scroll. Stretch the columns a touch so the
                // last whole one ends just before the chevron at the edge, instead of the
                // edge cutting a label like INT or LONG in half.
                else -> {
                    val room = maxWidth - ScrollPeek
                    var fit = 0.dp
                    for (w in baseWidths) {
                        if (fit + w > room) break
                        fit += w
                    }
                    if (fit > 0.dp) room / fit else 1f
                }
            }
            val widths = baseWidths.map { it * scale }
            val fullWidth = widths.fold(0.dp) { a, w -> a + w }
            val scroll = rememberScrollState()
            Column(Modifier.horizontalScroll(scroll)) {
                Row(Modifier.height(HeaderHeight), verticalAlignment = Alignment.CenterVertically) {
                    columns.forEachIndexed { i, label ->
                        Text(
                            label,
                            Modifier.width(widths[i]),
                            textAlign = TextAlign.End,
                            style = headerStyle,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
                }
                rows.forEach { row ->
                    HorizontalDivider(Modifier.width(fullWidth), color = divider)
                    Row(
                        Modifier
                            .height(RowHeight)
                            .then(if (onRowClick != null) Modifier.clickable { onRowClick(row) } else Modifier),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        row.values.forEachIndexed { i, value ->
                            if (i < widths.size) {
                                Text(
                                    value,
                                    Modifier.width(widths[i]),
                                    textAlign = TextAlign.End,
                                    style = TableNumberStyle,
                                    fontWeight = if (row.emphasized) FontWeight.Bold else null,
                                    color = strong,
                                    maxLines = 1,
                                    softWrap = false,
                                )
                            }
                        }
                    }
                }
            }
            if (scroll.canScrollForward) {
                val card = SportsHubTheme.colors.card
                val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
                val fade = remember(card, rtl) {
                    val stops = listOf(card.copy(alpha = 0f), card)
                    Brush.horizontalGradient(if (rtl) stops.reversed() else stops)
                }
                // Mid-scroll, numbers cut at the edge fade out rather than end in a hard line.
                Box(
                    Modifier
                        .align(Alignment.CenterEnd)
                        .matchParentSize()
                        .padding(start = (maxWidth - ScrollPeek).coerceAtLeast(0.dp))
                        .background(fade),
                )
                Icon(
                    Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 4.dp, y = (HeaderHeight - 16.dp) / 2)
                        .size(16.dp),
                )
            }
        }
    }
}

/**
 * Leaders grouped under each team (away first), e.g. the season's top passer,
 * rusher and receiver for each side before kickoff.
 */
@Composable
fun TeamLeadersContent(
    leaders: List<Leader>,
    game: Game,
    onOpenTeam: ((Team) -> Unit)? = null,
    onOpenPlayer: ((PlayerRef) -> Unit)? = null,
) {
    val groups = listOf(game.away.team, game.home.team)
        .map { team -> team to leaders.filter { it.teamId == team.id } }
        .filter { it.second.isNotEmpty() }
    if (groups.isEmpty()) {
        // No team info on the leaders: show them in one list rather than not at all.
        LeaderRows(leaders, team = null, onOpenPlayer)
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        groups.forEach { (team, teamLeaders) ->
            Column {
                TeamHeader(team, onOpenTeam)
                Spacer(Modifier.height(10.dp))
                LeaderRows(teamLeaders, team, onOpenPlayer)
            }
        }
    }
}

@Composable
private fun TeamHeader(team: Team, onOpenTeam: ((Team) -> Unit)?) {
    Row(
        Modifier
            .clip(CircleShape)
            .then(if (onOpenTeam != null) Modifier.clickable { onOpenTeam(team) } else Modifier)
            .padding(end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TeamLogo(team, size = 26.dp)
        Spacer(Modifier.width(10.dp))
        Text(team.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        team.record?.let {
            Spacer(Modifier.width(8.dp))
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun LeaderRows(leaders: List<Leader>, team: Team?, onOpenPlayer: ((PlayerRef) -> Unit)?) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        leaders.forEach { leader ->
            val open = leader.athleteId?.takeIf { onOpenPlayer != null }?.let { id ->
                { onOpenPlayer?.invoke(PlayerRef(id, leader.athleteName, leader.headshotUrl, leader.position, team = team)) }
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.medium)
                    .background(SportsHubTheme.colors.cardHigh)
                    .then(if (open != null) Modifier.clickable { open() } else Modifier)
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PlayerHeadshot(leader.headshotUrl, leader.athleteName, size = HeadshotSize)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        leader.category.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            leader.athleteName,
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        if (leader.position != null) {
                            Spacer(Modifier.width(6.dp))
                            Text(
                                leader.position,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Text(leader.summary, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

private val HeadshotSize: Dp = 44.dp
