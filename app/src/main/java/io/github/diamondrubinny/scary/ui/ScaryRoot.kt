// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary.ui

import io.github.diamondrubinny.scary.ui.details.LaunchOverlay
import android.app.Activity
import android.content.Context
import android.content.ActivityNotFoundException
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import io.github.diamondrubinny.scary.launch.HomeRole
import io.github.diamondrubinny.scary.ui.home.LauncherStatus
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.currentStateAsState
import io.github.diamondrubinny.scary.R
import io.github.diamondrubinny.scary.core.model.Language
import io.github.diamondrubinny.scary.data.LanguageStore
import io.github.diamondrubinny.scary.data.SettingsRepository
import io.github.diamondrubinny.scary.ui.apps.AllAppsActions
import io.github.diamondrubinny.scary.ui.apps.AllAppsScreen
import io.github.diamondrubinny.scary.ui.pin.PinDialog
import io.github.diamondrubinny.scary.ui.search.SearchScreen
import io.github.diamondrubinny.scary.ui.settings.SettingsActions
import io.github.diamondrubinny.scary.ui.settings.DocumentScreen
import io.github.diamondrubinny.scary.ui.settings.SettingsScreen
import io.github.diamondrubinny.scary.ui.settings.TmdbKeyDialog
import io.github.diamondrubinny.scary.ui.settings.CountryPicker
import io.github.diamondrubinny.scary.ui.components.MessageBanner
import io.github.diamondrubinny.scary.ui.details.DetailsScreen
import io.github.diamondrubinny.scary.ui.fx.Atmosphere
import io.github.diamondrubinny.scary.ui.fx.MovieBackdrop
import io.github.diamondrubinny.scary.ui.home.AppTile
import io.github.diamondrubinny.scary.ui.home.HomeActions
import io.github.diamondrubinny.scary.ui.home.HomeScreen
import io.github.diamondrubinny.scary.ui.home.HomeViewModel
import io.github.diamondrubinny.scary.ui.home.LoadingView
import io.github.diamondrubinny.scary.ui.home.DocumentKind
import io.github.diamondrubinny.scary.ui.home.Screen
import io.github.diamondrubinny.scary.ui.theme.LocalReduceMotion
import io.github.diamondrubinny.scary.ui.theme.ScaryColors
import io.github.diamondrubinny.scary.ui.theme.ScaryTheme
import io.github.diamondrubinny.scary.ui.trailer.TrailerIntroDialog
import io.github.diamondrubinny.scary.ui.stats.StatsScreen
import io.github.diamondrubinny.scary.system.SleepTimer
import io.github.diamondrubinny.scary.ui.components.minutesLeft
import io.github.diamondrubinny.scary.ui.trailer.TrailerSettings

@Composable
fun ScaryRoot(viewModel: HomeViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val screen by viewModel.screen.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val resets by viewModel.homeResets.collectAsStateWithLifecycle()
    val scrollToRow by viewModel.scrollToRow.collectAsStateWithLifecycle()
    val launching by viewModel.launching.collectAsStateWithLifecycle()
    val watchOptions by viewModel.watchOptions.collectAsStateWithLifecycle()
    val canSearchEverywhere by viewModel.canSearchEverywhere.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val pinDialog by viewModel.pinDialog.collectAsStateWithLifecycle()
    val launcherStatus by viewModel.launcherStatus.collectAsStateWithLifecycle()
    val tmdbKeyMask by viewModel.tmdbKeyMask.collectAsStateWithLifecycle()
    val tmdbDialog by viewModel.tmdbDialog.collectAsStateWithLifecycle()
    val webSearch by viewModel.webSearch.collectAsStateWithLifecycle()
    val rentOrBuy by viewModel.rentOrBuy.collectAsStateWithLifecycle()
    val rentCountry by viewModel.rentCountry.collectAsStateWithLifecycle()
    val trailerIntro by viewModel.trailerIntro.collectAsStateWithLifecycle()
    var countryPicker by remember { mutableStateOf(false) }
    val language = Language.fromCode(LocalConfiguration.current.locales[0].language)
    val settingsLabel = stringResource(R.string.system_settings)

    // Scroll positions survive a trip to the details screen and back.
    val homeListState = rememberLazyListState()
    // Search keeps its scroll position and the movie it opened, so Back from that movie lands on it again.
    val searchGrid = rememberLazyGridState()
    var searchOpenedId by remember { mutableStateOf<String?>(null) }
    val rowStates = remember { mutableMapOf<String, LazyListState>() }
    LaunchedEffect(resets) {
        if (resets == 0) return@LaunchedEffect
        homeListState.scrollToItem(0)
        rowStates.values.forEach { it.scrollToItem(0) }
    }

    // Back on the home screen is swallowed: a launcher has nowhere to go back to.
    BackHandler { viewModel.back() }

    val context = LocalContext.current

    // A sleep timer saved before the TV restarted has lost its alarm: it shows as off.
    val sleepSaved = state.sleepAtMillis != null
    LaunchedEffect(sleepSaved) {
        if (sleepSaved && !SleepTimer.isSet(context)) viewModel.setSleepAt(null)
    }

    // Becoming the home screen: the system dialog where the TV offers it, otherwise the Home button service.
    fun refreshLauncherStatus() = viewModel.updateLauncherStatus(
        LauncherStatus(HomeRole.isDefaultHome(context), HomeRole.isHomeButtonServiceOn(context)),
    )
    fun openAccessibility() {
        try {
            context.startActivity(HomeRole.accessibilitySettingsIntent())
            viewModel.showMessage(R.string.msg_enable_service)
        } catch (e: ActivityNotFoundException) {
            viewModel.showMessage(R.string.settings_home_note)
        }
    }
    val roleRequest = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        refreshLauncherStatus()
        if (!HomeRole.isDefaultHome(context) && !HomeRole.isHomeButtonServiceOn(context)) openAccessibility()
    }
    fun requestDefaultHome() {
        val intent = HomeRole.requestRoleIntent(context)
        if (intent == null) {
            openAccessibility()
        } else {
            try {
                roleRequest.launch(intent)
            } catch (e: ActivityNotFoundException) {
                openAccessibility()
            }
        }
    }
    val actions = remember(viewModel, settingsLabel, language) {
        HomeActions(
            onCardFocused = viewModel::onCardFocused,
            onOpenDetails = { viewModel.openDetails(it.id) },
            onWatch = viewModel::watch,
            onTrailer = viewModel::trailer,
            onTile = { tile -> if (tile == AppTile.SetAsHome) requestDefaultHome() else viewModel.openTile(tile, settingsLabel) },
            onToggleEffects = viewModel::toggleEffects,
            onOpenSettings = viewModel::openSettings,
            onOpenSearch = viewModel::openSearch,
            onToggleMyList = { viewModel.toggleMyList(it, language) },
            onSurprise = viewModel::surprise,
            onMood = viewModel::toggleMood,
            onDecade = viewModel::toggleDecade,
            onNearRowEnd = viewModel::onNearRowEnd,
            onTimeBudget = viewModel::toggleTimeBudget,
        )
    }
    val allAppsActions = remember(viewModel) {
        AllAppsActions(
            onLaunch = viewModel::launchApp,
            onTogglePinned = viewModel::togglePinned,
            onMovePinned = viewModel::movePinned,
            onToggleHidden = viewModel::toggleHidden,
            onAppInfo = viewModel::openAppInfo,
        )
    }
    // Which document settings opened last, so focus can return to its row. Forgotten once settings close.
    var lastDocument by remember { mutableStateOf<DocumentKind?>(null) }
    LaunchedEffect(screen) {
        if (screen != Screen.Settings && screen !is Screen.Document) lastDocument = null
    }
    val settingsActions = SettingsActions(
        onToggleLanguage = {
            LanguageStore.write(context, language.next())
            context.findActivity()?.recreate()
        },
        onToggleEffects = viewModel::toggleEffects,
        onCycleNightFear = { viewModel.setNightMaxFear(state.nightMaxFear % SettingsRepository.MAX_FEAR + 1) },
        onSystemSettings = { viewModel.openTile(AppTile.SystemSettings, settingsLabel) },
        onToggleSounds = viewModel::toggleSounds,
        onToggleTrailerAutoplay = viewModel::toggleTrailerAutoplay,
        onToggleTrailerSound = viewModel::toggleTrailerSound,
        onCycleSleepTimer = {
            // Off, 30, 60, 90, 120 minutes, then off again: the next choice above the minutes left.
            val next = SleepTimer.next(state.sleepAtMillis?.let(::minutesLeft) ?: 0)
            if (next == 0) {
                SleepTimer.cancel(context)
                viewModel.setSleepAt(null)
            } else {
                viewModel.setSleepAt(SleepTimer.start(context, next))
            }
        },
        onOpenStats = viewModel::openStats,
        onSetupPin = viewModel::startPinSetup,
        onCycleLockLevel = viewModel::cycleLockLevel,
        onTurnOffLock = viewModel::turnOffParental,
        onToggleFamilyNight = viewModel::toggleFamilyNight,
        onToggleSeasonalRows = viewModel::toggleSeasonalRows,
        onToggleAutoWatched = viewModel::toggleAutoWatched,
        onToggleOnline = viewModel::toggleOnline,
        onRequestDefaultHome = ::requestDefaultHome,
        onOpenAccessibility = ::openAccessibility,
        onOpenTmdb = viewModel::openTmdbDialog,
        onOpenRentCountry = { countryPicker = true },
        onOpenScreensaver = {
            try {
                context.startActivity(Intent(Settings.ACTION_DREAM_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (e: ActivityNotFoundException) {
                viewModel.showMessage(R.string.msg_no_screensaver_settings)
            }
        },
        onOpenPrivacy = {
            lastDocument = DocumentKind.Privacy
            viewModel.openDocument(DocumentKind.Privacy)
        },
        onOpenAccessibilityStatement = {
            lastDocument = DocumentKind.Accessibility
            viewModel.openDocument(DocumentKind.Accessibility)
        },
    )
    val versionName = remember(context) { context.versionName() }

    // Re-read on every lifecycle change, so turning off animations in the TV settings applies on return.
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val systemAnimations = remember(lifecycleState) { SettingsRepository.systemAnimationsEnabled(context) }

    // Trailers play by themselves only while SCARY is in front and nothing is over the screen. Not offline, not in
    // family night (YouTube may show ads and suggestions), and not when the TV's "Remove animations" is on.
    val trailersEnabled = state.trailerAutoplay && state.onlineEnabled && !state.familyNight && systemAnimations &&
        lifecycleState.isAtLeast(Lifecycle.State.RESUMED) &&
        launching == null && pinDialog == null && tmdbDialog == null && !countryPicker && !trailerIntro
    val trailerSettings = remember(viewModel, trailersEnabled, state.trailerSound, state.trailerIntroSeen) {
        TrailerSettings(
            enabled = trailersEnabled,
            muted = !state.trailerSound,
            introSeen = state.trailerIntroSeen,
            onNeedIntro = viewModel::requestTrailerIntro,
            onAudible = viewModel::setTrailerAudible,
        )
    }

    ScaryTheme(reduceMotion = !state.effectsEnabled || !systemAnimations) {
        Box(Modifier.fillMaxSize().background(ScaryColors.Void)) {
            val current = screen
            val details = (current as? Screen.Details)?.let { viewModel.details(it.movieId) }
            val backdropMovie = if (current == Screen.Home || details != null) details?.movie ?: state.hero else null
            // A locked movie's art stays off the screen, as on its card.
            MovieBackdrop(backdropMovie?.let { if (state.isLocked(it)) it.copy(posterUrl = null, backgroundUrl = null, logoUrl = null) else it })
            Atmosphere(animate = !LocalReduceMotion.current)

            when {
                state.loading -> LoadingView()
                current == Screen.Search -> SearchScreen(
                    movies = state.movies,
                    query = searchQuery,
                    onQueryChange = viewModel::setSearchQuery,
                    onOpenMovie = {
                        searchOpenedId = it.id
                        viewModel.openDetails(it.id)
                    },
                    language = language,
                    isLocked = state::isLocked,
                    isWatched = state::isWatched,
                    gridState = searchGrid,
                    returnTo = searchOpenedId,
                    webSearch = webSearch,
                    onSurprise = viewModel::surprise,
                )
                current is Screen.Document -> DocumentScreen(current.kind)
                current == Screen.AllApps -> AllAppsScreen(apps = state.drawer, actions = allAppsActions)
                current == Screen.Settings -> SettingsScreen(
                    language = language,
                    effectsEnabled = state.effectsEnabled,
                    nightMaxFear = state.nightMaxFear,
                    soundsEnabled = state.soundsEnabled,
                    trailerAutoplay = state.trailerAutoplay,
                    trailerSound = state.trailerSound,
                    sleepAtMillis = state.sleepAtMillis,
                    parentalEnabled = state.parentalEnabled,
                    lockFromFear = state.lockFromFear,
                    familyNight = state.familyNight,
                    seasonalRows = state.seasonalRows,
                    autoWatched = state.autoWatched,
                    onlineEnabled = state.onlineEnabled,
                    isDefaultHome = launcherStatus.isDefaultHome,
                    homeButtonServiceOn = launcherStatus.homeButtonServiceOn,
                    tmdbKeyMask = tmdbKeyMask,
                    rentCountryName = rentCountry?.let { viewModel.countryName(it.code, language) },
                    rentCountryAutomatic = rentCountry?.automatic ?: true,
                    versionName = versionName,
                    actions = settingsActions,
                    returningFrom = lastDocument,
                )
                details != null -> DetailsScreen(
                    details = details,
                    language = language,
                    onWatch = viewModel::watch,
                    onTrailer = viewModel::trailer,
                    onOpenSimilar = { viewModel.openDetails(it.id) },
                    isLocked = state::isLocked,
                    inMyList = details.movie.id in state.myListIds,
                    onToggleMyList = { viewModel.toggleMyList(it, language) },
                    watched = state.isWatched(details.movie),
                    isWatched = state::isWatched,
                    onToggleWatched = { viewModel.toggleWatched(it, language) },
                    watchOptions = watchOptions,
                    canSearchEverywhere = canSearchEverywhere,
                    onSearchIn = viewModel::searchIn,
                    onSearchEverywhere = viewModel::searchEverywhere,
                    rentOrBuy = rentOrBuy?.takeIf { it.movieId == details.movie.id }?.state,
                    countryName = { viewModel.countryName(it, language) },
                    onOffer = viewModel::openOffer,
                    onLoadOffers = viewModel::loadRentOrBuy,
                    offersKey = listOf(tmdbKeyMask, rentCountry, state.onlineEnabled),
                    trailer = trailerSettings,
                    loved = details.movie.id in state.lovedIds,
                    notForMe = details.movie.id in state.dislikedIds,
                    onToggleLoved = { viewModel.toggleLoved(it, language) },
                    onToggleNotForMe = { viewModel.toggleNotForMe(it, language) },
                )
                current == Screen.Stats -> StatsScreen(language = language, load = viewModel::fearStats)
                else -> HomeScreen(
                    state = state,
                    language = language,
                    listState = homeListState,
                    rowStates = { key -> rowStates.getOrPut(key) { LazyListState() } },
                    restoreKey = viewModel.lastFocusedKey,
                    resets = resets,
                    actions = actions,
                    showSetHome = launcherStatus.needsSetup,
                    scrollToRow = scrollToRow,
                    onScrolledToRow = viewModel::onScrolledToRow,
                    trailer = trailerSettings,
                )
            }

            if (countryPicker) {
                CountryPicker(
                    countries = remember(language) { viewModel.rentCountries(language) },
                    current = rentCountry?.takeUnless { it.automatic }?.code,
                    detected = remember(rentCountry) { viewModel.detectedCountry() }?.let { viewModel.countryName(it, language) },
                    countryName = { viewModel.countryName(it, language) },
                    onPick = { code ->
                        viewModel.setRentBuyCountry(code)
                        countryPicker = false
                    },
                    onDismiss = { countryPicker = false },
                )
            }

            tmdbDialog?.let { dialog ->
                TmdbKeyDialog(
                    state = dialog,
                    keyMask = tmdbKeyMask,
                    onSubmit = viewModel::submitTmdbKey,
                    onDelete = viewModel::clearTmdbKey,
                    onDismiss = viewModel::dismissTmdbDialog,
                )
            }

            if (trailerIntro) {
                TrailerIntroDialog(
                    onPlay = { viewModel.answerTrailerIntro(true) },
                    onTurnOff = { viewModel.answerTrailerIntro(false) },
                    onDismiss = viewModel::dismissTrailerIntro,
                )
            }

            launching?.let { LaunchOverlay(it, language) }
            pinDialog?.let { dialog ->
                PinDialog(state = dialog, onSubmit = viewModel::submitPin, onDismiss = viewModel::dismissPin)
            }

            MessageBanner(
                message = message,
                onDismiss = viewModel::consumeMessage,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private fun Context.versionName(): String = try {
    val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
    } else {
        @Suppress("DEPRECATION")
        packageManager.getPackageInfo(packageName, 0)
    }
    info.versionName.orEmpty()
} catch (_: PackageManager.NameNotFoundException) {
    ""
}
