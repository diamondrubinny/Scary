// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary.ui.realdebrid

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.diamondrubinny.scary.R
import io.github.diamondrubinny.scary.core.brand.Donation
import io.github.diamondrubinny.scary.core.realdebrid.RealDebrid
import io.github.diamondrubinny.scary.data.RealDebridRepository.Reason
import io.github.diamondrubinny.scary.data.RealDebridRepository.State
import io.github.diamondrubinny.scary.ui.components.FocusSurface
import io.github.diamondrubinny.scary.ui.components.QrImage
import io.github.diamondrubinny.scary.ui.components.ScaryButton
import io.github.diamondrubinny.scary.ui.home.RdFilesState
import io.github.diamondrubinny.scary.ui.home.SidePadding
import io.github.diamondrubinny.scary.ui.theme.ScaryColors
import io.github.diamondrubinny.scary.ui.theme.ScaryType
import io.github.diamondrubinny.scary.ui.theme.scaryLayout
import java.util.Locale

/** Test tags of the Real-Debrid screen. */
const val REAL_DEBRID_TAG = "realDebrid"
const val REAL_DEBRID_CODE_TAG = "realDebridCode"
const val REAL_DEBRID_FILES_TAG = "realDebridFiles"

class RealDebridActions(
    val onSignIn: () -> Unit,
    val onCancel: () -> Unit,
    val onSignOut: () -> Unit,
    val onRefresh: () -> Unit,
    val onPlay: (RealDebrid.RdFile) -> Unit,
)

/**
 * The viewer's Real-Debrid account: what connecting does (only their own files are played; SCARY looks for nothing
 * else), the code to enter at real-debrid.com/device while the TV waits, and once connected, the account and its
 * videos. Choosing one plays it in the internal player.
 */
@Composable
fun RealDebridScreen(state: State, files: RdFilesState, actions: RealDebridActions) {
    val firstFocus = remember { FocusRequester() }
    // Leaving while waiting stops asking Real-Debrid: approving later would not reach the TV anyway.
    val waiting by rememberUpdatedState(state is State.Waiting)
    val cancel by rememberUpdatedState(actions.onCancel)
    DisposableEffect(Unit) {
        onDispose { if (waiting) cancel() }
    }
    LaunchedEffect(state::class) {
        withFrameNanos { }
        runCatching { firstFocus.requestFocus() }
    }
    val narrow = scaryLayout.narrow
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = SidePadding, vertical = 28.dp)
            .testTag(REAL_DEBRID_TAG),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        BasicText(
            stringResource(R.string.rd_title),
            style = ScaryType.Display.copy(fontSize = ScaryType.Display.fontSize * 0.7f),
            modifier = Modifier.semantics { heading() },
        )
        BasicText(stringResource(R.string.rd_what), style = ScaryType.Body)
        BasicText(
            text = statusText(state),
            style = ScaryType.BodyStrong.copy(color = ScaryColors.Candle),
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            when (state) {
                State.SignedOut, is State.Failed ->
                    ScaryButton(stringResource(R.string.rd_sign_in), onClick = actions.onSignIn, primary = true, focusRequester = firstFocus)
                is State.Waiting -> ScaryButton(stringResource(R.string.rd_cancel), onClick = actions.onCancel, focusRequester = firstFocus)
                is State.SignedIn -> {
                    ScaryButton(stringResource(R.string.rd_refresh), onClick = actions.onRefresh, focusRequester = firstFocus)
                    ScaryButton(stringResource(R.string.rd_sign_out), onClick = actions.onSignOut)
                }
            }
        }
        if (state is State.Waiting) {
            WaitingForCode(state, narrow)
        }
        if (state is State.SignedIn) {
            BasicText(stringResource(R.string.rd_files_title), style = ScaryType.RowTitle, modifier = Modifier.semantics { heading() })
            when (files) {
                RdFilesState.Idle, RdFilesState.Loading -> BasicText(stringResource(R.string.rd_files_loading), style = ScaryType.Caption)
                RdFilesState.Unreachable -> BasicText(stringResource(R.string.rd_unreachable), style = ScaryType.Caption)
                is RdFilesState.Ready -> if (files.files.isEmpty()) {
                    BasicText(stringResource(R.string.rd_files_empty), style = ScaryType.Caption)
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().weight(1f).testTag(REAL_DEBRID_FILES_TAG),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(files.files, key = { it.key }) { file -> FileRow(file) { actions.onPlay(file) } }
                    }
                }
            }
        }
        BasicText(stringResource(R.string.rd_privacy), style = ScaryType.Caption)
    }
}

@Composable
private fun WaitingForCode(state: State.Waiting, narrow: Boolean) {
    val qr = remember(state.verificationUrl) { Donation.qr(state.verificationUrl) }
    val steps: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            BasicText(stringResource(R.string.rd_step_open, state.verificationUrl.removePrefix("https://")), style = ScaryType.Body.copy(textDirection = TextDirection.Content))
            BasicText(stringResource(R.string.rd_step_code), style = ScaryType.Body)
            // The code is Latin letters and digits: shown big, left to right, and read out letter by letter.
            BasicText(
                state.userCode,
                style = ScaryType.Display.copy(color = ScaryColors.Candle, textDirection = TextDirection.Ltr),
                modifier = Modifier.testTag(REAL_DEBRID_CODE_TAG).semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
    }
    if (narrow) {
        steps()
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(32.dp), verticalAlignment = Alignment.CenterVertically) {
            QrImage(code = qr, size = QR_SIZE, description = stringResource(R.string.rd_qr_description))
            steps()
        }
    }
}

@Composable
private fun FileRow(file: RealDebrid.RdFile, onClick: () -> Unit) {
    FocusSurface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        focusedScale = 1.02f,
        containerColor = ScaryColors.Crypt,
        focusedContainerColor = ScaryColors.Bone,
        contentDescription = stringResource(R.string.rd_play_file, file.name),
    ) { focused ->
        Row(Modifier.padding(horizontal = 18.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            BasicText(
                file.name,
                style = ScaryType.Body.copy(color = if (focused) ScaryColors.Void else ScaryColors.Bone, textDirection = TextDirection.Content),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            BasicText(
                size(file.sizeBytes),
                style = ScaryType.Caption.copy(color = if (focused) ScaryColors.Void else ScaryColors.Ash),
                modifier = Modifier.padding(start = 12.dp).width(80.dp),
            )
        }
    }
}

/** "4.2 GB", "700 MB"; empty when Real-Debrid did not say. */
fun size(bytes: Long): String = when {
    bytes <= 0 -> ""
    bytes >= 1L shl 30 -> String.format(Locale.ROOT, "%.1f GB", bytes / (1L shl 30).toDouble())
    else -> String.format(Locale.ROOT, "%d MB", bytes / (1L shl 20))
}

@Composable
private fun statusText(state: State): String = when (state) {
    State.SignedOut -> stringResource(R.string.rd_signed_out)
    is State.Waiting -> stringResource(R.string.rd_waiting)
    is State.SignedIn -> when {
        state.username.isBlank() -> stringResource(R.string.rd_connected)
        state.premium && state.expiration.isNotBlank() -> stringResource(R.string.rd_signed_in_premium, state.username, state.expiration.take(10))
        state.premium -> stringResource(R.string.rd_signed_in, state.username)
        else -> stringResource(R.string.rd_signed_in_free, state.username)
    }
    is State.Failed -> stringResource(
        when (state.reason) {
            Reason.UNREACHABLE -> R.string.rd_unreachable
            Reason.EXPIRED -> R.string.rd_expired
            Reason.STORAGE -> R.string.rd_storage
            Reason.SESSION_ENDED -> R.string.rd_session_ended
            Reason.BUSY -> R.string.rd_busy
        },
    )
}

/** Big enough to scan from the sofa. */
private val QR_SIZE = 220.dp
