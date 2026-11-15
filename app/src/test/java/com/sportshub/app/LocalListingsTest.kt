package com.sportshub.app

import com.sportshub.app.data.tv.LocalAiring
import com.sportshub.app.data.tv.LocalLineup
import com.sportshub.app.data.tv.LocalListings
import com.sportshub.app.data.tv.ListingChannel
import com.sportshub.app.data.tv.ListingEvent
import com.sportshub.app.data.tv.LocalListingsRepository
import com.sportshub.app.data.tv.TvMarkets
import com.sportshub.app.model.Competitor
import com.sportshub.app.model.Game
import com.sportshub.app.model.GameStatus
import com.sportshub.app.model.Sport
import com.sportshub.app.model.Team
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import kotlin.io.path.createTempDirectory

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

    /** A real answer from the guide for Dallas-Fort Worth (ZIP 75201), Nov 15, 2026 from 1 PM ET: the five big-network stations. */
    private val dallasGrid = javaClass.getResource("/gracenote_grid_75201.json")!!.readText()

    @Test fun dallasGetsItsRealChannelsWithoutAZip() {
        val repo = LocalListingsRepository(OkHttpClient())
        assertEquals("75201", repo.zipFor(null, TvMarkets.byId("dallas")))
        assertEquals("76102", repo.zipFor("76102", TvMarkets.byId("dallas")))
        assertEquals(null, repo.zipFor(null, TvMarkets.byId("new-york")))
        assertEquals(null, repo.zipFor("123", null))

        val slate = early + game("SF", "49ers", "DAL", "Cowboys", "Fox", "2026-11-15T21:25:00Z")
        val start = Instant.parse("2026-11-15T18:00:00Z")
        val end = Instant.parse("2026-11-16T00:00:00Z")
        val lineup = LocalListings.lineup("75201", slate, LocalListings.parse(dallasGrid)) { !it.isBefore(start) && it.isBefore(end) }
        assertEquals("CBS 11.1", lineup.airings["KC@ATL"]!!.label)
        // FOX's early game is on both KDFW 4.1 and KDFI 27.4; either is right.
        assertTrue(lineup.airings["MIN@GB"]!!.label in setOf("FOX 4.1", "FOX 27.4"))
        assertTrue(lineup.airings["SF@DAL"]!!.label in setOf("FOX 4.1", "FOX 27.4"))
        assertEquals(setOf("KC@ATL", "MIN@GB", "SF@DAL"), lineup.airings.keys)
        // The guide checked every game: the other two are not on free TV in Dallas.
        assertEquals(slate.map { it.id }.toSet(), lineup.checked)
    }

    @Test fun aGameTheGuideHasntPlacedYetKeepsTheEstimate() {
        // Early in the week the guide says "NFL Football" with no teams, and two CBS games share the window.
        val channels = listOf(
            ListingChannel(
                "KTVTDT", "11.1", "CBS",
                listOf(ListingEvent("NFL Football", null, null, Instant.parse("2026-11-15T18:00:00Z"), Instant.parse("2026-11-15T21:00:00Z"))),
            ),
            ListingChannel("KXASDT", "5.1", "NBC", emptyList()),
        )
        val start = Instant.parse("2026-11-15T18:00:00Z")
        val lineup = LocalListings.lineup("75201", early, channels) { !it.isBefore(start) }
        assertTrue(lineup.airings.isEmpty())
        // The two CBS games stay on the estimate; the FOX games (nothing on a FOX station here) are checked.
        assertEquals(setOf("MIN@GB", "HOU@CLE"), lineup.checked)
    }

    @Test fun showsNamedNflOrFootballOnly() {
        fun event(title: String) = ListingEvent(title, null, null, Instant.EPOCH, Instant.EPOCH)
        assertTrue(LocalListings.isFootball(event("NFL Football")))
        assertTrue(LocalListings.isFootball(event("College Football")))
        assertFalse(LocalListings.isFootball(event("DragonFly")))
    }

    @Test fun keepsTheFootballOfAnAnswerAndReadsItBack() {
        val channels = LocalListings.parse(dallasGrid)
        val saved = LocalListings.parse(LocalListings.footballOnly(channels))
        assertEquals(channels.map { it.callSign to it.affiliate }, saved.map { it.callSign to it.affiliate })
        assertEquals(channels.map { c -> c.events.filter(LocalListings::isFootball) }, saved.map { it.events })
        assertTrue(saved.flatMap { it.events }.all(LocalListings::isFootball))
    }

    @Test fun whenTheGuideRefusesTheLastAnswerOnThePhoneStandsIn() = runBlocking {
        var refuse = false
        var asked = 0
        val http = OkHttpClient.Builder().addInterceptor { chain ->
            asked++
            val ok = !refuse
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(if (ok) 200 else 405)
                .message(if (ok) "OK" else "Not Allowed")
                .body((if (ok) dallasGrid else "<html>captcha</html>").toResponseBody())
                .build()
        }.build()
        val dir = createTempDirectory("tv-guide").toFile()
        val now = Instant.parse("2026-11-15T15:00:00Z")
        val first = LocalListingsRepository(http, dir).lineup("75201", early, now)
        assertEquals("CBS 11.1", first.airings["KC@ATL"]!!.label)
        assertEquals(1, asked)

        // A new launch (nothing in memory), and the guide now refuses.
        refuse = true
        val second = LocalListingsRepository(http, dir).lineup("75201", early, now.plusSeconds(3600))
        assertEquals(first, second)
        assertEquals(3, asked)

        // Nothing saved and a refusal: no games checked, so the estimate stands.
        val third = LocalListingsRepository(http, null).lineup("75201", early, now)
        assertTrue(third.checked.isEmpty())
        dir.deleteRecursively()
        Unit
    }
}
