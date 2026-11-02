package com.sportshub.app

import com.sportshub.app.data.espn.EspnPlayerParser
import org.junit.Assert.assertEquals
import org.junit.Test

class EspnPlayerParserTest {
    private fun resource(name: String) =
        requireNotNull(javaClass.classLoader?.getResource(name)) { "missing $name" }.readText()

    @Test fun parsesBio() {
        val p = EspnPlayerParser.parseProfile(resource("espn_athlete.json"))!!
        assertEquals("Josh Allen", p.name)
        assertEquals("QB", p.position)
        assertEquals("BUF", p.team?.abbreviation)
        assertEquals(
            listOf("Height", "Weight", "Age", "Born", "College", "Draft", "Experience"),
            p.bio.map { it.label },
        )
        assertEquals("May 21, 1996 · Firebaugh, CA", p.bio.first { it.label == "Born" }.value)
        assertEquals("15th", p.summary.first().rank)
    }

    @Test fun parsesCareerNewestFirst() {
        val career = EspnPlayerParser.parseCareer(resource("espn_athlete_stats.json"))
        assertEquals(listOf("passing", "kicking"), career.map { it.key })
        val passing = career.first()
        assertEquals(listOf("2026", "2025"), passing.seasons.map { it.label })
        assertEquals("BUF", passing.seasons.first().team)
        assertEquals(listOf("23", "433", "5,172"), passing.totals?.values)
        // Stray tabs in labels are trimmed.
        assertEquals(listOf("1-19"), career[1].columns)
    }
}
