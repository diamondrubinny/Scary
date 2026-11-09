// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

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
import io.github.diamondrubinny.scary.ai.AiAssistant
import io.github.diamondrubinny.scary.ai.AiChip
import io.github.diamondrubinny.scary.ai.AiContext
import io.github.diamondrubinny.scary.core.ai.AiProvider
import io.github.diamondrubinny.scary.core.apps.AppOrdering
import io.github.diamondrubinny.scary.core.catalog.AutoWatched
import io.github.diamondrubinny.scary.core.catalog.BucketList
import io.github.diamondrubinny.scary.core.catalog.BucketReturn
import io.github.diamondrubinny.scary.core.catalog.CatalogException
import io.github.diamondrubinny.scary.core.catalog.Categories
import io.github.diamondrubinny.scary.core.catalog.Decades
import io.github.diamondrubinny.scary.core.catalog.FamilyNight
import io.github.diamondrubinny.scary.core.catalog.FearStats
import io.github.diamondrubinny.scary.core.catalog.Mood
import io.github.diamondrubinny.scary.core.catalog.NightPick
import io.github.diamondrubinny.scary.core.catalog.PendingWatch
import io.github.diamondrubinny.scary.core.catalog.Personalize
import io.github.diamondrubinny.scary.core.catalog.Recommender
import io.github.diamondrubinny.scary.core.catalog.Search
import io.github.diamondrubinny.scary.core.catalog.Season
import io.github.diamondrubinny.scary.core.catalog.Similar
import io.github.diamondrubinny.scary.core.catalog.TimeBudget
import io.github.diamondrubinny.scary.core.catalog.TimePlan
import io.github.diamondrubinny.scary.core.cinemeta.Cinemeta
import io.github.diamondrubinny.scary.core.cinemeta.CinemetaMeta
import io.github.diamondrubinny.scary.core.links.KnownPackages
import io.github.diamondrubinny.scary.core.links.ResolvedCountry
import io.github.diamondrubinny.scary.core.links.StreamingApps
import io.github.diamondrubinny.scary.core.model.Catalog
import io.github.diamondrubinny.scary.core.model.Language
import io.github.diamondrubinny.scary.core.model.MediaKind
import io.github.diamondrubinny.scary.core.model.Movie
import io.github.diamondrubinny.scary.core.realdebrid.RealDebrid
import io.github.diamondrubinny.scary.core.security.ParentalGate
import io.github.diamondrubinny.scary.core.security.PinHasher
import io.github.diamondrubinny.scary.core.security.PinLockout
import io.github.diamondrubinny.scary.core.tmdb.RegionOffers
import io.github.diamondrubinny.scary.core.tmdb.WatchProvider
import io.github.diamondrubinny.scary.data.AiRepository
import io.github.diamondrubinny.scary.data.CatalogRepository
import io.github.diamondrubinny.scary.data.CinemetaRepository
import io.github.diamondrubinny.scary.data.InstalledApp
import io.github.diamondrubinny.scary.data.InstalledAppsRepository
import io.github.diamondrubinny.scary.data.LauncherPrefs
import io.github.diamondrubinny.scary.data.OnlinePage
import io.github.diamondrubinny.scary.data.Rating
import io.github.diamondrubinny.scary.data.RealDebridRepository
import io.github.diamondrubinny.scary.data.WatchStore
import io.github.diamondrubinny.scary.core.player.WatchHistory
import io.github.diamondrubinny.scary.core.player.WatchEntry
import io.github.diamondrubinny.scary.data.RegionDetector
import io.github.diamondrubinny.scary.data.SavedMoviesRepository
import io.github.diamondrubinny.scary.data.SettingsRepository
import io.github.diamondrubinny.scary.data.TmdbAccountRepository
import io.github.diamondrubinny.scary.data.TmdbKeyResult
import io.github.diamondrubinny.scary.data.TmdbRepository
import io.github.diamondrubinny.scary.data.TvInput
import io.github.diamondrubinny.scary.data.TvInputsRepository
import io.github.diamondrubinny.scary.launch.LaunchResult
import io.github.diamondrubinny.scary.launch.Launcher
import io.github.diamondrubinny.scary.remote.PhoneRemoteServer
import java.io.IOException
import java.time.LocalDate
import kotlin.random.Random
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

class HomeViewModel(
    private val catalogRepository: CatalogRepository,
    private val appsRepository: InstalledAppsRepository,
    private val settings: SettingsRepository,
    private val launcher: Launcher,
    private val tmdb: TmdbRepository,
    private val savedMovies: SavedMoviesRepository,
    private val cinemeta: CinemetaRepository,
    private val regionDetector: RegionDetector,
    private val tvInputs: TvInputsRepository,
    /** Signals that the TV's network is back, to fetch online rows that failed or are old. */
    private val networkBack: Flow<Unit> = emptyFlow(),
    /** The viewer's TMDB account, when they sign in; null in tests that do not need it. */
    private val tmdbAccount: TmdbAccountRepository? = null,
    /** AI services (Pollinations' free one by default, or the viewer's own keys); null in tests that do not need it. */
    private val aiRepository: AiRepository? = null,
    /** The viewer's own Real-Debrid account, for the internal player; null in tests that do not need it. */
    private val realDebrid: RealDebridRepository? = null,
    private val watchStore: WatchStore? = null,
    private val today: () -> LocalDate = LocalDate::now,
) : ViewModel() {

    private sealed interface CatalogState {
        data object Loading : CatalogState
        data object Error : CatalogState
        data class Ready(val catalog: Catalog) : CatalogState
    }

    private val catalog = MutableStateFlow<CatalogState>(CatalogState.Loading)
    private val installedApps = MutableStateFlow<List<InstalledApp>>(emptyList())

    /** The TV's own inputs (HDMI, antenna), refreshed with the apps; none on streaming boxes. */
    private val inputs = MutableStateFlow<List<TvInput>>(emptyList())

    /** "How much time do you have?": the chosen budget, or null. */
    private val timeBudget = MutableStateFlow<TimeBudget?>(null)
    private val heroId = MutableStateFlow<String?>(null)
    private val mood = MutableStateFlow<Mood?>(null)
    private val decade = MutableStateFlow<Int?>(null)

    /** The home screen's tab: films or series. */
    private val tab = MutableStateFlow(MediaKind.MOVIE)

    /** Picking films for the bucket list from the home rows. */
    private val picking = MutableStateFlow(false)

    /** The home screen's choices that rebuild its rows. */
    private data class HomeChoices(val mood: Mood?, val decade: Int?, val budget: TimeBudget?, val tab: MediaKind, val picking: Boolean)

    /** Pages of endless online rows loaded while scrolling, by row id. In memory only; gone when SCARY restarts. */
    private val extraPages = MutableStateFlow<Map<String, List<CinemetaMeta>>>(emptyMap())
    private val language = MutableStateFlow(Language.HE)

    /** The UI language; online text (TMDB) is fetched in it. */
    fun setLanguage(value: Language) {
        language.value = value
    }
    private val sessionUnlocked = MutableStateFlow(false)
    private var unlockedAtMillis = 0L
    private var unlockExpiry: Job? = null
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
        // The id lookup is built here, off the main thread, not at the first movie page or card that asks.
        if (state is CatalogState.Ready) CatalogState.Ready(shown(state.catalog, extras, online, family).also { it.movie("") }) else state
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, CatalogState.Loading)

    /** The internal player's watch history (resume spots and bookmarks), kept on this TV only. */
    val watchHistory: StateFlow<List<WatchEntry>> = watchStore?.entries ?: MutableStateFlow(emptyList())

    /** Everything on the home screen except the hero; moving between cards does not rebuild it. */
    private val homeBase: Flow<HomeUiState> = combine(
        shownCatalog,
        combine(installedApps, inputs, watchHistory) { a, i, w -> Triple(a, i, w) },
        combine(mood, decade, timeBudget, tab, picking) { m, d, t, k, p -> HomeChoices(m, d, t, k, p) },
        settings.prefs,
        sessionUnlocked,
    ) { catalogState, (apps, tvInputList, history), choices, prefs, unlocked ->
        val tiles = appTiles(apps, prefs, tvInputList)
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
            is CatalogState.Ready -> buildState(catalogState.catalog, tiles, drawer, choices, prefs, unlocked, history)
        }.copy(
            parentalEnabled = prefs.parentalEnabled,
            lockFromFear = prefs.lockFromFear,
            sessionUnlocked = unlocked,
            soundsEnabled = prefs.soundsEnabled,
            onlineEnabled = prefs.onlineEnabled,
            myListIds = prefs.myListIds.toSet(),
            familyNight = prefs.familyNight,
            seasonalRows = prefs.seasonalRows,
            watchedIds = prefs.watchedIds.toSet(),
            autoWatched = prefs.autoWatched,
            hideWatched = prefs.hideWatched,
            trailerAutoplay = prefs.trailerAutoplay,
            trailerSound = prefs.trailerSound,
            trailerIntroSeen = prefs.trailerIntroSeen,
            lovedIds = prefs.lovedIds.toSet(),
            dislikedIds = prefs.dislikedIds.toSet(),
            // On until SleepTimerReceiver has paused playback and cleared it (or a restart of the TV dropped the alarm).
            sleepAtMillis = prefs.sleepAtMillis,
            tab = choices.tab,
            picking = choices.picking,
            bucketIds = prefs.bucketIds,
            recentCount = prefs.recentIds.size,
            autoUpdateCheck = prefs.autoUpdateCheck,
            aiEnabled = prefs.aiEnabled && aiRepository != null,
            aiProvider = prefs.aiProvider,
            aiModels = prefs.aiModels,
            aiCustomUrl = prefs.aiCustomUrl,
            aiShareTaste = prefs.aiShareTaste,
            aiHomeRow = prefs.aiHomeRow,
        )
    }.flowOn(Dispatchers.Default)

    /** The home screen: the base above, with the focused card's movie as the hero (tonight's pick otherwise). */
    val uiState: StateFlow<HomeUiState> = combine(homeBase, heroId) { base, hero ->
        val focused = hero?.let { id -> (shownCatalog.value as? CatalogState.Ready)?.catalog?.movie(id) }
        if (focused == null || base.loading) base else base.copy(hero = focused)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), HomeUiState())

    private var appsJob: Job? = null
    private var nextMessageId = 0L

    /** "Look again": SCARY is back on screen. Online data that is missing or old is then fetched. */
    private val refreshRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    /**
     * When to check online data again: back on screen, network back, and every [REFRESH_CHECK_MS] while SCARY stays
     * in front for hours. A check fetches only what is missing, old (a day) or due for a retry ([RefreshPolicy]).
     */
    private val refreshSignals: Flow<Unit> = merge(
        refreshRequests,
        networkBack,
        flow {
            while (true) {
                delay(REFRESH_CHECK_MS)
                emit(Unit)
            }
        },
    )

    private fun loadCatalog() {
        viewModelScope.launch {
            tmdb.load()
            tmdbAccount?.load()
            realDebrid?.load()
            savedMovies.load()
            watchTmdbAccount()
            val online = settings.prefs.map { it.onlineEnabled }.distinctUntilChanged()
            // The full index comes in the background, after the library is on screen.
            launch { indexMovies.value = catalogRepository.loadIndex() }
            val index = indexMovies.combine(enrichedIndex) { films, enriched -> films to enriched }
            catalogRepository.catalog(online, tmdb.keyMask.map { it != null }, language, refreshSignals)
                .combine(savedMovies.items) { catalog, saved -> catalog to saved }
                .combine(online) { (catalog, saved), isOnline -> withSaved(catalog, saved, isOnline) to isOnline }
                .combine(index) { (catalog, isOnline), (films, enriched) -> withIndex(catalog, films, enriched, isOnline) }
                // Thousands of films are merged per update: off the main thread.
                .flowOn(Dispatchers.Default)
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

    /** The full horror index (core HorrorIndex): empty until read, and if it cannot be. */
    private val indexMovies = MutableStateFlow<List<Movie>>(emptyList())

    /** Index films opened once, filled in from Cinemeta (synopsis, length, cast, trailers), by movie id. */
    private val enrichedIndex = MutableStateFlow<Map<String, Movie>>(emptyMap())

    /**
     * Adds the full index after everything else: search finds thousands of films, offline too, and a decade, "More like
     * this" and Ask SCARY choose from all of them. With online data on, each shows Cinemeta's poster for its IMDb id;
     * off, nothing loads from the web. Films already shown (library, online rows, saved) are not added twice.
     */
    private fun withIndex(catalog: Catalog, films: List<Movie>, enriched: Map<String, Movie>, online: Boolean): Catalog {
        if (films.isEmpty()) return catalog
        val known = catalog.movies.mapTo(HashSet()) { it.imdbId }
        val extra = films.asSequence()
            .filter { it.imdbId !in known }
            .map { enriched[it.id] ?: it }
            .map {
                if (online) it.copy(posterUrl = it.posterUrl ?: Cinemeta.posterUrl(it.imdbId)) else it.copy(posterUrl = null, backgroundUrl = null, logoUrl = null)
            }
            .toList()
        // The library's subgenre rows go on with index films of that subgenre (slashers, zombies…), best known first.
        val collections = catalog.collections.map { collection ->
            val subgenre = Categories.subgenreOfRow(collection.id) ?: return@map collection
            val have = collection.movieIds.toHashSet()
            val more = extra.asSequence().filter { subgenre in it.subgenres && it.id !in have }.take(INDEX_PER_ROW).map { it.id }.toList()
            if (more.isEmpty()) collection else collection.copy(movieIds = collection.movieIds + more)
        }
        return catalog.copy(movies = catalog.movies + extra, collections = collections)
    }

    /**
     * An index film comes with no synopsis: the first time it is opened (online), its details come from Cinemeta. Only
     * its IMDb id leaves the TV, as for every online row.
     */
    private fun fillInIndexFilm(movieId: String) {
        if (latestPrefs?.onlineEnabled != true || movieId in enrichedIndex.value) return
        val film = indexMovies.value.firstOrNull { it.id == movieId } ?: return
        viewModelScope.launch {
            val meta = cinemeta.meta(film.imdbId, film.kind) ?: return@launch
            enrichedIndex.update { it + (movieId to Cinemeta.enrich(film, meta)) }
        }
    }

    fun refreshApps() {
        if (appsJob?.isActive == true) return
        appsJob = viewModelScope.launch {
            val apps = appsRepository.load()
            installedApps.value = apps
            inputs.value = tvInputs.load()
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

    private sealed interface RentPhase {
        data object Hidden : RentPhase
        data object NeedsKey : RentPhase
        data object NoCountry : RentPhase
        data class Loading(val country: String) : RentPhase
        data class Loaded(val offers: RegionOffers) : RentPhase
    }

    private val rentPhase = MutableStateFlow<Pair<String, RentPhase>?>(null)
    private var rentJob: Job? = null

    /** Rent, buy and subscription offers for the open movie page, matched to the apps on this TV. */
    val rentOrBuy: StateFlow<RentOrBuyUi?> = combine(rentPhase, _watchOptions) { phase, options ->
        phase?.let { (movieId, p) -> RentOrBuyUi(movieId, p.toUi(options)) }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** The country rent and buy offers are for: the one chosen in settings, or the device's. */
    val rentCountry: StateFlow<ResolvedCountry?> = settings.prefs
        .map { regionDetector.resolve(it.rentBuyCountry) }
        .distinctUntilChanged()
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Loads the offers for a movie page. The page calls it again when the key, the country or online data change. */
    fun loadRentOrBuy(movie: Movie) {
        rentJob?.cancel()
        rentJob = viewModelScope.launch {
            val prefs = settings.prefs.first()
            val country = withContext(Dispatchers.Default) { regionDetector.resolve(prefs.rentBuyCountry) }?.code
            val early = when {
                !prefs.onlineEnabled -> RentPhase.Hidden
                tmdb.keyMask.value == null -> RentPhase.NeedsKey
                country == null -> RentPhase.NoCountry
                else -> null
            }
            if (early != null || country == null) {
                rentPhase.value = movie.id to (early ?: RentPhase.NoCountry)
                return@launch
            }
            rentPhase.value = movie.id to RentPhase.Loading(country)
            val all = tmdb.watchProviders(movie.imdbId)
            // A failed request hides the section quietly: the movie page itself still works.
            rentPhase.value = movie.id to
                if (all == null) RentPhase.Hidden else RentPhase.Loaded(all[country] ?: RegionOffers(country, emptyList(), null))
        }
    }

    private fun RentPhase.toUi(options: List<WatchOption>): RentOrBuyState = when (this) {
        RentPhase.Hidden -> RentOrBuyState.Hidden
        RentPhase.NeedsKey -> RentOrBuyState.NeedsKey
        RentPhase.NoCountry -> RentOrBuyState.NoCountry
        is RentPhase.Loading -> RentOrBuyState.Loading(country)
        is RentPhase.Loaded -> RentOrBuyState.Ready(
            country = offers.country,
            rentOrBuy = offers.rentOrBuy.map { offer(it, options) },
            included = offers.included.map { offer(it, options) },
            link = offers.link,
        )
    }

    /** Search in the store's app when it is here, open its store page when it is known, otherwise nothing to open. */
    private fun offer(provider: WatchProvider, options: List<WatchOption>): ProviderOffer {
        val app = StreamingApps.matchProvider(provider.name)
        val installed = app?.let { known -> options.firstOrNull { StreamingApps.match(it.packageName, it.label)?.key == known.key } }
        val action = when {
            installed != null -> OfferAction.Search(installed)
            app != null && app.packages.isNotEmpty() -> OfferAction.Store(app.packages.first())
            else -> OfferAction.None
        }
        return ProviderOffer(provider, action)
    }

    fun openOffer(movie: Movie, offer: ProviderOffer) {
        when (val action = offer.action) {
            is OfferAction.Search -> searchIn(movie, action.option)
            is OfferAction.Store -> report(launcher.openStoreFor(action.packageName, offer.provider.name))
            OfferAction.None -> Unit
        }
    }

    /** Null follows the device again. */
    fun setRentBuyCountry(code: String?) {
        viewModelScope.launch { settings.setRentBuyCountry(code) }
    }

    fun rentCountries(language: Language): List<String> = regionDetector.countries(language)

    /** What "Automatic" would pick: the device's country, ignoring the one chosen in settings. */
    fun detectedCountry(): String? = regionDetector.resolve(null)?.code

    fun countryName(code: String, language: Language): String = regionDetector.displayName(code, language)

    fun searchEverywhere(movie: Movie) = guard(movie, GatedAction.SearchEverywhere(movie))

    private fun buildState(
        all: Catalog,
        tiles: List<AppTile>,
        drawer: List<DrawerApp>,
        choices: HomeChoices,
        prefs: LauncherPrefs,
        unlocked: Boolean,
        history: List<WatchEntry> = emptyList(),
    ): HomeUiState {
        // The tab shows its own kind only: films, or series. Search (state.movies) still finds both.
        val catalog = all.ofKind(choices.tab)
        val selectedMood = choices.mood
        val selectedDecade = choices.decade
        val budget = choices.budget
        val films = choices.tab == MediaKind.MOVIE
        // Tonight's pick is one not seen yet, as long as any is left, and never one marked "not for me".
        val watched = prefs.watchedIds.toHashSet()
        val disliked = prefs.dislikedIds.toHashSet()
        val liked = catalog.movies.filter { it.id !in disliked }.ifEmpty { catalog.movies }
        val nightPick = NightPick.pick(liked.filter { it.id !in watched }, today(), prefs.nightMaxFear)
            ?: NightPick.pick(liked, today(), prefs.nightMaxFear)
        val isLocked = { movie: Movie -> ParentalGate.isLocked(prefs.parentalEnabled, unlocked, prefs.lockFromFear, movie) }
        // Sorted by what the viewer watches (Personalize): in rows for finding something new, films already seen go to
        // the end, or out with "hide watched"; the rows of the kind they watch most come first. Their own lists
        // (recently opened, My list) stay as they made them.
        val hideWatched = prefs.hideWatched
        fun unseenFirst(movies: List<Movie>): List<Movie> =
            if (hideWatched) movies.filter { it.id !in watched } else Personalize.watchedLast(movies) { it.id in watched }
        val taste = Personalize.taste(all::movie, prefs.watchedIds, prefs.lovedIds, prefs.dislikedIds)
        val seenOrLoved = watched + prefs.lovedIds
        val rows = buildList {
            // The viewer's own files stopped halfway or marked: never on family night, and with the parental lock on,
            // only once the PIN was given (personal files have no fear level).
            if (!prefs.familyNight && (!prefs.parentalEnabled || unlocked)) {
                val going = WatchHistory.continueWatching(history).map { ContinueItem(it, it.movieId?.let(all::movie)) }
                if (going.isNotEmpty()) add(HomeRow.ContinueWatching(going))
            }
            val recent = prefs.recentIds.mapNotNull(catalog::movie)
            if (recent.isNotEmpty()) add(HomeRow.Recent(recent))
            add(HomeRow.Apps(tiles))
            // A new viewer: until a few films are watched or loved, the rows cannot follow their taste. Ask once.
            if (!taste.known && !prefs.tasteInviteDismissed && !prefs.familyNight) add(HomeRow.TasteInvite)
            if (prefs.aiEnabled && prefs.aiHomeRow && aiRepository != null) add(HomeRow.AskAi)
            if (films && prefs.seasonalRows) Season.activeOn(today()).forEach { season ->
                val movies = unseenFirst(season.pick(catalog.movies))
                if (movies.isNotEmpty()) add(HomeRow.Seasonal(season, movies))
            }
            val myList = prefs.myListIds.mapNotNull(catalog::movie)
            if (myList.isNotEmpty()) add(HomeRow.MyList(myList))
            // Moods, decades, time plans and recommendations are about films: a series has no single length or year.
            if (films) {
                val loved = Recommender.becauseYouLoved(catalog.movies, prefs.lovedIds, exclude = watched + disliked, isLocked = isLocked)
                loved?.let { add(HomeRow.BecauseYouLoved(it.because, it.movies)) }
                // Not the same films twice: "Because you watched" skips the loved row's seed and its films.
                Recommender.becauseYouWatched(
                    catalog.movies,
                    prefs.watchedIds,
                    exclude = disliked + prefs.lovedIds + loved?.movies?.map { it.id }.orEmpty(),
                    skipSeeds = setOfNotNull(loved?.because?.id),
                    isLocked = isLocked,
                )?.let { add(HomeRow.BecauseYouWatched(it.because, it.movies)) }
                add(
                    HomeRow.Moods(
                        selected = selectedMood,
                        movies = unseenFirst(Decades.pick(catalog.movies, selectedMood, selectedDecade)),
                        decade = selectedDecade,
                        decades = Decades.available(catalog.movies),
                    ),
                )
                add(
                    HomeRow.TimePlan(
                        selected = budget,
                        plan = budget?.let { TimePlan.plan(catalog.movies, it, today(), exclude = disliked, watched = watched, isLocked = isLocked) },
                    ),
                )
            }
            if (hideWatched) {
                val seen = prefs.watchedIds.mapNotNull(catalog::movie)
                if (seen.isNotEmpty()) add(HomeRow.Watched(seen))
            }
            // Family night can empty a row; an empty row is not shown.
            val collections = catalog.collections
                .map { collection -> collection to catalog.moviesOf(collection) }
                .filter { (_, movies) -> movies.isNotEmpty() }
            val online = Cinemeta.onlineCollectionId("")
            val ordered = Personalize.byTaste(collections, taste) { (_, movies) -> Personalize.rowScore(movies, taste, seenOrLoved) }
            // Each film once down the screen: a film from the web only in the first row that reaches it; library films
            // keep their places in the categories they were chosen for. Then, inside each row, the films from the web
            // that fit the viewer's taste best come first.
            Personalize.withoutRepeats(ordered) { collection, movie -> movie.curated && !collection.id.startsWith(online) }
                .forEach { (collection, movies) ->
                    val shown = unseenFirst(Personalize.byTasteInRow(movies, taste))
                    if (shown.isNotEmpty()) add(HomeRow.Collection(collection, shown))
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
            movies = all.movies,
        )
    }

    private fun appTiles(apps: List<InstalledApp>, prefs: LauncherPrefs, tvInputList: List<TvInput>): List<AppTile> {
        val installed = AppOrdering.order(apps, InstalledApp::packageName, InstalledApp::label, prefs.pinned, prefs.hidden)
            .map { AppTile.Installed(it) }
        val stremioInstalled = apps.any { it.packageName == KnownPackages.STREMIO }
        val suggestions = if (stremioInstalled) {
            emptyList()
        } else {
            listOf(AppTile.Suggestion(KnownPackages.STREMIO, Launcher.STREMIO_LABEL))
        }
        // A TV's own inputs come first: on a TV with screen, the HDMI devices and the antenna are where many evenings start.
        return tvInputList.map { AppTile.Input(it) } + suggestions + installed + AppTile.AllApps + AppTile.SystemSettings
    }

    private fun drawerApps(apps: List<InstalledApp>, prefs: LauncherPrefs): List<DrawerApp> =
        AppOrdering.order(apps, InstalledApp::packageName, InstalledApp::label, prefs.pinned)
            .map { DrawerApp(it, pinned = it.packageName in prefs.pinned, hidden = it.packageName in prefs.hidden) }

    /** "More like this" for one movie page, worked out from that catalog. */
    class SimilarFor(val movieId: String, val catalog: Catalog, val movies: List<Movie>)

    /**
     * "More like this" for the open movie page, worked out off the main thread: it scores the whole catalog (thousands
     * of films since the full index), which on a TV froze the page each time the catalog or a setting changed, such as
     * pressing "Watch". Worked out again only when the movie, the catalog or the viewer's taste changes. Films marked
     * "not for me" are never in it; among films alike, those that fit the viewer's taste go first, and the ones they
     * watched go last.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val similar: StateFlow<SimilarFor?> = combine(
        screen.map { (it as? Screen.Details)?.movieId }.distinctUntilChanged(),
        shownCatalog,
        settings.prefs.map { Triple(it.watchedIds, it.lovedIds, it.dislikedIds) }.distinctUntilChanged(),
    ) { movieId, catalogState, taste -> Triple(movieId, (catalogState as? CatalogState.Ready)?.catalog, taste) }
        .mapLatest { (movieId, shown, taste) ->
            if (movieId == null || shown == null) return@mapLatest null
            val movie = shown.movie(movieId) ?: webMovieIfAllowed(movieId) ?: return@mapLatest null
            val (watchedIds, lovedIds, dislikedIds) = taste
            val disliked = dislikedIds.toHashSet()
            val watched = watchedIds.toHashSet()
            val candidates = shown.movies.filter { it.kind == movie.kind && it.id !in disliked }
            val viewerTaste = Personalize.taste(shown::movie, watchedIds, lovedIds, dislikedIds)
            val bonus: (Movie) -> Double = if (viewerTaste.known) {
                { TASTE_WEIGHT * viewerTaste.score(it).coerceIn(-TASTE_CAP, TASTE_CAP) / TASTE_CAP }
            } else {
                { 0.0 }
            }
            SimilarFor(movieId, shown, Similar.find(candidates, movie, bonus = bonus) { it.id in watched })
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /**
     * The movie page, from composition: cheap, a lookup only. "More like this" is [similar]'s, for this movie (empty
     * for the moment it takes to work out, the last one while the catalog changes under an open page).
     */
    fun details(movieId: String, similarFor: SimilarFor?): MovieDetails? {
        val shown = visibleCatalog() ?: return null
        val movie = shown.movie(movieId) ?: webMovieIfAllowed(movieId) ?: return null
        return MovieDetails(movie, similarFor?.takeIf { it.movieId == movieId }?.movies.orEmpty())
    }

    /** The catalog as the viewer may see it: with pages loaded while scrolling, and on family night only gentle movies. */
    private fun visibleCatalog(): Catalog? = (shownCatalog.value as? CatalogState.Ready)?.catalog

    /** The catalog with the pages loaded while scrolling, then filtered for family night. */
    private fun shown(catalog: Catalog, extras: Map<String, List<CinemetaMeta>>, online: Boolean, familyNight: Boolean): Catalog {
        val extended = if (!online) {
            catalog
        } else {
            extras.entries.fold(catalog) { acc, (key, metas) ->
                val collectionId = if (key.startsWith(CATEGORY_PREFIX)) key.removePrefix(CATEGORY_PREFIX) else Cinemeta.onlineCollectionId(key)
                Cinemeta.extend(acc, collectionId, metas)
            }
        }
        return if (familyNight) FamilyNight.filter(extended) else extended
    }

    /** An endless row's place: [next] is its next page (a Cinemeta cursor or a TMDB page), null at its end. */
    private class RowPaging(var next: Int?, var loading: Boolean = false)

    private val paging = HashMap<String, RowPaging>()

    /**
     * The viewer is near the end of a home row: every online row (Cinemeta's, and TMDB's with a key) loads its next
     * page, with no cap but the catalog's own end; rows that walk back through the years go on from year to year.
     * Pages without a single movie the row keeps (a year page of comedies) are skipped, a few at a time. Offline,
     * nothing happens, and the next scroll tries again.
     */
    fun onNearRowEnd(rowKey: String) {
        if (latestPrefs?.onlineEnabled == false || latestPrefs?.familyNight == true) return
        val collectionId = rowKey.removePrefix("collection:").takeIf { it != rowKey } ?: return
        // A library category goes on past its films from the daily pool (CategoryPaging), kept under "cat:<id>".
        if (collectionId in Categories.ids) {
            loadMore(CATEGORY_PREFIX + collectionId, start = 0) { cursor -> cinemeta.loadCategoryPage(collectionId, cursor) }
            return
        }
        val rowId = collectionId.removePrefix(Cinemeta.onlineCollectionId("")).takeIf { it != collectionId } ?: return
        val fromTmdb = cinemeta.endlessStart(rowId) == null
        val start = cinemeta.endlessStart(rowId) ?: tmdb.endlessStart(rowId) ?: return
        loadMore(rowId, start) { cursor -> if (fromTmdb) tmdb.loadPage(rowId, cursor, language.value) else cinemeta.loadPage(rowId, cursor) }
    }

    /**
     * Loads the next pages of the row kept under [key] in [extraPages], from [start]: pages until one brings films
     * not on the home screen yet (each film shows once down the screen), skipping up to [MAX_EMPTY_PAGES] at a time.
     */
    private fun loadMore(key: String, start: Int, load: suspend (Int) -> OnlinePage?) {
        val rowId = key
        val state = paging.getOrPut(rowId) { RowPaging(start) }
        if (state.loading || state.next == null) return
        if (extraPages.value[rowId].orEmpty().size >= Cinemeta.MAX_ENDLESS_ROW_MOVIES) {
            state.next = null
            return
        }
        state.loading = true
        viewModelScope.launch {
            try {
                var found = emptyList<CinemetaMeta>()
                var attempts = 0
                // Films already on the home screen do not count: rows show each film once, and a page of them would
                // leave the row as it was, with nothing to scroll to.
                val onScreen = uiState.value.rows.flatMapTo(HashSet()) { row ->
                    (row as? HomeRow.Collection)?.movies?.map { it.imdbId }.orEmpty()
                }
                while (found.isEmpty() && attempts < MAX_EMPTY_PAGES) {
                    val cursor = state.next ?: break
                    val page = load(cursor) ?: return@launch
                    attempts++
                    state.next = page.next
                    found = page.movies.filterNot { it.imdbId in onScreen }
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
    @Volatile
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
        val watched = prefs.watchedIds.toHashSet()
        fun surpriseFrom(movies: List<Movie>) = NightPick.surprise(
            movies = movies,
            maxFear = prefs.nightMaxFear,
            random = Random.Default,
            exclude = heroId.value,
            isLocked = { ParentalGate.isLocked(prefs.parentalEnabled, isSessionUnlocked(), prefs.lockFromFear, it) },
            watched = watched,
        )
        // With a mood or a decade chosen, the surprise comes from those movies; the whole library when none of them fits.
        // Never a film marked "not for me".
        val disliked = prefs.dislikedIds.toHashSet()
        // From the tab on screen: a film, or a series.
        val candidates = shown.movies.filter { it.kind == tab.value && it.id !in disliked }
        val chosen = if (tab.value == MediaKind.MOVIE) Decades.pick(candidates, mood.value, decade.value) else candidates
        val pick = surpriseFrom(chosen) ?: surpriseFrom(candidates)
        if (pick == null) show(R.string.msg_surprise_none) else openDetails(pick.id)
    }

    private fun push(screen: Screen) {
        backStack.update { if (it.last() == screen) it else it + screen }
    }

    /** Returns false when already on the home screen, so Back does nothing there. */
    fun back(): Boolean {
        // Back during the shiver screen cancels opening the movie, and stays on this screen.
        if (_launching.value != null) {
            cancelLaunch()
            return true
        }
        // Back while picking films ends the picking and shows the bucket list.
        if (picking.value && backStack.value.last() == Screen.Home) {
            finishPicking()
            return true
        }
        if (backStack.value.size <= 1) return false
        if (backStack.value.last() == Screen.Player) onPlayerClosed()
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
        val job = viewModelScope.launch {
            try {
                delay(if (motion) LAUNCH_SHIVER_MS else LAUNCH_STILL_MS)
                if (generation != dialogGeneration) return@launch
                val result = open()
                report(result)
                // Only when the movie itself opened (or a search for it): an app that merely opened says nothing about it.
                if (result is LaunchResult.Opened) rememberWatching(movie)
                // Kept a little longer, so the dark screen covers the moment the other app takes to appear.
                delay(LAUNCH_CLEAR_MS)
            } finally {
                // Whatever ends it (done, cancelled, or a dialog closed meanwhile), the dark screen goes with it,
                // unless a newer launch has taken its place.
                if (launchJob === coroutineContext[Job]) _launching.value = null
            }
        }
        launchJob = job
    }

    /**
     * Remembers the movie now playing elsewhere, so the return to SCARY can mark it as watched. A coroutine of its own:
     * leaving SCARY cancels the launch job right after this.
     */
    private fun rememberWatching(movie: Movie) {
        // From the bucket list only if this very film was started from it: the return then asks about the next one.
        val fromBucket = bucketLaunch == movie.id
        bucketLaunch = null
        viewModelScope.launch {
            settings.setPendingWatch(PendingWatch(movie.id, System.currentTimeMillis()))
            settings.setBucketPlaying(movie.id.takeIf { fromBucket })
        }
    }

    /**
     * SCARY is on screen again. If a movie was handed to another app and the time away looks like a viewing, it is
     * marked as watched (when the viewer allows it), and a message says so and how to undo it.
     */
    fun onResumed() {
        // Back on the trailer question put it off only while SCARY stayed in front.
        trailerIntroPutOff = false
        refreshRequests.tryEmit(Unit)
        viewModelScope.launch {
            val pending = settings.takePendingWatch() ?: return@launch
            val prefs = settings.prefs.first()
            val catalog = (shownCatalog.first { it !is CatalogState.Loading } as? CatalogState.Ready)?.catalog
            val movie = catalog?.movie(pending.movieId) ?: webMovieIfAllowed(pending.movieId)
            // Up to the screen going off, when the TV was switched off after the film (SettingsRepository.endPendingWatch).
            val away = pending.awayMillis(System.currentTimeMillis())
            // Back from a bucket-list film: ask about the next one, every time.
            if (prefs.bucketPlaying == pending.movieId) {
                settings.setBucketPlaying(null)
                if (movie != null) offerNextInBucket(movie, finished = BucketReturn.finished(away, movie.runtimeMin))
            }
            // A series is never marked by itself: coming back after an episode is not the end of it.
            if (!prefs.autoWatched || pending.movieId in prefs.watchedIds || movie?.isSeries == true) return@launch
            if (!AutoWatched.counts(away, movie?.runtimeMin ?: 0)) return@launch
            if (settings.setWatched(pending.movieId, watched = true) && movie != null) {
                show(R.string.msg_auto_watched, movie.displayTitle(language.value))
            }
        }
    }

    // The viewer's TMDB account ------------------------------------------------------------------------------------

    /** Signed in or not, waiting for approval, syncing: for the account's screen and the settings row. */
    val tmdbAccountState: StateFlow<TmdbAccountRepository.State> =
        tmdbAccount?.state ?: MutableStateFlow(TmdbAccountRepository.State.SignedOut)

    /** The lists as the last sync left them: changes the sync makes itself do not start another. */
    private var syncedLists: Pair<List<String>, List<String>>? = null

    /**
     * Keeps the account in step: after signing in, when SCARY is back on screen (at most every [ACCOUNT_SYNC_GAP_MS]),
     * and shortly after My list or "Loved it" changes here. A key added or deleted updates what the account can do.
     */
    @OptIn(FlowPreview::class)
    private fun watchTmdbAccount() {
        val account = tmdbAccount ?: return
        viewModelScope.launch { tmdb.keyMask.collect { account.onKeyChanged() } }
        viewModelScope.launch { refreshRequests.collect { syncTmdbAccount(ACCOUNT_SYNC_GAP_MS) } }
        viewModelScope.launch {
            settings.prefs.map { it.myListIds to it.lovedIds }
                .distinctUntilChanged()
                .debounce(ACCOUNT_SYNC_DEBOUNCE_MS)
                .collect { lists -> if (lists != syncedLists) syncTmdbAccount(0) }
        }
    }

    private suspend fun syncTmdbAccount(minGapMs: Long) {
        val account = tmdbAccount ?: return
        if (account.state.value !is TmdbAccountRepository.State.SignedIn) return
        val full = (catalog.value as? CatalogState.Ready)?.catalog ?: return
        val result = account.sync(full, language.value, minGapMs) ?: return
        val prefs = settings.prefs.first()
        syncedLists = prefs.myListIds to prefs.lovedIds
        if (result.addedToMyList + result.addedToLoved + result.addedToWatched > 0) show(R.string.msg_tmdb_synced)
    }

    fun openTmdbAccount() = push(Screen.TmdbAccount)

    // Real-Debrid and the internal player -------------------------------------------------------------------------------

    val realDebridState: StateFlow<RealDebridRepository.State> =
        realDebrid?.state ?: MutableStateFlow(RealDebridRepository.State.SignedOut)

    private val _rdFiles = MutableStateFlow<RdFilesState>(RdFilesState.Idle)
    val rdFiles: StateFlow<RdFilesState> = _rdFiles.asStateFlow()

    /** The viewer's files that are the film on screen, by movie id (the movie page's "Play from Real-Debrid"). */
    private val _rdMatches = MutableStateFlow<Pair<String, List<RealDebrid.RdFile>>?>(null)
    val rdMatches: StateFlow<Pair<String, List<RealDebrid.RdFile>>?> = _rdMatches.asStateFlow()

    private val _player = MutableStateFlow<PlayerSession?>(null)
    val player: StateFlow<PlayerSession?> = _player.asStateFlow()

    /** "Continue from 1:12:40, or from the start?" for a file with a saved spot. */
    private val _resumeChoice = MutableStateFlow<ResumeChoice?>(null)
    val resumeChoice: StateFlow<ResumeChoice?> = _resumeChoice.asStateFlow()

    /** Files already counted as watched in this player session (near the end), so it is said once. */
    private val markedWatched = HashSet<String>()

    /** Settings → Real-Debrid. Family night leaves personal files out, as it does every film without a fear level. */
    fun openRealDebrid() {
        if (latestPrefs?.familyNight == true) return show(R.string.rd_family_night)
        guard(null, GatedAction.RealDebrid)
    }

    fun realDebridSignIn() {
        realDebrid?.startSignIn()
    }

    fun realDebridCancelSignIn() {
        realDebrid?.cancelSignIn()
    }

    fun realDebridSignOut() {
        realDebrid?.signOut()
        // What was watched from the account goes with it.
        watchStore?.clear()
        _rdFiles.value = RdFilesState.Idle
        _rdMatches.value = null
        show(R.string.rd_signed_out_message)
    }

    fun loadRealDebridFiles(force: Boolean = false) {
        val repository = realDebrid ?: return
        if (!repository.signedIn) return
        viewModelScope.launch {
            _rdFiles.value = RdFilesState.Loading
            _rdFiles.value = when (val files = repository.files(force)) {
                is RealDebridRepository.Files.Ready -> RdFilesState.Ready(files.files)
                RealDebridRepository.Files.SignedOut -> RdFilesState.Idle
                RealDebridRepository.Files.Unreachable -> RdFilesState.Unreachable
            }
        }
    }

    /** Looks through the viewer's own files for the film being opened; nothing is searched for anywhere else. */
    private fun findRealDebridFiles(movieId: String) {
        _rdMatches.value = null
        val repository = realDebrid ?: return
        val prefs = latestPrefs ?: return
        if (!repository.signedIn || !prefs.onlineEnabled || prefs.familyNight) return
        val movie = movieById(movieId) ?: return
        viewModelScope.launch {
            val matches = repository.filesFor(movie)
            if (matches.isNotEmpty()) _rdMatches.value = movieId to matches
        }
    }

    fun playRealDebrid(file: RealDebrid.RdFile, movieId: String?, startAtMs: Long? = null) {
        if (latestPrefs?.familyNight == true) return show(R.string.rd_family_night)
        guard(null, GatedAction.PlayRealDebrid(file, movieId, startAtMs))
    }

    /** A file chosen to play: with a saved spot, the viewer picks where to start; otherwise from the start. */
    private fun startPlaying(file: RealDebrid.RdFile, movieId: String?, startAtMs: Long?) {
        val saved = watchStore?.entry(file.key)?.positionMs ?: 0L
        when {
            startAtMs != null -> play(file, movieId, startAtMs)
            saved > 0 -> _resumeChoice.value = ResumeChoice(file, movieId, saved)
            else -> play(file, movieId, 0L)
        }
    }

    /** "Continue from …" ([fromSpot]) or "From the start". */
    fun answerResume(fromSpot: Boolean) {
        val choice = _resumeChoice.value ?: return
        _resumeChoice.value = null
        play(choice.file, choice.movieId, if (fromSpot) choice.positionMs else 0L)
    }

    fun dismissResume() {
        _resumeChoice.value = null
    }

    private fun play(file: RealDebrid.RdFile, movieId: String?, startAtMs: Long) {
        val repository = realDebrid ?: return
        viewModelScope.launch {
            val playable = repository.playable(file)
            if (playable == null) {
                show(R.string.rd_play_failed)
                return@launch
            }
            movieId?.let { settings.pushRecent(it) }
            // The file is in the history from the start, so a spot can be marked at once.
            val old = watchStore?.entry(file.key)
            watchStore?.update { WatchHistory.progress(it, file.key, file.name, movieId, startAtMs, old?.durationMs ?: 0L, System.currentTimeMillis()) }
            val next = (repository.files() as? RealDebridRepository.Files.Ready)?.files?.let { files ->
                WatchHistory.nextInTorrent(file.key, files.map { it.key })?.let { key -> files.firstOrNull { it.key == key } }
            }
            val session = PlayerSession(file.key, playable, movieId, startAtMs, next)
            val replacing = _player.value != null
            _player.value = session
            if (!replacing) push(Screen.Player)
        }
    }

    /** The player's position, saved now and then and when it closes; near the end the film counts as watched. */
    fun onPlayerProgress(positionMs: Long, durationMs: Long) {
        val session = _player.value ?: return
        watchStore?.update { WatchHistory.progress(it, session.key, "", session.movieId, positionMs, durationMs, System.currentTimeMillis()) }
        val movieId = session.movieId ?: return
        if (durationMs > 0 && positionMs >= durationMs * WatchHistory.FINISHED_FRACTION && markedWatched.add(session.key)) {
            viewModelScope.launch { settings.setWatched(movieId, watched = true) }
        }
    }

    /**
     * "Mark this spot" in the player. A spot within a few seconds of one already marked is that one; either way the
     * spot is marked now. False only when there is no history to mark it in.
     */
    fun addBookmark(positionMs: Long): Boolean {
        val session = _player.value ?: return false
        val store = watchStore ?: return false
        store.update { WatchHistory.addBookmark(it, session.key, positionMs, System.currentTimeMillis()) }
        return store.entry(session.key) != null
    }

    fun removeBookmark(positionMs: Long) {
        val session = _player.value ?: return
        watchStore?.update { WatchHistory.removeBookmark(it, session.key, positionMs) }
    }

    /** The next file in the same torrent, from where the viewer stopped in it (or its start). */
    fun playNext() {
        val next = _player.value?.next ?: return
        play(next, null, watchStore?.entry(next.key)?.positionMs ?: 0L)
    }

    fun onPlayerClosed() {
        _player.value = null
        markedWatched.clear()
    }

    /**
     * A card under "Continue watching", the movie page's "Continue from", or the Real-Debrid screen's list: the file is
     * looked up in the account again (its link is never kept), and plays from the saved spot, or the last spot marked.
     */
    fun continueWatching(entry: WatchEntry) {
        if (latestPrefs?.familyNight == true) return show(R.string.rd_family_night)
        val repository = realDebrid ?: return
        if (!repository.signedIn) return show(R.string.rd_continue_signed_out)
        viewModelScope.launch {
            when (val files = repository.files()) {
                is RealDebridRepository.Files.Ready -> {
                    val file = files.files.firstOrNull { it.key == entry.key }
                    if (file == null) {
                        watchStore?.update { WatchHistory.forget(it, entry.key) }
                        show(R.string.rd_file_gone)
                    } else {
                        val start = entry.positionMs.takeIf { it > 0 }
                            ?: entry.bookmarks.maxByOrNull { it.createdAt }?.positionMs
                            ?: 0L
                        playRealDebrid(file, entry.movieId, start)
                    }
                }
                RealDebridRepository.Files.SignedOut -> show(R.string.rd_continue_signed_out)
                RealDebridRepository.Files.Unreachable -> show(R.string.rd_unreachable)
            }
        }
    }

    /** Holding OK on a "Continue watching" card: it leaves the row (and its bookmarks go). */
    fun forgetWatching(entry: WatchEntry) {
        watchStore?.update { WatchHistory.forget(it, entry.key) }
        show(R.string.continue_removed)
    }

    /** "Clear watch history" on the Real-Debrid screen. */
    fun clearWatchHistory() {
        watchStore?.clear()
        show(R.string.rd_history_cleared)
    }

    /** The player's own settings that outlast a film: subtitle size, picture, night sound. */
    val playerSettings: WatchStore? get() = watchStore

    /** UI tests: plays a local file in the internal player, as a Real-Debrid file would. */
    @androidx.annotation.VisibleForTesting
    internal fun playForTest(session: PlayerSession) {
        watchStore?.update { WatchHistory.progress(it, session.key, session.playable.title, session.movieId, session.startAtMs, 0L, System.currentTimeMillis()) }
        _player.value = session
        push(Screen.Player)
    }

    fun startTmdbSignIn() {
        tmdbAccount?.startSignIn { viewModelScope.launch { syncTmdbAccount(0) } }
    }

    fun cancelTmdbSignIn() {
        tmdbAccount?.cancelSignIn()
    }

    fun signOutOfTmdb() {
        viewModelScope.launch {
            tmdbAccount?.signOut()
            show(R.string.msg_tmdb_signed_out)
        }
    }

    fun syncTmdbNow() {
        viewModelScope.launch { syncTmdbAccount(0) }
    }

    /** Marks a movie as watched or not, from its page, and says which. Not PIN-gated: it opens nothing. */
    fun toggleWatched(movie: Movie, language: Language) {
        viewModelScope.launch {
            val watched = movie.id !in settings.prefs.first().watchedIds
            settings.setWatched(movie.id, watched)
            if (watched) savedMovies.save(movie, language, keptOnline())
            show(if (watched) R.string.msg_marked_watched else R.string.msg_unmarked_watched, movie.displayTitle(language))
        }
    }

    fun toggleAutoWatched() {
        viewModelScope.launch { settings.setAutoWatched(!settings.prefs.first().autoWatched) }
    }

    fun toggleHideWatched() {
        viewModelScope.launch { settings.setHideWatched(!settings.prefs.first().hideWatched) }
    }

    private fun cancelLaunch() {
        launchJob?.cancel()
        launchJob = null
        _launching.value = null
        bucketLaunch = null
    }

    fun onHomePressed() {
        // Home always lands on a clean home screen: no dialog left floating over it.
        dialogGeneration++
        cancelLaunch()
        _pinDialog.value = null
        _tmdbDialog.value = null
        _trailerIntro.value = false
        // Home lands on a clean screen. Coming back from Stremio with Home, this runs before onResumed asks its question.
        _bucketPrompt.value = null
        backStack.value = listOf(Screen.Home)
        heroId.value = null
        lastFocusedKey = null
        picking.value = false
        _homeResets.update { it + 1 }
    }

    /** Adds or removes a movie from "My list" and says which happened. Not PIN-gated: it opens nothing. */
    fun toggleMyList(movie: Movie, language: Language) {
        viewModelScope.launch {
            val added = settings.toggleMyList(movie.id)
            if (Log.isLoggable(KEY_LOG_TAG, Log.DEBUG)) Log.d(KEY_LOG_TAG, "My list: ${movie.id} added=$added")
            if (added) savedMovies.save(movie, language, keptOnline())
            show(if (added) R.string.msg_my_list_added else R.string.msg_my_list_removed, movie.displayTitle(language))
        }
    }

    /** IMDb ids of the online movies on My list and Recently opened: their saved details must stay. */
    private suspend fun keptOnline(): Set<String> {
        val prefs = settings.prefs.first()
        return (prefs.myListIds + prefs.recentIds + prefs.bucketIds).filter { it.startsWith(Cinemeta.ID_PREFIX) }.mapTo(HashSet()) { it.removePrefix(Cinemeta.ID_PREFIX) }
    }

    fun watch(movie: Movie) {
        bucketLaunch = null
        guard(movie, GatedAction.Watch(movie))
    }

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
            is GatedAction.Details -> {
                fillInIndexFilm(action.movieId)
                findRealDebridFiles(action.movieId)
                push(Screen.Details(action.movieId))
            }
            is GatedAction.Watch -> {
                viewModelScope.launch {
                    settings.pushRecent(action.movie.id)
                    savedMovies.save(action.movie, language.value, keptOnline())
                }
                launchWithShiver(action.movie, Launcher.STREMIO_LABEL) { launcher.openInStremio(action.movie) }
            }
            is GatedAction.Trailer -> report(launcher.openTrailer(action.movie))
            is GatedAction.SearchIn -> {
                viewModelScope.launch {
                    settings.pushRecent(action.movie.id)
                    // Online movies are kept, so Recently opened still finds them after they leave the online rows.
                    savedMovies.save(action.movie, language.value, keptOnline())
                }
                launchWithShiver(action.movie, action.app.label) {
                    launcher.searchIn(action.app.packageName, action.app.label, action.movie)
                }
            }
            is GatedAction.SearchEverywhere -> launchWithShiver(action.movie, GOOGLE_TV_LABEL) {
                launcher.searchEverywhere(action.movie, GOOGLE_TV_LABEL)
            }
            GatedAction.Settings -> push(Screen.Settings)
            GatedAction.RealDebrid -> {
                push(Screen.RealDebrid)
                realDebrid?.refreshAccount()
                loadRealDebridFiles()
            }
            is GatedAction.PlayRealDebrid -> startPlaying(action.file, action.movieId, action.startAtMs)
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
        unlockExpiry?.cancel()
        unlockExpiry = null
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
        bucketLaunch = null
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
        // The unlock ends on its own after the window, even if nobody touches the remote: locked movies hide their art
        // again, and open settings close.
        unlockExpiry?.cancel()
        unlockExpiry = viewModelScope.launch {
            delay(ParentalGate.UNLOCK_WINDOW_MS)
            lockSession()
        }
    }

    /** Ends the PIN unlock. Settings need the PIN, so when the lock is on they close as well. */
    private fun lockSession() {
        unlockExpiry?.cancel()
        unlockExpiry = null
        sessionUnlocked.value = false
        if (latestPrefs?.parentalEnabled == true && Screen.Settings in backStack.value) {
            backStack.value = listOf(Screen.Home)
        }
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
            is AppTile.Input -> report(launcher.openInput(tile.input))
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

    private val _trailerIntro = MutableStateFlow(false)

    /** The first-time explanation of trailers that play by themselves, before any of them reaches YouTube. */
    val trailerIntro: StateFlow<Boolean> = _trailerIntro.asStateFlow()

    private val _trailerAudible = MutableStateFlow(false)

    /** A trailer is playing with sound: the ambient sounds step aside meanwhile. */
    val trailerAudible: StateFlow<Boolean> = _trailerAudible.asStateFlow()

    // Back on the explanation puts it off until SCARY comes back to the front, rather than asking on every movie.
    private var trailerIntroPutOff = false

    fun requestTrailerIntro() {
        if (latestPrefs?.trailerIntroSeen != true && !trailerIntroPutOff) _trailerIntro.value = true
    }

    /** [play]: keep trailers playing by themselves; false turns them off. Either way the question is not asked again. */
    fun answerTrailerIntro(play: Boolean) {
        _trailerIntro.value = false
        viewModelScope.launch {
            settings.answerTrailerIntro(play)
            if (!play) show(R.string.msg_trailers_off)
        }
    }

    /** Debug builds (CI screenshots and tests): the explanation, whatever was answered before. */
    fun debugShowTrailerIntro() {
        _trailerIntro.value = true
    }

    /** Debug builds (CI screenshots): trailers on or off as if answered, so screenshots never catch one playing. */
    fun debugSetTrailers(enabled: Boolean) {
        viewModelScope.launch { settings.answerTrailerIntro(enabled) }
    }

    /** Back on the explanation: nothing plays, and it is asked again once SCARY comes back to the front. */
    fun dismissTrailerIntro() {
        trailerIntroPutOff = true
        _trailerIntro.value = false
    }

    fun setTrailerAudible(audible: Boolean) {
        _trailerAudible.value = audible
    }

    /** Changing the setting by hand also answers the first-time question. */
    fun toggleTrailerAutoplay() {
        viewModelScope.launch { settings.answerTrailerIntro(!settings.prefs.first().trailerAutoplay) }
    }

    fun toggleTrailerSound() {
        viewModelScope.launch { settings.setTrailerSound(!settings.prefs.first().trailerSound) }
    }

    fun toggleSeasonalRows() {
        viewModelScope.launch {
            settings.setSeasonalRows(!settings.prefs.first().seasonalRows)
        }
    }

    /**
     * Turning family night on also ends any PIN unlock, so turning it off again (in Settings) asks for the PIN.
     * Without a PIN, anyone can turn it off; the setting's note says so.
     */
    fun toggleFamilyNight() {
        viewModelScope.launch {
            val prefs = settings.prefs.first()
            val enabled = !prefs.familyNight
            settings.setFamilyNight(enabled)
            // With a PIN, family night is for handing over the remote: settings close, so turning it off (or removing
            // the lock) asks for the PIN again. Without one, the setting's note says anyone can turn it off.
            if (enabled) lockSession()
            _webSearch.value = WebSearchState()
            show(if (enabled) R.string.msg_family_on else R.string.msg_family_off)
        }
    }

    /** Picks a time budget, or clears it when the same one is chosen again. */
    fun toggleTimeBudget(selected: TimeBudget) {
        timeBudget.update { if (it == selected) null else selected }
    }

    /** "Loved it" on a film's page; pressed again, it is taken back. */
    fun toggleLoved(movie: Movie, language: Language) = rate(movie, Rating.LOVED, language)

    /** "Not for me" on a film's page: it is no longer suggested; pressed again, it is taken back. */
    fun toggleNotForMe(movie: Movie, language: Language) = rate(movie, Rating.NOT_FOR_ME, language)

    /**
     * OK on a poster in "What do you love?": loved, or taken back. No message for each pick: the screen's count and the
     * heart on the poster say it, and TalkBack reads both.
     */
    fun toggleLovedQuietly(movie: Movie) = rate(movie, Rating.LOVED, quiet = true)

    fun openTastePicker() = push(Screen.TastePicker)

    /** "Not now" on the home screen's invitation; settings still offer the picker. */
    fun dismissTasteInvite() {
        viewModelScope.launch { settings.setTasteInviteDismissed(true) }
    }

    /** "Done" in the picker: back where it was opened, saying the rows now follow the picks (once there are enough). */
    fun finishTastePicker() {
        val loved = latestPrefs?.lovedIds?.size ?: 0
        back()
        if (loved >= Personalize.MIN_HISTORY) show(R.string.msg_taste_sorted) else if (loved > 0) show(R.string.msg_taste_few)
    }

    /**
     * The picker's posters for one tab: the library's own (chosen by hand, so known) best rated first, then popular ones
     * from the web in the order they came. Films marked "Not for me" stay out; family night's limits apply as everywhere.
     */
    fun tasteCandidates(kind: MediaKind): List<Movie> {
        val catalog = (shownCatalog.value as? CatalogState.Ready)?.catalog ?: return emptyList()
        val disliked = latestPrefs?.dislikedIds?.toHashSet().orEmpty()
        val state = uiState.value
        val ofKind = catalog.ofKind(kind).movies.filter { it.id !in disliked && !state.isLocked(it) }
        val (curated, online) = ofKind.partition { it.curated }
        return (curated.sortedByDescending { it.imdbRating ?: 0.0 } + online).take(MAX_TASTE_CANDIDATES)
    }

    /** "Look for updates by itself" in settings. */
    fun toggleAutoUpdateCheck() {
        viewModelScope.launch {
            val prefs = settings.prefs.first()
            settings.setAutoUpdateCheck(!prefs.autoUpdateCheck)
        }
    }

    // AI --------------------------------------------------------------------------------------------------------------

    /** The four AI features (see AiAssistant); null only in tests built without AI. */
    val ai: AiAssistant? = aiRepository?.let { repository -> AiAssistant(repository, settings, viewModelScope, ::aiContext) }

    /**
     * What the AI may choose from now: what this screen may show (family night, the parental lock and "Not for me"
     * applied), the current tab's kind first, and the taste worked out on the TV. The open chat is off while the
     * parental lock or family night is on.
     */
    private fun aiContext(): AiContext? {
        val shown = visibleCatalog() ?: return null
        val prefs = latestPrefs ?: return null
        val state = uiState.value
        val disliked = prefs.dislikedIds.toHashSet()
        val everything = shown.movies.filter { it.id !in disliked && !state.isLocked(it) }
        return AiContext(
            shelf = everything.filter { it.kind == tab.value }.ifEmpty { everything },
            everything = everything,
            taste = Personalize.taste(shown::movie, prefs.watchedIds, prefs.lovedIds, prefs.dislikedIds),
            watched = prefs.watchedIds.toHashSet(),
            language = language.value,
            chatBlocked = prefs.familyNight || prefs.parentalEnabled,
        )
    }

    /** "Ask SCARY" (the top bar's button, or a chip on the home screen: asked at once). */
    fun openAsk(chip: AiChip? = null, query: String? = null, chat: Boolean = false) {
        if (chip != null && query != null) ai?.recommend(query, chip)
        push(Screen.Ask(chat))
    }

    /** Suggestions or chat on the "Ask SCARY" screen: the same place in the back stack. */
    fun setAskTab(chat: Boolean) {
        backStack.update { stack -> if (stack.last() is Screen.Ask) stack.dropLast(1) + Screen.Ask(chat) else stack }
    }

    fun setAiEnabled(enabled: Boolean) {
        viewModelScope.launch { settings.setAiEnabled(enabled) }
    }

    fun setAiProvider(provider: AiProvider) {
        viewModelScope.launch { settings.setAiProvider(provider) }
    }

    fun setAiModel(provider: AiProvider, model: String?) {
        viewModelScope.launch { settings.setAiModel(provider, model) }
    }

    fun setAiShareTaste(enabled: Boolean) {
        viewModelScope.launch { settings.setAiShareTaste(enabled) }
    }

    fun setAiHomeRow(enabled: Boolean) {
        viewModelScope.launch { settings.setAiHomeRow(enabled) }
    }

    /** A custom OpenAI-compatible address; false when it is not plain HTTPS. */
    fun setAiCustomUrl(input: String): Boolean {
        val url = AiProvider.customChatUrl(input) ?: return false
        viewModelScope.launch {
            settings.setAiCustomUrl(url)
            settings.setAiModel(AiProvider.CUSTOM, null)
        }
        return true
    }

    suspend fun aiModels(provider: AiProvider): List<String> = aiRepository?.models(provider).orEmpty()

    fun aiKeyMask(provider: AiProvider): String? = aiRepository?.keyMask(provider)

    /** A key typed on the TV: checked with the service, then kept (and that service chosen). */
    suspend fun saveAiKey(provider: AiProvider, key: String): AiRepository.KeyResult {
        val result = aiRepository?.saveKey(provider, key) ?: return AiRepository.KeyResult.STORAGE
        if (result == AiRepository.KeyResult.SAVED) {
            settings.setAiProvider(provider)
            show(R.string.msg_ai_key_saved, provider.displayName)
        }
        return result
    }

    fun deleteAiKey(provider: AiProvider) {
        aiRepository?.deleteKey(provider)
        show(R.string.msg_ai_key_deleted)
    }

    fun deleteAllAiKeys() {
        aiRepository?.deleteAllKeys()
        viewModelScope.launch { settings.setAiProvider(AiProvider.DEFAULT) }
        show(R.string.msg_ai_keys_deleted)
    }

    /** A key pasted on the phone ("service\nkey"); the phone gets the outcome. */
    suspend fun remoteAiKey(input: String): String {
        val provider = AiProvider.fromKey(input.substringBefore('\n').trim())
            ?.takeIf { it.needsKey || it == AiProvider.CUSTOM } ?: return "ai_invalid"
        return when (saveAiKey(provider, input.substringAfter('\n', ""))) {
            AiRepository.KeyResult.SAVED -> "ai_saved"
            AiRepository.KeyResult.INVALID -> "ai_invalid"
            AiRepository.KeyResult.REJECTED -> "ai_rejected"
            AiRepository.KeyResult.UNREACHABLE -> "ai_unreachable"
            AiRepository.KeyResult.STORAGE -> "ai_storage"
        }
    }

    /** "Clear recently opened" in settings. Watched, My list and the ratings stay. */
    fun clearRecent() {
        viewModelScope.launch {
            settings.clearRecent()
            show(R.string.msg_recent_cleared)
        }
    }

    private fun rate(movie: Movie, rating: Rating, language: Language? = null, quiet: Boolean = false) {
        viewModelScope.launch {
            val prefs = settings.prefs.first()
            val current = when (movie.id) {
                in prefs.lovedIds -> Rating.LOVED
                in prefs.dislikedIds -> Rating.NOT_FOR_ME
                else -> null
            }
            val next = rating.takeIf { it != current }
            settings.setRating(movie.id, next)
            if (!quiet) show(
                when (next) {
                    Rating.LOVED -> R.string.msg_loved
                    Rating.NOT_FOR_ME -> R.string.msg_not_for_me
                    null -> R.string.msg_rating_cleared
                },
            )
        }
    }

    /** The sleep timer was started (ending at [endsAtMillis]) or turned off (null), by ScaryRoot through SleepTimer. */
    fun setSleepAt(endsAtMillis: Long?) {
        viewModelScope.launch { settings.setSleepAt(endsAtMillis) }
    }

    fun openStats() = push(Screen.Stats)

    // Phone remote --------------------------------------------------------------------------------------------------

    fun openPhoneRemote() = push(Screen.PhoneRemote)

    /** The phone's Back: closes the dialog on top, if any (dialogs have windows of their own, out of a key's reach). */
    fun closeTopDialog(): Boolean = when {
        _bucketPrompt.value != null -> { dismissBucketPrompt(); true }
        _pinDialog.value != null -> { dismissPin(); true }
        _tmdbDialog.value != null -> { dismissTmdbDialog(); true }
        _trailerIntro.value -> { dismissTrailerIntro(); true }
        else -> false
    }

    /**
     * A search typed on the phone: the TV shows it on its search screen, and the phone gets the matches to open (the
     * library's at once, the web's when they arrive, within a few seconds). Family night's limits apply, as on the TV.
     */
    suspend fun remoteSearch(text: String): List<PhoneRemoteServer.Result> {
        val query = text.trim().take(MAX_QUERY_LENGTH)
        backStack.value = listOf(Screen.Home, Screen.Search)
        setSearchQuery(query)
        val library = withContext(Dispatchers.Default) { Search.query(uiState.value.movies, query, limit = REMOTE_RESULTS) }
        val web = withTimeoutOrNull(REMOTE_WEB_WAIT_MS) {
            webSearch.first { it.query == query.trim() && !it.loading }
        }?.results.orEmpty()
        val lang = language.value
        return (library + web).distinctBy { it.id }.take(REMOTE_RESULTS)
            .map { PhoneRemoteServer.Result(it.id, it.displayTitle(lang), it.yearsLabel) }
    }

    /** A film or series picked on the phone; false when SCARY does not know it (or no longer shows it). */
    fun remoteOpen(id: String): Boolean {
        if (movieById(id) == null) return false
        openDetails(id)
        return true
    }

    /** A TMDB key pasted on the phone: checked and stored as from the key dialog; the phone gets the outcome. */
    suspend fun remoteTmdbKey(input: String): String = when (tmdb.setKey(input)) {
        TmdbKeyResult.SAVED -> {
            _tmdbDialog.value = null
            show(R.string.msg_tmdb_saved)
            "tmdb_saved"
        }
        TmdbKeyResult.INVALID_FORMAT -> "tmdb_invalid"
        TmdbKeyResult.REJECTED -> "tmdb_rejected"
        TmdbKeyResult.UNREACHABLE -> "tmdb_unreachable"
        TmdbKeyResult.STORAGE_FAILED -> "tmdb_storage"
    }

    /** Films or series on the home screen. The rows start again from the top and the hero shows the tab's pick. */
    fun selectTab(kind: MediaKind) {
        if (tab.value == kind) return
        tab.value = kind
        heroId.value = null
        lastFocusedKey = null
    }

    // Bucket list -----------------------------------------------------------------------------------------------------

    private val _bucketPrompt = MutableStateFlow<BucketPrompt?>(null)

    /** The question shown on coming back from a bucket-list film; null when there is none. */
    val bucketPrompt: StateFlow<BucketPrompt?> = _bucketPrompt.asStateFlow()

    /** The bucket list's screen, or picking straight away while it is empty. */
    fun openBucket() {
        if (latestPrefs?.bucketIds.isNullOrEmpty()) {
            startPicking()
        } else {
            picking.value = false
            push(Screen.Bucket)
        }
    }

    /** Picking mode on the films tab: OK on a poster adds or removes it. Back or "Done" shows the list. */
    fun startPicking() {
        tab.value = MediaKind.MOVIE
        backStack.value = listOf(Screen.Home)
        heroId.value = null
        lastFocusedKey = null
        picking.value = true
        _homeResets.update { it + 1 }
    }

    fun finishPicking() {
        picking.value = false
        backStack.value = listOf(Screen.Home, Screen.Bucket)
    }

    /** A film in or out of the bucket list. Not a series (it has no end), and not a locked film. */
    fun toggleBucket(movie: Movie, language: Language) {
        val title = movie.displayTitle(language)
        if (movie.isSeries) {
            show(R.string.msg_bucket_films_only)
            return
        }
        val prefs = latestPrefs
        if (prefs != null && ParentalGate.isLocked(prefs.parentalEnabled, isSessionUnlocked(), prefs.lockFromFear, movie)) {
            show(R.string.msg_bucket_locked, title)
            return
        }
        viewModelScope.launch {
            val before = settings.prefs.first().bucketIds
            if (movie.id !in before && before.size >= BucketList.MAX_SIZE) {
                show(R.string.msg_bucket_full, BucketList.MAX_SIZE.toString())
                return@launch
            }
            val after = settings.updateBucket { BucketList.toggle(it, movie.id) }
            val place = after.indexOf(movie.id)
            when {
                place >= 0 -> {
                    savedMovies.save(movie, language, keptOnline())
                    show(R.string.msg_bucket_added, title, (place + 1).toString())
                }
                // Not in before, still not in: the list filled up in between.
                movie.id !in before -> show(R.string.msg_bucket_full, BucketList.MAX_SIZE.toString())
                else -> show(R.string.msg_bucket_removed, title)
            }
        }
    }

    /** A film of the bucket list as it may be shown now (family night hides some); null when it is not available. */
    fun bucketMovie(id: String): Movie? = movieById(id)

    fun moveInBucket(id: String, by: Int) {
        viewModelScope.launch { settings.updateBucket { BucketList.move(it, id, by) } }
    }

    fun removeFromBucket(id: String) {
        viewModelScope.launch { settings.updateBucket { BucketList.remove(it, id) } }
    }

    fun clearBucket() {
        viewModelScope.launch {
            settings.updateBucket { emptyList() }
            settings.setBucketPlaying(null)
        }
        show(R.string.msg_bucket_cleared)
    }

    /** Opens the first film of the list in Stremio (through the PIN when it is locked); the rest follow on return. */
    fun startBucket() {
        val first = latestPrefs?.bucketIds.orEmpty().firstNotNullOfOrNull(::movieById)
        if (first == null) show(R.string.msg_bucket_empty) else playFromBucket(first)
    }

    /**
     * The bucket-list film being opened, until it really opens in Stremio (then it is stored as bucketPlaying). A PIN
     * left unanswered, a cancelled shiver or a missing Stremio leave nothing behind.
     */
    private var bucketLaunch: String? = null

    private fun playFromBucket(movie: Movie) {
        guard(movie, GatedAction.Watch(movie))
        bucketLaunch = movie.id
    }

    /**
     * Back from [current]: when it was watched to the end ([finished]) it leaves the list and the next one is offered;
     * back early, the viewer may return to it or go on. After the last film, a message says the list is done.
     */
    private suspend fun offerNextInBucket(current: Movie, finished: Boolean) {
        val list = if (finished) settings.updateBucket { BucketList.remove(it, current.id) } else settings.prefs.first().bucketIds
        val upcoming = if (finished) list else list.dropWhile { it != current.id }.drop(1)
        val next = upcoming.firstNotNullOfOrNull(::movieById)
        if (finished && next == null) {
            show(R.string.msg_bucket_done)
            return
        }
        _bucketPrompt.value = BucketPrompt(current, next, finished)
    }

    /** "Play the next one": a film left early is taken off the list first. */
    fun playNextInBucket() {
        val prompt = _bucketPrompt.value ?: return
        _bucketPrompt.value = null
        viewModelScope.launch {
            if (!prompt.finished) settings.updateBucket { BucketList.remove(it, prompt.current.id) }
            prompt.next?.let(::playFromBucket)
        }
    }

    /** Back to the film left early. */
    fun backToBucketFilm() {
        val prompt = _bucketPrompt.value ?: return
        _bucketPrompt.value = null
        playFromBucket(prompt.current)
    }

    /** "Not now": the list stays as it is, and "Start" goes on from its first film. */
    fun dismissBucketPrompt() {
        _bucketPrompt.value = null
    }

    /** Debug builds only (screenshots): the question as it shows after a film watched to the end. */
    fun debugBucketPrompt() {
        val films = visibleCatalog()?.ofKind(MediaKind.MOVIE)?.movies ?: return
        _bucketPrompt.value = BucketPrompt(films[0], films.getOrNull(1), finished = true)
    }

    /** Debug builds only (screenshots): a bucket list of the first three classics. */
    fun debugFillBucket() {
        val ids = visibleCatalog()?.ofKind(MediaKind.MOVIE)?.movies?.take(3)?.map { it.id } ?: return
        viewModelScope.launch { settings.updateBucket { ids } }
    }

    /**
     * "Your year in fear": this calendar year (films marked as watched with a date) and all time. Watched films no
     * longer in the library are left out of the details but still counted.
     */
    suspend fun fearStats(): Pair<FearStats, FearStats> {
        val prefs = settings.prefs.first()
        val year = today().year
        val inYear = { id: String -> prefs.watchedDays[id]?.let { LocalDate.ofEpochDay(it).year == year } == true }
        val thisYearIds = prefs.watchedIds.filter(inYear)
        // The whole library, not only what family night shows; a film no longer at hand still counts, without details.
        val whole = (catalog.value as? CatalogState.Ready)?.catalog?.let { raw ->
            withContext(Dispatchers.Default) { shown(raw, extraPages.value, prefs.onlineEnabled, familyNight = false) }
        }
        val lookUp = { id: String -> whole?.movie(id) ?: webMovies[id] }
        // A locked film, or any film on family night, is not named.
        val hideScariest = { stats: FearStats ->
            val scariest = stats.scariest
            if (scariest != null && (prefs.familyNight || ParentalGate.isLocked(prefs.parentalEnabled, isSessionUnlocked(), prefs.lockFromFear, scariest))) {
                stats.copy(scariest = null)
            } else {
                stats
            }
        }
        val thisYear = FearStats.of(thisYearIds.mapNotNull(lookUp), prefs.lovedIds.count(inYear)).copy(watchedCount = thisYearIds.size)
        val allTime = FearStats.of(prefs.watchedIds.mapNotNull(lookUp), prefs.lovedIds.size).copy(watchedCount = prefs.watchedIds.size)
        return hideScariest(thisYear) to hideScariest(allTime)
    }

    /** Picks a mood, or clears it when the same mood is chosen again. */
    fun toggleMood(selected: Mood) {
        mood.update { if (it == selected) null else selected }
    }

    /** Picks a decade (1980 for the 1980s), or clears it when the same one is chosen again. */
    fun toggleDecade(selected: Int) {
        decade.update { if (it == selected) null else selected }
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

    // Last in the class: the work it starts runs at once on the main thread (Main.immediate) and can reach any field
    // before its first suspension, so every field must be initialized by then.
    init {
        loadCatalog()
        refreshApps()
        viewModelScope.launch { settings.prefs.collect { latestPrefs = it } }
    }

    companion object {
        private const val TAG = "HomeViewModel"
        /** Off unless "setprop log.tag.ScaryKeys DEBUG", as in FocusSurface: lets a failed UI test show what happened. */
        private const val KEY_LOG_TAG = "ScaryKeys"
        private const val STOP_TIMEOUT_MS = 5_000L
        /** How often to look whether online data is old while SCARY stays on screen; a look fetches only what is due. */
        private const val REFRESH_CHECK_MS = 30 * 60_000L

        /** A sync with the TMDB account when SCARY is back on screen, at most this often. */
        private const val ACCOUNT_SYNC_GAP_MS = 15 * 60_000L

        /** A change to My list or "Loved it" goes to the account this long after the last one (several in a row: once). */
        private const val ACCOUNT_SYNC_DEBOUNCE_MS = 5_000L
        private const val LAUNCH_SHIVER_MS = 1_600L
        private const val MAX_EMPTY_PAGES = 12

        /** A library category's pages in [extraPages], next to the online rows' ids. */
        private const val CATEGORY_PREFIX = "cat:"
        private const val MAX_TASTE_CANDIDATES = 120

        /** How much the taste may move a film in "More like this": up to one shared genre's worth. */
        private const val TASTE_WEIGHT = 1.5
        private const val TASTE_CAP = 4.0
        private const val REMOTE_RESULTS = 15
        private const val REMOTE_WEB_WAIT_MS = 5_000L
        private const val GOOGLE_TV_LABEL = "Google TV"
        private const val LAUNCH_STILL_MS = 400L
        private const val LAUNCH_CLEAR_MS = 1_500L
        private const val MAX_QUERY_LENGTH = 80
        private const val WEB_SEARCH_DELAY_MS = 700L
        /** Index films added to each library subgenre row. */
        private const val INDEX_PER_ROW = 40

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
                    container.regionDetector,
                    container.tvInputsRepository,
                    container.networkWatcher.online,
                    container.tmdbAccount,
                    container.aiRepository,
                    container.realDebrid,
                    container.watchStore,
                )
            }
        }
    }
}
