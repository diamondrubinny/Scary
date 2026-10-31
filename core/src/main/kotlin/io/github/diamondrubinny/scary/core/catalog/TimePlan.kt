// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary.core.catalog

import io.github.diamondrubinny.scary.core.model.Movie
import java.time.LocalDate

/** "How much time do you have?": the evening's length, from one short film to an all-night marathon. */
enum class TimeBudget(val key: String, val minutes: Int) {
    /** Films of at most an hour and a half. */
    QUICK("quick", 90),

    /** Films of an hour and a half to two hours. */
    EVENING("evening", 120),

    /** Two films, three and a half hours at most. */
    DOUBLE("double", 210),

    /** Three films, five hours at most. */
    MARATHON("marathon", 300),
    ;

    /** A lineup is watched in order (double feature, marathon); otherwise the viewer picks one of the films. */
    val isLineup: Boolean get() = this == DOUBLE || this == MARATHON

    companion object {
        fun fromKey(key: String?): TimeBudget? = entries.firstOrNull { it.key == key }
    }
}

data class TimePlanResult(val budget: TimeBudget, val movies: List<Movie>) {
    val totalMinutes: Int get() = movies.sumOf { it.runtimeMin }
}

object TimePlan {
    /**
     * Curated films that fit [budget], from those not in [exclude] (marked "not for me") nor [isLocked]; films not
     * yet [watched] while there are enough of them. The choice changes from day to day and holds within a day. A
     * lineup runs from the gentlest film to the scariest, so the night warms up.
     */
    fun plan(
        movies: List<Movie>,
        budget: TimeBudget,
        date: LocalDate,
        exclude: Set<String> = emptySet(),
        watched: Set<String> = emptySet(),
        isLocked: (Movie) -> Boolean = { false },
    ): TimePlanResult {
        val pool = movies.filter { it.curated && it.runtimeMin > 0 && it.fear != null && it.id !in exclude && !isLocked(it) }
        val fresh = pool.filter { it.id !in watched }
        val ranked = { list: List<Movie> -> list.sortedBy { dayRank(it, date) } }
        val picked = when (budget) {
            TimeBudget.QUICK -> options(ranked(fresh), ranked(pool)) { it.runtimeMin <= budget.minutes }
            TimeBudget.EVENING -> options(ranked(fresh), ranked(pool)) { it.runtimeMin in (TimeBudget.QUICK.minutes + 1)..budget.minutes }
            TimeBudget.DOUBLE -> lineup(ranked(fresh), 2, DOUBLE_MIN..budget.minutes) ?: lineup(ranked(pool), 2, DOUBLE_MIN..budget.minutes)
            TimeBudget.MARATHON -> lineup(ranked(fresh), 3, MARATHON_MIN..budget.minutes) ?: lineup(ranked(pool), 3, MARATHON_MIN..budget.minutes)
        }.orEmpty()
        return TimePlanResult(budget, picked)
    }

    private fun options(fresh: List<Movie>, all: List<Movie>, fits: (Movie) -> Boolean): List<Movie> {
        val unseen = fresh.filter(fits)
        return (if (unseen.size >= MIN_OPTIONS) unseen else unseen + all.filter { fits(it) && it !in unseen }).take(MAX_OPTIONS)
    }

    /** The first [count] films (in day order) whose total length is in [range], gentlest first. */
    private fun lineup(ranked: List<Movie>, count: Int, range: IntRange): List<Movie>? {
        val candidates = ranked.take(MAX_CANDIDATES)
        fun search(start: Int, chosen: List<Movie>, total: Int): List<Movie>? {
            if (chosen.size == count) return chosen.takeIf { total in range }
            for (i in start until candidates.size) {
                val next = candidates[i]
                if (total + next.runtimeMin > range.last) continue
                search(i + 1, chosen + next, total + next.runtimeMin)?.let { return it }
            }
            return null
        }
        return search(0, emptyList(), 0)?.sortedWith(compareBy<Movie>({ it.fear ?: 0 }, { it.runtimeMin }))
    }

    /** The same order for everyone on a date, a different one the next day (multiplicative hashing, as tonight's pick). */
    private fun dayRank(movie: Movie, date: LocalDate): Long =
        Math.floorMod((movie.id.hashCode().toLong() + date.toEpochDay()) * 2_654_435_761L, Int.MAX_VALUE.toLong())

    private const val DOUBLE_MIN = 150
    private const val MARATHON_MIN = 240
    private const val MIN_OPTIONS = 8
    private const val MAX_OPTIONS = 20
    private const val MAX_CANDIDATES = 40
}
