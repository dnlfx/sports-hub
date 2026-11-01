package com.sportshub.app.model

import java.time.Instant

data class Team(
    val id: String,
    val name: String,
    val abbreviation: String,
    val logoUrl: String? = null,
    val record: String? = null,
    /** Hex color like "#004C54", used to tint score cards. */
    val primaryColor: String? = null,
)

sealed interface GameStatus {
    data object Scheduled : GameStatus
    /** [period] is the quarter, inning, half, etc. [clock] is the game clock, if the sport has one. */
    data class Live(val period: Int, val clock: String?, val detail: String) : GameStatus
    data class Final(val detail: String = "Final") : GameStatus
    data class Postponed(val detail: String) : GameStatus
}

data class Game(
    val id: String,
    val sport: Sport,
    val startTime: Instant,
    val status: GameStatus,
    val home: Team,
    val away: Team,
    val homeScore: Int?,
    val awayScore: Int?,
    /** TV networks or services carrying the game, e.g. "CBS", "Prime Video". */
    val broadcasts: List<String> = emptyList(),
    val venue: String? = null,
)
