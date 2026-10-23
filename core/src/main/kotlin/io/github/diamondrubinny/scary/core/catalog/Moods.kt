// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary.core.catalog

import io.github.diamondrubinny.scary.core.model.Movie
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
    RELENTLESS("relentless");

    fun matches(movie: Movie): Boolean {
        val fear = movie.fear ?: return false
        return when (this) {
            GENTLE -> fear <= 2
            MIND_GAMES -> movie.subgenres.any { it == Subgenre.PSYCHOLOGICAL || it == Subgenre.MYSTERY }
            GHOSTS -> movie.subgenres.any { it in SPIRITS }
            BLOOD -> fear >= 3 && movie.subgenres.any { it in VISCERAL }
            CLASSICS -> movie.year < CLASSIC_BEFORE
            RELENTLESS -> fear >= 4
        }
    }

    /** Matching curated movies; the scariest first for [RELENTLESS], otherwise the library's own order. */
    fun pick(movies: List<Movie>): List<Movie> {
        val matching = movies.filter { it.curated && matches(it) }
        return if (this == RELENTLESS) matching.sortedByDescending { it.fear } else matching
    }

    companion object {
        private const val CLASSIC_BEFORE = 1990
        private val SPIRITS = setOf(Subgenre.SUPERNATURAL, Subgenre.POSSESSION, Subgenre.GHOST, Subgenre.FOLK)
        private val VISCERAL = setOf(Subgenre.SLASHER, Subgenre.CREATURE, Subgenre.SURVIVAL)

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
