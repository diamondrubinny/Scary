// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary.core.catalog

/**
 * A home row begins with other films each day. The feed changes once a week and by little, and a row holds up to
 * eighty films of which a TV shows six without scrolling, so the same six stood at the front day after day. Each day
 * the row is turned by one screenful: the films keep their order, the row only starts further along, and after its
 * last film comes its first. A row comes back to its own beginning once it has gone all the way round.
 *
 * The turn depends on the day and the row alone: it is the same all day, so nothing moves under the viewer, and rows
 * are out of step with one another, so they do not all show their beginnings on the same day.
 */
object DailyRotation {
    /** The films a TV shows of a row without scrolling: a row is turned by this many each day. */
    const val STEP = 6

    /** A row shorter than this is left alone: turning it would only show the same films in another order. */
    const val MIN_FILMS = STEP * 3

    /**
     * Rows that say something by their order and are never turned: the newest films stand first in "New nightmares",
     * and a row turned would put an older one in front.
     */
    val FIXED_ROWS: Set<String> = setOf("new_nightmares")

    /** [ids] as the row [rowId] shows them on [day] (days since 1970, in the viewer's own time). */
    fun <T> turned(ids: List<T>, rowId: String, day: Long): List<T> {
        if (rowId in FIXED_ROWS || ids.size < MIN_FILMS) return ids
        val turns = ids.size / STEP
        // Out of step with the other rows, by a number that is the row's own.
        val phase = rowId.fold(7) { sum, letter -> (sum * 31 + letter.code) and 0xFFFF }
        val start = Math.floorMod(day + phase, turns.toLong()).toInt() * STEP
        return if (start == 0) ids else ids.subList(start, ids.size) + ids.subList(0, start)
    }
}
