// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary.ui.remote

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.diamondrubinny.scary.R
import io.github.diamondrubinny.scary.core.brand.Donation
import io.github.diamondrubinny.scary.remote.PhoneRemoteServer
import io.github.diamondrubinny.scary.ui.components.QrImage
import io.github.diamondrubinny.scary.ui.components.ScaryButton
import io.github.diamondrubinny.scary.ui.home.SidePadding
import io.github.diamondrubinny.scary.ui.theme.ScaryColors
import io.github.diamondrubinny.scary.ui.theme.ScaryType

/** Test tags of the phone remote's screen and its QR code. */
const val PHONE_REMOTE_TAG = "phoneRemote"
const val PHONE_REMOTE_QR_TAG = "phoneRemoteQr"

/**
 * The phone remote: a QR code for the phone, how to use it, whether a phone is connected, and Disconnect. Opening
 * this screen turns the remote on ([onStart]); it stays on while the viewer browses, until Disconnect or 30 minutes
 * without a command.
 */
@Composable
fun PhoneRemoteScreen(state: PhoneRemoteServer.State, onStart: () -> Unit, onStop: () -> Unit, onNewCode: () -> Unit) {
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { onStart() }
    val on = state as? PhoneRemoteServer.State.On
    LaunchedEffect(on != null) {
        withFrameNanos { }
        runCatching { firstFocus.requestFocus() }
    }
    Row(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = SidePadding, vertical = 32.dp)
            .testTag(PHONE_REMOTE_TAG),
        horizontalArrangement = Arrangement.spacedBy(40.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            BasicText(
                stringResource(R.string.remote_title),
                style = ScaryType.Display.copy(fontSize = ScaryType.Display.fontSize * 0.7f),
                modifier = Modifier.semantics { heading() },
            )
            BasicText(stringResource(R.string.remote_step_wifi), style = ScaryType.Body)
            BasicText(stringResource(R.string.remote_step_scan), style = ScaryType.Body)
            BasicText(stringResource(R.string.remote_step_use), style = ScaryType.Body)
            BasicText(
                text = when (state) {
                    is PhoneRemoteServer.State.On -> stringResource(if (state.connected) R.string.remote_connected else R.string.remote_waiting)
                    is PhoneRemoteServer.State.Stopped -> stringResource(
                        when (state.reason) {
                            PhoneRemoteServer.StopReason.NO_NETWORK -> R.string.remote_no_network
                            PhoneRemoteServer.StopReason.IDLE -> R.string.remote_idle
                            PhoneRemoteServer.StopReason.FORGED -> R.string.remote_forged
                        },
                    )
                    PhoneRemoteServer.State.Off -> stringResource(R.string.remote_off)
                },
                style = ScaryType.BodyStrong.copy(color = ScaryColors.Candle),
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 4.dp)) {
                if (on != null) {
                    ScaryButton(stringResource(R.string.remote_disconnect), onClick = onStop, primary = true, focusRequester = firstFocus)
                    ScaryButton(stringResource(R.string.remote_new_code), onClick = onNewCode)
                } else {
                    ScaryButton(stringResource(R.string.remote_turn_on), onClick = onStart, primary = true, focusRequester = firstFocus)
                }
            }
            BasicText(stringResource(R.string.remote_limits), style = ScaryType.Caption)
            BasicText(stringResource(R.string.remote_privacy), style = ScaryType.Caption)
        }
        if (on != null) {
            val code = remember(on.url) { Donation.qr(on.url) }
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(QR_SIZE)) {
                QrImage(
                    code = code,
                    size = QR_SIZE,
                    description = stringResource(R.string.remote_qr_description),
                    modifier = Modifier.testTag(PHONE_REMOTE_QR_TAG),
                )
            }
        }
    }
}

/** Big enough to scan from the sofa: the address with its secret makes a code of about 37 modules. */
private val QR_SIZE = 240.dp
