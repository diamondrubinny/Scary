// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary.core.player

/**
 * What a video's file name says about it: "The.Shining.1980.1080p.BluRay.x264-GRP.mkv" is The Shining (1980);
 * "The.Haunting.of.Hill.House.S01E02.720p.WEB.mkv" is season 1, episode 2 of that show. Read on the TV only; nothing
 * is looked up here. File names are untrusted text: a guess can be wrong, so callers check it against a real title.
 */
object FileNames {
    data class Guess(val title: String, val year: Int?, val season: Int?, val episode: Int?) {
        val isEpisode: Boolean get() = season != null && episode != null
    }

    /** Words that start the technical part of a release name when there is no year or episode marker. */
    val QUALITY_WORDS = setOf(
        "2160p", "1080p", "720p", "480p", "4k", "uhd", "bluray", "bdrip", "brrip", "webrip", "web", "webdl", "hdtv", "dvdrip",
        "remux", "x264", "x265", "h264", "h265", "hevc", "hdr", "proper", "repack", "extended", "unrated", "remastered",
        "hdrip", "dvdscr", "bdremux", "imax", "10bit", "ac3", "dts", "aac", "atmos", "multi", "dual", "internal", "amzn", "dsnp", "hmax",
    )

    private val YEAR = Regex("(?<![0-9])(19[0-9]{2}|20[0-9]{2})(?![0-9])")
    private val SEASON_EPISODE = Regex("(?i)(?<![a-z0-9])s(\\d{1,2})[ ._-]?e(\\d{1,3})(?![0-9])|(?<![a-z0-9])(\\d{1,2})x(\\d{2})(?![0-9a-z])")
    private const val MAX_TITLE = 60

    fun guess(fileName: String): Guess {
        val extension = fileName.substringAfterLast('.', "")
        val base = if (extension.length in 2..5 && '.' in fileName) fileName.substringBeforeLast('.') else fileName
        val spaced = base.substringAfterLast('/').replace(Regex("[._]+"), " ").replace(Regex("[\\[\\](){}]"), " ").replace(Regex("\\s+"), " ").trim()
        val marker = SEASON_EPISODE.find(spaced)
        val season = marker?.let { (it.groupValues[1].ifEmpty { it.groupValues[3] }).toIntOrNull() }
        val episode = marker?.let { (it.groupValues[2].ifEmpty { it.groupValues[4] }).toIntOrNull() }
        // The last plausible year before any episode marker is the release year ("2001 A Space Odyssey 1968" is 1968).
        val years = YEAR.findAll(spaced).filter { it.range.first > 0 && (marker == null || it.range.first < marker.range.first) }
        val year = if (marker != null) years.firstOrNull() else years.lastOrNull()
        val cut = listOfNotNull(marker?.range?.first, year?.range?.first).minOrNull()
        val titlePart = if (cut != null) {
            spaced.substring(0, cut)
        } else {
            spaced.split(' ').takeWhile { it.lowercase().replace("-", "") !in QUALITY_WORDS }.joinToString(" ")
        }
        val title = titlePart.replace(Regex("[^\\p{L}\\p{N}' &:!?,.-]+"), " ").replace(Regex("\\s+"), " ").trim().trim('-', ' ').take(MAX_TITLE)
        return Guess(
            title = title,
            year = year?.value?.toIntOrNull(),
            season = season?.takeIf { it in 0..99 },
            episode = episode?.takeIf { it in 0..999 },
        )
    }
}
