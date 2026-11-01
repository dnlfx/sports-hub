package com.sportshub.app.data.espn

import com.sportshub.app.model.Competitor
import com.sportshub.app.model.Game
import com.sportshub.app.model.GameDetail
import com.sportshub.app.model.GameStatus
import com.sportshub.app.model.Leader
import com.sportshub.app.model.Scoreboard
import com.sportshub.app.model.Situation
import com.sportshub.app.model.Sport
import com.sportshub.app.model.Team
import com.sportshub.app.model.TeamStatRow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.time.Instant
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

/**
 * Reads ESPN's public site API JSON. Parsing is deliberately lenient: any missing
 * or renamed field gives a null or empty value instead of failing the whole slate.
 */
object EspnParser {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun parseScoreboard(sport: Sport, body: String, fetchedAtEpochMs: Long = System.currentTimeMillis()): Scoreboard {
        val root = json.parseToJsonElement(body).obj
        val events = root?.arr("events").orEmpty()
        val games = events.mapNotNull { runCatching { parseEvent(sport, it.obj) }.getOrNull() }
            .sortedWith(compareBy<Game> { statusOrder(it.status) }.thenBy { it.startTime })
        return Scoreboard(
            sport = sport,
            season = root?.obj("season")?.int("year") ?: OffsetDateTime.now().year,
            seasonType = root?.obj("season")?.int("type") ?: 2,
            week = root?.obj("week")?.int("number"),
            games = games,
            fetchedAtEpochMs = fetchedAtEpochMs,
        )
    }

    fun parseSummary(sport: Sport, body: String): GameDetail {
        val root = json.parseToJsonElement(body).obj
        val header = root?.obj("header")
        // The summary header has the same shape as a scoreboard event.
        val game = header?.let { h -> runCatching { parseEvent(sport, h) }.getOrNull() }

        val homeId = game?.home?.team?.id
        val teams = root?.obj("boxscore")?.arr("teams").orEmpty().mapNotNull { it.obj }
        val homeStats = teams.firstOrNull { it.obj("team")?.str("id") == homeId } ?: teams.getOrNull(1)
        val awayStats = teams.firstOrNull { it !== homeStats } ?: teams.getOrNull(0)
        val awayMap = awayStats.statMap()
        val rows = homeStats?.arr("statistics").orEmpty().mapNotNull { stat ->
            val o = stat.obj ?: return@mapNotNull null
            val name = o.str("name") ?: return@mapNotNull null
            TeamStatRow(
                label = o.str("label") ?: name,
                away = awayMap[name] ?: "-",
                home = o.str("displayValue") ?: "-",
            )
        }

        val leaders = root?.arr("leaders").orEmpty().flatMap { teamBlock ->
            val teamId = teamBlock.obj?.obj("team")?.str("id")
            teamBlock.obj?.arr("leaders").orEmpty().mapNotNull { parseLeaderCategory(it.obj, teamId) }
        }

        return GameDetail(game = game, teamStats = rows, leaders = leaders)
    }

    private fun JsonObject?.statMap(): Map<String, String> =
        this?.arr("statistics").orEmpty().mapNotNull { s ->
            val o = s.obj ?: return@mapNotNull null
            val name = o.str("name") ?: return@mapNotNull null
            name to (o.str("displayValue") ?: "-")
        }.toMap()

    private fun parseEvent(sport: Sport, event: JsonObject?): Game {
        requireNotNull(event)
        val comp = event.arr("competitions")?.firstOrNull()?.obj ?: event
        val competitors = comp.arr("competitors").orEmpty().mapNotNull { it.obj }
        val homeJson = competitors.firstOrNull { it.str("homeAway") == "home" } ?: competitors[0]
        val awayJson = competitors.firstOrNull { it.str("homeAway") == "away" } ?: competitors[1]
        val home = parseCompetitor(homeJson)
        val away = parseCompetitor(awayJson)

        val statusJson = comp.obj("status") ?: event.obj("status")
        val status = parseStatus(sport, statusJson)

        val broadcasts = buildList {
            comp.arr("broadcasts").orEmpty().forEach { b ->
                b.obj?.arr("names").orEmpty().forEach { n -> n.primitiveString()?.let(::add) }
            }
            if (isEmpty()) comp.str("broadcast")?.let(::add)
            if (isEmpty()) comp.arr("geoBroadcasts").orEmpty().forEach { g ->
                g.obj?.obj("media")?.str("shortName")?.let(::add)
            }
        }.distinct()

        val leaders = buildList {
            comp.arr("leaders").orEmpty().forEach { cat -> parseLeaderCategory(cat.obj, null)?.let(::add) }
            if (isEmpty()) {
                competitors.forEach { c ->
                    val teamId = c.obj("team")?.str("id")
                    c.arr("leaders").orEmpty().forEach { cat -> parseLeaderCategory(cat.obj, teamId)?.let(::add) }
                }
            }
        }

        val situation = comp.obj("situation")?.let { s ->
            Situation(
                downDistance = s.str("downDistanceText") ?: s.str("shortDownDistanceText"),
                possessionTeamId = s.str("possession"),
                isRedZone = s.bool("isRedZone") ?: false,
                lastPlay = s.obj("lastPlay")?.str("text"),
            )
        }?.takeIf { status is GameStatus.Live }

        return Game(
            id = event.str("id") ?: comp.str("id") ?: error("event without id"),
            sport = sport,
            startTime = parseDate(event.str("date") ?: comp.str("date")),
            status = status,
            home = home,
            away = away,
            broadcasts = broadcasts,
            venue = comp.obj("venue")?.str("fullName"),
            leaders = leaders,
            situation = situation,
        )
    }

    private fun parseCompetitor(c: JsonObject): Competitor {
        val team = c.obj("team")
        val records = c.arr("records") ?: c.arr("record")
        val overall = records.orEmpty().mapNotNull { it.obj }.let { list ->
            (list.firstOrNull { it.str("type") == "total" || it.str("name") == "overall" } ?: list.firstOrNull())
                ?.let { it.str("summary") ?: it.str("displayValue") }
        }
        val logo = team?.str("logo")
            ?: team?.arr("logos")?.firstOrNull()?.obj?.str("href")
        return Competitor(
            team = Team(
                id = team?.str("id") ?: c.str("id") ?: "",
                name = team?.str("shortDisplayName") ?: team?.str("name") ?: team?.str("displayName") ?: "TBD",
                abbreviation = team?.str("abbreviation") ?: "TBD",
                logoUrl = logo,
                record = overall,
                primaryColor = team?.str("color")?.let { "#$it" },
            ),
            score = c.str("score")?.toIntOrNull() ?: c.obj("score")?.str("value")?.toDoubleOrNull()?.toInt(),
            periodScores = c.arr("linescores").orEmpty().mapNotNull { ls ->
                ls.obj?.let { it.str("value") ?: it.str("displayValue") }?.toDoubleOrNull()?.toInt()
            },
            isWinner = c.bool("winner") ?: false,
        )
    }

    private fun parseStatus(sport: Sport, s: JsonObject?): GameStatus {
        val type = s?.obj("type")
        val state = type?.str("state")
        val name = type?.str("name").orEmpty()
        val detail = type?.str("shortDetail") ?: type?.str("detail") ?: type?.str("description") ?: ""
        return when {
            name.contains("POSTPONED") || name.contains("CANCELED") || name.contains("SUSPENDED") ->
                GameStatus.Postponed(detail.ifEmpty { "Postponed" })
            state == "in" -> GameStatus.Live(
                period = s?.int("period") ?: 0,
                clock = s?.str("displayClock"),
                detail = detail,
            )
            state == "post" -> GameStatus.Final(detail.ifEmpty { "Final" })
            else -> GameStatus.Scheduled
        }
    }

    private fun parseLeaderCategory(cat: JsonObject?, teamId: String?): Leader? {
        cat ?: return null
        val top = cat.arr("leaders")?.firstOrNull()?.obj ?: return null
        val athlete = top.obj("athlete")
        val headshot = athlete?.obj("headshot")?.str("href") ?: athlete?.str("headshot")
        return Leader(
            category = (cat.str("displayName") ?: cat.str("name") ?: "").removeSuffix(" Leader"),
            athleteName = athlete?.str("shortName") ?: athlete?.str("displayName") ?: return null,
            headshotUrl = headshot,
            summary = top.str("displayValue") ?: "",
            teamId = top.obj("team")?.str("id") ?: athlete?.obj("team")?.str("id") ?: teamId,
        )
    }

    private fun statusOrder(status: GameStatus) = when (status) {
        is GameStatus.Live -> 0
        GameStatus.Scheduled -> 1
        is GameStatus.Final -> 2
        is GameStatus.Postponed -> 3
    }

    private fun parseDate(raw: String?): Instant {
        raw ?: return Instant.EPOCH
        return runCatching { Instant.parse(raw) }.getOrNull()
            // ESPN often omits seconds: "2026-11-01T18:00Z"
            ?: runCatching { OffsetDateTime.parse(raw, DateTimeFormatter.ISO_OFFSET_DATE_TIME).toInstant() }.getOrNull()
            ?: runCatching { Instant.parse(raw.replace("Z", ":00Z")) }.getOrNull()
            ?: Instant.EPOCH
    }

    // Small JSON helpers that never throw.
    private val JsonElement?.obj: JsonObject? get() = this as? JsonObject
    private fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject
    private fun JsonObject.arr(key: String): JsonArray? = this[key] as? JsonArray
    private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull
    private fun JsonObject.int(key: String): Int? = str(key)?.toDoubleOrNull()?.toInt()
    private fun JsonObject.bool(key: String): Boolean? = str(key)?.toBooleanStrictOrNull()
    private fun JsonElement.primitiveString(): String? = (this as? JsonPrimitive)?.contentOrNull
}
