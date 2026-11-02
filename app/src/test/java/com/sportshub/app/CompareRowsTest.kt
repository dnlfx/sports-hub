package com.sportshub.app

import com.sportshub.app.model.BioItem
import com.sportshub.app.model.CareerRow
import com.sportshub.app.model.CareerTable
import com.sportshub.app.model.PlayerProfile
import com.sportshub.app.model.SeasonStat
import com.sportshub.app.model.SeasonStatGroup
import com.sportshub.app.model.Team
import com.sportshub.app.model.TeamProfile
import com.sportshub.app.model.TeamSeasonStats
import com.sportshub.app.ui.compare.PlayerSide
import com.sportshub.app.ui.compare.TeamSide
import com.sportshub.app.ui.compare.comparePlayers
import com.sportshub.app.ui.compare.compareRow
import com.sportshub.app.ui.compare.compareTeams
import com.sportshub.app.ui.compare.edges
import com.sportshub.app.ui.compare.statNumber
import com.sportshub.app.ui.compare.winPct
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CompareRowsTest {

    @Test fun readsStatNumbers() {
        assertEquals(2104.0, statNumber("2,104")!!, 0.0)
        assertEquals(65.3, statNumber("65.3%")!!, 0.0)
        assertEquals(5.0, statNumber("+5")!!, 0.0)
        assertEquals(-3.0, statNumber("-3")!!, 0.0)
        assertNull(statNumber("6' 2\""))
        assertNull(statNumber("21/30"))
        assertNull(statNumber(null))
    }

    @Test fun readsRecords() {
        assertEquals(0.75, winPct("6-2")!!, 0.0001)
        assertEquals(0.6111, winPct("5-3-1")!!, 0.0001)
        assertNull(winPct("0-0"))
        assertNull(winPct("Bye"))
    }

    @Test fun picksTheBetterSide() {
        assertEquals(-1, compareRow("k", "Yards", "300", "250").edge)
        assertEquals(1, compareRow("k", "Picks", "3", "1", lowerIsBetter = true).edge)
        assertEquals(0, compareRow("k", "Age", "27", "30", neutral = true).edge)
        assertEquals(0, compareRow("k", "Even", "10", "10").edge)
        assertEquals(0.6f, compareRow("k", "Yards", "300", "200").share!!, 0.0001f)
        assertNull(compareRow("k", "Diff", "+5", "-3").share)
        assertNull(compareRow("k", "Diff", "5", "3", bar = false).share)
        assertEquals("–", compareRow("k", "Missing", null, "3").a)
    }

    private fun team(id: String, record: String) = Team(id, id, id.uppercase(), record = record)

    private fun stats(vararg stats: Pair<String, SeasonStat>) =
        TeamSeasonStats(2026, stats.groupBy({ it.first }, { it.second }).map { (key, list) -> SeasonStatGroup(key, key, list) })

    @Test fun comparesTeams() {
        val a = TeamSide(
            team("buf", "6-2"),
            TeamProfile(team("buf", "6-2"), "Buffalo Bills", null, pointsForPerGame = 27.5, pointsAgainstPerGame = 18.0, pointDifferential = 76),
            stats(
                "offense" to SeasonStat("netPassingYards", "Passing yards", "2,104", perGame = "263.0"),
                "offense" to SeasonStat("interceptions", "Interceptions thrown", "4"),
                "offense" to SeasonStat("onlyHere", "Only here", "1"),
            ),
        )
        val b = TeamSide(
            team("mia", "3-5"),
            TeamProfile(team("mia", "3-5"), "Miami Dolphins", null, pointsForPerGame = 20.1, pointsAgainstPerGame = 24.0, pointDifferential = -31),
            stats(
                "offense" to SeasonStat("netPassingYards", "Passing yards", "1,950", perGame = "278.6"),
                "offense" to SeasonStat("interceptions", "Interceptions thrown", "9"),
            ),
        )
        val groups = compareTeams(a, b)
        assertEquals(listOf("season", "offense"), groups.map { it.key })
        val season = groups[0].rows.associateBy { it.key }
        assertEquals(-1, season.getValue("record").edge)
        assertEquals(-1, season.getValue("pa").edge)
        assertEquals("+76", season.getValue("diff").a)
        assertNull(season.getValue("diff").share)
        val offense = groups[1].rows.associateBy { it.key }
        // Per game when both have it, so a bye week doesn't skew it.
        val passing = offense.getValue("offense:netPassingYards")
        assertEquals("263.0", passing.a)
        assertEquals("Passing yards a game", passing.label)
        assertEquals(1, passing.edge)
        // Fewer interceptions thrown is better.
        assertEquals(-1, offense.getValue("offense:interceptions").edge)
        // Stats only one team has are left out.
        assertEquals(2, offense.size)
        assertEquals(5 to 1, edges(groups))
    }

    private fun player(id: String, bio: List<BioItem>) =
        PlayerProfile(id, id, null, null, "QB", "Quarterback", null, bio, null, emptyList())

    private fun passing(season: String, vararg values: String) = CareerTable(
        key = "passing",
        title = "Passing",
        columns = listOf("GP", "YDS", "TD", "INT"),
        seasons = listOf(CareerRow(season, "BUF", values.toList())),
        totals = CareerRow("Career", null, listOf("100", "30,000", "220", "90")),
    )

    @Test fun comparesPlayers() {
        val a = PlayerSide(player("a", listOf(BioItem("Age", "30"), BioItem("Height", "6' 5\""))), listOf(passing("2026", "8", "2,100", "16", "4")))
        val b = PlayerSide(
            player("b", listOf(BioItem("Age", "27"))),
            listOf(
                passing("2025", "9", "2,000", "18", "7"),
                CareerTable("rushing", "Rushing", listOf("YDS"), listOf(CareerRow("2025", null, listOf("300"))), null),
            ),
        )
        val season = comparePlayers(a, b, career = false)
        assertEquals(listOf("bio", "passing"), season.map { it.key })
        val bio = season[0].rows.associateBy { it.label }
        assertEquals(0, bio.getValue("Age").edge)
        assertEquals("–", bio.getValue("Height").b)
        val pass = season[1]
        assertEquals("2026 vs 2025", pass.note)
        val rows = pass.rows.associateBy { it.label }
        assertEquals(0, rows.getValue("GP").edge)
        assertEquals(-1, rows.getValue("YDS").edge)
        assertEquals(1, rows.getValue("TD").edge)
        assertEquals(-1, rows.getValue("INT").edge)

        val career = comparePlayers(a, b, career = true)
        assertEquals("Career", career[1].note)
        assertEquals("30,000", career[1].rows.first { it.label == "YDS" }.a)
    }
}
