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
    ): List<Movie> {
        val terms = normalize(text).split(' ').filter(String::isNotEmpty)
        if (terms.isEmpty()) return emptyList()
        return movies.asSequence()
            .mapNotNull { movie -> score(movie, terms, labels(movie))?.let { movie to it } }
            .sortedWith(compareByDescending<Pair<Movie, Int>> { it.second }.thenBy { it.first.title })
            .map { it.first }
            .take(limit)
            .toList()
    }

    private fun score(movie: Movie, terms: List<String>, labels: List<String>): Int? {
        val title = normalize(movie.title + " " + movie.titleHe + " " + movie.titles.values.joinToString(" "))
        val titleWords = title.split(' ')
        val director = normalize(movie.director)
        val labelText = normalize(labels.joinToString(" "))
        val synopsis = normalize(movie.synopsisHe + " " + movie.synopsisEn + " " + movie.synopses.values.joinToString(" "))
        val year = movie.year.toString()

        var total = 0
        for (term in terms) {
            val best = when {
                title.startsWith(term) -> TITLE_PREFIX
                titleWords.any { it.startsWith(term) } -> TITLE_WORD
                term in title -> TITLE_CONTAINS
                year.startsWith(term) && term.length >= 2 -> YEAR
                term in director -> DIRECTOR
                term in labelText -> LABEL
                term.length >= 2 && term in synopsis -> SYNOPSIS
                else -> return null
            }
            total += best
        }
        return total
    }
}
