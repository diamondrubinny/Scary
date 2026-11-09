// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary

import android.view.KeyEvent
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.diamondrubinny.scary.data.RealDebridRepository
import io.github.diamondrubinny.scary.ui.home.HOME_ROWS_TAG
import io.github.diamondrubinny.scary.ui.home.HomeViewModel
import io.github.diamondrubinny.scary.ui.home.PlayerSession
import io.github.diamondrubinny.scary.ui.player.PLAYER_TAG
import io.github.diamondrubinny.scary.ui.realdebrid.REAL_DEBRID_TAG
import io.github.diamondrubinny.scary.ui.settings.REAL_DEBRID_ROW_TAG
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * The internal player and the Real-Debrid screen. The player plays a short local clip (no account, no network): the
 * same player a Real-Debrid file opens in. The Real-Debrid screen is checked signed out, without connecting.
 */
@RunWith(AndroidJUnit4::class)
class PlayerTest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    @Before
    fun likeATv() {
        InstrumentationRegistry.getInstrumentation().setInTouchMode(false)
    }

    private fun text(id: Int): String = rule.activity.getString(id)

    private fun waitFor(matcher: SemanticsMatcher, timeoutMillis: Long = TIMEOUT_MS) {
        rule.waitUntil(timeoutMillis) { rule.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun viewModel(): HomeViewModel {
        val container = (rule.activity.application as ScaryApplication).container
        return ViewModelProvider(rule.activity, HomeViewModel.factory(container))[HomeViewModel::class.java]
    }

    private fun session(uri: String, transcoded: String? = null) = PlayerSession(
        key = "test:${uri.hashCode()}",
        playable = RealDebridRepository.Playable(uri, "Test clip") { transcoded },
        movieId = null,
        startAtMs = 0L,
    )

    private fun pressBack() {
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        rule.waitForIdle()
    }

    @Test
    fun aClipPlaysAndBackLeavesThePlayer() {
        waitFor(hasTestTag(HOME_ROWS_TAG))
        // The clip ships in the test APK; the player reads files from the app's own storage.
        val clip = File(rule.activity.cacheDir, "test-clip.mp4")
        InstrumentationRegistry.getInstrumentation().context.assets.open("test-clip.mp4").use { input -> clip.outputStream().use(input::copyTo) }
        rule.runOnUiThread { viewModel().playForTest(session(clip.toURI().toString())) }
        waitFor(hasTestTag(PLAYER_TAG))
        // Playing: the main button offers to pause.
        waitFor(hasText(text(R.string.player_pause)))
        assertTrue(rule.onAllNodes(hasText(text(R.string.player_error))).fetchSemanticsNodes().isEmpty())
        pressBack()
        rule.waitUntil(TIMEOUT_MS) { rule.onAllNodes(hasTestTag(PLAYER_TAG)).fetchSemanticsNodes().isEmpty() }
        assertTrue("The player session ends with the screen", viewModel().player.value == null)
    }

    @Test
    fun aFileThatCannotPlaySaysSoAndCloses() {
        waitFor(hasTestTag(HOME_ROWS_TAG))
        val missing = File(rule.activity.cacheDir, "missing-file.mkv").also { it.delete() }
        rule.runOnUiThread { viewModel().playForTest(session(missing.toURI().toString())) }
        waitFor(hasText(text(R.string.player_error)))
        rule.onNode(hasText(text(R.string.player_close))).performClick()
        rule.waitUntil(TIMEOUT_MS) { rule.onAllNodes(hasTestTag(PLAYER_TAG)).fetchSemanticsNodes().isEmpty() }
    }

    @Test
    fun theRealDebridScreenExplainsAndOffersToConnect() {
        waitFor(hasTestTag(HOME_ROWS_TAG))
        rule.onNodeWithContentDescription(text(R.string.settings_title)).performClick()
        waitFor(hasTestTag(REAL_DEBRID_ROW_TAG))
        rule.onNode(hasTestTag(REAL_DEBRID_ROW_TAG)).performScrollTo().performClick()
        waitFor(hasTestTag(REAL_DEBRID_TAG))
        rule.onNode(hasText(text(R.string.rd_what))).assertExists()
        rule.onNode(hasText(text(R.string.rd_sign_in))).assertExists()
        rule.onNode(hasText(text(R.string.rd_privacy))).assertExists()
        pressBack()
    }


    private companion object {
        const val TIMEOUT_MS = 20_000L
    }
}
