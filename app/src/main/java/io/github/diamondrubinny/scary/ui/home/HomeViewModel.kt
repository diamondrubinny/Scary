// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary.ui.home

import io.github.diamondrubinny.scary.core.links.StreamingApps
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
import io.github.diamondrubinny.scary.core.cinemeta.Cinemeta
import io.github.diamondrubinny.scary.core.cinemeta.CinemetaMeta
import io.github.diamondrubinny.scary.core.links.KnownPackages
import io.github.diamondrubinny.scary.core.catalog.FamilyNight
import io.github.diamondrubinny.scary.core.security.ParentalGate
import io.github.diamondrubinny.scary.core.catalog.Mood
import io.github.diamondrubinny.scary.core.catalog.Season
import io.github.diamondrubinny.scary.core.model.Catalog
import io.github.diamondrubinny.scary.core.model.Language
import io.github.diamondrubinny.scary.core.model.Movie
import io.github.diamondrubinny.scary.core.security.PinHasher
import io.github.diamondrubinny.scary.core.security.PinLockout
import io.github.diamondrubinny.scary.data.CatalogRepository
import io.github.diamondrubinny.scary.data.CinemetaRepository
import io.github.diamondrubinny.scary.data.InstalledApp
import io.github.diamondrubinny.scary.data.InstalledAppsRepository
import io.github.diamondrubinny.scary.data.LauncherPrefs
import io.github.diamondrubinny.scary.data.SavedMoviesRepository
import io.github.diamondrubinny.scary.data.SettingsRepository
import io.github.diamondrubinny.scary.data.TmdbKeyResult
import io.github.diamondrubinny.scary.data.TmdbRepository
import io.github.diamondrubinny.scary.launch.LaunchResult
import io.github.diamondrubinny.scary.launch.Launcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.time.LocalDate
import kotlin.random.Random

class HomeViewModel(
    private val catalogRepository: CatalogRepository,
    private val appsRepository: InstalledAppsRepository,
    private val settings: SettingsRepository,
    private val launcher: Launcher,
    private val tmdb: TmdbRepository,
    private val savedMovies: SavedMoviesRepository,
    private val cinemeta: CinemetaRepository,
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
    private val mood = MutableStateFlow<Mood?>(null)

    /** Pages of endless online rows loaded while scrolling, by row id. In memory only; gone when SCARY restarts. */
    private val extraPages = MutableStateFlow<Map<String, List<CinemetaMeta>>>(emptyMap())
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

    private val _scrollToRow = MutableStateFlow<String?>(null)

    /** A home row to bring into view, then focus [lastFocusedKey] in it. Set only by debug intents. */
    val scrollToRow: StateFlow<String?> = _scrollToRow.asStateFlow()

    /** Debug builds only (via MainActivity): shows [rowKey] with the card [focusKey] focused, for screenshots. */
    fun debugShowRow(rowKey: String, focusKey: String?) {
        lastFocusedKey = focusKey
        _scrollToRow.value = rowKey
    }

    fun onScrolledToRow() {
        _scrollToRow.value = null
    }

    val message: StateFlow<UserMessage?> = _message.asStateFlow()
    val homeResets: StateFlow<Int> = _homeResets.asStateFlow()

    val screen: StateFlow<Screen> = backStack
        .map { it.last() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, Screen.Home)

    /**
     * The catalog as shown: online pages loaded while scrolling merged in, filtered for family night. Rebuilt only when
     * one of those changes, and off the main thread: with endless rows it can hold thousands of movies.
     */
    private val shownCatalog: StateFlow<CatalogState> = combine(
        catalog,
        extraPages,
        settings.prefs.map { it.onlineEnabled to it.familyNight }.distinctUntilChanged(),
    ) { state, extras, (online, family) ->
        if (state is CatalogState.Ready) CatalogState.Ready(shown(state.catalog, extras, online, family)) else state
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, CatalogState.Loading)

    /** Everything on the home screen except the hero; moving between cards does not rebuild it. */
    private val homeBase: Flow<HomeUiState> = combine(
        shownCatalog,
        installedApps,
        mood,
        settings.prefs,
        sessionUnlocked,
    ) { catalogState, apps, selectedMood, prefs, unlocked ->
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
            is CatalogState.Ready -> buildState(catalogState.catalog, tiles, drawer, selectedMood, prefs)
        }.copy(
            parentalEnabled = prefs.parentalEnabled,
            lockFromFear = prefs.lockFromFear,
            sessionUnlocked = unlocked,
            soundsEnabled = prefs.soundsEnabled,
            onlineEnabled = prefs.onlineEnabled,
            myListIds = prefs.myListIds.toSet(),
            familyNight = prefs.familyNight,
            seasonalRows = prefs.seasonalRows,
        )
    }.flowOn(Dispatchers.Default)

    /** The home screen: the base above, with the focused card's movie as the hero (tonight's pick otherwise). */
    val uiState: StateFlow<HomeUiState> = combine(homeBase, heroId) { base, hero ->
        val focused = hero?.let { id -> (shownCatalog.value as? CatalogState.Ready)?.catalog?.movie(id) }
        if (focused == null || base.loading) base else base.copy(hero = focused)
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
            savedMovies.load()
            val online = settings.prefs.map { it.onlineEnabled }.distinctUntilChanged()
            catalogRepository.catalog(online, tmdb.keyMask.map { it != null }, language)
                .combine(savedMovies.items) { catalog, saved -> catalog to saved }
                .combine(online) { (catalog, saved), isOnline -> withSaved(catalog, saved, isOnline) }
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

    /**
     * Adds movies the viewer kept from online rows or web search, so My list and Recently opened keep them after
     * they leave the popular rows. With online data off their image URLs are dropped, so nothing loads from the web.
     */
    private fun withSaved(catalog: Catalog, saved: List<CinemetaMeta>, online: Boolean): Catalog {
        if (saved.isEmpty()) return catalog
        val known = catalog.movies.mapTo(HashSet()) { it.imdbId }
        val extra = saved.asSequence()
            .filter { it.imdbId !in known }
            .mapNotNull(Cinemeta::toMovie)
            .map { if (online) it else it.copy(posterUrl = null, backgroundUrl = null, logoUrl = null) }
            .toList()
        return if (extra.isEmpty()) catalog else catalog.copy(movies = catalog.movies + extra)
    }

    fun refreshApps() {
        if (appsJob?.isActive == true) return
        appsJob = viewModelScope.launch {
            val apps = appsRepository.load()
            installedApps.value = apps
            _watchOptions.value = withContext(Dispatchers.IO) {
                apps.mapNotNull { app ->
                    StreamingApps.match(app.packageName, app.label)?.let {
                        WatchOption(app.packageName, app.label, launcher.canSearchIn(app.packageName))
                    }
                }.sortedBy { it.label.lowercase() }
            }
            _canSearchEverywhere.value = withContext(Dispatchers.IO) { launcher.canSearchEverywhere() }
        }
    }

    private val _watchOptions = MutableStateFlow<List<WatchOption>>(emptyList())

    /** Streaming apps on this TV, for "Search in …" on movie pages. Refreshed with the app list. */
    val watchOptions: StateFlow<List<WatchOption>> = _watchOptions.asStateFlow()

    private val _canSearchEverywhere = MutableStateFlow(false)

    /** Whether this TV has a system-wide search (Google TV) that lists where a movie is. */
    val canSearchEverywhere: StateFlow<Boolean> = _canSearchEverywhere.asStateFlow()

    fun searchIn(movie: Movie, app: WatchOption) = guard(movie, GatedAction.SearchIn(movie, app))

    fun searchEverywhere(movie: Movie) = guard(movie, GatedAction.SearchEverywhere(movie))

    private fun buildState(
        catalog: Catalog,
        tiles: List<AppTile>,
        drawer: List<DrawerApp>,
        selectedMood: Mood?,
        prefs: LauncherPrefs,
    ): HomeUiState {
        val nightPick = NightPick.pick(catalog.movies, today(), prefs.nightMaxFear)
        val rows = buildList {
            val recent = prefs.recentIds.mapNotNull(catalog::movie)
            if (recent.isNotEmpty()) add(HomeRow.Recent(recent))
            add(HomeRow.Apps(tiles))
            if (prefs.seasonalRows) Season.activeOn(today()).forEach { season ->
                val movies = season.pick(catalog.movies)
                if (movies.isNotEmpty()) add(HomeRow.Seasonal(season, movies))
            }
            val myList = prefs.myListIds.mapNotNull(catalog::movie)
            if (myList.isNotEmpty()) add(HomeRow.MyList(myList))
            add(HomeRow.Moods(selectedMood, selectedMood?.pick(catalog.movies).orEmpty()))
            catalog.collections.forEach { collection ->
                // Family night can empty a row; an empty row is not shown.
                val movies = catalog.moviesOf(collection)
                if (movies.isNotEmpty()) add(HomeRow.Collection(collection, movies))
            }
        }
        return HomeUiState(
            loading = false,
            nightPick = nightPick,
            hero = nightPick,
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
        val shown = visibleCatalog() ?: return null
        val movie = shown.movie(movieId) ?: webMovieIfAllowed(movieId) ?: return null
        return MovieDetails(movie, Similar.find(shown.movies, movie))
    }

    /** The catalog as the viewer may see it: with pages loaded while scrolling, and on family night only gentle movies. */
    private fun visibleCatalog(): Catalog? = (shownCatalog.value as? CatalogState.Ready)?.catalog

    /** The catalog with the pages loaded while scrolling, then filtered for family night. */
    private fun shown(catalog: Catalog, extras: Map<String, List<CinemetaMeta>>, online: Boolean, familyNight: Boolean): Catalog {
        val extended = if (!online) {
            catalog
        } else {
            extras.entries.fold(catalog) { acc, (rowId, metas) -> Cinemeta.extend(acc, Cinemeta.onlineCollectionId(rowId), metas) }
        }
        return if (familyNight) FamilyNight.filter(extended) else extended
    }

    private class RowPaging(var nextSkip: Int, var loading: Boolean = false, var done: Boolean = false)

    private val paging = HashMap<String, RowPaging>()

    /**
     * The viewer is near the end of a home row: an endless online row loads its next page (50 movies). Pages without a
     * single movie the row keeps (a year page of comedies) are skipped, a few at a time. Offline, nothing happens, and
     * the next scroll tries again.
     */
    fun onNearRowEnd(rowKey: String) {
        val rowId = rowKey.removePrefix("collection:" + Cinemeta.onlineCollectionId(""))
        if (rowId == rowKey || latestPrefs?.onlineEnabled == false || latestPrefs?.familyNight == true) return
        val start = cinemeta.endlessStart(rowId) ?: return
        val state = paging.getOrPut(rowId) { RowPaging(start) }
        if (state.loading || state.done) return
        if (extraPages.value[rowId].orEmpty().size >= Cinemeta.MAX_ENDLESS_ROW_MOVIES) {
            state.done = true
            return
        }
        state.loading = true
        viewModelScope.launch {
            try {
                var found = emptyList<CinemetaMeta>()
                var attempts = 0
                while (found.isEmpty() && attempts < MAX_EMPTY_PAGES) {
                    val page = cinemeta.loadPage(rowId, state.nextSkip) ?: return@launch
                    attempts++
                    if (page.rawSize == 0) {
                        state.done = true
                        break
                    }
                    state.nextSkip += Cinemeta.PAGE_SIZE
                    found = page.movies
                }
                if (found.isNotEmpty()) {
                    extraPages.update { pages ->
                        val merged = (pages[rowId].orEmpty() + found).distinctBy { it.imdbId }.take(Cinemeta.MAX_ENDLESS_ROW_MOVIES)
                        pages + (rowId to merged)
                    }
                }
            } finally {
                state.loading = false
            }
        }
    }

    /** Movies from web search open only outside family night (they have no fear level to check). */
    private fun webMovieIfAllowed(id: String): Movie? =
        if (latestPrefs?.familyNight == true) null else webMovies[id]

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
        scheduleWebSearch(_searchQuery.value)
    }

    private val _webSearch = MutableStateFlow(WebSearchState())

    /** Results from Cinemeta's search for the current query, shown under the library results. */
    val webSearch: StateFlow<WebSearchState> = _webSearch.asStateFlow()

    /** Movies from the latest web search, so their pages open even though they are not in the catalog. */
    private var webMovies: Map<String, Movie> = emptyMap()
    private var webJob: Job? = null

    private fun scheduleWebSearch(query: String) {
        val q = query.trim()
        // The same query again (focus moving around, coming back from a movie) keeps the search already done.
        if (_webSearch.value.query == q && !_webSearch.value.failed && q.isNotEmpty()) return
        webJob?.cancel()
        val online = latestPrefs?.onlineEnabled ?: false
        val family = latestPrefs?.familyNight ?: false
        if (!online || family || Cinemeta.searchUrl(q) == null) {
            _webSearch.value = WebSearchState()
            return
        }
        _webSearch.value = WebSearchState(query = q, loading = true)
        webJob = viewModelScope.launch {
            // Wait for the typing to settle, so one search goes out per query rather than per letter.
            delay(WEB_SEARCH_DELAY_MS)
            val known = (catalog.value as? CatalogState.Ready)?.catalog ?: return@launch
            val results = cinemeta.search(q, known)
            if (results != null) webMovies = webMovies + results.associateBy { it.id }
            _webSearch.value = if (results == null) WebSearchState(q, failed = true) else WebSearchState(q, results = results)
        }
    }

    /** "Surprise me": a random curated movie within tonight's fear level that the parental lock allows. */
    fun surprise() {
        val shown = visibleCatalog() ?: return
        val prefs = latestPrefs ?: return
        val pick = NightPick.surprise(
            movies = shown.movies,
            maxFear = prefs.nightMaxFear,
            random = Random.Default,
            exclude = heroId.value,
            isLocked = { prefs.parentalEnabled && !isSessionUnlocked() && it.fearForLock >= prefs.lockFromFear },
        )
        if (pick == null) show(R.string.msg_surprise_none) else openDetails(pick.id)
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

    private val _launching = MutableStateFlow<LaunchingState?>(null)

    /** Set while the shiver screen shows, just before another app opens a movie. */
    val launching: StateFlow<LaunchingState?> = _launching.asStateFlow()
    private var launchJob: Job? = null

    /**
     * Shows the movie's title shivering for a moment, then opens it in [appLabel]. Shorter and still when motion is
     * reduced. Home cancels it; the screen clears once the other app is in front.
     */
    private fun launchWithShiver(movie: Movie, appLabel: String, open: () -> LaunchResult) {
        launchJob?.cancel()
        val generation = dialogGeneration
        _launching.value = LaunchingState(movie, appLabel)
        val motion = latestPrefs?.effectsEnabled != false && settings.systemAnimationsOn()
        launchJob = viewModelScope.launch {
            delay(if (motion) LAUNCH_SHIVER_MS else LAUNCH_STILL_MS)
            if (generation != dialogGeneration) return@launch
            report(open())
            // Kept a little longer, so the dark screen covers the moment the other app takes to appear.
            delay(LAUNCH_CLEAR_MS)
            _launching.value = null
        }
    }

    private fun cancelLaunch() {
        launchJob?.cancel()
        launchJob = null
        _launching.value = null
    }

    fun onHomePressed() {
        // Home always lands on a clean home screen: no dialog left floating over it.
        dialogGeneration++
        cancelLaunch()
        _pinDialog.value = null
        _tmdbDialog.value = null
        backStack.value = listOf(Screen.Home)
        heroId.value = null
        lastFocusedKey = null
        _homeResets.update { it + 1 }
    }

    /** Adds or removes a movie from "My list" and says which happened. Not PIN-gated: it opens nothing. */
    fun toggleMyList(movie: Movie, language: Language) {
        viewModelScope.launch {
            val added = settings.toggleMyList(movie.id)
            if (added) savedMovies.save(movie, language)
            show(if (added) R.string.msg_my_list_added else R.string.msg_my_list_removed, movie.displayTitle(language))
        }
    }

    fun watch(movie: Movie) = guard(movie, GatedAction.Watch(movie))

    fun trailer(movie: Movie) = guard(movie, GatedAction.Trailer(movie))

    private fun movieById(id: String): Movie? = visibleCatalog()?.movie(id) ?: webMovieIfAllowed(id)

    /** Runs [action] now, or asks for the PIN first when the parental lock covers it. */
    private fun guard(movie: Movie?, action: GatedAction) {
        val prefs = latestPrefs
        val needsPin = prefs != null &&
            ParentalGate.needsPin(prefs.parentalEnabled, isSessionUnlocked(), prefs.lockFromFear, movie)
        if (needsPin) _pinDialog.value = PinDialogState(PinStep.Verify(action)) else perform(action)
    }

    private fun perform(action: GatedAction) {
        when (action) {
            is GatedAction.Details -> push(Screen.Details(action.movieId))
            is GatedAction.Watch -> {
                viewModelScope.launch {
                    settings.pushRecent(action.movie.id)
                    savedMovies.save(action.movie, language.value)
                }
                launchWithShiver(action.movie, Launcher.STREMIO_LABEL) { launcher.openInStremio(action.movie) }
            }
            is GatedAction.Trailer -> report(launcher.openTrailer(action.movie))
            is GatedAction.SearchIn -> {
                viewModelScope.launch { settings.pushRecent(action.movie.id) }
                launchWithShiver(action.movie, action.app.label) {
                    launcher.searchIn(action.app.packageName, action.app.label, action.movie)
                }
            }
            is GatedAction.SearchEverywhere -> launchWithShiver(action.movie, GOOGLE_TV_LABEL) {
                launcher.searchEverywhere(action.movie, GOOGLE_TV_LABEL)
            }
            GatedAction.Settings -> push(Screen.Settings)
        }
    }

    private fun isSessionUnlocked(): Boolean {
        if (sessionUnlocked.value && !ParentalGate.stillUnlocked(unlockedAtMillis, System.currentTimeMillis())) {
            sessionUnlocked.value = false
        }
        return sessionUnlocked.value
    }

    /** Called when the launcher leaves the screen: the next protected action asks for the PIN again. */
    fun onBackgrounded() {
        sessionUnlocked.value = false
        // The other app is in front (or SCARY was left before it opened): coming back shows SCARY, not the shiver
        // screen, and nothing opens by surprise later.
        cancelLaunch()
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
            // The wait itself (lockedUntilMillis) is what the dialog shows, counting down.
            _pinDialog.value = dialog.copy(error = null, lockedUntilMillis = prefs.lockout.lockedUntilMillis)
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
                    error = if (next.lockedUntilMillis > 0) null else R.string.pin_error_wrong,
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

    /**
     * Turning family night on also ends any PIN unlock, so turning it off again (in Settings) asks for the PIN.
     * Without a PIN, anyone can turn it off; the setting's note says so.
     */
    fun toggleSeasonalRows() {
        viewModelScope.launch {
            settings.setSeasonalRows(!settings.prefs.first().seasonalRows)
        }
    }

    fun toggleFamilyNight() {
        viewModelScope.launch {
            val enabled = !settings.prefs.first().familyNight
            if (enabled) sessionUnlocked.value = false
            settings.setFamilyNight(enabled)
            _webSearch.value = WebSearchState()
            show(if (enabled) R.string.msg_family_on else R.string.msg_family_off)
        }
    }

    /** Picks a mood, or clears it when the same mood is chosen again. */
    fun toggleMood(selected: Mood) {
        mood.update { if (it == selected) null else selected }
    }

    /** After a crash loop: turns off the heavier parts (fog, grain, animations, sounds) and says so. */
    fun enterSafeMode() {
        viewModelScope.launch {
            settings.setEffectsEnabled(false)
            settings.setSoundsEnabled(false)
            show(R.string.msg_safe_mode)
        }
    }

    fun consumeMessage(id: Long) {
        _message.update { if (it?.id == id) null else it }
    }

    private fun report(result: LaunchResult) {
        when (result) {
            LaunchResult.Opened -> Unit
            is LaunchResult.OpenedAppOnly -> show(R.string.msg_app_only, result.appLabel, result.movieTitle)
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
        private const val LAUNCH_SHIVER_MS = 1_600L
        private const val MAX_EMPTY_PAGES = 4
        private const val GOOGLE_TV_LABEL = "Google TV"
        private const val LAUNCH_STILL_MS = 400L
        private const val LAUNCH_CLEAR_MS = 1_500L
        private const val MAX_QUERY_LENGTH = 80
        private const val WEB_SEARCH_DELAY_MS = 700L

        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                HomeViewModel(
                    container.catalogRepository,
                    container.appsRepository,
                    container.settingsRepository,
                    container.launcher,
                    container.tmdbRepository,
                    container.savedMoviesRepository,
                    container.cinemetaRepository,
                )
            }
        }
    }
}
