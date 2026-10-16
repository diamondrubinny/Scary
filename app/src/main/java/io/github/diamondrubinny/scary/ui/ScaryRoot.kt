package io.github.diamondrubinny.scary.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.diamondrubinny.scary.R
import io.github.diamondrubinny.scary.core.model.Language
import io.github.diamondrubinny.scary.data.LanguageStore
import io.github.diamondrubinny.scary.data.SettingsRepository
import io.github.diamondrubinny.scary.ui.apps.AllAppsActions
import io.github.diamondrubinny.scary.ui.apps.AllAppsScreen
import io.github.diamondrubinny.scary.ui.search.SearchScreen
import io.github.diamondrubinny.scary.ui.settings.SettingsActions
import io.github.diamondrubinny.scary.ui.settings.SettingsScreen
import io.github.diamondrubinny.scary.ui.components.MessageBanner
import io.github.diamondrubinny.scary.ui.details.DetailsScreen
import io.github.diamondrubinny.scary.ui.fx.Atmosphere
import io.github.diamondrubinny.scary.ui.fx.HeroBackdrop
import io.github.diamondrubinny.scary.ui.home.AppTile
import io.github.diamondrubinny.scary.ui.home.HomeActions
import io.github.diamondrubinny.scary.ui.home.HomeScreen
import io.github.diamondrubinny.scary.ui.home.HomeViewModel
import io.github.diamondrubinny.scary.ui.home.LoadingView
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

    val actions = remember(viewModel, settingsLabel) {
        HomeActions(
            onCardFocused = viewModel::onCardFocused,
            onOpenDetails = { viewModel.openDetails(it.id) },
            onWatch = viewModel::watch,
            onTrailer = viewModel::trailer,
            onTile = { viewModel.openTile(it, settingsLabel) },
            onToggleEffects = viewModel::toggleEffects,
            onOpenSettings = viewModel::openSettings,
            onOpenSearch = viewModel::openSearch,
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
    val context = LocalContext.current
    val settingsActions = SettingsActions(
        onToggleLanguage = {
            LanguageStore.write(context, if (language == Language.HE) Language.EN else Language.HE)
            context.findActivity()?.recreate()
        },
        onToggleEffects = viewModel::toggleEffects,
        onCycleNightFear = { viewModel.setNightMaxFear(state.nightMaxFear % SettingsRepository.MAX_FEAR + 1) },
        onSystemSettings = { viewModel.openTile(AppTile.SystemSettings, settingsLabel) },
    )
    val versionName = remember(context) { context.versionName() }

    ScaryTheme(reduceMotion = !state.effectsEnabled) {
        Box(Modifier.fillMaxSize().background(ScaryColors.Void)) {
            val current = screen
            val details = (current as? Screen.Details)?.let { viewModel.details(it.movieId) }
            HeroBackdrop(if (current == Screen.Home || details != null) details?.movie ?: state.hero else null)
            Atmosphere(animate = !LocalReduceMotion.current)

            when {
                state.loading -> LoadingView()
                current == Screen.Search -> SearchScreen(
                    movies = state.movies,
                    query = searchQuery,
                    onQueryChange = viewModel::setSearchQuery,
                    onOpenMovie = { viewModel.openDetails(it.id) },
                )
                current == Screen.AllApps -> AllAppsScreen(apps = state.drawer, actions = allAppsActions)
                current == Screen.Settings -> SettingsScreen(
                    language = language,
                    effectsEnabled = state.effectsEnabled,
                    nightMaxFear = state.nightMaxFear,
                    versionName = versionName,
                    actions = settingsActions,
                )
                details != null -> DetailsScreen(
                    details = details,
                    language = language,
                    onWatch = viewModel::watch,
                    onTrailer = viewModel::trailer,
                    onOpenSimilar = { viewModel.openDetails(it.id) },
                )
                else -> HomeScreen(
                    state = state,
                    language = language,
                    listState = homeListState,
                    rowStates = { key -> rowStates.getOrPut(key) { LazyListState() } },
                    restoreKey = viewModel.lastFocusedKey,
                    resets = resets,
                    actions = actions,
                )
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
