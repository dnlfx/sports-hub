package com.sportshub.app.ui.scores

import com.sportshub.app.model.PlayKind
import com.sportshub.app.model.WinProbPoint
import kotlin.math.abs

// The numbers behind the win probability chart, kept apart from the drawing so they can be tested.

/** How much play [i] moved the home team's chance, from -1 to 1. */
internal fun swingAt(points: List<WinProbPoint>, i: Int): Float =
    if (i <= 0 || i >= points.size) 0f else points[i].homeWinPct - points[i - 1].homeWinPct

/**
 * The plays worth marking on the chart: the biggest swings, plus scores and turnovers
 * that moved the needle at all. At most [max], in game order.
 */
internal fun keyPlays(points: List<WinProbPoint>, max: Int = 8): List<Int> =
    (1 until points.size)
        .filter { i ->
            val swing = abs(swingAt(points, i))
            swing >= 0.08f || (points[i].kind != PlayKind.Other && swing >= 0.03f)
        }
        .sortedByDescending { abs(swingAt(points, it)) }
        .take(max)
        .sorted()

/** The first point of each period, as (period, index), for the quarter lines. */
internal fun periodStarts(points: List<WinProbPoint>): List<Pair<Int, Int>> {
    val out = mutableListOf<Pair<Int, Int>>()
    var last: Int? = null
    points.forEachIndexed { i, p ->
        val period = p.period ?: return@forEachIndexed
        if (last == null || period > last!!) {
            out += period to i
            last = period
        }
    }
    return out
}

internal fun periodLabel(period: Int, short: String = "Q"): String = when {
    period <= 4 -> "$short$period"
    period == 5 -> "OT"
    else -> "${period - 4}OT"
}
