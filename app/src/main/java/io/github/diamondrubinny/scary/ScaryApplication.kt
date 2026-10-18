package io.github.diamondrubinny.scary

import android.app.Application
import android.content.Context
import io.github.diamondrubinny.scary.data.CatalogRepository
import io.github.diamondrubinny.scary.data.CinemetaRepository
import io.github.diamondrubinny.scary.data.InstalledAppsRepository
import io.github.diamondrubinny.scary.data.SettingsRepository
import io.github.diamondrubinny.scary.launch.Launcher

class ScaryApplication : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}

/** Manual dependency wiring. Small enough that a DI framework would add more than it saves. */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val cinemetaRepository = CinemetaRepository(appContext)
    val catalogRepository = CatalogRepository(appContext, cinemetaRepository)
    val appsRepository = InstalledAppsRepository(appContext)
    val settingsRepository = SettingsRepository(appContext)
    val launcher = Launcher(appContext, appsRepository)
}
