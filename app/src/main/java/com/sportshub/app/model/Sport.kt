package com.sportshub.app.model

/**
 * Every sport the app knows about. Only sports marked [isAvailable] show data;
 * the rest appear as "coming soon" so adding one later is a data change, not a redesign.
 *
 * [espnPath] is the sport/league path on ESPN's public site API.
 * [periodName] labels periods in the UI (quarter, inning, period, half).
 * [hasWeeks] is true for sports scheduled in weeks rather than days.
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
) {
    NFL("nfl", "NFL Football", "NFL", true, "football/nfl", "Q", hasWeeks = true, regularSeasonWeeks = 18),
    NBA("nba", "NBA Basketball", "NBA", false, "basketball/nba", "Q", hasWeeks = false),
    MLB("mlb", "MLB Baseball", "MLB", false, "baseball/mlb", "Inn", hasWeeks = false),
    NHL("nhl", "NHL Hockey", "NHL", false, "hockey/nhl", "P", hasWeeks = false),
    SOCCER("soccer", "Soccer", "Soccer", false, "soccer/eng.1", "H", hasWeeks = false);

    companion object {
        val default: Sport = NFL
        fun fromId(id: String?): Sport = entries.firstOrNull { it.id == id } ?: default
    }
}
