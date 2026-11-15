package com.sportshub.app.data.tv

import com.sportshub.app.model.Game
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.concurrent.ConcurrentHashMap

/**
 * The user's real antenna lineup for a slate of NFL games: which station each game is on,
 * if any, read on the phone from the free TV guide.
 *
 * The guide is asked by the user's ZIP code or, in a market where every ZIP gets the same
 * stations (Dallas-Fort Worth), by a ZIP in that market, so those users get exact channels
 * without typing one. One request covers six hours of every station, so a Sunday's
 * afternoon games take one request and a whole week about four. Answers are kept for half
 * an hour, and the football part of each good answer is saved on the phone: when the
 * guide refuses (it can, at any time), the last answer it gave stands in, and games it
 * never answered for keep the estimate.
 */
class LocalListingsRepository(private val http: OkHttpClient, private val dir: File? = null) {
    private data class Grid(val fetchedAt: Instant, val channels: List<ListingChannel>?, val keepFor: Duration)

    private val grids = ConcurrentHashMap<String, Grid>()

    /** The ZIP to ask the guide with: the user's own, else the one that stands for their market. */
    fun zipFor(zip: String?, market: TvMarket?): String? =
        zip?.takeIf(LocalListings::isZip) ?: market?.let { SAMPLE_ZIPS[it.id] }

    /**
     * The lineup for [games] by the user's [zip], or else by their [market]'s. Null when
     * there's nothing to ask the guide with.
     */
    suspend fun lineup(zip: String?, market: TvMarket?, games: List<Game>, now: Instant = Instant.now()): LocalLineup? =
        zipFor(zip, market)?.let { lineup(it, games, now) }

    /** The lineup for [games] in [zip]. Games the guide couldn't answer for aren't in [LocalLineup.checked]. */
    suspend fun lineup(zip: String, games: List<Game>, now: Instant = Instant.now()): LocalLineup {
        if (!LocalListings.isZip(zip)) return LocalLineup.Empty
        // The guide lists about two weeks ahead and three days back.
        val inRange = games.filter { it.startTime.isAfter(now.minus(LOOK_BACK)) && it.startTime.isBefore(now.plus(LOOK_AHEAD)) }
        if (inRange.isEmpty()) return LocalLineup(zip, emptyMap(), emptySet())
        val results = coroutineScope {
            blocks(inRange).map { (start, slate) -> async { Triple(start, slate, grid(zip, start, now)) } }.awaitAll()
        }
        val airings = HashMap<String, LocalAiring>()
        val checked = HashSet<String>()
        for ((start, slate, channels) in results) {
            if (channels == null) continue
            val end = start.plus(Duration.ofHours(GRID_HOURS.toLong()))
            val part = LocalListings.lineup(zip, slate, channels) { t -> !t.isBefore(start) && t.isBefore(end) }
            airings += part.airings
            checked += part.checked
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
        grids[key]?.let { if (Duration.between(it.fetchedAt, now) < it.keepFor) return it.channels }
        val fresh = fetchGrid(zip, start)
        if (fresh != null) {
            grids[key] = Grid(now, fresh, CACHE_TIME)
            save(key, fresh, now)
            return fresh
        }
        // Refused: the last answer saved on the phone, and the guide is asked again in a few minutes.
        val saved = load(key, now)
        grids[key] = Grid(now, saved, RETRY_AFTER)
        return saved
    }

    /** The guide's grid, asked twice before giving up. Null when it refuses or lists nothing. */
    private suspend fun fetchGrid(zip: String, start: Instant): List<ListingChannel>? {
        repeat(ATTEMPTS) { attempt ->
            if (attempt > 0) delay(RETRY_DELAY_MS)
            val channels = runCatching { fetch(LocalListings.gridUrl(zip, start, GRID_HOURS)) }
                .getOrNull()
                ?.let(LocalListings::parse)
            if (!channels.isNullOrEmpty()) return channels
        }
        return null
    }

    private suspend fun save(key: String, channels: List<ListingChannel>, now: Instant) {
        val folder = dir ?: return
        withContext(Dispatchers.IO) {
            runCatching {
                folder.mkdirs()
                val tmp = File(folder, "$key.tmp")
                tmp.writeText(LocalListings.footballOnly(channels))
                tmp.renameTo(File(folder, "$key.json"))
                // Grids for games long over aren't needed again.
                folder.listFiles()?.forEach { file ->
                    val epoch = file.name.substringAfterLast('-').substringBefore('.').toLongOrNull()
                    if (epoch != null && Instant.ofEpochSecond(epoch).isBefore(now.minus(LOOK_BACK))) file.delete()
                }
            }
        }
    }

    private suspend fun load(key: String, now: Instant): List<ListingChannel>? {
        val file = dir?.let { File(it, "$key.json") } ?: return null
        return withContext(Dispatchers.IO) {
            runCatching {
                // A copy from more than a few days ago may predate the week's coverage decisions.
                if (!file.exists() || Duration.between(Instant.ofEpochMilli(file.lastModified()), now) > SAVED_FOR) {
                    null
                } else {
                    LocalListings.parse(file.readText()).takeIf { it.isNotEmpty() }
                }
            }.getOrNull()
        }
    }

    private suspend fun fetch(url: String): String = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            // The guide only answers web browsers, so the request reads like the one its own
            // web page makes on a phone. It can still refuse, and the saved copy or the
            // estimate covers that.
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
        private val RETRY_AFTER: Duration = Duration.ofMinutes(5)
        private val SAVED_FOR: Duration = Duration.ofDays(4)
        private val LOOK_BACK: Duration = Duration.ofDays(3)
        private val LOOK_AHEAD: Duration = Duration.ofDays(13)
        private const val ATTEMPTS = 2
        private const val RETRY_DELAY_MS = 1_500L

        /**
         * Markets where every ZIP's antenna lineup carries the same games, and a ZIP that
         * stands for all of them. In Dallas-Fort Worth every ZIP gets the same Dallas
         * stations (KTVT 11 CBS, KDFW 4 FOX, KDFI 27 FOX, KXAS 5 NBC, WFAA 8 ABC); the
         * northern edge also picks up Sherman's stations, which a ZIP of its own shows.
         */
        val SAMPLE_ZIPS: Map<String, String> = mapOf("dallas" to "75201")

        private const val BROWSER_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36"
    }
}
