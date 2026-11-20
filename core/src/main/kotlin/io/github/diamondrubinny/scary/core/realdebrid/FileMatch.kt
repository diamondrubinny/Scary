// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary.core.realdebrid

import io.github.diamondrubinny.scary.core.catalog.Search
import io.github.diamondrubinny.scary.core.model.Movie
import io.github.diamondrubinny.scary.core.player.FileNames

/**
 * Which of the viewer's own files are a given film or series: "The.Shining.1980.1080p.BluRay.x264.mkv" is The Shining
 * (1980); "Dark.S01E02.720p.WEB.mkv" is episode 2 of season 1 of Dark. The title in the file name must be the title
 * (in any of the languages SCARY knows) word for word, apart from spelling variants ("Alien³" is "Alien 3", "Part II"
 * is "Part 2", a leading "The" does not count). For a film the year, when the name has one, is within a year of the
 * film's; a film is never an episode and an episode is never a film. Nothing is looked up anywhere: this only reads
 * names the viewer's account already lists.
 */
object FileMatch {
    data class Parsed(
        val title: String,
        val year: Int?,
        val episode: Boolean,
        /** The title before the season and episode marker, for episodes; empty otherwise. */
        val showTitle: String = "",
        val season: Int? = null,
        val episodeNumber: Int? = null,
    )

    private val yearPattern = Regex("(?<![0-9])(19[0-9]{2}|20[0-9]{2})(?![0-9])")
    private val episodePattern = Regex("(?i)\\bS\\d{1,2}\\s?E\\d{1,3}\\b|\\b\\d{1,2}x\\d{2}\\b")

    fun parse(fileName: String): Parsed {
        val base = fileName.substringBeforeLast('.').takeIf { '.' in fileName && fileName.substringAfterLast('.').length in 2..5 } ?: fileName
        val spaced = base.replace(Regex("[._]+"), " ").replace(Regex("[\\[\\](){}]"), " ").trim()
        val episode = episodePattern.containsMatchIn(spaced)
        // The last plausible year is the release year ("2001 A Space Odyssey 1968" is 1968); the title is before it.
        val year = yearPattern.findAll(spaced).lastOrNull { it.range.first > 0 }
        val titlePart = if (year != null) {
            spaced.substring(0, year.range.first)
        } else {
            spaced.split(' ').takeWhile { it.lowercase() !in FileNames.QUALITY_WORDS }.joinToString(" ")
        }
        val guess = FileNames.guess(fileName)
        return Parsed(
            title = Search.normalize(titlePart).trim(),
            year = year?.value?.toInt(),
            episode = episode,
            showTitle = if (guess.isEpisode) Search.normalize(guess.title).trim() else "",
            season = guess.season,
            episodeNumber = guess.episode,
        )
    }

    /**
     * Whether a file named as [parsed] is [movie]. [alt] adds the film's other titles from TMDB (films only): a file
     * named by one of them fits too, under the same rules for the year and for short titles.
     */
    fun matches(movie: Movie, parsed: Parsed, alt: AltTitles = AltTitles.NONE): Boolean {
        if (movie.isSeries) return matchesSeries(movie, parsed)
        if (parsed.episode) return false
        if (parsed.title.length < 2) return false
        if (parsed.year != null && kotlin.math.abs(parsed.year - movie.year) > 1) return false
        val title = key(parsed.title)
        // A file with no year must match a longer title exactly, so "It" or "Us" alone never claims a random file.
        if (parsed.year == null && title.length < 6) return false
        return title in titleKeys(movie) || title in altKeys(movie, alt)
    }

    private fun matchesSeries(movie: Movie, parsed: Parsed): Boolean {
        if (parsed.season == null || parsed.episodeNumber == null || parsed.showTitle.length < 2) return false
        // A year in the name is the show's first year (or within its run).
        if (parsed.year != null) {
            val inRun = parsed.year >= movie.year - 1 && (movie.ongoing || parsed.year <= (movie.endYear ?: movie.year) + 1)
            if (!inRun) return false
        }
        val title = key(parsed.showTitle)
        // The season and episode already narrow it, so a short title is enough (but not one or two letters).
        return title.length >= 3 && title in titleKeys(movie)
    }

    internal fun titleKeys(movie: Movie): Set<String> =
        (listOf(movie.title, movie.titleHe) + movie.titles.values).filter(String::isNotBlank).mapTo(HashSet()) { key(Search.normalize(it)) }

    /** The keys of a film's other titles; one too short to tell films apart is left out. */
    internal fun altKeys(movie: Movie, alt: AltTitles): Set<String> =
        alt.of(movie.imdbId).mapNotNullTo(HashSet()) { title -> key(Search.normalize(title)).takeIf { it.length >= MIN_ALT_KEY } }

    private const val MIN_ALT_KEY = 3

    private val ROMAN = mapOf("ii" to "2", "iii" to "3", "iv" to "4", "v" to "5", "vi" to "6", "vii" to "7", "viii" to "8", "ix" to "9", "x" to "10")
    private val ARTICLES = setOf("the", "a", "an")

    /**
     * A title's comparison key: lowercase words with spelling variants made one ("Alien³" and "Alien 3", "Part II" and
     * "Part 2", "&" and "and"), a leading article dropped, and the spaces taken out ("Rosemarys Baby").
     */
    internal fun key(normalized: String): String {
        val text = normalized.replace('¹', '1').replace('²', '2').replace('³', '3')
        // "&" is already a separator after normalizing, so "and" is dropped too: "Tom & Jerry" is "Tom and Jerry".
        val words = Search.normalize(text).split(' ').filter { it.isNotEmpty() && it != "and" }.toMutableList()
        for (i in words.indices) if (i > 0) ROMAN[words[i]]?.let { words[i] = it }
        // "Part 2", "Chapter 2": the number is what counts.
        val kept = words.filterIndexed { i, w -> !((w == "part" || w == "chapter") && words.getOrNull(i + 1)?.firstOrNull()?.isDigit() == true) }
        val noArticle = if (kept.size > 1 && kept.first() in ARTICLES) kept.drop(1) else kept
        return noArticle.joinToString("")
    }

    /** The viewer's files that are [movie], biggest first (usually the best quality). For a series, see [forSeries]. */
    fun forMovie(movie: Movie, files: List<RealDebrid.RdFile>, alt: AltTitles = AltTitles.NONE): List<RealDebrid.RdFile> =
        files.filter { matches(movie, parse(it.name), alt) }.sortedByDescending { it.sizeBytes }

    /** One of the viewer's files for an episode of a series. */
    data class Episode(val season: Int, val episode: Int, val file: RealDebrid.RdFile)

    /**
     * The viewer's episode files of [series], by season and episode, the biggest file first when an episode has several
     * (usually the best quality).
     */
    fun forSeries(series: Movie, files: List<RealDebrid.RdFile>): List<Episode> =
        files.mapNotNull { file ->
            val parsed = parse(file.name)
            if (matchesSeries(series, parsed)) Episode(parsed.season ?: return@mapNotNull null, parsed.episodeNumber ?: return@mapNotNull null, file) else null
        }.sortedWith(compareBy<Episode>({ it.season }, { it.episode }).thenByDescending { it.file.sizeBytes })

    /**
     * The films and series of a catalog by their title keys, to tell which one a file is without comparing the file with
     * every film each time. Built once per catalog.
     */
    class Index(movies: Collection<Movie>, private val alt: AltTitles = AltTitles.NONE) {
        private val byKey: Map<String, List<Movie>> = HashMap<String, MutableList<Movie>>().also { map ->
            for (movie in movies) for (key in titleKeys(movie)) map.getOrPut(key) { ArrayList(1) }.add(movie)
        }

        /** Films by the keys of their other titles from TMDB ([alt]); a film's own titles are in [byKey]. */
        private val byAltKey: Map<String, List<Movie>> = HashMap<String, MutableList<Movie>>().also { map ->
            if (alt.size > 0) for (movie in movies) if (!movie.isSeries) for (key in altKeys(movie, alt)) map.getOrPut(key) { ArrayList(1) }.add(movie)
        }

        private fun likeliestFirst(parsed: Parsed) =
            compareBy<Movie>({ parsed.year?.let { y -> kotlin.math.abs(y - it.year) } ?: 0 }, { -(it.imdbRating ?: 0.0) })

        /** The films whose own title [fileName] carries, by the rules of [matches], the likeliest first. */
        fun ownTitle(fileName: String): List<Movie> {
            val parsed = parse(fileName)
            val key = key(if (parsed.episode) parsed.showTitle else parsed.title)
            return byKey[key].orEmpty().distinctBy { it.id }.filter { matches(it, parsed) }.sortedWith(likeliestFirst(parsed))
        }

        /** The films one of whose other titles [fileName] carries, and whose own title it does not. */
        private fun otherTitle(fileName: String, own: List<Movie>): List<Movie> {
            if (byAltKey.isEmpty()) return emptyList()
            val parsed = parse(fileName)
            if (parsed.episode) return emptyList()
            val ownIds = own.mapTo(HashSet()) { it.id }
            return byAltKey[key(parsed.title)].orEmpty().distinctBy { it.id }
                .filter { it.id !in ownIds && matches(it, parsed, alt) }.sortedWith(likeliestFirst(parsed))
        }

        /**
         * The films that [fileName] is by the rules of [matches]: those whose own title it carries first (nearest
         * year, best rated), then those it fits by one of their other titles.
         */
        fun find(fileName: String): List<Movie> {
            val own = ownTitle(fileName)
            return own + otherTitle(fileName, own)
        }

        /**
         * The one film [fileName] is, or null when none or several fit: the viewer decides then. A film's own title
         * wins: other titles are looked at only when no film's own title fits.
         */
        fun one(fileName: String): Movie? {
            val own = ownTitle(fileName)
            return if (own.isNotEmpty()) own.singleOrNull() else otherTitle(fileName, own).singleOrNull()
        }

        /**
         * Films that [fileName] may be when no rule says so: a title that holds, or is held by, the file's title. For
         * the viewer to choose from when correcting a guess; at most [limit], the closest titles first.
         */
        fun similar(fileName: String, limit: Int = 8): List<Movie> {
            val parsed = parse(fileName)
            val wanted = key(if (parsed.episode) parsed.showTitle else parsed.title)
            if (wanted.length < 3) return emptyList()
            return byKey.entries.filter { (key, _) -> key.length >= 3 && (key.contains(wanted) || (key.length >= 4 && wanted.contains(key))) }
                .sortedWith(compareBy({ kotlin.math.abs(it.key.length - wanted.length) }, { it.key }))
                .flatMap { it.value }.distinctBy { it.id }.take(limit)
        }
    }
}
