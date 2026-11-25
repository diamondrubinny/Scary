// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary.core.torbox

import io.github.diamondrubinny.scary.core.player.SubtitleFiles
import io.github.diamondrubinny.scary.core.realdebrid.Magnets
import io.github.diamondrubinny.scary.core.realdebrid.RealDebrid
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import java.net.URI

/**
 * TorBox's REST API, as documented at api.torbox.app/openapi.json and api-docs.torbox.app (read 25 Nov 2026), for a
 * second account beside Real-Debrid (docs/TORBOX_PLAN.md): signing in with a device code, listing the viewer's own
 * torrents, adding a magnet link the viewer chose, asking for a file's link, and deleting a torrent. SCARY never sends
 * anything but the requests below, and searching happens only at an indexer the viewer set up (core sources).
 *
 * The account's token is its API key: it does not expire, and the link request carries it in the address. That
 * address is therefore SCARY's alone to open; the player is given only the file's address that comes back.
 *
 * Every answer is read as untrusted: a field of the wrong type, an over-long string, or a link that is not HTTPS makes
 * that item (or the answer) be dropped, never half-used. The same types as Real-Debrid's are produced, so the screens
 * show both accounts alike.
 */
object TorBox {
    const val API = "https://api.torbox.app/v1/api"

    /** The name TorBox shows the viewer when they approve the device; letters and digits only, as it asks. */
    const val APP_NAME = "SCARY"

    /** The API allows 300 requests a minute for a token; SCARY stays well under it. */
    const val MAX_REQUESTS_PER_MINUTE = 120

    /** Torrents asked for at once (the API's own default is 1000). */
    const val PAGE_SIZE = 500

    /** What marks one of TorBox's files in a file's key and link: "torbox:<torrent id>:<file id>". */
    const val SCHEME = "torbox"

    private const val MAX_TEXT = 512
    private const val MAX_URL = 2048
    private const val MAX_NAME = 255
    private const val MAX_FILES = 2000
    private const val MAX_ID = 4_000_000_000_000L
    private const val MIN_INTERVAL_S = 5
    private const val MAX_INTERVAL_S = 60
    private const val MAX_EXPIRES_S = 600

    /** A video under a twentieth of the torrent's biggest one is a sample or a trailer, not the film. */
    private const val SAMPLE_FRACTION = 20
    private val deviceCodePattern = Regex("^[A-Za-z0-9_\\-]{8,256}$")
    private val userCodePattern = Regex("^[A-Za-z0-9]{4,12}$")
    private val tokenPattern = Regex("^[A-Za-z0-9_\\-.]{16,256}$")
    private val boundaryPattern = Regex("^[A-Za-z0-9]{16,64}$")
    private val json = Json { ignoreUnknownKeys = true; isLenient = false }

    // Requests ----------------------------------------------------------------------------------------------------------

    /** Starts the device flow; no token is sent. */
    const val DEVICE_START_URL = "$API/user/auth/device/start?app=$APP_NAME"

    /** Asked again every few seconds until the viewer approved the code. */
    const val DEVICE_TOKEN_URL = "$API/user/auth/device/token"

    fun deviceTokenBody(deviceCode: String): String {
        require(deviceCodePattern.matches(deviceCode)) { "invalid device code" }
        return buildJsonObject { put("device_code", deviceCode) }.toString()
    }

    const val USER_URL = "$API/user/me"

    /** The viewer's torrents, [fresh] past TorBox's own ten-minute cache. */
    fun torrentsUrl(offset: Int = 0, fresh: Boolean = false): String {
        require(offset >= 0) { "offsets start at 0" }
        return "$API/torrents/mylist?offset=$offset&limit=$PAGE_SIZE" + if (fresh) "&bypass_cache=true" else ""
    }

    /** One torrent, always fresh: it is asked for while the viewer waits for it. */
    fun torrentUrl(id: Long): String = "$API/torrents/mylist?id=${checkedId(id)}&bypass_cache=true"

    const val CREATE_URL = "$API/torrents/createtorrent"

    fun multipartType(boundary: String): String {
        require(boundaryPattern.matches(boundary)) { "invalid boundary" }
        return "multipart/form-data; boundary=$boundary"
    }

    /**
     * The form for adding [magnet] (the link SCARY rebuilt, never the viewer's text). The files are asked for as they
     * are, not zipped: a zip cannot be played.
     */
    fun createBody(magnet: Magnets.Magnet, boundary: String): String {
        require(boundaryPattern.matches(boundary)) { "invalid boundary" }
        val uri = magnet.uri
        require(!uri.contains(boundary) && uri.none { it == '\r' || it == '\n' }) { "invalid magnet" }
        return buildString {
            for ((name, value) in listOf("magnet" to uri, "allow_zip" to "false")) {
                append("--").append(boundary).append("\r\n")
                append("Content-Disposition: form-data; name=\"").append(name).append("\"\r\n\r\n")
                append(value).append("\r\n")
            }
            append("--").append(boundary).append("--\r\n")
        }
    }

    /**
     * Asks for one file's link. The address holds the account's token (the API takes it nowhere else for this request):
     * it must never be logged, shown, or handed to a player.
     */
    fun linkUrl(token: String, torrentId: Long, fileId: Int): String {
        require(tokenPattern.matches(token)) { "invalid token" }
        require(fileId >= 0) { "invalid file id" }
        return "$API/torrents/requestdl?token=$token&torrent_id=${checkedId(torrentId)}&file_id=$fileId"
    }

    const val CONTROL_URL = "$API/torrents/controltorrent"

    fun deleteBody(torrentId: Long): String = buildJsonObject {
        put("torrent_id", checkedId(torrentId))
        put("operation", "delete")
    }.toString()

    private fun checkedId(id: Long): Long {
        require(id in 0..MAX_ID) { "invalid id" }
        return id
    }

    // Answers -----------------------------------------------------------------------------------------------------------

    /** What an answer that is not a success means for the viewer; the API names its errors (openapi "Errors"). */
    enum class Problem {
        /** Every active place of the account is taken. */
        SLOTS_FULL,

        /** A monthly, daily or cooldown limit of the plan is reached. */
        LIMIT,
        TOO_BIG,

        /** The plan does not allow this (the API is for paid plans), or the account may not have this item. */
        NOT_ALLOWED,

        /** The token is missing or no longer good: the viewer reset their key. */
        SIGNED_OUT,

        /** The link is not a torrent TorBox can read. */
        INVALID,

        /** Too many requests: quiet for a while. */
        BUSY,

        /** TorBox's side failed or is down. */
        UNAVAILABLE,
        OTHER,
    }

    /** Null when the answer says it succeeded. An answer that cannot be read is [Problem.OTHER]. */
    fun problem(text: String?): Problem? {
        val o = text?.let(::obj) ?: return Problem.OTHER
        val error = o.str("error")
        // Some answers carry an error beside "success": true (the documentation's own examples): the error decides.
        if (error == null) return if ((o["success"] as? JsonPrimitive)?.booleanOrNull == true) null else Problem.OTHER
        return when (error) {
            "ACTIVE_LIMIT" -> Problem.SLOTS_FULL
            "MONTHLY_LIMIT", "COOLDOWN_LIMIT", "DOWNLOAD_LIMIT_REACHED", "DAILY_BANDWIDTH_LIMIT_EXCEEDED" -> Problem.LIMIT
            "DOWNLOAD_TOO_LARGE", "TOO_MUCH_DATA" -> Problem.TOO_BIG
            "PLAN_RESTRICTED_FEATURE", "UPGRADE_REQUIRED", "ITEM_NOT_ALLOWED", "AUTH_ERROR" -> Problem.NOT_ALLOWED
            "NO_AUTH", "BAD_TOKEN" -> Problem.SIGNED_OUT
            "BOZO_TORRENT", "INVALID_HASH", "DOWNLOAD_METADATA_ERROR" -> Problem.INVALID
            "RATE_LIMIT_EXCEEDED" -> Problem.BUSY
            "DATABASE_ERROR", "UNKNOWN_ERROR", "DOWNLOAD_SERVER_ERROR", "STORAGE_ERROR", "AUTH_UNAVAILABLE",
            "NO_SERVERS_AVAILABLE_ERROR", "DOWNLOAD_SERVER_OFFLINE", "LINK_OFFLINE", "TEMPORARILY_DISABLED" -> Problem.UNAVAILABLE
            else -> Problem.OTHER
        }
    }

    /** The API's own name for what went wrong ("DEVICE_CODE_NOT_USED"), when an answer carries one. */
    fun errorName(text: String?): String? = text?.let(::obj)?.str("error")

    /** While the viewer has not approved the code yet, the token request answers with this error. */
    const val NOT_APPROVED_YET = "DEVICE_CODE_NOT_USED"

    data class DeviceCode(
        val deviceCode: String,
        /** The short code the viewer types. */
        val userCode: String,
        val intervalSeconds: Int,
        val expiresInSeconds: Int,
        /** Where the viewer types it: always an HTTPS address on TorBox's own site. */
        val verificationUrl: String,
    )

    fun parseDeviceCode(text: String): DeviceCode? {
        val o = data(text) as? JsonObject ?: return null
        val deviceCode = o.str("device_code")?.takeIf(deviceCodePattern::matches) ?: return null
        val userCode = o.str("code")?.takeIf(userCodePattern::matches) ?: return null
        val url = listOf("friendly_verification_url", "verification_url").firstNotNullOfOrNull { o.str(it)?.takeIf(::isTorBoxPage) } ?: return null
        // The documentation's example gives an interval of 0: never asked faster than every few seconds.
        val interval = (o.long("interval") ?: 0).coerceIn(MIN_INTERVAL_S.toLong(), MAX_INTERVAL_S.toLong()).toInt()
        val expires = (o.long("expires_in") ?: MAX_EXPIRES_S.toLong()).coerceIn(interval.toLong(), MAX_EXPIRES_S.toLong()).toInt()
        return DeviceCode(deviceCode, userCode, interval, expires, url)
    }

    /** The account's token (its API key), from the device flow's last answer. */
    fun parseToken(text: String): String? {
        val o = data(text) as? JsonObject ?: return null
        if (o.str("token_type")?.equals("Bearer", ignoreCase = true) == false) return null
        return o.str("access_token")?.takeIf(tokenPattern::matches)
    }

    fun isToken(text: String): Boolean = tokenPattern.matches(text)

    /** [plan]: 0 free, 1 Essential, 2 Pro, 3 Standard, as the documentation numbers them. */
    data class User(val email: String, val plan: Int, val expiration: String) {
        val paid: Boolean get() = plan != 0
    }

    fun parseUser(text: String): User? {
        val o = data(text) as? JsonObject ?: return null
        val plan = o.long("plan")?.takeIf { it in 0..99 } ?: return null
        return User(o.str("email").orEmpty(), plan.toInt(), o.str("premium_expires_at").orEmpty())
    }

    data class TorrentFile(val id: Int, val name: String, val sizeBytes: Long, val mimeType: String?)

    /** One of the viewer's torrents, in any state. */
    data class Torrent(
        val id: Long,
        val name: String,
        val state: RealDebrid.TorrentState,
        /** 0 to 100. */
        val progress: Int,
        val sizeBytes: Long,
        val files: List<TorrentFile>,
    ) {
        /** As the screens read a torrent of either account; the id says whose it is. */
        fun status(): RealDebrid.TorrentStatus = RealDebrid.TorrentStatus("$SCHEME$id", name, state, progress, sizeBytes)

        /**
         * The videos of a finished torrent, without the small ones beside a big one (samples, trailers), each with the
         * subtitle files that go with it. Empty while the torrent has nothing to play.
         */
        fun videos(): List<RealDebrid.RdFile> {
            if (state != RealDebrid.TorrentState.READY) return emptyList()
            val videos = files.filter { RealDebrid.isVideo(it.name, it.mimeType) }
            val biggest = videos.maxOfOrNull { it.sizeBytes } ?: return emptyList()
            val names = files.map { it.name }
            return videos.filter { it.sizeBytes >= biggest / SAMPLE_FRACTION }.map { video ->
                val subtitles = SubtitleFiles.forVideo(video.name, names, videos.size)
                    .map { files[it] }
                    .filter { it.sizeBytes in 1..SubtitleFiles.MAX_BYTES.toLong() }
                    .map { RealDebrid.RdSubtitle(it.name, it.sizeBytes, fileRef(id, it.id)) }
                RealDebrid.RdFile(fileRef(id, video.id), video.name, video.sizeBytes, fileRef(id, video.id), subtitles = subtitles)
            }
        }
    }

    /** "torbox:<torrent id>:<file id>": a file's key, and what stands where Real-Debrid's files hold a link. */
    fun fileRef(torrentId: Long, fileId: Int): String = "$SCHEME:$torrentId:$fileId"

    /** The torrent and file a [fileRef] names; null for anything else (a Real-Debrid key or link). */
    fun parseFileRef(ref: String): Pair<Long, Int>? {
        val parts = ref.split(':')
        if (parts.size != 3 || parts[0] != SCHEME) return null
        val torrent = parts[1].toLongOrNull()?.takeIf { it in 0..MAX_ID } ?: return null
        val file = parts[2].toIntOrNull()?.takeIf { it >= 0 } ?: return null
        return torrent to file
    }

    /** The torrent id inside the id [Torrent.status] gives the screens; null for a Real-Debrid id. */
    fun parseStatusId(id: String): Long? = id.takeIf { it.startsWith(SCHEME) }?.removePrefix(SCHEME)?.toLongOrNull()?.takeIf { it in 0..MAX_ID }

    /** The viewer's torrents; an answer for one torrent ("id" given) is a list of that one. */
    fun parseTorrents(text: String): List<Torrent> = when (val d = data(text)) {
        is JsonArray -> d.mapNotNull { (it as? JsonObject)?.let(::torrent) }
        is JsonObject -> listOfNotNull(torrent(d))
        else -> emptyList()
    }

    private fun torrent(o: JsonObject): Torrent? {
        val id = o.long("id")?.takeIf { it in 0..MAX_ID } ?: return null
        val name = o.str("name")?.take(MAX_NAME) ?: return null
        val files = (o["files"] as? JsonArray)?.take(MAX_FILES)?.mapNotNull { f ->
            val file = f as? JsonObject ?: return@mapNotNull null
            val fileId = file.long("id")?.takeIf { it in 0..Int.MAX_VALUE } ?: return@mapNotNull null
            val fileName = (file.str("short_name") ?: file.str("name")?.substringAfterLast('/'))?.take(MAX_NAME) ?: return@mapNotNull null
            TorrentFile(fileId.toInt(), fileName, file.long("size")?.coerceAtLeast(0) ?: 0, file.str("mimetype"))
        }.orEmpty()
        // The documentation: a torrent is finished when "download_finished" says so, never by its state's name.
        val finished = o.bool("download_finished") == true && o.bool("download_present") != false
        val state = if (finished) RealDebrid.TorrentState.READY else state(o.str("download_state"))
        return Torrent(id, name, state, if (finished) 100 else percent(o), o.long("size")?.coerceAtLeast(0) ?: 0, files)
    }

    /**
     * The client's state names (qBittorrent's, the documentation says). Only a state that says so in words is a
     * failure: a failed torrent is deleted from the account, and that is never done on a guess.
     */
    private fun state(name: String?): RealDebrid.TorrentState {
        val s = name?.lowercase().orEmpty()
        return when {
            s.startsWith("metadl") || s.startsWith("checking") || s == "allocating" -> RealDebrid.TorrentState.CONVERTING
            s.startsWith("queued") || s.startsWith("paused") || s.startsWith("stopped") -> RealDebrid.TorrentState.QUEUED
            s == "error" || s == "missingfiles" || s.startsWith("failed") -> RealDebrid.TorrentState.FAILED
            s == "uploading" || s == "completed" || s == "cached" || s.startsWith("stalledup") -> RealDebrid.TorrentState.FINISHING
            else -> RealDebrid.TorrentState.DOWNLOADING
        }
    }

    /** The documentation gives no scale for "progress": a value up to 1 is read as a fraction, a larger one as percent. */
    private fun percent(o: JsonObject): Int {
        val value = (o["progress"] as? JsonPrimitive)?.doubleOrNull?.takeIf { it.isFinite() && it >= 0 } ?: return 0
        return (if (value <= 1.0) value * 100 else value).coerceIn(0.0, 100.0).toInt()
    }

    /** The id of the torrent an added magnet became. */
    fun parseCreated(text: String): Long? = (data(text) as? JsonObject)?.long("torrent_id")?.takeIf { it in 0..MAX_ID }

    /**
     * A file's link, for the player: HTTPS, of sane length, with no user name in it, and without [token] anywhere in it
     * (the account's key never reaches a player). The documentation names no host for these links, so none is required.
     */
    fun parseLink(text: String, token: String): String? {
        val url = (data(text) as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull ?: return null
        if (url.length > MAX_URL || !url.startsWith("https://") || url.any { it.isWhitespace() || it.isISOControl() }) return null
        if (token.isNotEmpty() && url.contains(token)) return null
        val uri = runCatching { URI(url) }.getOrNull() ?: return null
        return url.takeIf { uri.rawUserInfo == null && !uri.host.isNullOrBlank() }
    }

    /** Only TorBox's own site: where the viewer approves the device. */
    private fun isTorBoxPage(url: String): Boolean {
        if (url.length > MAX_URL || !url.startsWith("https://") || url.any { it.isWhitespace() || it.isISOControl() }) return false
        val uri = runCatching { URI(url) }.getOrNull() ?: return false
        val host = uri.host?.lowercase() ?: return false
        return uri.rawUserInfo == null && (host == "torbox.app" || host.endsWith(".torbox.app"))
    }

    // JSON --------------------------------------------------------------------------------------------------------------

    private fun parse(text: String): JsonElement? = try {
        json.parseToJsonElement(text)
    } catch (e: IllegalArgumentException) {
        null
    }

    private fun obj(text: String): JsonObject? = parse(text) as? JsonObject

    /** The "data" of an answer that carries no error. */
    private fun data(text: String): JsonElement? = obj(text)?.takeIf { it.str("error") == null }?.get("data")

    private fun JsonObject.str(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull
            ?.takeIf { it.isNotBlank() && it.length <= MAX_TEXT && it.none(Char::isISOControl) }

    private fun JsonObject.long(key: String): Long? =
        (this[key] as? JsonPrimitive)?.takeIf { !it.isString }?.let { p -> p.longOrNull ?: p.doubleOrNull?.takeIf(Double::isFinite)?.toLong() }

    private fun JsonObject.bool(key: String): Boolean? = (this[key] as? JsonPrimitive)?.takeIf { !it.isString }?.booleanOrNull
}
