// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary.core.catalog

import io.github.diamondrubinny.scary.core.model.Movie
import java.time.LocalDate

object NightPick {
    /**
     * Picks the same movie for everyone on a given date, and a different one the next day.
     * Returns null when no movie is at or below [maxFear].
     */
    fun pick(movies: List<Movie>, date: LocalDate, maxFear: Int = 5): Movie? {
        val pool = movies.filter { m -> m.curated && m.fear != null && m.fear <= maxFear }.sortedBy { it.id }
        if (pool.isEmpty()) return null
        // Multiplicative hashing spreads consecutive days across the pool.
        val mixed = date.toEpochDay() * 2_654_435_761L
        return pool[Math.floorMod(mixed, pool.size.toLong()).toInt()]
    }

    /**
     * "Surprise me": a random curated movie at or below [maxFear], skipping [exclude] (the one on screen) and any
     * movie [isLocked] rejects, so the button never lands on a PIN prompt. Movies not in [watched] come first, while
     * any are left. Null when nothing qualifies.
     */
    fun surprise(
        movies: List<Movie>,
        maxFear: Int,
        random: kotlin.random.Random,
        exclude: String? = null,
        isLocked: (Movie) -> Boolean = { false },
        watched: Set<String> = emptySet(),
    ): Movie? {
        val pool = movies.filter { m -> m.curated && m.fear != null && m.fear <= maxFear && !isLocked(m) }
        val fresh = pool.filter { it.id != exclude }.ifEmpty { pool }
        return fresh.filter { it.id !in watched }.ifEmpty { fresh }.randomOrNull(random)
    }
}

object Similar {
    /** Movies sharing the most subgenres with [movie], then the same genre, then the closest fear level. */
    fun find(movies: List<Movie>, movie: Movie, limit: Int = 10): List<Movie> =
        movies.asSequence()
            .filter { it.id != movie.id }
            .map { it to it.subgenres.count(movie.subgenres::contains) }
            .filter { (candidate, shared) -> shared > 0 || candidate.genre == movie.genre }
            .sortedWith(
                compareByDescending<Pair<Movie, Int>> { it.second }
                    .thenByDescending { it.first.genre == movie.genre }
                    .thenBy { kotlin.math.abs(it.first.fearForLock - movie.fearForLock) }
                    .thenBy { it.first.id },
            )
            .map { it.first }
            .take(limit)
            .toList()
}

object RecentList {
    const val MAX_SIZE = 12

    /** Moves [id] to the front, removing duplicates and capping the size. */
    fun push(current: List<String>, id: String, max: Int = MAX_SIZE): List<String> =
        (listOf(id) + current.filter { it != id }).take(max)

    fun decode(raw: String?): List<String> =
        raw?.split(',')?.map(String::trim)?.filter(String::isNotEmpty) ?: emptyList()

    fun encode(ids: List<String>): String = ids.joinToString(",")
}

/** The viewer's own watchlist: newest first, stored with the same encoding as [RecentList]. */
object MyList {
    const val MAX_SIZE = 100

    /** Removes [id] if it is on the list, otherwise adds it at the front. The oldest entry drops past [max]. */
    fun toggle(current: List<String>, id: String, max: Int = MAX_SIZE): List<String> =
        if (id in current) current.filter { it != id } else (listOf(id) + current).take(max)
}
