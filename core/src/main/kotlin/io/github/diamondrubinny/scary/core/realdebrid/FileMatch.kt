// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary.core.realdebrid

import io.github.diamondrubinny.scary.core.catalog.Search
import io.github.diamondrubinny.scary.core.model.Movie

/**
 * Which of the viewer's own files are a given film: "The.Shining.1980.1080p.BluRay.x264.mkv" is The Shining (1980).
 * The title in the file name must be the film's title (in any of its languages) word for word, and the year, when
 * the name has one, within a year of the film's. Episodes ("S01E02") are never a film. Nothing is looked up anywhere:
 * this only reads names the viewer's account already lists.
 */
object FileMatch {
    data class Parsed(val title: String, val year: Int?, val episode: Boolean)

    private val yearPattern = Regex("(?<![0-9])(19[0-9]{2}|20[0-9]{2})(?![0-9])")
    private val episodePattern = Regex("(?i)\\bS\\d{1,2}\\s?E\\d{1,3}\\b|\\b\\d{1,2}x\\d{2}\\b")

    /** Words that start the technical part of a release name when there is no year. */
    private val qualityWords = setOf(
        "2160p", "1080p", "720p", "480p", "4k", "uhd", "bluray", "bdrip", "brrip", "webrip", "web", "webdl", "hdtv", "dvdrip",
        "remux", "x264", "x265", "h264", "h265", "hevc", "hdr", "proper", "repack", "extended", "unrated", "remastered",
    )

    fun parse(fileName: String): Parsed {
        val base = fileName.substringBeforeLast('.').takeIf { '.' in fileName && fileName.substringAfterLast('.').length in 2..5 } ?: fileName
        val spaced = base.replace(Regex("[._]+"), " ").replace(Regex("[\\[\\](){}]"), " ").trim()
        val episode = episodePattern.containsMatchIn(spaced)
        // The last plausible year is the release year ("2001 A Space Odyssey 1968" is 1968); the title is before it.
        val year = yearPattern.findAll(spaced).lastOrNull { it.range.first > 0 }
        val titlePart = if (year != null) {
            spaced.substring(0, year.range.first)
        } else {
            spaced.split(' ').takeWhile { it.lowercase() !in qualityWords }.joinToString(" ")
        }
        return Parsed(Search.normalize(titlePart).trim(), year?.value?.toInt(), episode)
    }

    fun matches(movie: Movie, parsed: Parsed): Boolean {
        if (parsed.episode && !movie.isSeries) return false
        if (parsed.title.length < 2) return false
        if (parsed.year != null && kotlin.math.abs(parsed.year - movie.year) > 1) return false
        // Compared without spaces: "Rosemarys.Baby" is "Rosemary's Baby".
        val titles = (listOf(movie.title, movie.titleHe) + movie.titles.values).filter(String::isNotBlank).map { compact(Search.normalize(it)) }
        val title = compact(parsed.title)
        // A file with no year must match a longer title exactly, so "It" or "Us" alone never claims a random file.
        if (parsed.year == null && title.length < 6) return false
        return title in titles
    }

    private fun compact(text: String): String = text.replace(" ", "")

    /** The viewer's files that are [movie], biggest first (usually the best quality). */
    fun forMovie(movie: Movie, files: List<RealDebrid.RdFile>): List<RealDebrid.RdFile> =
        files.filter { matches(movie, parse(it.name)) }.sortedByDescending { it.sizeBytes }
}
