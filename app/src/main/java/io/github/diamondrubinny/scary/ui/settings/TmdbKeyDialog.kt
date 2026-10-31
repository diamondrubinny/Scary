// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import io.github.diamondrubinny.scary.R
import io.github.diamondrubinny.scary.data.TmdbKeyResult
import io.github.diamondrubinny.scary.ui.components.ScaryButton
import io.github.diamondrubinny.scary.ui.home.TmdbDialogState
import io.github.diamondrubinny.scary.ui.theme.ScaryColors
import io.github.diamondrubinny.scary.ui.theme.ScaryType

/** Entering the user's TMDB key. The key is checked against TMDB before it is stored. */
@Composable
fun TmdbKeyDialog(
    state: TmdbDialogState,
    keyMask: String?,
    onSubmit: (String) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
    /** Paste the key on the phone instead: opens the phone remote. */
    onUsePhone: (() -> Unit)? = null,
) {
    var input by remember { mutableStateOf("") }
    val fieldFocus = remember { FocusRequester() }
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val label = stringResource(R.string.tmdb_dialog_title)

    Dialog(onDismissRequest = onDismiss) {
        LaunchedEffect(Unit) {
            withFrameNanos { }
            runCatching { fieldFocus.requestFocus() }
        }
        Column(
            modifier = Modifier
                .width(520.dp)
                .background(ScaryColors.Crypt, RoundedCornerShape(12.dp))
                .border(1.dp, ScaryColors.Bone.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                // The TV keyboard can cover the lower half of the screen: keep everything reachable.
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            BasicText(label, style = ScaryType.Title, modifier = Modifier.semantics { heading() })
            BasicText(stringResource(R.string.tmdb_dialog_body), style = ScaryType.Body)
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(ScaryColors.Void, RoundedCornerShape(8.dp))
                    .border(2.dp, if (focused) ScaryColors.Candle else ScaryColors.Bone.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                if (input.isEmpty()) {
                    BasicText(
                        keyMask ?: stringResource(R.string.tmdb_hint),
                        style = ScaryType.Body.copy(color = ScaryColors.Ash),
                    )
                }
                BasicTextField(
                    value = input,
                    onValueChange = { input = it.take(MAX_INPUT) },
                    singleLine = true,
                    // Keys are Latin: keep them left to right even in the Hebrew layout.
                    textStyle = ScaryType.BodyStrong.copy(textDirection = TextDirection.Ltr),
                    cursorBrush = SolidColor(ScaryColors.Candle),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { if (input.isNotBlank()) onSubmit(input) }),
                    interactionSource = interaction,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(fieldFocus)
                        .semantics { contentDescription = label },
                )
            }
            val status = when {
                state.busy -> stringResource(R.string.tmdb_checking)
                else -> when (state.result) {
                    TmdbKeyResult.INVALID_FORMAT -> stringResource(R.string.tmdb_invalid_format)
                    TmdbKeyResult.REJECTED -> stringResource(R.string.tmdb_rejected)
                    TmdbKeyResult.UNREACHABLE -> stringResource(R.string.tmdb_unreachable)
                    TmdbKeyResult.STORAGE_FAILED -> stringResource(R.string.tmdb_storage_failed)
                    TmdbKeyResult.SAVED, null -> ""
                }
            }
            BasicText(
                status,
                style = ScaryType.BodyStrong.copy(color = ScaryColors.Candle),
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ScaryButton(
                    text = stringResource(R.string.tmdb_save),
                    onClick = { if (input.isNotBlank() && !state.busy) onSubmit(input) },
                    primary = true,
                )
                if (keyMask != null) ScaryButton(stringResource(R.string.tmdb_delete), onClick = onDelete)
                ScaryButton(stringResource(R.string.pin_cancel), onClick = onDismiss)
            }
            // A long key is easier to paste on a phone than to type with a remote.
            if (onUsePhone != null) {
                ScaryButton(stringResource(R.string.tmdb_use_phone), onClick = onUsePhone)
            }
            BasicText(stringResource(R.string.tmdb_note), style = ScaryType.Caption)
        }
    }
}

private const val MAX_INPUT = 1_000
