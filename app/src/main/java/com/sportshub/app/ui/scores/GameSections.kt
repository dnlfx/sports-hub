package com.sportshub.app.ui.scores

import androidx.compose.runtime.Immutable
import com.sportshub.app.model.Game
import com.sportshub.app.model.GameStatus
import com.sportshub.app.model.Scoreboard
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** A titled group of games on the Scores list, e.g. "Live now" or "Sunday, Nov 2". */
@Immutable
data class GameSection(val key: String, val title: String, val games: List<Game>)

private val dayFormat = DateTimeFormatter.ofPattern("EEEE, MMM d")

/** Live games first, then upcoming games grouped by day, then finals. */
fun sectionsOf(
    board: Scoreboard,
    zone: ZoneId = ZoneId.systemDefault(),
    today: LocalDate = LocalDate.now(zone),
): List<GameSection> {
    val live = board.games.filter { it.status is GameStatus.Live }
    val upcomingByDay = board.games
        .filter { it.status == GameStatus.Scheduled }
        .groupBy { it.startTime.atZone(zone).toLocalDate() }
        .toSortedMap()
    val final = board.games.filter { it.status is GameStatus.Final }
    val postponed = board.games.filter { it.status is GameStatus.Postponed }
    return buildList {
        if (live.isNotEmpty()) add(GameSection("live", "Live now", live))
        upcomingByDay.forEach { (day, games) ->
            val title = when (day) {
                today -> "Today"
                today.plusDays(1) -> "Tomorrow"
                else -> day.format(dayFormat)
            }
            add(GameSection("day_$day", title, games))
        }
        if (final.isNotEmpty()) add(GameSection("final", "Final", final))
        if (postponed.isNotEmpty()) add(GameSection("postponed", "Postponed", postponed))
    }
}

/**
 * The away side's share of a team stat, from 0 to 1, when both values are plain numbers,
 * percentages or clock times ("31:22"). Null for stats like "5-12" that don't compare that way.
 */
fun statShare(away: String, home: String): Float? {
    val a = statValue(away) ?: return null
    val h = statValue(home) ?: return null
    if (a + h <= 0.0) return null
    return (a / (a + h)).toFloat()
}

private val plainNumber = Regex("""^\d+(\.\d+)?%?$""")
private val clockValue = Regex("""^(\d+):(\d{2})$""")

private fun statValue(raw: String): Double? {
    val s = raw.trim().replace(",", "")
    if (plainNumber.matches(s)) return s.removeSuffix("%").toDoubleOrNull()
    clockValue.matchEntire(s)?.let { m -> return m.groupValues[1].toDouble() * 60 + m.groupValues[2].toDouble() }
    return null
}
