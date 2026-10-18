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
import io.github.diamondrubinny.scary.core.model.Language
import io.github.diamondrubinny.scary.core.model.Movie
import io.github.diamondrubinny.scary.core.security.PinHasher
import io.github.diamondrubinny.scary.core.security.PinLockout
import io.github.diamondrubinny.scary.data.CatalogRepository
import io.github.diamondrubinny.scary.data.InstalledApp
import io.github.diamondrubinny.scary.data.InstalledAppsRepository
import io.github.diamondrubinny.scary.data.LauncherPrefs
import io.github.diamondrubinny.scary.data.SettingsRepository
import io.github.diamondrubinny.scary.data.TmdbKeyResult
import io.github.diamondrubinny.scary.data.TmdbRepository
import io.github.diamondrubinny.scary.launch.LaunchResult
import io.github.diamondrubinny.scary.launch.Launcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.time.LocalDate

class HomeViewModel(
    private val catalogRepository: CatalogRepository,
    private val appsRepository: InstalledAppsRepository,
    private val settings: SettingsRepository,
    private val launcher: Launcher,
    private val tmdb: TmdbRepository,
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
    private val language = MutableStateFlow(Language.HE)

    /** The UI language; online text (TMDB) is fetched in it. */
    fun setLanguage(value: Language) {
        language.value = value
    }
    private val sessionUnlocked = MutableStateFlow(false)
    private var unlockedAtMillis = 0L
    private var latestPrefs: LauncherPrefs? = null
    private val _pinDialog = MutableStateFlow<PinDialogState?>(null)
    val pinDialog: StateFlow<PinDialogState?> = _pinDialog.asStateFlow()

    private val backStack = MutableStateFlow<List<Screen>>(listOf(Screen.Home))
    private val _message = MutableStateFlow<UserMessage?>(null)
    private val _homeResets = MutableStateFlow(0)

    /** Key of the last focused home card, used to restore focus when returning from another screen. */
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
        settings.prefs,
        sessionUnlocked,
    ) { catalogState, apps, hero, prefs, unlocked ->
        val tiles = appTiles(apps, prefs)
        val drawer = drawerApps(apps, prefs)
        when (catalogState) {
            CatalogState.Loading -> HomeUiState(loading = true, effectsEnabled = prefs.effectsEnabled)
            CatalogState.Error -> HomeUiState(
                loading = false,
                catalogError = true,
                rows = listOf(HomeRow.Apps(tiles)),
                effectsEnabled = prefs.effectsEnabled,
                drawer = drawer,
                nightMaxFear = prefs.nightMaxFear,
            )
            is CatalogState.Ready -> buildState(catalogState.catalog, tiles, drawer, hero, prefs)
        }.copy(
            parentalEnabled = prefs.parentalEnabled,
            lockFromFear = prefs.lockFromFear,
            sessionUnlocked = unlocked,
            soundsEnabled = prefs.soundsEnabled,
            onlineEnabled = prefs.onlineEnabled,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), HomeUiState())

    private var appsJob: Job? = null
    private var nextMessageId = 0L

    init {
        loadCatalog()
        refreshApps()
        viewModelScope.launch { settings.prefs.collect { latestPrefs = it } }
    }

    private fun loadCatalog() {
        viewModelScope.launch {
            tmdb.load()
            catalogRepository.catalog(settings.prefs.map { it.onlineEnabled }, tmdb.keyMask.map { it != null }, language)
                .catch { e ->
                    when (e) {
                        is CatalogException -> Log.e(TAG, "Bundled catalog is invalid: ${e.problems.take(5)}")
                        is IOException -> Log.e(TAG, "Bundled catalog could not be read", e)
                        else -> throw e
                    }
                    catalog.value = CatalogState.Error
                }
                .collect { catalog.value = CatalogState.Ready(it) }
        }
    }

    fun refreshApps() {
        if (appsJob?.isActive == true) return
        appsJob = viewModelScope.launch { installedApps.value = appsRepository.load() }
    }

    private fun buildState(
        catalog: Catalog,
        tiles: List<AppTile>,
        drawer: List<DrawerApp>,
        heroId: String?,
        prefs: LauncherPrefs,
    ): HomeUiState {
        val nightPick = NightPick.pick(catalog.movies, today(), prefs.nightMaxFear)
        val rows = buildList {
            val recent = prefs.recentIds.mapNotNull(catalog::movie)
            if (recent.isNotEmpty()) add(HomeRow.Recent(recent))
            add(HomeRow.Apps(tiles))
            catalog.collections.forEach { add(HomeRow.Collection(it, catalog.moviesOf(it))) }
        }
        return HomeUiState(
            loading = false,
            nightPick = nightPick,
            hero = heroId?.let(catalog::movie) ?: nightPick,
            rows = rows,
            effectsEnabled = prefs.effectsEnabled,
            drawer = drawer,
            nightMaxFear = prefs.nightMaxFear,
            movies = catalog.movies,
        )
    }

    private fun appTiles(apps: List<InstalledApp>, prefs: LauncherPrefs): List<AppTile> {
        val installed = AppOrdering.order(apps, InstalledApp::packageName, InstalledApp::label, prefs.pinned, prefs.hidden)
            .map { AppTile.Installed(it) }
        val stremioInstalled = apps.any { it.packageName == KnownPackages.STREMIO }
        val suggestions = if (stremioInstalled) {
            emptyList()
        } else {
            listOf(AppTile.Suggestion(KnownPackages.STREMIO, Launcher.STREMIO_LABEL))
        }
        return suggestions + installed + AppTile.AllApps + AppTile.SystemSettings
    }

    private fun drawerApps(apps: List<InstalledApp>, prefs: LauncherPrefs): List<DrawerApp> =
        AppOrdering.order(apps, InstalledApp::packageName, InstalledApp::label, prefs.pinned)
            .map { DrawerApp(it, pinned = it.packageName in prefs.pinned, hidden = it.packageName in prefs.hidden) }

    fun details(movieId: String): MovieDetails? {
        val ready = catalog.value as? CatalogState.Ready ?: return null
        val movie = ready.catalog.movie(movieId) ?: return null
        return MovieDetails(movie, Similar.find(ready.catalog.movies, movie))
    }

    fun onCardFocused(key: String, movieId: String?) {
        lastFocusedKey = key
        if (movieId != null) heroId.value = movieId
    }

    fun openDetails(movieId: String) = guard(movieById(movieId), GatedAction.Details(movieId))

    fun openAllApps() = push(Screen.AllApps)

    fun openSettings() = guard(null, GatedAction.Settings)

    fun openSearch() = push(Screen.Search)

    fun openDocument(kind: DocumentKind) = push(Screen.Document(kind))

    private val _searchQuery = MutableStateFlow("")

    /** Kept here so the query survives a trip to a movie's details and back. */
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    fun setSearchQuery(text: String) {
        _searchQuery.value = text.take(MAX_QUERY_LENGTH)
    }

    private fun push(screen: Screen) {
        backStack.update { if (it.last() == screen) it else it + screen }
    }

    /** Returns false when already on the home screen, so Back does nothing there. */
    fun back(): Boolean {
        if (backStack.value.size <= 1) return false
        backStack.update { it.dropLast(1) }
        return true
    }

    /** Bumped whenever dialogs are closed, so PIN or TMDB work still running cannot act afterwards. */
    private var dialogGeneration = 0L

    fun onHomePressed() {
        // Home always lands on a clean home screen: no dialog left floating over it.
        dialogGeneration++
        _pinDialog.value = null
        _tmdbDialog.value = null
        backStack.value = listOf(Screen.Home)
        heroId.value = null
        lastFocusedKey = null
        _homeResets.update { it + 1 }
    }

    fun watch(movie: Movie) = guard(movie, GatedAction.Watch(movie))

    fun trailer(movie: Movie) = guard(movie, GatedAction.Trailer(movie))

    private fun movieById(id: String): Movie? = (catalog.value as? CatalogState.Ready)?.catalog?.movie(id)

    /** Runs [action] now, or asks for the PIN first when the parental lock covers it. */
    private fun guard(movie: Movie?, action: GatedAction) {
        val prefs = latestPrefs
        val needsPin = prefs != null && prefs.parentalEnabled && !isSessionUnlocked() &&
            (movie == null || movie.fearForLock >= prefs.lockFromFear)
        if (needsPin) _pinDialog.value = PinDialogState(PinStep.Verify(action)) else perform(action)
    }

    private fun perform(action: GatedAction) {
        when (action) {
            is GatedAction.Details -> push(Screen.Details(action.movieId))
            is GatedAction.Watch -> {
                viewModelScope.launch { settings.pushRecent(action.movie.id) }
                report(launcher.openInStremio(action.movie))
            }
            is GatedAction.Trailer -> report(launcher.openTrailer(action.movie))
            GatedAction.Settings -> push(Screen.Settings)
        }
    }

    private fun isSessionUnlocked(): Boolean {
        if (sessionUnlocked.value && System.currentTimeMillis() - unlockedAtMillis > UNLOCK_WINDOW_MS) {
            sessionUnlocked.value = false
        }
        return sessionUnlocked.value
    }

    /** Called when the launcher leaves the screen: the next protected action asks for the PIN again. */
    fun onBackgrounded() {
        sessionUnlocked.value = false
    }

    fun startPinSetup() {
        _pinDialog.value = PinDialogState(PinStep.Create)
    }

    fun dismissPin() {
        dialogGeneration++
        _pinDialog.value = null
    }

    fun submitPin(pin: String) {
        val dialog = _pinDialog.value ?: return
        if (dialog.busy) return
        when (val step = dialog.step) {
            PinStep.Create -> _pinDialog.value = if (PinHasher.isValidFormat(pin)) {
                PinDialogState(PinStep.Confirm(pin))
            } else {
                dialog.copy(error = R.string.pin_error_format)
            }
            is PinStep.Confirm -> {
                if (pin != step.firstEntry) {
                    _pinDialog.value = PinDialogState(PinStep.Create, error = R.string.pin_error_mismatch)
                    return
                }
                _pinDialog.value = dialog.copy(busy = true)
                val generation = dialogGeneration
                viewModelScope.launch {
                    val hash = withContext(Dispatchers.Default) { PinHasher.hash(pin) }
                    if (generation != dialogGeneration) return@launch
                    settings.setPin(hash)
                    unlockSession()
                    _pinDialog.value = null
                    show(R.string.msg_pin_set)
                }
            }
            is PinStep.Verify -> verify(pin, step.action, dialog)
        }
    }

    private fun verify(pin: String, action: GatedAction, dialog: PinDialogState) {
        val prefs = latestPrefs ?: return
        val stored = prefs.pinHash
        if (stored == null) {
            // The lock was turned off while the dialog was open.
            _pinDialog.value = null
            perform(action)
            return
        }
        val now = System.currentTimeMillis()
        if (PinLockout.remainingWaitMillis(prefs.lockout, now) > 0) {
            _pinDialog.value = dialog.copy(error = R.string.pin_error_wait, lockedUntilMillis = prefs.lockout.lockedUntilMillis)
            return
        }
        _pinDialog.value = dialog.copy(busy = true, error = null)
        val generation = dialogGeneration
        viewModelScope.launch {
            val ok = withContext(Dispatchers.Default) { PinHasher.verify(pin, stored) }
            // A wrong PIN still counts toward the lockout even if the dialog was closed meanwhile.
            if (ok && generation != dialogGeneration) return@launch
            if (ok) {
                settings.setLockout(PinLockout.onSuccess())
                unlockSession()
                _pinDialog.value = null
                perform(action)
            } else {
                val next = PinLockout.onFailure(prefs.lockout, System.currentTimeMillis())
                settings.setLockout(next)
                if (generation != dialogGeneration) return@launch
                _pinDialog.value = dialog.copy(
                    busy = false,
                    error = if (next.lockedUntilMillis > 0) R.string.pin_error_wait else R.string.pin_error_wrong,
                    lockedUntilMillis = next.lockedUntilMillis,
                )
            }
        }
    }

    private fun unlockSession() {
        unlockedAtMillis = System.currentTimeMillis()
        sessionUnlocked.value = true
    }

    fun turnOffParental() {
        viewModelScope.launch {
            settings.clearPin()
            show(R.string.msg_pin_removed)
        }
    }

    fun cycleLockLevel() {
        val current = latestPrefs?.lockFromFear ?: SettingsRepository.DEFAULT_LOCK_FROM_FEAR
        viewModelScope.launch { settings.setLockFromFear(current % SettingsRepository.MAX_FEAR + 1) }
    }

    /** "…a1b2" when a TMDB key is saved, null otherwise. */
    val tmdbKeyMask: StateFlow<String?> = tmdb.keyMask

    private val _tmdbDialog = MutableStateFlow<TmdbDialogState?>(null)
    val tmdbDialog: StateFlow<TmdbDialogState?> = _tmdbDialog.asStateFlow()

    fun openTmdbDialog() {
        _tmdbDialog.value = TmdbDialogState()
    }

    fun dismissTmdbDialog() {
        dialogGeneration++
        _tmdbDialog.value = null
    }

    fun submitTmdbKey(input: String) {
        if (_tmdbDialog.value?.busy == true) return
        _tmdbDialog.value = TmdbDialogState(busy = true)
        val generation = dialogGeneration
        viewModelScope.launch {
            val result = tmdb.setKey(input)
            // A key that was verified is kept; only the dialog update is skipped if it was closed meanwhile.
            if (generation != dialogGeneration) return@launch
            if (result == TmdbKeyResult.SAVED) {
                _tmdbDialog.value = null
                show(R.string.msg_tmdb_saved)
            } else {
                _tmdbDialog.value = TmdbDialogState(result = result)
            }
        }
    }

    fun clearTmdbKey() {
        viewModelScope.launch {
            tmdb.clearKey()
            _tmdbDialog.value = null
            show(R.string.msg_tmdb_deleted)
        }
    }

    fun toggleOnline() {
        val enabled = !(latestPrefs?.onlineEnabled ?: true)
        viewModelScope.launch { settings.setOnlineEnabled(enabled) }
    }

    private val _launcherStatus = MutableStateFlow(LauncherStatus(isDefaultHome = true, homeButtonServiceOn = false))

    /** Whether SCARY is the home screen, and whether the Home button service is on. Refreshed on every resume. */
    val launcherStatus: StateFlow<LauncherStatus> = _launcherStatus.asStateFlow()

    fun updateLauncherStatus(status: LauncherStatus) {
        _launcherStatus.value = status
    }

    fun showMessage(@StringRes text: Int) = show(text)

    fun toggleSounds() {
        val enabled = !(latestPrefs?.soundsEnabled ?: false)
        viewModelScope.launch { settings.setSoundsEnabled(enabled) }
    }

    fun openTile(tile: AppTile, settingsLabel: String) {
        when (tile) {
            is AppTile.Installed -> report(launcher.launchApp(tile.app.packageName, tile.app.label))
            is AppTile.Suggestion -> report(launcher.openStoreFor(tile.packageName, tile.label))
            AppTile.AllApps -> openAllApps()
            AppTile.SetAsHome -> Unit // Needs an Activity result; ScaryRoot handles it.
            AppTile.SystemSettings -> report(launcher.openSystemSettings(settingsLabel))
        }
    }

    fun launchApp(app: InstalledApp) = report(launcher.launchApp(app.packageName, app.label))

    fun openAppInfo(app: InstalledApp) = report(launcher.openAppInfo(app.packageName, app.label))

    fun togglePinned(app: InstalledApp) {
        viewModelScope.launch { settings.togglePinned(app.packageName) }
    }

    fun movePinned(app: InstalledApp, delta: Int) {
        viewModelScope.launch { settings.movePinned(app.packageName, delta) }
    }

    fun toggleHidden(app: InstalledApp) {
        viewModelScope.launch { settings.toggleHidden(app.packageName) }
    }

    fun setNightMaxFear(level: Int) {
        viewModelScope.launch { settings.setNightMaxFear(level) }
    }

    fun toggleEffects() {
        viewModelScope.launch {
            val enabled = !settings.prefs.first().effectsEnabled
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
        private const val MAX_QUERY_LENGTH = 80
        private const val UNLOCK_WINDOW_MS = 30 * 60_000L

        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                HomeViewModel(
                    container.catalogRepository,
                    container.appsRepository,
                    container.settingsRepository,
                    container.launcher,
                    container.tmdbRepository,
                )
            }
        }
    }
}
