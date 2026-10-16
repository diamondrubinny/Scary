package io.github.diamondrubinny.scary.core.catalog

import io.github.diamondrubinny.scary.core.model.Movie
import java.time.LocalDate

object NightPick {
    /**
     * Picks the same movie for everyone on a given date, and a different one the next day.
     * Returns null when no movie is at or below [maxFear].
     */
    fun pick(movies: List<Movie>, date: LocalDate, maxFear: Int = 5): Movie? {
        val pool = movies.filter { it.fear <= maxFear }.sortedBy { it.id }
        if (pool.isEmpty()) return null
        // Multiplicative hashing spreads consecutive days across the pool.
        val mixed = date.toEpochDay() * 2_654_435_761L
        return pool[Math.floorMod(mixed, pool.size.toLong()).toInt()]
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
                    .thenBy { kotlin.math.abs(it.first.fear - movie.fear) }
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
