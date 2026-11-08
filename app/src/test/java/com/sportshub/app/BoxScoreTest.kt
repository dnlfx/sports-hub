package com.sportshub.app

import com.sportshub.app.data.BoxScore
import com.sportshub.app.model.PlayerStatLine
import com.sportshub.app.model.PlayerStatTable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BoxScoreTest {
    private fun p(id: String, vararg values: String) = PlayerStatLine(id, "Player $id", null, null, values.toList())
    private fun t(key: String, columns: List<String>, vararg players: PlayerStatLine) =
        PlayerStatTable(key, key, columns, players.toList())

    private val passing = t("passing", listOf("C/ATT", "YDS", "AVG", "TD", "INT", "SACKS", "RTG"), p("qb", "20/30", "250", "8.3", "2", "1", "2-14", "101.2"))
    private val rushing = t("rushing", listOf("CAR", "YDS", "AVG", "TD", "LONG"), p("rb", "15", "80", "5.3", "1", "22"), p("qb", "3", "12", "4.0", "0", "8"))
    private val receiving = t("receiving", listOf("REC", "YDS", "AVG", "TD", "LONG", "TGTS"), p("wr", "6", "90", "15.0", "1", "40", "9"))
    private val fumbles = t("fumbles", listOf("FUM", "LOST", "REC"), p("wr", "1", "1", "0"), p("lb", "0", "0", "1"))
    private val defensive = t("defensive", listOf("TOT", "SOLO", "SACKS", "TFL", "PD", "QB HTS", "TD"), p("lb", "9", "6", "1", "2", "0", "1", "0"))
    private val interceptions = t("interceptions", listOf("INT", "YDS", "TD"), p("cb", "1", "25", "0"))
    private val kicking = t("kicking", listOf("FG", "PCT", "LONG", "XP", "PTS"), p("k", "2/3", "66.7", "48", "3/3", "9"))
    private val punting = t("punting", listOf("NO", "YDS"), p("p", "4", "180"))
    private val kickReturns = t("kickReturns", listOf("NO", "YDS"), p("kr", "2", "50"))
    private val game = listOf(passing, rushing, receiving, fumbles, defensive, interceptions, kicking, punting, kickReturns)

    @Test fun foldKeepsMainTablesInOrder() {
        assertEquals(listOf("passing", "rushing", "receiving", "defensive", "kicking"), BoxScore.fold(game).map { it.key })
    }

    @Test fun fumblesGoToTheFumblersOffensiveTable() {
        val receivingOut = BoxScore.fold(game).first { it.key == "receiving" }
        assertEquals(listOf("REC", "YDS", "AVG", "TD", "LONG", "TGTS", "FUM", "LST"), receivingOut.columns)
        assertEquals(listOf("6", "90", "15.0", "1", "40", "9", "1", "1"), receivingOut.players.single().values)
        // No one in rushing fumbled, so it keeps its columns.
        assertEquals(listOf("CAR", "YDS", "AVG", "TD", "LONG"), BoxScore.fold(game).first { it.key == "rushing" }.columns)
    }

    @Test fun interceptionsAndRecoveriesJoinDefense() {
        val defense = BoxScore.fold(game).first { it.key == "defensive" }
        assertEquals(listOf("TOT", "SOLO", "SACKS", "TFL", "PD", "QB HTS", "INT", "FR", "TD"), defense.columns)
        assertEquals(listOf("9", "6", "1", "2", "0", "1", "0", "1", "0"), defense.players.first { it.athleteId == "lb" }.values)
        // The cornerback had no tackles but gets a row for the pick.
        assertEquals(listOf("0", "0", "0", "0", "0", "0", "1", "0", "0"), defense.players.first { it.athleteId == "cb" }.values)
    }

    @Test fun seasonTotalsAddUpAndRecompute() {
        val week2 = listOf(
            t("passing", passing.columns, p("qb", "10/20", "150", "7.5", "0", "1", "1-5", "60.0")),
            t("rushing", rushing.columns, p("rb", "5", "40", "8.0", "0", "35")),
            t("kicking", kicking.columns, p("k", "1/1", "100.0", "30", "1/2", "4")),
        )
        val season = BoxScore.seasonTotals(listOf(game, week2))
        val qb = season.first { it.key == "passing" }
        assertEquals(listOf("GP", "C/ATT", "YDS", "AVG", "TD", "INT", "SACKS", "RTG"), qb.columns)
        val rating = String.format(java.util.Locale.US, "%.1f", BoxScore.passerRating(30.0, 50.0, 400.0, 2.0, 2.0))
        assertEquals(listOf("2", "30/50", "400", "8.0", "2", "2", "3-19", rating), qb.players.single().values)

        val rush = season.first { it.key == "rushing" }
        // Leaders first, longest run kept, average from the totals.
        assertEquals(listOf("rb", "qb"), rush.players.map { it.athleteId })
        assertEquals(listOf("2", "20", "120", "6.0", "1", "35"), rush.players.first().values)

        val k = season.first { it.key == "kicking" }.players.single()
        assertEquals(listOf("2", "3/4", "75.0", "48", "4/5", "13"), k.values)
    }

    @Test fun passerRatingMatchesKnownValue() {
        // A perfect line caps at 158.3.
        assertEquals(158.3, BoxScore.passerRating(20.0, 20.0, 400.0, 5.0, 0.0), 0.05)
    }

    private val defenseColumns = listOf("TOT", "SOLO", "SACKS", "TFL", "PD", "QB HTS", "TD")

    @Test fun gameDropsOffensivePlayersStrayTackles() {
        val defense = t(
            "defensive", defenseColumns,
            p("lb", "9", "6", "1", "2", "0", "1", "0"),
            // Tackled the man who picked him off.
            p("qb", "1", "1", "0", "0", "0", "0", "0"),
            p("wr", "2", "1", "0", "0", "0", "0", "0"),
            // Tackle on a kick return.
            p("k", "1", "1", "0", "0", "0", "0", "0"),
        )
        val shown = BoxScore.relevantRows(listOf(passing, rushing, receiving, defense, kicking), perGame = true)
        assertEquals(listOf("lb"), shown.first { it.key == "defensive" }.players.map { it.athleteId })
        // Offense tables are untouched.
        assertEquals(listOf("rb", "qb"), shown.first { it.key == "rushing" }.players.map { it.athleteId })
    }

    @Test fun gameKeepsTwoWayPlayers() {
        val defense = t(
            "defensive", defenseColumns,
            // Six catches and a pass breakup at corner.
            p("wr", "1", "1", "0", "0", "1", "0", "0"),
        )
        val fakePunt = t("rushing", rushing.columns, p("lb", "1", "12", "12.0", "0", "12"))
        val lbDefense = t("defensive", defenseColumns, p("lb", "9", "6", "1", "2", "0", "1", "0"))
        assertEquals(1, BoxScore.relevantRows(listOf(receiving, defense), perGame = true).first { it.key == "defensive" }.players.size)
        // A defender's carry on a fake punt is a real play in that game.
        assertEquals(listOf("lb"), BoxScore.relevantRows(listOf(fakePunt, lbDefense), perGame = true).first { it.key == "rushing" }.players.map { it.athleteId })
    }

    @Test fun seasonNeedsARealRoleOnTheOtherSide() {
        val catches = t("receiving", receiving.columns, p("wr", "60", "800", "13.3", "6", "50", "90"), p("hunter", "28", "298", "10.6", "1", "44", "45"))
        val defense = t(
            "defensive", defenseColumns,
            p("wr", "5", "4", "0", "0", "0", "0", "0"),
            p("hunter", "15", "11", "0", "0", "3", "0", "0"),
            p("lb", "90", "60", "4", "9", "2", "8", "0"),
        )
        val carries = t("rushing", rushing.columns, p("lb", "1", "12", "12.0", "0", "12"), p("rb", "200", "900", "4.5", "8", "40"))
        val shown = BoxScore.relevantRows(listOf(catches, carries, defense), perGame = false)
        assertEquals(listOf("hunter", "lb"), shown.first { it.key == "defensive" }.players.map { it.athleteId })
        assertEquals(listOf("rb"), shown.first { it.key == "rushing" }.players.map { it.athleteId })
    }

    @Test fun basketballTablesAreLeftAlone() {
        val players = t("players", listOf("MIN", "PTS"), p("a", "30", "20"))
        assertEquals(listOf(players), BoxScore.relevantRows(listOf(players), perGame = true))
    }
}
