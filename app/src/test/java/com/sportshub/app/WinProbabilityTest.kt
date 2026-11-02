package com.sportshub.app

import com.sportshub.app.data.espn.EspnParser
import com.sportshub.app.model.PlayKind
import com.sportshub.app.model.Sport
import com.sportshub.app.model.WinProbPoint
import com.sportshub.app.ui.scores.keyPlays
import com.sportshub.app.ui.scores.periodLabel
import com.sportshub.app.ui.scores.periodStarts
import com.sportshub.app.ui.scores.swingAt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WinProbabilityTest {
    private fun resource(name: String) =
        requireNotNull(javaClass.classLoader?.getResource(name)) { "missing $name" }.readText()

    private val wp = requireNotNull(EspnParser.parseSummary(Sport.NFL, resource("espn_winprob.json")).winProbability)

    @Test fun readsOnePointPerPlayWithItsDetails() {
        assertEquals(6, wp.points.size)
        val td = wp.points[1]
        assertEquals(0.45f, td.homeWinPct, 0.0001f)
        assertEquals("p2", td.playId)
        assertEquals(1, td.period)
        assertEquals("12:30", td.clock)
        assertEquals(7, td.awayScore)
        assertEquals(0, td.homeScore)
        assertEquals("15", td.teamId)
        assertTrue(td.isScoring)
        assertEquals(PlayKind.Touchdown, td.kind)
        assertTrue(td.text!!.startsWith("T.Tagovailoa"))
    }

    @Test fun sortsPlaysByKind() {
        assertEquals(PlayKind.Turnover, wp.points[2].kind)
        assertEquals(PlayKind.Other, wp.points[3].kind)
        // Only in the scoring plays list, not the drive log.
        val fg = wp.points[4]
        assertEquals(PlayKind.FieldGoal, fg.kind)
        assertTrue(fg.isScoring)
        assertEquals("2", fg.teamId)
    }

    @Test fun keepsPointsWithoutAPlay() {
        val last = wp.points.last()
        assertNull(last.playId)
        assertNull(last.text)
        assertEquals(0.48f, last.homeWinPct, 0.0001f)
    }

    @Test fun readsPregamePick() {
        assertEquals(0.614f, wp.pregameHomePct!!, 0.0001f)
    }

    @Test fun noWinProbabilityWhenEspnHasNone() {
        assertNull(EspnParser.parseSummary(Sport.NFL, resource("espn_summary.json")).winProbability)
    }

    @Test fun keyPlaysAreBigSwingsAndScoresInGameOrder() {
        // Swings: p2 -15 (TD), p3 -7 (turnover), p4 +2, p5 +7 (FG), last +1.
        assertEquals(listOf(1, 2, 4), keyPlays(wp.points))
        assertEquals(-0.15f, swingAt(wp.points, 1), 0.0001f)
        assertEquals(0f, swingAt(wp.points, 0), 0f)
        assertEquals(listOf(1), keyPlays(wp.points, max = 1))
    }

    @Test fun smallSwingsAreNotMarked() {
        val flat = List(10) { WinProbPoint(homeWinPct = 0.5f + it * 0.01f) }
        assertEquals(emptyList<Int>(), keyPlays(flat))
    }

    @Test fun periodStartsAndLabels() {
        assertEquals(listOf(1 to 0, 2 to 3), periodStarts(wp.points))
        assertEquals("Q3", periodLabel(3))
        assertEquals("OT", periodLabel(5))
        assertEquals("2OT", periodLabel(6))
    }
}
