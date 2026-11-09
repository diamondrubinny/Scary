// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary.data

import android.content.Context
import androidx.core.content.edit

/**
 * Where the viewer stopped in each file of their Real-Debrid account, so the player picks up from there. Kept on this
 * TV only (excluded from backups like everything else), by the file's key in the account, never by its link; the
 * oldest are forgotten after [MAX_ENTRIES].
 */
class PlayerPositions(context: Context) {
    private val prefs = context.getSharedPreferences("player_positions", Context.MODE_PRIVATE)

    fun read(key: String): Long = prefs.getLong(key, 0L).coerceAtLeast(0L)

    /** Saves [positionMs] of [durationMs]; near the end (credits) the next start is from the beginning again. */
    fun write(key: String, positionMs: Long, durationMs: Long) {
        val finished = durationMs > 0 && positionMs >= durationMs * FINISHED_FRACTION
        prefs.edit {
            if (finished || positionMs < MIN_RESUME_MS) remove(key) else putLong(key, positionMs)
            putLong(STAMP_PREFIX + key, System.currentTimeMillis())
        }
        trim()
    }

    private fun trim() {
        val stamps = prefs.all.filterKeys { it.startsWith(STAMP_PREFIX) }.mapValues { (it.value as? Long) ?: 0L }
        if (stamps.size <= MAX_ENTRIES) return
        val oldest = stamps.entries.sortedBy { it.value }.take(stamps.size - MAX_ENTRIES)
        prefs.edit {
            oldest.forEach { (stampKey, _) ->
                remove(stampKey)
                remove(stampKey.removePrefix(STAMP_PREFIX))
            }
        }
    }

    companion object {
        /** Past this share of the film, it counts as watched to the end. */
        const val FINISHED_FRACTION = 0.92
        private const val MIN_RESUME_MS = 60_000L
        private const val MAX_ENTRIES = 200
        private const val STAMP_PREFIX = "at:"
    }
}
