// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary

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
import androidx.compose.ui.test.performScrollToKey
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.diamondrubinny.scary.core.catalog.CatalogParser
import io.github.diamondrubinny.scary.core.catalog.Decades
import io.github.diamondrubinny.scary.core.catalog.Mood
import io.github.diamondrubinny.scary.core.links.KnownPackages
import io.github.diamondrubinny.scary.core.model.Movie
import io.github.diamondrubinny.scary.core.brand.Credit
import io.github.diamondrubinny.scary.ui.home.CREDIT_KEY
import io.github.diamondrubinny.scary.ui.home.HOME_ROWS_TAG
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
import org.junit.Assert.assertTrue
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.printToString
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

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

    private fun bundledMovies(): List<Movie> =
        rule.activity.assets.open("catalog.json").bufferedReader().use { CatalogParser.parse(it.readText()) }.movies

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
        waitForHome()
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
        val notice = hasText(text(R.string.settings_content_notice))
        waitFor(notice)
        rule.onNode(notice).performScrollTo().assertExists()
        // The movie data credit (TMDB's terms) and the donation code are in the same About section.
        rule.onNode(hasText(text(R.string.data_credit))).performScrollTo().assertExists()
        rule.onNodeWithContentDescription(text(R.string.donate_qr_description)).performScrollTo().assertExists()
        backToHome()
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
        val catalog = rule.activity.assets.open("catalog.json").bufferedReader().use { CatalogParser.parse(it.readText()) }
        val collection = catalog.collections.first()
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
        val rowKey = "collection:" + Cinemeta.onlineCollectionId("horror_deep")
        val rows = rule.onNodeWithTag(HOME_ROWS_TAG)
        // Online rows need Cinemeta; without the network the row is not there, and there is nothing to test.
        val present = runCatching { rule.waitUntil(TIMEOUT_MS) { runCatching { rows.performScrollToKey(rowKey) }.isSuccess } }.isSuccess
        assumeTrue("Online rows are not available on this emulator", present)
        val row = rule.onNodeWithTag(rowTag(rowKey))
        // The daily refresh loads two pages (up to 100 movies); reaching the end asks for the next page. Scrolling to
        // the furthest card that exists brings the end into view, whatever the exact count.
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
        repeat(ROWS_TO_WALK + 1) {
            if (rule.onAllNodes(inRows).fetchSemanticsNodes().isNotEmpty()) pressKey(KeyEvent.KEYCODE_DPAD_UP)
        }
        val heroButton = listOf(R.string.action_watch_stremio, R.string.action_trailer, R.string.action_details)
            .map { hasText(text(it)) }.reduce { a, b -> a or b }
        waitFor(heroButton and isFocused())
        waitFor(donate)
    }

    private fun pressKey(keyCode: Int) {
        waitUntilOrDescribe("the window to take the remote's keys") { readyForKeys() }
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(keyCode)
        rule.waitForIdle()
    }

    /** The focused card or chip lies wholly inside the rows, and so does its row's title (the nearest one above it). */
    private fun assertFocusedRowFullyShown() {
        val rows = rule.onNodeWithTag(HOME_ROWS_TAG).fetchSemanticsNode().boundsInRoot
        waitUntilOrDescribe("a focused card wholly inside the rows, its row title in view") {
            val focused = rule.onAllNodes(isFocused() and hasAnyAncestor(hasTestTag(HOME_ROWS_TAG))).fetchSemanticsNodes()
                .singleOrNull()?.boundsInRoot ?: return@waitUntilOrDescribe false
            val title = rule.onAllNodes(isHeading() and hasAnyAncestor(hasTestTag(HOME_ROWS_TAG))).fetchSemanticsNodes()
                .map { it.boundsInRoot }
                .filter { it.bottom <= focused.top }
                .maxByOrNull { it.bottom }
            focused.top >= rows.top && focused.bottom <= rows.bottom && title != null && title.top >= rows.top
        }
    }

    @Test
    fun theSeriesTabShowsTheSeriesLibraryAndTheMoviesTabComesBack() {
        waitForHome()
        val catalog = rule.activity.assets.open("series.json").bufferedReader().use { CatalogParser.parse(it.readText(), MediaKind.SERIES) }
        val collection = catalog.collections.first()
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
        val collection = catalog.collections.first()
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
        rule.onNode(hasText(text(R.string.bucket_prompt_later))).assertExists()
        rule.onNode(hasText(text(R.string.bucket_prompt_note))).assertExists()
        // Back goes to the window with the focus: wait until the dialog has it.
        waitFor(isFocused() and hasAnyAncestor(prompt))
        rule.waitForIdle()
        pressBack()
        waitForGone(prompt)
        waitUntilOrDescribe("the home screen") { onHome() }
    }

    private fun bundledCatalog() = rule.activity.assets.open("catalog.json").bufferedReader().use { CatalogParser.parse(it.readText()) }

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
        const val DEBUG_OPEN = "scary.debug.open"
        // Details, the on-screen keyboard (which takes a Back of its own, sometimes twice), search, and a spare.
        const val MAX_BACKS = 6
        const val ONLINE_TIMEOUT_MS = 45_000L
        val NEAR_END_OF_FIRST_PAGES = listOf(99, 95, 90, 80, 60)
        const val BEYOND_FIRST_PAGES = 120
        const val SCREEN_DEPTH = 6
        const val SLEEP_CHOICES = 5
        const val ROWS_TO_WALK = 6
        const val SCREEN_CHARS = 6_000
        const val FOCUS_DESCRIPTION_CHARS = 300
    }
}
