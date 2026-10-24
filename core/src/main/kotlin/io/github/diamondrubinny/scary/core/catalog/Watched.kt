// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary.core.catalog

/** Movies the viewer has seen: newest first, stored with the same encoding as [RecentList]. */
object WatchedList {
    const val MAX_SIZE = 1_000

    fun add(current: List<String>, id: String, max: Int = MAX_SIZE): List<String> =
        (listOf(id) + current.filter { it != id }).take(max)

    fun remove(current: List<String>, id: String): List<String> = current.filter { it != id }
}

/**
 * Whether time spent away from SCARY after "Watch" means the movie was watched. SCARY cannot see inside Stremio or any
 * other app, so it judges by time alone, and errs towards not marking:
 *
 * - back within most of the movie's length (or within [MIN_AWAY_MS]): not watched, maybe just a look;
 * - away far longer than a viewing ([MAX_AWAY_MS]): the TV was probably left or switched off, so it is not marked
 *   either; the viewer can still mark it on the movie page.
 */
object AutoWatched {
    const val SHARE_OF_RUNTIME = 0.6
    const val MIN_AWAY_MS = 20 * 60_000L
    const val UNKNOWN_RUNTIME_AWAY_MS = 45 * 60_000L
    const val MAX_AWAY_MS = 6 * 60 * 60_000L

    fun counts(awayMillis: Long, runtimeMin: Int): Boolean {
        val needed = if (runtimeMin > 0) (runtimeMin * 60_000L * SHARE_OF_RUNTIME).toLong() else UNKNOWN_RUNTIME_AWAY_MS
        return awayMillis >= maxOf(needed, MIN_AWAY_MS) && awayMillis <= MAX_AWAY_MS
    }
}

/** A movie handed to another app to watch, remembered until SCARY is back on screen. */
data class PendingWatch(val movieId: String, val startedAtMillis: Long) {
    fun encode(): String = "$movieId|$startedAtMillis"

    companion object {
        fun decode(raw: String?): PendingWatch? {
            val (id, at) = raw?.split('|')?.takeIf { it.size == 2 } ?: return null
            val started = at.toLongOrNull() ?: return null
            return if (id.isBlank() || started <= 0) null else PendingWatch(id, started)
        }
    }
}

/** Caps a newest-first list without dropping what must stay. */
object KeptList {
    /** Drops from the old end until [max] remain, never an item [isKept] accepts (so it may stay a little over [max]). */
    fun <T> trim(items: List<T>, max: Int, isKept: (T) -> Boolean): List<T> {
        if (items.size <= max) return items
        val result = items.toMutableList()
        var i = result.lastIndex
        while (result.size > max && i >= 0) {
            if (!isKept(result[i])) result.removeAt(i)
            i--
        }
        return result
    }
}
