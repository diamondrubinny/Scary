// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary.core.stability

/**
 * Keeps the focus from running by itself after a stall. While the main thread is busy, the remote's arrow presses (and
 * a held key's repeats) wait in Android's queue; when it frees up they all arrive at once and the focus jumps several
 * rows. A press that arrives late ([STALE_MS] after it was made) is delivered only when it is the first of such a
 * backlog; the presses behind it, and every late repeat, are dropped, and so is the release of each dropped press.
 * Presses that arrive on time are never touched. One per window; not thread-safe (the main thread only).
 */
class KeyBacklog {
    private var lastDeliveredAt: Long? = null
    private val dropped = HashSet<Int>()

    /**
     * Whether to deliver an arrow key event. [madeAt] is when the remote made it, [now] when it arrived (both on the
     * same clock, uptime milliseconds); [repeatCount] is above zero for a held key's repeats, and [pressedAt] is when
     * the key went down. Some remotes stamp every repeat with the time of the press: such repeats cannot be judged and
     * are always delivered, so holding a key keeps scrolling.
     */
    fun deliver(keyCode: Int, down: Boolean, repeatCount: Int, madeAt: Long, now: Long, pressedAt: Long = madeAt): Boolean {
        if (!down) return !dropped.remove(keyCode)
        if (repeatCount > 0 && madeAt <= pressedAt) return true
        val late = now - madeAt > STALE_MS
        val keep = when {
            !late -> true
            repeatCount > 0 -> false
            else -> lastDeliveredAt.let { it == null || now - it !in 0 until BURST_MS }
        }
        when {
            keep -> {
                lastDeliveredAt = now
                dropped.remove(keyCode)
            }
            // A dropped repeat leaves the press itself delivered, so its release still goes through.
            repeatCount == 0 -> dropped.add(keyCode)
        }
        return keep
    }

    companion object {
        /** A press older than this when it arrives waited behind a stall. */
        const val STALE_MS = 300L

        /** Late presses arriving this close after a delivered one are the rest of the same backlog. */
        const val BURST_MS = 250L
    }
}
