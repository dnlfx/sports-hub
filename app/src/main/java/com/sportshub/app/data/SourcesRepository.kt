package com.sportshub.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.sportshub.app.model.Sport
import com.sportshub.app.model.StreamSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
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

    /**
     * The saved sources, read once at launch and kept up to date. Null until the first
     * read finishes, so screens can tell "not loaded yet" apart from "none saved" and
     * don't flash a "No sources yet" message before the list appears.
     */
    val current: StateFlow<List<StreamSource>?> =
        sources.stateIn(CoroutineScope(SupervisorJob() + Dispatchers.IO), SharingStarted.Eagerly, null)

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
        /**
         * Accepts http(s) URLs only and keeps the whole address: path, query and fragment.
         * Returns the cleaned URL, or null if it isn't one.
         */
        fun normalizeUrl(input: String): String? {
            val trimmed = input.trim()
            if (trimmed.isEmpty()) return null
            val withScheme = if ("://" in trimmed) trimmed else "https://$trimmed"
            // java.net.URI keeps the text exactly as pasted. Pasted links with spaces or
            // non-ASCII characters fail there, so fall back to OkHttp, which encodes them.
            val parsed = runCatching { java.net.URI(withScheme) }.getOrNull()
            val (scheme, host, text) = if (parsed != null) {
                Triple(parsed.scheme, parsed.host, parsed.toString())
            } else {
                val url = withScheme.toHttpUrlOrNull() ?: return null
                Triple(url.scheme, url.host, url.toString())
            }
            if (scheme?.lowercase() !in setOf("http", "https")) return null
            if (host.isNullOrBlank() || '.' !in host) return null
            return text
        }

        /** The full address for display, without the scheme or "www.", e.g. "example.com/category/nfl". */
        fun displayUrl(url: String): String =
            url.substringAfter("://").removePrefix("www.").trimEnd('/')
    }
}
