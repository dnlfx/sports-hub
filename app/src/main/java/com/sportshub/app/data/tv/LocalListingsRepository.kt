package com.sportshub.app.data.tv

import com.sportshub.app.model.Game
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.concurrent.ConcurrentHashMap

/**
 * The user's real antenna lineup for a slate of NFL games, from the free TV guide by ZIP
 * code: which station each game is on, if any.
 *
 * One request covers six hours of every station, so a Sunday's afternoon games take one
 * request and a whole week about four. Answers are kept for half an hour, and when the
 * guide refuses (it can, at any time) the app keeps the estimate for those games and
 * doesn't ask again for a while.
 */
class LocalListingsRepository(private val http: OkHttpClient) {
    private data class Grid(val fetchedAt: Instant, val channels: List<ListingChannel>?)

    private val grids = ConcurrentHashMap<String, Grid>()

    /** The lineup for [games] in [zip]. Games the guide couldn't answer for aren't in [LocalLineup.checked]. */
    suspend fun lineup(zip: String, games: List<Game>, now: Instant = Instant.now()): LocalLineup {
        if (!LocalListings.isZip(zip)) return LocalLineup.Empty
        // The guide lists about two weeks ahead and not far back.
        val inRange = games.filter { it.startTime.isAfter(now.minus(Duration.ofHours(4))) && it.startTime.isBefore(now.plus(Duration.ofDays(13))) }
        if (inRange.isEmpty()) return LocalLineup(zip, emptyMap(), emptySet())
        val blocks = blocks(inRange)
        val results = coroutineScope {
            blocks.map { (start, slate) -> async { slate to grid(zip, start, now) } }.awaitAll()
        }
        val airings = HashMap<String, LocalAiring>()
        val checked = HashSet<String>()
        for ((slate, channels) in results) {
            if (channels == null) continue
            slate.filter { LocalListings.covers(channels, it.startTime) }.mapTo(checked) { it.id }
            airings += LocalListings.match(slate, channels).filterKeys { it in checked }
        }
        return LocalLineup(zip, airings, checked)
    }

    /** Games grouped into six-hour grids, each starting on the hour of its first kickoff. */
    internal fun blocks(games: List<Game>): List<Pair<Instant, List<Game>>> {
        val result = mutableListOf<Pair<Instant, MutableList<Game>>>()
        for (game in games.sortedBy { it.startTime }) {
            val last = result.lastOrNull()
            // A game fits when the grid still runs at least an hour past its kickoff.
            if (last != null && game.startTime.isBefore(last.first.plus(Duration.ofHours(GRID_HOURS - 1L)))) {
                last.second += game
            } else {
                result += game.startTime.truncatedTo(ChronoUnit.HOURS) to mutableListOf(game)
            }
        }
        return result
    }

    private suspend fun grid(zip: String, start: Instant, now: Instant): List<ListingChannel>? {
        val key = "$zip-${start.epochSecond}"
        grids[key]?.let { if (Duration.between(it.fetchedAt, now) < CACHE_TIME) return it.channels }
        val channels = runCatching { fetch(LocalListings.gridUrl(zip, start, GRID_HOURS)) }
            .getOrNull()
            ?.let(LocalListings::parse)
            ?.takeIf { it.isNotEmpty() }
        // A refusal is remembered too, so the guide isn't asked again on every refresh.
        grids[key] = Grid(now, channels)
        return channels
    }

    private suspend fun fetch(url: String): String = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            // The guide only answers web browsers, so the request reads like the one its own
            // web page makes on a phone. It can still refuse, and the estimate covers that.
            .header("User-Agent", BROWSER_USER_AGENT)
            .header("Referer", "https://tvlistings.gracenote.com/")
            .build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            response.body?.string() ?: throw IOException("Empty response")
        }
    }

    companion object {
        const val GRID_HOURS = 6
        private val CACHE_TIME: Duration = Duration.ofMinutes(30)
        private const val BROWSER_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36"
    }
}
