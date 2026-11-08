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

/** Key of the "Your teams" section, which leads the list when the user follows any team. */
const val YOUR_TEAMS_SECTION = "yours"

/**
 * The games of the user's teams first (under "Your teams", in the order they were followed;
 * [favoriteIds] should keep that order), then the rest: live games,
 * upcoming games grouped by day, then finals. A game shows up only once.
 */
fun sectionsOf(
    board: Scoreboard,
    zone: ZoneId = ZoneId.systemDefault(),
    today: LocalDate = LocalDate.now(zone),
    favoriteIds: Set<String> = emptySet(),
): List<GameSection> {
    val (followed, games) = board.games.partition { it.home.team.id in favoriteIds || it.away.team.id in favoriteIds }
    // In the order the user followed their teams, so their first team's game leads.
    val order = favoriteIds.withIndex().associate { (i, id) -> id to i }
    val mine = followed.sortedBy { game ->
        minOf(order[game.home.team.id] ?: Int.MAX_VALUE, order[game.away.team.id] ?: Int.MAX_VALUE)
    }
    val live = games.filter { it.status is GameStatus.Live }
    val upcomingByDay = games
        .filter { it.status == GameStatus.Scheduled }
        .groupBy { it.startTime.atZone(zone).toLocalDate() }
        .toSortedMap()
    val final = games.filter { it.status is GameStatus.Final }
    val postponed = games.filter { it.status is GameStatus.Postponed }
    return buildList {
        if (mine.isNotEmpty()) add(GameSection(YOUR_TEAMS_SECTION, "Your teams", mine))
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

/**
 * The game as the detail screen should show it: the latest data from the game summary,
 * with the TV networks, stadium and live situation from the scores list kept when the
 * summary leaves them out, so nothing on screen blinks away once details arrive.
 */
fun mergeGame(latest: Game?, fromList: Game?): Game? {
    if (latest == null) return fromList
    if (fromList == null || fromList.id != latest.id) return latest
    val situation = when {
        !latest.isLive -> null
        latest.situation == null -> fromList.situation
        else -> latest.situation.copy(
            downDistance = latest.situation.downDistance ?: fromList.situation?.downDistance,
            lastPlay = latest.situation.lastPlay ?: fromList.situation?.lastPlay,
        )
    }
    val merged = latest.copy(
        venue = latest.venue ?: fromList.venue,
        broadcasts = latest.broadcasts.ifEmpty { fromList.broadcasts },
        situation = situation,
    )
    // Hand back the same object when nothing changed, so the screen can skip redrawing.
    return if (merged == latest) latest else merged
}

/**
 * The days on the strip: every game day of the season when ESPN lists them, else two
 * weeks either side of today. The day showing and today's slate are always on it.
 */
fun stripDays(calendar: List<LocalDate>, current: LocalDate?, selected: LocalDate?): List<LocalDate> {
    val anchor = selected ?: current ?: LocalDate.now()
    val base = calendar.ifEmpty { (-14L..14L).map { (current ?: anchor).plusDays(it) } }
    return (base + listOfNotNull(current, selected)).distinct().sorted()
}

/** [new] if it differs from [old], else [old] itself, so unchanged data keeps its identity. */
fun <T> keepIfEqual(old: T?, new: T): T = if (old != null && old == new) old else new
