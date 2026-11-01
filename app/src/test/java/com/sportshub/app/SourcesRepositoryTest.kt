package com.sportshub.app

import com.sportshub.app.data.SourcesRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SourcesRepositoryTest {
    @Test fun addsHttpsWhenMissing() =
        assertEquals("https://example.com/page", SourcesRepository.normalizeUrl("  example.com/page "))

    @Test fun keepsHttp() =
        assertEquals("http://example.com", SourcesRepository.normalizeUrl("http://example.com"))

    @Test fun rejectsOtherSchemes() = assertNull(SourcesRepository.normalizeUrl("ftp://example.com"))

    @Test fun rejectsBlank() = assertNull(SourcesRepository.normalizeUrl("   "))

    @Test fun keepsPathQueryAndFragment() = assertEquals(
        "https://example.com/category/nfl/?page=2#games",
        SourcesRepository.normalizeUrl("https://example.com/category/nfl/?page=2#games"),
    )

    @Test fun keepsPathWithoutScheme() =
        assertEquals("https://example.com/category/nfl/", SourcesRepository.normalizeUrl("example.com/category/nfl/"))

    @Test fun encodesSpacesInsteadOfDroppingPath() =
        assertEquals("https://example.com/my%20videos/week%201", SourcesRepository.normalizeUrl("https://example.com/my videos/week 1"))

    @Test fun displayShowsFullPath() =
        assertEquals("example.com/category/nfl", SourcesRepository.displayUrl("https://www.example.com/category/nfl/"))

    @Test fun rejectsNoHost() = assertNull(SourcesRepository.normalizeUrl("not a url"))
}
