package com.sportshub.app.data

import com.sportshub.app.model.CareerRow
import com.sportshub.app.model.CareerTable

/**
 * Picks the stat categories that fit a player, so a receiver's page doesn't show a
 * defense table for the tackles they made after interceptions.
 *
 * A player gets the categories of their position. A category from the other side of the
 * ball only shows when they really played there for a season: enough tackles, picks or
 * pass breakups on defense, or enough throws, carries or catches on offense. That keeps
 * two-way players like Travis Hunter complete without listing every stray tackle.
 *
 * When the position is unknown (or is "ATH"), the side is worked out from the stats.
 */
object PositionStats {

    enum class Side { Offense, Defense, Specialist }

    private val offense = setOf(
        "QB", "RB", "HB", "FB", "WR", "TE",
        "OL", "OT", "OG", "T", "G", "C", "LT", "RT", "LG", "RG", "IOL",
    )
    private val defense = setOf(
        "DL", "DE", "DT", "NT", "EDGE",
        "LB", "ILB", "OLB", "MLB",
        "DB", "CB", "S", "FS", "SS", "SAF",
    )
    private val specialists = setOf("K", "PK", "P", "LS")

    /** Football side for a position abbreviation, or null when it doesn't say (e.g. "ATH"). */
    fun sideOf(position: String?): Side? = when (position?.trim()?.uppercase()) {
        in offense -> Side.Offense
        in defense -> Side.Defense
        in specialists -> Side.Specialist
        else -> null
    }

    /** Categories each position always shows. Positions not listed show their whole side. */
    private val primary = mapOf(
        "QB" to setOf("passing", "rushing"),
        "RB" to setOf("rushing", "receiving"),
        "HB" to setOf("rushing", "receiving"),
        "FB" to setOf("rushing", "receiving"),
        "WR" to setOf("receiving", "rushing"),
        "TE" to setOf("receiving"),
    )

    private val categorySide = mapOf(
        "passing" to Side.Offense,
        "rushing" to Side.Offense,
        "receiving" to Side.Offense,
        "defensive" to Side.Defense,
        "defensiveInterceptions" to Side.Defense,
        "interceptions" to Side.Defense,
        "tackles" to Side.Defense,
        "kicking" to Side.Specialist,
        "punting" to Side.Specialist,
    )

    // ---------------------------------------------------------------------
    // What counts as really playing a role, in one season
    // ---------------------------------------------------------------------

    /** Throws, carries or catches enough in a season to count as playing offense. */
    const val SEASON_PASSES = 10.0
    const val SEASON_CARRIES = 10.0
    const val SEASON_CATCHES = 5.0

    /**
     * Enough defense in a season to count as playing it. Travis Hunter's half rookie
     * year (15 tackles, 3 pass breakups in 7 games) clears it; a receiver or quarterback
     * tackling someone after a turnover a few times a year doesn't.
     */
    const val SEASON_TACKLES = 12.0
    const val SEASON_INTERCEPTIONS = 2.0
    const val SEASON_PASSES_DEFENDED = 3.0
    const val SEASON_SACKS = 2.0

    const val SEASON_KICKS = 3.0

    /** Whether one season line in [key] shows a real role there. Unknown categories: any count of 10. */
    private fun seasonRole(key: String, columns: List<String>, row: CareerRow): Boolean {
        fun v(vararg names: String) = StatValues.amountOf(columns, row.values, *names)
        return when (key) {
            "passing" -> v("ATT", "CMP") >= SEASON_PASSES
            "rushing" -> v("CAR", "ATT") >= SEASON_CARRIES
            "receiving" -> v("REC") >= SEASON_CATCHES
            "defensive", "tackles" ->
                v("TOT", "SOLO") >= SEASON_TACKLES ||
                    v("INT") >= SEASON_INTERCEPTIONS ||
                    v("PD") >= SEASON_PASSES_DEFENDED ||
                    v("SACK", "SACKS") >= SEASON_SACKS
            "defensiveInterceptions", "interceptions" -> v("INT") >= SEASON_INTERCEPTIONS || v("PD") >= SEASON_PASSES_DEFENDED
            "kicking" -> maxOf(v("FGA", "FG"), v("XPA", "XP")) >= SEASON_KICKS
            "punting" -> v("PUNTS", "P", "NO") >= SEASON_KICKS
            else -> columns.indices.any { i -> StatValues.isCount(columns[i]) && StatValues.amount(row.values.getOrNull(i)) >= 10.0 }
        }
    }

    // ---------------------------------------------------------------------
    // Career tables
    // ---------------------------------------------------------------------

    /**
     * The career tables worth showing for a player at [position]: empty seasons and empty
     * tables dropped (a receiver's 0-for-0 kicking line), only the categories that fit the
     * player, and in each table only the columns with something in them.
     */
    fun relevant(tables: List<CareerTable>, position: String?): List<CareerTable> =
        categoriesFor(tables.mapNotNull(::withoutEmptySeasons), position).map(::withoutEmptyColumns)

    private fun categoriesFor(cleaned: List<CareerTable>, position: String?): List<CareerTable> {
        val pos = position?.trim()?.uppercase()
        val side = sideOf(pos) ?: inferSide(cleaned) ?: return cleaned
        val core = if (sideOf(pos) != null) primary[pos] else null
        return cleaned.filter { table ->
            val tableSide = categorySide[table.key]
            when {
                // Scoring, fumbles, basketball and anything new apply to everyone.
                tableSide == null -> true
                core != null -> table.key in core || playedRole(table)
                tableSide == side -> true
                else -> playedRole(table)
            }
        }
    }

    /** True if any season in [table] shows a real role in that category. */
    private fun playedRole(table: CareerTable): Boolean =
        table.seasons.any { seasonRole(table.key, table.columns, it) }

    /**
     * The table without seasons that have nothing in them (ESPN sends a passing line of
     * zeros for every receiver), or null when no season is left.
     */
    private fun withoutEmptySeasons(table: CareerTable): CareerTable? {
        val counted = table.columns.indices.filter { StatValues.isCount(table.columns[it]) }
        if (counted.isEmpty()) return table
        val seasons = table.seasons.filter { row -> counted.any { StatValues.amount(row.values.getOrNull(it)) != 0.0 } }
        return when {
            seasons.isEmpty() -> null
            seasons.size == table.seasons.size -> table
            else -> table.copy(seasons = seasons)
        }
    }

    /** Columns kept even when they're all zero: they're the point of the table. */
    private val headline = mapOf(
        "passing" to setOf("CMP", "ATT", "YDS", "TD", "INT"),
        "rushing" to setOf("CAR", "YDS", "TD"),
        "receiving" to setOf("REC", "YDS", "TD"),
        "defensive" to setOf("TOT", "SACK", "INT", "PD"),
        "defensiveInterceptions" to setOf("INT"),
        "interceptions" to setOf("INT"),
        "kicking" to setOf("FG", "FGM", "FGA", "XPM", "XPA"),
        "punting" to setOf("P", "PUNTS", "YDS"),
        "scoring" to setOf("TD", "PTS"),
        "averages" to setOf("PTS", "REB", "AST"),
        "totals" to setOf("PTS", "REB", "AST"),
    )
    private val alwaysKept = setOf("GP", "GS", "MIN")

    /**
     * The table without columns that are zero in every season and the total, like stuffs
     * and kicks blocked on a cornerback's defense table, or ejections on a basketball one.
     */
    internal fun withoutEmptyColumns(table: CareerTable): CareerTable {
        val keep = headline[table.key].orEmpty() + alwaysKept
        val rows = table.seasons + listOfNotNull(table.totals)
        val kept = table.columns.indices.filter { i ->
            table.columns[i] in keep || rows.any { !StatValues.isZero(it.values.getOrNull(i)) }
        }
        if (kept.size == table.columns.size || kept.isEmpty()) return table
        fun CareerRow.pick() = copy(values = kept.map { values.getOrElse(it) { "-" } })
        return table.copy(
            columns = kept.map { table.columns[it] },
            seasons = table.seasons.map { it.pick() },
            totals = table.totals?.pick(),
        )
    }

    /** The side a player of unknown position mostly plays, from their career numbers. */
    private fun inferSide(tables: List<CareerTable>): Side? {
        fun total(key: String, vararg columns: String) = tables.filter { it.key == key }.sumOf { t ->
            t.seasons.sumOf { StatValues.amountOf(t.columns, it.values, *columns) }
        }
        val offense = total("passing", "ATT", "CMP") + total("rushing", "CAR", "ATT") + total("receiving", "REC")
        val defense = total("defensive", "TOT", "SOLO") + total("tackles", "TOT", "SOLO") +
            total("defensive", "INT") + total("defensiveInterceptions", "INT") + total("defensive", "PD")
        val kicks = total("kicking", "FGA", "FG") + total("kicking", "XPA", "XP") + total("punting", "PUNTS", "P", "NO")
        val best = maxOf(offense, defense, kicks)
        return when {
            best <= 0.0 -> null
            best == offense -> Side.Offense
            best == defense -> Side.Defense
            else -> Side.Specialist
        }
    }
}

/** Reading ESPN's stat strings as numbers. */
internal object StatValues {

    /** Columns that aren't a count of plays: games played, rates, averages, longest. */
    fun isCount(column: String): Boolean {
        val c = column.uppercase()
        if (c in setOf("GP", "GS", "MIN", "LNG", "LONG", "AVG", "RTG", "QBR", "PCT", "SC-EFF", "SH-EFF")) return false
        return '%' !in c && '/' !in c
    }

    private val fraction = Regex("""^(\d+(?:\.\d+)?)[/-](\d+(?:\.\d+)?)$""")

    /**
     * A cell as a number: "1,258" is 1258, and "20/30" or "2-3" (made and tried) is the
     * number tried, 30 or 3. Blanks and dashes are 0.
     */
    fun amount(raw: String?): Double {
        val s = raw?.trim()?.replace(",", "") ?: return 0.0
        fraction.matchEntire(s)?.let { return it.groupValues[2].toDouble() }
        return s.toDoubleOrNull() ?: 0.0
    }

    /** Nothing in the cell: blank, a dash, 0, 0.0 or 0-0. */
    fun isZero(raw: String?): Boolean {
        val s = raw?.trim()?.replace(",", "") ?: return true
        if (s.isEmpty() || s == "-" || s == "--") return true
        fraction.matchEntire(s)?.let { return it.groupValues[1].toDouble() == 0.0 && it.groupValues[2].toDouble() == 0.0 }
        return s.removeSuffix("%").toDoubleOrNull() == 0.0
    }

    /** The value of the first of [names] that [columns] has, or 0. */
    fun amountOf(columns: List<String>, values: List<String>, vararg names: String): Double {
        val i = names.firstNotNullOfOrNull { n -> columns.indexOf(n).takeIf { it >= 0 } } ?: return 0.0
        return amount(values.getOrNull(i))
    }
}
