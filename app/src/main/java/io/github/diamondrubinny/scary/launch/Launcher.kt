package io.github.diamondrubinny.scary.launch

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.util.Log
import io.github.diamondrubinny.scary.core.links.KnownPackages
import io.github.diamondrubinny.scary.core.links.Links
import io.github.diamondrubinny.scary.core.model.Movie
import io.github.diamondrubinny.scary.data.InstalledAppsRepository

sealed interface LaunchResult {
    data object Opened : LaunchResult
    data class OpenedStoreInstead(val appLabel: String) : LaunchResult
    data class OpenedAppOnly(val movieTitle: String) : LaunchResult
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
        val uri = Uri.parse(Links.youTubeTrailerSearch(movie.title, movie.year))
        val viaApp = Intent(Intent.ACTION_VIEW, uri).setPackage(KnownPackages.YOUTUBE_TV)
        if (apps.isInstalled(KnownPackages.YOUTUBE_TV) && start(viaApp)) return LaunchResult.Opened
        return if (start(Intent(Intent.ACTION_VIEW, uri))) LaunchResult.Opened else LaunchResult.Failed(YOUTUBE_LABEL)
    }

    fun openStoreFor(packageName: String, label: String): LaunchResult {
        val market = Intent(Intent.ACTION_VIEW, Uri.parse(Links.playStoreMarket(packageName)))
        val web = Intent(Intent.ACTION_VIEW, Uri.parse(Links.playStoreWeb(packageName)))
        return if (start(market) || start(web)) LaunchResult.OpenedStoreInstead(label) else LaunchResult.Failed(label)
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
        private const val YOUTUBE_LABEL = "YouTube"
    }
}
