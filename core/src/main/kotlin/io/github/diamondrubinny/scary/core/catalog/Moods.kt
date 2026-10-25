// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary.core.catalog

import io.github.diamondrubinny.scary.core.model.Movie
import io.github.diamondrubinny.scary.core.model.MovieTag
import io.github.diamondrubinny.scary.core.model.Subgenre
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.Month

/** "What do you feel like tonight?": each mood picks from the curated library by fear level, subgenre or era. */
enum class Mood(val key: String) {
    GENTLE("gentle"),
    MIND_GAMES("mind_games"),
    GHOSTS("ghosts"),
    BLOOD("blood"),
    CLASSICS("classics"),
    RELENTLESS("relentless"),

    // Newer moods come last, so the chips people know keep their place.
    SHORT("short"),
    SLOW_BURN("slow_burn");

    fun matches(movie: Movie): Boolean {
        val fear = movie.fear ?: return false
        return when (this) {
            GENTLE -> fear <= 2
            MIND_GAMES -> movie.subgenres.any { it == Subgenre.PSYCHOLOGICAL || it == Subgenre.MYSTERY }
            GHOSTS -> movie.subgenres.any { it in SPIRITS }
            BLOOD -> fear >= 3 && movie.subgenres.any { it in VISCERAL }
            SHORT -> movie.runtimeMin in 1..SHORT_MAX_MIN
            SLOW_BURN -> MovieTag.SLOW_BURN in movie.tags
            CLASSICS -> movie.year < CLASSIC_BEFORE
            RELENTLESS -> fear >= 4
        }
    }

    /** Matching curated movies; the scariest first for [RELENTLESS], the shortest first for [SHORT], otherwise the library's own order. */
    fun pick(movies: List<Movie>): List<Movie> {
        val matching = movies.filter { it.curated && matches(it) }
        return when (this) {
            RELENTLESS -> matching.sortedByDescending { it.fear }
            SHORT -> matching.sortedBy { it.runtimeMin }
            else -> matching
        }
    }

    companion object {
        private const val CLASSIC_BEFORE = 1990
        private const val SHORT_MAX_MIN = 90
        private val SPIRITS = setOf(Subgenre.SUPERNATURAL, Subgenre.POSSESSION, Subgenre.GHOST, Subgenre.FOLK, Subgenre.HAUNTED_HOUSE)
        private val VISCERAL = setOf(Subgenre.SLASHER, Subgenre.CREATURE, Subgenre.SURVIVAL, Subgenre.ZOMBIE, Subgenre.BODY, Subgenre.VAMPIRE)

        fun fromKey(key: String?): Mood? = entries.firstOrNull { it.key == key }
    }
}

/** Rows that appear only around certain dates. */
enum class Season(val key: String) {
    /** The whole of October. */
    HALLOWEEN("halloween"),

    /** The week leading up to a Friday the 13th, and the day itself. */
    FRIDAY_13TH("friday_13th");

    fun pick(movies: List<Movie>): List<Movie> {
        val allowed = when (this) {
            HALLOWEEN -> HALLOWEEN_SUBGENRES
            FRIDAY_13TH -> FRIDAY_SUBGENRES
        }
        return movies
            .filter { it.curated && it.fear != null && it.subgenres.any(allowed::contains) }
            .sortedByDescending { it.fear }
    }

    companion object {
        private const val LEAD_DAYS = 6
        private val HALLOWEEN_SUBGENRES = setOf(
            Subgenre.SUPERNATURAL, Subgenre.POSSESSION, Subgenre.GHOST, Subgenre.FOLK, Subgenre.SLASHER, Subgenre.CREATURE,
            Subgenre.ZOMBIE, Subgenre.VAMPIRE, Subgenre.HAUNTED_HOUSE,
        )
        private val FRIDAY_SUBGENRES = setOf(Subgenre.SLASHER, Subgenre.SURVIVAL, Subgenre.CREATURE)

        /** Seasons active on [date], the more specific one first. */
        fun activeOn(date: LocalDate): List<Season> = buildList {
            val thirteenth = date.withDayOfMonth(13)
            if (thirteenth.dayOfWeek == DayOfWeek.FRIDAY && date.dayOfMonth in (13 - LEAD_DAYS)..13) add(FRIDAY_13TH)
            if (date.month == Month.OCTOBER) add(HALLOWEEN)
        }
    }
}

/** "Or choose a decade": the curated library by the decade a movie came out in, alone or with a mood. */
object Decades {
    /** 1987 is in the 1980s. */
    fun of(movie: Movie): Int = movie.year / 10 * 10

    /** Decades with at least [minMovies] curated movies, oldest first, so no chip leads to a near-empty row. */
    fun available(movies: List<Movie>, minMovies: Int = MIN_MOVIES): List<Int> =
        movies.filter { it.curated }.groupingBy(::of).eachCount()
            .filterValues { it >= minMovies }.keys.sorted()

    /**
     * Curated movies for a mood, a decade, or both. Nothing is chosen: nothing to show. With a decade alone, the
     * decade's movies in order of year.
     */
    fun pick(movies: List<Movie>, mood: Mood?, decade: Int?): List<Movie> = when {
        mood == null && decade == null -> emptyList()
        mood == null -> movies.filter { it.curated && of(it) == decade }.sortedBy { it.year }
        decade == null -> mood.pick(movies)
        else -> mood.pick(movies).filter { of(it) == decade }
    }

    private const val MIN_MOVIES = 3
}
