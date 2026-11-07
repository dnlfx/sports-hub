package com.sportshub.app.data.espn

import com.sportshub.app.data.CollegeFootball
import com.sportshub.app.model.Competitor
import com.sportshub.app.model.Game
import com.sportshub.app.model.GameDetail
import com.sportshub.app.model.GameStatus
import com.sportshub.app.model.Leader
import com.sportshub.app.model.PlayKind
import com.sportshub.app.model.PlayerStatLine
import com.sportshub.app.model.PlayerStatTable
import com.sportshub.app.model.Scoreboard
import com.sportshub.app.model.Situation
import com.sportshub.app.model.Sport
import com.sportshub.app.model.Team
import com.sportshub.app.model.TeamPlayerStats
import com.sportshub.app.model.TeamStatRow
import com.sportshub.app.model.WinProbPoint
import com.sportshub.app.model.WinProbability
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

/**
 * Reads ESPN's public site API JSON. Parsing is deliberately lenient: any missing
 * or renamed field gives a null or empty value instead of failing the whole slate.
 */
object EspnParser {

    private val json = EspnJson

    fun parseScoreboard(sport: Sport, body: String, fetchedAtEpochMs: Long = System.currentTimeMillis()): Scoreboard {
        val root = json.parseToJsonElement(body).obj
        val events = root?.arr("events").orEmpty()
        val games = events.mapNotNull { runCatching { parseEvent(sport, it.obj) }.getOrNull() }
            // College football: only games with a team from the top conferences.
            .filter { !sport.isCollegeFootball || CollegeFootball.isTopGame(it) }
            .sortedWith(compareBy<Game> { statusOrder(it.status) }.thenBy { it.startTime })
        return Scoreboard(
            sport = sport,
            season = root?.obj("season")?.int("year") ?: OffsetDateTime.now().year,
            seasonType = root?.obj("season")?.int("type") ?: 2,
            week = root?.obj("week")?.int("number"),
            games = games,
            fetchedAtEpochMs = fetchedAtEpochMs,
            date = root?.obj("day")?.str("date")?.let(::localDate),
            calendar = if (sport.hasWeeks) emptyList() else calendarDays(root),
        )
    }

    /**
     * The days of the season with games, for sports played by day. ESPN lists them as
     * times like "2025-10-21T07:00Z", where the date part is the US game day.
     */
    private fun calendarDays(root: JsonObject?): List<LocalDate> =
        root?.arr("leagues")?.firstOrNull()?.obj?.arr("calendar").orEmpty()
            .mapNotNull { it.primitiveString()?.let(::localDate) }
            .distinct()
            .sorted()

    /** "2025-10-21" or "2025-10-21T07:00Z" as a date. */
    internal fun localDate(raw: String): LocalDate? = runCatching { LocalDate.parse(raw.trim().take(10)) }.getOrNull()

    fun parseSummary(sport: Sport, body: String): GameDetail {
        val root = json.parseToJsonElement(body).obj
        val header = root?.obj("header")
        // The summary header has the same shape as a scoreboard event, but keeps the
        // stadium and TV networks elsewhere, so fill those in from the rest of the summary.
        val game = header?.let { h -> runCatching { parseEvent(sport, h) }.getOrNull() }?.let { g ->
            g.copy(
                venue = g.venue ?: root?.obj("gameInfo")?.obj("venue")?.str("fullName"),
                broadcasts = g.broadcasts.ifEmpty { broadcastNames(root?.arr("broadcasts")) },
                situation = if (g.status is GameStatus.Live) summarySituation(root, g.situation, sport) else null,
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
            if (sport.isBasketball && name in basketballSkippedStats) return@mapNotNull null
            val label = o.str("label") ?: name
            TeamStatRow(
                label = if (sport.isBasketball) basketballStatLabels[label] ?: label else label,
                away = awayMap[name] ?: "-",
                home = o.str("displayValue") ?: "-",
            )
        }

        val leaders = root?.arr("leaders").orEmpty().flatMap { teamBlock ->
            val teamId = teamBlock.obj?.obj("team")?.str("id")
            teamBlock.obj?.arr("leaders").orEmpty().mapNotNull { parseLeaderCategory(it.obj, teamId) }
        }

        val playerStats = root?.obj("boxscore")?.arr("players").orEmpty().mapNotNull { parseTeamPlayers(sport, it.obj) }

        return GameDetail(
            game = game,
            teamStats = rows,
            leaders = leaders,
            playerStats = playerStats,
            winProbability = parseWinProbability(root, sport),
        )
    }

    /**
     * The win probability line: ESPN's "winprobability" list (the home team's chance after
     * each play), matched by play id to the plays in the drive log for their text, time and
     * score. Before kickoff there is no line, only the "predictor" pregame pick.
     */
    internal fun parseWinProbability(root: JsonObject?, sport: Sport = Sport.NFL): WinProbability? {
        root ?: return null
        val plays = playsById(root, sport)
        val points = root.arr("winprobability").orEmpty().mapNotNull { e ->
            val o = e.obj ?: return@mapNotNull null
            val pct = o.double("homeWinPercentage")?.toFloat()?.coerceIn(0f, 1f) ?: return@mapNotNull null
            val id = o.str("playId")
            id?.let { plays[it] }?.copy(homeWinPct = pct) ?: WinProbPoint(homeWinPct = pct, playId = id)
        }
        val pregame = root.obj("predictor")?.obj("homeTeam")?.let { it.double("gameProjection") ?: it.double("teamChanceWin") }
            ?.let { (it / 100.0).toFloat().coerceIn(0f, 1f) }
        if (points.isEmpty() && pregame == null) return null
        return WinProbability(points = points, pregameHomePct = pregame)
    }

    /** Every play in the summary by id: from the drive log, plus the scoring plays list for anything it misses. */
    private fun playsById(root: JsonObject, sport: Sport): Map<String, WinProbPoint> {
        val out = LinkedHashMap<String, WinProbPoint>()
        val drives = root.obj("drives")
        val driveList = drives?.arr("previous").orEmpty().mapNotNull { it.obj } + listOfNotNull(drives?.obj("current"))
        driveList.forEach { drive ->
            val teamId = drive.obj("team")?.str("id")
            drive.arr("plays").orEmpty().forEach { p -> parsePlay(p.obj, teamId, sport)?.let { out.putIfAbsent(it.playId!!, it) } }
        }
        // Some sports list plays at the top level instead.
        root.arr("plays").orEmpty().forEach { p -> parsePlay(p.obj, null, sport)?.let { out.putIfAbsent(it.playId!!, it) } }
        root.arr("scoringPlays").orEmpty().forEach { p ->
            val o = p.obj ?: return@forEach
            val id = o.str("id") ?: return@forEach
            val scoring = parsePlay(o, o.obj("team")?.str("id"), sport, isScoring = true) ?: return@forEach
            val known = out[id]
            out[id] = if (known == null) {
                scoring
            } else {
                known.copy(isScoring = true, kind = if (known.kind != PlayKind.Other) known.kind else scoring.kind)
            }
        }
        return out
    }

    private fun parsePlay(p: JsonObject?, teamId: String?, sport: Sport, isScoring: Boolean = false): WinProbPoint? {
        p ?: return null
        val id = p.str("id") ?: return null
        val type = p.obj("type")?.str("text").orEmpty()
        val scoring = isScoring || p.bool("scoringPlay") == true
        return WinProbPoint(
            homeWinPct = 0.5f,
            playId = id,
            period = p.obj("period")?.int("number"),
            clock = p.obj("clock")?.str("displayValue"),
            text = p.str("text")?.trim()?.takeIf { it.isNotEmpty() },
            awayScore = p.int("awayScore"),
            homeScore = p.int("homeScore"),
            isScoring = scoring,
            teamId = p.obj("team")?.str("id") ?: p.obj("start")?.obj("team")?.str("id") ?: teamId,
            kind = if (sport.isBasketball) {
                basketballPlayKind(type, p.str("text").orEmpty(), p.int("scoreValue"), scoring)
            } else {
                playKind(type, p.obj("scoringType")?.str("abbreviation"), scoring)
            },
        )
    }

    /** Sorts a basketball play by its type and points, e.g. "Driving Dunk Shot" or "Bad Pass Turnover". */
    internal fun basketballPlayKind(type: String, text: String, scoreValue: Int?, scoring: Boolean): PlayKind {
        val t = type.lowercase()
        val desc = text.lowercase()
        return when {
            scoring && (scoreValue == 3 || desc.contains("three point") || desc.contains("3-pt")) -> PlayKind.ThreePointer
            scoring && (t.contains("free throw") || scoreValue == 1) -> PlayKind.FreeThrow
            scoring && (t.contains("dunk") || desc.contains("dunk")) -> PlayKind.Dunk
            scoring -> PlayKind.Basket
            t.contains("turnover") || desc.contains("turnover") || desc.contains("steal") -> PlayKind.Turnover
            else -> PlayKind.Other
        }
    }

    /** Sorts a play by ESPN's play type, e.g. "Passing Touchdown" or "Pass Interception Return". */
    internal fun playKind(type: String, scoringAbbreviation: String?, scoring: Boolean): PlayKind {
        val t = type.lowercase()
        return when {
            scoringAbbreviation == "TD" || t.contains("touchdown") -> PlayKind.Touchdown
            scoringAbbreviation == "FG" || (scoring && t.contains("field goal")) -> PlayKind.FieldGoal
            scoringAbbreviation == "SF" || t.contains("safety") -> PlayKind.Safety
            t.contains("interception") || t.contains("fumble recovery (opponent)") || t.contains("blocked") ||
                t.contains("fumble lost") || t.contains("turnover") -> PlayKind.Turnover
            else -> PlayKind.Other
        }
    }

    /**
     * The live situation from a game summary: the header's if it has one, else the
     * summary's own "situation" block, with the last play taken from the drive log
     * when neither names it.
     */
    private fun summarySituation(root: JsonObject?, fromHeader: Situation?, sport: Sport): Situation? {
        val base = fromHeader ?: root?.obj("situation")?.let(::parseSituation)
        val lastPlay = base?.lastPlay ?: lastPlayFromDrives(root?.obj("drives"))
            // Basketball has no drives: its plays are one list, newest last.
            ?: root?.arr("plays")?.lastOrNull()?.obj?.str("text")?.takeIf { sport.isBasketball && it.isNotBlank() }
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

    /** Basketball team stats ESPN labels with box score shorthand, spelled out. */
    private val basketballStatLabels = mapOf(
        "FG" to "Field goals",
        "3PT" to "3-pointers",
        "FT" to "Free throws",
        "Field Goal %" to "Field goal %",
        "Three Point %" to "3-point %",
        "Free Throw %" to "Free throw %",
    )

    /** Basketball team stats that repeat another row. */
    private val basketballSkippedStats = setOf("teamTurnovers", "totalTechnicalFouls")

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

    private fun parseTeamPlayers(sport: Sport, block: JsonObject?): TeamPlayerStats? {
        val teamId = block?.obj("team")?.str("id") ?: return null
        val raw = block.arr("statistics").orEmpty()
        val tables = if (sport.isBasketball) {
            // One table for the whole team, every player who played, starters first.
            listOfNotNull(raw.firstNotNullOfOrNull { parseStatTable(it.obj, keyFallback = BASKETBALL_TABLE) }?.copy(key = BASKETBALL_TABLE))
        } else {
            raw.mapNotNull { parseStatTable(it.obj) }
                .sortedBy { t -> tableOrder.indexOf(t.key).let { if (it < 0) Int.MAX_VALUE else it } }
        }
        return TeamPlayerStats(teamId, tables).takeIf { tables.isNotEmpty() }
    }

    /** Key of the single raw box score table a basketball team has. */
    const val BASKETBALL_TABLE = "players"

    private fun parseStatTable(stat: JsonObject?, keyFallback: String? = null): PlayerStatTable? {
        stat ?: return null
        val key = stat.str("name") ?: keyFallback ?: return null
        val columns = (stat.arr("labels") ?: stat.arr("names")).orEmpty().mapNotNull { it.primitiveString() }
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
                starter = o.bool("starter") ?: false,
            )
        }
        if (players.isEmpty()) return null
        val title = tableTitles[key]
            ?: stat.str("text")?.substringAfter(' ', "")?.ifBlank { null }
            ?: key.replaceFirstChar { it.uppercase() }
        return PlayerStatTable(key = key, title = title, columns = columns, players = players)
    }

    /** "J. Allen" style name: ESPN's short name, or built from first and last name. */
    internal fun shortName(athlete: JsonObject): String? {
        athlete.str("shortName")?.let { return it }
        val first = athlete.str("firstName")?.trim().orEmpty()
        val last = athlete.str("lastName")?.trim().orEmpty()
        if (first.isNotEmpty() && last.isNotEmpty()) return "${first.first()}. $last"
        return athlete.str("displayName") ?: athlete.str("fullName")
    }

    internal fun headshot(athlete: JsonObject?): String? =
        EspnImages.canonical(athlete?.obj("headshot")?.str("href") ?: athlete?.str("headshot"))

    private fun JsonObject?.statMap(): Map<String, String> =
        this?.arr("statistics").orEmpty().mapNotNull { s ->
            val o = s.obj ?: return@mapNotNull null
            val name = o.str("name") ?: return@mapNotNull null
            name to (o.str("displayValue") ?: "-")
        }.toMap()

    internal fun parseEvent(sport: Sport, event: JsonObject?): Game {
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

    internal fun parseCompetitor(c: JsonObject): Competitor {
        val team = c.obj("team")
        val records = c.arr("records") ?: c.arr("record")
        val overall = records.orEmpty().mapNotNull { it.obj }.let { list ->
            (list.firstOrNull { it.str("type") == "total" || it.str("name") == "overall" } ?: list.firstOrNull())
                ?.let { it.str("summary") ?: it.str("displayValue") }
        } ?: c.str("record")
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
            // College polls: ESPN marks unranked teams as 99.
            rank = c.obj("curatedRank")?.int("current")?.takeIf { it in 1..25 },
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

    internal fun parseLeaderCategory(cat: JsonObject?, teamId: String?): Leader? {
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
            athleteId = athlete.str("id"),
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
}
