// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary

import io.github.diamondrubinny.scary.ui.home.rowTag
import io.github.diamondrubinny.scary.core.cinemeta.Cinemeta
import org.junit.Assume.assumeTrue
import androidx.compose.ui.test.performScrollToIndex
import android.view.KeyEvent
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isNotSelected
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
import io.github.diamondrubinny.scary.core.catalog.Mood
import io.github.diamondrubinny.scary.core.links.KnownPackages
import io.github.diamondrubinny.scary.core.model.Movie
import io.github.diamondrubinny.scary.core.brand.Credit
import io.github.diamondrubinny.scary.ui.home.CREDIT_KEY
import io.github.diamondrubinny.scary.ui.home.HOME_ROWS_TAG
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import org.junit.Assert.assertTrue
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

    private fun text(id: Int): String = rule.activity.getString(id)

    private fun waitFor(matcher: SemanticsMatcher, timeoutMillis: Long = TIMEOUT_MS) {
        rule.waitUntil(timeoutMillis) { rule.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty() }
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
        }
        rule.waitUntil(TIMEOUT_MS) { onHome() }
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
        waitForHome()
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
            rule.waitUntil(TIMEOUT_MS) { runCatching { rows.performScrollToKey("mylist") }.isSuccess }
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

    /** A real long press of the remote's OK button, as the system delivers it (repeats and the long-press flag). */
    private fun longPressOk() {
        InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("input keyevent --longpress KEYCODE_DPAD_CENTER").close()
        // The shell command returns before the events arrive.
        Thread.sleep(LONG_PRESS_SETTLE_MS)
        rule.waitForIdle()
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
        const val MAX_BACKS = 4
        const val ONLINE_TIMEOUT_MS = 45_000L
        val NEAR_END_OF_FIRST_PAGES = listOf(99, 95, 90, 80, 60)
        const val BEYOND_FIRST_PAGES = 120
        const val LONG_PRESS_SETTLE_MS = 2_500L
    }
}
