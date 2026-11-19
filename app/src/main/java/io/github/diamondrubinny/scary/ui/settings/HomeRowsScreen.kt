// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicText
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.diamondrubinny.scary.R
import io.github.diamondrubinny.scary.core.model.Language
import io.github.diamondrubinny.scary.ui.home.RowChoice
import io.github.diamondrubinny.scary.ui.home.SidePadding
import io.github.diamondrubinny.scary.ui.theme.ScaryType
import io.github.diamondrubinny.scary.ui.theme.paneWidth
import io.github.diamondrubinny.scary.ui.theme.scaryLayout

/** Test tags of Settings → Home screen rows. */
const val HOME_ROWS_SCREEN_TAG = "homeRowsScreen"
const val HOME_ROWS_SHOW_ALL_TAG = "homeRowsShowAll"
fun homeRowChoiceTag(key: String) = "homeRowChoice:$key"

/**
 * Settings → Home screen rows: every row of the home screen with "Shown" or "Hidden"; a press on a row turns it the
 * other way. "My apps" is not here (it always shows). A row turned off is not built at all, so the home screen is
 * shorter and its other rows get the films that row would have taken.
 */
@Composable
fun HomeRowsScreen(
    choices: List<RowChoice>,
    /** How many rows are turned off, rows the catalog no longer holds included, so "Show all" clears those too. */
    hiddenCount: Int,
    language: Language,
    onToggle: (String) -> Unit,
    onShowAll: () -> Unit,
) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { first.requestFocus() }
    }
    // "Show all the rows" leaves the screen once nothing is hidden: the focus it held goes to the first row, so the
    // remote is never left pointing at nothing.
    var hadHidden by remember { mutableStateOf(hiddenCount > 0) }
    LaunchedEffect(hiddenCount) {
        if (hadHidden && hiddenCount == 0) {
            withFrameNanos { }
            runCatching { first.requestFocus() }
        }
        hadHidden = hiddenCount > 0
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize().paneWidth(scaryLayout, 0.7f).testTag(HOME_ROWS_SCREEN_TAG),
        contentPadding = PaddingValues(horizontal = SidePadding, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "title") {
            BasicText(
                stringResource(R.string.settings_home_rows),
                style = ScaryType.Display.copy(fontSize = ScaryType.Display.fontSize * 0.7f),
                modifier = Modifier.semantics { heading() },
            )
        }
        item(key = "note") { BasicText(stringResource(R.string.home_rows_note), style = ScaryType.Caption) }
        if (hiddenCount > 0) {
            item(key = "all") {
                SettingRow(
                    label = stringResource(R.string.home_rows_show_all),
                    value = stringResource(R.string.home_rows_hidden_count, hiddenCount),
                    onClick = onShowAll,
                    modifier = Modifier.testTag(HOME_ROWS_SHOW_ALL_TAG),
                )
            }
        }
        items(choices, key = { it.key }) { choice ->
            SettingRow(
                label = choice.title?.let { stringResource(it) } ?: choice.collection?.title(language).orEmpty(),
                value = stringResource(if (choice.shown) R.string.value_row_shown else R.string.value_row_hidden),
                onClick = { onToggle(choice.key) },
                modifier = (if (choice.key == choices.firstOrNull()?.key) Modifier.focusRequester(first) else Modifier).testTag(homeRowChoiceTag(choice.key)),
            )
        }
    }
}
