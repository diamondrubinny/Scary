// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary.ui.stremio

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
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import io.github.diamondrubinny.scary.R
import io.github.diamondrubinny.scary.core.brand.Donation
import io.github.diamondrubinny.scary.data.StremioRepository.Reason
import io.github.diamondrubinny.scary.data.StremioRepository.State
import io.github.diamondrubinny.scary.ui.components.QrImage
import io.github.diamondrubinny.scary.ui.components.ScaryButton
import io.github.diamondrubinny.scary.ui.home.SidePadding
import io.github.diamondrubinny.scary.ui.theme.ScaryColors
import io.github.diamondrubinny.scary.ui.theme.ScaryType
import io.github.diamondrubinny.scary.ui.theme.scaryLayout
import java.text.DateFormat
import java.util.Date

/** Test tags of the Stremio account screen. */
const val STREMIO_TAG = "stremioAccount"
const val STREMIO_CODE_TAG = "stremioCode"

class StremioActions(
    val onSignIn: () -> Unit,
    val onCancel: () -> Unit,
    val onSignOut: () -> Unit,
    val onSync: () -> Unit,
)

/**
 * The viewer's Stremio account: what connecting does (and what it never does: no add-ons, no streams), Stremio's own
 * code and QR code to approve on a phone while the TV waits, and once connected, the last sync, "Sync now" and
 * "Disconnect".
 */
@Composable
fun StremioAccountScreen(state: State, actions: StremioActions) {
    val firstFocus = remember { FocusRequester() }
    // Leaving while waiting stops asking Stremio: approving later would not reach the TV anyway.
    val waiting by rememberUpdatedState(state is State.Waiting)
    val cancel by rememberUpdatedState(actions.onCancel)
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
            .testTag(STREMIO_TAG),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        BasicText(
            stringResource(R.string.stremio_title),
            style = ScaryType.Display.copy(fontSize = ScaryType.Display.fontSize * 0.7f),
            modifier = Modifier.semantics { heading() },
        )
        BasicText(stringResource(R.string.stremio_what), style = ScaryType.Body)
        BasicText(
            text = statusText(state),
            style = ScaryType.BodyStrong.copy(color = ScaryColors.Candle),
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            when (state) {
                State.SignedOut, is State.Failed ->
                    ScaryButton(stringResource(R.string.stremio_sign_in), onClick = actions.onSignIn, primary = true, focusRequester = firstFocus)
                is State.Waiting -> ScaryButton(stringResource(R.string.stremio_cancel), onClick = actions.onCancel, focusRequester = firstFocus)
                is State.SignedIn -> {
                    ScaryButton(stringResource(R.string.stremio_sync_now), onClick = actions.onSync, focusRequester = firstFocus)
                    ScaryButton(stringResource(R.string.stremio_sign_out), onClick = actions.onSignOut)
                }
            }
        }
        if (state is State.Waiting) WaitingForCode(state)
        BasicText(stringResource(R.string.stremio_privacy), style = ScaryType.Caption)
    }
}

@Composable
private fun WaitingForCode(state: State.Waiting) {
    val qr = remember(state.link) { Donation.qr(state.link) }
    val steps: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            BasicText(stringResource(R.string.stremio_step_open, state.link.removePrefix("https://")), style = ScaryType.Body.copy(textDirection = TextDirection.Content))
            BasicText(stringResource(R.string.stremio_step_code), style = ScaryType.Body)
            // The code is Latin letters and digits: shown big, left to right, and read out letter by letter.
            val spelled = state.code.toCharArray().joinToString(" ")
            BasicText(
                state.code,
                style = ScaryType.Display.copy(color = ScaryColors.Candle, textDirection = TextDirection.Ltr),
                modifier = Modifier.testTag(STREMIO_CODE_TAG).semantics {
                    contentDescription = spelled
                    liveRegion = LiveRegionMode.Polite
                },
            )
        }
    }
    if (scaryLayout.narrow) {
        steps()
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(32.dp), verticalAlignment = Alignment.CenterVertically) {
            QrImage(code = qr, size = QR_SIZE, description = stringResource(R.string.stremio_qr_description))
            steps()
        }
    }
}

@Composable
private fun statusText(state: State): String = when (state) {
    State.SignedOut -> stringResource(R.string.stremio_signed_out)
    is State.Waiting -> stringResource(R.string.stremio_waiting)
    is State.SignedIn -> when {
        state.syncing -> stringResource(R.string.stremio_syncing)
        state.lastResult?.complete == false -> stringResource(R.string.stremio_sync_partial)
        state.lastSyncMillis != null -> {
            val format = DateFormat.getTimeInstance(DateFormat.SHORT, LocalLocale.current.platformLocale)
            stringResource(R.string.stremio_last_sync, format.format(Date(state.lastSyncMillis)))
        }
        else -> stringResource(R.string.stremio_connected)
    }
    is State.Failed -> stringResource(
        when (state.reason) {
            Reason.UNREACHABLE -> R.string.stremio_unreachable
            Reason.EXPIRED -> R.string.stremio_expired
            Reason.STORAGE -> R.string.stremio_storage
            Reason.SESSION_ENDED -> R.string.stremio_session_ended
        },
    )
}

/** Big enough to scan from the sofa. */
private val QR_SIZE = 220.dp
