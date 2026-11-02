package com.sportshub.app.data

import android.content.Context
import android.util.LruCache
import com.sportshub.app.data.espn.EspnClient
import com.sportshub.app.data.espn.EspnJson
import com.sportshub.app.data.espn.EspnParser
import com.sportshub.app.data.espn.EspnPlayerParser
import com.sportshub.app.data.espn.EspnTeamParser
import com.sportshub.app.model.CareerTable
import com.sportshub.app.model.DepthChart
import com.sportshub.app.model.Game
import com.sportshub.app.model.GameStatus
import com.sportshub.app.model.PlayerProfile
import com.sportshub.app.model.PlayerSeasonStats
import com.sportshub.app.model.RosterGroup
import com.sportshub.app.model.Sport
import com.sportshub.app.model.Team
import com.sportshub.app.model.TeamListing
import com.sportshub.app.model.TeamPlayerStats
import com.sportshub.app.model.TeamProfile
import com.sportshub.app.model.TeamSchedule
import com.sportshub.app.model.TeamSeasonStats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/** One part of a team page. Each part loads on its own, so one slow request doesn't hold up the rest. */
enum class TeamPart(val id: String) {
    Profile("profile"),
    Schedule("schedule"),
    TeamStats("teamstats"),
    Roster("roster"),
    Depth("depth"),
}

/**
 * Team pages: the league's teams and, for each team, its profile, schedule, season stats,
 * roster and depth chart. Everything is kept in memory and on disk, so a team opened
 * before draws in full on its first frame and then refreshes in place.
 */
class TeamRepository(context: Context, private val client: EspnClient) {

    private val cacheDir = File(context.cacheDir, "teams").apply { mkdirs() }
    private val summaryDir = File(context.cacheDir, "summaries").apply { mkdirs() }
    private val memory = ConcurrentHashMap<String, Any>()

    private fun key(sport: Sport, part: String, teamId: String) = "${sport.id}_${part}_$teamId"

    // ------------------------------------------------------------------
    // League teams
    // ------------------------------------------------------------------

    fun peekTeams(sport: Sport): List<TeamListing>? = peek(key(sport, "teams", "all"))

    suspend fun cachedTeams(sport: Sport): List<TeamListing>? =
        cached(key(sport, "teams", "all")) { EspnTeamParser.parseTeams(it).takeIf { l -> l.isNotEmpty() } }

    suspend fun teams(sport: Sport): Result<List<TeamListing>> =
        fetch(key(sport, "teams", "all"), { client.teams(sport) }) { body ->
            EspnTeamParser.parseTeams(body).also { require(it.isNotEmpty()) { "no teams" } }
        }

    /** A team from the league list, for its colors and logo when another source leaves them out. */
    fun teamInfo(sport: Sport, teamId: String): Team? = peekTeams(sport)?.firstOrNull { it.team.id == teamId }?.team

    // ------------------------------------------------------------------
    // Team page parts
    // ------------------------------------------------------------------

    fun <T> peek(sport: Sport, part: TeamPart, teamId: String): T? = peek(key(sport, part.id, teamId))

    suspend fun <T> cached(sport: Sport, part: TeamPart, teamId: String): T? =
        cached(key(sport, part.id, teamId)) { body -> parse(sport, part, teamId, body) }

    suspend fun <T> load(sport: Sport, part: TeamPart, teamId: String): Result<T> =
        fetch(key(sport, part.id, teamId), { download(sport, part, teamId) }) { body -> parse(sport, part, teamId, body) }

    private suspend fun download(sport: Sport, part: TeamPart, teamId: String): String = when (part) {
        TeamPart.Profile -> client.team(sport, teamId)
        TeamPart.TeamStats -> client.teamStatistics(sport, teamId)
        TeamPart.Roster -> client.roster(sport, teamId)
        TeamPart.Depth -> client.depthCharts(sport, teamId)
        TeamPart.Schedule -> coroutineScope {
            // Regular season and playoffs come from separate requests; saved together as one.
            val regular = async { client.teamSchedule(sport, teamId, seasonType = 2) }
            val playoffs = async { runCatching { client.teamSchedule(sport, teamId, seasonType = 3) }.getOrNull() }
            val events = listOfNotNull(regular.await(), playoffs.await()).flatMap { body ->
                ((EspnJson.parseToJsonElement(body) as? JsonObject)?.get("events") as? JsonArray).orEmpty()
            }
            val first = EspnJson.parseToJsonElement(regular.await()) as? JsonObject
            val merged = JsonObject(
                buildMap {
                    first?.get("season")?.let { put("season", it) }
                    first?.get("requestedSeason")?.let { put("requestedSeason", it) }
                    put("events", JsonArray(events.distinctBy { (it as? JsonObject)?.get("id").toString() }))
                },
            )
            merged.toString()
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> parse(sport: Sport, part: TeamPart, teamId: String, body: String): T = when (part) {
        TeamPart.Profile -> requireNotNull(EspnTeamParser.parseTeamProfile(body)) { "no team" }
            .let { p -> p.copy(team = withDirectory(sport, p.team)) }
        TeamPart.Schedule -> EspnTeamParser.parseSchedule(sport, body).let { s ->
            s.copy(entries = s.entries.map { e -> e.copy(game = withDirectory(sport, e.game)) }.sortedBy { it.game.startTime })
        }
        TeamPart.TeamStats -> EspnTeamParser.parseTeamStatistics(body)
        TeamPart.Roster -> EspnTeamParser.parseRoster(body)
        TeamPart.Depth -> EspnTeamParser.parseDepthCharts(body)
    } as T

    private fun withDirectory(sport: Sport, team: Team): Team {
        val known = teamInfo(sport, team.id) ?: return team
        return team.copy(
            logoUrl = team.logoUrl ?: known.logoUrl,
            primaryColor = team.primaryColor ?: known.primaryColor,
        )
    }

    private fun withDirectory(sport: Sport, game: Game): Game = game.copy(
        home = game.home.copy(team = withDirectory(sport, game.home.team)),
        away = game.away.copy(team = withDirectory(sport, game.away.team)),
    )

    // ------------------------------------------------------------------
    // Player season stats, from the box scores of finished games
    // ------------------------------------------------------------------

    private val boxScores = LruCache<String, List<TeamPlayerStats>>(80)
    private val requests = Semaphore(4)

    fun peekPlayerStats(sport: Sport, teamId: String, schedule: TeamSchedule?): PlayerSeasonStats? {
        val stats = peek<Pair<List<String>, PlayerSeasonStats>>(key(sport, "players", teamId)) ?: return null
        // Still current only if no game has finished since.
        return stats.second.takeIf { schedule == null || stats.first == finishedIds(schedule) }
    }

    /**
     * Adds up the team's box scores. Finished games never change, so each one is
     * downloaded once and kept on disk; later visits only fetch games played since.
     */
    suspend fun playerStats(sport: Sport, teamId: String, schedule: TeamSchedule): Result<PlayerSeasonStats> = runCatching {
        val ids = finishedIds(schedule)
        val perGame = coroutineScope {
            ids.map { gameId -> async { requests.withPermit { boxScore(sport, gameId) } } }.awaitAll()
        }
        val tables = perGame.mapNotNull { game -> game.firstOrNull { it.teamId == teamId }?.tables }
        val result = withContext(Dispatchers.Default) {
            PlayerSeasonStats(gamesCounted = tables.size, tables = BoxScore.seasonTotals(tables))
        }
        memory[key(sport, "players", teamId)] = ids to result
        result
    }

    private fun finishedIds(schedule: TeamSchedule): List<String> =
        schedule.entries.filter { it.game.status is GameStatus.Final }.map { it.game.id }

    private suspend fun boxScore(sport: Sport, gameId: String): List<TeamPlayerStats> {
        boxScores.get(gameId)?.let { return it }
        val file = File(summaryDir, "${sport.id}_$gameId.json")
        val body = withContext(Dispatchers.IO) { if (file.exists()) runCatching { file.readText() }.getOrNull() else null }
            ?: client.summary(sport, gameId).also { full ->
                // Keep only the box score: a fraction of the full summary's size.
                withContext(Dispatchers.IO) {
                    runCatching {
                        val root = EspnJson.parseToJsonElement(full) as? JsonObject
                        val slim = JsonObject(buildMap { root?.get("boxscore")?.let { put("boxscore", it) } })
                        file.writeText(slim.toString())
                    }
                }
            }
        return withContext(Dispatchers.Default) { EspnParser.parseSummary(sport, body).playerStats }
            .also { boxScores.put(gameId, it) }
    }

    // ------------------------------------------------------------------
    // Players
    // ------------------------------------------------------------------

    fun peekPlayer(sport: Sport, athleteId: String): PlayerProfile? = peek(key(sport, "player", athleteId))
    fun peekCareer(sport: Sport, athleteId: String): List<CareerTable>? = peek(key(sport, "career", athleteId))

    suspend fun cachedPlayer(sport: Sport, athleteId: String): PlayerProfile? =
        cached(key(sport, "player", athleteId)) { EspnPlayerParser.parseProfile(it) }

    suspend fun cachedCareer(sport: Sport, athleteId: String): List<CareerTable>? =
        cached(key(sport, "career", athleteId)) { EspnPlayerParser.parseCareer(it) }

    suspend fun player(sport: Sport, athleteId: String): Result<PlayerProfile> =
        fetch(key(sport, "player", athleteId), { client.athlete(sport, athleteId) }) { body ->
            requireNotNull(EspnPlayerParser.parseProfile(body)) { "no player" }.let { p ->
                p.copy(team = p.team?.let { withDirectory(sport, it) })
            }
        }

    suspend fun career(sport: Sport, athleteId: String): Result<List<CareerTable>> =
        fetch(key(sport, "career", athleteId), { client.athleteStats(sport, athleteId) }) { EspnPlayerParser.parseCareer(it) }

    // ------------------------------------------------------------------
    // Memory and disk caching
    // ------------------------------------------------------------------

    @Suppress("UNCHECKED_CAST")
    private fun <T> peek(key: String): T? = memory[key] as? T

    private suspend fun <T> cached(key: String, parse: (String) -> T?): T? {
        peek<T>(key)?.let { return it }
        return withContext(Dispatchers.IO) {
            val file = File(cacheDir, "$key.json")
            if (!file.exists()) return@withContext null
            runCatching { parse(file.readText()) }.getOrNull()
        }?.also { memory.putIfAbsent(key, it as Any) }
    }

    private suspend fun <T> fetch(key: String, download: suspend () -> String, parse: (String) -> T): Result<T> = runCatching {
        val body = download()
        val value = withContext(Dispatchers.Default) { parse(body) }
        memory[key] = value as Any
        withContext(Dispatchers.IO) { runCatching { File(cacheDir, "$key.json").writeText(body) } }
        value
    }
}

/** Typed shortcuts, so screens don't spell out the type of each part. */
fun TeamRepository.peekProfile(sport: Sport, id: String): TeamProfile? = peek(sport, TeamPart.Profile, id)
fun TeamRepository.peekSchedule(sport: Sport, id: String): TeamSchedule? = peek(sport, TeamPart.Schedule, id)
fun TeamRepository.peekTeamStats(sport: Sport, id: String): TeamSeasonStats? = peek(sport, TeamPart.TeamStats, id)
fun TeamRepository.peekRoster(sport: Sport, id: String): List<RosterGroup>? = peek(sport, TeamPart.Roster, id)
fun TeamRepository.peekDepth(sport: Sport, id: String): List<DepthChart>? = peek(sport, TeamPart.Depth, id)
