// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary.core.catalog

import io.github.diamondrubinny.scary.core.model.MediaKind
import io.github.diamondrubinny.scary.core.model.Movie

/**
 * What narrows a search: films or series, a decade, how frightening, and only what was not watched yet. Each part
 * that is not set lets everything through. Worked out on the TV, from what SCARY already holds.
 */
data class SearchFilter(
    /** Films only, series only, or both (null). */
    val kind: MediaKind? = null,
    /** The first year of a decade (1980 for 1980 to 1989), [OLDER] for everything before [OLDEST_DECADE], or null. */
    val decade: Int? = null,
    val fear: FearBand? = null,
    val unwatchedOnly: Boolean = false,
) {
    /** How frightening, by the library's own scare rating (1 to 5). Films without a rating fit no band. */
    enum class FearBand(val levels: IntRange) { MILD(1..2), MEDIUM(3..3), INTENSE(4..5) }

    val active: Boolean get() = this != NONE

    fun matches(movie: Movie, watched: Boolean): Boolean =
        (kind == null || movie.kind == kind) &&
            (decade == null || inDecade(movie.year)) &&
            (fear == null || movie.fear?.let { it in fear.levels } == true) &&
            !(unwatchedOnly && watched)

    private fun inDecade(year: Int): Boolean = when {
        year <= 0 -> false
        decade == OLDER -> year < OLDEST_DECADE
        else -> year in decade!!..(decade + 9)
    }

    /** OK on a filter moves it to its next value, and back to "any" after the last. */
    fun nextKind(): SearchFilter = copy(kind = when (kind) {
        null -> MediaKind.MOVIE
        MediaKind.MOVIE -> MediaKind.SERIES
        MediaKind.SERIES -> null
    })

    /** The decades from the one [thisYear] is in, back to [OLDEST_DECADE], then "older", then any. */
    fun nextDecade(thisYear: Int): SearchFilter {
        val all = decades(thisYear)
        return copy(decade = if (decade == null) all.first() else all.getOrNull(all.indexOf(decade) + 1))
    }

    fun nextFear(): SearchFilter = copy(fear = when (fear) {
        null -> FearBand.MILD
        FearBand.MILD -> FearBand.MEDIUM
        FearBand.MEDIUM -> FearBand.INTENSE
        FearBand.INTENSE -> null
    })

    companion object {
        val NONE = SearchFilter()

        /** Everything before [OLDEST_DECADE]. */
        const val OLDER = 0
        const val OLDEST_DECADE = 1970

        /** Newest first, ending with [OLDER]. */
        fun decades(thisYear: Int): List<Int> {
            val newest = (thisYear.coerceIn(OLDEST_DECADE, 2199) / 10) * 10
            return (newest downTo OLDEST_DECADE step 10).toList() + OLDER
        }
    }
}
