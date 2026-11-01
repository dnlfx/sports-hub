package com.sportshub.app.ui.scores

/** Shortest gap between two refreshes, so a refresh loop can never spin without pausing. */
const val MIN_POLL_GAP_MS = 1_000L

/**
 * How long a refresh loop waits before its next refresh: what's left of [intervalMs]
 * since the last load, but never less than [MIN_POLL_GAP_MS].
 */
fun nextPollDelayMs(intervalMs: Long, sinceLastMs: Long): Long =
    (intervalMs - sinceLastMs).coerceIn(MIN_POLL_GAP_MS, maxOf(intervalMs, MIN_POLL_GAP_MS))
