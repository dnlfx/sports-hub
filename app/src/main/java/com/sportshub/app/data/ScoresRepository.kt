package com.sportshub.app.data

import android.content.Context
import com.sportshub.app.data.espn.EspnClient
import com.sportshub.app.data.espn.EspnParser
import com.sportshub.app.model.GameDetail
import com.sportshub.app.model.Scoreboard
import com.sportshub.app.model.Sport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/** Which slate to load. A null week means "whatever is current". */
data class SlateRequest(val season: Int? = null, val seasonType: Int? = null, val week: Int? = null) {
    val isCurrent: Boolean get() = week == null
}

/**
 * Where scores come from. Kept behind an interface so the data provider can be
 * swapped (ESPN public data now, a licensed feed later) without touching the UI.
 */
interface ScoresRepository {
    /** The last saved scoreboard for instant display on launch, or null on first run. */
    suspend fun cachedScoreboard(sport: Sport): Scoreboard?
    suspend fun scoreboard(sport: Sport, request: SlateRequest = SlateRequest()): Result<Scoreboard>
    suspend fun gameDetail(sport: Sport, gameId: String): Result<GameDetail>
}

class EspnScoresRepository(
    context: Context,
    private val client: EspnClient,
) : ScoresRepository {

    private val cacheDir = File(context.cacheDir, "scores").apply { mkdirs() }
    private val memory = ConcurrentHashMap<Sport, Scoreboard>()

    private fun cacheFile(sport: Sport) = File(cacheDir, "${sport.id}_current.json")

    override suspend fun cachedScoreboard(sport: Sport): Scoreboard? {
        memory[sport]?.let { return it }
        return withContext(Dispatchers.IO) {
            val file = cacheFile(sport)
            if (!file.exists()) return@withContext null
            runCatching { EspnParser.parseScoreboard(sport, file.readText(), file.lastModified()) }.getOrNull()
        }?.also { memory[sport] = it }
    }

    override suspend fun scoreboard(sport: Sport, request: SlateRequest): Result<Scoreboard> = runCatching {
        val body = client.scoreboard(sport, request.season, request.seasonType, request.week)
        val board = withContext(Dispatchers.Default) { EspnParser.parseScoreboard(sport, body) }
        if (request.isCurrent) {
            memory[sport] = board
            withContext(Dispatchers.IO) { runCatching { cacheFile(sport).writeText(body) } }
        }
        board
    }

    override suspend fun gameDetail(sport: Sport, gameId: String): Result<GameDetail> = runCatching {
        val body = client.summary(sport, gameId)
        withContext(Dispatchers.Default) { EspnParser.parseSummary(sport, body) }
    }
}
