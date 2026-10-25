// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import io.github.diamondrubinny.scary.R
import io.github.diamondrubinny.scary.ui.components.FocusSurface
import io.github.diamondrubinny.scary.ui.components.tryRequestFocus
import io.github.diamondrubinny.scary.ui.theme.ScaryColors
import io.github.diamondrubinny.scary.ui.theme.ScaryType

/**
 * The country rent and buy offers are shown for. "Automatic" follows the TV (and on a phone, its network); any
 * country can be picked instead. The list opens on the current choice.
 */
@Composable
fun CountryPicker(
    countries: List<String>,
    /** The chosen country, or null when it is automatic. */
    current: String?,
    /** The device's own country's name, for the "Automatic" line. */
    detected: String?,
    countryName: (String) -> String,
    onPick: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    // Index 0 is "Automatic"; countries follow.
    val startIndex = current?.let { countries.indexOf(it) + 1 }?.takeIf { it > 0 } ?: 0
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = (startIndex - 2).coerceAtLeast(0))
    val startFocus = remember { FocusRequester() }

    Dialog(onDismissRequest = onDismiss) {
        LaunchedEffect(Unit) {
            withFrameNanos { }
            startFocus.tryRequestFocus()
        }
        Column(
            modifier = Modifier
                .width(520.dp)
                .background(ScaryColors.Crypt, RoundedCornerShape(12.dp))
                .border(1.dp, ScaryColors.Bone.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            BasicText(stringResource(R.string.country_dialog_title), style = ScaryType.Title, modifier = Modifier.semantics { heading() })
            BasicText(stringResource(R.string.settings_rent_country_note), style = ScaryType.Caption)
            LazyColumn(
                state = listState,
                modifier = Modifier.heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(4.dp),
            ) {
                item(key = "auto") {
                    Choice(
                        label = if (detected != null) stringResource(R.string.country_auto, detected) else stringResource(R.string.country_auto_unknown),
                        chosen = current == null,
                        onClick = { onPick(null) },
                        modifier = if (startIndex == 0) Modifier.focusRequester(startFocus) else Modifier,
                    )
                }
                items(countries, key = { it }) { code ->
                    Choice(
                        label = countryName(code),
                        chosen = code == current,
                        onClick = { onPick(code) },
                        modifier = if (code == current) Modifier.focusRequester(startFocus) else Modifier,
                    )
                }
            }
        }
    }
}

@Composable
private fun Choice(label: String, chosen: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    FocusSurface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().semantics { selected = chosen },
        shape = RoundedCornerShape(8.dp),
        focusedScale = 1.02f,
        containerColor = if (chosen) ScaryColors.Blood.copy(alpha = 0.35f) else ScaryColors.Void.copy(alpha = 0.5f),
        focusedContainerColor = ScaryColors.Bone,
        borderColor = if (chosen) ScaryColors.Blood else ScaryColors.Bone.copy(alpha = 0.1f),
        focusedBorderColor = ScaryColors.Bone,
        contentDescription = label,
    ) { focused ->
        BasicText(
            text = if (chosen) "✓ $label" else label,
            style = ScaryType.BodyStrong.copy(color = if (focused) ScaryColors.Void else ScaryColors.Bone),
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )
    }
}
