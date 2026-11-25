// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary.ui.realdebrid

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
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
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import io.github.diamondrubinny.scary.R
import io.github.diamondrubinny.scary.core.brand.Donation
import io.github.diamondrubinny.scary.data.RealDebridRepository.Reason
import io.github.diamondrubinny.scary.data.RealDebridRepository.State
import io.github.diamondrubinny.scary.ui.components.QrImage
import io.github.diamondrubinny.scary.ui.components.ScaryButton
import io.github.diamondrubinny.scary.ui.home.SidePadding
import io.github.diamondrubinny.scary.ui.theme.ScaryColors
import io.github.diamondrubinny.scary.ui.theme.ScaryType
import io.github.diamondrubinny.scary.ui.theme.scaryLayout

const val TORBOX_TAG = "torBox"
const val TORBOX_CODE_TAG = "torBoxCode"
const val TORBOX_SIGN_IN_TAG = "torBoxSignIn"
const val TORBOX_SIGN_OUT_TAG = "torBoxSignOut"
const val TORBOX_OPEN_TAG = "torBoxOpen"

private val QR_SIZE = 220.dp

/**
 * The viewer's TorBox account (docs/TORBOX_PLAN.md): connecting with a code typed on TorBox's own page, and
 * disconnecting. The account's state is Real-Debrid's own type, since both connect the same way.
 */
@Composable
fun TorBoxScreen(state: State, onSignIn: () -> Unit, onCancel: () -> Unit, onSignOut: () -> Unit) {
    val firstFocus = remember { FocusRequester() }
    // Leaving while waiting stops asking TorBox: approving later would not reach the TV anyway.
    val waiting by rememberUpdatedState(state is State.Waiting)
    val cancel by rememberUpdatedState(onCancel)
    DisposableEffect(Unit) {
        onDispose { if (waiting) cancel() }
    }
    LaunchedEffect(state::class) {
        withFrameNanos { }
        runCatching { firstFocus.requestFocus() }
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = SidePadding, vertical = 28.dp)
            .testTag(TORBOX_TAG),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        BasicText(
            stringResource(R.string.torbox_title),
            style = ScaryType.Display.copy(fontSize = ScaryType.Display.fontSize * 0.7f),
            modifier = Modifier.semantics { heading() },
        )
        BasicText(stringResource(R.string.torbox_what), style = ScaryType.Body)
        BasicText(
            text = statusText(state),
            style = ScaryType.BodyStrong.copy(color = ScaryColors.Candle),
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            when (state) {
                State.SignedOut, is State.Failed ->
                    ScaryButton(stringResource(R.string.torbox_sign_in), onClick = onSignIn, primary = true, focusRequester = firstFocus, modifier = Modifier.testTag(TORBOX_SIGN_IN_TAG))
                is State.Waiting -> ScaryButton(stringResource(R.string.torbox_cancel), onClick = onCancel, focusRequester = firstFocus)
                is State.SignedIn ->
                    ScaryButton(stringResource(R.string.torbox_sign_out), onClick = onSignOut, focusRequester = firstFocus, modifier = Modifier.testTag(TORBOX_SIGN_OUT_TAG))
            }
        }
        if (state is State.Waiting) WaitingForCode(state, scaryLayout.narrow)
        // What the connection is, before and after: it is a key to the whole account and does not run out.
        BasicText(stringResource(R.string.torbox_key_note), style = ScaryType.Caption.copy(color = ScaryColors.Ash))
        BasicText(stringResource(R.string.torbox_privacy), style = ScaryType.Caption)
    }
}

@Composable
private fun WaitingForCode(state: State.Waiting, narrow: Boolean) {
    val qr = remember(state.verificationUrl) { Donation.qr(state.verificationUrl) }
    val steps: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            BasicText(stringResource(R.string.torbox_step_open, state.verificationUrl.removePrefix("https://")), style = ScaryType.Body.copy(textDirection = TextDirection.Content))
            BasicText(stringResource(R.string.torbox_step_code), style = ScaryType.Body)
            // The code is digits: shown big, left to right, and read out as it appears.
            BasicText(
                state.userCode,
                style = ScaryType.Display.copy(color = ScaryColors.Candle, textDirection = TextDirection.Ltr),
                modifier = Modifier.testTag(TORBOX_CODE_TAG).semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
    }
    if (narrow) {
        steps()
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(32.dp), verticalAlignment = Alignment.CenterVertically) {
            QrImage(code = qr, size = QR_SIZE, description = stringResource(R.string.torbox_qr_description))
            steps()
        }
    }
}

@Composable
private fun statusText(state: State): String = when (state) {
    State.SignedOut -> stringResource(R.string.torbox_signed_out)
    is State.Waiting -> stringResource(R.string.torbox_waiting)
    is State.SignedIn -> when {
        state.username.isBlank() -> stringResource(R.string.torbox_connected)
        !state.premium -> stringResource(R.string.torbox_signed_in_free, state.username)
        state.expiration.isNotBlank() -> stringResource(R.string.torbox_signed_in_until, state.username, state.expiration.take(10))
        else -> stringResource(R.string.torbox_signed_in, state.username)
    }
    is State.Failed -> stringResource(
        when (state.reason) {
            Reason.UNREACHABLE, Reason.BUSY -> R.string.torbox_unreachable
            Reason.EXPIRED -> R.string.torbox_expired
            Reason.STORAGE -> R.string.torbox_storage
            Reason.SESSION_ENDED -> R.string.torbox_session_ended
        },
    )
}
