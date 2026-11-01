package com.sportshub.app.data.espn

import com.sportshub.app.model.Sport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Fetches raw JSON from ESPN's public site API. No key or account is needed,
 * and nothing is tied to the user's location, so it works the same on a VPN.
 */
class EspnClient(
    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build(),
) {
    private val base = "https://site.api.espn.com/apis/site/v2/sports"

    suspend fun scoreboard(sport: Sport, season: Int? = null, seasonType: Int? = null, week: Int? = null): String {
        val params = buildList {
            if (week != null) add("week=$week")
            if (seasonType != null) add("seasontype=$seasonType")
            if (season != null) add("dates=$season")
        }
        val query = if (params.isEmpty()) "" else params.joinToString("&", prefix = "?")
        return get("$base/${sport.espnPath}/scoreboard$query")
    }

    suspend fun summary(sport: Sport, eventId: String): String =
        get("$base/${sport.espnPath}/summary?event=$eventId")

    /** GET with up to 3 attempts and a short backoff, so a brief network drop doesn't surface as an error. */
    private suspend fun get(url: String): String = withContext(Dispatchers.IO) {
        var lastError: IOException? = null
        repeat(3) { attempt ->
            try {
                val request = Request.Builder()
                    .url(url)
                    .header("Accept", "application/json")
                    .header("User-Agent", "SportsHub-Android/1.0")
                    .build()
                http.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                    return@withContext response.body?.string() ?: throw IOException("Empty response")
                }
            } catch (e: IOException) {
                lastError = e
                if (attempt < 2) delay(600L * (attempt + 1))
            }
        }
        throw lastError ?: IOException("Request failed")
    }
}
