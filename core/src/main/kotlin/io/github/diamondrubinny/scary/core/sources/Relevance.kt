// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary.core.sources

import java.text.Normalizer

/**
 * Whether a release an indexer offered is the film that was asked for. An indexer answers a search loosely: a film's
 * number it does not understand can bring its newest releases, and a film's name brings every film that shares a word
 * with it (its sequels, its remakes, another film of another year). Only releases whose own name says they are the
 * film are shown; nothing is sent anywhere, the names already came with the answer.
 *
 * A release's name is read as scene names are written: the film's name, then its year, then how it was made
 * ("The.Shining.1980.1080p.BluRay.x264"). The part before the year or the first technical word is the name.
 */
object Relevance {
    /** What was asked for: a film by its [title] and [year] (0 when unknown), or a series by its title. */
    data class Wanted(val title: String, val year: Int, val series: Boolean = false)

    private val ARTICLES = setOf("the", "a", "an")

    /** What follows a film's name when the release is another film of the series: "Scream 2", "Halloween II", "Saw Part 3". */
    private val SEQUEL = setOf("ii", "iii", "iv", "v", "vi", "vii", "viii", "ix", "x", "part", "chapter") + (2..20).map { it.toString() }

    /** Words that may stand beside a film's name without making it another film. */
    private val HARMLESS = setOf(
        "extended", "cut", "directors", "director", "s", "unrated", "uncut", "remastered", "restored", "theatrical", "edition",
        "special", "final", "ultimate", "anniversary", "collectors", "criterion", "imax", "version", "redux", "open", "matte",
        "proper", "repack", "limited", "internal", "dubbed", "subbed", "multi", "dual", "audio", "complete", "integrale", "us", "uk",
    )

    /** Where the name ends in a release's own name: at its year, an episode's number, or the first technical word. */
    private val TECHNICAL = Regex(
        "^(\\d{3,4}p|\\d{3,4}i|4k|uhd|hdr|hdr10|dv|bluray|blu|bdrip|brrip|bdremux|remux|web|webrip|webdl|hdtv|hdrip|dvdrip|dvd|dvdscr|cam|hdcam|ts|hdts|" +
            "telesync|tc|screener|scr|x264|x265|h264|h265|hevc|avc|xvid|divx|aac|ac3|dts|ddp|dd5|atmos|truehd|10bit|8bit|" +
            "s\\d{1,2}(e\\d{1,3})?|season|episode|complete)$",
    )
    private val YEAR = Regex("^(19|20)\\d{2}$")

    private fun words(text: String): List<String> =
        Normalizer.normalize(text.take(400), Normalizer.Form.NFKD)
            .replace(Regex("\\p{M}+"), "")
            .lowercase()
            .replace("&", " and ")
            .replace(Regex("['’`]"), "")
            .split(Regex("[^\\p{L}\\p{N}]+"))
            .filter { it.isNotEmpty() }

    private fun withoutArticle(words: List<String>): List<String> = if (words.size > 1 && words.first() in ARTICLES) words.drop(1) else words

    /** The film's name as the release gives it, and the years written after it. */
    private class Read(val name: List<String>, val years: List<Int>)

    private fun read(release: String): Read {
        val all = words(release)
        // A year is the name's end only when a name stands before it: "2001 A Space Odyssey 1968" keeps its first word.
        val end = all.indices.firstOrNull { i -> i > 0 && (YEAR.matches(all[i]) || TECHNICAL.matches(all[i])) } ?: all.size
        return Read(all.take(end), all.drop(end).filter(YEAR::matches).map(String::toInt))
    }

    /**
     * True when [release] names the film or series [wanted]: its name holds the title's words together, what stands
     * beside them is only harmless ("Extended", "Director's Cut") and never a sequel's number, and for a film the
     * year the release gives is the film's (or the one beside it, since a film's year differs by country). A film's
     * release that gives no year is taken only when its name is the title and nothing else.
     */
    fun isWanted(release: String, wanted: Wanted): Boolean {
        val title = withoutArticle(words(wanted.title))
        if (title.isEmpty()) return false
        val read = read(release)
        val name = read.name
        val at = (0..name.size - title.size).firstOrNull { name.subList(it, it + title.size) == title } ?: return false
        val before = name.take(at)
        val after = name.drop(at + title.size)
        if (after.firstOrNull() in SEQUEL) return false
        val beside = before + after
        val plain = beside.all { it in HARMLESS || it in ARTICLES }
        if (wanted.series) return plain
        if (wanted.year <= 0) return plain
        return if (read.years.isEmpty()) plain else read.years.any { kotlin.math.abs(it - wanted.year) <= 1 } && (plain || beside.size <= MAX_EXTRA_WORDS)
    }

    /**
     * True when [release] holds every word the viewer typed ([query]), in any order: what a search by words should
     * give, where an indexer also gives what holds any one of them. A query with no letters or digits passes all.
     */
    fun holdsWords(release: String, query: String): Boolean {
        val asked = words(query).filterNot { it in ARTICLES }
        if (asked.isEmpty()) return true
        val there = words(release).toHashSet()
        return asked.all { it in there }
    }

    /** With the right year, a name may carry a few more words (a studio's or a director's name before the title). */
    private const val MAX_EXTRA_WORDS = 3
}
