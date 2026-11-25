// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary

import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.diamondrubinny.scary.data.RealDebridRepository.Reason
import io.github.diamondrubinny.scary.data.RealDebridRepository.State
import io.github.diamondrubinny.scary.ui.realdebrid.TORBOX_CODE_TAG
import io.github.diamondrubinny.scary.ui.realdebrid.TORBOX_SIGN_IN_TAG
import io.github.diamondrubinny.scary.ui.realdebrid.TORBOX_SIGN_OUT_TAG
import io.github.diamondrubinny.scary.ui.realdebrid.TORBOX_TAG
import io.github.diamondrubinny.scary.ui.realdebrid.TorBoxScreen
import io.github.diamondrubinny.scary.ui.theme.ScaryTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The TorBox account screen, on its own: nothing is sent to TorBox here, only what the screen says and asks for. */
@RunWith(AndroidJUnit4::class)
class TorBoxScreenTest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private fun text(id: Int, vararg args: Any) = rule.activity.getString(id, *args)

    @Test
    fun connectingShowsTheCodeAndTheAccountThenDisconnects() {
        val calls = mutableListOf<String>()
        var state by mutableStateOf<State>(State.SignedOut)
        rule.runOnUiThread { rule.activity.setContent {
            ScaryTheme {
                TorBoxScreen(
                    state = state,
                    onSignIn = { calls += "in"; state = State.Waiting("https://torbox.app/oauth/device", "123456") },
                    onCancel = { calls += "cancel"; state = State.SignedOut },
                    onSignOut = { calls += "out"; state = State.SignedOut },
                )
            }
        } }
        rule.onNode(hasTestTag(TORBOX_TAG)).assertExists()
        rule.onNode(hasText(text(R.string.torbox_signed_out))).assertExists()
        // What the connection is, said before connecting: a key to the whole account that does not run out.
        rule.onNode(hasText(text(R.string.torbox_key_note))).assertExists()

        rule.onNode(hasTestTag(TORBOX_SIGN_IN_TAG)).performClick()
        rule.onNode(hasTestTag(TORBOX_CODE_TAG)).assertTextEquals("123456")
        rule.onNode(hasText(text(R.string.torbox_step_open, "torbox.app/oauth/device"))).assertExists()
        rule.onNode(hasTestTag(TORBOX_SIGN_IN_TAG)).assertDoesNotExist()

        rule.runOnUiThread { state = State.SignedIn("viewer@example.org", premium = true, expiration = "2027-01-01T00:00:00Z") }
        rule.onNode(hasText(text(R.string.torbox_signed_in_until, "viewer@example.org", "2027-01-01"))).assertExists()
        rule.onNode(hasTestTag(TORBOX_CODE_TAG)).assertDoesNotExist()

        rule.onNode(hasTestTag(TORBOX_SIGN_OUT_TAG)).performClick()
        rule.onNode(hasText(text(R.string.torbox_signed_out))).assertExists()
        assertEquals(listOf("in", "out"), calls)
    }

    @Test
    fun aFreePlanAndAFailureAreSaidInWords() {
        var state by mutableStateOf<State>(State.SignedIn("viewer@example.org", premium = false, expiration = ""))
        rule.runOnUiThread { rule.activity.setContent {
            ScaryTheme { TorBoxScreen(state = state, onSignIn = {}, onCancel = {}, onSignOut = {}) }
        } }
        rule.onNode(hasText(text(R.string.torbox_signed_in_free, "viewer@example.org"))).assertExists()
        rule.runOnUiThread { state = State.Failed(Reason.EXPIRED) }
        rule.onNode(hasText(text(R.string.torbox_expired))).assertExists()
        rule.onNode(hasTestTag(TORBOX_SIGN_IN_TAG)).assertExists()
        rule.runOnUiThread { state = State.Failed(Reason.SESSION_ENDED) }
        rule.onNode(hasText(text(R.string.torbox_session_ended))).assertExists()
    }
}
