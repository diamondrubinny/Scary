// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary.core.catalog

import io.github.diamondrubinny.scary.core.model.Movie
import kotlin.math.abs

/** "Because you loved X": films like the one the viewer loved most recently. Worked out on the TV, from the TV's lists. */
object Recommender {
    data class Recommendation(val because: Movie, val movies: List<Movie>)

    /**
     * Films like the most recently loved one that still has enough of them; [lovedIds] newest first. Never a film in
     * [exclude] (watched or "not for me"), a loved one, or one [isLocked] rejects. Null when nothing qualifies.
     */
    fun becauseYouLoved(
        movies: List<Movie>,
        lovedIds: List<String>,
        exclude: Set<String> = emptySet(),
        isLocked: (Movie) -> Boolean = { false },
    ): Recommendation? {
        val byId = movies.associateBy { it.id }
        val loved = lovedIds.toSet()
        for (id in lovedIds) {
            val seed = byId[id] ?: continue
            val picks = movies.asSequence()
                .filter { it.id != seed.id && it.id !in loved && it.id !in exclude && !isLocked(it) }
                .map { it to score(seed, it) }
                .filter { (_, score) -> score >= MIN_SCORE }
                .sortedWith(compareByDescending<Pair<Movie, Int>> { it.second }.thenBy { it.first.id })
                .map { it.first }
                .take(MAX_MOVIES)
                .toList()
            if (picks.size >= MIN_MOVIES) return Recommendation(seed, picks)
        }
        return null
    }

    /** Shared subgenres weigh most, then the same director, tags, decade and a close fear level. */
    fun score(seed: Movie, other: Movie): Int {
        var score = 3 * other.subgenres.count(seed.subgenres::contains) + 2 * other.tags.count(seed.tags::contains)
        if (directors(seed).any(directors(other)::contains)) score += 3
        if (seed.genre == other.genre) score += 1
        if (abs(seed.year - other.year) <= DECADE) score += 1
        if (seed.fear != null && other.fear != null) score += (2 - abs(seed.fear - other.fear)).coerceAtLeast(0)
        return score
    }

    private fun directors(movie: Movie): Set<String> =
        movie.director.split(',').map { it.trim().lowercase() }.filter(String::isNotEmpty).toSet()

    private const val MIN_SCORE = 5
    private const val MIN_MOVIES = 3
    private const val MAX_MOVIES = 15
    private const val DECADE = 10
}
