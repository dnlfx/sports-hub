package com.sportshub.app.model

/**
 * Every sport the app knows about. Only sports marked [isAvailable] show data;
 * the rest are listed but greyed out, so turning one on later is a data change, not a redesign.
 *
 * [espnPath] is the sport/league path on ESPN's public site API.
 * [periodName] labels periods in the UI (quarter, inning, period, half).
 * [hasWeeks] is true for sports scheduled in weeks rather than days.
 * [startWord] is what the start of a game is called, e.g. "kickoff" or "tip-off".
 * [hasWinProbability] is true when ESPN charts every game's win probability, so the
 * game page can hold the chart's place while it loads.
 * [seasonSpansYears] is true for leagues whose season runs across New Year, like the
 * NBA's 2025-26 season, which ESPN files under its last year (2026).
 */
enum class Sport(
    val id: String,
    val displayName: String,
    val shortName: String,
    val isAvailable: Boolean,
    val espnPath: String,
    val periodName: String,
    val hasWeeks: Boolean,
    val regularSeasonWeeks: Int = 0,
    val startWord: String = "the start",
    val hasWinProbability: Boolean = false,
    val seasonSpansYears: Boolean = false,
) {
    NFL(
        "nfl", "NFL Football", "NFL", true, "football/nfl", "Q",
        hasWeeks = true, regularSeasonWeeks = 18, startWord = "kickoff", hasWinProbability = true,
    ),
    NBA(
        "nba", "NBA Basketball", "NBA", true, "basketball/nba", "Q",
        hasWeeks = false, startWord = "tip-off", hasWinProbability = true, seasonSpansYears = true,
    ),
    MLB("mlb", "MLB Baseball", "MLB", false, "baseball/mlb", "Inn", hasWeeks = false, startWord = "first pitch"),
    NHL("nhl", "NHL Hockey", "NHL", false, "hockey/nhl", "P", hasWeeks = false, startWord = "puck drop", seasonSpansYears = true),
    SOCCER("soccer", "Soccer", "Soccer", false, "soccer/eng.1", "H", hasWeeks = false, startWord = "kickoff", seasonSpansYears = true);

    /** How a season is named: "2026" for the NFL, "2025-26" for the NBA season ESPN files as 2026. */
    fun seasonName(year: Int): String =
        if (seasonSpansYears) "${year - 1}-${(year % 100).toString().padStart(2, '0')}" else "$year"

    /** True for basketball, whose box scores, stats and plays read differently from football's. */
    val isBasketball: Boolean get() = espnPath.startsWith("basketball/")

    companion object {
        val default: Sport = NFL
        fun fromId(id: String?): Sport = entries.firstOrNull { it.id == id } ?: default
    }
}
