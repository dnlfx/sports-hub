package com.sportshub.app.model

/**
 * Every sport the app knows about. Only sports marked [isAvailable] show data;
 * the rest appear as "coming soon" so adding one later is a data change, not a redesign.
 */
enum class Sport(
    val id: String,
    val displayName: String,
    val shortName: String,
    val isAvailable: Boolean,
) {
    NFL(id = "nfl", displayName = "NFL Football", shortName = "NFL", isAvailable = true),
    NBA(id = "nba", displayName = "NBA Basketball", shortName = "NBA", isAvailable = false),
    MLB(id = "mlb", displayName = "MLB Baseball", shortName = "MLB", isAvailable = false),
    NHL(id = "nhl", displayName = "NHL Hockey", shortName = "NHL", isAvailable = false),
    SOCCER(id = "soccer", displayName = "Soccer", shortName = "Soccer", isAvailable = false);

    companion object {
        val default: Sport = NFL
        fun fromId(id: String?): Sport = entries.firstOrNull { it.id == id } ?: default
    }
}
