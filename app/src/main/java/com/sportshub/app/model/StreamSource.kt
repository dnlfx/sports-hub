package com.sportshub.app.model

import kotlinx.serialization.Serializable

/**
 * A page URL the user added on the Sources screen. The app ships with none.
 * Step 3 adds scanning these pages for standard video (files, HLS, embeds).
 */
@Serializable
data class StreamSource(
    val id: String,
    val url: String,
    val label: String,
    val sportId: String,
    val addedAtEpochMs: Long,
) {
    val sport: Sport get() = Sport.fromId(sportId)
}
