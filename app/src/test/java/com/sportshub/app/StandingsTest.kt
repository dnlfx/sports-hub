package com.sportshub.app

import com.sportshub.app.data.espn.EspnTeamParser
import com.sportshub.app.model.Sport
import com.sportshub.app.model.Team
import com.sportshub.app.model.TeamListing
import com.sportshub.app.ui.team.TeamGroup
import com.sportshub.app.ui.team.formatPercent
import com.sportshub.app.ui.team.gamesBehind
import com.sportshub.app.ui.team.ordinal
import com.sportshub.app.ui.team.parseRecord
import com.sportshub.app.ui.team.rankGroup
import com.sportshub.app.ui.team.winPercent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StandingsTest {
    private fun resource(name: String) =
        requireNotNull(javaClass.classLoader?.getResource(name)) { "missing $name" }.readText()

    private fun listing(id: String, abbr: String, name: String) = TeamListing(Team(id, name, abbr), name)

    private fun group(title: String, vararg teams: TeamListing) = TeamGroup(title, "", title, teams.toList())

    private val nfl = EspnTeamParser.parseStandings(resource("espn_standings.json"))

    @Test fun readsEveryTeamAcrossNestedDivisions() {
        assertEquals(8, nfl.size)
        val bills = nfl.getValue("2")
        assertEquals(6, bills.wins)
        assertEquals(1, bills.losses)
        assertEquals("6-1", bills.overall)
        assertEquals("2-0", bills.division)
        assertEquals("2-2", bills.conference)
        assertEquals("W3", bills.streak)
        assertEquals(1, bills.seed)
        assertEquals(0, bills.order)
        assertEquals(1, nfl.getValue("23").ties)
        assertEquals("4-3-1", nfl.getValue("23").overall)
    }

    @Test fun ranksADivisionByRecordWithGamesBehind() {
        // Listed alphabetically, as the division table in TeamGroups has them.
        val east = group(
            "AFC East",
            listing("2", "BUF", "Bills"), listing("15", "MIA", "Dolphins"),
            listing("17", "NE", "Patriots"), listing("20", "NYJ", "Jets"),
        )
        val ranked = rankGroup(Sport.NFL, east, nfl)
        assertTrue(ranked.ranked)
        assertEquals(listOf("BUF", "NE", "NYJ", "MIA"), ranked.rows.map { it.listing.team.abbreviation })
        assertEquals(listOf(1, 2, 3, 4), ranked.rows.map { it.rank })
        assertEquals(listOf("-", "1.5", "4.5", "5.5"), ranked.rows.map { it.gamesBehind })
    }

    @Test fun levelTeamsKeepEspnsTieBreakOrder() {
        val north = group(
            "AFC North",
            listing("33", "BAL", "Ravens"), listing("4", "CIN", "Bengals"),
            listing("5", "CLE", "Browns"), listing("23", "PIT", "Steelers"),
        )
        val ranked = rankGroup(Sport.NFL, north, nfl)
        assertEquals(listOf("CLE", "BAL", "PIT", "CIN"), ranked.rows.map { it.listing.team.abbreviation })
        assertEquals(listOf("-", "-", "0.5", "3"), ranked.rows.map { it.gamesBehind })
    }

    @Test fun nbaBreaksTiesBySeedSinceItsListRunsWorstToBest() {
        val nba = EspnTeamParser.parseStandings(resource("nba_standings.json"))
        val central = group(
            "East · Central",
            listing("4", "CHI", "Bulls"), listing("5", "CLE", "Cavaliers"), listing("8", "DET", "Pistons"),
            listing("11", "IND", "Pacers"), listing("15", "MIL", "Bucks"),
        )
        val ranked = rankGroup(Sport.NBA, central, nba)
        assertEquals(listOf("IND", "CLE", "DET", "CHI", "MIL"), ranked.rows.map { it.listing.team.abbreviation })
        assertEquals(listOf("-", "1", "2.5", "2.5", "4.5"), ranked.rows.map { it.gamesBehind })
    }

    @Test fun collegeRanksByConferenceRecord() {
        val cfb = EspnTeamParser.parseStandings(resource("cfb_standings.json"))
        // College lists "wins" both overall and in conference: the overall one is kept.
        assertEquals(7, cfb.getValue("61").wins)
        assertEquals("5-0", cfb.getValue("61").conference)
        val sec = group(
            "SEC",
            listing("333", "ALA", "Alabama"), listing("8", "ARK", "Arkansas"), listing("61", "UGA", "Georgia"),
            listing("2633", "TENN", "Tennessee"), listing("238", "VAN", "Vanderbilt"),
        )
        val ranked = rankGroup(Sport.NCAAF, sec, cfb)
        // Georgia and Tennessee are both unbeaten in conference; ESPN's order puts Georgia first.
        // Vanderbilt, yet to play a conference game, sits above winless Arkansas.
        assertEquals(listOf("UGA", "TENN", "ALA", "VAN", "ARK"), ranked.rows.map { it.listing.team.abbreviation })
        assertEquals(listOf(1, 2, 3, 4, 5), ranked.rows.map { it.rank })
        assertTrue(ranked.rows.all { it.gamesBehind == null })

        val independent = rankGroup(Sport.NCAAF, group("Independent", listing("87", "ND", "Notre Dame")), cfb)
        assertFalse(independent.ranked)
    }

    @Test fun teamsStayUnrankedWithoutStandingsOrBeforeTheSeason() {
        val east = group("AFC East", listing("2", "BUF", "Bills"), listing("15", "MIA", "Dolphins"))
        val none = rankGroup(Sport.NFL, east, emptyMap())
        assertFalse(none.ranked)
        assertEquals(listOf("BUF", "MIA"), none.rows.map { it.listing.team.abbreviation })

        val preseason = nfl.mapValues { (_, s) -> s.copy(wins = 0, losses = 0, ties = 0) }
        assertFalse(rankGroup(Sport.NFL, east, preseason).ranked)
    }

    @Test fun teamMissingFromStandingsGoesLast() {
        val east = group("AFC East", listing("99", "XXX", "Aardvarks"), listing("15", "MIA", "Dolphins"), listing("2", "BUF", "Bills"))
        val ranked = rankGroup(Sport.NFL, east, nfl)
        assertEquals(listOf("BUF", "MIA", "XXX"), ranked.rows.map { it.listing.team.abbreviation })
        assertNull(ranked.rows.last().rank)
    }

    @Test fun formatsStandingsNumbers() {
        assertEquals(Triple(4, 3, 1), parseRecord("4-3-1"))
        assertEquals(Triple(6, 1, 0), parseRecord("6-1"))
        assertNull(parseRecord("-"))
        assertEquals(".563", formatPercent(winPercent(4, 3, 1)))
        assertEquals("1.000", formatPercent(winPercent(3, 0)))
        assertEquals(".000", formatPercent(winPercent(0, 2)))
        assertEquals("-", formatPercent(winPercent(0, 0)))
        assertEquals(listOf("1st", "2nd", "3rd", "4th", "11th", "12th", "13th", "21st", "22nd"), listOf(1, 2, 3, 4, 11, 12, 13, 21, 22).map(::ordinal))
        val leader = nfl.getValue("2")
        assertEquals("-", gamesBehind(leader, leader))
    }
}
