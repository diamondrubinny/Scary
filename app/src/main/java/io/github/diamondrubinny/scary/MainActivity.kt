// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import io.github.diamondrubinny.scary.audio.AmbientPlayer
import io.github.diamondrubinny.scary.core.catalog.Mood
import io.github.diamondrubinny.scary.core.model.Language
import io.github.diamondrubinny.scary.data.LanguageStore
import io.github.diamondrubinny.scary.ui.theme.ScaryFonts
import io.github.diamondrubinny.scary.launch.HomeRole
import io.github.diamondrubinny.scary.stability.CrashGuard
import io.github.diamondrubinny.scary.ui.home.LauncherStatus
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import io.github.diamondrubinny.scary.ui.ScaryRoot
import io.github.diamondrubinny.scary.ui.home.HomeViewModel
import io.github.diamondrubinny.scary.ui.home.DocumentKind

class MainActivity : ComponentActivity() {

    private val viewModel: HomeViewModel by viewModels {
        HomeViewModel.factory((application as ScaryApplication).container)
    }

    private val ambient = AmbientPlayer()

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleOverride.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val language = LanguageStore.read(this)
        ScaryFonts.useFor(language)
        viewModel.setLanguage(language)
        if (savedInstanceState == null && CrashGuard.consumeCrashLoop(this)) viewModel.enterSafeMode()
        setContent { ScaryRoot(viewModel) }
        handleDebugIntent(intent)

        val settings = (application as ScaryApplication).container.settingsRepository
        lifecycleScope.launch {
            // Sound only while the launcher is visible; leaving for Stremio stops it.
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                // A trailer playing with sound has the room to itself meanwhile.
                combine(settings.prefs.map { it.soundsEnabled }, viewModel.trailerAudible) { sounds, trailer -> sounds && !trailer }
                    .distinctUntilChanged()
                    .collect { enabled -> if (enabled) ambient.start() else ambient.stop() }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        ambient.stop()
        viewModel.onBackgrounded()
    }

    override fun onResume() {
        super.onResume()
        // Apps may have been installed or removed while another app was in front.
        viewModel.refreshApps()
        // Back from a movie in Stremio (or another app): it may count as watched.
        viewModel.onResumed()
        viewModel.updateLauncherStatus(
            LauncherStatus(HomeRole.isDefaultHome(this), HomeRole.isHomeButtonServiceOn(this)),
        )
    }

    /** The remote's search key, on remotes that deliver it to the app rather than to the assistant. */
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_SEARCH) {
            viewModel.openSearch()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onSearchRequested(): Boolean {
        viewModel.openSearch()
        return true
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.hasCategory(Intent.CATEGORY_HOME)) viewModel.onHomePressed()
        handleDebugIntent(intent)
    }

    /**
     * Debug builds only: lets CI open a specific screen for automated screenshots, e.g.
     * `adb shell am start -n <pkg>/.MainActivity --es scary.debug.open search --es scary.debug.query fincher`.
     * Release builds are not debuggable, so this does nothing there.
     */
    private fun handleDebugIntent(intent: Intent?) {
        if (intent == null || applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE == 0) return
        val target = intent.getStringExtra(DEBUG_OPEN) ?: return
        viewModel.onHomePressed()
        when {
            target == "search" -> {
                viewModel.setSearchQuery(intent.getStringExtra(DEBUG_QUERY).orEmpty())
                viewModel.openSearch()
            }
            target == "apps" -> viewModel.openAllApps()
            target == "settings" -> viewModel.openSettings()
            target == "pin" -> viewModel.startPinSetup()
            target == "home" -> viewModel.onHomePressed()
            target == "surprise" -> viewModel.surprise()
            target == "family" -> viewModel.toggleFamilyNight()
            target == "trailer-intro" -> viewModel.debugShowTrailerIntro()
            target == "trailers:off" || target == "trailers:on" -> viewModel.debugSetTrailers(target == "trailers:on")
            // A home row brought into view, e.g. "row:moods" or "row:season:halloween"; with "mood:<key>", that mood
            // is chosen and its chip focused.
            target.startsWith("row:") -> viewModel.debugShowRow(target.removePrefix("row:"), focusKey = null)
            target.startsWith("mood:") -> Mood.fromKey(target.removePrefix("mood:"))?.let { mood ->
                viewModel.toggleMood(mood)
                viewModel.debugShowRow("moods", focusKey = "moods:chip:${mood.key}")
            }
            target == "privacy" || target == "accessibility" -> {
                viewModel.openSettings()
                viewModel.openDocument(if (target == "privacy") DocumentKind.Privacy else DocumentKind.Accessibility)
            }
            target.startsWith("language:") -> {
                // Consume the target first, or the recreated activity would read it again and loop.
                intent.removeExtra(DEBUG_OPEN)
                this.intent?.removeExtra(DEBUG_OPEN)
                LanguageStore.write(this, Language.fromCode(target.removePrefix("language:")))
                recreate()
            }
            target == "tmdb" -> {
                viewModel.openSettings()
                viewModel.openTmdbDialog()
            }
            target.startsWith("details:") -> viewModel.openDetails(target.removePrefix("details:"))
            // A real uncaught crash, so CI can check that three in a row start SCARY in safe mode.
            target == "crash" -> throw IllegalStateException("Debug crash")
        }
    }

    private companion object {
        const val DEBUG_OPEN = "scary.debug.open"
        const val DEBUG_QUERY = "scary.debug.query"
    }
}
