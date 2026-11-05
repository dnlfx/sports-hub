package com.sportshub.app

import com.sportshub.app.data.BasketballStats
import com.sportshub.app.data.espn.EspnParser
import com.sportshub.app.model.PlayerStatLine
import com.sportshub.app.model.PlayerStatTable
import org.junit.Assert.assertEquals
import org.junit.Test

class NbaStatsTest {
    private val columns = listOf("MIN", "FG", "3PT", "FT", "OREB", "DREB", "REB", "AST", "STL", "BLK", "TO", "PF", "+/-", "PTS")

    private fun game(vararg lines: Pair<String, List<String>>) = listOf(
        PlayerStatTable(
            EspnParser.BASKETBALL_TABLE, "Players", columns,
            lines.map { (id, values) -> PlayerStatLine(id, "Player $id", null, null, values, starter = true) },
        ),
    )

    @Test fun addsUpPerGameAveragesAndTotals() {
        val games = listOf(
            game("a" to listOf("30", "10-20", "2-5", "4-4", "1", "5", "6", "4", "1", "0", "2", "3", "+6", "26"),
                "b" to listOf("20", "2-4", "0-0", "0-2", "2", "2", "4", "1", "0", "2", "1", "2", "-3", "4")),
            game("a" to listOf("34", "8-18", "3-8", "6-8", "0", "4", "4", "6", "2", "1", "4", "2", "-2", "25")),
        )
        val (perGame, totals) = BasketballStats.seasonTotals(games)
        assertEquals(listOf("GP", "MIN", "PTS", "REB", "AST", "STL", "BLK", "TO", "FG%", "3P%", "FT%"), perGame.columns)
        assertEquals(listOf("a", "b"), perGame.players.map { it.athleteId })
        assertEquals(listOf("2", "32.0", "25.5", "5.0", "5.0", "1.5", "0.5", "3.0", "47.4", "38.5", "83.3"), perGame.players[0].values)
        // Never shot a free throw that went in, never shot a three.
        assertEquals("-", perGame.players[1].values[9])
        assertEquals("0.0", perGame.players[1].values[10])

        assertEquals(listOf("2", "64", "51", "10", "10", "3", "1", "6", "18-38", "5-13", "10-12", "+4"), totals.players[0].values)
        assertEquals("-3", totals.players[1].values.last())
    }

    @Test fun noGamesMeansNoTables() {
        assertEquals(emptyList<PlayerStatTable>(), BasketballStats.seasonTotals(emptyList()))
    }

    @Test fun oneTableWhenLineupsAreUnknown() {
        val table = game("a" to List(14) { "1" }).single().let { t -> t.copy(players = t.players.map { it.copy(starter = false) }) }
        val shown = BasketballStats.forDisplay(listOf(table))
        assertEquals(listOf("Box score"), shown.map { it.title })
    }
}

class DayStripTest {
    private val d = java.time.LocalDate.of(2026, 11, 5)

    @Test fun usesTheCalendarAndKeepsTheShownDays() {
        val calendar = listOf(d.minusDays(1), d.plusDays(2))
        assertEquals(listOf(d.minusDays(3), d.minusDays(1), d, d.plusDays(2)), com.sportshub.app.ui.scores.stripDays(calendar, d, d.minusDays(3)))
    }

    @Test fun twoWeeksEitherSideWithoutACalendar() {
        val days = com.sportshub.app.ui.scores.stripDays(emptyList(), d, null)
        assertEquals(29, days.size)
        assertEquals(d.minusDays(14), days.first())
        assertEquals(d.plusDays(14), days.last())
    }
}
