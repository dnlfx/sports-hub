package com.sportshub.app.ui.scores

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sportshub.app.model.Game
import com.sportshub.app.model.Leader
import com.sportshub.app.model.PlayerRef
import com.sportshub.app.model.PlayerStatTable
import com.sportshub.app.model.Team
import com.sportshub.app.model.TeamPlayerStats
import com.sportshub.app.ui.components.Pill
import com.sportshub.app.ui.components.PlayerHeadshot
import com.sportshub.app.ui.components.TeamLogo
import com.sportshub.app.ui.theme.SportsHubTheme
import com.sportshub.app.ui.theme.TableNumberStyle

private val RowHeight = 44.dp
private val HeaderHeight = 30.dp

/**
 * Box score for a live or finished game, as list items: a card with a switch between
 * the two teams, then one card per table for that team (passing, rushing, receiving,
 * defense, kicking). Separate items let the list build only the tables on screen.
 */
fun LazyListScope.playerStatsItems(
    stats: List<TeamPlayerStats>,
    game: Game,
    selectedTeamId: String?,
    onSelectTeam: (String) -> Unit,
    onOpenPlayer: (PlayerRef) -> Unit,
) {
    val teams = listOf(game.away.team, game.home.team)
    val selected = teams.firstOrNull { it.id == selectedTeamId }
        ?: teams.firstOrNull { t -> stats.any { it.teamId == t.id } }
        ?: game.away.team
    val tables = stats.firstOrNull { it.teamId == selected.id }?.tables.orEmpty()

    item(key = "players", contentType = "players_header") {
        Section("Player stats") {
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
    items(tables, key = { "table_${selected.id}_${it.key}" }, contentType = { "stat_table" }) { table ->
        Section(table.title, trailing = { TeamLogo(selected, size = 24.dp, contentDescription = selected.name) }) {
            StatTable(table, onOpenPlayer = { onOpenPlayer(it.copy(team = selected)) })
        }
    }
}

/** Two pills, one per team, each with its logo. */
@Composable
private fun TeamSwitch(teams: List<Team>, selectedId: String, onSelect: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        teams.forEach { team ->
            Pill(
                label = team.name,
                selected = selectedId == team.id,
                onClick = { onSelect(team.id) },
                modifier = Modifier.weight(1f),
                leading = { TeamLogo(team, size = 20.dp) },
            )
        }
    }
}

/** One player's box score table, with tappable rows that open the player. */
@Composable
internal fun StatTable(table: PlayerStatTable, onOpenPlayer: ((PlayerRef) -> Unit)? = null, limit: Int = Int.MAX_VALUE) {
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
    StatGrid(columns = table.columns, rows = rows, showHeadshots = true, onRowClick = onRowClick)
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
 * (passing has eight), the numbers scroll sideways under the header.
 */
@Composable
internal fun StatGrid(
    columns: List<String>,
    rows: List<GridRow>,
    showHeadshots: Boolean,
    nameWidth: Dp = if (showHeadshots) 136.dp else 92.dp,
    onRowClick: ((GridRow) -> Unit)? = null,
) {
    val baseWidths = remember(columns, rows) {
        columns.indices.map { i ->
            val longest = maxOf(columns[i].length, rows.maxOfOrNull { it.values.getOrNull(i)?.length ?: 0 } ?: 0)
            (longest * 8 + 16).coerceAtLeast(40).dp
        }
    }
    val divider = SportsHubTheme.colors.stroke
    val strong = MaterialTheme.colorScheme.onSurface
    Row(Modifier.fillMaxWidth()) {
        Column(Modifier.width(nameWidth)) {
            Spacer(Modifier.height(HeaderHeight))
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
                        PlayerHeadshot(row.headshotUrl, row.label, size = 28.dp)
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
            // Spread the columns out when they fit; otherwise keep them tight and scroll.
            val total = baseWidths.fold(0.dp) { acc, w -> acc + w }
            val scale = if (total < maxWidth && total > 0.dp) maxWidth / total else 1f
            val widths = baseWidths.map { it * scale }
            val fullWidth = widths.fold(0.dp) { a, w -> a + w }
            Column(Modifier.horizontalScroll(rememberScrollState())) {
                Row(Modifier.height(HeaderHeight), verticalAlignment = Alignment.CenterVertically) {
                    columns.forEachIndexed { i, label ->
                        Text(
                            label,
                            Modifier.width(widths[i]),
                            textAlign = TextAlign.End,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
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
                                )
                            }
                        }
                    }
                }
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
