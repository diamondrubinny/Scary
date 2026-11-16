// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary.data

import android.util.Log
import io.github.diamondrubinny.scary.core.cinemeta.CinemetaMeta
import io.github.diamondrubinny.scary.core.model.MediaKind
import io.github.diamondrubinny.scary.core.player.FileNames
import io.github.diamondrubinny.scary.core.player.OnlineSubtitles
import io.github.diamondrubinny.scary.core.player.SubtitleFiles
import io.github.diamondrubinny.scary.ui.home.OnlineSubtitleSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import javax.net.ssl.HttpsURLConnection

/**
 * Subtitles found online by the film's name: Stremio's OpenSubtitles add-on, keyless (docs/SUBTITLE_SEARCH_PLAN.md).
 *
 * What leaves the TV: the film's IMDb id (a series: with the season and episode), and the TV's IP address, to the
 * subtitle service; then the address of one subtitle, to the same service. When a played file has no known film, its
 * name is read here and only the guessed title goes to Cinemeta (already used for search) to find the film's id. Never
 * the file name, a Real-Debrid link, the viewer's history or any key.
 *
 * Every answer is untrusted: the service's own domain over HTTPS only, no redirects followed, the size limited, the
 * text parsed as text and never run (core OnlineSubtitles, SubtitleFiles).
 */
class SubtitleRepository(
    private val cache: SubtitleCache?,
    private val cinemeta: CinemetaRepository,
) {
    /** The subtitles for one played file. [imdbId] is the film's when SCARY knows it; else it is found from [fileName]. */
    fun source(fileName: String, imdbId: String?, series: Boolean): OnlineSubtitleSource {
        val guess = FileNames.guess(fileName)
        val lock = Mutex()
        var resolved: Target? = null
        var tried = false

        /** The film (or episode) this file is, found once; null when it cannot be told for sure. */
        suspend fun target(): Target? = lock.withLock {
            if (tried) return@withLock resolved
            val found = when {
                imdbId != null && !series -> Target(imdbId, null, null)
                imdbId != null -> if (guess.isEpisode) Target(imdbId, guess.season, guess.episode) else null
                else -> lookup(guess)
            }
            // A film that was told stays told; a failed lookup (offline) is tried again next time.
            resolved = found
            tried = found != null
            found
        }

        return OnlineSubtitleSource(
            search = {
                val t = target()
                val url = t?.let { OnlineSubtitles.searchUrl(it.imdbId, it.season, it.episode) }
                if (t == null || url == null) {
                    emptyList()
                } else {
                    fetchText(url)?.let(OnlineSubtitles::parse)
                }
            },
            fetch = { entry -> download(entry) },
        )
    }

    private class Target(val imdbId: String, val season: Int?, val episode: Int?)

    /** The film or series a file name is, from Cinemeta's search for its title; only the guessed title leaves the TV. */
    private suspend fun lookup(guess: FileNames.Guess): Target? {
        if (guess.title.length < 2) return null
        val kind = if (guess.isEpisode) MediaKind.SERIES else MediaKind.MOVIE
        val results = cinemeta.lookup(guess.title, kind) ?: return null
        val meta: CinemetaMeta = OnlineSubtitles.pick(results, guess) ?: return null
        return Target(meta.imdbId, guess.season.takeIf { guess.isEpisode }, guess.episode.takeIf { guess.isEpisode })
    }

    /** One subtitle as UTF-8 text on the TV (the cache), or null when it cannot be fetched or is not subtitles. */
    private suspend fun download(entry: OnlineSubtitles.Entry): File? = withContext(Dispatchers.IO) {
        val store = cache ?: return@withContext null
        if (!OnlineSubtitles.isTrustedUrl(entry.url)) return@withContext null
        val mimeType = OnlineSubtitles.mimeType(entry)
        val file = store.fileFor(entry.url, SubtitleFiles.FORMATS.entries.firstOrNull { it.value == mimeType }?.key ?: "srt")
        store.kept(file)?.let { return@withContext it }
        val bytes = get(entry.url, SubtitleFiles.MAX_BYTES) ?: return@withContext null
        // The service already sends UTF-8; the same decoding as for a torrent's files covers anything else.
        val text = SubtitleFiles.decode(bytes, mimeType, entry.language) ?: return@withContext null
        store.keep(file, text)
    }

    private suspend fun fetchText(url: String): String? = withContext(Dispatchers.IO) {
        get(url, OnlineSubtitles.MAX_ANSWER_BYTES)?.toString(Charsets.UTF_8)
    }

    /** The answer's body up to [limit] bytes; null for anything but 200 on the service's own domain, or on a bigger one. */
    private fun get(url: String, limit: Int): ByteArray? {
        if (!OnlineSubtitles.isTrustedUrl(url)) return null
        var connection: HttpsURLConnection? = null
        return try {
            connection = URL(url).openConnection() as? HttpsURLConnection ?: return null
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("User-Agent", "SCARY-launcher")
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                Log.i(TAG, "Subtitle service answered ${connection.responseCode}")
                return null
            }
            connection.inputStream.use { it.readUpTo(limit) }
        } catch (e: IOException) {
            Log.i(TAG, "Subtitle request failed: ${e.javaClass.simpleName}")
            null
        } finally {
            connection?.disconnect()
        }
    }

    private fun InputStream.readUpTo(limit: Int): ByteArray? {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        while (true) {
            val n = read(buffer)
            if (n < 0) break
            out.write(buffer, 0, n)
            if (out.size() > limit) return null
        }
        return out.toByteArray()
    }

    private companion object {
        const val TAG = "OnlineSubtitles"
        const val TIMEOUT_MS = 15_000
    }
}
