// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary.ui.taste

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.diamondrubinny.scary.R
import io.github.diamondrubinny.scary.core.catalog.Personalize
import io.github.diamondrubinny.scary.core.model.MediaKind
import io.github.diamondrubinny.scary.core.model.Movie
import io.github.diamondrubinny.scary.ui.components.LocalLovedMark
import io.github.diamondrubinny.scary.ui.components.PosterCard
import io.github.diamondrubinny.scary.ui.components.ScaryButton
import io.github.diamondrubinny.scary.ui.home.MoodChip
import io.github.diamondrubinny.scary.ui.home.SidePadding
import io.github.diamondrubinny.scary.ui.theme.ScaryColors
import io.github.diamondrubinny.scary.ui.theme.ScaryType

/** Test tags of the taste picker's screen and its films or series tabs. */
const val TASTE_PICKER_TAG = "tastePicker"
fun tasteTabTag(kind: MediaKind): String = "tasteTab:${kind.name}"
fun tasteCardTag(movieId: String): String = "taste:$movieId"

/**
 * "What do you love?": well-known films and series as posters, films and series on tabs. OK marks one as loved (a heart
 * on the poster, read out with it) or takes the mark back; the count above says how many are in and is read out as it
 * changes. A pick is an ordinary "Loved it", so it sorts the rows (Personalize) and, when signed in, reaches TMDB.
 * [candidates] gives each tab's posters, best known first; [isLoved] reads the current marks.
 */
@Composable
fun TastePickerScreen(
    candidates: (MediaKind) -> List<Movie>,
    isLoved: (Movie) -> Boolean,
    lovedCount: Int,
    onToggle: (Movie) -> Unit,
    onDone: () -> Unit,
) {
    var kind by rememberSaveable { mutableStateOf(MediaKind.MOVIE) }
    val movies = remember(kind, candidates) { candidates(kind) }
    val doneFocus = remember { FocusRequester() }
    val gridState = rememberLazyGridState()
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { doneFocus.requestFocus() }
    }
    LaunchedEffect(kind) { gridState.scrollToItem(0) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 28.dp)
            .testTag(TASTE_PICKER_TAG),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = SidePadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                BasicText(
                    stringResource(R.string.taste_title),
                    style = ScaryType.Display.copy(fontSize = ScaryType.Display.fontSize * 0.6f),
                    modifier = Modifier.semantics { heading() },
                )
                BasicText(stringResource(R.string.taste_explainer), style = ScaryType.Body)
                BasicText(
                    text = if (lovedCount < Personalize.MIN_HISTORY) {
                        pluralStringResource(R.plurals.taste_count_more, lovedCount, lovedCount, Personalize.MIN_HISTORY)
                    } else {
                        pluralStringResource(R.plurals.taste_count, lovedCount, lovedCount)
                    },
                    style = ScaryType.BodyStrong.copy(color = ScaryColors.Candle),
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
            ScaryButton(stringResource(R.string.taste_done), onClick = onDone, primary = true, focusRequester = doneFocus)
        }
        Row(
            modifier = Modifier.padding(horizontal = SidePadding, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MediaKind.entries.forEach { tab ->
                MoodChip(
                    label = stringResource(if (tab == MediaKind.MOVIE) R.string.tab_movies else R.string.tab_series),
                    selected = tab == kind,
                    onClick = { kind = tab },
                    onFocused = {},
                    focusRequester = null,
                    modifier = Modifier.testTag(tasteTabTag(tab)),
                )
            }
        }
        CompositionLocalProvider(LocalLovedMark provides isLoved) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 104.dp),
                state = gridState,
                contentPadding = PaddingValues(horizontal = SidePadding, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
                modifier = Modifier.weight(1f),
            ) {
                items(movies, key = { it.id }) { movie ->
                    PosterCard(
                        movie = movie,
                        onClick = { onToggle(movie) },
                        onFocused = {},
                        modifier = Modifier.testTag(tasteCardTag(movie.id)),
                    )
                }
            }
        }
    }
}
