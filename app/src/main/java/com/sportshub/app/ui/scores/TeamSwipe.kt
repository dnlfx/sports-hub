package com.sportshub.app.ui.scores

import androidx.compose.foundation.gestures.DraggableState
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.sign

/** How far a finger has to travel sideways to switch teams. */
private val SwipeDistance = 64.dp

/** A quick flick switches teams sooner, once it has moved at least this far. */
private val FlickDistance = 20.dp
private val FlickVelocity = 500.dp // per second

/**
 * A modifier that lets a sideways swipe switch between the two teams in the box score,
 * so you don't have to scroll back up to the team pills. Put it on every box score
 * section so a swipe works anywhere in them.
 *
 * Stat tables whose numbers scroll sideways keep working as before: a swipe on the
 * numbers scrolls the table, and only once the table is at its edge does the rest of
 * the swipe count toward switching teams.
 *
 * [onSwipe] gets +1 for the next team (home, on the right) and -1 for the previous one.
 */
@Composable
internal fun rememberTeamSwipe(onSwipe: (step: Int) -> Unit): Modifier {
    val currentOnSwipe by rememberUpdatedState(onSwipe)
    val density = LocalDensity.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val swipe = remember(density, rtl) {
        with(density) {
            TeamSwipe(
                swipeDistance = SwipeDistance.toPx(),
                flickDistance = FlickDistance.toPx(),
                flickVelocity = FlickVelocity.toPx(),
                rtl = rtl,
                onSwipe = { currentOnSwipe(it) },
            )
        }
    }
    return Modifier
        .nestedScroll(swipe)
        .draggable(
            state = swipe.draggableState,
            orientation = Orientation.Horizontal,
            onDragStopped = { velocity -> swipe.finish(velocity) },
        )
}

internal class TeamSwipe(
    private val swipeDistance: Float,
    private val flickDistance: Float,
    private val flickVelocity: Float,
    private val rtl: Boolean,
    private val onSwipe: (Int) -> Unit,
) : NestedScrollConnection {
    /** How far the current swipe has gone, in pixels; negative is toward the left. */
    private var dragged = 0f

    /** Swipes outside a scrolling table: on names, headers or the team pills. */
    val draggableState = DraggableState { dragged += it }

    /** What a sideways-scrolling table couldn't use because it's already at its edge. */
    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
        if (source != NestedScrollSource.UserInput || available.x == 0f) return Offset.Zero
        dragged += available.x
        return Offset(available.x, 0f)
    }

    /** The finger lifted off a table. Its speed belongs to the table's own fling, so only distance counts. */
    override suspend fun onPreFling(available: Velocity): Velocity {
        finish(0f)
        return Velocity.Zero
    }

    fun finish(velocity: Float) {
        val dx = dragged
        dragged = 0f
        val flicked = abs(velocity) >= flickVelocity && abs(dx) >= flickDistance && sign(velocity) == sign(dx)
        if (!flicked && abs(dx) < swipeDistance) return
        // Swiping left brings in the team on the right, like turning a page.
        val step = if (dx < 0) 1 else -1
        // Right to left, the home team sits on the left.
        onSwipe(if (rtl) -step else step)
    }
}
