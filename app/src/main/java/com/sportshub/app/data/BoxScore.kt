package com.sportshub.app.data

import com.sportshub.app.model.PlayerStatLine
import com.sportshub.app.model.PlayerStatTable
import java.util.Locale

/**
 * Shapes ESPN's box score tables for display.
 *
 * [fold] moves interceptions and fumbles into the tables above them and drops the
 * punting and return tables. [seasonTotals] adds a team's box scores from several
 * games into one set of season tables. [relevantRows] leaves each player out of the
 * tables for the side of the ball they don't really play.
 */
object BoxScore {

    /** Tables shown, in order. Anything ESPN adds later goes after these. */
    private val order = listOf("passing", "rushing", "receiving", "defensive", "kicking")

    /** Tables never shown on their own: folded into others, or left out. */
    private val hidden = setOf("interceptions", "fumbles", "punting", "kickReturns", "puntReturns")

    /** Offensive tables a fumble is shown in, first match wins. */
    private val fumbleHomes = listOf("rushing", "receiving", "passing")

    fun fold(tables: List<PlayerStatTable>): List<PlayerStatTable> {
        val byKey = tables.associateBy { it.key }.toMutableMap()
        val interceptions = byKey["interceptions"]
        val fumbles = byKey["fumbles"]

        // Fumbles: each player who fumbled gets FUM and LST in the first offensive table they're in.
        val recoveries = mutableMapOf<String, Pair<PlayerStatLine, String>>()
        if (fumbles != null) {
            val fum = fumbles.columns.indexOf("FUM")
            val lost = fumbles.columns.indexOf("LOST")
            val rec = fumbles.columns.indexOf("REC")
            val additions = mutableMapOf<String, MutableMap<String, List<String>>>()
            val newRows = mutableMapOf<String, MutableList<PlayerStatLine>>()
            fumbles.players.forEach { p ->
                val fumbled = number(p.values.getOrNull(fum)) ?: 0.0
                val lostCount = number(p.values.getOrNull(lost)) ?: 0.0
                val recovered = number(p.values.getOrNull(rec)) ?: 0.0
                if (fumbled > 0 || lostCount > 0) {
                    val cells = listOf(p.values.getOrNull(fum) ?: "0", p.values.getOrNull(lost) ?: "0")
                    val home = fumbleHomes.firstOrNull { key -> byKey[key]?.players?.any { it.athleteId == p.athleteId } == true }
                    when {
                        home != null -> additions.getOrPut(home) { mutableMapOf() }[p.athleteId] = cells
                        // Fumbled without touching the ball on offense (a returner, say): list under rushing.
                        byKey["rushing"] != null -> {
                            additions.getOrPut("rushing") { mutableMapOf() }[p.athleteId] = cells
                            newRows.getOrPut("rushing") { mutableListOf() } += p
                        }
                    }
                } else if (recovered > 0) {
                    recoveries[p.athleteId] = p to (p.values.getOrNull(rec) ?: "0")
                }
            }
            additions.forEach { (key, cellsById) ->
                val table = byKey.getValue(key)
                val extraRows = newRows[key].orEmpty().map { blankRow(it, fumbles.columns, table.columns) }
                byKey[key] = table.withColumns(listOf("FUM", "LST"), table.players + extraRows) { id ->
                    cellsById[id] ?: listOf("0", "0")
                }
            }
        }

        // Interceptions and fumble recoveries are defensive stats: INT and FR columns in Defense.
        val intCol = interceptions?.columns?.indexOf("INT") ?: -1
        val picks = interceptions?.players.orEmpty()
            .filter { (number(it.values.getOrNull(intCol)) ?: 0.0) > 0 }
            .associate { it.athleteId to (it to it.values[intCol]) }
        val defense = byKey["defensive"]
        // Only recoveries by players who are on the defense table (or made an interception) count here.
        val defensiveIds = defense?.players.orEmpty().map { it.athleteId }.toSet() + picks.keys
        val frs = recoveries.filterKeys { it in defensiveIds }
        if (picks.isNotEmpty() || frs.isNotEmpty()) {
            val base = defense ?: PlayerStatTable("defensive", "Defense", emptyList(), emptyList())
            val known = base.players.map { it.athleteId }.toSet()
            val newPlayers = (picks.values.map { it.first to interceptions!!.columns } + frs.values.map { it.first to fumbles!!.columns })
                .filter { it.first.athleteId !in known }
                .distinctBy { it.first.athleteId }
                .map { (p, from) -> blankRow(p, from, base.columns) }
            val newColumns = buildList {
                if (picks.isNotEmpty()) add("INT")
                if (frs.isNotEmpty()) add("FR")
            }
            byKey["defensive"] = base.withColumns(newColumns, base.players + newPlayers, beforeColumn = "TD") { id ->
                buildList {
                    if (picks.isNotEmpty()) add(picks[id]?.second ?: "0")
                    if (frs.isNotEmpty()) add(frs[id]?.second ?: "0")
                }
            }
        }

        return byKey.values
            .filter { it.key !in hidden && it.players.isNotEmpty() }
            .sortedBy { t -> order.indexOf(t.key).let { if (it < 0) Int.MAX_VALUE else it } }
    }

    /** A row for a player new to a table: zeros, except games played, which carries over. */
    private fun blankRow(p: PlayerStatLine, fromColumns: List<String>, toColumns: List<String>): PlayerStatLine =
        p.copy(values = toColumns.map { c -> if (c == "GP") p.values.getOrNull(fromColumns.indexOf(c)) ?: "1" else "0" })

    /** Adds [columns] to every row, before [beforeColumn] when the table has it, else at the end. */
    private fun PlayerStatTable.withColumns(
        columns: List<String>,
        rows: List<PlayerStatLine>,
        beforeColumn: String? = null,
        cells: (String) -> List<String>,
    ): PlayerStatTable {
        val at = beforeColumn?.let { this.columns.indexOf(it) }?.takeIf { it >= 0 } ?: this.columns.size
        fun <T> List<T>.insert(items: List<T>) = take(at) + items + drop(at)
        return copy(
            columns = this.columns.insert(columns),
            players = rows.map { p -> p.copy(values = p.values.insert(cells(p.athleteId))) },
        )
    }

    // ---------------------------------------------------------------------
    // Season totals
    // ---------------------------------------------------------------------

    /**
     * Season tables for one team from its per-game box scores (raw tables, before [fold]).
     * Counts are added up, longest plays kept, averages and rates worked out again from
     * the totals, and a GP (games played) column added. The result is folded and sorted
     * with the leaders first.
     */
    fun seasonTotals(games: List<List<PlayerStatTable>>): List<PlayerStatTable> {
        val tableKeys = LinkedHashMap<String, PlayerStatTable>()
        games.flatten().forEach { tableKeys.putIfAbsent(it.key, it) }
        val totals = tableKeys.values.map { first ->
            val columns = first.columns.filter { it != "QBR" }
            val rows = LinkedHashMap<String, MutableList<PlayerStatLine>>()
            val latest = mutableMapOf<String, PlayerStatLine>()
            games.forEach { tables ->
                val table = tables.firstOrNull { it.key == first.key } ?: return@forEach
                table.players.forEach { p ->
                    // Line the values up by column name, in case a game's table had different columns.
                    val aligned = PlayerStatLine(
                        p.athleteId, p.name, p.headshotUrl, p.jersey,
                        columns.map { c -> table.columns.indexOf(c).let { i -> if (i >= 0) p.values.getOrElse(i) { "-" } else "-" } },
                    )
                    rows.getOrPut(p.athleteId) { mutableListOf() } += aligned
                    latest[p.athleteId] = p
                }
            }
            val players = rows.map { (id, lines) ->
                val who = latest.getValue(id)
                PlayerStatLine(id, who.name, who.headshotUrl ?: lines.firstNotNullOfOrNull { it.headshotUrl }, who.jersey, totalRow(first.key, columns, lines))
            }
            first.copy(columns = listOf("GP") + columns, players = players)
        }
        return relevantRows(fold(totals), perGame = false).map { it.sortedByLeaders() }
    }

    // ---------------------------------------------------------------------
    // Who belongs in which table
    // ---------------------------------------------------------------------

    private val offenseTables = setOf("passing", "rushing", "receiving")
    private val defenseTables = setOf("defensive", "interceptions")

    /** What one player did across a team's box score tables. */
    private class Volume {
        var passes = 0.0
        var carries = 0.0
        var catches = 0.0
        var tackles = 0.0
        var interceptions = 0.0
        var passesDefended = 0.0
        var sacks = 0.0
        var disruptions = 0.0
        var kicks = 0.0
        val offense get() = passes + carries + catches
        val defense get() = tackles + interceptions + passesDefended + sacks
    }

    /**
     * Drops each player's rows in tables for the side of the ball they don't play: a
     * receiver's tackle after an interception, a kicker's tackle on a return, a
     * linebacker's carry on a fake punt over a season. A player who really plays both
     * ways (Travis Hunter) keeps both.
     *
     * [perGame] is for one game's box score, where one pass breakup or three tackles
     * count as playing defense. Over a season ([perGame] false) the bar is the same as
     * on the player's page, see [PositionStats].
     */
    fun relevantRows(tables: List<PlayerStatTable>, perGame: Boolean): List<PlayerStatTable> {
        val volumes = mutableMapOf<String, Volume>()
        tables.forEach { t ->
            t.players.forEach { p ->
                val v = volumes.getOrPut(p.athleteId) { Volume() }
                fun a(vararg names: String) = StatValues.amountOf(t.columns, p.values, *names)
                when (t.key) {
                    "passing" -> v.passes += a("C/ATT", "ATT")
                    "rushing" -> v.carries += a("CAR")
                    "receiving" -> v.catches += a("REC")
                    "defensive" -> {
                        v.tackles += a("TOT")
                        v.interceptions += a("INT")
                        v.passesDefended += a("PD")
                        v.sacks += a("SACKS", "SACK")
                        v.disruptions += a("TFL") + a("QB HTS")
                    }
                    "interceptions" -> v.interceptions += a("INT")
                    "kicking" -> v.kicks += a("FG") + a("XP")
                    "punting" -> v.kicks += a("NO")
                }
            }
        }

        fun playsDefense(v: Volume) = if (perGame) {
            v.tackles >= 3 || v.interceptions > 0 || v.passesDefended > 0 || v.sacks > 0 || v.disruptions > 0
        } else {
            v.tackles >= PositionStats.SEASON_TACKLES || v.interceptions >= PositionStats.SEASON_INTERCEPTIONS ||
                v.passesDefended >= PositionStats.SEASON_PASSES_DEFENDED || v.sacks >= PositionStats.SEASON_SACKS
        }
        // In one game any touch on offense is a real play (a fake punt run, say).
        fun playsOffense(v: Volume) = if (perGame) {
            v.offense > 0
        } else {
            v.passes >= PositionStats.SEASON_PASSES || v.carries >= PositionStats.SEASON_CARRIES ||
                v.catches >= PositionStats.SEASON_CATCHES
        }

        fun belongs(tableKey: String, id: String): Boolean {
            val v = volumes[id] ?: return true
            val side = when {
                v.kicks > 0 && v.kicks >= v.offense && v.kicks >= v.defense -> PositionStats.Side.Specialist
                v.defense > v.offense -> PositionStats.Side.Defense
                else -> PositionStats.Side.Offense
            }
            return when (tableKey) {
                in defenseTables -> side == PositionStats.Side.Defense || playsDefense(v)
                in offenseTables -> side == PositionStats.Side.Offense || playsOffense(v)
                else -> true
            }
        }

        return tables.mapNotNull { t ->
            if (t.key !in defenseTables && t.key !in offenseTables) return@mapNotNull t
            val players = t.players.filter { belongs(t.key, it.athleteId) }
            when {
                players.isEmpty() -> null
                players.size == t.players.size -> t
                else -> t.copy(players = players)
            }
        }
    }

    private fun totalRow(tableKey: String, columns: List<String>, lines: List<PlayerStatLine>): List<String> {
        fun col(name: String) = columns.indexOf(name)
        fun values(i: Int) = lines.map { it.values.getOrNull(i) }
        fun sum(i: Int) = if (i < 0) 0.0 else values(i).sumOf { number(it) ?: 0.0 }
        fun fractionSums(i: Int): Pair<Double, Double>? {
            if (i < 0) return null
            val parts = values(i).mapNotNull { fraction(it) }
            if (parts.isEmpty()) return null
            return parts.sumOf { it.first } to parts.sumOf { it.second }
        }

        val yards = sum(col("YDS"))
        val cells = columns.mapIndexed { i, name ->
            val raw = values(i)
            when {
                name == "LONG" -> format(raw.mapNotNull { number(it?.removeSuffix("t")) }.maxOrNull() ?: 0.0)
                name == "AVG" -> {
                    val base = when (tableKey) {
                        "passing" -> fractionSums(col("C/ATT"))?.second
                        "rushing" -> sum(col("CAR"))
                        "receiving" -> sum(col("REC"))
                        else -> sum(col("NO"))
                    } ?: 0.0
                    if (base > 0) String.format(Locale.US, "%.1f", yards / base) else "0.0"
                }
                name == "PCT" -> {
                    val fg = fractionSums(col("FG"))
                    if (fg != null && fg.second > 0) String.format(Locale.US, "%.1f", fg.first / fg.second * 100) else "0.0"
                }
                name == "RTG" -> {
                    val ca = fractionSums(col("C/ATT"))
                    if (ca == null || ca.second <= 0) "0.0" else String.format(
                        Locale.US, "%.1f",
                        passerRating(ca.first, ca.second, yards, sum(col("TD")), sum(col("INT"))),
                    )
                }
                raw.any { fraction(it) != null } -> {
                    val sep = raw.firstNotNullOfOrNull { v -> v?.firstOrNull { it == '/' || it == '-' } } ?: '/'
                    val (a, b) = fractionSums(i) ?: (0.0 to 0.0)
                    "${format(a)}$sep${format(b)}"
                }
                else -> format(sum(i))
            }
        }
        return listOf(lines.size.toString()) + cells
    }

    /** NFL passer rating from season totals. */
    fun passerRating(completions: Double, attempts: Double, yards: Double, touchdowns: Double, interceptions: Double): Double {
        if (attempts <= 0) return 0.0
        fun clamp(v: Double) = v.coerceIn(0.0, 2.375)
        val a = clamp((completions / attempts - 0.3) * 5)
        val b = clamp((yards / attempts - 3) * 0.25)
        val c = clamp(touchdowns / attempts * 20)
        val d = clamp(2.375 - interceptions / attempts * 25)
        return (a + b + c + d) / 6 * 100
    }

    /** Leaders first: by yards, then tackles, then points, else by games played. */
    private fun PlayerStatTable.sortedByLeaders(): PlayerStatTable {
        val key = listOf("YDS", "TOT", "PTS").map { columns.indexOf(it) }.firstOrNull { it >= 0 } ?: 0
        return copy(players = players.sortedByDescending { number(it.values.getOrNull(key)) ?: 0.0 })
    }

    private val fractionPattern = Regex("""^(\d+(?:\.\d+)?)[/-](\d+(?:\.\d+)?)$""")

    private fun fraction(raw: String?): Pair<Double, Double>? {
        val m = fractionPattern.matchEntire(raw?.trim() ?: return null) ?: return null
        return m.groupValues[1].toDouble() to m.groupValues[2].toDouble()
    }

    internal fun number(raw: String?): Double? = raw?.trim()?.replace(",", "")?.toDoubleOrNull()

    /** Whole numbers without a decimal point; halves (half sacks) with one. */
    private fun format(v: Double): String =
        if (v == Math.floor(v)) v.toLong().toString() else String.format(Locale.US, "%.1f", v)
}
