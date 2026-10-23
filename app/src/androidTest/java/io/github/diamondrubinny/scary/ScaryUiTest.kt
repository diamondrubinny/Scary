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

    private fun onHome(): Boolean = rule.onAllNodes(hasText(text(R.string.row_apps))).fetchSemanticsNodes().isNotEmpty()

    /** Back until the home screen shows: the first Back may only close the on-screen keyboard. */
    private fun backToHome() {
        repeat(MAX_BACKS) {
            if (onHome()) return
            pressBack()
        }
        waitForHome()
    }

    @Test
    fun homeShowsTonightsPickAndTheAppsRow() {
        waitForHome()
        waitFor(hasText(text(R.string.night_pick)))
        rule.onNodeWithContentDescription(text(R.string.search_title)).assertExists()
        rule.onNodeWithContentDescription(text(R.string.surprise_me)).assertExists()
    }

    @Test
    fun theCreditShowsUnderTheLogoAndAtTheEndOfTheRows() {
        waitForHome()
        // Required by the NOTICE file (Apache License 2.0); the build fails if it disappears from the screen.
        rule.onNode(hasText(Credit.SHORT)).assertExists()
        rule.onNodeWithTag(HOME_ROWS_TAG).performScrollToKey(CREDIT_KEY)
        waitFor(hasText(Credit.LINE))
    }

    @Test
    fun settingsSayThatScaryHoldsNoVideoAndOnlyOpensInstalledApps() {
        waitForHome()
        rule.onNodeWithContentDescription(text(R.string.settings_title)).performClick()
        val notice = hasText(text(R.string.settings_content_notice))
        waitFor(notice)
        rule.onNode(notice).performScrollTo().assertExists()
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
    }
}
