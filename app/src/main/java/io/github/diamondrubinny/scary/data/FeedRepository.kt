// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary.data

import android.content.Context
import android.util.Log
import io.github.diamondrubinny.scary.core.catalog.CatalogException
import io.github.diamondrubinny.scary.core.catalog.FilmFeed
import io.github.diamondrubinny.scary.core.update.Updates
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import javax.net.ssl.HttpsURLConnection

/**
 * SCARY's film feed (docs/CATALOG_REBUILD_PLAN.md, core FilmFeed): every film and every home row, built each week from
 * public data and published on the repository's "film-feed" release. The newest good copy is used: the one this TV
 * downloaded, or the one packed in the app when it was built, so there are films at the first start and without a
 * network.
 *
 * The download is a plain GET of one fixed address, at most once a day: no query, no cookie, nothing about the viewer;
 * GitHub sees the TV's IP address, as when SCARY checks for an update. Every redirect is checked (HTTPS, GitHub's own
 * hosts only), the file is read up to a size limit, and it replaces the copy kept only when the whole of it passes
 * FilmFeed's rules and it is not older than that copy.
 */
class FeedRepository(private val context: Context) {
    private val mutex = Mutex()
    private var held: FilmFeed.Result? = null
    private val prefs by lazy { context.getSharedPreferences("film_feed", Context.MODE_PRIVATE) }
    private val file: File get() = File(context.filesDir, FILE_NAME)

    /** The newest good feed on this TV; null only when neither copy can be read (the app's own would have to be broken). */
    suspend fun load(): FilmFeed.Result? = mutex.withLock {
        held ?: withContext(Dispatchers.IO) {
            val downloaded = read("downloaded") { file.takeIf(File::exists)?.readText() }
            val packed = read("packed") { context.assets.open(ASSET_PATH).bufferedReader().use { it.readText() } }
            // A new app may carry a newer feed than the one an old download left behind.
            listOfNotNull(downloaded, packed).maxByOrNull { it.generated }
        }.also { held = it }
    }

    private inline fun read(which: String, text: () -> String?): FilmFeed.Result? = try {
        text()?.let(FilmFeed::parse)?.also { feed ->
            if (feed.droppedFilms > 0 || feed.droppedRows > 0) Log.w(TAG, "The $which feed: ${feed.droppedFilms} films and ${feed.droppedRows} rows dropped")
        }
    } catch (e: CatalogException) {
        Log.w(TAG, "The $which feed is not usable: ${e.problems.take(2)}")
        null
    } catch (e: IOException) {
        // No downloaded copy yet is the usual case.
        null
    }

    /**
     * Downloads the feed when the last try was a day ago or more ([force]: now). Returns the feed to use from here on
     * when a newer good one arrived, null otherwise (nothing new, no network, a file that failed the rules).
     */
    suspend fun refresh(force: Boolean = false): FilmFeed.Result? = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val last = prefs.getLong(KEY_TRIED, 0)
        if (!force && now - last in 0 until REFRESH_EVERY_MS) return@withContext null
        prefs.edit().putLong(KEY_TRIED, now).apply()
        val text = download() ?: return@withContext null
        val fresh = read("downloaded") { text } ?: return@withContext null
        val current = load()
        if (current != null && fresh.generated <= current.generated) return@withContext null
        // Written whole or not at all: a file cut short is never left where the next start would read it.
        val temp = File(context.filesDir, "$FILE_NAME.tmp")
        try {
            temp.writeText(text)
            if (!temp.renameTo(file)) throw IOException("rename failed")
        } catch (e: IOException) {
            Log.w(TAG, "The new feed could not be kept: ${e.javaClass.simpleName}")
            temp.delete()
        }
        mutex.withLock { held = fresh }
        fresh
    }

    private fun download(): String? {
        var current = FEED_URL
        repeat(MAX_REDIRECTS + 1) {
            if (!Updates.isAllowedUrl(current)) return null
            val connection = try {
                (URL(current).openConnection() as? HttpsURLConnection) ?: return null
            } catch (e: IOException) {
                return null
            }
            try {
                connection.instanceFollowRedirects = false
                connection.connectTimeout = TIMEOUT_MS
                connection.readTimeout = TIMEOUT_MS
                connection.setRequestProperty("User-Agent", "SCARY-launcher")
                when (val code = connection.responseCode) {
                    HttpURLConnection.HTTP_OK -> return connection.inputStream.use { input ->
                        val out = ByteArrayOutputStream()
                        val buffer = ByteArray(16 * 1024)
                        while (true) {
                            val n = input.read(buffer)
                            if (n < 0) break
                            out.write(buffer, 0, n)
                            // Bytes, against a limit in characters: a file over it in bytes is over it in characters or close.
                            if (out.size() > MAX_BYTES) return null
                        }
                        out.toString(Charsets.UTF_8.name())
                    }
                    in REDIRECTS -> {
                        val location = connection.getHeaderField("Location") ?: return null
                        current = URL(URL(current), location).toString()
                    }
                    else -> {
                        Log.i(TAG, "The feed was not served: $code")
                        return null
                    }
                }
            } catch (e: IOException) {
                Log.i(TAG, "The feed could not be downloaded: ${e.javaClass.simpleName}")
                return null
            } finally {
                connection.disconnect()
            }
        }
        return null
    }

    private companion object {
        const val TAG = "FeedRepository"
        const val ASSET_PATH = "feed.json"
        const val FILE_NAME = "feed.json"
        const val FEED_URL = "https://github.com/${Updates.OFFICIAL_REPOSITORY}/releases/download/film-feed/feed.json"
        const val KEY_TRIED = "tried_at"
        const val REFRESH_EVERY_MS = 24 * 60 * 60_000L
        const val TIMEOUT_MS = 20_000
        const val MAX_REDIRECTS = 4

        /** UTF-8 takes up to three bytes for a Hebrew or Arabic letter; the parser holds the limit in characters. */
        const val MAX_BYTES = FilmFeed.MAX_CHARS * 2
        val REDIRECTS = setOf(301, 302, 303, 307, 308)
    }
}
