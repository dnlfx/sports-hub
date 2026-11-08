package com.sportshub.app.ui.team

import androidx.compose.runtime.Immutable
import com.sportshub.app.model.Sport
import com.sportshub.app.model.TeamListing
import com.sportshub.app.model.TeamStanding
import java.util.Locale

/** One team's line in its division's standings. */
@Immutable
data class StandingRow(
    val listing: TeamListing,
    val standing: TeamStanding?,
    /** 1 for the leader. Null before anyone has played, or when standings haven't loaded. */
    val rank: Int?,
    /** Games behind the leader, e.g. "1.5", or "-" for the leader and teams level with them. Pro leagues only. */
    val gamesBehind: String?,
)

/** A division (or college conference) in standings order. */
@Immutable
data class DivisionStandings(val group: TeamGroup, val rows: List<StandingRow>) {
    /** True once the teams carry ranks, so the list is in standings order. */
    val ranked: Boolean get() = rows.any { it.rank != null }
}

/** Wins over games played, a tie counting as half a win. Null before any games. */
internal fun winPercent(wins: Int, losses: Int, ties: Int = 0): Double? {
    val games = wins + losses + ties
    return if (games == 0) null else (wins + ties / 2.0) / games
}

/** "7-2" or "7-2-1" as wins, losses and ties. Null if it isn't a record. */
internal fun parseRecord(summary: String?): Triple<Int, Int, Int>? {
    val parts = summary?.trim()?.split("-")?.map { it.trim().toIntOrNull() ?: return null } ?: return null
    return when (parts.size) {
        2 -> Triple(parts[0], parts[1], 0)
        3 -> Triple(parts[0], parts[1], parts[2])
        else -> null
    }
}

/** ".750", or "1.000" for an unbeaten team, as standings print it. */
internal fun formatPercent(pct: Double?): String {
    if (pct == null) return "-"
    val thousandths = Math.round(pct * 1000).toInt()
    return if (thousandths >= 1000) "1.000" else ".%03d".format(Locale.US, thousandths)
}

/** 1st, 2nd, 3rd, 4th, 11th, 12th, 13th, 21st... */
internal fun ordinal(n: Int): String {
    val suffix = if (n % 100 in 11..13) "th" else when (n % 10) {
        1 -> "st"
        2 -> "nd"
        3 -> "rd"
        else -> "th"
    }
    return "$n$suffix"
}

/** The overall record to show: ESPN's own summary, or one made from the counts. */
internal fun TeamStanding.overallRecord(): String =
    overall ?: if (ties > 0) "$wins-$losses-$ties" else "$wins-$losses"

/** Conference record as a win percentage, for ranking college conferences. */
private fun TeamStanding.conferencePercent(): Double? =
    parseRecord(conference)?.let { (w, l, t) -> winPercent(w, l, t) }

/**
 * Puts a group's teams in standings order and numbers them.
 *
 * Pro leagues rank by win percentage, then by wins (2-0 is ahead of 1-0). Remaining ties
 * follow ESPN's own order in the NFL, whose division lists already apply the league's
 * tie-breakers, and the projected seed in the NBA, whose lists run worst to best.
 * College conferences rank by conference record, then ESPN's order, which applies the
 * conference's tie-breakers. Teams without a standing go last, by name. Nobody is
 * numbered before the first game.
 */
internal fun rankGroup(sport: Sport, group: TeamGroup, standings: Map<String, TeamStanding>): DivisionStandings {
    val (known, unknown) = group.teams.partition { it.team.id in standings }
    if (known.isEmpty()) return DivisionStandings(group, group.teams.map { StandingRow(it, null, null, null) })

    val college = sport.isCollegeFootball
    fun s(listing: TeamListing) = standings.getValue(listing.team.id)
    val order = when {
        // A team yet to play a conference game sits at .500, between the unbeaten and the
        // winless, as conference tables show them.
        college -> compareByDescending<TeamListing> { s(it).conferencePercent() ?: 0.5 }.thenBy { s(it).order }
        else -> compareByDescending<TeamListing> { s(it).let { t -> winPercent(t.wins, t.losses, t.ties) } ?: 0.0 }
            .thenByDescending { s(it).wins }
            .then(
                if (sport.isBasketball) compareBy<TeamListing> { s(it).seed ?: Int.MAX_VALUE } else compareBy<TeamListing> { s(it).order },
            )
    }
    val sorted = known.sortedWith(order) + unknown.sortedBy { it.displayName }

    // An independent has nobody to be ranked against.
    val ranked = group.teams.size > 1 && known.any { standings.getValue(it.team.id).gamesPlayed > 0 }
    val leader = standings[sorted.first().team.id]
    val rows = sorted.mapIndexed { index, listing ->
        val standing = standings[listing.team.id]
        StandingRow(
            listing = listing,
            standing = standing,
            rank = if (ranked && standing != null) index + 1 else null,
            gamesBehind = if (!college && ranked && standing != null && leader != null) gamesBehind(leader, standing) else null,
        )
    }
    return DivisionStandings(group, rows)
}

/** Every group of the page in standings order. */
internal fun rankGroups(sport: Sport, groups: List<TeamGroup>, standings: Map<String, TeamStanding>): List<DivisionStandings> =
    groups.map { rankGroup(sport, it, standings) }

/** How far [team] trails [leader]: half the gap in wins plus half the gap in losses. */
internal fun gamesBehind(leader: TeamStanding, team: TeamStanding): String {
    val halves = (leader.wins - team.wins) + (team.losses - leader.losses)
    return when {
        halves <= 0 -> "-"
        halves % 2 == 0 -> "${halves / 2}"
        else -> "${halves / 2}.5"
    }
}
