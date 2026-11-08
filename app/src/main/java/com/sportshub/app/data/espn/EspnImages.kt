package com.sportshub.app.data.espn

/**
 * Normalizes ESPN image URLs so the same picture always has the same address, and
 * points the app at small copies of each picture instead of the full-size originals.
 */
object EspnImages {
    private const val HOST = "https://a.espncdn.com"

    /** Side of the square a logo is fetched at, in pixels. */
    const val LOGO_PX = 256

    /**
     * The scoreboard and the game summary point at different copies of each team logo
     * (".../500/scoreboard/buf.png" vs ".../500/buf.png"). Treated as two images, the logo
     * downloaded twice and flashed back to a placeholder when opening a game. One URL per
     * image means one cache entry that every screen shares.
     */
    fun canonical(url: String?): String? {
        val trimmed = url?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return trimmed
            .replaceFirst("http://", "https://")
            .replace("/scoreboard/", "/")
    }

    /**
     * The same picture, resized on ESPN's image server before it's sent, or null when
     * [url] isn't one ESPN can resize.
     *
     * Logos come as 500×500 PNGs and headshots as 350×254, far bigger than they're ever
     * shown, so every one used to download several times the bytes it needed. Headshots
     * keep their shape; logos stay square with their transparent background.
     */
    fun resized(url: String?): String? {
        if (url == null || !url.startsWith("$HOST/i/") || '?' in url) return null
        val path = url.removePrefix(HOST)
        val size = if ("/headshots/" in path) "w=$LOGO_PX&h=${LOGO_PX * 254 / 350}" else "w=$LOGO_PX&h=$LOGO_PX"
        return "$HOST/combiner/i?img=$path&$size&scale=crop&location=origin&transparent=true"
    }
}
