// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary.ui.settings

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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
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
import io.github.diamondrubinny.scary.data.TmdbAccountRepository.Reason
import io.github.diamondrubinny.scary.data.TmdbAccountRepository.State
import io.github.diamondrubinny.scary.ui.components.QrImage
import io.github.diamondrubinny.scary.ui.components.ScaryButton
import io.github.diamondrubinny.scary.ui.home.SidePadding
import io.github.diamondrubinny.scary.ui.theme.ScaryColors
import io.github.diamondrubinny.scary.ui.theme.ScaryType
import java.text.DateFormat
import java.util.Date

/** Test tags of the TMDB account's screen and its QR code. */
const val TMDB_ACCOUNT_TAG = "tmdbAccount"
const val TMDB_ACCOUNT_QR_TAG = "tmdbAccountQr"

class TmdbAccountActions(
    val onSignIn: () -> Unit,
    val onCancel: () -> Unit,
    val onSignOut: () -> Unit,
    val onSyncNow: () -> Unit,
    val onAddKey: () -> Unit,
)

/**
 * The viewer's TMDB account: what signing in does, a QR code to TMDB's approval page while the TV waits, and once
 * signed in, the account's name, the last sync, "Sync now" and "Sign out".
 */
@Composable
fun TmdbAccountScreen(state: State, actions: TmdbAccountActions) {
    val firstFocus = remember { FocusRequester() }
    // Leaving the screen while waiting stops asking TMDB: approving later on the phone would not reach the TV anyway.
    val waiting by rememberUpdatedState(state is State.Waiting)
    val cancel by rememberUpdatedState(actions.onCancel)
    DisposableEffect(Unit) {
        onDispose { if (waiting) cancel() }
    }
    val stateKind = state::class
    LaunchedEffect(stateKind) {
        withFrameNanos { }
        runCatching { firstFocus.requestFocus() }
    }
    Row(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = SidePadding, vertical = 32.dp)
            .testTag(TMDB_ACCOUNT_TAG),
        horizontalArrangement = Arrangement.spacedBy(40.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            BasicText(
                stringResource(R.string.tmdb_account_title),
                style = ScaryType.Display.copy(fontSize = ScaryType.Display.fontSize * 0.7f),
                modifier = Modifier.semantics { heading() },
            )
            BasicText(stringResource(R.string.tmdb_account_what), style = ScaryType.Body)
            BasicText(
                text = statusText(state),
                style = ScaryType.BodyStrong.copy(color = ScaryColors.Candle),
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 4.dp)) {
                when (state) {
                    State.NoKey -> ScaryButton(stringResource(R.string.tmdb_account_add_key), onClick = actions.onAddKey, primary = true, focusRequester = firstFocus)
                    State.SignedOut, is State.Failed ->
                        ScaryButton(stringResource(R.string.tmdb_account_sign_in), onClick = actions.onSignIn, primary = true, focusRequester = firstFocus)
                    is State.Waiting -> ScaryButton(stringResource(R.string.tmdb_account_cancel), onClick = actions.onCancel, focusRequester = firstFocus)
                    is State.SignedIn -> {
                        ScaryButton(stringResource(R.string.tmdb_account_sync_now), onClick = actions.onSyncNow, primary = true, focusRequester = firstFocus)
                        ScaryButton(stringResource(R.string.tmdb_account_sign_out), onClick = actions.onSignOut)
                    }
                }
            }
            if (state is State.Waiting) {
                BasicText(stringResource(R.string.tmdb_account_step_scan), style = ScaryType.Body)
                BasicText(stringResource(R.string.tmdb_account_step_approve), style = ScaryType.Body)
            }
            BasicText(stringResource(R.string.tmdb_account_lists), style = ScaryType.Caption)
            BasicText(stringResource(R.string.tmdb_account_privacy), style = ScaryType.Caption)
        }
        if (state is State.Waiting) {
            val code = remember(state.approveUrl) { Donation.qr(state.approveUrl) }
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(QR_SIZE)) {
                QrImage(
                    code = code,
                    size = QR_SIZE,
                    description = stringResource(R.string.tmdb_account_qr_description),
                    modifier = Modifier.testTag(TMDB_ACCOUNT_QR_TAG),
                )
            }
        }
    }
}

@Composable
private fun statusText(state: State): String = when (state) {
    State.NoKey -> stringResource(R.string.tmdb_account_no_key)
    State.SignedOut -> stringResource(R.string.tmdb_account_signed_out)
    is State.Waiting -> stringResource(R.string.tmdb_account_waiting)
    is State.SignedIn -> when {
        state.syncing -> stringResource(R.string.tmdb_account_syncing, state.username)
        state.lastSyncMillis == null -> stringResource(R.string.tmdb_account_signed_in, state.username)
        else -> {
            val time = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(state.lastSyncMillis))
            val result = state.lastResult
            if (result != null && !result.complete) {
                stringResource(R.string.tmdb_account_synced_partly, state.username, time)
            } else {
                stringResource(R.string.tmdb_account_synced, state.username, time)
            }
        }
    }
    is State.Failed -> stringResource(
        when (state.reason) {
            Reason.UNREACHABLE -> R.string.tmdb_account_unreachable
            Reason.EXPIRED -> R.string.tmdb_account_expired
            Reason.NO_ACCOUNT -> R.string.tmdb_account_no_account
            Reason.STORAGE -> R.string.tmdb_storage_failed
            Reason.SESSION_ENDED -> R.string.tmdb_account_session_ended
        },
    )
}

/** Big enough to scan from the sofa. */
private val QR_SIZE = 240.dp
