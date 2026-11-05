package com.sportshub.app

import com.sportshub.app.data.BasketballStats
import com.sportshub.app.data.espn.EspnParser
import com.sportshub.app.data.espn.EspnTeamParser
import com.sportshub.app.model.GameStatus
import com.sportshub.app.model.PlayKind
import com.sportshub.app.model.Sport
import com.sportshub.app.model.Team
import com.sportshub.app.model.TeamListing
import com.sportshub.app.ui.team.groupTeams
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NbaParserTest {
    private fun resource(name: String) =
        requireNotNull(javaClass.classLoader?.getResource(name)) { "missing $name" }.readText()

    private val board = EspnParser.parseScoreboard(Sport.NBA, resource("nba_scoreboard.json"))
    private val summary = EspnParser.parseSummary(Sport.NBA, resource("nba_summary.json"))

    @Test fun nbaIsAvailable() {
        assertTrue(Sport.NBA.isAvailable)
        assertTrue(Sport.NBA.isBasketball)
        assertEquals("2026-27", Sport.NBA.seasonName(2027))
        assertEquals("2026", Sport.NFL.seasonName(2026))
    }

    @Test fun readsTheDayAndCalendar() {
        assertEquals(LocalDate.of(2026, 11, 5), board.date)
        assertNull(board.week)
        assertEquals(
            listOf(LocalDate.of(2026, 10, 20), LocalDate.of(2026, 10, 21), LocalDate.of(2026, 11, 4), LocalDate.of(2026, 11, 5)),
            board.calendar,
        )
    }

    @Test fun nflBoardsHaveNoCalendar() {
        val nfl = EspnParser.parseScoreboard(Sport.NFL, resource("espn_scoreboard.json"))
        assertTrue(nfl.calendar.isEmpty())
    }

    @Test fun parsesGames() {
        assertEquals(listOf("401800002", "401800001"), board.games.map { it.id })
        val live = board.games.first()
        val status = live.status as GameStatus.Live
        assertEquals(3, status.period)
        assertEquals(listOf(25, 33, 23), live.away.periodScores)
        assertEquals("Jalen Brunson makes 26-foot three point jumper", live.situation?.lastPlay)
        assertNull(live.situation?.downDistance)

        val upcoming = board.games.last()
        assertEquals("Celtics", upcoming.home.team.name)
        assertEquals("4-2", upcoming.home.team.record)
        assertEquals("Points Per Game", upcoming.leaders.single().category)
        assertEquals(listOf("ESPN"), upcoming.broadcasts)
    }

    @Test fun boxScoreKeepsEveryoneWhoPlayedWithStarterFlags() {
        val home = summary.playerStats.first { it.teamId == "18" }
        val table = home.tables.single()
        assertEquals(EspnParser.BASKETBALL_TABLE, table.key)
        // The player who didn't play has no row.
        assertEquals(listOf("1", "2", "3"), table.players.map { it.athleteId })
        assertEquals(listOf(true, true, false), table.players.map { it.starter })
        assertEquals("MIN", table.columns.first())
    }

    @Test fun boxScoreSplitsStartersAndBench() {
        val shown = BasketballStats.forDisplay(summary.playerStats.first { it.teamId == "18" }.tables)
        assertEquals(listOf("Starters", "Bench"), shown.map { it.title })
        val starters = shown.first()
        assertEquals(listOf("MIN", "PTS", "REB", "AST", "FG"), starters.columns.take(5))
        assertEquals(listOf("36", "27", "8", "5", "10-20"), starters.players.first().values.take(5))
        assertEquals(14, starters.columns.size)
    }

    @Test fun teamStatsAndHeader() {
        assertEquals(GameStatus.Final("Final"), summary.game?.status)
        assertEquals(listOf(28, 30, 20, 34), summary.game?.home?.periodScores)
        assertEquals("Madison Square Garden", summary.game?.venue)
        val fg = summary.teamStats.first { it.label == "Field goals" }
        assertEquals("40-88", fg.away)
        assertEquals("42-85", fg.home)
        assertEquals("A. One", summary.leaders.single().athleteName)
    }

    @Test fun winProbabilityReadsBasketballPlays() {
        val wp = assertNotNull(summary.winProbability).let { summary.winProbability!! }
        assertEquals(6, wp.points.size)
        assertEquals(0.612f, wp.pregameHomePct!!, 0.001f)
        assertEquals(
            listOf(PlayKind.Other, PlayKind.ThreePointer, PlayKind.Turnover, PlayKind.Dunk, PlayKind.FreeThrow, PlayKind.Basket),
            wp.points.map { it.kind },
        )
        assertEquals(5, wp.points.last().period)
        assertEquals("17", wp.points[1].teamId)
        assertEquals(3, wp.points[1].awayScore)
    }

    @Test fun nflPlaysStillReadAsFootball() {
        assertEquals(PlayKind.FieldGoal, EspnParser.playKind("Field Goal Good", "FG", scoring = true))
        assertEquals(PlayKind.Basket, EspnParser.basketballPlayKind("Jump Shot", "makes jumper", 2, scoring = true))
        assertEquals(PlayKind.Other, EspnParser.basketballPlayKind("Defensive Rebound", "rebound", 0, scoring = false))
    }

    @Test fun teamSeasonStatsPickBasketballNumbers() {
        val stats = EspnTeamParser.parseTeamStatistics(resource("nba_team_stats.json"), Sport.NBA)
        assertEquals(2027, stats.season)
        assertEquals(listOf("scoring", "playmaking", "rebounding", "defense"), stats.groups.map { it.key })
        val scoring = stats.groups.first { it.key == "scoring" }
        val points = scoring.stats.first { it.key == "points" }
        assertEquals("690", points.value)
        assertEquals("115", points.perGame)
        assertEquals("37.0", scoring.stats.first { it.key == "threePointFieldGoalPct" }.value)
        assertNull(scoring.stats.first { it.key == "fieldGoalPct" }.perGame)
        val defense = stats.groups.first { it.key == "defense" }
        assertEquals("8", defense.stats.first { it.key == "steals" }.perGame)
        assertEquals("5", defense.stats.first { it.key == "blocks" }.perGame)
        val playmaking = stats.groups.first { it.key == "playmaking" }
        assertNull(playmaking.stats.first { it.key == "assistTurnoverRatio" }.perGame)
    }

    @Test fun groupsNbaTeamsByDivision() {
        val abbrs = listOf(
            "BOS", "BKN", "NY", "PHI", "TOR", "CHI", "CLE", "DET", "IND", "MIL", "ATL", "CHA", "MIA", "ORL", "WSH",
            "DEN", "MIN", "OKC", "POR", "UTAH", "GS", "LAC", "LAL", "PHX", "SAC", "DAL", "HOU", "MEM", "NO", "SA",
        )
        val list = abbrs.mapIndexed { i, a -> TeamListing(Team("$i", a, a), a) }
        val groups = groupTeams(Sport.NBA, list)
        assertEquals(6, groups.size)
        assertTrue(groups.all { it.teams.size == 5 })
        assertEquals("East · Atlantic", groups.first().title)
        assertEquals(listOf("East", "East", "East", "West", "West", "West"), groups.map { it.conference })
    }
}
