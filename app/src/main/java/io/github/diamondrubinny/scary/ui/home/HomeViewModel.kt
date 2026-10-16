package io.github.diamondrubinny.scary.ui.home

import android.util.Log
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.diamondrubinny.scary.AppContainer
import io.github.diamondrubinny.scary.R
import io.github.diamondrubinny.scary.core.apps.AppOrdering
import io.github.diamondrubinny.scary.core.catalog.CatalogException
import io.github.diamondrubinny.scary.core.catalog.NightPick
import io.github.diamondrubinny.scary.core.catalog.Similar
import io.github.diamondrubinny.scary.core.links.KnownPackages
import io.github.diamondrubinny.scary.core.model.Catalog
import io.github.diamondrubinny.scary.core.model.Movie
import io.github.diamondrubinny.scary.data.CatalogRepository
import io.github.diamondrubinny.scary.data.InstalledApp
import io.github.diamondrubinny.scary.data.InstalledAppsRepository
import io.github.diamondrubinny.scary.data.SettingsRepository
import io.github.diamondrubinny.scary.launch.LaunchResult
import io.github.diamondrubinny.scary.launch.Launcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException
import java.time.LocalDate

class HomeViewModel(
    private val catalogRepository: CatalogRepository,
    private val appsRepository: InstalledAppsRepository,
    private val settings: SettingsRepository,
    private val launcher: Launcher,
    private val today: () -> LocalDate = LocalDate::now,
) : ViewModel() {

    private sealed interface CatalogState {
        data object Loading : CatalogState
        data object Error : CatalogState
        data class Ready(val catalog: Catalog) : CatalogState
    }

    private val catalog = MutableStateFlow<CatalogState>(CatalogState.Loading)
    private val installedApps = MutableStateFlow<List<InstalledApp>>(emptyList())
    private val heroId = MutableStateFlow<String?>(null)

    private val backStack = MutableStateFlow<List<Screen>>(listOf(Screen.Home))
    private val _message = MutableStateFlow<UserMessage?>(null)
    private val _homeResets = MutableStateFlow(0)

    /** Key of the last focused home card, used to restore focus when returning from details. */
    var lastFocusedKey: String? = null
        private set

    val message: StateFlow<UserMessage?> = _message.asStateFlow()
    val homeResets: StateFlow<Int> = _homeResets.asStateFlow()

    val screen: StateFlow<Screen> = backStack
        .map { it.last() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, Screen.Home)

    val uiState: StateFlow<HomeUiState> = combine(
        catalog,
        installedApps,
        heroId,
        settings.effectsEnabled,
        settings.recentIds,
    ) { catalogState, apps, hero, effects, recent ->
        when (catalogState) {
            CatalogState.Loading -> HomeUiState(loading = true, effectsEnabled = effects)
            CatalogState.Error -> HomeUiState(
                loading = false,
                catalogError = true,
                rows = listOf(HomeRow.Apps(appTiles(apps))),
                effectsEnabled = effects,
            )
            is CatalogState.Ready -> buildState(catalogState.catalog, apps, hero, effects, recent)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), HomeUiState())

    private var appsJob: Job? = null
    private var nextMessageId = 0L

    init {
        loadCatalog()
        refreshApps()
    }

    private fun loadCatalog() {
        viewModelScope.launch {
            catalog.value = try {
                CatalogState.Ready(catalogRepository.load())
            } catch (e: CatalogException) {
                Log.e(TAG, "Bundled catalog is invalid: ${e.problems.take(5)}")
                CatalogState.Error
            } catch (e: IOException) {
                Log.e(TAG, "Bundled catalog could not be read", e)
                CatalogState.Error
            }
        }
    }

    fun refreshApps() {
        if (appsJob?.isActive == true) return
        appsJob = viewModelScope.launch { installedApps.value = appsRepository.load() }
    }

    private fun buildState(
        catalog: Catalog,
        apps: List<InstalledApp>,
        heroId: String?,
        effects: Boolean,
        recentIds: List<String>,
    ): HomeUiState {
        val nightPick = NightPick.pick(catalog.movies, today())
        val rows = buildList {
            val recent = recentIds.mapNotNull(catalog::movie)
            if (recent.isNotEmpty()) add(HomeRow.Recent(recent))
            add(HomeRow.Apps(appTiles(apps)))
            catalog.collections.forEach { add(HomeRow.Collection(it, catalog.moviesOf(it))) }
        }
        return HomeUiState(
            loading = false,
            nightPick = nightPick,
            hero = heroId?.let(catalog::movie) ?: nightPick,
            rows = rows,
            effectsEnabled = effects,
        )
    }

    private fun appTiles(apps: List<InstalledApp>): List<AppTile> {
        val installed = AppOrdering.order(apps, InstalledApp::packageName, InstalledApp::label, KnownPackages.PINNED)
            .map { AppTile.Installed(it) }
        val installedPackages = apps.map { it.packageName }.toSet()
        val suggestions = if (KnownPackages.STREMIO in installedPackages) {
            emptyList()
        } else {
            listOf(AppTile.Suggestion(KnownPackages.STREMIO, Launcher.STREMIO_LABEL))
        }
        return suggestions + installed + AppTile.SystemSettings
    }

    fun details(movieId: String): MovieDetails? {
        val ready = catalog.value as? CatalogState.Ready ?: return null
        val movie = ready.catalog.movie(movieId) ?: return null
        return MovieDetails(movie, Similar.find(ready.catalog.movies, movie))
    }

    fun onCardFocused(key: String, movieId: String?) {
        lastFocusedKey = key
        if (movieId != null) heroId.value = movieId
    }

    fun openDetails(movieId: String) {
        backStack.update { it + Screen.Details(movieId) }
    }

    /** Returns false when already on the home screen, so Back does nothing there. */
    fun back(): Boolean {
        if (backStack.value.size <= 1) return false
        backStack.update { it.dropLast(1) }
        return true
    }

    fun onHomePressed() {
        backStack.value = listOf(Screen.Home)
        heroId.value = null
        lastFocusedKey = null
        _homeResets.update { it + 1 }
    }

    fun watch(movie: Movie) {
        viewModelScope.launch { settings.pushRecent(movie.id) }
        report(launcher.openInStremio(movie))
    }

    fun trailer(movie: Movie) = report(launcher.openTrailer(movie))

    fun openTile(tile: AppTile, settingsLabel: String) {
        val result = when (tile) {
            is AppTile.Installed -> launcher.launchApp(tile.app.packageName, tile.app.label)
            is AppTile.Suggestion -> launcher.openStoreFor(tile.packageName, tile.label)
            AppTile.SystemSettings -> launcher.openSystemSettings(settingsLabel)
        }
        report(result)
    }

    fun toggleEffects() {
        viewModelScope.launch {
            val enabled = !settings.effectsEnabled.first()
            settings.setEffectsEnabled(enabled)
            show(if (enabled) R.string.msg_effects_on else R.string.msg_effects_off)
        }
    }

    fun consumeMessage(id: Long) {
        _message.update { if (it?.id == id) null else it }
    }

    private fun report(result: LaunchResult) {
        when (result) {
            LaunchResult.Opened -> Unit
            is LaunchResult.OpenedAppOnly -> show(R.string.msg_stremio_app_only, result.movieTitle)
            is LaunchResult.OpenedStoreInstead -> show(R.string.msg_not_installed_store, result.appLabel)
            is LaunchResult.Failed -> show(R.string.msg_open_failed, result.targetLabel)
        }
    }

    private fun show(@StringRes text: Int, vararg args: String) {
        _message.value = UserMessage(++nextMessageId, text, args.toList())
    }

    companion object {
        private const val TAG = "HomeViewModel"
        private const val STOP_TIMEOUT_MS = 5_000L

        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                HomeViewModel(
                    container.catalogRepository,
                    container.appsRepository,
                    container.settingsRepository,
                    container.launcher,
                )
            }
        }
    }
}
