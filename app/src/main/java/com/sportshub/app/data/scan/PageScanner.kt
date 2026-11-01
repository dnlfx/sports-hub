package com.sportshub.app.data.scan

import androidx.compose.runtime.Immutable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

@Immutable
sealed interface ScanResult {
    data class Found(val videos: List<FoundVideo>) : ScanResult
    /** The page asks for a human check or refuses automated access. The app stops there. */
    data class Blocked(val reason: String) : ScanResult
    data class Failed(val message: String) : ScanResult
}

/** Loads a source page and lists the videos on it, looking one level into embedded players. */
class PageScanner(private val http: OkHttpClient) {
    suspend fun scan(pageUrl: String): ScanResult = withContext(Dispatchers.IO) {
        val page = try {
            fetch(pageUrl, referer = null)
        } catch (e: IOException) {
            return@withContext ScanResult.Failed("Couldn't load the page. Check the address and your connection.")
        }
        when {
            page.code == 401 || page.code == 403 || page.code == 429 || page.code == 503 ||
                HtmlMediaExtractor.looksLikeHumanCheck(page.body) ->
                return@withContext ScanResult.Blocked("This page needs a human check or blocks apps, so no videos could be read from it.")
            page.code !in 200..299 ->
                return@withContext ScanResult.Failed("The page returned an error (HTTP ${page.code}).")
        }

        val top = HtmlMediaExtractor.extract(page.body, page.finalUrl)
        val embeds = top.filter { it.kind == VideoKind.EMBED }.take(MAX_EMBEDS)

        // Look inside embedded players for direct video. Embeds we can't resolve stay listed
        // so the user can open them in a browser.
        val inner = coroutineScope {
            embeds.map { embed ->
                async {
                    runCatching {
                        val frame = fetch(embed.url, referer = page.finalUrl)
                        if (frame.code !in 200..299 || HtmlMediaExtractor.looksLikeHumanCheck(frame.body)) {
                            emptyList()
                        } else {
                            HtmlMediaExtractor.extract(frame.body, frame.finalUrl)
                                .filter { it.isPlayable }
                                .map { it.copy(title = embed.title, referer = frame.finalUrl) }
                        }
                    }.getOrDefault(emptyList())
                }
            }.awaitAll()
        }

        val resolvedEmbeds = embeds.filterIndexed { i, _ -> inner[i].isNotEmpty() }.map { it.url }.toSet()
        val videos = (top.filter { it.isPlayable } + inner.flatten() + top.filter { it.kind == VideoKind.EMBED && it.url !in resolvedEmbeds })
            .distinctBy { it.url }
        ScanResult.Found(videos)
    }

    private data class Page(val code: Int, val body: String, val finalUrl: String)

    private fun fetch(url: String, referer: String?): Page {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "text/html,application/xhtml+xml,*/*;q=0.8")
            .apply { if (referer != null) header("Referer", referer) }
            .build()
        http.newCall(request).execute().use { response ->
            val body = response.body?.let { b ->
                // Pages bigger than 3 MB are cut off; video tags are near the top in practice.
                b.source().let { src -> src.request(MAX_BYTES); src.buffer.readUtf8(minOf(src.buffer.size, MAX_BYTES)) }
            }.orEmpty()
            return Page(response.code, body, response.request.url.toString())
        }
    }

    companion object {
        private const val MAX_EMBEDS = 6
        private const val MAX_BYTES = 3L * 1024 * 1024
        const val USER_AGENT = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36 SportsHub/1.0"
    }
}
