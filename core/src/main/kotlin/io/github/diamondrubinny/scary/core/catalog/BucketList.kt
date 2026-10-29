// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary.core.catalog

/**
 * The bucket list: films picked to watch one after another, in order. Stored as ids, like [RecentList]. Films only:
 * a series has no end to come back from.
 */
object BucketList {
    const val MAX_SIZE = 30

    /** Adds [id] at the end, or takes it out when it is already there. A full list takes no more. */
    fun toggle(current: List<String>, id: String, max: Int = MAX_SIZE): List<String> = when {
        id in current -> current - id
        current.size >= max -> current
        else -> current + id
    }

    fun remove(current: List<String>, id: String): List<String> = current - id

    /** Moves [id] one place towards the start ([by] -1) or the end (+1); at either end it stays. */
    fun move(current: List<String>, id: String, by: Int): List<String> {
        val from = current.indexOf(id)
        val to = from + by
        if (from < 0 || to !in current.indices) return current
        return current.toMutableList().apply { add(to, removeAt(from)) }
    }

    /** The film after [id] (the one just watched); the first one when [id] is not in the list. */
    fun after(current: List<String>, id: String?): String? {
        val index = current.indexOf(id)
        return current.getOrNull(if (index < 0) 0 else index + 1)
    }
}

/**
 * Whether coming back to SCARY after a bucket-list film looks like the end of the film, from the time away alone (SCARY
 * cannot see inside Stremio). Unlike [AutoWatched], a very long time away still counts: the list moves on, and the
 * viewer is asked about the next film either way.
 */
object BucketReturn {
    fun finished(awayMillis: Long, runtimeMin: Int): Boolean {
        val needed = if (runtimeMin > 0) (runtimeMin * 60_000L * AutoWatched.SHARE_OF_RUNTIME).toLong() else AutoWatched.UNKNOWN_RUNTIME_AWAY_MS
        return awayMillis >= maxOf(needed, AutoWatched.MIN_AWAY_MS)
    }
}
