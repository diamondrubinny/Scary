// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf

/** Whether the remote's search or microphone key asked for dictation, and how the field that starts it says so. */
class VoiceRequest(val pending: Boolean, val onHandled: () -> Unit)

/** None outside SCARY's screens (previews, tests of a single screen). */
val LocalVoiceRequest = staticCompositionLocalOf<VoiceRequest?> { null }

/**
 * Starts dictation in the text field on screen when the remote's search or microphone key asked for it. The request
 * is answered either way, so it never fires again later; without a speech recognizer on the TV the field just stays
 * ready for typing.
 */
@Composable
fun VoiceRequestEffect(available: Boolean, start: () -> Unit) {
    val request = LocalVoiceRequest.current ?: return
    val latestStart by rememberUpdatedState(start)
    LaunchedEffect(request.pending) {
        if (!request.pending) return@LaunchedEffect
        request.onHandled()
        if (available) runCatching { latestStart() }
    }
}
