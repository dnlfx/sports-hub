package com.sportshub.app.data.espn

import com.sportshub.app.model.BioItem
import com.sportshub.app.model.CareerRow
import com.sportshub.app.model.CareerTable
import com.sportshub.app.model.PlayerProfile
import com.sportshub.app.model.RankedStat
import com.sportshub.app.model.Team
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Reads ESPN's athlete endpoints: a player's bio and their career stats. */
object EspnPlayerParser {

    private val dobOut = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US)

    fun parseProfile(body: String): PlayerProfile? {
        val root = EspnJson.parseToJsonElement(body).obj ?: return null
        val a = root.obj("athlete") ?: root
        // ESPN has moved these fields between the athlete object and the top level; look in both.
        fun field(key: String) = a.str(key) ?: root.str(key)
        fun objField(key: String) = a.obj(key) ?: root.obj(key)

        val id = a.str("id") ?: return null
        val name = a.str("displayName") ?: a.str("fullName") ?: return null
        val position = objField("position")
        val teamJson = objField("team")
        val team = teamJson?.let { t ->
            val teamId = t.str("id") ?: return@let null
            Team(
                id = teamId,
                name = t.str("shortDisplayName") ?: t.str("name") ?: t.str("displayName") ?: "",
                abbreviation = t.str("abbreviation") ?: "",
                logoUrl = EspnImages.canonical(t.arr("logos")?.firstOrNull()?.obj?.str("href") ?: t.str("logo")),
                primaryColor = t.str("color")?.takeIf { it.length == 6 }?.let { "#$it" },
            )
        }
        val college = (a.obj("college")?.str("name") ?: root.obj("college")?.str("name"))?.takeIf { it.isNotBlank() }
        val born = listOfNotNull(field("displayDOB")?.let(::formatDob), field("displayBirthPlace")).joinToString(" · ")
        val bio = listOfNotNull(
            field("displayHeight")?.let { BioItem("Height", it) },
            field("displayWeight")?.let { BioItem("Weight", it) },
            (a.int("age") ?: root.int("age"))?.let { BioItem("Age", "$it") },
            born.takeIf { it.isNotBlank() }?.let { BioItem("Born", it) },
            college?.let { BioItem("College", it) },
            field("displayDraft")?.let { BioItem("Draft", it) },
            field("displayExperience")?.let { BioItem("Experience", it) },
            objField("status")?.str("name")?.takeIf { !it.equals("Active", ignoreCase = true) }?.let { BioItem("Status", it) },
        )
        val summary = objField("statsSummary")
        return PlayerProfile(
            id = id,
            name = name,
            headshotUrl = EspnImages.canonical(objField("headshot")?.str("href")),
            jersey = a.str("jersey") ?: root.str("jersey"),
            position = position?.str("abbreviation"),
            positionName = position?.str("displayName") ?: position?.str("name"),
            team = team,
            bio = bio,
            summaryTitle = summary?.str("displayName"),
            summary = summary?.arr("statistics").orEmpty().mapNotNull { s ->
                val o = s.obj ?: return@mapNotNull null
                RankedStat(
                    label = o.str("abbreviation") ?: o.str("shortDisplayName") ?: return@mapNotNull null,
                    value = o.str("displayValue") ?: return@mapNotNull null,
                    rank = o.str("rankDisplayValue")?.takeIf { it.isNotBlank() },
                )
            },
        )
    }

    /** "21/5/1996" as ESPN sends it, shown as "May 21, 1996". Anything else is shown as is. */
    internal fun formatDob(raw: String): String {
        val parts = raw.split('/').mapNotNull { it.trim().toIntOrNull() }
        if (parts.size != 3) return raw
        val (d, m, y) = parts
        return runCatching { LocalDate.of(y, m, d).format(dobOut) }.getOrDefault(raw)
    }

    /** Categories left out, to match the game screens. */
    private val skipped = setOf("punting", "returning", "kickReturns", "puntReturns")

    fun parseCareer(body: String): List<CareerTable> {
        val root = EspnJson.parseToJsonElement(body).obj ?: return emptyList()
        val teamAbbr = teamAbbreviations(root)
        return root.arr("categories").orEmpty().mapNotNull { c ->
            val cat = c.obj ?: return@mapNotNull null
            val key = cat.str("name") ?: return@mapNotNull null
            if (key in skipped) return@mapNotNull null
            val columns = cat.arr("labels").orEmpty().mapNotNull { it.primitiveString()?.trim() }
            if (columns.isEmpty()) return@mapNotNull null
            fun row(values: JsonArray?) = List(columns.size) { i -> values?.getOrNull(i)?.primitiveString() ?: "-" }
            val seasons = cat.arr("statistics").orEmpty().mapNotNull { s ->
                val o = s.obj ?: return@mapNotNull null
                // "2024-25" for seasons that span two years (basketball), else the year.
                val year = o.obj("season")?.let { season ->
                    season.str("displayName")?.takeIf { Regex("""^\d{4}-\d{2}$""").matches(it.trim()) }?.trim()
                        ?: season.str("year") ?: season.str("displayName")
                } ?: return@mapNotNull null
                val team = o.str("teamId")?.let { teamAbbr[it] }
                CareerRow(year, team, row(o.arr("stats")))
            }.reversed()
            if (seasons.isEmpty()) return@mapNotNull null
            val totals = cat.arr("totals")?.let { CareerRow("Career", null, row(it)) }
            CareerTable(
                key = key,
                title = cat.str("displayName") ?: key.replaceFirstChar { it.uppercase() },
                columns = columns,
                seasons = seasons,
                totals = totals,
            )
        }
    }

    /** Team id to abbreviation, from the "teams" block (an object keyed by id, or a list). */
    private fun teamAbbreviations(root: JsonObject): Map<String, String> {
        val teams = root["teams"]
        val entries: List<JsonObject> = when (teams) {
            is JsonObject -> teams.values.mapNotNull { it.obj }
            is JsonArray -> teams.mapNotNull { it.obj }
            else -> emptyList()
        }
        return entries.mapNotNull { t ->
            val id = t.str("id") ?: return@mapNotNull null
            val abbr = t.str("abbreviation") ?: t.str("displayName") ?: return@mapNotNull null
            id to abbr
        }.toMap()
    }
}
