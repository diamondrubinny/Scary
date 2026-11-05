// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary.core.catalog

import io.github.diamondrubinny.scary.core.model.Movie
import kotlin.math.pow

/**
 * The home screen sorted by what the viewer watches: films they have seen go to the end of each row (or out of it),
 * and the rows of the kind they watch most come first. Worked out on the TV from the TV's own lists; nothing leaves it.
 */
object Personalize {

    /**
     * What the viewer likes, as weights on a film's traits (subgenres, tags, genre, decade, director). Watched films
     * count, newer ones more ([RECENCY] per step back); "Loved it" counts [LOVED] times as much; "Not for me" counts
     * against. [history] is how many watched or loved films it was built from.
     */
    class Taste internal constructor(private val weights: Map<String, Double>, val history: Int) {
        /** Enough history to sort by: a single film says little about a taste. */
        val known: Boolean get() = history >= MIN_HISTORY

        fun score(movie: Movie): Double = traits(movie).sumOf { weights[it] ?: 0.0 }

        companion object {
            val NONE = Taste(emptyMap(), 0)
        }
    }

    /** [watchedIds], [lovedIds] and [dislikedIds] newest first, as stored; ids [movie] does not know are skipped. */
    fun taste(movie: (String) -> Movie?, watchedIds: List<String>, lovedIds: List<String>, dislikedIds: List<String>): Taste {
        val weights = HashMap<String, Double>()
        fun add(film: Movie, amount: Double) = traits(film).forEach { trait ->
            weights.merge(trait, amount * traitWeight(trait), Double::plus)
        }
        val counted = HashSet<String>()
        watchedIds.take(MAX_HISTORY).forEachIndexed { step, id ->
            movie(id)?.let { add(it, RECENCY.pow(step)); counted += id }
        }
        lovedIds.take(MAX_HISTORY).forEachIndexed { step, id ->
            movie(id)?.let { add(it, LOVED * RECENCY.pow(step)); counted += id }
        }
        dislikedIds.take(MAX_HISTORY).forEachIndexed { step, id ->
            movie(id)?.let { add(it, DISLIKED * RECENCY.pow(step)) }
        }
        return Taste(weights, counted.size)
    }

    /** Films not seen yet first, then the watched ones, each group in its own order. */
    fun <T> watchedLast(items: List<T>, isWatched: (T) -> Boolean): List<T> {
        val (seen, unseen) = items.partition(isWatched)
        return if (seen.isEmpty() || unseen.isEmpty()) items else unseen + seen
    }

    /**
     * How well a row of [movies] fits the [taste]: the average fit of its first [SAMPLE] films, plus a bonus for each
     * film in it the viewer watched or loved ([seenOrLoved]): the row they keep watching from is a row for them.
     */
    fun rowScore(movies: List<Movie>, taste: Taste, seenOrLoved: Set<String>): Double {
        if (movies.isEmpty()) return 0.0
        val sample = movies.take(SAMPLE)
        val fit = sample.sumOf(taste::score) / sample.size
        val members = movies.count { it.id in seenOrLoved }.coerceAtMost(MAX_MEMBERS)
        return fit + MEMBER_BONUS * members
    }

    /**
     * The rows best fitting the taste first. Rows that score the same keep their order, and nothing moves while the
     * taste is not [Taste.known]: a new viewer sees the library as curated.
     */
    fun <T> byTaste(rows: List<T>, taste: Taste, score: (T) -> Double): List<T> {
        if (!taste.known || rows.size < 2) return rows
        val scores = rows.map(score)
        return rows.indices.sortedWith(compareByDescending<Int> { scores[it] }.thenBy { it }).map(rows::get)
    }

    /** A film's traits, as the keys of [Taste]'s weights. */
    internal fun traits(movie: Movie): List<String> = buildList {
        add("g:${movie.genre}")
        movie.subgenres.forEach { add("s:$it") }
        movie.tags.forEach { add("t:$it") }
        if (movie.year > 0) add("d:${movie.year / 10}")
        movie.director.split(',').map { it.trim().lowercase() }.filter(String::isNotEmpty).forEach { add("p:$it") }
    }

    private fun traitWeight(trait: String): Double = when (trait[0]) {
        's' -> 1.0
        'p' -> 0.8
        't' -> 0.6
        'g' -> 0.3
        else -> 0.2
    }

    const val MIN_HISTORY = 3
    private const val MAX_HISTORY = 200
    private const val RECENCY = 0.97
    private const val LOVED = 3.0
    private const val DISLIKED = -2.0
    private const val SAMPLE = 30
    private const val MEMBER_BONUS = 0.5
    private const val MAX_MEMBERS = 6
}
