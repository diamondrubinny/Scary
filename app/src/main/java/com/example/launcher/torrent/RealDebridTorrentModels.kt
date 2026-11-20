package com.example.launcher.torrent

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RdTorrentAddResponse(val id: String, val uri: String? = null)

@Serializable
data class RdTorrentFile(val id: Int, val path: String, val bytes: Long, val selected: Int = 0)

@Serializable
data class RdTorrentInfo(
    val id: String,
    val filename: String,
    val hash: String? = null,
    val bytes: Long = 0L,
    val status: String = "",
    val progress: Double = 0.0,
    val speed: Long? = null,
    val seeders: Int? = null,
    val links: List<String> = emptyList(),
    val files: List<RdTorrentFile> = emptyList(),
    val added: String? = null,
    val ended: String? = null
)

@Serializable
data class RdUnrestrictLinkResponse(
    val id: String,
    val filename: String,
    @SerialName("mimeType") val mimeType: String? = null,
    val filesize: Long = 0L,
    val link: String,
    val host: String? = null,
    val chunks: Int? = null,
    val crc: Int? = null,
    val download: String,
    val streamable: Int = 0
)

@Serializable
data class RdActiveCountResponse(val active: Int = 0, val limit: Int = 0)

@Serializable
data class PulledTorrentStream(
    val torrentId: String,
    val torrentName: String,
    val fileName: String,
    val fileSizeBytes: Long,
    val unrestrictedUrl: String,
    val status: String,
    val progress: Double,
    val streamable: Boolean
)

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

sealed interface TorrentPullResult {
    data class Ready(val torrent: RdTorrentInfo, val streams: List<PulledTorrentStream>) : TorrentPullResult
    data class InProgress(val torrent: RdTorrentInfo, val message: String) : TorrentPullResult
    data class Error(val message: String, val errorCode: Int? = null) : TorrentPullResult
}
