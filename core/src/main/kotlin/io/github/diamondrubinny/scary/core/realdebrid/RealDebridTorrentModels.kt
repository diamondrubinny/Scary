// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary.core.realdebrid

/*
 * Types for the source search of docs/TORRENT_SCRAPER_PLAN.md (stage 3, not built yet). Real-Debrid's own answers are
 * read by RealDebrid's strict parsers, not by generated serializers.
 */

enum class ScraperQualityFilter(val label: String) {
    ALL("ALL"), CACHED_ONLY("RD CACHED"), Q_4K("4K"), Q_1080P("1080p"), Q_720P("720p"), Q_SD("SD")
}

enum class ScraperSortMode(val label: String) {
    SMART_SCORE("Smart Score"), SEEDERS("Seeders"), SIZE("Size")
}

data class ScrapedTorrentCandidate(
    val title: String,
    val magnetLink: String,
    val infoHash: String,
    val sourceName: String,
    val sizeBytes: Long,
    val seeders: Int,
    val leechers: Int = 0,
    val qualityTag: String = "",
    val codecTag: String = "",
    val sourceTypeTag: String = "",
    val isCachedOnRd: Boolean = false,
    val qualityScore: Int = 0
)
