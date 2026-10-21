package io.github.diamondrubinny.scary

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import io.github.diamondrubinny.scary.data.CatalogRepository
import io.github.diamondrubinny.scary.data.CinemetaRepository
import io.github.diamondrubinny.scary.data.InstalledAppsRepository
import io.github.diamondrubinny.scary.data.SavedMoviesRepository
import io.github.diamondrubinny.scary.data.SecretStore
import io.github.diamondrubinny.scary.data.TmdbRepository
import io.github.diamondrubinny.scary.data.SettingsRepository
import io.github.diamondrubinny.scary.launch.Launcher

class ScaryApplication : Application(), ImageLoaderFactory {
    val container: AppContainer by lazy { AppContainer(this) }

    /**
     * One image loader for the whole app, tuned for TV boxes with little memory (a Mi Box S has 2 GB): a capped
     * memory cache, a disk cache so artwork shows offline, and half-size pixels (RGB_565) on low-memory devices.
     */
    override fun newImageLoader(): ImageLoader {
        val activityManager = getSystemService(ActivityManager::class.java)
        val lowMemory = activityManager == null || activityManager.isLowRamDevice || activityManager.memoryClass <= LOW_MEMORY_CLASS_MB
        return ImageLoader.Builder(this)
            .memoryCache { MemoryCache.Builder(this).maxSizePercent(if (lowMemory) 0.12 else 0.2).build() }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(DISK_CACHE_BYTES)
                    .build()
            }
            .allowRgb565(lowMemory)
            // Poster hosts send short cache lifetimes; keeping images longer is what makes the launcher work offline.
            .respectCacheHeaders(false)
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
    val settingsRepository = SettingsRepository(appContext)
    val savedMoviesRepository = SavedMoviesRepository(appContext)
    val launcher = Launcher(appContext, appsRepository)
}
