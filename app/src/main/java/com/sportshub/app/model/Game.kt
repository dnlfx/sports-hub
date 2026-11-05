package com.sportshub.app.model

import androidx.compose.runtime.Immutable
import java.time.Instant
import java.time.LocalDate

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

/**
 * A stat leader, e.g. "Passing: J. Allen 24/31, 287 YDS, 2 TD". Before kickoff these
 * are the team's season leaders; once the game starts they're the game's leaders.
 */
@Immutable
data class Leader(
    val category: String,
    val athleteName: String,
    val headshotUrl: String?,
    val summary: String,
    val teamId: String?,
    /** Position abbreviation like "QB", when known. */
    val position: String? = null,
    val athleteId: String? = null,
)

/** One player's line in a box score table, e.g. a passer's C/ATT, YDS, TD, INT. */
@Immutable
data class PlayerStatLine(
    val athleteId: String,
    val name: String,
    val headshotUrl: String?,
    val jersey: String?,
    /** One value per column of the table, in the same order as [PlayerStatTable.columns]. */
    val values: List<String>,
    /** In the starting lineup (basketball). */
    val starter: Boolean = false,
)

/** One box score table for one team, e.g. "Passing" with columns C/ATT, YDS, TD... */
@Immutable
data class PlayerStatTable(
    /** ESPN's category key, e.g. "passing". */
    val key: String,
    val title: String,
    val columns: List<String>,
    val players: List<PlayerStatLine>,
)

/** A team's full box score: every stat table with at least one player in it. */
@Immutable
data class TeamPlayerStats(val teamId: String, val tables: List<PlayerStatTable>)

/** Live situation: down and distance in football, the last play in any sport. */
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

/** A slate of games, e.g. one NFL week or one NBA day. */
@Immutable
data class Scoreboard(
    val sport: Sport,
    val season: Int,
    val seasonType: Int,
    val week: Int?,
    val games: List<Game>,
    val fetchedAtEpochMs: Long,
    /** The day this slate is for, in sports played by day (the NBA). */
    val date: LocalDate? = null,
    /** Every day of the season with games on it, in sports played by day. */
    val calendar: List<LocalDate> = emptyList(),
)

/** Side-by-side team stat for the game detail screen. */
@Immutable
data class TeamStatRow(val label: String, val away: String, val home: String)

@Immutable
data class GameDetail(
    val game: Game?,
    val teamStats: List<TeamStatRow>,
    val leaders: List<Leader>,
    /** Per-player box score for each team. Empty before kickoff. */
    val playerStats: List<TeamPlayerStats> = emptyList(),
    /** How each team's chances moved play by play, or ESPN's pregame pick. Null when ESPN has neither. */
    val winProbability: WinProbability? = null,
)

/** What kind of play moved the game, for the marker on the win probability chart. */
enum class PlayKind {
    // Football
    Touchdown, FieldGoal, Safety,
    // Basketball
    ThreePointer, Dunk, Basket, FreeThrow,
    // Both
    Turnover, Other,
}

/** The home team's chance of winning right after one play, with the play itself when known. */
@Immutable
data class WinProbPoint(
    /** From 0 to 1. The away team's chance is the rest, give or take a tie. */
    val homeWinPct: Float,
    val playId: String? = null,
    val period: Int? = null,
    val clock: String? = null,
    val text: String? = null,
    val awayScore: Int? = null,
    val homeScore: Int? = null,
    val isScoring: Boolean = false,
    /** The team that had the ball. */
    val teamId: String? = null,
    val kind: PlayKind = PlayKind.Other,
)

@Immutable
data class WinProbability(
    /** One point per play, in order. Empty before kickoff. */
    val points: List<WinProbPoint>,
    /** ESPN's pregame chance for the home team, from 0 to 1. */
    val pregameHomePct: Float? = null,
)
