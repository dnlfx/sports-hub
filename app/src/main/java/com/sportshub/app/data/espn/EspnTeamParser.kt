package com.sportshub.app.data.espn

import com.sportshub.app.model.DepthChart
import com.sportshub.app.model.DepthPlayer
import com.sportshub.app.model.DepthPosition
import com.sportshub.app.model.RecordSplit
import com.sportshub.app.model.RosterGroup
import com.sportshub.app.model.RosterPlayer
import com.sportshub.app.model.ScheduleEntry
import com.sportshub.app.model.SeasonStat
import com.sportshub.app.model.SeasonStatGroup
import com.sportshub.app.model.Sport
import com.sportshub.app.model.Team
import com.sportshub.app.model.TeamListing
import com.sportshub.app.model.TeamProfile
import com.sportshub.app.model.TeamSchedule
import com.sportshub.app.model.TeamSeasonStats
import kotlinx.serialization.json.JsonObject
import java.util.Locale

/**
 * Reads ESPN's team endpoints: the league's teams, one team's profile, schedule,
 * season stats, roster and depth charts. Lenient like [EspnParser]: anything missing
 * becomes null or empty rather than failing the page.
 */
object EspnTeamParser {

    fun parseTeams(body: String): List<TeamListing> {
        val root = EspnJson.parseToJsonElement(body).obj
        val teams = root?.arr("sports")?.firstOrNull()?.obj?.arr("leagues")?.firstOrNull()?.obj?.arr("teams").orEmpty()
        return teams.mapNotNull { entry ->
            val t = entry.obj?.obj("team") ?: entry.obj ?: return@mapNotNull null
            if (t.bool("isActive") == false) return@mapNotNull null
            val team = teamOf(t) ?: return@mapNotNull null
            TeamListing(team, t.str("displayName") ?: team.name)
        }
    }

    fun parseTeamProfile(body: String): TeamProfile? {
        val t = EspnJson.parseToJsonElement(body).obj?.obj("team") ?: return null
        val items = t.obj("record")?.arr("items").orEmpty().mapNotNull { it.obj }
        val total = items.firstOrNull { it.str("type") == "total" } ?: items.firstOrNull()
        val stats = total?.arr("stats").orEmpty().mapNotNull { it.obj }
            .mapNotNull { s -> s.str("name")?.let { it to s.double("value") } }
            .toMap()
        val team = teamOf(t)?.copy(record = total?.str("summary")) ?: return null
        val splits = items.mapNotNull { item ->
            val label = when (item.str("type")) {
                "home" -> "Home"
                "road", "away" -> "Away"
                else -> return@mapNotNull null
            }
            item.str("summary")?.let { RecordSplit(label, it) }
        }
        return TeamProfile(
            team = team,
            displayName = t.str("displayName") ?: team.name,
            standing = t.str("standingSummary"),
            splits = splits,
            pointsForPerGame = stats["avgPointsFor"],
            pointsAgainstPerGame = stats["avgPointsAgainst"],
            pointDifferential = (stats["pointDifferential"] ?: stats["differential"])?.toInt(),
            streak = stats["streak"]?.toInt(),
        )
    }

    fun parseSchedule(sport: Sport, body: String): TeamSchedule {
        val root = EspnJson.parseToJsonElement(body).obj
        val entries = root?.arr("events").orEmpty().mapNotNull { e ->
            val event = e.obj ?: return@mapNotNull null
            val game = runCatching { EspnParser.parseEvent(sport, event) }.getOrNull() ?: return@mapNotNull null
            val seasonType = event.obj("seasonType")?.int("type") ?: event.obj("season")?.int("type") ?: 2
            val week = event.obj("week")?.int("number")
            val label = event.obj("week")?.str("text") ?: week?.let { "Week $it" } ?: ""
            ScheduleEntry(game = game, week = week.takeIf { seasonType == 2 }, label = label, seasonType = seasonType)
        }
        val season = root?.obj("requestedSeason")?.int("year") ?: root?.obj("season")?.int("year")
        return TeamSchedule(season, entries)
    }

    private data class Pick(val category: String, val stat: String, val label: String)

    private val groupSpecs: List<Triple<String, String, List<Pick>>> = listOf(
        Triple(
            "offense", "Offense",
            listOf(
                Pick("scoring", "totalPoints", "Points"),
                Pick("passing", "totalYards", "Total yards"),
                Pick("passing", "netPassingYards", "Passing yards"),
                Pick("rushing", "rushingYards", "Rushing yards"),
                Pick("scoring", "totalTouchdowns", "Touchdowns"),
                Pick("passing", "passingTouchdowns", "Passing TDs"),
                Pick("rushing", "rushingTouchdowns", "Rushing TDs"),
                Pick("passing", "completionPct", "Completion %"),
                Pick("passing", "yardsPerPassAttempt", "Yards per pass"),
                Pick("rushing", "yardsPerRushAttempt", "Yards per carry"),
                Pick("passing", "interceptions", "Interceptions thrown"),
                Pick("passing", "sacks", "Sacks taken"),
                Pick("general", "fumblesLost", "Fumbles lost"),
            ),
        ),
        Triple(
            "efficiency", "Downs and discipline",
            listOf(
                Pick("miscellaneous", "firstDowns", "First downs"),
                Pick("miscellaneous", "thirdDownConvPct", "3rd down %"),
                Pick("miscellaneous", "fourthDownConvPct", "4th down %"),
                Pick("miscellaneous", "redzoneScoringPct", "Red zone scoring %"),
                Pick("miscellaneous", "totalTakeaways", "Takeaways"),
                Pick("miscellaneous", "totalGiveaways", "Giveaways"),
                Pick("miscellaneous", "turnOverDifferential", "Turnover differential"),
                Pick("miscellaneous", "totalPenalties", "Penalties"),
                Pick("miscellaneous", "totalPenaltyYards", "Penalty yards"),
            ),
        ),
        Triple(
            "defense", "Defense",
            listOf(
                Pick("defensive", "totalTackles", "Tackles"),
                Pick("defensive", "sacks", "Sacks"),
                Pick("defensive", "tacklesForLoss", "Tackles for loss"),
                Pick("defensive", "QBHits", "QB hits"),
                Pick("defensive", "passesDefended", "Passes defended"),
                Pick("defensiveInterceptions", "interceptions", "Interceptions"),
                Pick("general", "fumblesForced", "Forced fumbles"),
                Pick("general", "fumblesRecovered", "Fumbles recovered"),
                Pick("defensive", "defensiveTouchdowns", "Defensive TDs"),
            ),
        ),
        Triple(
            "kicking", "Kicking",
            listOf(
                Pick("kicking", "fieldGoalsMade", "Field goals made"),
                Pick("kicking", "fieldGoalAttempts", "Field goal attempts"),
                Pick("kicking", "fieldGoalPct", "Field goal %"),
                Pick("kicking", "longFieldGoalMade", "Longest field goal"),
                Pick("kicking", "extraPointsMade", "Extra points made"),
                Pick("kicking", "extraPointPct", "Extra point %"),
                Pick("kicking", "totalKickingPoints", "Kicking points"),
            ),
        ),
    )

    /** Each group's main ESPN category, used when none of the picked stats are found. */
    private val fallbackCategory = mapOf(
        "offense" to "passing",
        "efficiency" to "miscellaneous",
        "defense" to "defensive",
        "kicking" to "kicking",
    )

    fun parseTeamStatistics(body: String): TeamSeasonStats {
        val root = EspnJson.parseToJsonElement(body).obj
        val results = root?.obj("results") ?: root
        val categories = (results?.obj("stats")?.arr("categories") ?: results?.arr("categories")).orEmpty()
            .mapNotNull { it.obj }
        val byCategory: Map<String, Map<String, JsonObject>> = categories.mapNotNull { c ->
            val name = c.str("name") ?: return@mapNotNull null
            val stats = LinkedHashMap<String, JsonObject>()
            c.arr("stats").orEmpty().mapNotNull { it.obj }.forEach { s -> s.str("name")?.let { stats.putIfAbsent(it, s) } }
            name to stats
        }.toMap()

        val groups = groupSpecs.mapNotNull { (key, title, picks) ->
            var stats = picks.mapNotNull { p -> byCategory[p.category]?.get(p.stat)?.let { seasonStat(p.stat, p.label, it) } }
            if (stats.size < 2) {
                stats = byCategory[fallbackCategory[key]].orEmpty().entries.take(6).mapNotNull { (name, s) ->
                    seasonStat(name, s.str("displayName") ?: name, s)
                }
            }
            SeasonStatGroup(key, title, stats).takeIf { stats.isNotEmpty() }
        }
        val season = results?.obj("season")?.int("year") ?: root?.obj("season")?.int("year")
        return TeamSeasonStats(season, groups)
    }

    private val notPerGame = listOf("pct", "per", "avg", "long", "rating", "differential", "rate")

    private fun seasonStat(key: String, label: String, s: JsonObject): SeasonStat? {
        val value = s.str("displayValue") ?: return null
        val counting = notPerGame.none { key.lowercase(Locale.US).contains(it) }
        val perGame = s.double("perGameValue")?.takeIf { counting }?.let { formatPerGame(it) }
        return SeasonStat(key, label, value, perGame)
    }

    private fun formatPerGame(v: Double): String =
        if (v == Math.floor(v)) v.toLong().toString() else String.format(Locale.US, "%.1f", v)

    private val rosterTitles = mapOf("offense" to "Offense", "defense" to "Defense", "specialTeam" to "Special teams")

    fun parseRoster(body: String): List<RosterGroup> {
        val athletes = EspnJson.parseToJsonElement(body).obj?.arr("athletes").orEmpty().mapNotNull { it.obj }
        // Usually grouped by side of the ball; some sports list players directly.
        if (athletes.isNotEmpty() && athletes.none { it.arr("items") != null }) {
            val players = athletes.mapNotNull(::rosterPlayer)
            return if (players.isEmpty()) emptyList() else listOf(RosterGroup("all", "Roster", players))
        }
        return athletes.mapNotNull { group ->
            val key = group.str("position") ?: "players"
            val players = group.arr("items").orEmpty().mapNotNull { rosterPlayer(it.obj) }
            val title = rosterTitles[key] ?: key.replaceFirstChar { it.uppercase() }
            RosterGroup(key, title, players).takeIf { players.isNotEmpty() }
        }
    }

    private fun rosterPlayer(p: JsonObject?): RosterPlayer? {
        p ?: return null
        val id = p.str("id") ?: return null
        val position = p.obj("position")
        return RosterPlayer(
            id = id,
            name = p.str("displayName") ?: p.str("fullName") ?: return null,
            jersey = p.str("jersey"),
            position = position?.str("abbreviation"),
            positionName = position?.str("displayName") ?: position?.str("name"),
            headshotUrl = EspnParser.headshot(p),
            age = p.int("age"),
            height = p.str("displayHeight"),
            weight = p.str("displayWeight"),
            experience = p.obj("experience")?.int("years"),
            college = p.obj("college")?.let { it.str("shortName") ?: it.str("name") },
            injury = injuryOf(p),
        )
    }

    private fun injuryOf(p: JsonObject): String? =
        p.arr("injuries")?.firstOrNull()?.obj?.let { it.str("status") ?: it.obj("type")?.str("description") }
            ?.takeIf { it.isNotBlank() && !it.equals("active", ignoreCase = true) }

    fun parseDepthCharts(body: String): List<DepthChart> {
        val root = EspnJson.parseToJsonElement(body).obj
        val charts = (root?.arr("depthchart") ?: root?.arr("items")).orEmpty().mapNotNull { it.obj }
        return charts.mapNotNull { chart ->
            val positions = chart.obj("positions")?.entries.orEmpty().mapNotNull { (key, value) ->
                val o = value.obj ?: return@mapNotNull null
                val pos = o.obj("position")
                val players = o.arr("athletes").orEmpty().mapNotNull { a ->
                    val ao = a.obj ?: return@mapNotNull null
                    DepthPlayer(
                        id = ao.str("id") ?: return@mapNotNull null,
                        name = ao.str("displayName") ?: ao.str("shortName") ?: return@mapNotNull null,
                        injury = injuryOf(ao),
                    )
                }
                DepthPosition(
                    key = key,
                    abbreviation = (pos?.str("abbreviation") ?: key).uppercase(Locale.US),
                    name = (pos?.str("displayName") ?: pos?.str("name") ?: key).trim().replace(Regex("\\s+"), " "),
                    players = players,
                ).takeIf { players.isNotEmpty() }
            }
            DepthChart(chart.str("name")?.trim() ?: "Depth chart", positions).takeIf { positions.isNotEmpty() }
        }.sortedBy { chartOrder(it) }
    }

    /** Offense first, then defense, then special teams. */
    private fun chartOrder(chart: DepthChart): Int {
        val keys = chart.positions.map { it.key.lowercase(Locale.US) }
        return when {
            "qb" in keys -> 0
            "pk" in keys || "k" in keys || "p" in keys || chart.name.contains("special", ignoreCase = true) -> 2
            else -> 1
        }
    }

    private fun teamOf(t: JsonObject): Team? {
        val id = t.str("id") ?: return null
        return Team(
            id = id,
            name = t.str("shortDisplayName") ?: t.str("name") ?: t.str("displayName") ?: return null,
            abbreviation = t.str("abbreviation") ?: "",
            logoUrl = EspnImages.canonical(t.arr("logos")?.firstOrNull()?.obj?.str("href") ?: t.str("logo")),
            primaryColor = t.str("color")?.takeIf { it.length == 6 }?.let { "#$it" },
        )
    }
}
