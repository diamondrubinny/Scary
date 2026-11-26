// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary

import io.github.diamondrubinny.scary.core.model.Catalog
import io.github.diamondrubinny.scary.core.model.MediaKind
import io.github.diamondrubinny.scary.core.model.Language
import io.github.diamondrubinny.scary.ui.components.BUCKET_BUTTON_TAG
import io.github.diamondrubinny.scary.ui.components.tabTag
import io.github.diamondrubinny.scary.ui.home.PICKING_BANNER_TAG
import io.github.diamondrubinny.scary.ui.bucket.BUCKET_SCREEN_TAG
import io.github.diamondrubinny.scary.ui.bucket.BUCKET_PROMPT_TAG
import io.github.diamondrubinny.scary.ui.bucket.bucketItemTag
import io.github.diamondrubinny.scary.ui.home.rowTag
import io.github.diamondrubinny.scary.ui.settings.TRAILER_AUTOPLAY_ROW_TAG
import io.github.diamondrubinny.scary.ui.trailer.TRAILER_INTRO_TAG
import io.github.diamondrubinny.scary.core.cinemeta.Cinemeta
import org.junit.Assume.assumeTrue
import androidx.compose.ui.test.performScrollToIndex
import android.content.Intent
import android.os.SystemClock
import android.view.ViewConfiguration
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import android.util.Log
import android.view.KeyEvent
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isNotSelected
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performScrollToKey
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.diamondrubinny.scary.core.catalog.FeedCatalog
import io.github.diamondrubinny.scary.core.catalog.LegacyLibrary
import io.github.diamondrubinny.scary.core.catalog.CatalogParser
import io.github.diamondrubinny.scary.core.catalog.Decades
import io.github.diamondrubinny.scary.core.catalog.Mood
import io.github.diamondrubinny.scary.core.links.KnownPackages
import io.github.diamondrubinny.scary.core.model.Movie
import io.github.diamondrubinny.scary.core.brand.Credit
import io.github.diamondrubinny.scary.ui.home.CREDIT_KEY
import io.github.diamondrubinny.scary.ui.components.NAV_WORD_TAG
import io.github.diamondrubinny.scary.ui.settings.SETTINGS_JUMP_TAG
import io.github.diamondrubinny.scary.ui.settings.settingsJumpTag
import io.github.diamondrubinny.scary.ui.details.BY_DIRECTOR_ROW_TAG
import io.github.diamondrubinny.scary.ui.details.REMIND_TAG
import io.github.diamondrubinny.scary.ui.details.CAST_NAME_TAG
import io.github.diamondrubinny.scary.ui.details.CAST_ROW_TAG
import io.github.diamondrubinny.scary.ui.person.PERSON_SCREEN_TAG
import io.github.diamondrubinny.scary.ui.settings.ORIGINS_ROW_TAG
import io.github.diamondrubinny.scary.ui.settings.HOME_ROWS_ROW_TAG
import io.github.diamondrubinny.scary.ui.settings.HOME_ROWS_SCREEN_TAG
import io.github.diamondrubinny.scary.ui.settings.HOME_ROWS_SHOW_ALL_TAG
import io.github.diamondrubinny.scary.ui.settings.homeRowChoiceTag
import io.github.diamondrubinny.scary.core.catalog.HomeRowChoices
import io.github.diamondrubinny.scary.core.catalog.SearchFilter
import io.github.diamondrubinny.scary.ui.search.SEARCH_FILTER_CLEAR_TAG
import io.github.diamondrubinny.scary.ui.search.SEARCH_FILTER_FEAR_TAG
import io.github.diamondrubinny.scary.ui.search.SEARCH_FILTER_KIND_TAG
import io.github.diamondrubinny.scary.core.backup.Backup
import io.github.diamondrubinny.scary.ui.home.HERO_TITLE_TAG
import io.github.diamondrubinny.scary.ui.home.HOME_ROWS_TAG
import io.github.diamondrubinny.scary.ui.settings.REFRESH_FILMS_ROW_TAG
import io.github.diamondrubinny.scary.ui.settings.SLEEP_ROW_TAG
import io.github.diamondrubinny.scary.ui.settings.STATS_ROW_TAG
import io.github.diamondrubinny.scary.ui.stats.STATS_SCREEN_TAG
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.printToString
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import android.net.Uri
import android.util.Base64
import android.view.View
import io.github.diamondrubinny.scary.core.remote.RemoteProtocol
import io.github.diamondrubinny.scary.core.remote.RemoteProtocol.Command
import io.github.diamondrubinny.scary.remote.PhoneRemoteServer
import io.github.diamondrubinny.scary.ui.remote.PHONE_REMOTE_QR_TAG
import io.github.diamondrubinny.scary.ui.remote.PHONE_REMOTE_TAG
import io.github.diamondrubinny.scary.ui.settings.PHONE_REMOTE_ROW_TAG
import java.net.Socket
import io.github.diamondrubinny.scary.ui.settings.TMDB_ACCOUNT_ROW_TAG
import io.github.diamondrubinny.scary.ui.settings.TMDB_ACCOUNT_TAG
import androidx.lifecycle.ViewModelProvider
import io.github.diamondrubinny.scary.ui.home.HomeRow
import io.github.diamondrubinny.scary.ui.settings.LANGUAGE_DIALOG_TAG
import io.github.diamondrubinny.scary.ui.settings.LANGUAGE_ROW_TAG
import io.github.diamondrubinny.scary.ui.settings.languageTag
import io.github.diamondrubinny.scary.core.ai.AiProvider
import io.github.diamondrubinny.scary.ui.ai.AI_CONSENT_TAG
import io.github.diamondrubinny.scary.ui.ai.AI_PROVIDER_TAG
import io.github.diamondrubinny.scary.ui.ai.AI_STATUS_TAG
import io.github.diamondrubinny.scary.ui.ai.ASK_CHAT_TAG
import io.github.diamondrubinny.scary.ui.ai.ASK_SCREEN_TAG
import io.github.diamondrubinny.scary.ui.ai.aiProviderTag
import io.github.diamondrubinny.scary.ui.ai.askTabTag
import io.github.diamondrubinny.scary.ui.components.ASK_BUTTON_TAG
import io.github.diamondrubinny.scary.ui.details.AI_ABOUT_TAG
import io.github.diamondrubinny.scary.ui.details.AI_ABOUT_TEXT_TAG
import io.github.diamondrubinny.scary.ui.settings.AI_PROVIDER_ROW_TAG
import io.github.diamondrubinny.scary.core.update.UpdateManifest
import io.github.diamondrubinny.scary.ui.update.UPDATE_DIALOG_TAG
import io.github.diamondrubinny.scary.ui.taste.TASTE_PICKER_TAG
import io.github.diamondrubinny.scary.ui.taste.tasteCardTag
import io.github.diamondrubinny.scary.ui.taste.tasteTabTag
import io.github.diamondrubinny.scary.ui.home.TASTE_INVITE_TAG
import io.github.diamondrubinny.scary.ui.settings.TASTE_ROW_TAG
import io.github.diamondrubinny.scary.ui.settings.CLEAR_RECENT_ROW_TAG
import io.github.diamondrubinny.scary.core.catalog.Personalize
import io.github.diamondrubinny.scary.ui.home.HomeViewModel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import android.os.ParcelFileDescriptor
import android.app.UiAutomation
import android.provider.Settings
import androidx.lifecycle.Lifecycle
import io.github.diamondrubinny.scary.launch.HomeButtonService

/**
 * The main flows, driven through the same semantics TalkBack uses. Text comes from the app's own resources, so the
 * tests pass in whichever language the app is set to. Each test puts back what it changed.
 */
@RunWith(AndroidJUnit4::class)
class ScaryUiTest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    /**
     * A TV is never in touch mode, but the test runner starts activities in it; the first remote key would then only
     * take the window out of it (Android swallows that key), so OK presses would not behave as on a TV.
     */
    @Before
    fun likeATv() {
        InstrumentationRegistry.getInstrumentation().setInTouchMode(false)
        // The library stays the one the tests read at their start: the emulator is online, and a newer feed downloaded
        // during the run would otherwise become the library between two tests.
        (rule.activity.application as ScaryApplication).container.feedRepository.frozen = true
    }

    private fun text(id: Int): String = rule.activity.getString(id)

    private fun waitFor(matcher: SemanticsMatcher, timeoutMillis: Long = TIMEOUT_MS) {
        waitUntilOrDescribe("${matcher.description} on screen", timeoutMillis) {
            rule.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty()
        }
    }

    /** Like waitUntil, but a timeout says what was on screen and what had the focus, so CI failures can be read. */
    private fun waitUntilOrDescribe(what: String, timeoutMillis: Long = TIMEOUT_MS, condition: () -> Boolean) {
        try {
            rule.waitUntil(timeoutMillis, condition)
        } catch (timeout: ComposeTimeoutException) {
            val focused = runCatching {
                rule.onAllNodes(isFocused()).fetchSemanticsNodes().joinToString { node ->
                    node.config.toString().take(FOCUS_DESCRIPTION_CHARS)
                }
            }.getOrDefault("?")
            val screen = runCatching { rule.onRoot().printToString(maxDepth = SCREEN_DEPTH) }.getOrDefault("?")
            throw AssertionError("Timed out waiting for $what.\nFocused: $focused\nScreen:\n${screen.take(SCREEN_CHARS)}", timeout)
        }
    }

    private fun waitForGone(matcher: SemanticsMatcher) {
        rule.waitUntil(TIMEOUT_MS) { rule.onAllNodes(matcher).fetchSemanticsNodes().isEmpty() }
    }

    /** The remote's Back key, as a real key event. */
    private fun pressBack() {
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        rule.waitForIdle()
    }

    private fun isInstalled(packageName: String): Boolean =
        runCatching { rule.activity.packageManager.getPackageInfo(packageName, 0) }.isSuccess

    private fun bundledMovies(): List<Movie> = bundledCatalog().movies

    private fun waitForHome() = waitFor(hasText(text(R.string.row_apps)))

    /**
     * Home sends the focus to "watch tonight's pick" one frame after it shows. A test that moves the focus itself waits
     * for that first, or the hero takes the focus back and the remote's OK lands there instead.
     */
    private fun waitForHomeFocus() {
        waitForHome()
        waitUntilOrDescribe("the first focus on the home screen") { rule.onAllNodes(isFocused()).fetchSemanticsNodes().isNotEmpty() }
        rule.waitForIdle()
    }

    /** On the home screen, whatever row it is scrolled to (the apps row is not composed once it scrolls away). */
    private fun onHome(): Boolean = rule.onAllNodes(hasTestTag(HOME_ROWS_TAG)).fetchSemanticsNodes().isNotEmpty()

    /** Runs [test], then [cleanup]; a failing cleanup never hides the test's own failure. */
    private fun withCleanup(test: () -> Unit, cleanup: () -> Unit) {
        try {
            test()
        } catch (failure: Throwable) {
            runCatching(cleanup)
            throw failure
        }
        cleanup()
    }

    /** Back until the home screen shows: the first Back may only close the on-screen keyboard. */
    private fun backToHome() {
        repeat(MAX_BACKS) {
            if (onHome()) return
            pressBack()
            // Let the screen change land before deciding whether another Back is needed.
            rule.waitForIdle()
        }
        waitUntilOrDescribe("the home screen") { onHome() }
    }

    @Test
    fun homeShowsTonightsPickAndTheAppsRow() {
        waitForHome()
        waitFor(hasText(text(R.string.night_pick)))
        rule.onNodeWithContentDescription(text(R.string.search_title)).assertExists()
        rule.onNodeWithContentDescription(text(R.string.surprise_me)).assertExists()
    }

    @Test
    fun theCreditsShowUnderTheLogoAndAtTheEndOfTheRows() {
        // Home's first focus (tonight's pick) scrolls the rows back to the top: scroll to the end only after it.
        waitForHomeFocus()
        // Required by the NOTICE file (Apache License 2.0); the build fails if it disappears from the screen.
        rule.onNode(hasText(Credit.SHORT)).assertExists()
        rule.onNodeWithTag(HOME_ROWS_TAG).performScrollToKey(CREDIT_KEY)
        waitFor(hasText(Credit.LINE))
        // Beside it, where the movie information and pictures come from (TMDB's API terms).
        waitFor(hasText(text(R.string.data_credit)))
    }

    @Test
    fun settingsSayWhereMoviesComeFromAndOfferTheDonationCode() {
        waitForHome()
        rule.onNodeWithContentDescription(text(R.string.settings_title)).performClick()
        // A build with the sources feature says what that build does.
        val sources = rule.activity.resources.getBoolean(R.bool.rd_sources_enabled)
        val notice = hasText(text(if (sources) R.string.settings_content_notice_sources else R.string.settings_content_notice))
        waitFor(notice)
        rule.onNode(notice).performScrollTo().assertExists()
        // The movie data credit (TMDB's terms) and the donation code are in the same About section.
        rule.onNode(hasText(text(R.string.data_credit))).performScrollTo().assertExists()
        rule.onNodeWithContentDescription(text(R.string.donate_qr_description)).performScrollTo().assertExists()
        // Diagnostics, for a photo when something goes wrong.
        rule.onNode(hasText(text(R.string.diag_title))).performScrollTo().assertExists()
        rule.onNode(hasText(text(R.string.diag_note))).performScrollTo().assertExists()
        backToHome()
    }

    /**
     * Settings offer to read the films again now and say when they last were; a second press right away is answered
     * without asking the service again. Diagnostics say whether the TV removes animations (why a title stays still).
     */
    @Test
    fun settingsOfferToRefreshTheFilmsNow() {
        waitForHome()
        // The night's film has its title on the home screen (it shivers when effects are on).
        rule.onNode(hasTestTag(HERO_TITLE_TAG)).assertExists()
        rule.onNodeWithContentDescription(text(R.string.settings_title)).performClick()
        waitFor(hasTestTag(REFRESH_FILMS_ROW_TAG))
        rule.onNode(hasTestTag(REFRESH_FILMS_ROW_TAG)).performScrollTo().performClick()
        waitFor(hasText(text(R.string.msg_refreshing)))
        rule.onNode(hasTestTag(REFRESH_FILMS_ROW_TAG)).performScrollTo().performClick()
        waitFor(hasText(text(R.string.msg_refresh_recent)))
        rule.onNode(hasText(text(R.string.diag_motion).substringBefore("%"), substring = true)).performScrollTo().assertExists()
        backToHome()
    }

    /**
     * Ease of use: the top bar's items are named in words, and the settings' jump bar takes the focus straight to a
     * section far down the list.
     */
    @Test
    fun iconsSayTheirNamesAndSettingsJumpToASection() {
        waitForHome()
        // A key press, so SCARY knows a remote is in use (a touch hides the focus marks, and the names with them).
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN)
        rule.waitForIdle()
        // The top bar is words: each item's name is on screen all the time, with or without the focus.
        rule.onNodeWithContentDescription(text(R.string.search_title)).assertExists()
        assertTrue(rule.onAllNodes(hasTestTag(NAV_WORD_TAG), useUnmergedTree = true).fetchSemanticsNodes().size >= 4)

        rule.onNodeWithContentDescription(text(R.string.settings_title)).performClick()
        waitFor(hasTestTag(SETTINGS_JUMP_TAG))
        // As with the remote: the focus goes to the jump button, and OK presses it (a key, so the focus stays in use).
        fun pressWithRemote(tag: String) {
            InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_UP)
            rule.waitForIdle()
            rule.onNode(hasTestTag(tag)).performScrollTo().performSemanticsAction(SemanticsActions.RequestFocus)
            rule.waitForIdle()
            InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER)
            rule.waitForIdle()
        }
        // "About" is the last section: its first row takes the focus, wherever the list was.
        pressWithRemote(settingsJumpTag("about"))
        waitFor(hasContentDescription(text(R.string.settings_system)) and isFocused())
        // And back up to the films and accounts.
        pressWithRemote(settingsJumpTag("films"))
        waitFor(hasContentDescription(text(R.string.settings_online), substring = true) and isFocused())
        // Every section has its heading in the list.
        listOf(R.string.settings_section_films, R.string.settings_section_display, R.string.settings_section_rows, R.string.settings_section_sound, R.string.settings_section_parents)
            .forEach { title -> rule.onAllNodes(hasText(text(title))).fetchSemanticsNodes().let { assertTrue("heading and jump button for ${text(title)}", it.size >= 2) } }
        backToHome()
    }

    /**
     * A film's page has a second row, the director's other films (from the library and the offline index, nothing
     * asked online); and Settings say whether Arab and Indian films are hidden, as SCARY comes.
     */
    @Test
    fun aFilmPageShowsMoreFromItsDirectorAndSettingsShowTheHiddenFilmsSwitch() {
        waitForHome()
        rule.onNodeWithContentDescription(text(R.string.search_title)).performClick()
        waitFor(hasSetTextAction())
        rule.onNode(hasSetTextAction()).performTextInput("prince of darkness")
        val film = hasContentDescription("Prince of Darkness", substring = true)
        waitFor(film)
        rule.onAllNodes(film)[0].performClick()
        waitFor(hasText(text(R.string.row_more_like)))
        // John Carpenter has five more films in the library.
        waitFor(hasText(rule.activity.getString(R.string.row_more_from_director, "John Carpenter")))
        rule.onNode(hasTestTag(BY_DIRECTOR_ROW_TAG)).performScrollTo().assertExists()
        backToHome()

        rule.onNodeWithContentDescription(text(R.string.settings_title)).performClick()
        waitFor(hasTestTag(ORIGINS_ROW_TAG))
        rule.onNode(hasTestTag(ORIGINS_ROW_TAG)).performScrollTo().assertExists()
        rule.onNode(hasText(text(R.string.value_origins_hidden))).assertExists()
        backToHome()
    }

    /** Settings → Home screen rows: a row turned off leaves the home screen, and "Show all the rows" brings it back. */
    @Test
    fun aHomeRowIsTurnedOffAndBackOn() {
        waitForHome()
        rule.onNodeWithContentDescription(text(R.string.settings_title)).performClick()
        waitFor(hasTestTag(HOME_ROWS_ROW_TAG))
        rule.onNode(hasTestTag(HOME_ROWS_ROW_TAG)).performScrollTo().performClick()
        waitFor(hasTestTag(HOME_ROWS_SCREEN_TAG))
        val moods = hasTestTag(homeRowChoiceTag(HomeRowChoices.MOODS))
        rule.onNode(hasTestTag(HOME_ROWS_SCREEN_TAG)).performScrollToNode(moods)
        waitFor(moods and hasContentDescription(text(R.string.value_row_shown), substring = true))
        rule.onNode(moods).performClick()
        waitFor(moods and hasContentDescription(text(R.string.value_row_hidden), substring = true))
        backToHome()
        waitForGone(hasText(text(R.string.row_moods)))

        rule.onNodeWithContentDescription(text(R.string.settings_title)).performClick()
        // Settings say how many rows are off.
        waitFor(hasTestTag(HOME_ROWS_ROW_TAG) and hasContentDescription(rule.activity.getString(R.string.home_rows_hidden_count, 1), substring = true))
        rule.onNode(hasTestTag(HOME_ROWS_ROW_TAG)).performScrollTo().performClick()
        waitFor(hasTestTag(HOME_ROWS_SCREEN_TAG))
        rule.onNode(hasTestTag(HOME_ROWS_SCREEN_TAG)).performScrollToNode(hasTestTag(HOME_ROWS_SHOW_ALL_TAG))
        rule.onNode(hasTestTag(HOME_ROWS_SHOW_ALL_TAG)).performClick()
        waitForGone(hasTestTag(HOME_ROWS_SHOW_ALL_TAG))
        backToHome()
    }

    /** Search filters: a fear level the film does not have takes it out of the results, and "Clear filters" brings it back. */
    @Test
    fun searchFiltersNarrowTheResultsAndClear() {
        waitForHome()
        rule.onNodeWithContentDescription(text(R.string.search_title)).performClick()
        waitFor(hasSetTextAction())
        rule.onNode(hasSetTextAction()).performTextInput("shining")
        val shining = hasContentDescription("The Shining", substring = true)
        waitFor(shining)
        rule.onNode(hasTestTag(SEARCH_FILTER_CLEAR_TAG)).assertDoesNotExist()
        // A band its scare rating is not in. Films from the web have no rating, so they fit no band either.
        val fear = bundledMovies().first { it.title == "The Shining" }.fear ?: error("The Shining has no scare rating")
        val other = SearchFilter.FearBand.entries.first { fear !in it.levels }
        repeat(other.ordinal + 1) { rule.onNode(hasTestTag(SEARCH_FILTER_FEAR_TAG)).performClick() }
        waitForGone(shining)
        waitFor(hasText(text(R.string.search_no_filter_results)))
        rule.onNode(hasTestTag(SEARCH_FILTER_CLEAR_TAG)).performClick()
        waitFor(shining)
        waitForGone(hasTestTag(SEARCH_FILTER_CLEAR_TAG))
        // With no text, a filter alone picks films: the 1980s, only films.
        rule.onNode(hasSetTextAction()).performTextClearance()
        rule.onNode(hasTestTag(SEARCH_FILTER_KIND_TAG)).performClick()
        waitFor(hasText(text(R.string.filter_kind_films)))
        waitUntilOrDescribe("films picked by the filter alone") { rule.onAllNodes(hasText(text(R.string.search_no_filter_results))).fetchSemanticsNodes().isEmpty() && rule.onAllNodes(hasTestTag(SEARCH_FILTER_CLEAR_TAG)).fetchSemanticsNodes().isNotEmpty() }
        rule.onNode(hasTestTag(SEARCH_FILTER_CLEAR_TAG)).performClick()
        backToHome()
    }

    /** "Remind me": a film whose day has come shows in "Now out"; taking the reminder off on its page takes it out. */
    @Test
    fun aRemindedFilmShowsInNowOutAndLeavesWhenTheReminderIsRemoved() {
        waitForHome()
        val container = (rule.activity.application as ScaryApplication).container
        val viewModel = ViewModelProvider(rule.activity, HomeViewModel.factory(container))[HomeViewModel::class.java]
        val settings = container.settingsRepository
        val film = bundledMovies().first { it.title == "The Shining" }
        val today = java.time.LocalDate.now().toEpochDay()
        fun nowOut() = viewModel.uiState.value.rows.filterIsInstance<HomeRow.NowOut>().singleOrNull()?.movies.orEmpty().map { it.id }
        withCleanup({
            // Asked about before it came out; it came out yesterday.
            assertTrue(runBlocking { settings.toggleReminder(film.id, today - 1, today) })
            waitUntilOrDescribe("${film.title} in Now out") { film.id in nowOut() }
            rule.onNodeWithContentDescription(text(R.string.search_title)).performClick()
            waitFor(hasSetTextAction())
            rule.onNode(hasSetTextAction()).performTextInput("shining")
            val card = hasContentDescription("The Shining", substring = true)
            waitFor(card)
            rule.onAllNodes(card)[0].performClick()
            waitFor(hasTestTag(REMIND_TAG))
            waitFor(hasText(text(R.string.action_reminded)))
            rule.onNode(hasTestTag(REMIND_TAG)).performClick()
            // Out already and no reminder: the button has nothing left to offer.
            waitForGone(hasTestTag(REMIND_TAG))
            waitUntilOrDescribe("${film.title} out of Now out") { film.id !in nowOut() }
            backToHome()
        }) {
            runBlocking { if (settings.prefs.first().reminders.any { it.id == film.id }) settings.toggleReminder(film.id, today - 1, today) }
        }
    }

    /** A film's cast: an actor's name is a button, and OK opens the films SCARY holds with them. */
    @Test
    fun anActorsNameOnAFilmPageOpensTheirFilms() {
        waitForHome()
        openViaSearch(bundledMovies().first { it.title == "Hereditary" })
        // The cast comes with the film's details from the web (the bundled library carries none): without it there is
        // nothing to press, and the test has nothing to say.
        val names = hasTestTag(CAST_NAME_TAG)
        assumeTrue("the film's cast did not arrive from the web", runCatching { waitFor(names, ONLINE_TIMEOUT_MS) }.isSuccess)
        rule.onAllNodes(names)[0].performClick()
        waitFor(hasTestTag(PERSON_SCREEN_TAG))
        // The film the viewer came from is one of the actor's films.
        waitFor(hasContentDescription("Hereditary", substring = true) and hasAnyAncestor(hasTestTag(PERSON_SCREEN_TAG)))
        // Back on the film's page, with its cast.
        pressBack()
        waitFor(hasTestTag(CAST_ROW_TAG))
        backToHome()
    }

    /** The whole feed: search finds a well-known film outside the hand-picked library, and it opens. */
    @Test
    fun searchFindsAFilmFromTheFullIndex() {
        waitForHome()
        rule.onNodeWithContentDescription(text(R.string.search_title)).performClick()
        waitFor(hasSetTextAction())
        rule.onNode(hasSetTextAction()).performTextInput("deep red")
        // Its title as shown: Hebrew where Wikidata has one ("אדום עמוק"), English otherwise.
        val deepRed = hasContentDescription("Deep Red", substring = true) or hasContentDescription("אדום עמוק", substring = true) or
            hasContentDescription("Кроваво-красное", substring = true) or hasContentDescription("Profondo rosso", substring = true) or hasContentDescription("أحمر داكن", substring = true)
        waitFor(deepRed, ONLINE_TIMEOUT_MS)
        rule.onAllNodes(deepRed)[0].performClick()
        waitFor(hasText(text(R.string.action_watch_stremio)))
        pressBack()
    }

    @Test
    fun searchFindsAMovieAndMyListToggles() {
        waitForHome()
        rule.onNodeWithContentDescription(text(R.string.search_title)).performClick()
        waitFor(hasSetTextAction())
        rule.onNode(hasSetTextAction()).performTextInput("shining")
        val shining = hasContentDescription("The Shining", substring = true)
        waitFor(shining)
        rule.onAllNodes(shining)[0].performClick()
        waitFor(hasText(text(R.string.row_more_like)))

        // Whatever the starting state, toggling twice must flip it and flip it back.
        val add = hasText(text(R.string.action_add_my_list))
        val inList = hasText(text(R.string.action_in_my_list))
        val startedInList = rule.onAllNodes(inList).fetchSemanticsNodes().isNotEmpty()
        val (before, after) = if (startedInList) inList to add else add to inList
        rule.onNode(before).performClick()
        waitFor(after)
        rule.onNode(after).performClick()
        waitFor(before)

        // "Look for it in your apps": the TV emulator has YouTube, so it gets a button. Its wording follows what the
        // installed YouTube accepts ("Search in" or "Open"), which depends on that app's own version.
        if (isInstalled(KnownPackages.YOUTUBE_TV)) {
            waitFor(hasText(text(R.string.watch_elsewhere)))
            val searchIn = hasText(rule.activity.getString(R.string.search_in_app, "YouTube"), substring = true)
            val open = hasText(rule.activity.getString(R.string.open_app, "YouTube"), substring = true)
            waitFor(searchIn or open)
        }

        backToHome()
    }

    @Test
    fun myListKeepsSeveralMoviesAndEachCanBeRemoved() {
        waitForHome()
        val first = bundledMovies().first { it.title == "The Shining" }
        val second = bundledMovies().first { it.title == "Hereditary" }
        withCleanup(
            test = {
                setInMyList(first, wanted = true)
                setInMyList(second, wanted = true)
                assertMyListRow(present = listOf(first, second))
                setInMyList(first, wanted = false)
                assertMyListRow(present = listOf(second), absent = listOf(first))
            },
            cleanup = {
                setInMyList(first, wanted = false)
                setInMyList(second, wanted = false)
            },
        )
    }

    @Test
    fun holdingOkOnAPosterAddsItToMyListAndHoldingAgainRemovesIt() {
        waitForHomeFocus()
        val catalog = bundledCatalog()
        val collection = stableRow(catalog)
        val movie = catalog.moviesOf(collection).first()
        val rowKey = "collection:${collection.id}"
        val card = hasContentDescription("${movie.title}, ${movie.year}", substring = true) and
            hasAnyAncestor(hasTestTag(rowTag(rowKey)))
        val inList = hasContentDescription("${movie.title}, ${movie.year}", substring = true) and
            hasAnyAncestor(hasTestTag(rowTag("mylist")))
        rule.onNodeWithTag(HOME_ROWS_TAG).performScrollToKey(rowKey)
        rule.onNodeWithTag(rowTag(rowKey)).performScrollToIndex(0)
        waitFor(card)
        rule.onNode(card).performSemanticsAction(SemanticsActions.RequestFocus)
        rule.waitForIdle()
        // Holding OK acts on the focused card; make sure that is this one.
        rule.onNode(card).assertIsFocused()
        withCleanup(test = {
            longPressOk()
            // The movie page must not open on the way.
            assertTrue("Holding OK opened the movie page", rule.onAllNodes(hasText(text(R.string.row_more_like))).fetchSemanticsNodes().isEmpty())
            val rows = rule.onNodeWithTag(HOME_ROWS_TAG)
            waitUntilOrDescribe("the My list row") { runCatching { rows.performScrollToKey("mylist") }.isSuccess }
            waitFor(inList)
            rule.onNodeWithTag(HOME_ROWS_TAG).performScrollToKey(rowKey)
            rule.onNodeWithTag(rowTag(rowKey)).performScrollToIndex(0)
            rule.onNode(card).performSemanticsAction(SemanticsActions.RequestFocus)
            rule.waitForIdle()
            rule.onNode(card).assertIsFocused()
            longPressOk()
            assertTrue("Holding OK opened the movie page", rule.onAllNodes(hasText(text(R.string.row_more_like))).fetchSemanticsNodes().isEmpty())
            waitForGone(inList)
        }, cleanup = { setInMyList(movie, wanted = false) })
    }

    @Test
    fun markingAMovieAsWatchedShowsOnItsCardAndCanBeUndone() {
        waitForHome()
        val movie = bundledMovies().first { it.title == "Hereditary" }
        val mark = hasText(text(R.string.action_mark_watched))
        val watched = hasText(text(R.string.action_watched))
        withCleanup(
            test = {
                openViaSearch(movie)
                waitFor(mark or watched)
                if (rule.onAllNodes(watched).fetchSemanticsNodes().isEmpty()) rule.onNode(mark).performClick()
                waitFor(watched)
                // Its card now says so, for TalkBack too (search results show the same card as the rows).
                backToHome()
                rule.onNodeWithContentDescription(text(R.string.search_title)).performClick()
                waitFor(hasSetTextAction())
                rule.onNode(hasSetTextAction()).performTextReplacement(movie.title)
                waitFor(hasContentDescription("${movie.title}, ${movie.year}, ${text(R.string.watched_badge)}", substring = true))
                backToHome()
                openViaSearch(movie)
                rule.onNode(watched).performClick()
                waitFor(mark)
                backToHome()
            },
            cleanup = {
                backToHome()
                openViaSearch(movie)
                waitFor(mark or watched)
                if (rule.onAllNodes(watched).fetchSemanticsNodes().isNotEmpty()) rule.onNode(watched).performClick()
                backToHome()
            },
        )
    }

    private fun openViaSearch(movie: Movie) {
        rule.onNodeWithContentDescription(text(R.string.search_title)).performClick()
        waitFor(hasSetTextAction())
        rule.onNode(hasSetTextAction()).performTextReplacement(movie.title)
        val result = hasContentDescription("${movie.title}, ${movie.year}", substring = true)
        waitFor(result)
        rule.onAllNodes(result)[0].performClick()
        waitFor(hasText(text(R.string.row_more_like)))
    }

    @Test
    fun withoutATmdbKeyAMoviePageSaysHowToSeeWhereToRentOrBuyIt() {
        // CI has no TMDB key; on a device that has one, the stores themselves show instead.
        waitForHome()
        // The key is read at start-up, before the home screen shows.
        val app = rule.activity.application as ScaryApplication
        assumeTrue(app.container.tmdbRepository.keyMask.value == null)
        openViaSearch(bundledMovies().first { it.title == "Hereditary" })
        val hint = hasText(text(R.string.rent_buy_needs_key))
        waitFor(hint)
        rule.onNode(hasText(text(R.string.rent_buy_title_plain))).assertExists()
        // The page scrolls to it: nothing on the movie page is out of reach.
        rule.onNode(hint).performScrollTo().assertIsDisplayed()
        backToHome()
    }

    @Test
    fun holdingOkInMyListRemovesTheMovieAndTheNextOneTakesTheFocus() {
        waitForHome()
        val first = bundledMovies().first { it.title == "The Shining" }
        val second = bundledMovies().first { it.title == "Hereditary" }
        fun inMyList(movie: Movie) =
            hasContentDescription("${movie.title}, ${movie.year}", substring = true) and hasAnyAncestor(hasTestTag(rowTag("mylist")))
        withCleanup(
            test = {
                // Added second, so it comes first in the row (newest first).
                setInMyList(first, wanted = true)
                setInMyList(second, wanted = true)
                waitForHomeFocus()
                val rows = rule.onNodeWithTag(HOME_ROWS_TAG)
                waitUntilOrDescribe("the My list row") { runCatching { rows.performScrollToKey("mylist") }.isSuccess }
                waitFor(inMyList(second))
                rule.onNode(inMyList(second)).performSemanticsAction(SemanticsActions.RequestFocus)
                rule.waitForIdle()
                rule.onNode(inMyList(second)).assertIsFocused()
                longPressOk()
                waitForGone(inMyList(second))
                // The card that took its place has the focus, so holding OK again removes it too.
                waitFor(inMyList(first) and isFocused())
                longPressOk()
                waitForGone(inMyList(first))
            },
            cleanup = {
                setInMyList(first, wanted = false)
                setInMyList(second, wanted = false)
            },
        )
    }

    /** A real long press of the remote's OK button, as the system delivers it (repeats and the long-press flag). */
    /**
     * Holds the remote's OK the way a TV remote does: a press, the held repeat flagged as a long press, then the
     * release. The events are injected from here, synchronously, so each one has reached the window before the next.
     * A key only reaches the app while its window has the input focus and no on-screen keyboard is up (the keyboard
     * takes the remote's keys first), so both are waited for, and logged for CI.
     */
    private fun longPressOk() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        waitUntilOrDescribe("the window to take the remote's keys") { readyForKeys() }
        Log.d("ScaryKeys", "test: holding OK")
        val down = SystemClock.uptimeMillis()
        instrumentation.sendKeySync(KeyEvent(down, down, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER, 0))
        SystemClock.sleep(ViewConfiguration.getLongPressTimeout().toLong())
        val held = SystemClock.uptimeMillis()
        instrumentation.sendKeySync(
            KeyEvent.changeFlags(
                KeyEvent(down, held, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER, 1),
                KeyEvent.FLAG_LONG_PRESS,
            ),
        )
        instrumentation.sendKeySync(
            KeyEvent(down, SystemClock.uptimeMillis(), KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_CENTER, 0),
        )
        Log.d("ScaryKeys", "test: OK released")
        rule.waitForIdle()
    }

    /** True when the activity's window has the input focus and no on-screen keyboard is showing; hides one that is. */
    private fun readyForKeys(): Boolean {
        var ready = false
        rule.runOnUiThread {
            val view = rule.activity.window.decorView
            val keyboard = ViewCompat.getRootWindowInsets(view)?.isVisible(WindowInsetsCompat.Type.ime()) == true
            if (keyboard) WindowCompat.getInsetsController(rule.activity.window, view).hide(WindowInsetsCompat.Type.ime())
            ready = view.hasWindowFocus() && !keyboard
            if (!ready) Log.d("ScaryKeys", "test: not ready, windowFocus=${view.hasWindowFocus()} keyboard=$keyboard")
        }
        return ready
    }

    /** Opens [movie] through search, puts it in or out of My list with the movie page's button, and goes home. */
    private fun setInMyList(movie: Movie, wanted: Boolean) {
        backToHome()
        rule.onNodeWithContentDescription(text(R.string.search_title)).performClick()
        waitFor(hasSetTextAction())
        // Search keeps its last query when it comes back; replace it rather than adding to it.
        rule.onNode(hasSetTextAction()).performTextReplacement(movie.title)
        val result = hasContentDescription("${movie.title}, ${movie.year}", substring = true)
        waitFor(result)
        rule.onAllNodes(result)[0].performClick()
        val add = hasText(text(R.string.action_add_my_list))
        val inList = hasText(text(R.string.action_in_my_list))
        waitFor(add or inList)
        val isIn = rule.onAllNodes(inList).fetchSemanticsNodes().isNotEmpty()
        if (isIn != wanted) {
            rule.onNode(if (isIn) inList else add).performClick()
            waitFor(if (wanted) inList else add)
        }
        backToHome()
    }

    private fun assertMyListRow(present: List<Movie>, absent: List<Movie> = emptyList()) {
        val rows = rule.onNodeWithTag(HOME_ROWS_TAG)
        rule.waitUntil(TIMEOUT_MS) { runCatching { rows.performScrollToKey("mylist") }.isSuccess }
        for (movie in present) {
            waitFor(hasContentDescription("${movie.title}, ${movie.year}", substring = true) and hasAnyAncestor(hasTestTag(rowTag("mylist"))))
        }
        for (movie in absent) {
            waitForGone(hasContentDescription("${movie.title}, ${movie.year}", substring = true) and hasAnyAncestor(hasTestTag(rowTag("mylist"))))
        }
    }

    @Test
    fun aSearchWithNoMatchSaysSoAndOffersSurpriseMe() {
        waitForHome()
        rule.onNodeWithContentDescription(text(R.string.search_title)).performClick()
        waitFor(hasSetTextAction())
        rule.onNode(hasSetTextAction()).performTextInput("qqxqzz")
        // Waits for the web search too, which may take a few seconds or be off.
        waitFor(hasText(text(R.string.empty_search_title)), timeoutMillis = 30_000)
        rule.onNode(hasText(text(R.string.surprise_me))).assertExists()
        backToHome()
    }

    @Test
    fun choosingAMoodShowsItsMoviesAndChoosingItAgainClosesThem() {
        waitForHome()
        rule.onNodeWithTag(HOME_ROWS_TAG).performScrollToKey("moods")
        val chip = hasText(text(R.string.mood_relentless))
        waitFor(chip)
        rule.onNode(chip).assertIsNotSelected()
        rule.onNode(chip).performClick()
        waitFor(chip and isSelected())
        rule.onNode(chip).assertIsSelected()
        // The row starts with the scariest movie of the bundled library, the same pick the app makes.
        val first = Mood.RELENTLESS.pick(bundledMovies()).first()
        waitFor(hasContentDescription("${first.title}, ${first.year}", substring = true))
        rule.onNode(chip).performClick()
        // The home state is built off the main thread, so idle Compose does not mean the new state has arrived yet.
        waitFor(chip and isNotSelected())
        rule.onNode(chip).assertIsNotSelected()
    }

    @Test
    fun choosingADecadeShowsItsMoviesOldestFirst() {
        waitForHome()
        rule.onNodeWithTag(HOME_ROWS_TAG).performScrollToKey("moods")
        val chip = hasText(rule.activity.getString(R.string.decade_label, 1980))
        waitFor(chip)
        rule.onNode(chip).performClick()
        waitFor(chip and isSelected())
        val first = Decades.pick(bundledMovies(), null, 1980).first()
        assertTrue(first.year in 1980..1989)
        waitFor(hasContentDescription("${first.title}, ${first.year}", substring = true))
        rule.onNode(chip).performClick()
        waitFor(chip and isNotSelected())
    }

    @Test
    fun anEndlessOnlineRowLoadsMoreMoviesAtItsEnd() {
        waitForHome()
        val rowKey = "collection:" + Cinemeta.onlineCollectionId("horror_popular")
        val rows = rule.onNodeWithTag(HOME_ROWS_TAG)
        // Online rows need Cinemeta; without the network the row is not there, and there is nothing to test.
        val present = runCatching { rule.waitUntil(TIMEOUT_MS) { runCatching { rows.performScrollToKey(rowKey) }.isSuccess } }.isSuccess
        assumeTrue("Online rows are not available on this emulator", present)
        val row = rule.onNodeWithTag(rowTag(rowKey))
        // The daily refresh loads the first page (up to 50 movies); nearing the end asks for the next pages, with no
        // cap. Scrolling to the furthest card that exists brings the end into view, whatever the exact count.
        row.performScrollToIndex(0)
        rule.waitUntil(ONLINE_TIMEOUT_MS) {
            runCatching { row.performScrollToIndex(BEYOND_FIRST_PAGES) }.isSuccess.also { reached ->
                if (!reached) NEAR_END_OF_FIRST_PAGES.firstOrNull { runCatching { row.performScrollToIndex(it) }.isSuccess }
            }
        }
    }

    @Test
    fun familyNightShowsABadgeAndTurnsOffAgain() {
        waitForHome()
        val badge = hasText(text(R.string.family_night_badge))
        toggleFamilyNightInSettings()
        try {
            waitFor(badge)
        } finally {
            toggleFamilyNightInSettings()
        }
        waitForGone(badge)
    }

    @Test
    fun trailersThatPlayByThemselvesCanBeTurnedOffAndOnAgain() {
        waitForHome()
        rule.onNodeWithContentDescription(text(R.string.settings_title)).performClick()
        val row = hasTestTag(TRAILER_AUTOPLAY_ROW_TAG)
        val label = text(R.string.settings_trailer_autoplay)
        val on = hasContentDescription("$label: ${text(R.string.value_on)}")
        val off = hasContentDescription("$label: ${text(R.string.value_off)}")
        val sound = hasText(text(R.string.settings_trailer_sound))
        waitFor(row)
        withCleanup({
            // On by default, with its sound setting below it; off hides the sound setting.
            waitFor(on)
            rule.onNode(sound).assertExists()
            rule.onNode(row).performScrollTo().performClick()
            waitFor(off)
            waitForGone(sound)
        }) {
            if (rule.onAllNodes(off).fetchSemanticsNodes().isNotEmpty()) rule.onNode(row).performScrollTo().performClick()
            waitFor(on)
            backToHome()
        }
    }

    @Test
    fun theFirstTimeTrailerQuestionExplainsYouTubeAndBackPutsItOff() {
        waitForHomeFocus()
        // The question SCARY asks before the first trailer would play by itself (trailers never start in these tests:
        // system animations are off while they run), opened through the debug intent CI uses for its screenshot.
        rule.activity.startActivity(Intent(rule.activity, MainActivity::class.java).putExtra(DEBUG_OPEN, "trailer-intro"))
        val dialog = hasTestTag(TRAILER_INTRO_TAG)
        waitFor(dialog)
        rule.onNode(hasText(text(R.string.trailer_intro_privacy))).assertExists()
        rule.onNode(hasText(text(R.string.trailer_intro_off))).assertExists()
        // Back goes to the window with the focus: wait until the dialog has it ("Play trailers" is focused first).
        waitFor(hasText(text(R.string.trailer_intro_play)) and isFocused())
        rule.waitForIdle()
        pressBack()
        waitForGone(dialog)
        waitUntilOrDescribe("the home screen") { onHome() }
    }

    @Test
    fun choosingHowMuchTimeYouHaveShowsANumberedLineupAndChoosingItAgainClosesIt() {
        waitForHome()
        rule.onNodeWithTag(HOME_ROWS_TAG).performScrollToKey("time")
        val chip = hasText(text(R.string.time_double))
        val first = hasText(rule.activity.getString(R.string.time_order, 1))
        val second = hasText(rule.activity.getString(R.string.time_order, 2))
        waitFor(chip)
        rule.onNode(chip).assertIsNotSelected()
        rule.onNode(chip).performClick()
        waitFor(chip and isSelected())
        // A double feature is two films, numbered in the order to watch them.
        waitFor(first)
        rule.onNode(second).assertExists()
        rule.onNode(chip).performClick()
        waitFor(chip and isNotSelected())
        waitForGone(first)
    }

    @Test
    fun lovingAFilmBringsBecauseYouLovedAndCanBeTakenBack() {
        waitForHome()
        val movie = bundledMovies().first { it.title == "Hereditary" }
        val love = hasText(text(R.string.action_love))
        val loved = hasText(text(R.string.action_loved))
        val rows = rule.onNodeWithTag(HOME_ROWS_TAG)
        withCleanup(
            test = {
                openViaSearch(movie)
                waitFor(love or loved)
                if (rule.onAllNodes(loved).fetchSemanticsNodes().isEmpty()) rule.onNode(love).performScrollTo().performClick()
                waitFor(loved)
                backToHome()
                // Films like it get a row of their own on the home screen.
                rule.waitUntil(TIMEOUT_MS) { runCatching { rows.performScrollToKey("because") }.isSuccess }
                waitFor(hasText(rule.activity.getString(R.string.row_because_loved, movie.title)))
                openViaSearch(movie)
                rule.onNode(loved).performScrollTo().performClick()
                waitFor(love)
                backToHome()
                // The home state is built off the main thread: the row goes once the new state arrives.
                rule.waitUntil(TIMEOUT_MS) { runCatching { rows.performScrollToKey("because") }.isFailure }
            },
            cleanup = {
                backToHome()
                openViaSearch(movie)
                waitFor(love or loved)
                if (rule.onAllNodes(loved).fetchSemanticsNodes().isNotEmpty()) rule.onNode(loved).performScrollTo().performClick()
                waitFor(love)
                backToHome()
            },
        )
    }

    @Test
    fun yourYearInFearOpensFromSettings() {
        waitForHome()
        rule.onNodeWithContentDescription(text(R.string.settings_title)).performClick()
        val row = hasTestTag(STATS_ROW_TAG)
        waitFor(row)
        rule.onNode(row).performScrollTo().performClick()
        waitFor(hasTestTag(STATS_SCREEN_TAG))
        // A fresh emulator has watched nothing yet; either way, the screen says what it shows.
        waitFor(hasText(text(R.string.stats_empty_title)) or hasText(text(R.string.stats_all_time)))
        backToHome()
    }

    @Test
    fun thePhoneRemoteShowsItsCodeAndTakesOnlySignedCommands() {
        waitForHome()
        val remote = (rule.activity.application as ScaryApplication).container.phoneRemote
        withCleanup({
            rule.onNodeWithContentDescription(text(R.string.settings_title)).performClick()
            val row = hasTestTag(PHONE_REMOTE_ROW_TAG)
            waitFor(row)
            rule.onNode(row).performScrollTo().performClick()
            waitFor(hasTestTag(PHONE_REMOTE_QR_TAG))
            waitFor(hasText(text(R.string.remote_waiting)))
            val on = remote.state.value as PhoneRemoteServer.State.On
            val port = Uri.parse(on.url).port
            val secret = Base64.decode(
                Uri.parse(on.url).fragment.orEmpty().split('&').first { it.startsWith("s=") }.removePrefix("s="),
                Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP,
            )
            // The page is served to anyone, and holds no secret.
            val page = remoteHttp(port, "GET / HTTP/1.1\r\nHost: tv\r\n\r\n")
            assertTrue(page, page.startsWith("HTTP/1.1 200") && "remote.js" in page && "Content-Security-Policy" in page)
            var counter = System.currentTimeMillis() * 1000
            // A command signed with another key is refused, and does nothing.
            val forged = remotePost(port, RemoteProtocol.seal(ByteArray(RemoteProtocol.SECRET_BYTES), ++counter, Command.HOME))
            assertTrue(forged, forged.startsWith("HTTP/1.1 401"))
            rule.onNode(hasTestTag(PHONE_REMOTE_TAG)).assertExists()
            // A signed arrow moves the focus as the TV remote's would: from Disconnect to New code.
            waitFor(hasText(text(R.string.remote_disconnect)) and isFocused())
            val right = if (rule.activity.resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL) Command.LEFT else Command.RIGHT
            val arrow = remotePost(port, RemoteProtocol.seal(secret, ++counter, right))
            assertTrue(arrow, arrow.startsWith("HTTP/1.1 200"))
            waitFor(hasText(text(R.string.remote_new_code)) and isFocused())
            waitFor(hasText(text(R.string.remote_connected)))
            // A signed search runs on the TV and answers the phone with the matches.
            val movie = bundledMovies().first()
            val body = RemoteProtocol.seal(secret, ++counter, Command.SEARCH, movie.title)
            val search = remotePost(port, body)
            assertTrue(search, search.startsWith("HTTP/1.1 200") && "\"${movie.id}\"" in search)
            waitFor(hasSetTextAction() and hasText(movie.title))
            // The same command sent again is a replay.
            val replay = remotePost(port, body)
            assertTrue(replay, replay.startsWith("HTTP/1.1 409"))
            val home = remotePost(port, RemoteProtocol.seal(secret, ++counter, Command.HOME))
            assertTrue(home, home.startsWith("HTTP/1.1 200"))
            waitUntilOrDescribe("the home screen") { onHome() }
            // Disconnect forgets the key: the old code stops working.
            remote.stop()
            val after = runCatching { remotePost(port, RemoteProtocol.seal(secret, ++counter, Command.HOME)) }.getOrDefault("closed")
            assertTrue(after, !after.startsWith("HTTP/1.1 200"))
        }) {
            remote.stop()
            backToHome()
        }
    }

    @Test
    fun withItsAccessibilityServiceOnThePhoneRemoteReachesOtherApps() {
        waitForHome()
        val remote = (rule.activity.application as ScaryApplication).container.phoneRemote
        val service = "${rule.activity.packageName}/${HomeButtonService::class.java.name}"
        val servicesBefore = shell("settings get secure enabled_accessibility_services")
        val enabledBefore = shell("settings get secure accessibility_enabled")
        fun scaryInFront(): Boolean {
            var resumed = false
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                resumed = rule.activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
            }
            return resumed
        }
        withCleanup({
            // Off: another app in front gets nothing, and the phone is told how to allow it.
            val url = remote.start(Language.EN) ?: error("no network for the phone remote")
            val port = Uri.parse(url).port
            val secret = Base64.decode(
                Uri.parse(url).fragment.orEmpty().split('&').first { it.startsWith("s=") }.removePrefix("s="),
                Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP,
            )
            var counter = System.currentTimeMillis() * 1000
            rule.activity.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            waitUntilOrDescribe("the TV's settings in front of SCARY") { !scaryInFront() }
            if (HomeButtonService.instance == null) {
                // SCARY is the home app here, and now and then the emulator brings it back to the front by itself
                // right after the settings open: then Back reaches SCARY, which answers "ok". That says nothing about
                // other apps, so the settings are opened again and the question is asked again.
                var off = ""
                for (attempt in 1..3) {
                    if (scaryInFront()) {
                        rule.activity.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        waitUntilOrDescribe("the TV's settings in front of SCARY") { !scaryInFront() }
                    }
                    off = remotePost(port, RemoteProtocol.seal(secret, ++counter, Command.BACK))
                    if ("\"a11y_off\"" in off) break
                }
                assertTrue(off, "\"a11y_off\"" in off)
            }
            // On: Back from the phone closes the other app, and the arrows go as far as this Android allows.
            shell("settings put secure enabled_accessibility_services $service")
            shell("settings put secure accessibility_enabled 1")
            waitUntilOrDescribe("SCARY's accessibility service to start") { HomeButtonService.instance != null }
            // As with Back above: the emulator may bring SCARY to the front between the look and the question, and
            // SCARY then answers the arrow itself ("ok"), which says nothing about other apps. So the settings are
            // opened again and the question is asked again.
            val expected = if (HomeButtonService.arrowsEverywhere) "\"ok\"" else "\"old_android\""
            var down = ""
            for (attempt in 1..3) {
                if (scaryInFront()) {
                    rule.activity.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    waitUntilOrDescribe("the TV's settings in front of SCARY") { !scaryInFront() }
                }
                down = remotePost(port, RemoteProtocol.seal(secret, ++counter, Command.DOWN))
                if (expected in down) break
            }
            assertTrue(down, down.startsWith("HTTP/1.1 200") && expected in down)
            // The service may open SCARY by itself shortly after the TV starts: then go back to the settings.
            if (scaryInFront()) {
                rule.activity.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                waitUntilOrDescribe("the TV's settings in front of SCARY") { !scaryInFront() }
            }
            repeat(MAX_BACKS) {
                if (scaryInFront()) return@repeat
                val back = remotePost(port, RemoteProtocol.seal(secret, ++counter, Command.BACK))
                assertTrue(back, back.startsWith("HTTP/1.1 200") && "\"ok\"" in back)
                SystemClock.sleep(BACK_SETTLE_MS)
            }
            waitUntilOrDescribe("SCARY back in front after Back on the phone") { scaryInFront() }
        }) {
            remote.stop()
            shell(if (servicesBefore == "null" || servicesBefore.isEmpty()) "settings delete secure enabled_accessibility_services" else "settings put secure enabled_accessibility_services $servicesBefore")
            shell(if (enabledBefore == "null" || enabledBefore.isEmpty()) "settings delete secure accessibility_enabled" else "settings put secure accessibility_enabled $enabledBefore")
            if (!scaryInFront()) rule.activity.startActivity(Intent(rule.activity, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            waitUntilOrDescribe("SCARY in front") { scaryInFront() }
            backToHome()
        }
    }

    /**
     * A shell command, as the test. Its UiAutomation must not suppress accessibility services, as it does by
     * default: SCARY's own service would never start.
     */
    private fun shell(command: String): String {
        val automation = InstrumentationRegistry.getInstrumentation()
            .getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
        return ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(command))
            .use { it.readBytes().toString(Charsets.UTF_8).trim() }
    }

    /** A plain HTTP exchange with the phone remote over the loopback address; the answer, head and body. */
    private fun remoteHttp(port: Int, request: String): String =
        Socket("127.0.0.1", port).use { socket ->
            socket.soTimeout = TIMEOUT_MS.toInt()
            socket.getOutputStream().apply {
                write(request.toByteArray(Charsets.UTF_8))
                flush()
            }
            socket.getInputStream().readBytes().toString(Charsets.UTF_8)
        }

    private fun remotePost(port: Int, json: String, path: String = "/api"): String {
        val body = json.toByteArray(Charsets.UTF_8)
        return remoteHttp(port, "POST $path HTTP/1.1\r\nHost: tv\r\nContent-Type: application/json\r\nContent-Length: ${body.size}\r\n\r\n$json")
    }

    /**
     * The viewer's backup through the phone remote: it leaves the TV encrypted and signed for the paired phone, and a
     * file sent back adds to My list without removing anything. A file is taken only at its own address.
     */
    @Test
    fun theBackupGoesToThePairedPhoneAndAFileIsMergedBack() {
        waitForHome()
        val container = (rule.activity.application as ScaryApplication).container
        val remote = container.phoneRemote
        val settings = container.settingsRepository
        val listBefore = runBlocking { settings.prefs.first().myListIds }
        val (kept, added) = bundledMovies().filter { it.id !in listBefore }.let { it[0] to it[1] }
        withCleanup({
            runBlocking { settings.toggleMyList(kept.id) }
            val url = remote.start(Language.EN) ?: error("no network for the phone remote")
            val port = Uri.parse(url).port
            val secret = Base64.decode(
                Uri.parse(url).fragment.orEmpty().split('&').first { it.startsWith("s=") }.removePrefix("s="),
                Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP,
            )
            var counter = System.currentTimeMillis() * 1000

            val asked = ++counter
            val answer = remotePost(port, RemoteProtocol.seal(secret, asked, Command.BACKUP))
            assertTrue(answer, answer.startsWith("HTTP/1.1 200") && "\"backup\"" in answer)
            // Nothing readable on the wire: not the format's name, not a film's id.
            assertFalse(answer, "scary-backup" in answer || kept.id in answer)
            val body = org.json.JSONObject(answer.substringAfter("\r\n\r\n"))
            val sealed = RemoteProtocol.SealedAnswer(body.getString("b"), body.getString("t"))
            val text = RemoteProtocol.openAnswer(secret, asked, sealed) ?: error("the backup's answer did not open")
            // Signed for that command only: another counter does not open it.
            assertNull(RemoteProtocol.openAnswer(secret, asked + 1, sealed))
            val backup = Backup.decode(text) ?: error("not a backup: ${text.take(80)}")
            assertTrue(text, kept.id in backup.myList)
            for (word in listOf("pin", "token", "secret", "password")) assertFalse(word, word in text.lowercase())

            // A file with one more film: only at the large address, and only when it is a backup.
            val file = Backup.encode(backup.copy(myList = backup.myList + added.id), "test", 20_000)
            val small = remotePost(port, RemoteProtocol.seal(secret, ++counter, Command.RESTORE, file))
            assertTrue(small, small.startsWith("HTTP/1.1 400") || small.startsWith("HTTP/1.1 413"))
            val other = remotePost(port, RemoteProtocol.seal(secret, ++counter, Command.RESTORE, """{"format":"other"}"""), path = "/api/big")
            assertTrue(other, "\"restore_invalid\"" in other)
            val restored = remotePost(port, RemoteProtocol.seal(secret, ++counter, Command.RESTORE, file), path = "/api/big")
            assertTrue(restored, restored.startsWith("HTTP/1.1 200") && "\"restore_ok\"" in restored)
            waitUntilOrDescribe("${added.title} in My list after the restore") {
                runBlocking { settings.prefs.first().myListIds }.let { added.id in it && kept.id in it && it.containsAll(listBefore) }
            }
        }) {
            remote.stop()
            runBlocking {
                val now = settings.prefs.first().myListIds
                for (film in listOf(kept, added)) if (film.id in now) settings.toggleMyList(film.id)
            }
        }
    }

    @Test
    fun watchedFilmsGoToTheEndOfTheirRowAndCanBeHiddenInAWatchedRow() {
        waitForHome()
        val container = (rule.activity.application as ScaryApplication).container
        val viewModel = ViewModelProvider(rule.activity, HomeViewModel.factory(container))[HomeViewModel::class.java]
        val catalog = bundledCatalog()
        val collection = catalog.collections.first { catalog.moviesOf(it).size >= 4 }
        val film = catalog.moviesOf(collection).first()
        fun row() = viewModel.uiState.value.rows.filterIsInstance<HomeRow.Collection>().firstOrNull { it.collection.id == collection.id }
        withCleanup({
            runBlocking { container.settingsRepository.setWatched(film.id, watched = true) }
            waitUntilOrDescribe("${film.title} at the end of its row once watched") { row()?.movies?.lastOrNull()?.id == film.id }
            runBlocking { container.settingsRepository.setHideWatched(true) }
            waitUntilOrDescribe("${film.title} out of its row and in the Watched row") {
                val watchedRow = viewModel.uiState.value.rows.filterIsInstance<HomeRow.Watched>().singleOrNull()
                row()?.movies?.none { it.id == film.id } == true && watchedRow?.movies?.any { it.id == film.id } == true
            }
            rule.onNodeWithTag(HOME_ROWS_TAG).performScrollToKey("watched")
            waitFor(hasText(text(R.string.row_watched)))
        }) {
            runBlocking {
                container.settingsRepository.setHideWatched(false)
                container.settingsRepository.setWatched(film.id, watched = false)
            }
            backToHome()
        }
    }

    /** The first card the taste picker shows that the viewer has not loved before the test. */
    private fun firstTasteCardOnScreenOrNull(lovedBefore: Set<String>, viewModel: HomeViewModel): io.github.diamondrubinny.scary.core.model.Movie? {
        val cards = SemanticsMatcher("a taste card") { node ->
            node.config.getOrNull(SemanticsProperties.TestTag)?.startsWith(tasteCardTag("")) == true
        }
        val ids = rule.onAllNodes(cards).fetchSemanticsNodes().mapNotNull { it.config.getOrNull(SemanticsProperties.TestTag)?.removePrefix(tasteCardTag("")) }
        val catalog = viewModel.tasteCandidates(MediaKind.MOVIE) + viewModel.tasteCandidates(MediaKind.SERIES)
        return ids.firstOrNull { it !in lovedBefore }?.let { id -> catalog.firstOrNull { it.id == id } }
    }

    private fun firstTasteCardOnScreen(lovedBefore: Set<String>, viewModel: HomeViewModel) =
        firstTasteCardOnScreenOrNull(lovedBefore, viewModel) ?: throw AssertionError("no taste card on screen")

    /**
     * Waits until the picker's list for [kind] has stopped changing: films keep arriving from the library and the web
     * for a while after start-up and the grid reorders with them, so a card read off the screen a moment earlier can
     * be gone before it is pressed.
     */
    private fun waitForSettledTaste(kind: MediaKind, viewModel: HomeViewModel) {
        var last = emptyList<String>()
        var since = System.currentTimeMillis()
        waitUntilOrDescribe("the ${kind.name} picker to settle", TIMEOUT_MS * 2) {
            val now = viewModel.tasteCandidates(kind).map { it.id }
            if (now != last) {
                last = now
                since = System.currentTimeMillis()
            }
            now.isNotEmpty() && System.currentTimeMillis() - since >= TASTE_SETTLE_MS
        }
        rule.waitForIdle()
    }

    @Test
    fun theTastePickerMarksFilmsAndSeriesAsLovedAndDoneGoesBackToSettings() {
        waitForHome()
        val container = (rule.activity.application as ScaryApplication).container
        val viewModel = ViewModelProvider(rule.activity, HomeViewModel.factory(container))[HomeViewModel::class.java]
        val lovedBefore = runBlocking { container.settingsRepository.prefs.first().lovedIds.toSet() }
        val picked = mutableListOf<String>()
        withCleanup({
            rule.onNodeWithContentDescription(text(R.string.settings_title)).performClick()
            val row = hasTestTag(TASTE_ROW_TAG)
            waitFor(row)
            rule.onNode(row).performScrollTo().performClick()
            waitFor(hasTestTag(TASTE_PICKER_TAG))
            waitFor(hasText(text(R.string.taste_done)) and isFocused())
            // A film on screen (the list can reorder while ratings arrive from the web, so it is read off the
            // screen, not recomputed); OK puts a heart on it, read out with its title.
            waitForSettledTaste(MediaKind.MOVIE, viewModel)
            val film = firstTasteCardOnScreen(lovedBefore, viewModel)
            picked += film.id
            rule.onNodeWithTag(tasteCardTag(film.id)).performScrollTo().performClick()
            waitUntilOrDescribe("${film.title} loved") { film.id in viewModel.uiState.value.lovedIds }
            waitFor(hasTestTag(tasteCardTag(film.id)) and hasContentDescription(text(R.string.loved_badge), substring = true))
            // Pressed again, the heart goes.
            rule.onNodeWithTag(tasteCardTag(film.id)).performClick()
            waitUntilOrDescribe("${film.title} no longer loved") { film.id !in viewModel.uiState.value.lovedIds }
            // The series tab shows series.
            rule.onNodeWithTag(tasteTabTag(MediaKind.SERIES)).performClick()
            waitUntilOrDescribe("a series on the picker") { firstTasteCardOnScreenOrNull(lovedBefore, viewModel)?.kind == MediaKind.SERIES }
            waitForSettledTaste(MediaKind.SERIES, viewModel)
            val series = firstTasteCardOnScreen(lovedBefore, viewModel)
            assertTrue(series.kind == MediaKind.SERIES)
            picked += series.id
            // Brought into view first, as with the film: a card can be composed just outside the grid's edge (kept ready
            // for scrolling), where a click at its centre lands on nothing.
            rule.onNodeWithTag(tasteCardTag(series.id)).performScrollTo().performClick()
            waitUntilOrDescribe("${series.title} loved") { series.id in viewModel.uiState.value.lovedIds }
            // Done: back on settings, on the row that opened the picker.
            rule.onNode(hasText(text(R.string.taste_done))).performClick()
            waitFor(row and isFocused())
        }) {
            runBlocking { picked.filter { it !in lovedBefore }.forEach { container.settingsRepository.setRating(it, null) } }
            backToHome()
        }
    }

    @Test
    fun aNewViewerIsInvitedToPickWhatTheyLoveAndNotNowPutsItAway() {
        waitForHome()
        val container = (rule.activity.application as ScaryApplication).container
        val viewModel = ViewModelProvider(rule.activity, HomeViewModel.factory(container))[HomeViewModel::class.java]
        fun invited() = viewModel.uiState.value.rows.any { it is HomeRow.TasteInvite }
        runBlocking { container.settingsRepository.setTasteInviteDismissed(false) }
        // Only while SCARY knows little about the viewer (other tests may have left some history on the emulator).
        val prefs = runBlocking { container.settingsRepository.prefs.first() }
        assumeTrue((prefs.watchedIds + prefs.lovedIds).toSet().size < Personalize.MIN_HISTORY && !prefs.familyNight)
        withCleanup({
            waitUntilOrDescribe("the taste invitation on the home screen") { invited() }
            rule.onNodeWithTag(HOME_ROWS_TAG).performScrollToKey(HomeRow.TasteInvite.key)
            waitFor(hasTestTag(TASTE_INVITE_TAG))
            rule.onNode(hasText(text(R.string.taste_invite_pick)) and hasAnyAncestor(hasTestTag(TASTE_INVITE_TAG))).performClick()
            waitFor(hasTestTag(TASTE_PICKER_TAG))
            pressBack()
            waitForHome()
            rule.onNodeWithTag(HOME_ROWS_TAG).performScrollToKey(HomeRow.TasteInvite.key)
            rule.onNode(hasText(text(R.string.taste_invite_later)) and hasAnyAncestor(hasTestTag(TASTE_INVITE_TAG))).performClick()
            waitUntilOrDescribe("the invitation gone") { !invited() }
            waitForGone(hasTestTag(TASTE_INVITE_TAG))
            // The focus does not get lost with the row.
            waitUntilOrDescribe("something focused") { rule.onAllNodes(isFocused()).fetchSemanticsNodes().isNotEmpty() }
        }) {
            runBlocking { container.settingsRepository.setTasteInviteDismissed(false) }
            backToHome()
        }
    }

    @Test
    fun recentlyOpenedIsClearedFromSettingsAfterAskingOnceMore() {
        waitForHome()
        val container = (rule.activity.application as ScaryApplication).container
        val viewModel = ViewModelProvider(rule.activity, HomeViewModel.factory(container))[HomeViewModel::class.java]
        val film = bundledMovies().first { it.title == "Hereditary" }
        runBlocking { container.settingsRepository.pushRecent(film.id) }
        waitUntilOrDescribe("a film in recently opened") { viewModel.uiState.value.recentCount > 0 }
        rule.onNodeWithContentDescription(text(R.string.settings_title)).performClick()
        val row = hasTestTag(CLEAR_RECENT_ROW_TAG)
        waitFor(row)
        rule.onNode(row).performScrollTo().performClick()
        // The first press only asks.
        waitFor(row and hasContentDescription(text(R.string.settings_clear_recent_confirm), substring = true))
        assertTrue(viewModel.uiState.value.recentCount > 0)
        rule.onNode(row).performClick()
        waitUntilOrDescribe("recently opened empty") { viewModel.uiState.value.recentCount == 0 }
        waitFor(row and hasContentDescription(text(R.string.settings_clear_recent_empty), substring = true))
        backToHome()
        assertTrue(viewModel.uiState.value.rows.none { it is HomeRow.Recent })
    }

    @Test
    fun aNewVersionIsMandatoryAndCanBePutOffOnlyWhenItFails() {
        waitForHomeFocus()
        val container = (rule.activity.application as ScaryApplication).container
        val updates = container.updateManager
        val manifest = UpdateManifest(
            versionCode = 999_999, versionName = "9.9.9", sha256 = "a".repeat(64), size = 3_000_000, minSdk = 26,
            notes = mapOf("en" to "Test notes", "he" to "Test notes", "ar" to "Test notes", "pt" to "Test notes", "ru" to "Test notes"),
        )
        val dialog = hasTestTag(UPDATE_DIALOG_TAG)
        withCleanup({
            rule.runOnUiThread { updates.offerForTest(manifest) }
            waitFor(dialog)
            waitFor(hasText(text(R.string.update_title)) and hasAnyAncestor(dialog))
            waitFor(hasText("Test notes") and hasAnyAncestor(dialog))
            waitFor(hasText(text(R.string.update_required)) and hasAnyAncestor(dialog))
            // "Update now" has the focus and is the only choice: no way to put a working update off.
            waitFor(hasText(text(R.string.update_now)) and isFocused() and hasAnyAncestor(dialog))
            // In the popup: the home screen behind it has a "Not now" of its own (the taste picker's invitation).
            assertTrue(rule.onAllNodes(hasText(text(R.string.update_not_now)) and hasAnyAncestor(dialog)).fetchSemanticsNodes().isEmpty())
            // Back does not close it.
            rule.waitForIdle()
            pressBack()
            rule.waitForIdle()
            rule.onNode(dialog).assertExists()
            // When the update fails, "Try again" (focused) and "Not now" appear; "Not now" puts it off.
            rule.runOnUiThread { updates.failForTest(manifest) }
            waitFor(hasText(text(R.string.update_try_again)) and isFocused() and hasAnyAncestor(dialog))
            rule.onNode(hasText(text(R.string.update_not_now)) and hasAnyAncestor(dialog)).performClick()
            waitForGone(dialog)
            waitUntilOrDescribe("the next look due at once") { runBlocking { container.settingsRepository.prefs.first().updateLastCheck } == 0L }
        }) {
            rule.runOnUiThread { updates.dismiss() }
            backToHome()
        }
    }

    @Test
    fun askScaryOpensFromTheTopBarAndAsksBeforeSendingAnything() {
        waitForHomeFocus()
        val container = (rule.activity.application as ScaryApplication).container
        runBlocking { container.settingsRepository.setAiConsent(AiProvider.POLLINATIONS_FREE, false) }
        val consent = hasTestTag(AI_CONSENT_TAG)
        withCleanup({
            rule.onNodeWithTag(ASK_BUTTON_TAG).performClick()
            waitFor(hasTestTag(ASK_SCREEN_TAG))
            // A one-press request: nothing goes out before the viewer agrees to the free service.
            rule.onNode(hasText(text(R.string.ai_chip_short)) and hasAnyAncestor(hasTestTag(ASK_SCREEN_TAG))).performClick()
            waitFor(consent)
            waitFor(hasText(text(R.string.ai_consent_agree)) and isFocused() and hasAnyAncestor(consent))
            rule.onNode(hasText(text(R.string.ai_consent_later)) and hasAnyAncestor(consent)).performClick()
            waitForGone(consent)
            // Nothing asked, nothing thinking.
            waitForGone(hasTestTag(AI_STATUS_TAG))
            // The chat tab.
            rule.onNodeWithTag(askTabTag(chat = true)).performClick()
            waitFor(hasTestTag(ASK_CHAT_TAG))
        }) {
            backToHome()
        }
    }

    /**
     * The owner's report: Ask SCARY's "Chat" could not be reached with the remote. Keys injected here come from a
     * keyboard source, as with many TV remotes, for which Compose's text field kept every arrow for its cursor.
     */
    @Test
    fun theRemoteLeavesAskScarysLineAndReachesTheChatTab() {
        waitForHomeFocus()
        val screen = hasTestTag(ASK_SCREEN_TAG)
        val line = hasContentDescription(text(R.string.ai_title)) and hasAnyAncestor(screen)
        val chatTab = hasTestTag(askTabTag(chat = true))
        withCleanup({
            rule.onNodeWithTag(ASK_BUTTON_TAG).performClick()
            waitFor(line and isFocused())
            // Typed text moves the cursor first; up still leaves a one-line field.
            rule.onNode(line).performTextInput("like Hereditary")
            pressKey(KeyEvent.KEYCODE_DPAD_UP)
            waitUntilOrDescribe("the focus out of the line, on the screen's top row") {
                rule.onAllNodes(line and isFocused()).fetchSemanticsNodes().isEmpty() &&
                    rule.onAllNodes(isFocused() and hasAnyAncestor(screen)).fetchSemanticsNodes().isNotEmpty()
            }
            // Along the top row to "Chat", from whichever side the focus landed on: by where the two are on the screen,
            // so the test holds in Hebrew and Arabic (where "Chat" is to the left of "Suggestions") as in English.
            repeat(4) {
                if (rule.onAllNodes(chatTab and isFocused()).fetchSemanticsNodes().isNotEmpty()) return@repeat
                val chatX = rule.onNode(chatTab).fetchSemanticsNode().boundsInRoot.center.x
                val focusedX = rule.onAllNodes(isFocused() and hasAnyAncestor(screen)).fetchSemanticsNodes().firstOrNull()?.boundsInRoot?.center?.x
                pressKey(if (focusedX != null && focusedX < chatX) KeyEvent.KEYCODE_DPAD_RIGHT else KeyEvent.KEYCODE_DPAD_LEFT)
            }
            waitFor(chatTab and isFocused())
            pressKey(KeyEvent.KEYCODE_DPAD_CENTER)
            waitFor(hasTestTag(ASK_CHAT_TAG))
        }) {
            backToHome()
        }
    }

    @Test
    fun theAskScaryRowAndAboutThisFilmAskForConsentToo() {
        waitForHome()
        val container = (rule.activity.application as ScaryApplication).container
        val viewModel = ViewModelProvider(rule.activity, HomeViewModel.factory(container))[HomeViewModel::class.java]
        runBlocking { container.settingsRepository.setAiConsent(AiProvider.POLLINATIONS_FREE, false) }
        waitUntilOrDescribe("the Ask SCARY row on the home screen") { viewModel.uiState.value.rows.any { it is HomeRow.AskAi } }
        val consent = hasTestTag(AI_CONSENT_TAG)
        withCleanup({
            openViaSearch(bundledMovies().first { it.title == "Hereditary" })
            rule.onNodeWithTag(AI_ABOUT_TAG).performScrollTo().performClick()
            waitFor(consent)
            rule.onNode(hasText(text(R.string.ai_consent_later)) and hasAnyAncestor(consent)).performClick()
            waitForGone(consent)
            waitForGone(hasTestTag(AI_ABOUT_TEXT_TAG))
        }) {
            backToHome()
        }
    }

    @Test
    fun aiSettingsListEveryServiceWithItsState() {
        waitForHome()
        rule.onNodeWithContentDescription(text(R.string.settings_title)).performClick()
        val row = hasTestTag(AI_PROVIDER_ROW_TAG)
        waitFor(row)
        withCleanup({
            rule.onNode(row).performScrollTo().performClick()
            waitFor(hasTestTag(AI_PROVIDER_TAG))
            AiProvider.entries.forEach { waitFor(hasTestTag(aiProviderTag(it))) }
            // The free service is chosen by default, and says it needs no key.
            waitFor(hasTestTag(aiProviderTag(AiProvider.POLLINATIONS_FREE)) and isFocused())
            pressBack()
            waitForGone(hasTestTag(AI_PROVIDER_TAG))
        }) {
            backToHome()
        }
    }

    @Test
    fun theLanguageListShowsEveryLanguageInItselfWithTheCurrentOneChosen() {
        waitForHome()
        rule.onNodeWithContentDescription(text(R.string.settings_title)).performClick()
        val row = hasTestTag(LANGUAGE_ROW_TAG)
        waitFor(row)
        val dialog = hasTestTag(LANGUAGE_DIALOG_TAG)
        withCleanup({
            rule.onNode(row).performScrollTo().performClick()
            waitFor(dialog)
            Language.entries.forEach { waitFor(hasTestTag(languageTag(it)) and hasContentDescription(it.nativeName)) }
            // The language in use is marked and has the focus.
            val current = Language.fromCode(rule.activity.resources.configuration.locales[0].language)
            waitFor(hasTestTag(languageTag(current)) and isSelected() and isFocused())
            pressBack()
            waitForGone(dialog)
        }) {
            backToHome()
        }
    }

    @Test
    fun theTmdbAccountScreenOpensFromSettingsAndAsksForAKeyFirst() {
        waitForHome()
        rule.onNodeWithContentDescription(text(R.string.settings_title)).performClick()
        val row = hasTestTag(TMDB_ACCOUNT_ROW_TAG)
        waitFor(row)
        rule.onNode(row).performScrollTo().performClick()
        waitFor(hasTestTag(TMDB_ACCOUNT_TAG))
        // The emulator has no TMDB key: signing in needs one, and the screen says so, with the way to add it.
        waitFor(hasText(text(R.string.tmdb_account_no_key)))
        waitFor(hasText(text(R.string.tmdb_account_add_key)) and isFocused())
        backToHome()
    }

    @Test
    fun theSleepTimerStepsThroughItsChoicesAndTurnsOff() {
        waitForHome()
        val row = hasTestTag(SLEEP_ROW_TAG)
        val label = text(R.string.settings_sleep_timer)
        val off = hasContentDescription("$label: ${text(R.string.sleep_off)}")
        fun openSettings() {
            rule.onNodeWithContentDescription(text(R.string.settings_title)).performClick()
            waitFor(row)
        }
        openSettings()
        withCleanup({
            waitFor(off)
            for (minutes in listOf(30, 60, 90, 120)) {
                rule.onNode(row).performScrollTo().performClick()
                waitFor(hasContentDescription("$label: ${rule.activity.getString(R.string.sleep_left, minutes)}"))
            }
            // The countdown shows in the top bar too, on the home screen.
            backToHome()
            waitFor(hasContentDescription(rule.activity.getString(R.string.sleep_left, 120)))
            openSettings()
            rule.onNode(row).performScrollTo().performClick()
            waitFor(off)
        }) {
            // Never leave an alarm behind: from settings, step on until the timer is off, one saved step at a time.
            backToHome()
            openSettings()
            repeat(SLEEP_CHOICES) {
                if (rule.onAllNodes(off).fetchSemanticsNodes().isNotEmpty()) return@repeat
                val before = rule.onNode(row).fetchSemanticsNode().config.getOrNull(SemanticsProperties.ContentDescription)
                rule.onNode(row).performScrollTo().performClick()
                rule.waitUntil(TIMEOUT_MS) {
                    rule.onNode(row).fetchSemanticsNode().config.getOrNull(SemanticsProperties.ContentDescription) != before
                }
            }
            waitFor(off)
            backToHome()
        }
    }

    @Test
    fun goingDownTheRowsShowsEachWholeRowWithItsTitleAndGoingUpBringsTheHeroBack() {
        waitForHomeFocus()
        val watch = hasText(text(R.string.action_watch_stremio)) and isFocused()
        val donate = hasContentDescription(text(R.string.donate_qr_description))
        waitFor(watch)
        rule.onNode(donate).assertExists()
        // Down from the hero into the rows: the top gives them room, and every focused row shows whole, title and all.
        repeat(ROWS_TO_WALK) { step ->
            pressKey(KeyEvent.KEYCODE_DPAD_DOWN)
            if (step == 0) waitForGone(donate)
            assertFocusedRowFullyShown()
        }
        // And back up to the hero (Up lands on its nearest button), which comes back whole.
        val inRows = isFocused() and hasAnyAncestor(hasTestTag(HOME_ROWS_TAG))
        val heroButton = listOf(R.string.action_watch_stremio, R.string.action_trailer, R.string.action_details)
            .map { hasText(text(it)) }.reduce { a, b -> a or b }
        fun onHero() = rule.onAllNodes(heroButton and isFocused()).fetchSemanticsNodes().isNotEmpty()
        fun cardInRows() = rule.onAllNodes(inRows).fetchSemanticsNodes().firstOrNull()?.id
        repeat(ROWS_TO_WALK + 1) {
            val from = cardInRows()
            if (!onHero() && from != null) {
                pressKey(KeyEvent.KEYCODE_DPAD_UP)
                // The focus lands a moment after the key (the hero grows back first): on a card of the row above, or on
                // the hero's button. Pressing again before it has landed would carry it past the hero, to the top bar.
                waitUntilOrDescribe("the focus to land one row up") { onHero() || cardInRows().let { it != null && it != from } }
            }
        }
        waitFor(heroButton and isFocused())
        waitFor(donate)
    }

    @Test
    fun goingDownAFilmPageAndBackUpBringsItsTitleBack() {
        waitForHomeFocus()
        val movie = bundledMovies().first { it.title == "The Shining" }
        rule.activity.startActivity(Intent(rule.activity, MainActivity::class.java).putExtra(DEBUG_OPEN, "details:${movie.id}"))
        val watch = hasText(text(R.string.action_watch_stremio)) and isFocused()
        // The page's heading is the film's name in the viewer's language (the feed has it in Hebrew: "הניצוץ").
        val language = Language.fromCode(rule.activity.resources.configuration.locales[0].language)
        val title = isHeading() and hasContentDescription(movie.displayTitle(language))
        waitFor(watch)
        waitFor(title)
        fun titleTop() = rule.onNode(title).fetchSemanticsNode().boundsInRoot.top
        val top = titleTop()
        // All the way down: the buttons, the apps, rent or buy, then "More like this".
        repeat(DETAILS_DEPTH) { pressKey(KeyEvent.KEYCODE_DPAD_DOWN) }
        Log.i("ScaryUiTest", "film page: title top ${top}px at first, ${titleTop()}px at the bottom")
        // And back up to the top row of buttons (Up lands on the nearest one; more Ups stay there): the title comes
        // back with the buttons, where it was at first.
        repeat(DETAILS_DEPTH) { pressKey(KeyEvent.KEYCODE_DPAD_UP) }
        waitUntilOrDescribe("the film's title back at the top of its page") { kotlin.math.abs(titleTop() - top) < 1f }
        backToHome()
    }

    private fun pressKey(keyCode: Int) {
        waitUntilOrDescribe("the window to take the remote's keys") { readyForKeys() }
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(keyCode)
        rule.waitForIdle()
    }

    /** The focused card or chip lies wholly inside the rows, and so does its row's title (the nearest one above it). */
    private fun assertFocusedRowFullyShown() {
        var seen = "nothing focused in the rows"
        try {
            waitForRowShown { seen = it }
        } catch (failure: AssertionError) {
            throw AssertionError("Last seen: $seen\n${failure.message}", failure)
        }
        Log.d("ScaryKeys", "test: row shown, $seen")
    }

    private fun waitForRowShown(report: (String) -> Unit) {
        waitUntilOrDescribe("a focused card wholly inside the rows, its row title in view") {
            // The rows' bounds are read each time: the hero shrinks as the focus enters the rows, moving them up.
            val rows = rule.onNodeWithTag(HOME_ROWS_TAG).fetchSemanticsNode().boundsInRoot
            val focusedNode = rule.onAllNodes(isFocused() and hasAnyAncestor(hasTestTag(HOME_ROWS_TAG))).fetchSemanticsNodes()
                .singleOrNull() ?: return@waitUntilOrDescribe false
            val focused = focusedNode.boundsInRoot
            val titleNode = rule.onAllNodes(isHeading() and hasAnyAncestor(hasTestTag(HOME_ROWS_TAG))).fetchSemanticsNodes()
                .filter { it.boundsInRoot.bottom <= focused.top }
                .maxByOrNull { it.boundsInRoot.bottom }
            val title = titleNode?.boundsInRoot
            report("rows ${rows.top}..${rows.bottom}, focused " +
                "${focusedNode.config.getOrNull(SemanticsProperties.ContentDescription)} ${focused.top}..${focused.bottom}, " +
                "title ${titleNode?.config?.getOrNull(SemanticsProperties.Text)} ${title?.top}..${title?.bottom}")
            focused.top >= rows.top && focused.bottom <= rows.bottom && title != null && title.top >= rows.top
        }
    }

    @Test
    fun theSeriesTabShowsTheSeriesLibraryAndTheMoviesTabComesBack() {
        waitForHome()
        val catalog = bundledCatalog()
        val collection = catalog.collections.first { row -> catalog.moviesOf(row).all { it.isSeries } }
        val series = catalog.moviesOf(collection).first()
        val seriesTab = hasTestTag(tabTag(MediaKind.SERIES))
        val moviesTab = hasTestTag(tabTag(MediaKind.MOVIE))
        rule.onNode(moviesTab).assertIsSelected()
        withCleanup({
            rule.onNode(seriesTab).performClick()
            waitFor(seriesTab and isSelected())
            val rows = rule.onNodeWithTag(HOME_ROWS_TAG)
            rule.waitUntil(TIMEOUT_MS) { runCatching { rows.performScrollToKey("collection:${collection.id}") }.isSuccess }
            // A series card says its years, and the film rows are gone.
            waitFor(hasContentDescription("${series.title}, ${series.yearsLabel}", substring = true))
            assertTrue(runCatching { rows.performScrollToKey("moods") }.isFailure)
        }) {
            rule.onNode(moviesTab).performClick()
            waitFor(moviesTab and isSelected())
            rule.waitUntil(TIMEOUT_MS) { runCatching { rule.onNodeWithTag(HOME_ROWS_TAG).performScrollToKey("moods") }.isSuccess }
        }
    }

    @Test
    fun pickingFilmsNumbersThemAndTheBucketListKeepsAndChangesTheirOrder() {
        waitForHome()
        val catalog = bundledCatalog()
        val collection = stableRow(catalog)
        val (first, second) = catalog.moviesOf(collection).take(2)
        val rowKey = "collection:${collection.id}"
        fun card(movie: Movie) = hasContentDescription("${movie.title}, ${movie.year}", substring = true) and
            hasAnyAncestor(hasTestTag(rowTag(rowKey)))
        val clear = hasText(text(R.string.bucket_clear))
        withCleanup({
            // Empty, the bucket list's button goes straight to picking.
            rule.onNodeWithTag(BUCKET_BUTTON_TAG).performClick()
            waitFor(hasTestTag(PICKING_BANNER_TAG))
            rule.onNodeWithTag(HOME_ROWS_TAG).performScrollToKey(rowKey)
            rule.onNodeWithTag(rowTag(rowKey)).performScrollToIndex(0)
            waitFor(card(first))
            rule.onNode(card(first)).performClick()
            waitFor(card(first) and hasContentDescription(rule.activity.getString(R.string.bucket_badge, 1), substring = true))
            rule.onNode(card(second)).performClick()
            waitFor(card(second) and hasContentDescription(rule.activity.getString(R.string.bucket_badge, 2), substring = true))
            // OK did not open a movie page while picking.
            assertTrue(rule.onAllNodes(hasText(text(R.string.row_more_like))).fetchSemanticsNodes().isEmpty())
            rule.onNode(hasText(text(R.string.picking_done))).performClick()
            waitFor(hasTestTag(BUCKET_SCREEN_TAG))
            assertBucketOrder(first.id, second.id)
            // The second film moves up: the order swaps.
            val uiLanguage = Language.fromCode(rule.activity.resources.configuration.locales[0].language)
            rule.onNode(hasContentDescription(rule.activity.getString(R.string.bucket_move_up, second.displayTitle(uiLanguage)))).performClick()
            rule.waitUntil(TIMEOUT_MS) { runCatching { assertBucketOrder(second.id, first.id) }.isSuccess }
            // Clearing asks once more.
            rule.onNode(clear).performClick()
            waitFor(hasText(text(R.string.bucket_clear_confirm)))
            rule.onNode(hasText(text(R.string.bucket_clear_confirm))).performClick()
            waitFor(hasText(text(R.string.bucket_empty_title)))
            backToHome()
        }) {
            backToHome()
            rule.activity.startActivity(Intent(rule.activity, MainActivity::class.java).putExtra(DEBUG_OPEN, "bucket"))
            rule.waitForIdle()
            if (rule.onAllNodes(clear).fetchSemanticsNodes().isNotEmpty()) {
                rule.onNode(clear).performClick()
                waitFor(hasText(text(R.string.bucket_clear_confirm)))
                rule.onNode(hasText(text(R.string.bucket_clear_confirm))).performClick()
            }
            backToHome()
        }
    }

    /** The bucket list's rows, top to bottom, are these films. */
    private fun assertBucketOrder(vararg ids: String) {
        val tops = ids.map { rule.onNodeWithTag(bucketItemTag(it)).fetchSemanticsNode().boundsInRoot.top }
        assertTrue("Bucket list order is not ${ids.toList()}", tops.zipWithNext().all { (a, b) -> a < b })
    }

    @Test
    fun theQuestionAfterABucketListFilmOffersTheNextAndBackKeepsTheList() {
        waitForHomeFocus()
        rule.activity.startActivity(Intent(rule.activity, MainActivity::class.java).putExtra(DEBUG_OPEN, "bucket:prompt"))
        val prompt = hasTestTag(BUCKET_PROMPT_TAG)
        waitFor(prompt)
        // Inside the question: the home screen behind it can have its own "Not now" (the taste invitation).
        rule.onNode(hasText(text(R.string.bucket_prompt_later)) and hasAnyAncestor(prompt)).assertExists()
        rule.onNode(hasText(text(R.string.bucket_prompt_note)) and hasAnyAncestor(prompt)).assertExists()
        // Back goes to the window with the focus: wait until the dialog has it.
        waitFor(isFocused() and hasAnyAncestor(prompt))
        rule.waitForIdle()
        pressBack()
        waitForGone(prompt)
        waitUntilOrDescribe("the home screen") { onHome() }
    }

    /**
     * A row whose first films stay where the feed put them: films with a fear level, which no other row takes away and
     * no online refresh replaces (the row of new films is rewritten from Cinemeta; a film without a fear level is shown
     * only in the first row that reaches it).
     */
    private fun stableRow(catalog: Catalog) = catalog.collections.first { row -> catalog.moviesOf(row).take(2).let { it.size == 2 && it.all(Movie::curated) && it.none(Movie::isSeries) } }

    /**
     * The library as the app makes it (core FeedCatalog): the film feed this start of the app holds, with the old
     * library's fear levels. Read from the app, not from the packed file: the emulator is online, so the app may have
     * downloaded a newer feed than the one it was built with, and that is then the library on screen.
     */
    private fun bundledCatalog() = FeedCatalog.catalog(
        runBlocking { (rule.activity.application as ScaryApplication).container.feedRepository.load() }
            ?: throw AssertionError("the app holds no film feed"),
        rule.activity.assets.open("legacy.json").bufferedReader().use { LegacyLibrary.parse(it.readText()) },
    )

    /** Opens settings (no PIN on a fresh emulator), switches family night, and goes back home. */
    private fun toggleFamilyNightInSettings() {
        rule.onNodeWithContentDescription(text(R.string.settings_title)).performClick()
        val row = hasText(text(R.string.settings_family_night))
        waitFor(row)
        rule.onNode(row).performScrollTo().performClick()
        rule.waitForIdle()
        backToHome()
    }

    private companion object {
        const val TIMEOUT_MS = 20_000L
        /** How long the taste picker's list must stay the same to count as settled. */
        const val TASTE_SETTLE_MS = 2_000L
        const val DEBUG_OPEN = "scary.debug.open"
        // Details, the on-screen keyboard (which takes a Back of its own, sometimes twice), search, and a spare.
        const val MAX_BACKS = 6
        const val ONLINE_TIMEOUT_MS = 45_000L
        val NEAR_END_OF_FIRST_PAGES = listOf(99, 95, 90, 80, 60, 49, 45, 40, 30)
        const val BEYOND_FIRST_PAGES = 120
        const val SCREEN_DEPTH = 6
        const val SLEEP_CHOICES = 5
        const val ROWS_TO_WALK = 6
        const val DETAILS_DEPTH = 8
        const val BACK_SETTLE_MS = 1_500L
        const val SCREEN_CHARS = 6_000
        const val FOCUS_DESCRIPTION_CHARS = 300
    }
}
