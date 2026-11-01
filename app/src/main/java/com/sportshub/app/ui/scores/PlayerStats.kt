package com.sportshub.app.ui.scores

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sportshub.app.model.Game
import com.sportshub.app.model.Leader
import com.sportshub.app.model.PlayerStatTable
import com.sportshub.app.model.Team
import com.sportshub.app.model.TeamPlayerStats
import com.sportshub.app.ui.components.PlayerHeadshot
import com.sportshub.app.ui.components.TeamLogo
import com.sportshub.app.ui.components.accentColor

private val NameColumnWidth = 128.dp
private val RowHeight = 40.dp

/**
 * Box score for a live or finished game, as list items: a card with a switch between
 * the two teams, then one card per table for that team (passing, rushing, receiving,
 * defense...). Separate items let the list build only the tables on screen.
 */
fun LazyListScope.playerStatsItems(
    stats: List<TeamPlayerStats>,
    game: Game,
    selectedTeamId: String?,
    onSelectTeam: (String) -> Unit,
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
        Section(table.title, trailing = { TeamLogo(selected, size = 24.dp) }) { StatTable(table) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TeamSwitch(teams: List<Team>, selectedId: String, onSelect: (String) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        teams.forEachIndexed { index, team ->
            val color = team.accentColor(MaterialTheme.colorScheme.secondaryContainer)
            SegmentedButton(
                selected = selectedId == team.id,
                onClick = { onSelect(team.id) },
                shape = SegmentedButtonDefaults.itemShape(index, teams.size),
                icon = {},
                colors = SegmentedButtonDefaults.colors(activeContainerColor = color.copy(alpha = 0.25f)),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TeamLogo(team, size = 20.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(team.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

/**
 * One stat table. Player names stay pinned on the left; when the columns don't fit
 * the screen (passing has eight), the numbers scroll sideways under the header.
 */
@Composable
private fun StatTable(table: PlayerStatTable) {
    val baseWidths = remember(table) {
        table.columns.indices.map { i ->
            val longest = maxOf(table.columns[i].length, table.players.maxOf { it.values[i].length })
            (longest * 8 + 14).coerceAtLeast(38).dp
        }
    }
    Row(Modifier.fillMaxWidth()) {
        Column(Modifier.width(NameColumnWidth)) {
            Spacer(Modifier.height(28.dp))
            table.players.forEach { player ->
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(Modifier.height(RowHeight), verticalAlignment = Alignment.CenterVertically) {
                    PlayerHeadshot(player.headshotUrl, player.name, size = 26.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        player.name,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        BoxWithConstraints(Modifier.weight(1f)) {
            // Spread the columns out when they fit; otherwise keep them tight and scroll.
            val total = baseWidths.fold(0.dp) { acc, w -> acc + w }
            val scale = if (total < maxWidth) maxWidth / total else 1f
            val widths = baseWidths.map { it * scale }
            Column(Modifier.horizontalScroll(rememberScrollState())) {
                Row(Modifier.height(28.dp)) {
                    table.columns.forEachIndexed { i, label ->
                        Text(
                            label,
                            Modifier.width(widths[i]),
                            textAlign = TextAlign.End,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                }
                table.players.forEach { player ->
                    HorizontalDivider(Modifier.width(widths.fold(0.dp) { a, w -> a + w }), color = MaterialTheme.colorScheme.outlineVariant)
                    Row(Modifier.height(RowHeight), verticalAlignment = Alignment.CenterVertically) {
                        player.values.forEachIndexed { i, value ->
                            Text(
                                value,
                                Modifier.width(widths[i]),
                                textAlign = TextAlign.End,
                                style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
                                maxLines = 1,
                            )
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
fun TeamLeadersContent(leaders: List<Leader>, game: Game) {
    val groups = listOf(game.away.team, game.home.team)
        .map { team -> team to leaders.filter { it.teamId == team.id } }
        .filter { it.second.isNotEmpty() }
    if (groups.isEmpty()) {
        // No team info on the leaders: show them in one list rather than not at all.
        LeaderRows(leaders, team = null)
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        groups.forEach { (team, teamLeaders) ->
            Column {
                TeamHeader(team)
                Spacer(Modifier.height(10.dp))
                LeaderRows(teamLeaders, team)
            }
        }
    }
}

@Composable
private fun TeamHeader(team: Team) {
    Row(verticalAlignment = Alignment.CenterVertically) {
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
private fun LeaderRows(leaders: List<Leader>, team: Team?) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        leaders.forEachIndexed { i, leader ->
            if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(verticalAlignment = Alignment.CenterVertically) {
                PlayerHeadshot(leader.headshotUrl, leader.athleteName, size = HeadshotSize)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        leader.category,
                        style = MaterialTheme.typography.labelMedium,
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
