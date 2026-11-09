// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import io.github.diamondrubinny.scary.R
import io.github.diamondrubinny.scary.core.model.Language
import io.github.diamondrubinny.scary.ui.components.FocusSurface
import io.github.diamondrubinny.scary.ui.theme.ScaryColors
import io.github.diamondrubinny.scary.ui.theme.ScaryType

/** Test tags of the language list and each of its languages. */
const val LANGUAGE_DIALOG_TAG = "languageDialog"
fun languageTag(language: Language): String = "language:${language.code}"

/**
 * The languages, each named in itself ("עברית", "English"…) so anyone can find their own, with the current one
 * marked and focused. Picking one switches the whole app.
 */
@Composable
fun LanguageDialog(current: Language, onPick: (Language) -> Unit, onDismiss: () -> Unit) {
    val currentFocus = remember { FocusRequester() }
    Dialog(onDismissRequest = onDismiss) {
        LaunchedEffect(Unit) {
            withFrameNanos { }
            runCatching { currentFocus.requestFocus() }
        }
        Column(
            modifier = Modifier
                .widthIn(max = 460.dp)
                .fillMaxWidth()
                .background(ScaryColors.Crypt, RoundedCornerShape(12.dp))
                .border(1.dp, ScaryColors.Candle.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                .verticalScroll(rememberScrollState())
                .padding(24.dp)
                .testTag(LANGUAGE_DIALOG_TAG),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            BasicText(stringResource(R.string.settings_language), style = ScaryType.Title, modifier = Modifier.semantics { heading() })
            Language.entries.forEach { language ->
                val chosen = language == current
                FocusSurface(
                    onClick = { onPick(language) },
                    modifier = (if (chosen) Modifier.focusRequester(currentFocus) else Modifier)
                        .fillMaxWidth()
                        .semantics { selected = chosen }
                        .testTag(languageTag(language)),
                    shape = RoundedCornerShape(8.dp),
                    focusedScale = 1.02f,
                    containerColor = if (chosen) ScaryColors.Blood.copy(alpha = 0.5f) else ScaryColors.Void,
                    focusedContainerColor = ScaryColors.Void,
                    borderColor = if (chosen) ScaryColors.Candle else ScaryColors.Bone.copy(alpha = 0.1f),
                    contentDescription = language.nativeName,
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        BasicText(
                            language.nativeName,
                            // Each name in its own direction and with its own language's shaping.
                            style = ScaryType.BodyStrong.copy(
                                textDirection = if (language.rtl) TextDirection.Rtl else TextDirection.Ltr,
                                localeList = LocaleList(language.code),
                            ),
                            modifier = Modifier.weight(1f),
                        )
                        if (chosen) BasicText("✓", style = ScaryType.BodyStrong.copy(color = ScaryColors.Candle))
                    }
                }
            }
        }
    }
}
