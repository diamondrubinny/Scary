// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary.core.links

import java.net.URLEncoder

/** Known package names. Verify against the installed app on the device (see docs/XIAOMI_SETUP.md). */
object KnownPackages {
    const val STREMIO = "com.stremio.one"
    const val PLAY_STORE = "com.android.vending"
    const val YOUTUBE_TV = "com.google.android.youtube.tv"

    /** Order of the pinned tiles at the start of the apps row. */
    val PINNED = listOf(STREMIO, PLAY_STORE, YOUTUBE_TV)
}

object Links {
    private val imdbPattern = Regex("^tt\\d{7,8}$")
    private val packagePattern = Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+$")

    /**
     * Stremio deep link to a movie's detail page. The scheme is not officially documented
     * and must be verified on the device; callers fall back to opening the app.
     */
    fun stremioMovie(imdbId: String): String {
        require(imdbPattern.matches(imdbId)) { "invalid IMDb id" }
        return "stremio:///detail/movie/$imdbId/$imdbId"
    }

    fun playStoreMarket(packageName: String): String {
        require(packagePattern.matches(packageName)) { "invalid package name" }
        return "market://details?id=$packageName"
    }

    fun playStoreWeb(packageName: String): String {
        require(packagePattern.matches(packageName)) { "invalid package name" }
        return "https://play.google.com/store/apps/details?id=$packageName"
    }

    private val youtubePattern = Regex("^[A-Za-z0-9_-]{11}$")

    fun youTubeVideo(videoId: String): String {
        require(youtubePattern.matches(videoId)) { "invalid YouTube id" }
        return "https://www.youtube.com/watch?v=$videoId"
    }

    fun youTubeTrailerSearch(title: String, year: Int): String = youTubeSearch("$title $year trailer")

    fun youTubeSearch(query: String): String = "https://www.youtube.com/results?search_query=" + urlEncode(query)

    /**
     * Form encoding (spaces as +) that works on every Android version: URLEncoder.encode(String, Charset) only exists
     * from Android 13 and crashed SCARY on older TVs, so the charset is named instead.
     */
    fun urlEncode(text: String): String = URLEncoder.encode(text, "UTF-8")
}
