// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary.core.player

import io.github.diamondrubinny.scary.core.catalog.Search
import io.github.diamondrubinny.scary.core.cinemeta.CinemetaMeta
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import java.net.URI

/**
 * Subtitles found online by a film's IMDb id, through Stremio's OpenSubtitles add-on (the add-on protocol is public;
 * using Stremio's own server from another app is unofficial and requires verification against its terms).
 *
 * Only the IMDb id (and, for a series, the season and episode) is asked for. Every answer is untrusted text: it is
 * parsed here without trusting any field, a link must be HTTPS on the service's own domain, and nothing is ever run.
 * Pure logic, tested on real answers; the network lives in the app.
 */
object OnlineSubtitles {
    const val BASE_URL = "https://opensubtitles-v3.strem.io"

    /** The service's own domain: the search and every download link must be on it (or a subdomain), over HTTPS. */
    const val SERVICE_DOMAIN = "strem.io"

    /** An answer larger than this is not a list of subtitles. */
    const val MAX_ANSWER_BYTES = 1_000_000

    private const val MAX_ENTRIES = 300
    private const val MAX_URL = 512
    private const val MAX_TEXT = 200
    private const val MAX_LANGUAGES = 12

    /** One subtitle the service has for a film. */
    data class Entry(
        val id: String,
        val url: String,
        /** ISO 639-1 where one exists ("en", "he", "pt" for Brazilian), else the service's code, lowercase. */
        val language: String,
        /** The file name the subtitle was made for ("The.Conjuring.2013.720p.BluRay.x264-ALLiANCE.srt"), cleaned. */
        val fileName: String?,
        /** The release it was timed against ("720p.BluRay.x264-ALLiANCE"), cleaned. */
        val release: String?,
        /** "BluRay", "WEBRip", "DVD"… as the service says. */
        val format: String?,
        /** The frame rate the subtitle was timed for (23.976, 25), when known. */
        val fps: Double?,
        /** For the deaf and hard of hearing: sounds as well as words. */
        val hearingImpaired: Boolean,
        /** Its place in the service's answer: the service's own ranking. */
        val order: Int,
    ) {
        /** What the track window calls it: its release, else its file. */
        val label: String? get() = release ?: fileName
    }

    /** How many subtitles the film has in one language. */
    data class LanguageCount(val language: String, val count: Int)

    private val IMDB_ID = Regex("tt\\d{5,10}")
    private val HEARING_IMPAIRED = Regex("(?i)hearing[ ._-]?impaired|(?<![a-z0-9])sdh(?![a-z0-9])|(?<![a-z0-9])cc(?![a-z0-9])")

    /** Codes OpenSubtitles uses that the standard tables do not. */
    private val SERVICE_CODES = mapOf("pob" to "pt", "pom" to "pt", "zht" to "zh", "zhc" to "zh", "zhe" to "zh", "scc" to "sr", "pb" to "pt", "ze" to "zh")

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * The search for a film ([season] and [episode] given for a series). Null when the id is not an IMDb id, or the
     * numbers are out of range: nothing is asked for.
     */
    fun searchUrl(imdbId: String, season: Int? = null, episode: Int? = null): String? {
        if (!IMDB_ID.matches(imdbId)) return null
        if (season == null && episode == null) return "$BASE_URL/subtitles/movie/$imdbId.json"
        if (season == null || episode == null || season !in 0..99 || episode !in 0..999) return null
        return "$BASE_URL/subtitles/series/$imdbId:$season:$episode.json"
    }

    /** True for an HTTPS link on the service's own domain, with no credentials, port or fragment in it. */
    fun isTrustedUrl(url: String): Boolean {
        if (url.length > MAX_URL || url.any { it.isWhitespace() || it.isISOControl() }) return false
        val uri = try {
            URI(url)
        } catch (e: java.net.URISyntaxException) {
            return false
        }
        val host = uri.host?.lowercase() ?: return false
        return uri.scheme == "https" && uri.userInfo == null && uri.port == -1 && uri.rawFragment == null &&
            (host == SERVICE_DOMAIN || host.endsWith(".$SERVICE_DOMAIN"))
    }

    /** The subtitles in the service's answer; an answer that is not the expected shape gives none. */
    fun parse(text: String): List<Entry> {
        val list = try {
            (json.parseToJsonElement(text).jsonObject["subtitles"] as? JsonArray) ?: return emptyList()
        } catch (e: Exception) {
            return emptyList()
        }
        val entries = ArrayList<Entry>()
        for ((order, item) in list.take(MAX_ENTRIES).withIndex()) {
            val o = item as? JsonObject ?: continue
            val id = o.str("id")?.takeIf { it.length in 1..40 && it.all { c -> c.isLetterOrDigit() || c == '-' || c == '_' } } ?: continue
            val url = o.str("url")?.takeIf(::isTrustedUrl) ?: continue
            val language = languageOf(o.str("lang")) ?: continue
            val fileName = TrackInfo.cleanLabel(o.str("subtitleFileName")?.take(MAX_TEXT))
            val release = TrackInfo.cleanLabel(o.str("movieReleaseName")?.take(MAX_TEXT))
            entries += Entry(
                id = id,
                url = url,
                language = language,
                fileName = fileName,
                release = release,
                format = TrackInfo.cleanLabel(o.str("releaseFormat")?.take(24)),
                fps = (o["fpsMilli"] as? JsonPrimitive)?.doubleOrNull?.div(1000.0)?.takeIf { it in 1.0..120.0 },
                hearingImpaired = HEARING_IMPAIRED.containsMatchIn(release.orEmpty() + " " + fileName.orEmpty()),
                order = order,
            )
        }
        return entries
    }

    private const val SRT_MIME = "application/x-subrip"

    /** The player's format for a subtitle: from its file name when that names one the player reads, else SubRip. */
    fun mimeType(entry: Entry): String = entry.fileName?.let(SubtitleFiles::mimeType) ?: SRT_MIME

    /** The service's language code as people know it: "heb" is "he", "pob" (Brazilian Portuguese) is "pt". */
    fun languageOf(code: String?): String? {
        val raw = code?.trim()?.lowercase()?.takeIf { it.length in 2..3 && it.all { c -> c in 'a'..'z' } } ?: return null
        return SERVICE_CODES[raw] ?: Languages.normalize(raw) ?: raw.takeIf { it.length == 3 }
    }

    /**
     * The languages the film has subtitles in, with how many: the languages in [first] (the viewer's own), then the
     * rest by how many subtitles they have. At most [MAX_LANGUAGES], those in [first] always among them.
     */
    fun languages(entries: List<Entry>, first: List<String?> = emptyList()): List<LanguageCount> {
        val counts = entries.groupingBy { it.language }.eachCount()
        val preferred = first.filterNotNull().distinct().filter { it in counts }
        val rest = counts.keys.filter { it !in preferred }.sortedWith(compareBy({ -counts.getValue(it) }, { it }))
        return (preferred + rest).take(MAX_LANGUAGES).map { LanguageCount(it, counts.getValue(it)) }
    }

    /**
     * The best subtitle in [language] for the file being played ([playedName]), or null when there is none:
     * 1. the most words in common with the played file's name ("BluRay", "1080p", "x264", the release group), because a
     *    subtitle made for another cut drifts;
     * 2. a frame rate equal to the video's ([videoFps]), then any known one;
     * 3. plain subtitles before those for the hearing impaired;
     * 4. the service's own order.
     */
    fun best(entries: List<Entry>, language: String, playedName: String, videoFps: Double? = null): Entry? =
        entries.filter { it.language == language }.minWithOrNull(
            compareBy<Entry>(
                { -overlap(playedName, it) },
                { fpsRank(it.fps, videoFps) },
                { it.hearingImpaired },
                { it.order },
            ),
        )

    private fun fpsRank(subtitle: Double?, video: Double?): Int = when {
        video == null -> 0
        subtitle == null -> 1
        kotlin.math.abs(subtitle - video) < 0.05 -> 0
        else -> 2
    }

    private val FORMAT_WORDS = mapOf(
        "blu" to "bluray", "ray" to "bluray", "bdrip" to "bluray", "brrip" to "bluray", "bdremux" to "bluray", "remux" to "bluray",
        "webdl" to "web", "webrip" to "web", "dl" to "web", "amzn" to "web", "nf" to "web",
        "dvdrip" to "dvd", "dvdscr" to "dvd", "hdrip" to "hdtv", "x265" to "hevc", "h265" to "hevc", "h264" to "x264", "avc" to "x264",
    )

    /** Words of a release name, lowercase, format spellings made one ("WEB-DL", "WEBRip" and "WEB" are "web"). */
    internal fun releaseWords(text: String): Set<String> =
        text.lowercase().split(Regex("[^a-z0-9]+")).filter { it.length >= 2 }.mapTo(HashSet()) { FORMAT_WORDS[it] ?: it }

    private fun overlap(playedName: String, entry: Entry): Int {
        val played = releaseWords(playedName.substringBeforeLast('.', playedName))
        val offered = releaseWords(listOfNotNull(entry.release, entry.fileName?.substringBeforeLast('.'), entry.format).joinToString(" "))
        return played.count { it in offered }
    }

    /**
     * The film or series a file is, among Cinemeta's search results for its guessed title: the title must be the same
     * word for word, and the year (when the name has one and Cinemeta knows one) within a year, else none is chosen.
     * A wrong film's subtitles are worse than none.
     */
    fun pick(results: List<CinemetaMeta>, guess: FileNames.Guess): CinemetaMeta? {
        val title = compact(guess.title)
        if (title.length < 2) return null
        val wanted = if (guess.isEpisode) CinemetaMeta.TYPE_SERIES else CinemetaMeta.TYPE_MOVIE
        val sameTitle = results.filter { it.type == wanted && IMDB_ID.matches(it.imdbId) && compact(it.name) == title }
        val byYear = if (guess.year != null) {
            sameTitle.filter { meta -> meta.year?.let { kotlin.math.abs(it - guess.year) <= 1 } ?: false }
        } else {
            // No year in the name: only a title long enough that it is surely this one (shorter for a series, whose
            // type and episode marker already narrow it), and only the top result.
            sameTitle.takeIf { title.length >= if (guess.isEpisode) 3 else 6 }.orEmpty()
        }
        return byYear.firstOrNull()
    }

    private fun compact(text: String): String = Search.normalize(text).replace(" ", "")

    private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString || key == "id" }?.contentOrNull
}
