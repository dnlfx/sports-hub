package com.sportshub.app.data.espn

import com.sportshub.app.model.Sport
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.CacheControl
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Fetches raw JSON from ESPN's public site API. No key or account is needed,
 * and nothing is tied to the user's location, so it works the same on a VPN.
 */
class EspnClient(private val http: OkHttpClient) {
    private val base = "https://site.api.espn.com/apis/site/v2/sports"
    private val webBase = "https://site.web.api.espn.com/apis/common/v3/sports"

    suspend fun scoreboard(sport: Sport, season: Int? = null, seasonType: Int? = null, week: Int? = null): String {
        val params = buildList {
            if (week != null) add("week=$week")
            if (seasonType != null) add("seasontype=$seasonType")
            if (season != null) add("dates=$season")
        }
        val query = if (params.isEmpty()) "" else params.joinToString("&", prefix = "?")
        return get("$base/${sport.espnPath}/scoreboard$query", live = true)
    }

    suspend fun summary(sport: Sport, eventId: String): String =
        get("$base/${sport.espnPath}/summary?event=$eventId", live = true)

    suspend fun teams(sport: Sport): String = get("$base/${sport.espnPath}/teams")

    suspend fun team(sport: Sport, teamId: String): String = get("$base/${sport.espnPath}/teams/$teamId")

    suspend fun teamSchedule(sport: Sport, teamId: String, seasonType: Int): String =
        get("$base/${sport.espnPath}/teams/$teamId/schedule?seasontype=$seasonType")

    suspend fun teamStatistics(sport: Sport, teamId: String): String =
        get("$base/${sport.espnPath}/teams/$teamId/statistics")

    suspend fun roster(sport: Sport, teamId: String): String = get("$base/${sport.espnPath}/teams/$teamId/roster")

    suspend fun depthCharts(sport: Sport, teamId: String): String =
        get("$base/${sport.espnPath}/teams/$teamId/depthcharts")

    /** A player's bio and headline stats. */
    suspend fun athlete(sport: Sport, athleteId: String): String = get("$webBase/${sport.espnPath}/athletes/$athleteId")

    /** A player's stats for every season of their career. */
    suspend fun athleteStats(sport: Sport, athleteId: String): String =
        get("$webBase/${sport.espnPath}/athletes/$athleteId/stats")

    /**
     * GET with up to 3 attempts and a short backoff, so a brief network drop doesn't surface as an error.
     * [live] data (scores, game details) always checks with the server, so a cached copy is never
     * shown as current; everything else may come from the HTTP cache while it's fresh.
     */
    private suspend fun get(url: String, live: Boolean = false): String = withContext(Dispatchers.IO) {
        var lastError: IOException? = null
        repeat(3) { attempt ->
            try {
                val request = Request.Builder()
                    .url(url)
                    .header("Accept", "application/json")
                    .header("User-Agent", "SportsHub-Android/1.0")
                    .apply { if (live) cacheControl(CacheControl.FORCE_NETWORK) }
                    .build()
                http.newCall(request).execute().use { response ->
                    // A 4xx won't fix itself on retry, so fail straight away.
                    if (response.code in 400..499) throw ClientError(response.code)
                    if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                    return@withContext response.body?.string() ?: throw IOException("Empty response")
                }
            } catch (e: ClientError) {
                throw e
            } catch (e: IOException) {
                lastError = e
                if (attempt < 2) delay(600L * (attempt + 1))
            }
        }
        throw lastError ?: IOException("Request failed")
    }
}

/** The server rejected the request itself (HTTP 4xx). */
class ClientError(val code: Int) : IOException("HTTP $code")
