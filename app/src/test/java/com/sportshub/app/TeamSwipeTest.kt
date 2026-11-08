package com.sportshub.app

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity
import com.sportshub.app.ui.scores.TeamSwipe
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class TeamSwipeTest {
    private val steps = mutableListOf<Int>()
    private fun swipe(rtl: Boolean = false) =
        TeamSwipe(swipeDistance = 100f, flickDistance = 30f, flickVelocity = 500f, rtl = rtl, onSwipe = { steps += it })

    @Test fun swipingLeftGoesToTheNextTeam() {
        val s = swipe()
        s.draggableState.dispatchRawDelta(-120f)
        s.finish(0f)
        assertEquals(listOf(1), steps)
    }

    @Test fun swipingRightGoesBack() {
        val s = swipe()
        s.draggableState.dispatchRawDelta(120f)
        s.finish(0f)
        assertEquals(listOf(-1), steps)
    }

    @Test fun aShortSlowDragDoesNothing() {
        val s = swipe()
        s.draggableState.dispatchRawDelta(-60f)
        s.finish(100f)
        assertEquals(emptyList<Int>(), steps)
    }

    @Test fun aShortFlickSwitches() {
        val s = swipe()
        s.draggableState.dispatchRawDelta(-40f)
        s.finish(-900f)
        assertEquals(listOf(1), steps)
    }

    @Test fun eachSwipeStartsFresh() {
        val s = swipe()
        s.draggableState.dispatchRawDelta(-60f)
        s.finish(0f)
        s.draggableState.dispatchRawDelta(-60f)
        s.finish(0f)
        assertEquals(emptyList<Int>(), steps)
    }

    @Test fun onlyWhatATableCantScrollCounts() = runBlocking {
        val s = swipe()
        // The table scrolled 80px and hit its edge; 30px was left over.
        val taken = s.onPostScroll(Offset(-80f, 0f), Offset(-30f, 0f), NestedScrollSource.UserInput)
        assertEquals(Offset(-30f, 0f), taken)
        s.onPreFling(Velocity(-2000f, 0f))
        assertEquals(emptyList<Int>(), steps)

        // Already at its edge: the whole swipe goes to switching teams.
        s.onPostScroll(Offset.Zero, Offset(-120f, 0f), NestedScrollSource.UserInput)
        s.onPreFling(Velocity.Zero)
        assertEquals(listOf(1), steps)
    }

    @Test fun rightToLeftFlips() {
        val s = swipe(rtl = true)
        s.draggableState.dispatchRawDelta(-120f)
        s.finish(0f)
        assertEquals(listOf(-1), steps)
    }
}
