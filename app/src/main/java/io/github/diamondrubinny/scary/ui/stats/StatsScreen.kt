// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.diamondrubinny.scary.R
import io.github.diamondrubinny.scary.core.catalog.FearStats
import io.github.diamondrubinny.scary.core.model.Language
import io.github.diamondrubinny.scary.ui.components.EmptyState
import io.github.diamondrubinny.scary.ui.components.FearMeter
import io.github.diamondrubinny.scary.ui.home.SidePadding
import io.github.diamondrubinny.scary.ui.home.hoursAndMinutes
import io.github.diamondrubinny.scary.ui.home.labelRes
import io.github.diamondrubinny.scary.ui.theme.ScaryColors
import io.github.diamondrubinny.scary.ui.theme.ScaryType
import io.github.diamondrubinny.scary.ui.theme.mixedLanguage
import java.time.LocalDate
import kotlin.math.roundToInt

/** Test tag of the "Your year in fear" screen. */
const val STATS_SCREEN_TAG = "statsScreen"

/**
 * "Your year in fear": this calendar year, then all time, from the watched list on this TV. Plain text, read in full by
 * TalkBack; each card takes the focus so the remote scrolls through them.
 */
@Composable
fun StatsScreen(language: Language, load: suspend () -> Pair<FearStats, FearStats>) {
    val stats by produceState<Pair<FearStats, FearStats>?>(null) { value = load() }
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(stats != null) {
        withFrameNanos { }
        runCatching { firstFocus.requestFocus() }
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = SidePadding, vertical = 28.dp)
            .fillMaxWidth(0.7f)
            .testTag(STATS_SCREEN_TAG),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        BasicText(
            stringResource(R.string.settings_stats),
            style = ScaryType.Display.copy(fontSize = ScaryType.Display.fontSize * 0.7f),
            modifier = Modifier.semantics { heading() },
        )
        val (year, all) = stats ?: return@Column
        if (all.isEmpty) {
            EmptyState(
                title = stringResource(R.string.stats_empty_title),
                body = stringResource(R.string.stats_empty_body),
                modifier = Modifier.focusRequester(firstFocus).focusable(),
            )
            return@Column
        }
        StatsCard(stringResource(R.string.stats_this_year, LocalDate.now().year), year, language, Modifier.focusRequester(firstFocus))
        StatsCard(stringResource(R.string.stats_all_time), all, language)
        BasicText(stringResource(R.string.stats_note), style = ScaryType.Caption)
    }
}

@Composable
private fun StatsCard(title: String, stats: FearStats, language: Language, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(10.dp)
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (focused) ScaryColors.Crypt else ScaryColors.Crypt.copy(alpha = 0.6f))
            .border(2.dp, if (focused) ScaryColors.Candle.copy(alpha = 0.6f) else Color.Transparent, shape)
            .focusable(interactionSource = interaction)
            .semantics(mergeDescendants = true) { }
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        BasicText(title, style = ScaryType.RowTitle, modifier = Modifier.semantics { heading() })
        if (stats.watchedCount == 0) {
            BasicText(stringResource(R.string.stats_none_yet), style = ScaryType.Body)
            return@Column
        }
        Line(stringResource(R.string.stats_watched), stats.watchedCount.toString())
        Line(stringResource(R.string.stats_hours), hoursAndMinutes(stats.totalMinutes))
        stats.averageFear?.let { average ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Line(stringResource(R.string.stats_average_fear), "%.1f / 5".format(java.util.Locale.ROOT, average))
                FearMeter(average.roundToInt())
            }
        }
        stats.scariest?.let { Line(stringResource(R.string.stats_scariest), "${it.displayTitle(language)} (${it.year})") }
        stats.favoriteSubgenre?.let { Line(stringResource(R.string.stats_favorite_subgenre), stringResource(it.labelRes())) }
        stats.favoriteDecade?.let { Line(stringResource(R.string.stats_favorite_decade), stringResource(R.string.decade_label, it)) }
        Line(stringResource(R.string.stats_loved), stats.lovedCount.toString())
    }
}

@Composable
private fun Line(label: String, value: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        BasicText("$label:", style = ScaryType.Body.copy(color = ScaryColors.Ash))
        BasicText(value, style = ScaryType.BodyStrong.mixedLanguage())
    }
}
