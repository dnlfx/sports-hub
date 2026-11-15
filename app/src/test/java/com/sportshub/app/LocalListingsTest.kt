package com.sportshub.app

import com.sportshub.app.data.tv.LocalAiring
import com.sportshub.app.data.tv.LocalLineup
import com.sportshub.app.data.tv.LocalListings
import com.sportshub.app.data.tv.LocalListingsRepository
import com.sportshub.app.model.Competitor
import com.sportshub.app.model.Game
import com.sportshub.app.model.GameStatus
import com.sportshub.app.model.Sport
import com.sportshub.app.model.Team
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class LocalListingsTest {
    /** A real answer from the guide: antenna stations in ZIP 10001, Nov 15, 2026 from 1 PM ET (trimmed to five). */
    private val grid = javaClass.getResource("/gracenote_grid_10001.json")!!.readText()

    private fun game(away: String, awayName: String, home: String, homeName: String, network: String, kickoff: String) = Game(
        id = "$away@$home",
        sport = Sport.NFL,
        startTime = Instant.parse(kickoff),
        status = GameStatus.Scheduled,
        home = Competitor(Team(home, homeName, home), score = null),
        away = Competitor(Team(away, awayName, away), score = null),
        broadcasts = listOf(network),
    )

    private val early = listOf(
        game("BUF", "Bills", "NYJ", "Jets", "CBS", "2026-11-15T18:00:00Z"),
        game("KC", "Chiefs", "ATL", "Falcons", "CBS", "2026-11-15T18:00:00Z"),
        game("MIN", "Vikings", "GB", "Packers", "Fox", "2026-11-15T18:00:00Z"),
        game("HOU", "Texans", "CLE", "Browns", "Fox", "2026-11-15T18:00:00Z"),
    )

    @Test fun readsTheGuidesStations() {
        val channels = LocalListings.parse(grid)
        assertEquals(listOf("2.1", "4.1", "5.1", "7.1", "45.3"), channels.map { it.number })
        assertEquals(listOf("CBS", "NBC", "FOX", "ABC", "CBS"), channels.map { it.affiliate })
        val cbs = channels.first().events.first()
        assertEquals("NFL Football", cbs.title)
        assertEquals("Buffalo Bills at New York Jets", cbs.episodeTitle)
        assertEquals(Instant.parse("2026-11-15T21:00:00Z"), cbs.end)
        assertTrue(LocalListings.parse("{\"message\":\"No lineups\"}").isEmpty())
        assertTrue(LocalListings.parse("<html>").isEmpty())
    }

    @Test fun findsTheStationEachGameIsOn() {
        val channels = LocalListings.parse(grid)
        val found = LocalListings.match(early, channels)
        assertEquals(LocalAiring("WCBSDT", "2.1", "CBS"), found["BUF@NYJ"])
        assertEquals("CBS 2.1", found["BUF@NYJ"]!!.label)
        assertEquals("FOX 5.1", found["MIN@GB"]!!.label)
        // Not on any New York antenna station.
        assertFalse("KC@ATL" in found)
        assertFalse("HOU@CLE" in found)
        assertTrue(early.all { LocalListings.covers(channels, it.startTime) })
    }

    @Test fun theGuideOverridesTheEstimateOnlyForTheGamesItChecked() {
        val lineup = LocalLineup(
            zip = "10001",
            airings = mapOf("MIN@GB" to LocalAiring("WNYWDT", "5.1", "FOX")),
            checked = setOf("MIN@GB", "HOU@CLE"),
        )
        // The estimate said HOU@CLE (wrong) and a night game the guide didn't check.
        val estimate = setOf("HOU@CLE", "PIT@CIN")
        assertEquals(setOf("MIN@GB", "PIT@CIN"), LocalListings.overTheAir(estimate, lineup))
        assertEquals(estimate, LocalListings.overTheAir(estimate, null))
    }

    @Test fun asksForOneSixHourGridPerStretchOfKickoffs() {
        val repo = LocalListingsRepository(OkHttpClient())
        val week = early + listOf(
            game("SEA", "Seahawks", "LV", "Raiders", "CBS", "2026-11-15T21:05:00Z"),
            game("SF", "49ers", "DAL", "Cowboys", "Fox", "2026-11-15T21:25:00Z"),
            game("PIT", "Steelers", "CIN", "Bengals", "NBC", "2026-11-16T01:20:00Z"),
        )
        val blocks = repo.blocks(week)
        assertEquals(listOf(Instant.parse("2026-11-15T18:00:00Z"), Instant.parse("2026-11-16T01:00:00Z")), blocks.map { it.first })
        assertEquals(6, blocks[0].second.size)
        assertTrue(LocalListings.isZip("02108"))
        assertFalse(LocalListings.isZip("2108"))
        assertFalse(LocalListings.isZip("1000a"))
    }
}
