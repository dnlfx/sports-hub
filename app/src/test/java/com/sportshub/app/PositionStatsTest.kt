package com.sportshub.app

import com.sportshub.app.data.PositionStats
import com.sportshub.app.model.CareerRow
import com.sportshub.app.model.CareerTable
import org.junit.Assert.assertEquals
import org.junit.Test

class PositionStatsTest {
    private fun table(key: String, columns: List<String>, vararg totals: String) = CareerTable(
        key = key,
        title = key,
        columns = columns,
        seasons = listOf(CareerRow("2026", "BUF", totals.toList())),
        totals = CareerRow("Career", null, totals.toList()),
    )

    /** A table with one row per season (newest first), and no totals row. */
    private fun seasons(key: String, columns: List<String>, vararg rows: Pair<String, List<String>>) = CareerTable(
        key = key,
        title = key,
        columns = columns,
        seasons = rows.map { (year, values) -> CareerRow(year, "JAX", values) },
        totals = null,
    )

    private val passing = table("passing", listOf("GP", "CMP", "ATT", "YDS"), "17", "350", "520", "4,100")
    private val rushing = table("rushing", listOf("GP", "CAR", "YDS"), "17", "80", "450")
    private val receiving = table("receiving", listOf("GP", "REC", "YDS"), "17", "1", "-4")
    private val fumbles = table("fumbles", listOf("GP", "FUM", "LST"), "17", "4", "2")
    private val oneTackle = table("defensive", listOf("GP", "TOT", "SOLO", "SACK"), "17", "1", "1", "0")
    private val realDefense = table("defensive", listOf("GP", "TOT", "SOLO", "SACK"), "17", "48", "30", "1")
    private val oneInterception = table("defensiveInterceptions", listOf("GP", "INT", "YDS"), "17", "1", "0")
    private val kicking = table("kicking", listOf("GP", "FGM", "FGA", "FG%", "LNG"), "17", "0", "0", "0.0", "0")

    private fun keys(tables: List<CareerTable>) = tables.map { it.key }

    // ESPN's NFL labels.
    private val defenseColumns = listOf("GP", "TOT", "SOLO", "AST", "SACK", "FF", "FR", "YDS", "INT", "INT YDS", "AVG", "TD", "LNG", "PD", "STF", "STFYDS", "KB")
    private val passingColumns = listOf("GP", "CMP", "ATT", "CMP%", "YDS", "AVG", "TD", "INT", "LNG", "SACK", "RTG", "QBR")
    private val receivingColumns = listOf("GP", "REC", "TGTS", "YDS", "AVG", "TD", "LNG", "FD", "FUM", "LST")

    @Test fun quarterbackSkipsStrayCatchAndTackle() {
        val shown = PositionStats.relevant(listOf(passing, rushing, receiving, fumbles, oneTackle, kicking), "QB")
        assertEquals(listOf("passing", "rushing", "fumbles"), keys(shown))
    }

    @Test fun veteranQuarterbackHidesTacklesAfterInterceptions() {
        // Thirteen career tackles over eight seasons, all chasing down interception returns.
        val defense = seasons(
            "defensive", defenseColumns,
            "2025" to listOf("17", "3", "3", "0", "0", "1", "0", "0", "0", "0", "0.0", "0", "0", "0", "0", "0", "0"),
            "2024" to listOf("17", "2", "2", "0", "0", "0", "0", "0", "0", "0", "0.0", "0", "0", "0", "0", "0", "0"),
            "2023" to listOf("16", "4", "3", "1", "0", "1", "0", "0", "0", "0", "0.0", "0", "0", "0", "0", "0", "0"),
            "2022" to listOf("12", "4", "4", "0", "0", "0", "0", "0", "0", "0", "0.0", "0", "0", "0", "0", "0", "0"),
        )
        val shown = PositionStats.relevant(listOf(passing, rushing, defense), "QB")
        assertEquals(listOf("passing", "rushing"), keys(shown))
    }

    @Test fun receiverSkipsDefenseFromATurnover() {
        val shown = PositionStats.relevant(listOf(receiving, rushing, oneTackle, oneInterception), "WR")
        assertEquals(listOf("receiving", "rushing"), keys(shown))
    }

    @Test fun receiverHidesZeroPassingAndKickingLines() {
        // ESPN sends a line of zeros for every category, kicking included.
        val noThrows = table("passing", passingColumns, "7", "0", "0", "0.0", "0", "0.0", "0", "0", "0", "0", "39.6", "-")
        val noKicks = table("kicking", listOf("GP", "FG", "FG%", "1-19", "XPM", "XPA"), "7", "0-0", "0.0", "0-0", "0", "0")
        val catches = table("receiving", receivingColumns, "7", "28", "45", "298", "10.6", "1", "44", "12", "1", "0")
        assertEquals(listOf("receiving"), keys(PositionStats.relevant(listOf(catches, noThrows, noKicks), "WR")))
        // Same with no position to go on.
        assertEquals(listOf("receiving"), keys(PositionStats.relevant(listOf(catches, noThrows, noKicks), null)))
    }

    @Test fun travisHunterKeepsBothSides() {
        // His NFL numbers: a receiver by position, with a real cornerback workload.
        val catches = seasons(
            "receiving", receivingColumns,
            "2026" to listOf("6", "6", "6", "76", "12.7", "1", "26", "4", "0", "0"),
            "2025" to listOf("7", "28", "45", "298", "10.6", "1", "44", "12", "1", "0"),
        )
        val defense = seasons(
            "defensive", defenseColumns,
            "2026" to listOf("6", "22", "14", "8", "0", "0", "0", "0", "2", "0", "0.0", "0", "0", "6", "1", "0", "0"),
            "2025" to listOf("7", "15", "11", "4", "0", "0", "0", "0", "0", "0", "0.0", "0", "0", "3", "0", "0", "0"),
        )
        assertEquals(listOf("receiving", "defensive"), keys(PositionStats.relevant(listOf(catches, defense), "WR")))
        // His rookie half season alone is enough.
        val rookie = listOf(catches.copy(seasons = catches.seasons.drop(1)), defense.copy(seasons = defense.seasons.drop(1)))
        assertEquals(listOf("receiving", "defensive"), keys(PositionStats.relevant(rookie, "WR")))
    }

    @Test fun collegeTwoWayPlayerKeepsBothSides() {
        val catches = seasons("receiving", listOf("REC", "YDS", "AVG", "TD", "LNG"), "2024" to listOf("96", "1,258", "13.1", "15", "58"))
        val defense = seasons(
            "defensive", listOf("TOT", "SOLO", "AST", "PD", "SACK", "FF", "INT", "YDS", "TD"),
            "2024" to listOf("35", "24", "11", "11", "0", "1", "4", "65", "0"),
        )
        assertEquals(listOf("receiving", "defensive"), keys(PositionStats.relevant(listOf(catches, defense), "WR")))
        assertEquals(listOf("receiving", "defensive"), keys(PositionStats.relevant(listOf(catches, defense), "ATH")))
    }

    @Test fun defenderSkipsOffenseUnlessTheyPlayIt() {
        val fakePunt = table("rushing", listOf("GP", "CAR", "YDS"), "17", "1", "12")
        val shown = PositionStats.relevant(listOf(realDefense, oneInterception, fakePunt, fumbles), "cb")
        assertEquals(listOf("defensive", "defensiveInterceptions", "fumbles"), keys(shown))
    }

    @Test fun unknownPositionGoesByTheStats() {
        val shown = PositionStats.relevant(listOf(passing, oneTackle, kicking), null)
        assertEquals(listOf("passing"), keys(shown))
        assertEquals(listOf("passing"), keys(PositionStats.relevant(listOf(passing, oneTackle, kicking), "ATH")))
    }

    @Test fun nothingToGoOnShowsEverythingWithNumbers() {
        val scoring = table("scoring", listOf("GP", "TD", "PTS"), "3", "1", "6")
        assertEquals(listOf("scoring"), keys(PositionStats.relevant(listOf(scoring, kicking), null)))
    }

    @Test fun kickerKeepsKicking() {
        val made = table("kicking", listOf("GP", "FGM", "FGA", "FG%"), "17", "30", "34", "88.2")
        val shown = PositionStats.relevant(listOf(made, oneTackle), "K")
        assertEquals(listOf("kicking"), keys(shown))
    }

    @Test fun emptyColumnsAreDropped() {
        val defense = table(
            "defensive", defenseColumns,
            "17", "48", "30", "18", "1.5", "0", "0", "0", "0", "0", "0.0", "0", "0", "4", "0", "0", "0",
        )
        val shown = PositionStats.relevant(listOf(defense), "LB").single()
        // Headline columns stay at zero; stuffs, blocked kicks and return columns go.
        assertEquals(listOf("GP", "TOT", "SOLO", "AST", "SACK", "INT", "PD"), shown.columns)
        assertEquals(listOf("17", "48", "30", "18", "1.5", "0", "4"), shown.totals?.values)
        assertEquals(shown.columns.size, shown.seasons.single().values.size)
    }

    @Test fun emptySeasonsAreDropped() {
        val rush = seasons(
            "rushing", listOf("GP", "CAR", "YDS", "AVG"),
            "2026" to listOf("6", "0", "0", "0.0"),
            "2025" to listOf("7", "12", "40", "3.3"),
        )
        assertEquals(listOf("2025"), PositionStats.relevant(listOf(rush), "RB").single().seasons.map { it.label })
    }

    @Test fun basketballTablesAllStay() {
        val averages = table("averages", listOf("GP", "MIN", "PTS", "REB"), "70", "35.1", "25.0", "7.8")
        val misc = table("miscellaneous", listOf("DD2", "TD3", "DQ", "EJECT"), "30", "5", "0", "0")
        val shown = PositionStats.relevant(listOf(averages, misc), "F")
        assertEquals(listOf("averages", "miscellaneous"), keys(shown))
        assertEquals(listOf("DD2", "TD3"), shown[1].columns)
    }
}
