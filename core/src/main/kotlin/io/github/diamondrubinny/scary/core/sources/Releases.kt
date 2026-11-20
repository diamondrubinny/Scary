// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary.core.sources

import io.github.diamondrubinny.scary.core.realdebrid.Magnets
import kotlin.math.ln

/**
 * What the viewer's own indexer answered for a search (docs/TORRENT_SCRAPER_PLAN.md, stage 3), and how the answers are
 * put in order. Everything is read from the release's name: SCARY never opens the torrent to look.
 */
object Releases {
    enum class Resolution(val label: String, val points: Int) {
        UHD("4K", 400), FULL_HD("1080p", 300), HD("720p", 200), SD("SD", 100)
    }

    /** Where the picture came from, best first; [CAM] is a recording of a cinema screen. */
    enum class Origin(val label: String, val points: Int) {
        REMUX("Remux", 90), BLURAY("BluRay", 80), WEB("WEB", 70), HDTV("HDTV", 40), DVD("DVD", 30), UNKNOWN("", 20), CAM("CAM", -400)
    }

    enum class Codec(val label: String) { HEVC("HEVC"), AVC("H.264"), AV1("AV1"), OTHER("") }

    data class Tags(val resolution: Resolution, val origin: Origin, val codec: Codec)

    /** One release the indexer offered: only ones with a magnet link or an info-hash are kept. */
    data class Candidate(
        val title: String,
        val magnet: Magnets.Magnet,
        /** The indexer's site name, when it says. */
        val source: String,
        val sizeBytes: Long,
        /** -1 when the indexer did not say. */
        val seeders: Int,
        val tags: Tags,
    ) {
        val score: Int get() = score(this)
    }

    enum class Filter(val resolution: Resolution?) { ALL(null), UHD(Resolution.UHD), FULL_HD(Resolution.FULL_HD), HD(Resolution.HD), SD(Resolution.SD) }

    enum class Sort { SMART, SEEDERS, SIZE }

    private val uhd = Regex("(?i)(?<![a-z0-9])(2160p|4k|uhd)(?![a-z0-9])")
    private val fullHd = Regex("(?i)(?<![a-z0-9])1080[pi](?![a-z0-9])")
    private val hd = Regex("(?i)(?<![a-z0-9])720p(?![a-z0-9])")
    private val cam = Regex("(?i)(?<![a-z0-9])(cam|camrip|hdcam|ts|hdts|telesync|tc|hdtc|telecine|scr|screener|dvdscr)(?![a-z0-9])")
    private val remux = Regex("(?i)(?<![a-z0-9])remux(?![a-z0-9])")
    private val bluRay = Regex("(?i)(?<![a-z0-9])(blu-?ray|bdrip|brrip|bd25|bd50)(?![a-z0-9])")
    private val web = Regex("(?i)(?<![a-z0-9])(web-?dl|web-?rip|web)(?![a-z0-9])")
    private val hdtv = Regex("(?i)(?<![a-z0-9])(hdtv|pdtv)(?![a-z0-9])")
    private val dvd = Regex("(?i)(?<![a-z0-9])(dvd-?rip|dvd|dvd[59r])(?![a-z0-9])")
    private val hevc = Regex("(?i)(?<![a-z0-9])(x265|h\\.?265|hevc)(?![a-z0-9])")
    private val avc = Regex("(?i)(?<![a-z0-9])(x264|h\\.?264|avc)(?![a-z0-9])")
    private val av1 = Regex("(?i)(?<![a-z0-9])av1(?![a-z0-9])")

    fun tags(title: String): Tags = Tags(
        resolution = when {
            uhd.containsMatchIn(title) -> Resolution.UHD
            fullHd.containsMatchIn(title) -> Resolution.FULL_HD
            hd.containsMatchIn(title) -> Resolution.HD
            else -> Resolution.SD
        },
        origin = when {
            cam.containsMatchIn(title) -> Origin.CAM
            remux.containsMatchIn(title) -> Origin.REMUX
            bluRay.containsMatchIn(title) -> Origin.BLURAY
            web.containsMatchIn(title) -> Origin.WEB
            hdtv.containsMatchIn(title) -> Origin.HDTV
            dvd.containsMatchIn(title) -> Origin.DVD
            else -> Origin.UNKNOWN
        },
        codec = when {
            hevc.containsMatchIn(title) -> Codec.HEVC
            avc.containsMatchIn(title) -> Codec.AVC
            av1.containsMatchIn(title) -> Codec.AV1
            else -> Codec.OTHER
        },
    )

    /**
     * The smart score: the picture first (resolution, then where it came from), then how many people share it, counted
     * so that the step from 5 to 50 matters and the one from 5,000 to 50,000 hardly does. A release nobody shares is
     * pushed down, since Real-Debrid may never fetch it.
     */
    fun score(candidate: Candidate): Int {
        val seeders = candidate.seeders
        val sharing = when {
            seeders < 0 -> 0
            seeders == 0 -> -150
            else -> (ln(seeders.toDouble() + 1) * 12).toInt().coerceAtMost(110)
        }
        return candidate.tags.resolution.points + candidate.tags.origin.points + sharing
    }

    /** The releases the viewer asked to see, best first; the same release from two sites is listed once. */
    fun arrange(candidates: List<Candidate>, filter: Filter, sort: Sort): List<Candidate> {
        val unique = candidates.groupBy { it.magnet.infoHash }.map { (_, same) -> same.maxBy { it.seeders } }
        val shown = unique.filter { filter.resolution == null || it.tags.resolution == filter.resolution }
        return when (sort) {
            Sort.SMART -> shown.sortedWith(compareByDescending<Candidate> { it.score }.thenByDescending { it.seeders }.thenBy { it.title })
            Sort.SEEDERS -> shown.sortedWith(compareByDescending<Candidate> { it.seeders }.thenBy { it.title })
            Sort.SIZE -> shown.sortedWith(compareByDescending<Candidate> { it.sizeBytes }.thenBy { it.title })
        }
    }

    /** The release "Suggest the best" offers: the top of the smart order, never a cinema recording. */
    fun best(candidates: List<Candidate>): Candidate? =
        arrange(candidates, Filter.ALL, Sort.SMART).firstOrNull { it.tags.origin != Origin.CAM }
}
