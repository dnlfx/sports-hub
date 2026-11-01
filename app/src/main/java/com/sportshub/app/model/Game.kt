package com.sportshub.app.model

import androidx.compose.runtime.Immutable
import java.time.Instant

// Models are marked @Immutable so Compose compares them by value: after a refresh,
// cards whose game didn't change are skipped instead of redrawn.

@Immutable
data class Team(
    val id: String,
    val name: String,
    val abbreviation: String,
    val logoUrl: String? = null,
    val record: String? = null,
    /** Hex color like "#004C54", used to tint score cards. */
    val primaryColor: String? = null,
)

@Immutable
sealed interface GameStatus {
    data object Scheduled : GameStatus
    /** [period] is the quarter, inning, half, etc. [clock] is the game clock, if the sport has one. */
    data class Live(val period: Int, val clock: String?, val detail: String) : GameStatus
    data class Final(val detail: String = "Final") : GameStatus
    data class Postponed(val detail: String) : GameStatus
}

/** One side of a game: the team plus its score and per-period scoring. */
@Immutable
data class Competitor(
    val team: Team,
    val score: Int?,
    val periodScores: List<Int> = emptyList(),
    val isWinner: Boolean = false,
)

/** A stat leader, e.g. "Passing: J. Allen 24/31, 287 YDS, 2 TD". */
@Immutable
data class Leader(
    val category: String,
    val athleteName: String,
    val headshotUrl: String?,
    val summary: String,
    val teamId: String?,
)

/** Football-style live situation. Other sports can leave it null. */
@Immutable
data class Situation(
    val downDistance: String?,
    val possessionTeamId: String?,
    val isRedZone: Boolean,
    val lastPlay: String?,
)

@Immutable
data class Game(
    val id: String,
    val sport: Sport,
    val startTime: Instant,
    val status: GameStatus,
    val home: Competitor,
    val away: Competitor,
    /** TV networks or services carrying the game, e.g. "CBS", "Prime Video". */
    val broadcasts: List<String> = emptyList(),
    val venue: String? = null,
    val leaders: List<Leader> = emptyList(),
    val situation: Situation? = null,
) {
    val isLive: Boolean get() = status is GameStatus.Live
}

/** A slate of games, e.g. one NFL week. */
@Immutable
data class Scoreboard(
    val sport: Sport,
    val season: Int,
    val seasonType: Int,
    val week: Int?,
    val games: List<Game>,
    val fetchedAtEpochMs: Long,
)

/** Side-by-side team stat for the game detail screen. */
@Immutable
data class TeamStatRow(val label: String, val away: String, val home: String)

@Immutable
data class GameDetail(
    val game: Game?,
    val teamStats: List<TeamStatRow>,
    val leaders: List<Leader>,
)
