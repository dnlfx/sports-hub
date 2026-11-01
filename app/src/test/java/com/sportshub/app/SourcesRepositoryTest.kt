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

    @Test fun rejectsNoHost() = assertNull(SourcesRepository.normalizeUrl("not a url"))
}
