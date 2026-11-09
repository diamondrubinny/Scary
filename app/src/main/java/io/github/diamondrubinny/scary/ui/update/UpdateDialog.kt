// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary.ui.update

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.focusable
import androidx.compose.ui.window.DialogProperties
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import io.github.diamondrubinny.scary.R
import io.github.diamondrubinny.scary.core.model.Language
import io.github.diamondrubinny.scary.ui.components.ScaryButton
import io.github.diamondrubinny.scary.ui.theme.ScaryColors
import io.github.diamondrubinny.scary.ui.theme.ScaryType
import io.github.diamondrubinny.scary.ui.theme.dialogWidth
import io.github.diamondrubinny.scary.update.UpdateState

/** Test tag of the update popup. */
const val UPDATE_DIALOG_TAG = "updateDialog"

class UpdateActions(
    val onUpdate: () -> Unit,
    val onNotNow: () -> Unit,
    val onOpenPermission: () -> Unit,
    val onDismiss: () -> Unit,
)

/**
 * "A new version of SCARY", which is mandatory: what is new and "Update now" only, while the download (started by
 * itself) shows its progress, read out; then the one-time permission to install and Android's own install screen.
 * Back does not close it. Only when something blocks the update (a failed download or check, no permission) are
 * "Try again" and "Not now" offered: SCARY is the TV's home screen, and a popup that could never close would lock it.
 */
@Composable
fun UpdateDialog(state: UpdateState, language: Language, actions: UpdateActions) {
    val firstFocus = remember { FocusRequester() }
    // Back and a tap outside close it only where "Not now" is offered (or once Android's screen has taken over).
    val canPutOff = state is UpdateState.Failed || state is UpdateState.NeedsPermission
    val closable = canPutOff || state is UpdateState.Installing
    Dialog(
        onDismissRequest = when {
            canPutOff -> actions.onNotNow
            closable -> actions.onDismiss
            else -> ({})
        },
        properties = DialogProperties(dismissOnBackPress = closable, dismissOnClickOutside = closable),
    ) {
        LaunchedEffect(state::class) {
            withFrameNanos { }
            runCatching { firstFocus.requestFocus() }
        }
        Column(
            modifier = Modifier
                .dialogWidth(620.dp)
                .background(ScaryColors.Crypt, RoundedCornerShape(12.dp))
                .border(1.dp, ScaryColors.Candle.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                .verticalScroll(rememberScrollState())
                .padding(24.dp)
                .testTag(UPDATE_DIALOG_TAG),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            BasicText(stringResource(R.string.update_title), style = ScaryType.Title, modifier = Modifier.semantics { heading() })
            when (state) {
                is UpdateState.Available -> {
                    BasicText(
                        stringResource(R.string.update_version, state.manifest.versionName, state.manifest.versionCode),
                        style = ScaryType.BodyStrong.copy(color = ScaryColors.Candle),
                    )
                    val notes = state.manifest.notesFor(language.code)
                    if (notes.isNotEmpty()) BasicText(notes, style = ScaryType.Body)
                    BasicText(stringResource(R.string.update_how), style = ScaryType.Caption)
                    BasicText(stringResource(R.string.update_required), style = ScaryType.Caption.copy(color = ScaryColors.Candle))
                    ScaryButton(
                        stringResource(R.string.update_now),
                        onClick = actions.onUpdate,
                        primary = true,
                        focusRequester = firstFocus,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                is UpdateState.Downloading -> {
                    val label = stringResource(R.string.update_downloading, state.percent)
                    BasicText(
                        label,
                        style = ScaryType.Body,
                        modifier = Modifier.semantics {
                            liveRegion = LiveRegionMode.Polite
                            progressBarRangeInfo = ProgressBarRangeInfo(state.percent / 100f, 0f..1f)
                        },
                    )
                    ProgressBar(state.percent)
                    // Nothing to press: the focus stays in the popup, so the remote does not reach the screen behind it.
                    Box(Modifier.size(1.dp).focusRequester(firstFocus).focusable())
                }
                is UpdateState.NeedsPermission -> {
                    BasicText(stringResource(R.string.update_permission), style = ScaryType.Body)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ScaryButton(stringResource(R.string.update_open_settings), onClick = actions.onOpenPermission, primary = true, focusRequester = firstFocus)
                        ScaryButton(stringResource(R.string.update_not_now), onClick = actions.onNotNow)
                    }
                }
                is UpdateState.Installing -> {
                    BasicText(
                        stringResource(R.string.update_installing),
                        style = ScaryType.Body,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                    ScaryButton(stringResource(R.string.update_close), onClick = actions.onDismiss, focusRequester = firstFocus)
                }
                is UpdateState.Failed -> {
                    BasicText(
                        stringResource(
                            when (state.reason) {
                                UpdateState.Reason.UNREACHABLE -> R.string.update_failed_unreachable
                                UpdateState.Reason.NOT_VERIFIED -> R.string.update_failed_verify
                                UpdateState.Reason.NOT_OFFICIAL -> R.string.update_failed_not_official
                                UpdateState.Reason.INSTALL_FAILED -> R.string.update_failed_install
                                UpdateState.Reason.NO_PERMISSION_SCREEN -> R.string.update_failed_permission_screen
                            },
                        ),
                        style = ScaryType.Body,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (state.manifest != null) {
                            ScaryButton(stringResource(R.string.update_try_again), onClick = actions.onUpdate, primary = true, focusRequester = firstFocus)
                            ScaryButton(stringResource(R.string.update_not_now), onClick = actions.onNotNow)
                        } else {
                            ScaryButton(stringResource(R.string.update_not_now), onClick = actions.onNotNow, focusRequester = firstFocus)
                        }
                    }
                }
                else -> ScaryButton(stringResource(R.string.update_close), onClick = actions.onDismiss, focusRequester = firstFocus)
            }
        }
    }
}

@Composable
private fun ProgressBar(percent: Int) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(8.dp)
            .background(ScaryColors.Void, RoundedCornerShape(4.dp)),
    ) {
        Box(
            Modifier
                .fillMaxWidth(percent.coerceIn(0, 100) / 100f)
                .fillMaxHeight()
                .background(ScaryColors.Blood, RoundedCornerShape(4.dp)),
        )
    }
}
