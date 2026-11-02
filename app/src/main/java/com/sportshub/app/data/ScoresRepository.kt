package com.sportshub.app.data

import android.content.Context
import android.util.LruCache
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

    /** The scoreboard already in memory, if any. Never touches disk or network, so screens can draw it on their first frame. */
    fun peekScoreboard(sport: Sport): Scoreboard? = null

    /** The last loaded details for a game, if still in memory, so reopening a game shows it instantly. */
    fun peekGameDetail(gameId: String): GameDetail? = null
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
        var detail = withContext(Dispatchers.Default) {
            // Box score tables as shown: interceptions and fumbles folded in, punts and returns left out.
            EspnParser.parseSummary(sport, body).let { d ->
                d.copy(playerStats = d.playerStats.map { it.copy(tables = BoxScore.fold(it.tables)) }.filter { it.tables.isNotEmpty() })
            }
        }
        val game = detail.game
        // The game summary doesn't always carry the live down & distance. The scoreboard
        // always does, so borrow it from there rather than let it blink out of the header.
        if (game != null && game.isLive && game.situation?.downDistance == null) {
            val fromBoard = scoreboard(sport).getOrNull()?.games?.firstOrNull { it.id == game.id }?.situation
            if (fromBoard != null) {
                detail = detail.copy(
                    game = game.copy(
                        situation = fromBoard.copy(lastPlay = game.situation?.lastPlay ?: fromBoard.lastPlay),
                    ),
                )
            }
        }
        detail.also { details.put(gameId, it) }
    }

    override fun peekScoreboard(sport: Sport): Scoreboard? = memory[sport]

    override fun peekGameDetail(gameId: String): GameDetail? = details.get(gameId)

    /** Recently opened games, newest kept. Small: a game summary can be a few hundred KB parsed. */
    private val details = LruCache<String, GameDetail>(12)
}
