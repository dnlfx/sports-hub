package com.sportshub.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.sportshub.app.model.Sport
import com.sportshub.app.model.StreamSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

private val Context.sourcesStore by preferencesDataStore(name = "sources")

/** Saves the page URLs the user adds, on the device only. */
class SourcesRepository(private val context: Context) {

    private val key = stringPreferencesKey("sources_json")
    private val json = Json { ignoreUnknownKeys = true }

    val sources: Flow<List<StreamSource>> = context.sourcesStore.data.map { prefs ->
        prefs[key]?.let { runCatching { json.decodeFromString<List<StreamSource>>(it) }.getOrNull() }
            ?: emptyList()
    }

    suspend fun add(url: String, label: String, sport: Sport): StreamSource {
        val source = StreamSource(
            id = UUID.randomUUID().toString(),
            url = url,
            label = label,
            sportId = sport.id,
            addedAtEpochMs = System.currentTimeMillis(),
        )
        update { it + source }
        return source
    }

    suspend fun remove(id: String) = update { list -> list.filterNot { it.id == id } }

    /** Puts back a source that was just removed (for "Undo"), in its original place. */
    suspend fun restore(source: StreamSource) = update { list ->
        if (list.any { it.id == source.id }) list else (list + source).sortedBy { it.addedAtEpochMs }
    }

    private suspend fun update(transform: (List<StreamSource>) -> List<StreamSource>) {
        context.sourcesStore.edit { prefs ->
            val current = prefs[key]
                ?.let { runCatching { json.decodeFromString<List<StreamSource>>(it) }.getOrNull() }
                ?: emptyList()
            prefs[key] = json.encodeToString(transform(current))
        }
    }

    companion object {
        /** Accepts http(s) URLs only. Returns the cleaned URL, or null if it isn't one. */
        fun normalizeUrl(input: String): String? {
            val trimmed = input.trim()
            if (trimmed.isEmpty()) return null
            val withScheme = if ("://" in trimmed) trimmed else "https://$trimmed"
            val uri = runCatching { java.net.URI(withScheme) }.getOrNull() ?: return null
            if (uri.scheme?.lowercase() !in setOf("http", "https")) return null
            if (uri.host.isNullOrBlank() || '.' !in uri.host) return null
            return uri.toString()
        }
    }
}
