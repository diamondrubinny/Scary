// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary.ui.fx

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.LongState
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameMillis
import kotlinx.coroutines.delay

/**
 * The clock of the screen's slow background effects (fog, film grain, the art's drift): milliseconds that advance about
 * [AMBIENT_STEPS_PER_SECOND] times a second while [running], and stand still otherwise.
 *
 * Why one slow clock: each of its steps redraws the whole screen. The effects used to run on the display's own
 * clock (the drift 60 times a second, the fog 30), so the TV drew the full scene all the time, even with nothing
 * happening, and had that much less left for moving through the rows. They move a few pixels a second; twelve steps
 * are enough, and the screen is drawn a fifth as often.
 *
 * Read it in the draw phase only (inside a draw or graphicsLayer block), so a step never recomposes anything.
 */
@Composable
fun rememberAmbientTime(running: Boolean): LongState {
    val time = remember { mutableLongStateOf(0L) }
    LaunchedEffect(running) {
        if (!running) return@LaunchedEffect
        var last = withFrameMillis { it }
        while (true) {
            delay(AMBIENT_STEP_MS)
            val now = withFrameMillis { it }
            // After a pause (the screen was covered, the TV slept) the effects go on from where they were.
            time.longValue += (now - last).coerceIn(0L, MAX_STEP_MS)
            last = now
        }
    }
    return time
}

const val AMBIENT_STEPS_PER_SECOND = 12
private const val AMBIENT_STEP_MS = 1000L / AMBIENT_STEPS_PER_SECOND
private const val MAX_STEP_MS = 250L
