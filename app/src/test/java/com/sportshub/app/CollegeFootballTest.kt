package com.sportshub.app

import com.sportshub.app.data.CollegeFootball
import com.sportshub.app.data.espn.EspnParser
import com.sportshub.app.data.espn.EspnTeamParser
import com.sportshub.app.model.GameStatus
import com.sportshub.app.model.Sport
import com.sportshub.app.ui.scores.spokenSummary
import com.sportshub.app.ui.team.groupTeams
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CollegeFootballTest {
    private fun resource(name: String) =
        requireNotNull(javaClass.classLoader?.getResource(name)) { "missing $name" }.readText()

    private val board = EspnParser.parseScoreboard(Sport.NCAAF, resource("cfb_scoreboard.json"))

    @Test fun collegeFootballIsAvailable() {
        val sport = Sport.NCAAF
        assertTrue(sport.isAvailable)
        assertTrue(sport.isCollegeFootball)
        assertFalse(sport.isBasketball)
        assertTrue(sport.hasWeeks)
        assertEquals(15, sport.regularSeasonWeeks)
        assertEquals("2026", sport.seasonName(2026))
        assertEquals("Bowls and playoff", sport.postseasonName)
        assertEquals("Playoffs", Sport.NFL.postseasonName)
        assertEquals(Sport.NCAAF, Sport.fromId("ncaaf"))
    }

    @Test fun topConferencesAreThePowerFourAndNotreDame() {
        val sizes = CollegeFootball.conferences.associate { it.name to it.teams.size }
        assertEquals(
            mapOf("SEC" to 16, "Big Ten" to 18, "Big 12" to 16, "ACC" to 17, "Independent" to 1),
            sizes,
        )
        val all = CollegeFootball.conferences.flatMap { it.teamIds }
        assertEquals("no team is listed twice", all.size, all.toSet().size)
        assertEquals(68, CollegeFootball.teamIds.size)
        assertTrue(CollegeFootball.isTopTeam("87"))
        assertFalse(CollegeFootball.isTopTeam("193"))
    }

    @Test fun keepsOnlyGamesWithATopTeam() {
        // Toledo vs Akron is dropped; Georgia vs Western Michigan stays for Georgia.
        assertEquals(listOf("401900001", "401900004", "401900002"), board.games.map { it.id })
        assertEquals(11, board.week)
        assertTrue(board.calendar.isEmpty())
    }

    @Test fun readsPollRanks() {
        val live = board.games.first()
        assertEquals(2, live.home.rank)
        assertEquals(14, live.away.rank)
        assertEquals("Ohio State", live.home.team.name)
        assertEquals("2nd & 7 at PSU 34", live.situation?.downDistance)
        assertEquals(2, (live.status as GameStatus.Live).period)

        val final = board.games.first { it.id == "401900002" }
        assertEquals(7, final.home.rank)
        assertNull("99 means unranked", final.away.rank)

        val upcoming = board.games.first { it.id == "401900004" }
        assertNull(upcoming.home.rank)
        assertNull(upcoming.away.rank)
    }

    @Test fun spokenSummaryReadsRanks() {
        val live = board.games.first()
        assertTrue(live.spokenSummary().startsWith("Live. Number 14 Penn State 10, Number 2 Ohio State 14."))
    }

    @Test fun teamsListHasOnlyTopTeams() {
        val teams = EspnTeamParser.parseTeams(resource("cfb_teams.json"), Sport.NCAAF)
        assertEquals(listOf("333", "87", "2390", "194", "252"), teams.map { it.team.id })
        // Without a sport, every team is kept, as for the NFL and NBA.
        assertEquals(7, EspnTeamParser.parseTeams(resource("cfb_teams.json")).size)
    }

    @Test fun teamsAreGroupedByConference() {
        val teams = EspnTeamParser.parseTeams(resource("cfb_teams.json"), Sport.NCAAF)
        val groups = groupTeams(Sport.NCAAF, teams)
        assertEquals(listOf("SEC", "Big Ten", "Big 12", "ACC", "Independent"), groups.map { it.title })
        assertEquals(listOf("Miami"), groups.first { it.title == "ACC" }.teams.map { it.team.name })
        assertEquals(listOf("Notre Dame"), groups.last().teams.map { it.team.name })
    }

    @Test fun otherSportsAreNotFiltered() {
        val nfl = EspnParser.parseScoreboard(Sport.NFL, resource("espn_scoreboard.json"))
        assertTrue(nfl.games.isNotEmpty())
        assertTrue(nfl.games.all { it.home.rank == null && it.away.rank == null })
    }
}
