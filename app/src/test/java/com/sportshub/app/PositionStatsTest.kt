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

    private val passing = table("passing", listOf("GP", "CMP", "ATT", "YDS"), "17", "350", "520", "4,100")
    private val rushing = table("rushing", listOf("GP", "CAR", "YDS"), "17", "80", "450")
    private val receiving = table("receiving", listOf("GP", "REC", "YDS"), "17", "1", "-4")
    private val fumbles = table("fumbles", listOf("GP", "FUM", "LST"), "17", "4", "2")
    private val oneTackle = table("defensive", listOf("GP", "TOT", "SOLO", "SACK"), "17", "1", "1", "0")
    private val realDefense = table("defensive", listOf("GP", "TOT", "SOLO", "SACK"), "17", "48", "30", "1")
    private val oneInterception = table("defensiveInterceptions", listOf("GP", "INT", "YDS"), "17", "1", "0")
    private val kicking = table("kicking", listOf("GP", "FGM", "FGA", "FG%", "LNG"), "17", "0", "0", "0.0", "0")

    private fun keys(tables: List<CareerTable>) = tables.map { it.key }

    @Test fun quarterbackSkipsStrayCatchAndTackle() {
        val shown = PositionStats.relevant(listOf(passing, rushing, receiving, fumbles, oneTackle, kicking), "QB")
        assertEquals(listOf("passing", "rushing", "fumbles"), keys(shown))
    }

    @Test fun receiverSkipsDefenseFromATurnover() {
        val shown = PositionStats.relevant(listOf(receiving, rushing, oneTackle, oneInterception), "WR")
        assertEquals(listOf("receiving", "rushing"), keys(shown))
    }

    @Test fun twoWayPlayerKeepsBothSides() {
        val catches = table("receiving", listOf("GP", "REC", "YDS"), "17", "60", "800")
        val picks = table("defensiveInterceptions", listOf("GP", "INT", "YDS"), "17", "3", "40")
        val shown = PositionStats.relevant(listOf(catches, realDefense, picks), "WR")
        assertEquals(listOf("receiving", "defensive", "defensiveInterceptions"), keys(shown))
    }

    @Test fun defenderSkipsOffenseUnlessTheyPlayIt() {
        val fakePunt = table("rushing", listOf("GP", "CAR", "YDS"), "17", "1", "12")
        val shown = PositionStats.relevant(listOf(realDefense, oneInterception, fakePunt, fumbles), "cb")
        assertEquals(listOf("defensive", "defensiveInterceptions", "fumbles"), keys(shown))
    }

    @Test fun unknownPositionShowsEverything() {
        val all = listOf(passing, oneTackle, kicking)
        assertEquals(all, PositionStats.relevant(all, null))
        assertEquals(all, PositionStats.relevant(all, "ATH"))
    }

    @Test fun kickerKeepsKicking() {
        val made = table("kicking", listOf("GP", "FGM", "FGA", "FG%"), "17", "30", "34", "88.2")
        val shown = PositionStats.relevant(listOf(made, oneTackle), "K")
        assertEquals(listOf("kicking"), keys(shown))
    }
}
