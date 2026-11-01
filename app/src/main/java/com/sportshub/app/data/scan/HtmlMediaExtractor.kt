package com.sportshub.app.data.scan

import org.jsoup.Jsoup
import java.net.URI

enum class VideoKind { HLS, DASH, FILE, EMBED }

/** A video found on a page. [referer] is the page it was found on, sent along when playing. */
data class FoundVideo(
    val url: String,
    val title: String,
    val kind: VideoKind,
    val referer: String?,
) {
    val isPlayable: Boolean get() = kind != VideoKind.EMBED
}

/**
 * Finds standard, openly published video on a page: <video>/<source> tags,
 * links to video files or HLS/DASH playlists, Open Graph and schema.org video
 * metadata, and embedded player frames. It reads only what the page serves
 * normally and does not run scripts or get around any access controls.
 */
object HtmlMediaExtractor {

    private val mediaUrlInText = Regex(
        """https?://[^\s"'<>\\]+?\.(?:m3u8|mpd|mp4|webm|m4v|mov)(?:\?[^\s"'<>\\]*)?""",
        RegexOption.IGNORE_CASE,
    )

    fun extract(html: String, pageUrl: String): List<FoundVideo> {
        val doc = Jsoup.parse(html, pageUrl)
        val pageTitle = doc.title().trim().ifEmpty { hostOf(pageUrl) }
        val found = LinkedHashMap<String, FoundVideo>()

        fun add(rawUrl: String?, title: String?, forcedKind: VideoKind? = null) {
            val url = rawUrl?.trim()?.takeIf { it.startsWith("http://") || it.startsWith("https://") } ?: return
            val kind = forcedKind ?: kindOf(url) ?: return
            if (url in found) return
            found[url] = FoundVideo(
                url = url,
                title = title?.trim()?.takeIf { it.isNotEmpty() } ?: pageTitle,
                kind = kind,
                referer = pageUrl,
            )
        }

        // <video src> and <video><source src>
        doc.select("video").forEach { video ->
            val title = video.attr("title").ifEmpty { video.attr("aria-label") }
            add(video.absUrl("src"), title, kindOf(video.absUrl("src")) ?: VideoKind.FILE)
            video.select("source").forEach { source ->
                val url = source.absUrl("src")
                val kind = kindFromMime(source.attr("type")) ?: kindOf(url) ?: VideoKind.FILE
                add(url, title, kind)
            }
        }

        // Links that point straight at video files or playlists
        doc.select("a[href]").forEach { a -> add(a.absUrl("href"), a.text()) }

        // Open Graph / Twitter card video metadata
        doc.select("meta[property~=^og:video(:secure_url|:url)?$], meta[name=twitter:player:stream]").forEach { meta ->
            add(meta.absUrl("content").ifEmpty { meta.attr("content") }, doc.select("meta[property=og:title]").attr("content"))
        }

        // schema.org VideoObject in JSON-LD, plus any direct media URLs written in page data
        doc.select("script").forEach { script ->
            mediaUrlInText.findAll(script.data()).forEach { add(it.value.replace("\\/", "/"), null) }
        }

        // Embedded players (iframes). Kept so the scanner can look one level inside them.
        doc.select("iframe[src]").forEach { frame ->
            val src = frame.absUrl("src")
            if (src.startsWith("http") && !isAdOrSocial(src)) {
                add(src, frame.attr("title"), VideoKind.EMBED)
            }
        }

        return found.values.toList()
    }

    /** Text a page shows when it requires a human check. Such pages are reported, not bypassed. */
    fun looksLikeHumanCheck(html: String): Boolean {
        val lower = html.take(20_000).lowercase()
        return listOf("cf-chl", "challenge-platform", "g-recaptcha", "h-captcha", "hcaptcha.com", "turnstile", "verify you are human")
            .any { it in lower }
    }

    fun title(html: String, pageUrl: String): String =
        runCatching { Jsoup.parse(html, pageUrl).title() }.getOrNull()?.trim()?.ifEmpty { null } ?: hostOf(pageUrl)

    internal fun kindOf(url: String): VideoKind? {
        val path = runCatching { URI(url).path }.getOrNull()?.lowercase() ?: url.lowercase()
        return when {
            path.endsWith(".m3u8") -> VideoKind.HLS
            path.endsWith(".mpd") -> VideoKind.DASH
            path.endsWith(".mp4") || path.endsWith(".webm") || path.endsWith(".m4v") || path.endsWith(".mov") -> VideoKind.FILE
            else -> null
        }
    }

    private fun kindFromMime(type: String): VideoKind? = when (type.lowercase().substringBefore(';').trim()) {
        "application/x-mpegurl", "application/vnd.apple.mpegurl" -> VideoKind.HLS
        "application/dash+xml" -> VideoKind.DASH
        "video/mp4", "video/webm" -> VideoKind.FILE
        else -> null
    }

    private fun isAdOrSocial(url: String): Boolean {
        val host = hostOf(url)
        return listOf("doubleclick", "googlesyndication", "facebook.com", "twitter.com", "x.com", "disqus", "googletagmanager")
            .any { it in host }
    }

    fun hostOf(url: String): String = runCatching { URI(url).host }.getOrNull()?.removePrefix("www.") ?: url
}
