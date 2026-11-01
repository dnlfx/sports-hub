package com.sportshub.app.data.espn

import com.sportshub.app.model.Competitor
import com.sportshub.app.model.Game
import com.sportshub.app.model.GameDetail
import com.sportshub.app.model.GameStatus
import com.sportshub.app.model.Leader
import com.sportshub.app.model.PlayerStatLine
import com.sportshub.app.model.PlayerStatTable
import com.sportshub.app.model.Scoreboard
import com.sportshub.app.model.Situation
import com.sportshub.app.model.Sport
import com.sportshub.app.model.Team
import com.sportshub.app.model.TeamPlayerStats
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
        // The summary header has the same shape as a scoreboard event, but keeps the
        // stadium and TV networks elsewhere, so fill those in from the rest of the summary.
        val game = header?.let { h -> runCatching { parseEvent(sport, h) }.getOrNull() }?.let { g ->
            g.copy(
                venue = g.venue ?: root?.obj("gameInfo")?.obj("venue")?.str("fullName"),
                broadcasts = g.broadcasts.ifEmpty { broadcastNames(root?.arr("broadcasts")) },
                situation = if (g.status is GameStatus.Live) summarySituation(root, g.situation) else null,
            )
        }

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

        val playerStats = root?.obj("boxscore")?.arr("players").orEmpty().mapNotNull { parseTeamPlayers(it.obj) }

        return GameDetail(game = game, teamStats = rows, leaders = leaders, playerStats = playerStats)
    }

    /**
     * The live situation from a game summary: the header's if it has one, else the
     * summary's own "situation" block, with the last play taken from the drive log
     * when neither names it.
     */
    private fun summarySituation(root: JsonObject?, fromHeader: Situation?): Situation? {
        val base = fromHeader ?: root?.obj("situation")?.let(::parseSituation)
        val lastPlay = base?.lastPlay ?: lastPlayFromDrives(root?.obj("drives"))
        return when {
            base != null -> base.copy(lastPlay = lastPlay)
            lastPlay != null -> Situation(downDistance = null, possessionTeamId = null, isRedZone = false, lastPlay = lastPlay)
            else -> null
        }
    }

    /** Text of the newest play: the end of the current drive, or of the last finished one. */
    private fun lastPlayFromDrives(drives: JsonObject?): String? {
        drives ?: return null
        val current = drives.obj("current")?.arr("plays")?.lastOrNull()?.obj?.str("text")
        return current ?: drives.arr("previous")?.lastOrNull()?.obj?.arr("plays")?.lastOrNull()?.obj?.str("text")
    }

    private fun parseSituation(s: JsonObject) = Situation(
        downDistance = s.str("downDistanceText") ?: s.str("shortDownDistanceText"),
        possessionTeamId = s.str("possession"),
        isRedZone = s.bool("isRedZone") ?: false,
        lastPlay = s.obj("lastPlay")?.str("text"),
    )

    /** Box score tables in the order a fan reads them; anything ESPN adds later goes at the end. */
    private val tableOrder = listOf(
        "passing", "rushing", "receiving", "defensive", "interceptions", "fumbles",
        "kicking", "punting", "kickReturns", "puntReturns",
    )
    private val tableTitles = mapOf(
        "passing" to "Passing",
        "rushing" to "Rushing",
        "receiving" to "Receiving",
        "defensive" to "Defense",
        "interceptions" to "Interceptions",
        "fumbles" to "Fumbles",
        "kicking" to "Kicking",
        "punting" to "Punting",
        "kickReturns" to "Kick returns",
        "puntReturns" to "Punt returns",
    )

    private fun parseTeamPlayers(block: JsonObject?): TeamPlayerStats? {
        val teamId = block?.obj("team")?.str("id") ?: return null
        val tables = block.arr("statistics").orEmpty().mapNotNull { parseStatTable(it.obj) }
            .sortedBy { t -> tableOrder.indexOf(t.key).let { if (it < 0) Int.MAX_VALUE else it } }
        return TeamPlayerStats(teamId, tables).takeIf { tables.isNotEmpty() }
    }

    private fun parseStatTable(stat: JsonObject?): PlayerStatTable? {
        stat ?: return null
        val key = stat.str("name") ?: return null
        val columns = stat.arr("labels").orEmpty().mapNotNull { it.primitiveString() }
        if (columns.isEmpty()) return null
        val players = stat.arr("athletes").orEmpty().mapNotNull { entry ->
            val o = entry.obj ?: return@mapNotNull null
            val values = o.arr("stats").orEmpty().map { it.primitiveString() ?: "-" }
            // ESPN lists players with no stats as an empty array; they'd be a row of dashes.
            if (values.isEmpty()) return@mapNotNull null
            val athlete = o.obj("athlete") ?: return@mapNotNull null
            PlayerStatLine(
                athleteId = athlete.str("id") ?: athlete.str("displayName") ?: return@mapNotNull null,
                name = shortName(athlete) ?: return@mapNotNull null,
                headshotUrl = headshot(athlete),
                jersey = athlete.str("jersey"),
                values = List(columns.size) { values.getOrNull(it) ?: "-" },
            )
        }
        if (players.isEmpty()) return null
        val title = tableTitles[key]
            ?: stat.str("text")?.substringAfter(' ', "")?.ifBlank { null }
            ?: key.replaceFirstChar { it.uppercase() }
        return PlayerStatTable(key = key, title = title, columns = columns, players = players)
    }

    /** "J. Allen" style name: ESPN's short name, or built from first and last name. */
    private fun shortName(athlete: JsonObject): String? {
        athlete.str("shortName")?.let { return it }
        val first = athlete.str("firstName")?.trim().orEmpty()
        val last = athlete.str("lastName")?.trim().orEmpty()
        if (first.isNotEmpty() && last.isNotEmpty()) return "${first.first()}. $last"
        return athlete.str("displayName") ?: athlete.str("fullName")
    }

    private fun headshot(athlete: JsonObject?): String? =
        EspnImages.canonical(athlete?.obj("headshot")?.str("href") ?: athlete?.str("headshot"))

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
            addAll(broadcastNames(comp.arr("broadcasts")))
            if (isEmpty()) comp.str("broadcast")?.let(::add)
            if (isEmpty()) addAll(broadcastNames(comp.arr("geoBroadcasts")))
        }.distinct()

        val perTeamLeaders = competitors.flatMap { c ->
            val teamId = c.obj("team")?.str("id")
            c.arr("leaders").orEmpty().mapNotNull { cat -> parseLeaderCategory(cat.obj, teamId) }
        }
        val gameLeaders = comp.arr("leaders").orEmpty().mapNotNull { cat -> parseLeaderCategory(cat.obj, null) }
        // Before kickoff, each team's own season leaders are what's worth showing.
        val leaders = when {
            status == GameStatus.Scheduled && perTeamLeaders.isNotEmpty() -> perTeamLeaders
            gameLeaders.isNotEmpty() -> gameLeaders
            else -> perTeamLeaders
        }

        val situation = comp.obj("situation")?.let(::parseSituation)?.takeIf { status is GameStatus.Live }

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

    /** TV names from either broadcast shape: {"names": ["CBS"]} or {"media": {"shortName": "CBS"}}. */
    private fun broadcastNames(list: JsonArray?): List<String> = buildList {
        list.orEmpty().forEach { b ->
            val o = b.obj ?: return@forEach
            val names = o.arr("names").orEmpty().mapNotNull { it.primitiveString() }
            if (names.isNotEmpty()) addAll(names) else o.obj("media")?.str("shortName")?.let(::add)
        }
    }.distinct()

    private fun parseCompetitor(c: JsonObject): Competitor {
        val team = c.obj("team")
        val records = c.arr("records") ?: c.arr("record")
        val overall = records.orEmpty().mapNotNull { it.obj }.let { list ->
            (list.firstOrNull { it.str("type") == "total" || it.str("name") == "overall" } ?: list.firstOrNull())
                ?.let { it.str("summary") ?: it.str("displayValue") }
        }
        val logo = EspnImages.canonical(
            team?.str("logo") ?: team?.arr("logos")?.firstOrNull()?.obj?.str("href"),
        )
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
        val athlete = top.obj("athlete") ?: return null
        return Leader(
            category = (cat.str("displayName") ?: cat.str("name") ?: "").removeSuffix(" Leader"),
            athleteName = shortName(athlete) ?: return null,
            headshotUrl = headshot(athlete),
            summary = top.str("displayValue") ?: "",
            teamId = top.obj("team")?.str("id") ?: athlete.obj("team")?.str("id") ?: teamId,
            position = athlete.obj("position")?.str("abbreviation"),
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
