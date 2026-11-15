package com.sportshub.app

import com.sportshub.app.data.tv.Coverage
import com.sportshub.app.data.tv.CoverageWindow
import com.sportshub.app.data.tv.Geo
import com.sportshub.app.data.tv.MarketRaster
import com.sportshub.app.data.tv.Networks
import com.sportshub.app.data.tv.NflHomes
import com.sportshub.app.data.tv.TvMarkets
import com.sportshub.app.data.tv.UsMap
import com.sportshub.app.model.Competitor
import com.sportshub.app.model.Game
import com.sportshub.app.model.GameStatus
import com.sportshub.app.model.Scoreboard
import com.sportshub.app.model.Sport
import com.sportshub.app.model.Team
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class CoverageTest {
    private val eastern = ZoneId.of("America/New_York")

    /** Sunday, Nov 15 2026. */
    private val sunday = LocalDate.of(2026, 11, 15)

    private fun game(away: String, home: String, network: String, day: LocalDate, hour: Int, minute: Int = 0) = Game(
        id = "$away@$home",
        sport = Sport.NFL,
        startTime = ZonedDateTime.of(day.atTime(hour, minute), eastern).toInstant(),
        status = GameStatus.Scheduled,
        home = Competitor(Team(home, home, home), score = null),
        away = Competitor(Team(away, away, away), score = null),
        broadcasts = listOf(network),
    )

    private val week = listOf(
        game("BUF", "KC", "CBS", sunday, 13),
        game("NYJ", "MIA", "CBS", sunday, 13),
        game("DEN", "LV", "CBS", sunday, 16, 25),
        game("DAL", "PHI", "FOX", sunday, 13),
        game("SEA", "SF", "FOX", sunday, 16, 5),
        game("GB", "CHI", "NBC", sunday, 20, 20),
        game("PIT", "CLE", "ESPN", sunday.plusDays(1), 20, 15),
        game("LAR", "TB", "Prime Video", sunday.minusDays(3), 20, 15),
    )

    private val coverage = Coverage.nflWeek(week)
    private fun window(title: String) = coverage.windows.first { it.title == title }
    private fun market(id: String) = TvMarkets.byId(id)!!

    @Test fun marketsHaveUniqueIdsAndTheNearestOneIsFound() {
        assertEquals(TvMarkets.all.size, TvMarkets.all.map { it.id }.toSet().size)
        assertEquals("chicago", TvMarkets.nearest(41.95, -87.70).id)
        assertEquals("tampa", TvMarkets.nearest(28.0, -82.6).id)
        assertEquals("tampa", TvMarkets.search("tampa").first().id)
        assertTrue(TvMarkets.search("").size == TvMarkets.all.size)
    }

    @Test fun homeMarketsFollowTheClosestStadium() {
        assertEquals(setOf("WSH", "WAS"), NflHomes.homeTeams(market("washington")))
        assertTrue("BAL" in NflHomes.homeTeams(market("baltimore")))
        assertTrue(NflHomes.homeTeams(market("new-york")).containsAll(listOf("NYG", "NYJ")))
        assertTrue(NflHomes.isHome(market("rochester-ny"), "BUF"))
        assertTrue(NflHomes.isHome(market("milwaukee"), "GB"))
        assertFalse(NflHomes.isHome(market("syracuse"), "BUF"))
        assertTrue(NflHomes.homeTeams(market("omaha")).isEmpty())
    }

    @Test fun onlyBroadcastNetworksCountAsOverTheAir() {
        assertEquals("ABC", Networks.overTheAir(listOf("ESPN/ABC")))
        assertEquals("FOX", Networks.overTheAir(listOf("FOX")))
        assertEquals("NBC", Networks.overTheAir(listOf("Peacock", "NBC")))
        assertNull(Networks.overTheAir(listOf("CBS Sports Network")))
        assertNull(Networks.overTheAir(listOf("Prime Video")))
        assertNull(Networks.overTheAir(listOf("NFL Net")))
    }

    @Test fun splitsSundayIntoTheNetworksWindows() {
        val titles = coverage.windows.map { it.title }
        assertTrue(titles.containsAll(listOf("CBS early", "CBS late", "FOX single game")))
        assertTrue("Sunday night · NBC" in titles)
        assertTrue("Monday night · ESPN" in titles)
        assertTrue("Thursday night · Prime Video" in titles)
        // FOX's late game kicks at 4:05, so FOX has the single game: one window for both slots.
        assertEquals(2, window("FOX single game").games.size)
        assertEquals(CoverageWindow.Kind.Regional, window("CBS early").kind)
        assertEquals(CoverageWindow.Kind.National, window("CBS late").kind)
    }

    @Test fun homeMarketsGetTheirTeamAndOthersTheClosestGame() {
        val early = window("CBS early")
        assertEquals("BUF@KC", early.airing["buffalo"])
        assertEquals("BUF@KC", early.airing["rochester-ny"])
        assertEquals("BUF@KC", early.airing["kansas-city"])
        assertEquals("NYJ@MIA", early.airing["miami"])
        assertEquals("NYJ@MIA", early.airing["new-york"])
        assertEquals("BUF@KC", early.airing["chicago"])
        assertEquals("NYJ@MIA", early.airing["atlanta"])
        assertEquals(1.0, early.share("BUF@KC") + early.share("NYJ@MIA"), 1e-9)

        val fox = window("FOX single game")
        assertEquals("DAL@PHI", fox.airing["philadelphia"])
        assertEquals("SEA@SF", fox.airing["seattle"])
        assertEquals("SEA@SF", fox.airing["portland-or"])
        assertEquals("DAL@PHI", fox.airing["denver"])

        assertEquals(TvMarkets.all.size, window("CBS late").airing.size)
    }

    @Test fun cableGamesAreOverTheAirOnlyInTheTeamsHomeMarkets() {
        val mnf = window("Monday night · ESPN")
        assertEquals(CoverageWindow.Kind.LocalOnly, mnf.kind)
        assertEquals("PIT@CLE", mnf.airing["pittsburgh"])
        assertEquals("PIT@CLE", mnf.airing["cleveland"])
        assertNull(mnf.airing["chicago"])
        assertEquals("LAR@TB", window("Thursday night · Prime Video").airing["los-angeles"])
    }

    @Test fun listsTheGamesOnFreeTvInAMarket() {
        assertEquals(setOf("BUF@KC", "DEN@LV", "DAL@PHI", "GB@CHI"), coverage.overTheAir(market("chicago")))
        assertEquals(setOf("BUF@KC", "DEN@LV", "DAL@PHI", "GB@CHI", "PIT@CLE"), coverage.overTheAir(market("pittsburgh")))
        assertEquals(setOf("DEN@LV", "GB@CHI"), coverage.nationalOverTheAir())
    }

    @Test fun otherSportsCountOnlyGamesAloneOnTheirNetwork() {
        val saturday = sunday.minusDays(1)
        val games = listOf(
            game("A", "B", "FOX", saturday, 12),
            game("C", "D", "FOX", saturday, 12, 30),
            game("E", "F", "ABC", saturday, 19, 30),
            game("G", "H", "ESPN", saturday, 19, 30),
        )
        assertEquals(setOf("E@F"), Coverage.nationalOverTheAir(games))
    }

    @Test fun drawsTheLowerFortyEight() {
        val map = UsMap.parse(File("src/main/assets/us_map.txt").readText())
        assertTrue(map.states.size >= 49)
        assertTrue(map.width > map.height)
        // Seattle is up and to the left of Miami, and both are on the map.
        val seattle = Geo.albers(47.61, -122.33)
        val miami = Geo.albers(25.76, -80.19)
        assertTrue(seattle[0] < miami[0] && seattle[1] < miami[1])
        listOf(seattle, miami).forEach { (x, y) ->
            assertTrue(x.toFloat() in map.minX..map.maxX && y.toFloat() in map.minY..map.maxY)
        }
    }

    @Test fun eachMapCellBelongsToTheClosestMarket() {
        val map = UsMap.parse(File("src/main/assets/us_map.txt").readText())
        val raster = MarketRaster.build(map, width = 200)
        fun cellAt(lat: Double, lon: Double): String {
            val (x, y) = Geo.albers(lat, lon)
            val col = ((x - map.minX) / map.width * raster.width).toInt()
            val row = ((y - map.minY) / map.height * raster.height).toInt()
            return raster.markets[raster.cells[row * raster.width + col]].id
        }
        assertEquals("chicago", cellAt(41.88, -87.63))
        assertEquals("denver", cellAt(39.74, -104.99))
        assertEquals("seattle", cellAt(47.61, -122.33))
    }

    @Test fun marksOnlyNationalNflGamesUntilTheMarketIsKnown() {
        val board = Scoreboard(Sport.NFL, 2026, 2, 11, week, 0L)
        assertEquals(setOf("DEN@LV", "GB@CHI"), Coverage.overTheAir(board, null))
        assertTrue("BUF@KC" in Coverage.overTheAir(board, market("chicago")))
    }
}
