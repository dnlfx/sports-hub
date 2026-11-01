package com.sportshub.app

import com.sportshub.app.data.espn.EspnImages
import com.sportshub.app.data.espn.EspnParser
import com.sportshub.app.model.GameStatus
import com.sportshub.app.model.Sport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EspnParserTest {
    private fun resource(name: String) =
        requireNotNull(javaClass.classLoader?.getResource(name)) { "missing $name" }.readText()

    private val board = EspnParser.parseScoreboard(Sport.NFL, resource("espn_scoreboard.json"))

    @Test fun readsSeasonAndWeek() {
        assertEquals(2026, board.season)
        assertEquals(2, board.seasonType)
        assertEquals(9, board.week)
    }

    @Test fun skipsBrokenEventsAndSortsLiveFirst() {
        assertEquals(listOf("200", "100", "300"), board.games.map { it.id })
    }

    @Test fun parsesLiveGame() {
        val live = board.games.first { it.id == "200" }
        val status = live.status as GameStatus.Live
        assertEquals(3, status.period)
        assertEquals("4:12 - 3rd", status.detail)
        assertEquals(21, live.away.score)
        assertEquals(listOf(0, 14, 7), live.away.periodScores)
        assertEquals("2nd & 7 at HOM 35", live.situation?.downDistance)
        assertEquals("4", live.situation?.possessionTeamId)
        assertEquals(listOf("FOX"), live.broadcasts)
        assertEquals("Passing", live.leaders.single().category)
        assertEquals("4", live.leaders.single().teamId)
    }

    @Test fun parsesScheduledGame() {
        val game = board.games.first { it.id == "100" }
        assertEquals(GameStatus.Scheduled, game.status)
        assertEquals("Homers", game.home.team.name)
        assertEquals("5-3", game.home.team.record)
        assertEquals("#112233", game.home.team.primaryColor)
        assertEquals(listOf("CBS"), game.broadcasts)
        assertEquals("Test Stadium", game.venue)
        assertNull(game.situation)
        assertEquals(1794160800L, game.startTime.epochSecond)
    }

    @Test fun parsesFinalGame() {
        val game = board.games.first { it.id == "300" }
        assertEquals(GameStatus.Final("Final/OT"), game.status)
        assertTrue(game.home.isWinner)
        assertFalse(game.away.isWinner)
    }

    @Test fun parsesSummary() {
        val detail = EspnParser.parseSummary(Sport.NFL, resource("espn_summary.json"))
        assertEquals("200", detail.game?.id)
        assertEquals("https://example.com/thr.png", detail.game?.home?.team?.logoUrl)
        val yards = detail.teamStats.first { it.label == "Total Yards" }
        assertEquals("312", yards.away)
        assertEquals("287", yards.home)
        assertEquals("R. Runner", detail.leaders.single().athleteName)
        assertEquals("Rushing Yards", detail.leaders.single().category)
        assertEquals("3", detail.leaders.single().teamId)
    }

    @Test fun summaryKeepsVenueAndTv() {
        val game = EspnParser.parseSummary(Sport.NFL, resource("espn_summary.json")).game
        assertEquals("Ford Field", game?.venue)
        assertEquals(listOf("FOX"), game?.broadcasts)
    }

    @Test fun parsesPlayerStatsPerTeam() {
        val detail = EspnParser.parseSummary(Sport.NFL, resource("espn_summary.json"))
        assertEquals(listOf("4", "3"), detail.playerStats.map { it.teamId })

        val away = detail.playerStats.first { it.teamId == "4" }
        // Passing comes before fumbles; the empty punting table is dropped.
        assertEquals(listOf("passing", "fumbles"), away.tables.map { it.key })
        val passing = away.tables.first()
        assertEquals("Passing", passing.title)
        assertEquals(listOf("C/ATT", "YDS", "TD", "INT"), passing.columns)
        // A player with no stats isn't listed.
        val passer = passing.players.single()
        assertEquals("J. Thrower", passer.name)
        assertEquals("7", passer.jersey)
        assertEquals(listOf("20/28", "250", "2", "0"), passer.values)
        assertEquals("https://a.espncdn.com/i/headshots/nfl/players/full/1.png", passer.headshotUrl)

        // Short stat rows are padded so every row has one value per column.
        val runner = detail.playerStats.first { it.teamId == "3" }.tables.single().players.single()
        assertEquals("R. Runner III", runner.name)
        assertEquals(listOf("15", "88", "5.9", "-", "-"), runner.values)
    }

    @Test fun leadersCarryPosition() {
        val detail = EspnParser.parseSummary(Sport.NFL, resource("espn_summary.json"))
        assertEquals("RB", detail.leaders.single().position)
    }

    @Test fun logoUrlsAreCanonical() {
        assertEquals(
            "https://a.espncdn.com/i/teamlogos/nfl/500/buf.png",
            EspnImages.canonical("http://a.espncdn.com/i/teamlogos/nfl/500/scoreboard/buf.png"),
        )
        assertNull(EspnImages.canonical("  "))
    }

    @Test fun survivesGarbage() {
        assertTrue(EspnParser.parseScoreboard(Sport.NFL, "{}").games.isEmpty())
    }
}
