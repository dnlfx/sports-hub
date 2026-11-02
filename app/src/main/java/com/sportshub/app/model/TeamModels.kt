package com.sportshub.app.model

import androidx.compose.runtime.Immutable

/** A team's record split, e.g. "Home 3-1". */
@Immutable
data class RecordSplit(val label: String, val summary: String)

/** Who a team is and how its season is going, for the top of the team page. */
@Immutable
data class TeamProfile(
    val team: Team,
    /** Full name, e.g. "Buffalo Bills". */
    val displayName: String,
    /** e.g. "1st in AFC East". */
    val standing: String?,
    /** Home and road records. The overall record is on [team]. */
    val splits: List<RecordSplit> = emptyList(),
    val pointsForPerGame: Double? = null,
    val pointsAgainstPerGame: Double? = null,
    val pointDifferential: Int? = null,
    /** Positive for a winning streak, negative for a losing one. */
    val streak: Int? = null,
)

/** One game on a team's schedule. */
@Immutable
data class ScheduleEntry(
    val game: Game,
    /** Week number in the regular season; null for playoff rounds. */
    val week: Int?,
    /** "Week 3", "Wild Card"... */
    val label: String,
    /** 2 for regular season, 3 for playoffs. */
    val seasonType: Int,
)

@Immutable
data class TeamSchedule(val season: Int?, val entries: List<ScheduleEntry>)

/** One team season stat, e.g. "Net Passing Yards 2,104 (233.8 a game)". */
@Immutable
data class SeasonStat(val key: String, val label: String, val value: String, val perGame: String? = null)

@Immutable
data class SeasonStatGroup(val key: String, val title: String, val stats: List<SeasonStat>)

@Immutable
data class TeamSeasonStats(val season: Int?, val groups: List<SeasonStatGroup>)

/** Season totals for each player, added up from the box scores of the team's games so far. */
@Immutable
data class PlayerSeasonStats(val gamesCounted: Int, val tables: List<PlayerStatTable>)

@Immutable
data class RosterPlayer(
    val id: String,
    val name: String,
    val jersey: String?,
    /** Position abbreviation, e.g. "QB". */
    val position: String?,
    val positionName: String?,
    val headshotUrl: String?,
    val age: Int?,
    val height: String?,
    val weight: String?,
    /** Seasons in the league; 0 for a rookie. */
    val experience: Int?,
    val college: String?,
    /** Injury status like "Questionable" or "Out", when there is one. */
    val injury: String?,
)

/** "Offense", "Defense" or "Special teams" with its players. */
@Immutable
data class RosterGroup(val key: String, val title: String, val players: List<RosterPlayer>)

@Immutable
data class DepthPlayer(val id: String, val name: String, val injury: String?)

/** One spot on a depth chart, e.g. "QB" with the starter first. */
@Immutable
data class DepthPosition(val key: String, val abbreviation: String, val name: String, val players: List<DepthPlayer>)

/** One formation's depth chart, e.g. "3WR 1TE" or "Base 3-4 D". */
@Immutable
data class DepthChart(val name: String, val positions: List<DepthPosition>)

/** A team in the league list, with its full name for display. */
@Immutable
data class TeamListing(val team: Team, val displayName: String)

/** Enough about a player to draw the top of their profile before it loads. */
@Immutable
data class PlayerRef(
    val id: String,
    val name: String,
    val headshotUrl: String? = null,
    val position: String? = null,
    val jersey: String? = null,
    val team: Team? = null,
)

/** One line of a player's bio, e.g. "Height 6' 5\"". */
@Immutable
data class BioItem(val label: String, val value: String)

/** A headline season stat with its league rank, e.g. "YDS 1,504 · 15th". */
@Immutable
data class RankedStat(val label: String, val value: String, val rank: String?)

@Immutable
data class PlayerProfile(
    val id: String,
    val name: String,
    val headshotUrl: String?,
    val jersey: String?,
    val position: String?,
    val positionName: String?,
    val team: Team?,
    val bio: List<BioItem>,
    /** e.g. "2026 regular season stats". */
    val summaryTitle: String?,
    val summary: List<RankedStat>,
)

/** One season (or the career total) in a career table. */
@Immutable
data class CareerRow(val label: String, val team: String?, val values: List<String>)

/** A player's year-by-year stats in one category, e.g. passing. */
@Immutable
data class CareerTable(
    val key: String,
    val title: String,
    val columns: List<String>,
    /** Newest season first. */
    val seasons: List<CareerRow>,
    val totals: CareerRow?,
)
