// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary.core.realdebrid

import io.github.diamondrubinny.scary.core.player.SubtitleFiles
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull
import java.net.URI
import java.net.URLEncoder

/**
 * Real-Debrid's REST API, as documented at api.real-debrid.com (read 9 Nov 2026), for playing files that are already in
 * the viewer's own account: their downloads, and the torrents they added themselves. SCARY never sends anything but
 * the requests below. The sources feature (docs/TORRENT_SCRAPER_PLAN.md, on since 0.6.2 unless a build turns it off)
 * uses the torrent requests that change the account: adding a magnet link the viewer gave, choosing its files, and
 * deleting a torrent. Searching happens only at an indexer the viewer set up (core sources), never here.
 *
 * Every answer is read as untrusted: a field of the wrong type, an over-long string, or a link that is not HTTPS on a
 * Real-Debrid host makes that item (or the answer) be dropped, never half-used.
 */
object RealDebrid {
    const val API = "https://api.real-debrid.com/rest/1.0"
    const val OAUTH = "https://api.real-debrid.com/oauth/v2"

    /** The client id Real-Debrid gives open-source apps (scopes unrestrict, torrents, downloads, user). */
    const val OPEN_SOURCE_CLIENT_ID = "X245A4XAIBGVM"

    /** The grant for the device flow and for refreshing a token, as the documentation names it. */
    const val DEVICE_GRANT = "http://oauth.net/grant_type/device/1.0"

    /** The API allows 250 requests a minute; SCARY stays well under it. */
    const val MAX_REQUESTS_PER_MINUTE = 120

    /** Downloads asked for per page (the API allows up to 5000). */
    const val PAGE_SIZE = 500

    private const val MAX_TEXT = 512
    private const val MAX_URL = 2048
    private const val MAX_ID = 64
    private val idPattern = Regex("^[A-Za-z0-9]{1,$MAX_ID}$")
    private val codePattern = Regex("^[A-Za-z0-9]{4,128}$")
    private val json = Json { ignoreUnknownKeys = true; isLenient = false }

    // Requests ----------------------------------------------------------------------------------------------------------

    fun deviceCodeUrl(): String = "$OAUTH/device/code?client_id=$OPEN_SOURCE_CLIENT_ID&new_credentials=yes"

    fun credentialsUrl(deviceCode: String): String {
        require(codePattern.matches(deviceCode)) { "invalid device code" }
        return "$OAUTH/device/credentials?client_id=$OPEN_SOURCE_CLIENT_ID&code=$deviceCode"
    }

    const val TOKEN_URL = "$OAUTH/token"

    /** The form for the token endpoint: [code] is the device code at sign-in, or the refresh token afterwards. */
    fun tokenForm(clientId: String, clientSecret: String, code: String): String =
        form("client_id" to clientId, "client_secret" to clientSecret, "code" to code, "grant_type" to DEVICE_GRANT)

    const val USER_URL = "$API/user"

    fun downloadsUrl(page: Int): String {
        require(page >= 1) { "pages start at 1" }
        return "$API/downloads?page=$page&limit=$PAGE_SIZE"
    }

    fun torrentsUrl(page: Int): String {
        require(page >= 1) { "pages start at 1" }
        return "$API/torrents?page=$page&limit=$PAGE_SIZE"
    }

    fun torrentInfoUrl(id: String): String = "$API/torrents/info/${checkedId(id)}"

    const val UNRESTRICT_URL = "$API/unrestrict/link"

    /** The form for unrestricting one of the viewer's own links (from their downloads or torrents). */
    fun unrestrictForm(link: String): String {
        require(isHttps(link)) { "not an https link" }
        return form("link" to link)
    }

    fun transcodeUrl(id: String): String = "$API/streaming/transcode/${checkedId(id)}"

    // Torrent requests of the sources feature (documentation read 20 Nov 2026) ------------------------------------------

    /** Answers 201 with the new torrent's id. */
    const val ADD_MAGNET_URL = "$API/torrents/addMagnet"

    /** The form for adding [magnet]: the link SCARY rebuilt from the viewer's text, never the text itself. */
    fun addMagnetForm(magnet: Magnets.Magnet): String = form("magnet" to magnet.uri)

    /** Answers 204 (or 202 when the files were already chosen). */
    fun selectFilesUrl(id: String): String = "$API/torrents/selectFiles/${checkedId(id)}"

    /** The form for choosing a torrent's files by their ids (from its info). */
    fun selectFilesForm(fileIds: List<Int>): String {
        require(fileIds.isNotEmpty() && fileIds.size <= MAX_LINKS && fileIds.all { it >= 0 }) { "invalid file ids" }
        return form("files" to fileIds.joinToString(","))
    }

    /** DELETE; answers 204. */
    fun deleteTorrentUrl(id: String): String = "$API/torrents/delete/${checkedId(id)}"

    const val ACTIVE_COUNT_URL = "$API/torrents/activeCount"

    /** The viewer's newest torrents, finished or not. */
    const val RECENT_TORRENTS_URL = "$API/torrents?page=1&limit=30"

    private fun checkedId(id: String): String {
        require(idPattern.matches(id)) { "invalid id" }
        return id
    }

    private fun form(vararg fields: Pair<String, String>): String =
        fields.joinToString("&") { (k, v) -> k + "=" + URLEncoder.encode(v, "UTF-8") }

    // Answers -----------------------------------------------------------------------------------------------------------

    data class DeviceCode(val deviceCode: String, val userCode: String, val intervalSeconds: Int, val expiresInSeconds: Int, val verificationUrl: String)

    data class Credentials(val clientId: String, val clientSecret: String)

    data class Token(val accessToken: String, val refreshToken: String, val expiresInSeconds: Long)

    data class User(val username: String, val premium: Boolean, val expiration: String)

    fun parseDeviceCode(text: String): DeviceCode? {
        val o = obj(text) ?: return null
        val deviceCode = o.str("device_code")?.takeIf(codePattern::matches) ?: return null
        val userCode = o.str("user_code")?.takeIf(codePattern::matches) ?: return null
        val url = o.str("verification_url")?.takeIf { isHttps(it) && host(it)?.let(::isRealDebridHost) == true } ?: return null
        return DeviceCode(
            deviceCode = deviceCode,
            userCode = userCode,
            intervalSeconds = (o.long("interval") ?: 5).toInt().coerceIn(2, 60),
            expiresInSeconds = (o.long("expires_in") ?: 600).toInt().coerceIn(30, 3600),
            verificationUrl = url,
        )
    }

    /** Null until the viewer has approved the code (the endpoint answers with an error until then). */
    fun parseCredentials(text: String): Credentials? {
        val o = obj(text) ?: return null
        val id = o.str("client_id")?.takeIf(codePattern::matches) ?: return null
        val secret = o.str("client_secret")?.takeIf(codePattern::matches) ?: return null
        return Credentials(id, secret)
    }

    fun parseToken(text: String): Token? {
        val o = obj(text) ?: return null
        val access = o.str("access_token")?.takeIf(codePattern::matches) ?: return null
        val refresh = o.str("refresh_token")?.takeIf(codePattern::matches) ?: return null
        val type = o.str("token_type") ?: "Bearer"
        if (!type.equals("Bearer", ignoreCase = true)) return null
        return Token(access, refresh, (o.long("expires_in") ?: 3600).coerceIn(60, 30L * 24 * 3600))
    }

    fun parseUser(text: String): User? {
        val o = obj(text) ?: return null
        val name = o.str("username") ?: return null
        return User(name, premium = o.str("type") == "premium", expiration = o.str("expiration").orEmpty())
    }

    /** A video in the viewer's account: one of their downloads, or one file of a torrent they added. */
    data class RdFile(
        /** Unique within the list: "d:<download id>" or "t:<torrent id>:<link index>". */
        val key: String,
        val name: String,
        val sizeBytes: Long,
        /** The hoster or Real-Debrid link to unrestrict before playing. */
        val link: String,
        /** A direct link Real-Debrid already generated (downloads only), tried first. */
        val direct: String? = null,
        /** The download id, for the transcoded stream (downloads only). */
        val downloadId: String? = null,
        /** Subtitle files from the same torrent that go with this video, best first (core SubtitleFiles). */
        val subtitles: List<RdSubtitle> = emptyList(),
    )

    /** A subtitle file of the viewer's torrent: its name (with the language in it, often) and the link to fetch it. */
    data class RdSubtitle(val name: String, val sizeBytes: Long, val link: String)

    /** The videos among the viewer's downloads, newest first as the API lists them. */
    fun parseDownloads(text: String): List<RdFile> = array(text)?.mapNotNull { item ->
        val o = item as? JsonObject ?: return@mapNotNull null
        val id = o.str("id")?.takeIf(idPattern::matches) ?: return@mapNotNull null
        val name = o.str("filename") ?: return@mapNotNull null
        if (!isVideo(name, o.str("mimeType"))) return@mapNotNull null
        val link = o.str("link")?.takeIf(::isHttps) ?: return@mapNotNull null
        val direct = o.str("download")?.takeIf(::isPlayableUrl)
        RdFile("d:$id", name, o.long("filesize")?.coerceAtLeast(0) ?: 0, link, direct, downloadId = id)
    }.orEmpty()

    /** A finished torrent the viewer added, with its links (one per selected file, in order). */
    data class RdTorrent(val id: String, val name: String, val sizeBytes: Long, val links: List<String>)

    /** Finished torrents only ("downloaded"); others have nothing to play yet. */
    fun parseTorrents(text: String): List<RdTorrent> = array(text)?.mapNotNull { item ->
        val o = item as? JsonObject ?: return@mapNotNull null
        if (o.str("status") != "downloaded") return@mapNotNull null
        val id = o.str("id")?.takeIf(idPattern::matches) ?: return@mapNotNull null
        val name = o.str("filename") ?: return@mapNotNull null
        val links = (o["links"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.takeIf(::isHttps) }.orEmpty()
        if (links.isEmpty()) return@mapNotNull null
        RdTorrent(id, name, o.long("bytes")?.coerceAtLeast(0) ?: 0, links.take(MAX_LINKS))
    }.orEmpty()

    /**
     * A torrent's video files, from its info: the selected files, in order, match its links one to one. A torrent with a
     * single link and no usable info is still one file, named after the torrent.
     */
    fun torrentFiles(torrent: RdTorrent, infoText: String?): List<RdFile> {
        val selected = infoText?.let(::obj)?.let { o ->
            (o["files"] as? JsonArray)?.mapNotNull { f ->
                val file = f as? JsonObject ?: return@mapNotNull null
                if (file.long("selected") != 1L) return@mapNotNull null
                val path = file.str("path") ?: return@mapNotNull null
                path.substringAfterLast('/').take(MAX_NAME) to (file.long("bytes")?.coerceAtLeast(0) ?: 0)
            }
        }
        if (selected == null || selected.size != torrent.links.size) {
            return if (torrent.links.size == 1 && isVideo(torrent.name, null)) {
                listOf(RdFile("t:${torrent.id}:0", torrent.name, torrent.sizeBytes, torrent.links[0]))
            } else {
                emptyList()
            }
        }
        val names = selected.map { it.first }
        val videos = names.count { isVideo(it, null) }
        return selected.mapIndexedNotNull { i, (name, bytes) ->
            if (!isVideo(name, null)) return@mapIndexedNotNull null
            // The subtitle files of the same torrent that go with this video, small enough to be subtitles.
            val subtitles = SubtitleFiles.forVideo(name, names, videos)
                .filter { selected[it].second in 1..SubtitleFiles.MAX_BYTES.toLong() }
                .map { RdSubtitle(names[it], selected[it].second, torrent.links[it]) }
            RdFile("t:${torrent.id}:$i", name, bytes, torrent.links[i], subtitles = subtitles)
        }
    }

    /** The torrent's single file when its name says it is a video, without asking for its info. */
    fun singleFile(torrent: RdTorrent): RdFile? =
        if (torrent.links.size == 1 && isVideo(torrent.name, null)) RdFile("t:${torrent.id}:0", torrent.name, torrent.sizeBytes, torrent.links[0]) else null

    data class Unrestricted(val id: String, val name: String, val url: String, val mimeType: String)

    fun parseUnrestricted(text: String): Unrestricted? {
        val o = obj(text) ?: return null
        val id = o.str("id")?.takeIf(idPattern::matches) ?: return null
        val url = o.str("download")?.takeIf(::isPlayableUrl) ?: return null
        return Unrestricted(id, o.str("filename").orEmpty(), url, o.str("mimeType").orEmpty())
    }

    /** The HLS ("apple") stream of the best quality Real-Debrid offers, for files the TV cannot play as they are. */
    fun parseTranscode(text: String): String? {
        val apple = obj(text)?.get("apple") as? JsonObject ?: return null
        val byQuality = apple.mapNotNull { (quality, url) -> ((url as? JsonPrimitive)?.contentOrNull?.takeIf(::isPlayableUrl))?.let { quality to it } }
        return byQuality.maxByOrNull { (quality, _) -> Regex("\\d+").find(quality)?.value?.toIntOrNull() ?: 0 }?.second
    }

    // Torrent answers of the sources feature ----------------------------------------------------------------------------

    /** Where a torrent stands, from the eleven statuses the documentation lists; an unknown status counts as failed. */
    enum class TorrentState { CONVERTING, CHOOSE_FILES, QUEUED, DOWNLOADING, FINISHING, READY, FAILED }

    data class TorrentFile(val id: Int, val name: String, val sizeBytes: Long, val selected: Boolean)

    /** A torrent in the viewer's account: from the list (no [files]) or from its info. */
    data class TorrentStatus(
        val id: String,
        val name: String,
        val state: TorrentState,
        /** 0 to 100. */
        val progress: Int,
        val sizeBytes: Long,
        val files: List<TorrentFile> = emptyList(),
        val links: List<String> = emptyList(),
    ) {
        /** The finished torrent as the rest of SCARY reads it, or null while it has nothing to play. */
        fun finished(): RdTorrent? = if (state == TorrentState.READY && links.isNotEmpty()) RdTorrent(id, name, sizeBytes, links) else null
    }

    data class ActiveCount(val active: Int, val limit: Int)

    /** The id of the torrent an added magnet became. */
    fun parseAdded(text: String): String? = obj(text)?.str("id")?.takeIf(idPattern::matches)

    /** The documentation names the count "nb". */
    fun parseActiveCount(text: String): ActiveCount? {
        val o = obj(text) ?: return null
        val active = o.long("nb")?.takeIf { it in 0..MAX_COUNT } ?: return null
        val limit = o.long("limit")?.takeIf { it in 0..MAX_COUNT } ?: return null
        return ActiveCount(active.toInt(), limit.toInt())
    }

    fun parseTorrentStatus(text: String): TorrentStatus? = obj(text)?.let(::torrentStatus)

    /** The viewer's torrents in any state, newest first as the API lists them. */
    fun parseTorrentStatuses(text: String): List<TorrentStatus> =
        array(text)?.mapNotNull { (it as? JsonObject)?.let(::torrentStatus) }.orEmpty()

    private fun torrentStatus(o: JsonObject): TorrentStatus? {
        val id = o.str("id")?.takeIf(idPattern::matches) ?: return null
        val name = o.str("filename") ?: return null
        val state = when (o.str("status")) {
            "magnet_conversion" -> TorrentState.CONVERTING
            "waiting_files_selection" -> TorrentState.CHOOSE_FILES
            "queued" -> TorrentState.QUEUED
            "downloading" -> TorrentState.DOWNLOADING
            "compressing", "uploading" -> TorrentState.FINISHING
            "downloaded" -> TorrentState.READY
            else -> TorrentState.FAILED
        }
        val files = (o["files"] as? JsonArray)?.take(MAX_FILES)?.mapNotNull { f ->
            val file = f as? JsonObject ?: return@mapNotNull null
            val fileId = file.long("id")?.takeIf { it in 0..Int.MAX_VALUE } ?: return@mapNotNull null
            val path = file.str("path") ?: return@mapNotNull null
            TorrentFile(fileId.toInt(), path.substringAfterLast('/').take(MAX_NAME), file.long("bytes")?.coerceAtLeast(0) ?: 0, file.long("selected") == 1L)
        }.orEmpty()
        val links = (o["links"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.takeIf(::isHttps) }.orEmpty()
        return TorrentStatus(id, name, state, (o.long("progress") ?: 0).coerceIn(0, 100).toInt(), o.long("bytes")?.coerceAtLeast(0) ?: 0, files, links.take(MAX_LINKS))
    }

    /**
     * The files to ask Real-Debrid for: the videos, without the small ones beside a big one (samples, trailers), and the
     * subtitle files small enough to be subtitles. Empty when the torrent holds no video: nothing is fetched then.
     */
    fun filesToSelect(files: List<TorrentFile>): List<Int> {
        val videos = files.filter { isVideo(it.name, null) }
        val biggest = videos.maxOfOrNull { it.sizeBytes } ?: return emptyList()
        val kept = videos.filter { it.sizeBytes >= biggest / SAMPLE_FRACTION }
        val subtitles = files.filter { SubtitleFiles.mimeType(it.name) != null && it.sizeBytes in 1..SubtitleFiles.MAX_BYTES.toLong() }
        return (kept + subtitles).map { it.id }.distinct().take(MAX_LINKS)
    }

    /** The video to play first among a pulled torrent's files: the biggest one. */
    fun mainFile(files: List<RdFile>): RdFile? = files.maxByOrNull { it.sizeBytes }

    /** The API's own error code, when an answer is one ({"error": "...", "error_code": 8}). */
    fun errorCode(text: String): Int? = obj(text)?.long("error_code")?.toInt()

    // Rules -------------------------------------------------------------------------------------------------------------

    private val VIDEO_EXTENSIONS = setOf("mkv", "mp4", "m4v", "avi", "mov", "webm", "ts", "m2ts", "wmv", "mpg", "mpeg")
    private const val MAX_LINKS = 500
    private const val MAX_NAME = 255
    private const val MAX_FILES = 2000
    private const val MAX_COUNT = 10_000L

    /** A video under a twentieth of the torrent's biggest one is a sample or a trailer, not the film. */
    private const val SAMPLE_FRACTION = 20

    fun isVideo(name: String, mimeType: String?): Boolean =
        mimeType?.startsWith("video/") == true || name.substringAfterLast('.', "").lowercase() in VIDEO_EXTENSIONS

    /** Only Real-Debrid's own hosts; any other host in an answer is refused. */
    fun isRealDebridHost(host: String): Boolean {
        val h = host.lowercase()
        return h == "real-debrid.com" || h.endsWith(".real-debrid.com")
    }

    /** A link the player may open: HTTPS, of sane length, on a Real-Debrid host, with no user name in it. */
    fun isPlayableUrl(url: String): Boolean {
        if (!isHttps(url)) return false
        val uri = runCatching { URI(url) }.getOrNull() ?: return false
        return uri.rawUserInfo == null && uri.host?.let(::isRealDebridHost) == true
    }

    private fun isHttps(url: String): Boolean =
        url.length <= MAX_URL && url.startsWith("https://") && url.none { it.isWhitespace() || it.isISOControl() }

    private fun host(url: String): String? = runCatching { URI(url).host }.getOrNull()

    // JSON --------------------------------------------------------------------------------------------------------------

    private fun parse(text: String): JsonElement? = try {
        json.parseToJsonElement(text)
    } catch (e: IllegalArgumentException) {
        null
    }

    private fun obj(text: String): JsonObject? = parse(text) as? JsonObject

    private fun array(text: String): JsonArray? = parse(text) as? JsonArray

    private fun JsonObject.str(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull
            ?.takeIf { it.isNotBlank() && it.length <= MAX_TEXT && it.none(Char::isISOControl) }

    private fun JsonObject.long(key: String): Long? =
        (this[key] as? JsonPrimitive)?.let { p -> p.longOrNull ?: p.doubleOrNull?.toLong() }
}
