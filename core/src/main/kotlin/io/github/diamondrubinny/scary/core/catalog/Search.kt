// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary.core.catalog

import io.github.diamondrubinny.scary.core.model.Movie
import java.text.Normalizer

/**
 * Offline search over the catalog. Ranks title matches above director, subgenre and synopsis matches,
 * so typing "the" or a single letter still puts the most likely titles first.
 */
object Search {
    private const val TITLE_PREFIX = 100
    private const val TITLE_WORD = 80
    private const val TITLE_CONTAINS = 60
    private const val YEAR = 50
    private const val DIRECTOR = 40
    private const val LABEL = 30
    private const val SYNOPSIS = 10

    private val combiningMarks = Regex("\\p{Mn}+")
    private val separators = Regex("[^\\p{L}\\p{N}]+")

    /** Lowercases, removes accents and Hebrew vowel points, and collapses punctuation to single spaces. */
    fun normalize(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFD)
            .replace(combiningMarks, "")
            .lowercase()
            .replace(separators, " ")
            .trim()

    /**
     * @param labels localized words describing a movie (genre and subgenre names) supplied by the UI layer.
     * Every word of the query must match somewhere for a movie to be included.
     */
    fun query(
        movies: List<Movie>,
        text: String,
        labels: (Movie) -> List<String> = { emptyList() },
        limit: Int = 60,
    ): List<Movie> = if (terms(text).isEmpty()) emptyList() else Index(movies, labels).query(text, limit)

    private fun terms(text: String): List<String> = normalize(text).split(' ').filter(String::isNotEmpty)

    /**
     * The catalog's searchable text, normalized once. Normalizing every title and synopsis (five languages) is what
     * costs: on a TV, doing it for thousands of films at each typed letter froze the screen. Build it off the main
     * thread when the catalog changes; each [query] then only compares strings.
     */
    class Index(movies: List<Movie>, labels: (Movie) -> List<String> = { emptyList() }) {
        private val entries: List<Entry> = movies.map { movie ->
            val title = normalize(movie.title + " " + movie.titleHe + " " + movie.titles.values.joinToString(" "))
            Entry(
                movie = movie,
                title = title,
                titleWords = title.split(' '),
                director = normalize(movie.director),
                labels = normalize(labels(movie).joinToString(" ")),
                synopsis = normalize(movie.synopsisHe + " " + movie.synopsisEn + " " + movie.synopses.values.joinToString(" ")),
                year = movie.year.toString(),
            )
        }

        val size: Int get() = entries.size

        fun query(text: String, limit: Int = 60): List<Movie> {
            val terms = terms(text)
            if (terms.isEmpty()) return emptyList()
            return entries.asSequence()
                .mapNotNull { entry -> entry.score(terms)?.let { entry.movie to it } }
                .sortedWith(compareByDescending<Pair<Movie, Int>> { it.second }.thenBy { it.first.title })
                .map { it.first }
                .take(limit)
                .toList()
        }
    }

    private class Entry(
        val movie: Movie,
        val title: String,
        val titleWords: List<String>,
        val director: String,
        val labels: String,
        val synopsis: String,
        val year: String,
    ) {
        fun score(terms: List<String>): Int? {
            var total = 0
            for (term in terms) {
                val best = when {
                    title.startsWith(term) -> TITLE_PREFIX
                    titleWords.any { it.startsWith(term) } -> TITLE_WORD
                    term in title -> TITLE_CONTAINS
                    year.startsWith(term) && term.length >= 2 -> YEAR
                    term in director -> DIRECTOR
                    term in labels -> LABEL
                    term.length >= 2 && term in synopsis -> SYNOPSIS
                    else -> return null
                }
                total += best
            }
            return total
        }
    }
}
