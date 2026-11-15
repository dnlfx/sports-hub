package com.sportshub.app

import com.sportshub.app.data.tv.Coverage
import com.sportshub.app.model.Competitor
import com.sportshub.app.model.Game
import com.sportshub.app.model.GameStatus
import com.sportshub.app.model.Sport
import com.sportshub.app.model.Team
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * Checks the coverage estimate against what stations really aired: the free TV guide's
 * antenna listings (tvlistings.gracenote.com) for about 60 cities in week 10 (Nov 15, 2026),
 * which the rules were tuned on, and 43 in week 11 (Nov 22), which they weren't. Where a
 * city's antenna gets stations from two markets, either game counts.
 *
 * At the time of writing: 91% of week 10 and 73% of week 11. The misses are mostly which
 * game the networks feature nationally, which they decide on more than records and ratings.
 */
class CoverageAccuracyTest {
    private fun g(away: String, awayRecord: String, home: String, homeRecord: String, network: String, kickoff: String) = Game(
        id = "$away@$home",
        sport = Sport.NFL,
        startTime = Instant.parse(kickoff.replace("Z", ":00Z")),
        status = GameStatus.Scheduled,
        home = Competitor(Team(home, home, home, record = homeRecord), score = null),
        away = Competitor(Team(away, away, away, record = awayRecord), score = null),
        broadcasts = listOf(network),
    )

    /** ESPN's week 10 scoreboard (Nov 15): networks, kickoffs and records going in. */
    private val week10 = listOf(
        g("NE", "5-4", "DET", "6-2", "Fox", "2026-11-15T14:30Z"),
        g("KC", "6-2", "ATL", "3-6", "CBS", "2026-11-15T18:00Z"),
        g("HOU", "3-5", "CLE", "5-4", "Fox", "2026-11-15T18:00Z"),
        g("MIN", "6-2", "GB", "6-3", "Fox", "2026-11-15T18:00Z"),
        g("JAX", "6-2", "TEN", "1-7", "Fox", "2026-11-15T18:00Z"),
        g("MIA", "1-7", "IND", "2-7", "CBS", "2026-11-15T18:00Z"),
        g("CAR", "3-5", "NO", "3-5", "Fox", "2026-11-15T18:00Z"),
        g("BUF", "7-1", "NYJ", "2-7", "CBS", "2026-11-15T18:00Z"),
        g("SEA", "5-4", "LV", "4-5", "CBS", "2026-11-15T21:05Z"),
        g("LAR", "5-4", "ARI", "3-6", "CBS", "2026-11-15T21:05Z"),
        g("SF", "7-1", "DAL", "4-5", "Fox", "2026-11-15T21:25Z"),
        g("PIT", "6-2", "CIN", "6-2", "NBC", "2026-11-16T01:20Z"),
        g("WSH", "3-5-1", "NYG", "5-3-1", "Prime Video", "2026-11-13T01:15Z"),
        g("LAC", "0-8", "BAL", "4-5", "ESPN", "2026-11-17T01:15Z"),
    )

    private val KC = "KC@ATL"; private val BUF = "BUF@NYJ"; private val IND = "MIA@IND"
    private val SEA = "SEA@LV"; private val LAR = "LAR@ARI"
    private val GB = "MIN@GB"; private val CLE = "HOU@CLE"; private val TEN = "JAX@TEN"; private val NO = "CAR@NO"

    /** Market, the CBS game(s) its antenna got, the FOX early game(s). Empty: not in the listings. */
    private val week10Listings: List<Triple<String, Set<String>, Set<String>>> = listOf(
        Triple("new-york", setOf(BUF), setOf(GB)),
        Triple("chicago", setOf(KC), setOf(GB)),
        Triple("pittsburgh", setOf(KC), setOf(CLE)),
        Triple("louisville", setOf(KC), setOf(GB)),
        Triple("columbus-oh", setOf(KC), emptySet()),
        Triple("birmingham", emptySet(), setOf(GB)),
        Triple("orlando", setOf(KC), setOf(GB, TEN)),
        Triple("st-louis", setOf(KC), setOf(GB)),
        Triple("memphis", setOf(KC), setOf(TEN)),
        Triple("atlanta", setOf(KC), setOf(GB)),
        Triple("dallas", setOf(KC), setOf(GB)),
        Triple("denver", setOf(KC), setOf(GB)),
        Triple("los-angeles", setOf(LAR), setOf(GB)),
        Triple("seattle", setOf(SEA), setOf(GB)),
        Triple("phoenix", setOf(LAR), setOf(GB)),
        Triple("detroit", setOf(KC), setOf(GB, CLE)),
        Triple("philadelphia", setOf(KC), setOf(GB)),
        Triple("boston", setOf(BUF), setOf(GB)),
        Triple("washington", setOf(KC), setOf(GB)),
        Triple("baltimore", setOf(KC), setOf(GB)),
        Triple("indianapolis", setOf(IND), setOf(GB)),
        Triple("cincinnati", setOf(KC), setOf(CLE)),
        Triple("cleveland", setOf(KC, SEA), setOf(CLE)),
        Triple("nashville", setOf(SEA), setOf(TEN)),
        Triple("new-orleans", setOf(SEA), setOf(NO)),
        Triple("houston", setOf(KC, SEA), setOf(CLE)),
        Triple("kansas-city", setOf(KC), setOf(GB)),
        Triple("minneapolis", setOf(SEA), setOf(GB)),
        Triple("milwaukee", setOf(SEA), setOf(GB)),
        Triple("buffalo", setOf(BUF), setOf(GB)),
        Triple("syracuse", setOf(BUF, KC), setOf(GB)),
        Triple("albany-ny", setOf(BUF), setOf(GB)),
        Triple("hartford", setOf(BUF), setOf(GB)),
        Triple("raleigh", setOf(KC), setOf(NO)),
        Triple("charlotte", setOf(KC, SEA), setOf(NO)),
        Triple("tampa", setOf(KC), setOf(GB)),
        Triple("jacksonville", setOf(SEA), setOf(TEN)),
        Triple("richmond", setOf(KC), setOf(GB)),
        Triple("norfolk", setOf(KC), setOf(GB)),
        Triple("harrisburg", setOf(KC), setOf(GB)),
        Triple("grand-rapids", setOf(KC), setOf(GB)),
        Triple("des-moines", setOf(KC), setOf(GB)),
        Triple("omaha", setOf(KC), setOf(GB)),
        Triple("oklahoma-city", setOf(KC), setOf(GB)),
        Triple("tulsa", setOf(KC), setOf(GB)),
        Triple("little-rock", setOf(KC), setOf(GB)),
        Triple("las-vegas", setOf(SEA), setOf(GB)),
        Triple("albuquerque", setOf(KC), setOf(GB)),
        Triple("el-paso", setOf(KC), setOf(GB)),
        Triple("san-antonio", setOf(KC), setOf(CLE)),
        Triple("salt-lake-city", setOf(KC), setOf(GB)),
        Triple("portland-or", setOf(SEA), setOf(GB)),
        Triple("sacramento", setOf(KC), setOf(GB)),
        Triple("san-diego", setOf(LAR), setOf(GB)),
        Triple("greenville-sc", setOf(KC), setOf(NO)),
        Triple("knoxville", setOf(KC), setOf(GB)),
        Triple("lexington", setOf(KC), setOf(GB)),
        Triple("evansville", setOf(IND), setOf(GB)),
        Triple("peoria", setOf(KC), setOf(GB)),
        Triple("green-bay", setOf(SEA), setOf(GB)),
        Triple("cedar-rapids", setOf(KC), setOf(GB)),
    )

    /** Week 11 (Nov 22): CBS has the doubleheader, FOX the single game. Not used to tune the rules. */
    private val week11 = listOf(
        g("MIA", "1-7", "BUF", "7-1", "CBS", "2026-11-22T18:00Z"),
        g("NO", "3-5", "CHI", "7-2", "Fox", "2026-11-22T18:00Z"),
        g("TEN", "1-7", "DAL", "4-5", "Fox", "2026-11-22T18:00Z"),
        g("TB", "3-6", "DET", "6-2", "Fox", "2026-11-22T18:00Z"),
        g("ARI", "3-6", "KC", "6-2", "CBS", "2026-11-22T18:00Z"),
        g("PIT", "6-2", "PHI", "2-7", "CBS", "2026-11-22T18:00Z"),
        g("BAL", "4-5", "CAR", "3-5", "Fox", "2026-11-22T18:00Z"),
        g("NYJ", "2-7", "LAC", "0-8", "Fox", "2026-11-22T21:05Z"),
        g("LV", "4-5", "DEN", "7-2", "CBS", "2026-11-22T21:25Z"),
        g("JAX", "6-2", "NYG", "5-3-1", "CBS", "2026-11-22T21:25Z"),
        g("MIN", "6-2", "SF", "7-1", "NBC", "2026-11-23T01:20Z"),
    )

    private class Listing(val market: String, val cbsEarly: Set<String>, val cbsLate: Set<String>, val fox: Set<String>)

    private fun setOf(vararg ids: String) = ids.toSet()

    /** CBS early, CBS late, and FOX's single game (early or late) in each city's antenna listings. */
    private val week11Listings = listOf(
        Listing("chicago", setOf("ARI@KC"), setOf("JAX@NYG"), setOf("NO@CHI")),
        Listing("pittsburgh", setOf("PIT@PHI"), setOf("JAX@NYG"), setOf("NO@CHI", "NYJ@LAC", "TEN@DAL")),
        Listing("louisville", setOf("PIT@PHI"), setOf("JAX@NYG"), setOf("NO@CHI")),
        Listing("atlanta", setOf("ARI@KC"), setOf("JAX@NYG"), setOf("TEN@DAL")),
        Listing("dallas", setOf("ARI@KC"), setOf("JAX@NYG"), setOf("TEN@DAL")),
        Listing("denver", setOf("ARI@KC"), setOf("LV@DEN"), setOf("NO@CHI")),
        Listing("los-angeles", setOf("ARI@KC"), setOf("JAX@NYG"), setOf("NYJ@LAC")),
        Listing("seattle", setOf("ARI@KC"), setOf("JAX@NYG"), setOf("TEN@DAL")),
        Listing("detroit", setOf("ARI@KC", "PIT@PHI"), setOf("JAX@NYG"), setOf("NYJ@LAC", "TB@DET")),
        Listing("philadelphia", setOf("PIT@PHI"), setOf("JAX@NYG"), setOf()),
        Listing("boston", setOf("MIA@BUF"), setOf("JAX@NYG"), setOf("NYJ@LAC", "TEN@DAL")),
        Listing("washington", setOf("PIT@PHI"), setOf("JAX@NYG"), setOf("BAL@CAR")),
        Listing("indianapolis", setOf("ARI@KC"), setOf("JAX@NYG"), setOf("NO@CHI")),
        Listing("cincinnati", setOf("PIT@PHI"), setOf("JAX@NYG"), setOf("NO@CHI")),
        Listing("cleveland", setOf("PIT@PHI"), setOf("JAX@NYG"), setOf("NO@CHI")),
        Listing("nashville", setOf("ARI@KC"), setOf("JAX@NYG"), setOf("TEN@DAL")),
        Listing("new-orleans", setOf("ARI@KC"), setOf("JAX@NYG"), setOf("NO@CHI")),
        Listing("houston", setOf("ARI@KC"), setOf("JAX@NYG"), setOf("TEN@DAL")),
        Listing("kansas-city", setOf("ARI@KC"), setOf("LV@DEN"), setOf("NYJ@LAC")),
        Listing("minneapolis", setOf("ARI@KC"), setOf("JAX@NYG"), setOf("NO@CHI")),
        Listing("buffalo", setOf("MIA@BUF"), setOf("JAX@NYG"), setOf()),
        Listing("syracuse", setOf("MIA@BUF", "PIT@PHI"), setOf("JAX@NYG"), setOf("NYJ@LAC")),
        Listing("raleigh", setOf("PIT@PHI"), setOf("JAX@NYG"), setOf("NYJ@LAC")),
        Listing("charlotte", setOf("PIT@PHI"), setOf("JAX@NYG"), setOf("BAL@CAR")),
        Listing("tampa", setOf("ARI@KC"), setOf("JAX@NYG"), setOf("TB@DET")),
        Listing("jacksonville", setOf("ARI@KC"), setOf("JAX@NYG"), setOf("TB@DET")),
        Listing("richmond", setOf("PIT@PHI"), setOf("JAX@NYG"), setOf("TEN@DAL")),
        Listing("harrisburg", setOf("PIT@PHI"), setOf("JAX@NYG"), setOf("BAL@CAR")),
        Listing("omaha", setOf("ARI@KC"), setOf("LV@DEN"), setOf("NO@CHI")),
        Listing("oklahoma-city", setOf("ARI@KC"), setOf("JAX@NYG"), setOf("TEN@DAL")),
        Listing("las-vegas", setOf("ARI@KC"), setOf("LV@DEN"), setOf("TEN@DAL")),
        Listing("salt-lake-city", setOf("ARI@KC"), setOf("LV@DEN"), setOf("NO@CHI")),
        Listing("portland-or", setOf("ARI@KC"), setOf("JAX@NYG"), setOf("TEN@DAL")),
        Listing("sacramento", setOf("ARI@KC"), setOf("JAX@NYG", "LV@DEN"), setOf("TEN@DAL")),
        Listing("san-diego", setOf("ARI@KC"), setOf("JAX@NYG"), setOf("NYJ@LAC")),
        Listing("knoxville", setOf("ARI@KC"), setOf("JAX@NYG"), setOf("TEN@DAL")),
        Listing("evansville", setOf("ARI@KC"), setOf("JAX@NYG"), setOf("NO@CHI")),
        Listing("milwaukee", setOf("ARI@KC"), setOf("JAX@NYG"), setOf("NO@CHI")),
        Listing("columbus-oh", setOf("PIT@PHI"), setOf("JAX@NYG"), setOf("NO@CHI")),
        Listing("st-louis", setOf("ARI@KC"), setOf("LV@DEN"), setOf("NO@CHI")),
        Listing("memphis", setOf("ARI@KC"), setOf("JAX@NYG"), setOf("TEN@DAL")),
        Listing("birmingham", setOf("ARI@KC"), setOf("JAX@NYG"), setOf("TEN@DAL")),
        Listing("orlando", setOf("ARI@KC"), setOf("JAX@NYG"), setOf("TB@DET")),
    )

    /** Share of [observed] (market, window title, games aired) the estimate for [games] gets right. */
    private fun score(label: String, games: List<Game>, observed: List<Triple<String, String, Set<String>>>): Double {
        val coverage = Coverage.nflWeek(games)
        val misses = observed.filter { (market, title, aired) ->
            aired.isNotEmpty() && coverage.windows.first { it.title == title }.airing[market] !in aired
        }
        val checked = observed.count { it.third.isNotEmpty() }
        val rate = 1.0 - misses.size.toDouble() / checked
        println("$label: matched ${checked - misses.size} of $checked (${"%.0f".format(rate * 100)}%)")
        misses.forEach { (market, title, aired) ->
            println("  miss: $market $title: estimated ${coverage.windows.first { it.title == title }.airing[market]}, aired $aired")
        }
        return rate
    }

    @Test fun matchesTheListingsItWasTunedOn() {
        val observed = week10Listings.flatMap { (market, cbs, fox) ->
            listOf(Triple(market, "CBS single game", cbs), Triple(market, "FOX early", fox))
        }
        assertTrue(score("Week 10 (tuned on)", week10, observed) >= 0.88)
    }

    @Test fun mostlyMatchesAWeekItWasNotTunedOn() {
        val observed = week11Listings.flatMap {
            listOf(
                Triple(it.market, "CBS early", it.cbsEarly),
                Triple(it.market, "CBS late", it.cbsLate),
                Triple(it.market, "FOX single game", it.fox),
            )
        }
        assertTrue(score("Week 11 (blind)", week11, observed) >= 0.70)
    }
}
