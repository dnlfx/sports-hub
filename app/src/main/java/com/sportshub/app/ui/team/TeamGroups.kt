package com.sportshub.app.ui.team

import androidx.compose.runtime.Immutable
import com.sportshub.app.model.Sport
import com.sportshub.app.model.TeamListing

/** A titled group of teams, e.g. "AFC East". */
@Immutable
data class TeamGroup(val key: String, val conference: String, val title: String, val teams: List<TeamListing>)

/** NFL divisions by team abbreviation. Stable for decades; anything unknown is listed under "Other". */
private val nflDivisions: List<Triple<String, String, List<String>>> = listOf(
    Triple("AFC", "AFC East", listOf("BUF", "MIA", "NE", "NYJ")),
    Triple("AFC", "AFC North", listOf("BAL", "CIN", "CLE", "PIT")),
    Triple("AFC", "AFC South", listOf("HOU", "IND", "JAX", "TEN")),
    Triple("AFC", "AFC West", listOf("DEN", "KC", "LV", "LAC")),
    Triple("NFC", "NFC East", listOf("DAL", "NYG", "PHI", "WSH")),
    Triple("NFC", "NFC North", listOf("CHI", "DET", "GB", "MIN")),
    Triple("NFC", "NFC South", listOf("ATL", "CAR", "NO", "TB")),
    Triple("NFC", "NFC West", listOf("ARI", "LAR", "SF", "SEA")),
)

/**
 * NBA divisions by team abbreviation. ESPN's abbreviations come first; the others are
 * the league's own, in case ESPN switches to them.
 */
private val nbaDivisions: List<Triple<String, String, List<String>>> = listOf(
    Triple("East", "East · Atlantic", listOf("BOS", "BKN", "BRK", "NY", "NYK", "PHI", "TOR")),
    Triple("East", "East · Central", listOf("CHI", "CLE", "DET", "IND", "MIL")),
    Triple("East", "East · Southeast", listOf("ATL", "CHA", "MIA", "ORL", "WSH", "WAS")),
    Triple("West", "West · Northwest", listOf("DEN", "MIN", "OKC", "POR", "UTAH", "UTA")),
    Triple("West", "West · Pacific", listOf("GS", "GSW", "LAC", "LAL", "PHX", "PHO", "SAC")),
    Triple("West", "West · Southwest", listOf("DAL", "HOU", "MEM", "NO", "NOP", "SA", "SAS")),
)

private fun divisions(sport: Sport): List<Triple<String, String, List<String>>>? = when (sport) {
    Sport.NFL -> nflDivisions
    Sport.NBA -> nbaDivisions
    else -> null
}

/** The league's teams by division, in the league's usual order, or one list for sports without divisions. */
internal fun groupTeams(sport: Sport, list: List<TeamListing>): List<TeamGroup> {
    val divisions = divisions(sport)
        ?: return listOf(TeamGroup("all", "", "All teams", list.sortedBy { it.displayName }))
    val byAbbr = list.associateBy { it.team.abbreviation.uppercase() }
    val used = mutableSetOf<String>()
    val groups = divisions.mapNotNull { (conference, title, abbrs) ->
        val teams = abbrs.mapNotNull { byAbbr[it] }.distinctBy { it.team.id }.filter { it.team.id !in used }
        used += teams.map { it.team.id }
        TeamGroup(title, conference, title, teams).takeIf { teams.isNotEmpty() }
    }
    val rest = list.filter { it.team.id !in used }.sortedBy { it.displayName }
    return if (rest.isEmpty()) groups else groups + TeamGroup("other", "", "Other", rest)
}
