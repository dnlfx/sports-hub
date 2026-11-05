package com.sportshub.app.data

import com.sportshub.app.model.CareerTable

/**
 * Picks the stat categories that fit a player's position, so a receiver's page doesn't
 * show a tackles table for the one tackle they made after an interception.
 *
 * A category outside the player's position still shows when they have real numbers in
 * it, which keeps two-way players (and a running back who throws now and then) complete.
 */
object PositionStats {

    private enum class Group { Offense, Defense, Specialist }

    private val offense = setOf(
        "QB", "RB", "HB", "FB", "WR", "TE",
        "OL", "OT", "OG", "T", "G", "C", "LT", "RT", "LG", "RG",
    )
    private val defense = setOf(
        "DL", "DE", "DT", "NT", "EDGE",
        "LB", "ILB", "OLB", "MLB",
        "DB", "CB", "S", "FS", "SS", "SAF",
    )
    private val specialists = setOf("K", "PK", "P", "LS")

    /** Categories each position always shows. Missing positions use their group's set. */
    private val primary = mapOf(
        "QB" to setOf("passing", "rushing"),
        "RB" to setOf("rushing", "receiving"),
        "HB" to setOf("rushing", "receiving"),
        "FB" to setOf("rushing", "receiving"),
        "WR" to setOf("receiving", "rushing"),
        "TE" to setOf("receiving"),
    )

    private val categoryGroup = mapOf(
        "passing" to Group.Offense,
        "rushing" to Group.Offense,
        "receiving" to Group.Offense,
        "defensive" to Group.Defense,
        "defensiveInterceptions" to Group.Defense,
        "interceptions" to Group.Defense,
        "tackles" to Group.Defense,
        "kicking" to Group.Specialist,
        "punting" to Group.Specialist,
    )

    /**
     * How much a player needs in a category outside their position for it to show: the
     * column to count (first one found) and the career amount.
     */
    private val twoWayVolume = mapOf(
        "passing" to (listOf("ATT", "CMP") to 10.0),
        "rushing" to (listOf("CAR", "ATT") to 10.0),
        "receiving" to (listOf("REC") to 5.0),
        "defensive" to (listOf("TOT", "SOLO") to 10.0),
        "defensiveInterceptions" to (listOf("INT") to 2.0),
        "interceptions" to (listOf("INT") to 2.0),
        "tackles" to (listOf("TOT", "SOLO") to 10.0),
        "kicking" to (listOf("FGA", "XPA", "FGM", "XPM") to 3.0),
        "punting" to (listOf("PUNTS", "P", "NO") to 3.0),
    )

    /** Fallback for a category with no known volume column: its biggest career count. */
    private const val FALLBACK_VOLUME = 10.0

    /** Columns that aren't a count of plays: games played, rates, averages, longest. */
    private fun isCount(column: String): Boolean {
        val c = column.uppercase()
        if (c in setOf("GP", "GS", "LNG", "LONG", "AVG", "RTG", "QBR", "PCT")) return false
        return '%' !in c && '/' !in c
    }

    fun relevant(tables: List<CareerTable>, position: String?): List<CareerTable> {
        val pos = position?.trim()?.uppercase()?.takeIf { it.isNotEmpty() } ?: return tables
        val group = when (pos) {
            in offense -> Group.Offense
            in defense -> Group.Defense
            in specialists -> Group.Specialist
            else -> return tables
        }
        val core = primary[pos]
        return tables.filter { table ->
            val tableGroup = categoryGroup[table.key]
            when {
                // Fumbles, scoring and anything new apply to everyone.
                tableGroup == null -> true
                core != null -> table.key in core || hasVolume(table)
                tableGroup == group -> true
                else -> hasVolume(table)
            }
        }
    }

    private fun hasVolume(table: CareerTable): Boolean {
        val (columns, needed) = twoWayVolume[table.key] ?: (emptyList<String>() to FALLBACK_VOLUME)
        val column = columns.firstNotNullOfOrNull { c -> table.columns.indexOf(c).takeIf { it >= 0 } }
        val indices = if (column != null) {
            listOf(column)
        } else {
            table.columns.indices.filter { isCount(table.columns[it]) }
        }
        return indices.any { i -> total(table, i) >= needed }
    }

    /** Career amount in column [i]: the totals row, or the seasons added up. */
    private fun total(table: CareerTable, i: Int): Double {
        table.totals?.values?.getOrNull(i)?.let(::number)?.let { return it }
        return table.seasons.sumOf { number(it.values.getOrNull(i)) ?: 0.0 }
    }

    private fun number(raw: String?): Double? = raw?.replace(",", "")?.trim()?.toDoubleOrNull()
}
