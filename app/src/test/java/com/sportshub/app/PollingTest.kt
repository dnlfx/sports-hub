package com.sportshub.app

import com.sportshub.app.ui.scores.MIN_POLL_GAP_MS
import com.sportshub.app.ui.scores.nextPollDelayMs
import org.junit.Assert.assertEquals
import org.junit.Test

class PollingTest {
    @Test fun waitsOutTheRestOfTheInterval() {
        assertEquals(10_000L, nextPollDelayMs(intervalMs = 15_000L, sinceLastMs = 5_000L))
    }

    @Test fun neverWaitsLongerThanTheInterval() {
        assertEquals(15_000L, nextPollDelayMs(intervalMs = 15_000L, sinceLastMs = -60_000L))
    }

    // Regression: at launch nothing has loaded yet, so "time since last load" is huge.
    // The wait used to come out as 0 ms, and the refresh loop spun on the main thread
    // while the first load was still running, freezing the app ("isn't responding").
    @Test fun neverReturnsZeroEvenWhenNothingHasLoadedYet() {
        assertEquals(MIN_POLL_GAP_MS, nextPollDelayMs(intervalMs = 15_000L, sinceLastMs = System.currentTimeMillis()))
    }
}
