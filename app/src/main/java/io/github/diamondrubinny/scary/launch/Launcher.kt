// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary.launch

import android.app.SearchManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.util.Log
import io.github.diamondrubinny.scary.core.links.KnownPackages
import io.github.diamondrubinny.scary.core.links.Links
import io.github.diamondrubinny.scary.core.links.StreamingApps
import io.github.diamondrubinny.scary.core.model.Movie
import io.github.diamondrubinny.scary.data.InstalledAppsRepository

sealed interface LaunchResult {
    data object Opened : LaunchResult
    data class OpenedStoreInstead(val appLabel: String) : LaunchResult
    /** The app opened, but not on the movie: the viewer searches there for [movieTitle]. */
    data class OpenedAppOnly(val movieTitle: String, val appLabel: String = Launcher.STREMIO_LABEL) : LaunchResult
    data class Failed(val targetLabel: String) : LaunchResult
}

/** Every way the launcher leaves for another app. All failures are caught and reported, never thrown. */
class Launcher(
    private val context: Context,
    private val apps: InstalledAppsRepository,
) {

    fun launchApp(packageName: String, label: String): LaunchResult {
        val pm = context.packageManager
        val intent = pm.getLeanbackLaunchIntentForPackage(packageName) ?: pm.getLaunchIntentForPackage(packageName)
        return if (intent != null && start(intent)) LaunchResult.Opened else LaunchResult.Failed(label)
    }

    fun openInStremio(movie: Movie): LaunchResult {
        if (!apps.isInstalled(KnownPackages.STREMIO)) return openStoreFor(KnownPackages.STREMIO, STREMIO_LABEL)
        val deepLink = Intent(Intent.ACTION_VIEW, Uri.parse(Links.stremioMovie(movie.imdbId)))
            .setPackage(KnownPackages.STREMIO)
        if (start(deepLink)) return LaunchResult.Opened
        return when (launchApp(KnownPackages.STREMIO, STREMIO_LABEL)) {
            LaunchResult.Opened -> LaunchResult.OpenedAppOnly(movie.title)
            else -> LaunchResult.Failed(STREMIO_LABEL)
        }
    }

    fun openTrailer(movie: Movie): LaunchResult {
        // The exact trailer when Cinemeta supplied one, otherwise a YouTube search.
        val link = movie.trailerYoutubeId?.let { runCatching { Links.youTubeVideo(it) }.getOrNull() }
            ?: Links.youTubeTrailerSearch(movie.title, movie.year)
        val uri = Uri.parse(link)
        val viaApp = Intent(Intent.ACTION_VIEW, uri).setPackage(KnownPackages.YOUTUBE_TV)
        if (apps.isInstalled(KnownPackages.YOUTUBE_TV) && start(viaApp)) return LaunchResult.Opened
        return if (start(Intent(Intent.ACTION_VIEW, uri))) LaunchResult.Opened else LaunchResult.Failed(YOUTUBE_LABEL)
    }

    /** Whether [packageName] accepts a search request (ACTION_SEARCH), so SCARY can hand it the movie's title. */
    fun canSearchIn(packageName: String): Boolean = resolves(Intent(Intent.ACTION_SEARCH).setPackage(packageName))

    /**
     * Looks for the movie in another app: its own search when it has one, YouTube's results page for YouTube, and
     * otherwise just opens the app and says what to search for. Only the title (and for YouTube the year) is passed.
     */
    fun searchIn(packageName: String, label: String, movie: Movie): LaunchResult {
        val query = StreamingApps.query(movie.title)
        if (packageName == KnownPackages.YOUTUBE_TV) {
            val results = Intent(Intent.ACTION_VIEW, Uri.parse(Links.youTubeSearch(StreamingApps.youTubeQuery(movie.title, movie.year))))
                .setPackage(packageName)
            if (start(results)) return LaunchResult.Opened
        }
        val search = Intent(Intent.ACTION_SEARCH)
            .setPackage(packageName)
            .putExtra(SearchManager.QUERY, query)
        if (start(search)) return LaunchResult.Opened
        return when (launchApp(packageName, label)) {
            LaunchResult.Opened -> LaunchResult.OpenedAppOnly(query, label)
            else -> LaunchResult.Failed(label)
        }
    }

    /** Google TV's own search, which lists the apps on this TV that have the movie. */
    fun canSearchEverywhere(): Boolean = resolves(Intent(GLOBAL_SEARCH))

    fun searchEverywhere(movie: Movie, label: String): LaunchResult {
        val intent = Intent(GLOBAL_SEARCH).putExtra(SearchManager.QUERY, StreamingApps.query(movie.title))
        return if (start(intent)) LaunchResult.Opened else LaunchResult.Failed(label)
    }

    private fun resolves(intent: Intent): Boolean = try {
        context.packageManager.queryIntentActivities(intent, 0).isNotEmpty()
    } catch (e: RuntimeException) {
        false
    }

    fun openStoreFor(packageName: String, label: String): LaunchResult {
        val market = Intent(Intent.ACTION_VIEW, Uri.parse(Links.playStoreMarket(packageName)))
        val web = Intent(Intent.ACTION_VIEW, Uri.parse(Links.playStoreWeb(packageName)))
        return if (start(market) || start(web)) LaunchResult.OpenedStoreInstead(label) else LaunchResult.Failed(label)
    }

    fun openAppInfo(packageName: String, label: String): LaunchResult {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
        return if (start(intent)) LaunchResult.Opened else LaunchResult.Failed(label)
    }

    fun openSystemSettings(label: String): LaunchResult =
        if (start(Intent(Settings.ACTION_SETTINGS))) LaunchResult.Opened else LaunchResult.Failed(label)

    private fun start(intent: Intent): Boolean = try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: ActivityNotFoundException) {
        Log.i(TAG, "No activity for ${intent.action} ${intent.`package` ?: ""}")
        false
    } catch (e: SecurityException) {
        Log.w(TAG, "Not allowed to start ${intent.action} ${intent.`package` ?: ""}")
        false
    }

    companion object {
        private const val TAG = "Launcher"
        const val STREMIO_LABEL = "Stremio"
        private const val GLOBAL_SEARCH = "android.search.action.GLOBAL_SEARCH"
        private const val YOUTUBE_LABEL = "YouTube"
    }
}
