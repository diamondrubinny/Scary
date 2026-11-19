// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary.core.catalog

import io.github.diamondrubinny.scary.core.cinemeta.CinemetaMeta
import io.github.diamondrubinny.scary.core.model.MediaKind

/**
 * Quality control on the films themselves, for the rows and categories filled from Cinemeta's catalogs. The other
 * checks ask whether a film belongs to a row ([RowAudit]); these ask whether it is worth offering at all:
 *
 * - a film shorter than [MIN_MINUTES] is a short, not a film for the evening;
 * - a film with no rating is not out yet or unknown: it belongs only where "new" is the point, and only when it is new;
 * - a film rated under [MIN_RATING] is not offered;
 * - a thriller or mystery that is not horror, and is also a war film, a comedy, a romance and the like, has no fear in it;
 * - a film with no poster or no synopsis is incomplete.
 *
 * Measured on 486 films of 16 live catalog pages (19.11.2026): about one in ten is caught. The hand-picked library is
 * never checked (its films were chosen by hand), and search still finds everything: a film left out of the rows still
 * opens. Cinemeta gives a rating but not how many voted, so an obscure film with a flattering rating passes.
 */
object FilmQuality {
    enum class Problem { INCOMPLETE, TOO_SHORT, UNRATED, LOW_RATED, NO_FEAR }

    /** "Host" (2020), a real feature, is 57 minutes: the line is under it. */
    const val MIN_MINUTES = 45

    const val MIN_RATING = 5.0

    /** Genres that say a thriller or a mystery is about something other than fear. Horror itself is never ruled out. */
    private val NO_FEAR_GENRES = setOf("war", "comedy", "romance", "musical", "music", "biography", "history", "western", "sport")

    /** The rows whose point is what is new: this year's films and series, and the category of new films. */
    private val NEW_ROWS = setOf("horror_new", "series_new", "new_nightmares")

    fun isNewRow(rowId: String): Boolean = rowId in NEW_ROWS

    /**
     * What is wrong with [meta] as a film to offer; null when nothing is. In a row about what is new ([newRow]) a film
     * of [thisYear] or later may have no rating yet.
     */
    fun problem(meta: CinemetaMeta, newRow: Boolean, thisYear: Int): Problem? {
        if (meta.poster.isNullOrBlank() || (meta.description.isBlank() && meta.localDescription.isBlank())) return Problem.INCOMPLETE
        // A series' length is one episode's.
        if (meta.kind == MediaKind.MOVIE && meta.runtimeMin != null && meta.runtimeMin < MIN_MINUTES) return Problem.TOO_SHORT
        val rating = meta.imdbRating
        if (rating == null) {
            if (!(newRow && (meta.year ?: 0) >= thisYear)) return Problem.UNRATED
        } else if (rating < MIN_RATING) {
            return Problem.LOW_RATED
        }
        val genres = meta.genres.mapTo(HashSet()) { it.trim().lowercase() }
        if ("horror" !in genres && genres.any(NO_FEAR_GENRES::contains)) return Problem.NO_FEAR
        return null
    }

    /** [metas] of the row [rowId] without the films that have a problem; [onRemoved] hears of each one taken out. */
    fun accepted(metas: List<CinemetaMeta>, rowId: String, thisYear: Int, onRemoved: (Problem) -> Unit = {}): List<CinemetaMeta> {
        val newRow = isNewRow(rowId)
        return metas.filter { meta ->
            val problem = problem(meta, newRow, thisYear)
            if (problem != null) onRemoved(problem)
            problem == null
        }
    }
}
