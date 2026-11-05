// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary.core.sync

import kotlin.math.min

/** How the last fetches of one online source went: failures in a row, and when it was last tried. */
data class RefreshState(val failures: Int = 0, val lastAttemptMillis: Long? = null)

/**
 * When to fetch online data again. SCARY is the home screen and runs for days, so it cannot fetch only at start: it
 * checks again whenever it comes back on screen, when the network returns, and every so often. A source is fetched
 * when its data is missing or old ([needed]); after a failure (often a TV that starts before its Wi-Fi), it waits a
 * little longer each time, from [FIRST_RETRY_MS] up to [MAX_RETRY_MS], so a dead network is not hammered.
 */
object RefreshPolicy {
    const val FIRST_RETRY_MS = 60_000L
    const val MAX_RETRY_MS = 30 * 60_000L

    /** How long to wait after [failures] failures in a row; none before the first try or after a success. */
    fun retryDelay(failures: Int): Long =
        if (failures <= 0) 0 else min(FIRST_RETRY_MS shl min(failures - 1, MAX_DOUBLINGS), MAX_RETRY_MS)

    /** Whether to fetch now. A clock that went backwards (the TV set its time) does not hold a fetch back. */
    fun due(needed: Boolean, state: RefreshState, nowMillis: Long): Boolean {
        if (!needed) return false
        val last = state.lastAttemptMillis ?: return true
        return nowMillis < last || nowMillis - last >= retryDelay(state.failures)
    }

    /** The state after a fetch: a success clears the failures. */
    fun after(state: RefreshState, success: Boolean, nowMillis: Long): RefreshState =
        RefreshState(if (success) 0 else state.failures + 1, nowMillis)

    private const val MAX_DOUBLINGS = 10
}
