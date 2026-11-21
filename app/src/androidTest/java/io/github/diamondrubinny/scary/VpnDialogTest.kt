// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary

import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.diamondrubinny.scary.core.vpn.WireGuardConfig
import io.github.diamondrubinny.scary.data.VpnRepository
import io.github.diamondrubinny.scary.ui.settings.VPN_CONNECT_TAG
import io.github.diamondrubinny.scary.ui.settings.VPN_DIALOG_TAG
import io.github.diamondrubinny.scary.ui.settings.VPN_KILL_SWITCH_TAG
import io.github.diamondrubinny.scary.ui.settings.VPN_REMOVE_TAG
import io.github.diamondrubinny.scary.ui.settings.VPN_STATE_TAG
import io.github.diamondrubinny.scary.ui.settings.VpnActions
import io.github.diamondrubinny.scary.ui.settings.VpnDialog
import io.github.diamondrubinny.scary.ui.theme.ScaryTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The VPN window in Settings, on its own: no tunnel is opened here, only what the window says and asks for. */
@RunWith(AndroidJUnit4::class)
class VpnDialogTest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private val summary = WireGuardConfig.Summary(listOf("vpn.example.org:51820"), listOf("10.2.0.2/32"), listOf("10.2.0.1"))

    @Test
    fun withNoSettingsFileItSaysHowToAddOne() {
        rule.runOnUiThread { rule.activity.setContent {
            ScaryTheme { VpnDialog(VpnRepository.State.None, killSwitch = false, apps = emptyList(), actions = VpnActions(), onDismiss = {}) }
        } }
        rule.onNode(hasTestTag(VPN_DIALOG_TAG)).assertExists()
        rule.onNode(hasText(rule.activity.getString(R.string.vpn_how_to))).assertExists()
        rule.onNode(hasTestTag(VPN_CONNECT_TAG)).assertDoesNotExist()
        rule.onNode(hasTestTag(VPN_REMOVE_TAG)).assertDoesNotExist()
    }

    @Test
    fun itConnectsDisconnectsAndRemovesOnlyAfterASecondPress() {
        val calls = mutableListOf<String>()
        var state by mutableStateOf<VpnRepository.State>(VpnRepository.State.Down(summary))
        var killSwitch by mutableStateOf(false)
        val actions = VpnActions(
            // Android already agreed: no system screen in this test.
            consent = { null },
            onConnect = { calls += "connect"; state = VpnRepository.State.Up(summary) },
            onDisconnect = { calls += "disconnect"; state = VpnRepository.State.Down(summary) },
            onRemove = { calls += "remove"; state = VpnRepository.State.None },
            onSetKillSwitch = { calls += "kill:$it"; killSwitch = it },
        )
        rule.runOnUiThread { rule.activity.setContent {
            ScaryTheme { VpnDialog(state, killSwitch = killSwitch, apps = emptyList(), actions = actions, onDismiss = {}) }
        } }
        val text = { id: Int -> rule.activity.getString(id) }
        // Where the file connects to is shown; the state is in words.
        rule.onNode(hasText(rule.activity.getString(R.string.vpn_connects_to, "vpn.example.org:51820"))).assertExists()
        rule.onNode(hasTestTag(VPN_STATE_TAG)).assertTextContains(text(R.string.vpn_disconnected))

        rule.onNode(hasTestTag(VPN_CONNECT_TAG)).performClick()
        rule.onNode(hasTestTag(VPN_STATE_TAG)).assertTextContains(text(R.string.vpn_connected))
        rule.onNode(hasTestTag(VPN_CONNECT_TAG)).performClick()
        rule.onNode(hasTestTag(VPN_STATE_TAG)).assertTextContains(text(R.string.vpn_disconnected))

        rule.onNode(hasTestTag(VPN_KILL_SWITCH_TAG)).performScrollTo().performClick()
        rule.onNode(hasTestTag(VPN_REMOVE_TAG)).performScrollTo().performClick()
        assertEquals(listOf("connect", "disconnect", "kill:true"), calls)
        rule.onNode(hasText(text(R.string.vpn_remove_confirm))).assertExists()
        rule.onNode(hasTestTag(VPN_REMOVE_TAG)).performScrollTo().performClick()
        assertEquals("remove", calls.last())
        rule.onNode(hasText(text(R.string.vpn_how_to))).assertExists()
    }

    @Test
    fun aFailureIsSaidInWords() {
        rule.runOnUiThread { rule.activity.setContent {
            ScaryTheme {
                VpnDialog(VpnRepository.State.Failed(summary, VpnRepository.Failure.NO_DNS), killSwitch = true, apps = emptyList(), actions = VpnActions(), onDismiss = {})
            }
        } }
        rule.onNode(hasTestTag(VPN_STATE_TAG)).assertTextContains(rule.activity.getString(R.string.vpn_failed_dns))
        rule.onNode(hasTestTag(VPN_CONNECT_TAG)).assertExists()
    }
}
