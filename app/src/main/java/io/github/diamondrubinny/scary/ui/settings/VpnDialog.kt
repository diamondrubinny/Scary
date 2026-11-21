// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary.ui.settings

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import io.github.diamondrubinny.scary.R
import io.github.diamondrubinny.scary.data.VpnRepository
import io.github.diamondrubinny.scary.data.VpnWatcher
import io.github.diamondrubinny.scary.ui.components.ScaryButton
import io.github.diamondrubinny.scary.ui.theme.ScaryColors
import io.github.diamondrubinny.scary.ui.theme.ScaryType

const val VPN_DIALOG_TAG = "vpnDialog"
const val VPN_STATE_TAG = "vpnState"
const val VPN_CONNECT_TAG = "vpnConnect"
const val VPN_KILL_SWITCH_TAG = "vpnKillSwitch"
const val VPN_REMOVE_TAG = "vpnRemove"

/** What the VPN window can do; the defaults do nothing, for tests and previews. */
class VpnActions(
    /** What Android wants shown before SCARY may open a VPN; null when the viewer already agreed. */
    val consent: () -> Intent? = { null },
    val onConnect: () -> Unit = {},
    val onDisconnect: () -> Unit = {},
    val onRemove: () -> Unit = {},
    val onSetKillSwitch: (Boolean) -> Unit = {},
    val onOpenApp: (VpnWatcher.VpnApp) -> Unit = {},
)

/** The words for where the tunnel stands; also the value of the VPN row in Settings. */
@Composable
internal fun vpnStateWords(state: VpnRepository.State): String? = when (state) {
    VpnRepository.State.None -> null
    is VpnRepository.State.Down -> stringResource(R.string.vpn_disconnected)
    is VpnRepository.State.Connecting -> stringResource(R.string.vpn_connecting)
    is VpnRepository.State.Up -> stringResource(R.string.vpn_connected)
    is VpnRepository.State.Failed -> stringResource(
        when (state.reason) {
            VpnRepository.Failure.NOT_ALLOWED -> R.string.vpn_failed_not_allowed
            VpnRepository.Failure.NO_DNS -> R.string.vpn_failed_dns
            VpnRepository.Failure.START -> R.string.vpn_failed_start
        },
    )
}

/**
 * The VPN window in Settings (docs/VPN_PLAN.md): SCARY's own WireGuard tunnel with the viewer's settings file, and the
 * way to the VPN apps installed on the TV. The file itself comes from the phone remote; this window never shows it.
 */
@Composable
fun VpnDialog(
    state: VpnRepository.State,
    killSwitch: Boolean,
    apps: List<VpnWatcher.VpnApp>,
    actions: VpnActions,
    onDismiss: () -> Unit,
) {
    var noConsentScreen by remember { mutableStateOf(false) }
    var refused by remember { mutableStateOf(false) }
    var confirmingRemove by remember { mutableStateOf(false) }
    val askConsent = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) actions.onConnect() else refused = true
    }
    val connect = {
        noConsentScreen = false
        refused = false
        val intent = actions.consent()
        if (intent == null) {
            actions.onConnect()
        } else {
            // Some TV builds leave out Android's own "allow this VPN" screen.
            try {
                askConsent.launch(intent)
            } catch (e: ActivityNotFoundException) {
                noConsentScreen = true
            }
        }
    }
    val summary = when (state) {
        VpnRepository.State.None -> null
        is VpnRepository.State.Down -> state.summary
        is VpnRepository.State.Connecting -> state.summary
        is VpnRepository.State.Up -> state.summary
        is VpnRepository.State.Failed -> state.summary
    }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .widthIn(max = 560.dp)
                .background(ScaryColors.Crypt, RoundedCornerShape(4.dp))
                .padding(22.dp)
                .verticalScroll(rememberScrollState())
                .testTag(VPN_DIALOG_TAG),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            BasicText(stringResource(R.string.settings_vpn), style = ScaryType.Title, modifier = Modifier.semantics { heading() })
            if (summary == null) {
                BasicText(stringResource(R.string.vpn_how_to), style = ScaryType.Body)
            } else {
                val words = when {
                    noConsentScreen -> stringResource(R.string.vpn_no_consent_screen)
                    refused -> stringResource(R.string.vpn_failed_not_allowed)
                    else -> vpnStateWords(state).orEmpty()
                }
                // Said aloud when it changes: connecting, connected, or why not.
                BasicText(
                    words,
                    style = ScaryType.BodyStrong.copy(color = ScaryColors.Candle),
                    modifier = Modifier.testTag(VPN_STATE_TAG).semantics { liveRegion = LiveRegionMode.Polite },
                )
                // Where the file sends SCARY's traffic: the one thing from it that is shown, so a wrong file is seen.
                BasicText(stringResource(R.string.vpn_connects_to, summary.endpoints.joinToString()), style = ScaryType.Body)
                when (state) {
                    is VpnRepository.State.Up ->
                        ScaryButton(stringResource(R.string.vpn_disconnect), onClick = actions.onDisconnect, primary = true, modifier = Modifier.testTag(VPN_CONNECT_TAG))
                    is VpnRepository.State.Connecting -> Unit
                    else -> ScaryButton(stringResource(R.string.vpn_connect), onClick = connect, primary = true, modifier = Modifier.testTag(VPN_CONNECT_TAG))
                }
                BasicText(stringResource(R.string.vpn_scope_note), style = ScaryType.Caption)
                SettingRow(
                    label = stringResource(R.string.vpn_kill_switch),
                    value = stringResource(if (killSwitch) R.string.vpn_on else R.string.vpn_off),
                    onClick = { actions.onSetKillSwitch(!killSwitch) },
                    modifier = Modifier.testTag(VPN_KILL_SWITCH_TAG),
                )
                BasicText(stringResource(R.string.vpn_kill_switch_note), style = ScaryType.Caption)
                // Two presses: the key is deleted for good, and the file has to be sent again.
                if (confirmingRemove) {
                    ScaryButton(
                        stringResource(R.string.vpn_remove_confirm),
                        onClick = { confirmingRemove = false; actions.onRemove() },
                        modifier = Modifier.testTag(VPN_REMOVE_TAG),
                    )
                } else {
                    ScaryButton(stringResource(R.string.vpn_remove), onClick = { confirmingRemove = true }, quiet = true, modifier = Modifier.testTag(VPN_REMOVE_TAG))
                }
            }
            apps.forEach { app -> ScaryButton(stringResource(R.string.vpn_open_app, app.label), onClick = { onDismiss(); actions.onOpenApp(app) }, quiet = true) }
            ScaryButton(stringResource(R.string.update_close), onClick = onDismiss)
        }
    }
}
