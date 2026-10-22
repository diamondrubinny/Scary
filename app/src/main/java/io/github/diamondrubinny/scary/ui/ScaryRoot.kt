package io.github.diamondrubinny.scary.ui

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

@Composable
fun ScaryRoot(viewModel: HomeViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val screen by viewModel.screen.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val resets by viewModel.homeResets.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val pinDialog by viewModel.pinDialog.collectAsStateWithLifecycle()
    val launcherStatus by viewModel.launcherStatus.collectAsStateWithLifecycle()
    val tmdbKeyMask by viewModel.tmdbKeyMask.collectAsStateWithLifecycle()
    val tmdbDialog by viewModel.tmdbDialog.collectAsStateWithLifecycle()
    val webSearch by viewModel.webSearch.collectAsStateWithLifecycle()
    val language = Language.fromCode(LocalConfiguration.current.locales[0].language)
    val settingsLabel = stringResource(R.string.system_settings)

    // Scroll positions survive a trip to the details screen and back.
    val homeListState = rememberLazyListState()
    val rowStates = remember { mutableMapOf<String, LazyListState>() }
    LaunchedEffect(resets) {
        if (resets == 0) return@LaunchedEffect
        homeListState.scrollToItem(0)
        rowStates.values.forEach { it.scrollToItem(0) }
    }

    // Back on the home screen is swallowed: a launcher has nowhere to go back to.
    BackHandler { viewModel.back() }

    val context = LocalContext.current

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
        onSetupPin = viewModel::startPinSetup,
        onCycleLockLevel = viewModel::cycleLockLevel,
        onTurnOffLock = viewModel::turnOffParental,
        onToggleFamilyNight = viewModel::toggleFamilyNight,
        onToggleOnline = viewModel::toggleOnline,
        onRequestDefaultHome = ::requestDefaultHome,
        onOpenAccessibility = ::openAccessibility,
        onOpenTmdb = viewModel::openTmdbDialog,
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

    ScaryTheme(reduceMotion = !state.effectsEnabled || !systemAnimations) {
        Box(Modifier.fillMaxSize().background(ScaryColors.Void)) {
            val current = screen
            val details = (current as? Screen.Details)?.let { viewModel.details(it.movieId) }
            MovieBackdrop(if (current == Screen.Home || details != null) details?.movie ?: state.hero else null)
            Atmosphere(animate = !LocalReduceMotion.current)

            when {
                state.loading -> LoadingView()
                current == Screen.Search -> SearchScreen(
                    movies = state.movies,
                    query = searchQuery,
                    onQueryChange = viewModel::setSearchQuery,
                    onOpenMovie = { viewModel.openDetails(it.id) },
                    language = language,
                    isLocked = state::isLocked,
                    webSearch = webSearch,
                )
                current is Screen.Document -> DocumentScreen(current.kind)
                current == Screen.AllApps -> AllAppsScreen(apps = state.drawer, actions = allAppsActions)
                current == Screen.Settings -> SettingsScreen(
                    language = language,
                    effectsEnabled = state.effectsEnabled,
                    nightMaxFear = state.nightMaxFear,
                    soundsEnabled = state.soundsEnabled,
                    parentalEnabled = state.parentalEnabled,
                    lockFromFear = state.lockFromFear,
                    familyNight = state.familyNight,
                    onlineEnabled = state.onlineEnabled,
                    isDefaultHome = launcherStatus.isDefaultHome,
                    homeButtonServiceOn = launcherStatus.homeButtonServiceOn,
                    tmdbKeyMask = tmdbKeyMask,
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
                )
                else -> HomeScreen(
                    state = state,
                    language = language,
                    listState = homeListState,
                    rowStates = { key -> rowStates.getOrPut(key) { LazyListState() } },
                    restoreKey = viewModel.lastFocusedKey,
                    resets = resets,
                    actions = actions,
                    showSetHome = launcherStatus.needsSetup,
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
