// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary.ui.trailer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import io.github.diamondrubinny.scary.R
import io.github.diamondrubinny.scary.ui.components.ScaryButton
import io.github.diamondrubinny.scary.ui.theme.ScaryColors
import io.github.diamondrubinny.scary.ui.theme.ScaryType

/** Test tag of the first-time explanation of trailers that play by themselves. */
const val TRAILER_INTRO_TAG = "trailerIntro"

/**
 * Asked once, before the first trailer would play by itself: what it means (YouTube, Google's privacy policy, muted
 * unless chosen otherwise) and the choice to keep it or turn it off. Back answers neither; it is asked again later.
 */
@Composable
fun TrailerIntroDialog(onPlay: () -> Unit, onTurnOff: () -> Unit, onDismiss: () -> Unit) {
    val playFocus = remember { FocusRequester() }
    Dialog(onDismissRequest = onDismiss) {
        LaunchedEffect(Unit) {
            withFrameNanos { }
            runCatching { playFocus.requestFocus() }
        }
        Column(
            modifier = Modifier
                .width(560.dp)
                .background(ScaryColors.Crypt, RoundedCornerShape(12.dp))
                .border(1.dp, ScaryColors.Bone.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                .verticalScroll(rememberScrollState())
                .padding(24.dp)
                .testTag(TRAILER_INTRO_TAG),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            BasicText(stringResource(R.string.trailer_intro_title), style = ScaryType.Title, modifier = Modifier.semantics { heading() })
            BasicText(stringResource(R.string.trailer_intro_body), style = ScaryType.Body)
            BasicText(stringResource(R.string.trailer_intro_privacy), style = ScaryType.Caption)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 4.dp)) {
                ScaryButton(
                    text = stringResource(R.string.trailer_intro_play),
                    onClick = onPlay,
                    primary = true,
                    focusRequester = playFocus,
                )
                ScaryButton(stringResource(R.string.trailer_intro_off), onClick = onTurnOff)
            }
        }
    }
}
