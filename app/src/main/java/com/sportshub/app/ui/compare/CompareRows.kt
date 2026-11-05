package com.sportshub.app.ui.compare

import androidx.compose.runtime.Immutable
import com.sportshub.app.data.PositionStats
import com.sportshub.app.model.CareerTable
import com.sportshub.app.model.PlayerProfile
import com.sportshub.app.model.Team
import com.sportshub.app.model.TeamProfile
import com.sportshub.app.model.TeamSeasonStats
import java.util.Locale

/**
 * One line of a comparison: the stat and each side's value. [edge] is -1 when the first
 * side is ahead, 1 when the second is, 0 when even or when "ahead" means nothing (age,
 * height). [share] is the first side's part of the total, for the split bar.
 */
@Immutable
data class CompareRow(
    val key: String,
    val label: String,
    val a: String,
    val b: String,
    val edge: Int,
    val share: Float?,
)

@Immutable
data class CompareGroup(val key: String, val title: String, val rows: List<CompareRow>, val note: String? = null)

/** How many rows each side is ahead in, for the "edges" tally. */
fun edges(groups: List<CompareGroup>): Pair<Int, Int> {
    val rows = groups.flatMap { it.rows }
    return rows.count { it.edge < 0 } to rows.count { it.edge > 0 }
}

/** A stat value as a number: "2,104", "65.3%", "+5" and "-3" all read; "6' 2\"" and "21/30" don't. */
internal fun statNumber(raw: String?): Double? {
    val s = raw?.trim()?.replace(",", "")?.removeSuffix("%")?.removePrefix("+") ?: return null
    return s.toDoubleOrNull()
}

/** Share of games won from a record like "6-2" or "6-2-1" (a tie counts half). */
internal fun winPct(record: String?): Double? {
    val parts = record?.trim()?.split("-")?.map { it.trim().toIntOrNull() ?: return null } ?: return null
    if (parts.size !in 2..3) return null
    val games = parts.sum()
    if (games == 0) return null
    return (parts[0] + parts.getOrElse(2) { 0 } / 2.0) / games
}

internal fun compareRow(
    key: String,
    label: String,
    a: String?,
    b: String?,
    lowerIsBetter: Boolean = false,
    neutral: Boolean = false,
    bar: Boolean = true,
): CompareRow {
    val x = statNumber(a)
    val y = statNumber(b)
    val edge = when {
        neutral || x == null || y == null || x == y -> 0
        (x > y) != lowerIsBetter -> -1
        else -> 1
    }
    val share = if (bar && x != null && y != null && x >= 0 && y >= 0 && x + y > 0) (x / (x + y)).toFloat() else null
    return CompareRow(key, label, a ?: "–", b ?: "–", edge, share)
}

private fun oneDecimal(v: Double) = String.format(Locale.US, "%.1f", v)

// ---------------------------------------------------------------------------
// Teams
// ---------------------------------------------------------------------------

/** One team's side of a comparison: who they are and whatever has loaded. */
@Immutable
data class TeamSide(val team: Team, val profile: TeamProfile?, val stats: TeamSeasonStats?)

/** Team stats where a smaller number is the better one. Keyed by group, then ESPN stat name. */
private val teamLowerIsBetter = setOf(
    "offense:interceptions",
    "offense:sacks",
    "offense:fumblesLost",
    "efficiency:totalGiveaways",
    "efficiency:totalPenalties",
    "efficiency:totalPenaltyYards",
    "playmaking:turnovers",
    "defense:fouls",
)

/** Team stats where neither number is better. */
private val teamNeutral = setOf("kicking:fieldGoalAttempts")

/**
 * Two teams side by side: the season at a glance, then each group of season stats that
 * both teams have. Counting stats compare per game when ESPN gives it, since one team may
 * have played a game more than the other.
 */
fun compareTeams(a: TeamSide, b: TeamSide): List<CompareGroup> {
    val season = buildList {
        val recordA = a.profile?.team?.record ?: a.team.record
        val recordB = b.profile?.team?.record ?: b.team.record
        if (recordA != null || recordB != null) {
            val pa = winPct(recordA)
            val pb = winPct(recordB)
            val edge = if (pa == null || pb == null || pa == pb) 0 else if (pa > pb) -1 else 1
            add(CompareRow("record", "Record", recordA ?: "–", recordB ?: "–", edge, share = null))
        }
        val pfA = a.profile?.pointsForPerGame
        val pfB = b.profile?.pointsForPerGame
        if (pfA != null || pfB != null) add(compareRow("pf", "Points a game", pfA?.let(::oneDecimal), pfB?.let(::oneDecimal)))
        val paA = a.profile?.pointsAgainstPerGame
        val paB = b.profile?.pointsAgainstPerGame
        if (paA != null || paB != null) {
            add(compareRow("pa", "Points allowed a game", paA?.let(::oneDecimal), paB?.let(::oneDecimal), lowerIsBetter = true))
        }
        val dA = a.profile?.pointDifferential
        val dB = b.profile?.pointDifferential
        if (dA != null || dB != null) {
            fun signed(v: Int) = if (v > 0) "+$v" else "$v"
            add(compareRow("diff", "Point differential", dA?.let(::signed), dB?.let(::signed), bar = false))
        }
    }
    val statGroups = a.stats?.groups.orEmpty().mapNotNull { groupA ->
        val groupB = b.stats?.groups?.firstOrNull { it.key == groupA.key } ?: return@mapNotNull null
        val rows = groupA.stats.mapNotNull { sa ->
            val sb = groupB.stats.firstOrNull { it.key == sa.key } ?: return@mapNotNull null
            val id = "${groupA.key}:${sa.key}"
            val perGame = sa.perGame != null && sb.perGame != null
            compareRow(
                key = id,
                label = if (perGame) "${sa.label} a game" else sa.label,
                a = if (perGame) sa.perGame else sa.value,
                b = if (perGame) sb.perGame else sb.value,
                lowerIsBetter = id in teamLowerIsBetter,
                neutral = id in teamNeutral,
                bar = !sa.key.contains("differential", ignoreCase = true),
            )
        }
        CompareGroup(groupA.key, groupA.title, rows).takeIf { rows.isNotEmpty() }
    }
    return listOfNotNull(CompareGroup("season", "Season", season).takeIf { season.isNotEmpty() }) + statGroups
}

// ---------------------------------------------------------------------------
// Players
// ---------------------------------------------------------------------------

@Immutable
data class PlayerSide(val profile: PlayerProfile?, val career: List<CareerTable>?)

/** Player stat columns where less is better, by ESPN category. */
private val playerLowerIsBetter = mapOf(
    "passing" to setOf("INT", "SACK", "SYL"),
    "rushing" to setOf("FUM", "LST"),
    "receiving" to setOf("FUM", "LST"),
    // Basketball
    "averages" to setOf("TO", "PF"),
    "totals" to setOf("TO", "PF"),
    "miscellaneous" to setOf("TECH", "FLAG", "EJECT"),
)

/** Columns that say how much someone played, not how well. */
private val playerNeutral = setOf("GP", "GS", "MIN")

private val bioRows = listOf("Age", "Height", "Weight", "Experience")

/**
 * Two players side by side: bio, then every stat category both have, either for each
 * player's latest season or their whole career.
 */
fun comparePlayers(a: PlayerSide, b: PlayerSide, career: Boolean): List<CompareGroup> {
    val bio = bioRows.mapNotNull { label ->
        val va = a.profile?.bio?.firstOrNull { it.label == label }?.value
        val vb = b.profile?.bio?.firstOrNull { it.label == label }?.value
        if (va == null && vb == null) null else compareRow(label.lowercase(), label, va, vb, neutral = true, bar = false)
    }
    // Each player's categories for their own position, so a QB and a WR share passing only
    // if the WR really throws.
    val careerB = b.career?.let { PositionStats.relevant(it, b.profile?.position) }
    val stats = a.career?.let { PositionStats.relevant(it, a.profile?.position) }.orEmpty().mapNotNull { ta ->
        val tb = careerB?.firstOrNull { it.key == ta.key } ?: return@mapNotNull null
        val rowA = (if (career) ta.totals else ta.seasons.firstOrNull()) ?: return@mapNotNull null
        val rowB = (if (career) tb.totals else tb.seasons.firstOrNull()) ?: return@mapNotNull null
        val lower = playerLowerIsBetter[ta.key].orEmpty()
        val rows = ta.columns.mapIndexedNotNull { i, column ->
            val j = tb.columns.indexOf(column)
            if (j < 0) return@mapIndexedNotNull null
            compareRow(
                key = "${ta.key}:$column",
                label = column,
                a = rowA.values.getOrNull(i),
                b = rowB.values.getOrNull(j),
                lowerIsBetter = column in lower,
                neutral = column in playerNeutral,
            )
        }
        val note = when {
            career -> "Career"
            rowA.label == rowB.label -> "${rowA.label} season"
            else -> "${rowA.label} vs ${rowB.label}"
        }
        CompareGroup(ta.key, ta.title, rows, note).takeIf { rows.isNotEmpty() }
    }
    return listOfNotNull(CompareGroup("bio", "Bio", bio).takeIf { bio.isNotEmpty() }) + stats
}
