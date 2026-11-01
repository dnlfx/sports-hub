package com.sportshub.app

import com.sportshub.app.data.scan.HtmlMediaExtractor
import com.sportshub.app.data.scan.VideoKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HtmlMediaExtractorTest {
    private val page = "https://example.com/videos/page"

    @Test fun findsVideoTagsAndSources() {
        val html = """
            <html><head><title>Highlights</title></head><body>
              <video src="/media/clip.mp4" title="Clip one"></video>
              <video><source src="https://cdn.example.com/live/index.m3u8" type="application/x-mpegURL"></video>
            </body></html>
        """
        val found = HtmlMediaExtractor.extract(html, page)
        assertEquals(listOf("https://example.com/media/clip.mp4", "https://cdn.example.com/live/index.m3u8"), found.map { it.url })
        assertEquals(VideoKind.FILE, found[0].kind)
        assertEquals("Clip one", found[0].title)
        assertEquals(VideoKind.HLS, found[1].kind)
        assertEquals("Highlights", found[1].title)
        assertEquals(page, found[1].referer)
    }

    @Test fun findsLinksMetaScriptsAndEmbeds() {
        val html = """
            <html><head>
              <meta property="og:video" content="https://cdn.example.com/a.mpd">
              <script type="application/ld+json">{"@type":"VideoObject","contentUrl":"https:\/\/cdn.example.com\/b.webm?x=1"}</script>
            </head><body>
              <a href="files/c.mov">Download</a>
              <a href="/about">About</a>
              <iframe src="https://player.example.net/embed/42" title="Main player"></iframe>
              <iframe src="https://www.facebook.com/plugins/like"></iframe>
            </body></html>
        """
        val found = HtmlMediaExtractor.extract(html, page).associateBy { it.url }
        assertEquals(VideoKind.DASH, found["https://cdn.example.com/a.mpd"]?.kind)
        assertEquals(VideoKind.FILE, found["https://cdn.example.com/b.webm?x=1"]?.kind)
        assertEquals(VideoKind.FILE, found["https://example.com/videos/files/c.mov"]?.kind)
        assertEquals(VideoKind.EMBED, found["https://player.example.net/embed/42"]?.kind)
        assertEquals(4, found.size)
    }

    @Test fun ignoresNonVideoAndDuplicates() {
        val html = """<a href="/x.mp4">a</a><a href="/x.mp4">b</a><a href="/doc.pdf">c</a><img src="/i.png">"""
        assertEquals(1, HtmlMediaExtractor.extract(html, page).size)
    }

    @Test fun detectsHumanChecks() {
        assertTrue(HtmlMediaExtractor.looksLikeHumanCheck("<div class=\"g-recaptcha\"></div>"))
        assertTrue(HtmlMediaExtractor.looksLikeHumanCheck("<script src=\"/cdn-cgi/challenge-platform/x.js\"></script>"))
        assertFalse(HtmlMediaExtractor.looksLikeHumanCheck("<html><video src=a.mp4></video></html>"))
    }
}
