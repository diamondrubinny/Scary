// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.StrictMode
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.allowRgb565
import coil3.request.crossfade
import io.github.diamondrubinny.scary.data.CatalogRepository
import io.github.diamondrubinny.scary.data.CinemetaRepository
import io.github.diamondrubinny.scary.data.InstalledAppsRepository
import io.github.diamondrubinny.scary.data.TvInputsRepository
import io.github.diamondrubinny.scary.data.SavedMoviesRepository
import io.github.diamondrubinny.scary.data.RegionDetector
import io.github.diamondrubinny.scary.data.SecretStore
import io.github.diamondrubinny.scary.data.TmdbRepository
import io.github.diamondrubinny.scary.data.SettingsRepository
import io.github.diamondrubinny.scary.launch.Launcher
import io.github.diamondrubinny.scary.stability.CrashGuard

class ScaryApplication : Application(), SingletonImageLoader.Factory {
    val container: AppContainer by lazy { AppContainer(this) }

    override fun onCreate() {
        super.onCreate()
        CrashGuard.install(this)
        if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            // Debug builds only: report disk and network work on the main thread, and leaked resources, in logcat.
            StrictMode.setThreadPolicy(StrictMode.ThreadPolicy.Builder().detectAll().penaltyLog().build())
            StrictMode.setVmPolicy(StrictMode.VmPolicy.Builder().detectAll().penaltyLog().build())
        }
    }

    /**
     * One image loader for the whole app, tuned for TV boxes with little memory (a Mi Box S has 2 GB): a capped
     * memory cache, a disk cache so artwork shows offline, and half-size pixels (RGB_565) on low-memory devices.
     */
    override fun newImageLoader(context: PlatformContext): ImageLoader {
        val activityManager = getSystemService(ActivityManager::class.java)
        val lowMemory = activityManager == null || activityManager.isLowRamDevice || activityManager.memoryClass <= LOW_MEMORY_CLASS_MB
        return ImageLoader.Builder(this)
            // Registered explicitly rather than through ServiceLoader, so R8 shrinking can never drop it.
            .components { add(OkHttpNetworkFetcherFactory()) }
            .memoryCache { MemoryCache.Builder().maxSizePercent(this, if (lowMemory) 0.12 else 0.2).build() }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(DISK_CACHE_BYTES)
                    .build()
            }
            // Coil 3 ignores Cache-Control by default. Poster hosts send short lifetimes, and keeping images longer is
            // what makes the launcher work offline, so the cache-control module is deliberately not added.
            .allowRgb565(lowMemory)
            .crossfade(true)
            .build()
    }

    private companion object {
        const val LOW_MEMORY_CLASS_MB = 192
        const val DISK_CACHE_BYTES = 128L * 1024 * 1024
    }
}

/** Manual dependency wiring. Small enough that a DI framework would add more than it saves. */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val cinemetaRepository = CinemetaRepository(appContext)
    val tmdbRepository = TmdbRepository(appContext, SecretStore(appContext))
    val catalogRepository = CatalogRepository(appContext, cinemetaRepository, tmdbRepository)
    val appsRepository = InstalledAppsRepository(appContext)
    val tvInputsRepository = TvInputsRepository(appContext)
    val settingsRepository = SettingsRepository(appContext)
    val savedMoviesRepository = SavedMoviesRepository(appContext)
    val launcher = Launcher(appContext, appsRepository)
    val regionDetector = RegionDetector(appContext)
}
