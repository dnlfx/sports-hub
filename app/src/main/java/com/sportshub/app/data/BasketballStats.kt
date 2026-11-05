package com.sportshub.app.data

import com.sportshub.app.data.espn.EspnParser
import com.sportshub.app.model.PlayerStatLine
import com.sportshub.app.model.PlayerStatTable
import java.util.Locale

/**
 * Shapes basketball box scores for display, the way [BoxScore] does for football.
 *
 * ESPN gives each team one table with every player. [forDisplay] splits it into
 * starters and bench, with points, rebounds and assists up front. [seasonTotals]
 * adds a team's box scores from several games into per-game averages and totals.
 */
object BasketballStats {

    /** Columns in the order a fan reads them. Anything else ESPN sends goes after these. */
    private val displayOrder = listOf("MIN", "PTS", "REB", "AST", "FG", "3PT", "FT", "STL", "BLK", "TO", "OREB", "DREB", "PF", "+/-")

    fun forDisplay(tables: List<PlayerStatTable>): List<PlayerStatTable> {
        val table = tables.firstOrNull { it.key == EspnParser.BASKETBALL_TABLE } ?: return tables
        val ordered = reorder(table)
        val starters = ordered.players.filter { it.starter }
        val bench = ordered.players.filterNot { it.starter }
        // Without lineup flags there's nothing to split on: one table for everyone.
        if (starters.isEmpty() || bench.isEmpty()) return listOf(ordered.copy(key = "box", title = "Box score"))
        return listOf(
            ordered.copy(key = "starters", title = "Starters", players = starters),
            ordered.copy(key = "bench", title = "Bench", players = bench),
        )
    }

    private fun reorder(table: PlayerStatTable): PlayerStatTable {
        val known = displayOrder.filter { it in table.columns }
        val columns = known + table.columns.filter { it !in known }
        val indices = columns.map { table.columns.indexOf(it) }
        return table.copy(
            columns = columns,
            players = table.players.map { p -> p.copy(values = indices.map { p.values.getOrElse(it) { "-" } }) },
        )
    }

    // ---------------------------------------------------------------------
    // Season totals
    // ---------------------------------------------------------------------

    private class Totals {
        var games = 0
        var minutes = 0.0
        var points = 0.0
        var rebounds = 0.0
        var assists = 0.0
        var steals = 0.0
        var blocks = 0.0
        var turnovers = 0.0
        var plusMinus = 0.0
        var fgm = 0.0
        var fga = 0.0
        var tpm = 0.0
        var tpa = 0.0
        var ftm = 0.0
        var fta = 0.0
    }

    /**
     * Season tables for one team from its per-game box scores (the raw tables, as parsed):
     * "Per game" averages and season "Totals", leaders first.
     */
    fun seasonTotals(games: List<List<PlayerStatTable>>): List<PlayerStatTable> {
        val totals = LinkedHashMap<String, Totals>()
        val latest = mutableMapOf<String, PlayerStatLine>()
        games.forEach { tables ->
            val table = tables.firstOrNull { it.key == EspnParser.BASKETBALL_TABLE } ?: return@forEach
            fun col(name: String) = table.columns.indexOf(name)
            val min = col("MIN")
            val pts = col("PTS")
            val reb = col("REB")
            val ast = col("AST")
            val stl = col("STL")
            val blk = col("BLK")
            val to = col("TO")
            val pm = col("+/-")
            val fg = col("FG")
            val tp = col("3PT")
            val ft = col("FT")
            table.players.forEach { p ->
                fun num(i: Int) = if (i < 0) 0.0 else BoxScore.number(p.values.getOrNull(i)?.removePrefix("+")) ?: 0.0
                fun made(i: Int) = fraction(p.values.getOrNull(i))
                val t = totals.getOrPut(p.athleteId) { Totals() }
                t.games++
                t.minutes += minutes(p.values.getOrNull(min))
                t.points += num(pts)
                t.rebounds += num(reb)
                t.assists += num(ast)
                t.steals += num(stl)
                t.blocks += num(blk)
                t.turnovers += num(to)
                t.plusMinus += num(pm)
                made(fg)?.let { (m, a) -> t.fgm += m; t.fga += a }
                made(tp)?.let { (m, a) -> t.tpm += m; t.tpa += a }
                made(ft)?.let { (m, a) -> t.ftm += m; t.fta += a }
                latest[p.athleteId] = p
            }
        }
        if (totals.isEmpty()) return emptyList()

        val ranked = totals.entries.sortedByDescending { (_, t) -> t.points / t.games }
        fun line(id: String, values: List<String>): PlayerStatLine {
            val who = latest.getValue(id)
            return PlayerStatLine(id, who.name, who.headshotUrl, who.jersey, values)
        }
        val perGame = PlayerStatTable(
            key = "perGame",
            title = "Per game",
            columns = listOf("GP", "MIN", "PTS", "REB", "AST", "STL", "BLK", "TO", "FG%", "3P%", "FT%"),
            players = ranked.map { (id, t) ->
                val g = t.games.toDouble()
                line(
                    id,
                    listOf(
                        "${t.games}", avg(t.minutes / g), avg(t.points / g), avg(t.rebounds / g), avg(t.assists / g),
                        avg(t.steals / g), avg(t.blocks / g), avg(t.turnovers / g),
                        pct(t.fgm, t.fga), pct(t.tpm, t.tpa), pct(t.ftm, t.fta),
                    ),
                )
            },
        )
        val season = PlayerStatTable(
            key = "totals",
            title = "Totals",
            columns = listOf("GP", "MIN", "PTS", "REB", "AST", "STL", "BLK", "TO", "FG", "3PT", "FT", "+/-"),
            players = totals.entries.sortedByDescending { it.value.points }.map { (id, t) ->
                line(
                    id,
                    listOf(
                        "${t.games}", whole(t.minutes), whole(t.points), whole(t.rebounds), whole(t.assists),
                        whole(t.steals), whole(t.blocks), whole(t.turnovers),
                        "${whole(t.fgm)}-${whole(t.fga)}", "${whole(t.tpm)}-${whole(t.tpa)}", "${whole(t.ftm)}-${whole(t.fta)}",
                        whole(t.plusMinus).let { if (t.plusMinus > 0) "+$it" else it },
                    ),
                )
            },
        )
        return listOf(perGame, season)
    }

    /** "34" or "34:12" minutes as a number. */
    private fun minutes(raw: String?): Double {
        val s = raw?.trim() ?: return 0.0
        val parts = s.split(':')
        if (parts.size == 2) {
            val m = parts[0].toDoubleOrNull() ?: return 0.0
            val sec = parts[1].toDoubleOrNull() ?: 0.0
            return m + sec / 60
        }
        return s.toDoubleOrNull() ?: 0.0
    }

    private val fractionPattern = Regex("""^(\d+)-(\d+)$""")

    private fun fraction(raw: String?): Pair<Double, Double>? {
        val m = fractionPattern.matchEntire(raw?.trim() ?: return null) ?: return null
        return m.groupValues[1].toDouble() to m.groupValues[2].toDouble()
    }

    private fun avg(v: Double) = String.format(Locale.US, "%.1f", v)

    private fun whole(v: Double) = Math.round(v).toString()

    private fun pct(made: Double, attempts: Double) =
        if (attempts <= 0) "-" else String.format(Locale.US, "%.1f", made / attempts * 100)
}
