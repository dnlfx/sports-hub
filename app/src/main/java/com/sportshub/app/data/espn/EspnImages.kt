package com.sportshub.app.data.espn

/**
 * Normalizes ESPN image URLs so the same picture always has the same address.
 *
 * The scoreboard and the game summary point at different copies of each team logo
 * (".../500/scoreboard/buf.png" vs ".../500/buf.png"). Treated as two images, the logo
 * downloaded twice and flashed back to a placeholder when opening a game. One URL per
 * image means one cache entry that every screen shares.
 */
object EspnImages {
    fun canonical(url: String?): String? {
        val trimmed = url?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return trimmed
            .replaceFirst("http://", "https://")
            .replace("/scoreboard/", "/")
    }
}
