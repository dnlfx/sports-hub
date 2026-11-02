package com.sportshub.app.data

import android.content.Context
import com.sportshub.app.data.espn.EspnJson
import com.sportshub.app.data.espn.arr
import com.sportshub.app.data.espn.obj
import com.sportshub.app.data.espn.str
import com.sportshub.app.model.Sport
import com.sportshub.app.model.Team
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * The teams the user follows, per sport, in the order they picked them. Saved on the
 * device only. The list is read once when the app starts, so screens have it on their
 * first frame and never flash an empty "Your teams" before it appears.
 */
class FavoritesRepository(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences("favorites", Context.MODE_PRIVATE)

    private val _teams = MutableStateFlow(decode(prefs.getString(KEY_TEAMS, null)))

    /** Sport id to followed teams. */
    val teams: StateFlow<Map<String, List<Team>>> = _teams.asStateFlow()

    private val _promptDismissed = MutableStateFlow(prefs.getBoolean(KEY_PROMPT_DISMISSED, false))

    /** Whether the user closed the "Follow your teams" card on the Scores tab. */
    val promptDismissed: StateFlow<Boolean> = _promptDismissed.asStateFlow()

    fun teams(sport: Sport): List<Team> = _teams.value[sport.id].orEmpty()

    fun isFavorite(sport: Sport, teamId: String): Boolean = teams(sport).any { it.id == teamId }

    /** Follows [team], or stops following it if already followed. */
    fun toggle(sport: Sport, team: Team) {
        _teams.update { all ->
            val list = all[sport.id].orEmpty()
            val next = if (list.any { it.id == team.id }) list.filterNot { it.id == team.id } else list + team.forStorage()
            all + (sport.id to next)
        }
        save()
    }

    fun dismissPrompt() {
        _promptDismissed.value = true
        prefs.edit().putBoolean(KEY_PROMPT_DISMISSED, true).apply()
    }

    private fun save() {
        prefs.edit().putString(KEY_TEAMS, encode(_teams.value)).apply()
    }

    /** The record changes every week, so it isn't saved; screens fill it in from the latest scores. */
    private fun Team.forStorage(): Team = copy(record = null)

    companion object {
        private const val KEY_TEAMS = "teams_json"
        private const val KEY_PROMPT_DISMISSED = "prompt_dismissed"

        internal fun encode(all: Map<String, List<Team>>): String = JsonObject(
            all.mapValues { (_, teams) ->
                JsonArray(
                    teams.map { t ->
                        JsonObject(
                            buildMap {
                                put("id", JsonPrimitive(t.id))
                                put("name", JsonPrimitive(t.name))
                                put("abbreviation", JsonPrimitive(t.abbreviation))
                                t.logoUrl?.let { put("logoUrl", JsonPrimitive(it)) }
                                t.primaryColor?.let { put("primaryColor", JsonPrimitive(it)) }
                            },
                        )
                    },
                )
            },
        ).toString()

        internal fun decode(raw: String?): Map<String, List<Team>> {
            raw ?: return emptyMap()
            val root = runCatching { EspnJson.parseToJsonElement(raw).obj }.getOrNull() ?: return emptyMap()
            return root.keys.associateWith { sport ->
                root.arr(sport).orEmpty().mapNotNull { e ->
                    val o = e.obj ?: return@mapNotNull null
                    Team(
                        id = o.str("id") ?: return@mapNotNull null,
                        name = o.str("name") ?: return@mapNotNull null,
                        abbreviation = o.str("abbreviation") ?: "",
                        logoUrl = o.str("logoUrl"),
                        primaryColor = o.str("primaryColor"),
                    )
                }.distinctBy { it.id }
            }
        }
    }
}
