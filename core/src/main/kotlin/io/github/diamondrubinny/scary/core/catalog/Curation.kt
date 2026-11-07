// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary.core.catalog

import io.github.diamondrubinny.scary.core.model.Movie
import java.time.LocalDate
import kotlin.math.abs

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

/**
 * "More like this" on a film's page. Each candidate gets a score: shared subgenres weigh most, then the same director,
 * shared tags, Cinemeta genres in common (a horror comedy is like other horror comedies), shared actors, a close
 * decade and fear level, a little for a better rating, and [bonus] (the viewer's taste). Only films that share
 * something real are in: a subgenre, a director, most of their genres, or (for films with no subgenres to compare)
 * the genre. Films that score the same are mixed by the pair of ids, so two pages never show the same row by the
 * accident of an id order, while one page always shows the same row. Films [isWatched] says were seen come last.
 */
object Similar {
    fun find(
        movies: List<Movie>,
        movie: Movie,
        limit: Int = LIMIT,
        bonus: (Movie) -> Double = { 0.0 },
        isWatched: (Movie) -> Boolean = { false },
    ): List<Movie> {
        val directors = directors(movie)
        val genres = movie.genres.mapTo(HashSet()) { it.lowercase() }
        val cast = movie.cast.take(CAST_COMPARED).mapTo(HashSet()) { it.lowercase() }
        val seed = movie.id.hashCode()
        val themed = !offTheme(movie)
        val ranked = movies.asSequence()
            .filter { it.id != movie.id && it.kind == movie.kind && !(themed && offTheme(it)) }
            .mapNotNull { candidate ->
                val sharedSubgenres = candidate.subgenres.count(movie.subgenres::contains)
                val sharedDirector = directors.isNotEmpty() && directors(candidate).any(directors::contains)
                val otherGenres = candidate.genres.mapTo(HashSet()) { it.lowercase() }
                val genreFit = if (genres.isEmpty() || otherGenres.isEmpty()) 0.0 else
                    otherGenres.count(genres::contains).toDouble() / (genres + otherGenres).size
                val unsorted = movie.subgenres.isEmpty() || candidate.subgenres.isEmpty()
                val related = sharedSubgenres > 0 || sharedDirector || genreFit >= GENRE_FIT ||
                    (unsorted && candidate.genre == movie.genre && (genreFit > 0 || genres.isEmpty() || otherGenres.isEmpty()))
                if (!related) return@mapNotNull null
                // Shared subgenres as a share of both films' (Dice): a film tagged with everything is not like everything.
                val subgenreFit = if (sharedSubgenres == 0) 0.0 else 2.0 * sharedSubgenres / (movie.subgenres.size + candidate.subgenres.size)
                var score = SUBGENRE_WEIGHT * subgenreFit + sharedSubgenres + 2.0 * candidate.tags.count(movie.tags::contains) + 3.0 * genreFit
                if (sharedDirector) score += 3.0
                score += candidate.cast.take(CAST_COMPARED).count { it.lowercase() in cast }.coerceAtMost(MAX_SHARED_CAST)
                if (candidate.genre == movie.genre) score += 1.0
                val years = abs(candidate.year - movie.year)
                score += when {
                    years <= 5 -> 1.0
                    years <= 10 -> 0.5
                    else -> 0.0
                }
                if (candidate.fear != null && movie.fear != null) score += 0.5 * (2 - abs(candidate.fear - movie.fear)).coerceAtLeast(0)
                score += RATING_WEIGHT * ((candidate.imdbRating ?: AVERAGE_RATING) - AVERAGE_RATING)
                score += bonus(candidate)
                Scored(candidate, score, mix(seed, candidate.id.hashCode()))
            }
            .sortedWith(compareByDescending<Scored> { it.score }.thenBy { it.tieBreak })
            .map { it.movie }
            .toList()
        return Personalize.watchedLast(ranked, isWatched).take(limit)
    }

    private class Scored(val movie: Movie, val score: Double, val tieBreak: Int)

    /** A film from the web whose genres are neither horror, thriller nor mystery (a drama in a general catalog). */
    private fun offTheme(movie: Movie): Boolean =
        movie.genres.isNotEmpty() && movie.genres.none { it.lowercase() in THEME_GENRES }

    private fun directors(movie: Movie): Set<String> =
        movie.director.split(',').map { it.trim().lowercase() }.filter(String::isNotEmpty).toSet()

    /** A stable mix of two hashes: the same pair, the same number; another page, another order. */
    private fun mix(a: Int, b: Int): Int {
        var h = a * 31 + b
        h = h xor (h ushr 16)
        h *= -0x7a143595
        h = h xor (h ushr 13)
        return h
    }

    const val LIMIT = 20
    private val THEME_GENRES = setOf("horror", "thriller", "mystery")
    private const val SUBGENRE_WEIGHT = 5.0
    private const val GENRE_FIT = 0.5
    private const val CAST_COMPARED = 6
    private const val MAX_SHARED_CAST = 3
    private const val AVERAGE_RATING = 6.5
    private const val RATING_WEIGHT = 0.3
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
