package com.sportshub.app.data

import com.sportshub.app.model.Game

/** A college football conference and its teams, as ESPN team ids with names for reading. */
data class Conference(val name: String, val teams: List<Pair<String, String>>) {
    val teamIds: List<String> get() = teams.map { it.first }
}

/**
 * College football in the app is limited to the top of the sport: the Power 4 conferences
 * (SEC, Big Ten, Big 12, ACC) plus Notre Dame, the one top independent. Teams are listed by
 * ESPN team id, which never changes, so a school that switches conferences is a one-line move.
 * Membership as of the 2026 season.
 */
object CollegeFootball {

    /** ESPN's group id for all of FBS, for scoreboards that would otherwise show only the Top 25. */
    const val FBS_GROUP = 80

    val conferences: List<Conference> = listOf(
        Conference(
            "SEC",
            listOf(
                "333" to "Alabama", "8" to "Arkansas", "2" to "Auburn", "57" to "Florida",
                "61" to "Georgia", "96" to "Kentucky", "99" to "LSU", "344" to "Mississippi State",
                "142" to "Missouri", "201" to "Oklahoma", "145" to "Ole Miss", "2579" to "South Carolina",
                "2633" to "Tennessee", "251" to "Texas", "245" to "Texas A&M", "238" to "Vanderbilt",
            ),
        ),
        Conference(
            "Big Ten",
            listOf(
                "356" to "Illinois", "84" to "Indiana", "2294" to "Iowa", "120" to "Maryland",
                "130" to "Michigan", "127" to "Michigan State", "135" to "Minnesota", "158" to "Nebraska",
                "77" to "Northwestern", "194" to "Ohio State", "2483" to "Oregon", "213" to "Penn State",
                "2509" to "Purdue", "164" to "Rutgers", "26" to "UCLA", "30" to "USC",
                "264" to "Washington", "275" to "Wisconsin",
            ),
        ),
        Conference(
            "Big 12",
            listOf(
                "12" to "Arizona", "9" to "Arizona State", "239" to "Baylor", "252" to "BYU",
                "2132" to "Cincinnati", "38" to "Colorado", "248" to "Houston", "66" to "Iowa State",
                "2305" to "Kansas", "2306" to "Kansas State", "197" to "Oklahoma State", "2628" to "TCU",
                "2641" to "Texas Tech", "2116" to "UCF", "254" to "Utah", "277" to "West Virginia",
            ),
        ),
        Conference(
            "ACC",
            listOf(
                "103" to "Boston College", "25" to "California", "228" to "Clemson", "150" to "Duke",
                "52" to "Florida State", "59" to "Georgia Tech", "97" to "Louisville", "2390" to "Miami",
                "152" to "NC State", "153" to "North Carolina", "221" to "Pittsburgh", "2567" to "SMU",
                "24" to "Stanford", "183" to "Syracuse", "258" to "Virginia", "259" to "Virginia Tech",
                "154" to "Wake Forest",
            ),
        ),
        Conference("Independent", listOf("87" to "Notre Dame")),
    )

    val teamIds: Set<String> = conferences.flatMapTo(LinkedHashSet()) { it.teamIds }

    fun isTopTeam(teamId: String): Boolean = teamId in teamIds

    /**
     * A game worth listing: at least one side is a top team. So Ohio State against a smaller
     * school still shows, but two teams from outside the top conferences don't.
     */
    fun isTopGame(game: Game): Boolean = isTopTeam(game.home.team.id) || isTopTeam(game.away.team.id)
}
