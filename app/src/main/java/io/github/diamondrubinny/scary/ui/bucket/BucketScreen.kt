// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary.ui.bucket

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import coil3.compose.AsyncImage
import io.github.diamondrubinny.scary.R
import io.github.diamondrubinny.scary.core.model.Language
import io.github.diamondrubinny.scary.core.model.Movie
import io.github.diamondrubinny.scary.ui.components.EmptyState
import io.github.diamondrubinny.scary.ui.components.IconCircleButton
import io.github.diamondrubinny.scary.ui.components.PosterArt
import io.github.diamondrubinny.scary.ui.components.ScaryButton
import io.github.diamondrubinny.scary.ui.components.rememberArtwork
import io.github.diamondrubinny.scary.ui.home.BucketPrompt
import io.github.diamondrubinny.scary.ui.home.SidePadding
import io.github.diamondrubinny.scary.ui.home.hoursAndMinutes
import io.github.diamondrubinny.scary.ui.theme.ScaryColors
import io.github.diamondrubinny.scary.ui.theme.ScaryType
import io.github.diamondrubinny.scary.ui.theme.mixedLanguage

/** Test tags of the bucket list's screen, its rows and the question on coming back from a film. */
const val BUCKET_SCREEN_TAG = "bucketScreen"
const val BUCKET_PROMPT_TAG = "bucketPrompt"
fun bucketItemTag(movieId: String): String = "bucket:$movieId"

class BucketActions(
    val onStart: () -> Unit,
    val onPickMore: () -> Unit,
    val onClear: () -> Unit,
    val onMove: (id: String, by: Int) -> Unit,
    val onRemove: (id: String) -> Unit,
)

/**
 * The bucket list: the films picked to watch one after another, in order, with Start, Pick more, Clear, and each film's
 * up, down and remove. [movieOf] resolves an id as it may show now (null when family night or a lost online film hides
 * it); [isLocked] hides a locked film's poster, as everywhere else.
 */
@Composable
fun BucketScreen(
    ids: List<String>,
    movieOf: (String) -> Movie?,
    language: Language,
    isLocked: (Movie) -> Boolean,
    actions: BucketActions,
) {
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(ids.isEmpty()) {
        withFrameNanos { }
        runCatching { firstFocus.requestFocus() }
    }
    val films = ids.map { it to movieOf(it) }
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag(BUCKET_SCREEN_TAG),
        contentPadding = PaddingValues(horizontal = SidePadding, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "head") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth(0.75f)) {
                BasicText(
                    stringResource(R.string.bucket_title),
                    style = ScaryType.Display.copy(fontSize = ScaryType.Display.fontSize * 0.7f),
                    modifier = Modifier.semantics { heading() },
                )
                BasicText(stringResource(R.string.bucket_explainer), style = ScaryType.Body)
                BasicText(stringResource(R.string.bucket_limits), style = ScaryType.Caption)
            }
        }
        if (ids.isEmpty()) {
            item(key = "empty") {
                EmptyState(
                    title = stringResource(R.string.bucket_empty_title),
                    body = stringResource(R.string.bucket_empty_body),
                    actionLabel = stringResource(R.string.bucket_pick),
                    onAction = actions.onPickMore,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            return@LazyColumn
        }
        item(key = "actions") {
            var confirmClear by remember { mutableStateOf(false) }
            val total = films.sumOf { (_, movie) -> movie?.runtimeMin ?: 0 }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(vertical = 6.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ScaryButton(stringResource(R.string.bucket_start), onClick = actions.onStart, primary = true, focusRequester = firstFocus)
                    ScaryButton(stringResource(R.string.bucket_pick_more), onClick = actions.onPickMore)
                    // Clearing asks once more: the list may have taken a while to build.
                    ScaryButton(
                        text = stringResource(if (confirmClear) R.string.bucket_clear_confirm else R.string.bucket_clear),
                        onClick = {
                            if (confirmClear) actions.onClear() else confirmClear = true
                        },
                    )
                }
                BasicText(
                    stringResource(R.string.bucket_total, ids.size, hoursAndMinutes(total)),
                    style = ScaryType.BodyStrong.copy(color = ScaryColors.Candle),
                )
            }
        }
        itemsIndexed(films, key = { _, (id, _) -> id }) { index, (id, movie) ->
            BucketRow(
                number = index + 1,
                id = id,
                movie = movie,
                language = language,
                locked = movie?.let(isLocked) ?: false,
                first = index == 0,
                last = index == films.lastIndex,
                actions = actions,
            )
        }
    }
}

@Composable
private fun BucketRow(
    number: Int,
    id: String,
    movie: Movie?,
    language: Language,
    locked: Boolean,
    first: Boolean,
    last: Boolean,
    actions: BucketActions,
) {
    val title = movie?.displayTitle(language) ?: stringResource(R.string.bucket_unavailable)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(ScaryColors.Crypt.copy(alpha = 0.7f), RoundedCornerShape(10.dp))
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .testTag(bucketItemTag(id)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        BasicText(
            number.toString(),
            style = ScaryType.Title.copy(color = ScaryColors.Candle),
            modifier = Modifier.width(32.dp),
        )
        Box(
            Modifier
                .size(width = 48.dp, height = 72.dp)
                .clip(RoundedCornerShape(4.dp)),
        ) {
            if (movie != null) {
                PosterArt(movie, Modifier.fillMaxSize())
                // A locked film shows no poster here either.
                rememberArtwork(movie.posterUrl.takeUnless { locked })?.let {
                    AsyncImage(model = it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                }
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            BasicText(title, style = ScaryType.BodyStrong.mixedLanguage(), maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (movie != null) {
                val meta = listOfNotNull(
                    movie.year.toString(),
                    movie.runtimeMin.takeIf { it > 0 }?.let { stringResource(R.string.minutes, it) },
                ).joinToString(" · ")
                BasicText(meta, style = ScaryType.Caption)
            }
        }
        // Up and down only where the film can move; remove always.
        if (!first) {
            IconCircleButton(
                contentDescription = stringResource(R.string.bucket_move_up, title),
                onClick = { actions.onMove(id, -1) },
            ) { BasicText("↑", style = ScaryType.BodyStrong.copy(color = it)) }
        }
        if (!last) {
            IconCircleButton(
                contentDescription = stringResource(R.string.bucket_move_down, title),
                onClick = { actions.onMove(id, 1) },
            ) { BasicText("↓", style = ScaryType.BodyStrong.copy(color = it)) }
        }
        IconCircleButton(
            contentDescription = stringResource(R.string.bucket_remove, title),
            onClick = { actions.onRemove(id) },
        ) { BasicText("✕", style = ScaryType.BodyStrong.copy(color = it)) }
    }
}

/**
 * Asked on coming back to SCARY from a bucket-list film. Watched to the end: play the next one, or not now. Back early:
 * back to that film, on to the next, or stop. Back or "Not now" keeps the list as it is.
 */
@Composable
fun BucketPromptDialog(
    prompt: BucketPrompt,
    language: Language,
    onPlayNext: () -> Unit,
    onBackToFilm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val firstFocus = remember { FocusRequester() }
    val current = prompt.current.displayTitle(language)
    val next = prompt.next
    Dialog(onDismissRequest = onDismiss) {
        LaunchedEffect(Unit) {
            withFrameNanos { }
            runCatching { firstFocus.requestFocus() }
        }
        Column(
            modifier = Modifier
                .width(600.dp)
                .background(ScaryColors.Crypt, RoundedCornerShape(12.dp))
                .border(1.dp, ScaryColors.Candle.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                .verticalScroll(rememberScrollState())
                .padding(24.dp)
                .testTag(BUCKET_PROMPT_TAG),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            BasicText(
                if (prompt.finished) stringResource(R.string.bucket_prompt_finished, current) else stringResource(R.string.bucket_prompt_early, current),
                style = ScaryType.Title.mixedLanguage(),
                modifier = Modifier.semantics { heading() },
            )
            if (next != null) {
                val meta = listOfNotNull(
                    next.year.toString(),
                    next.runtimeMin.takeIf { it > 0 }?.let { stringResource(R.string.minutes, it) },
                ).joinToString(" · ")
                BasicText(stringResource(R.string.bucket_prompt_next, next.displayTitle(language), meta), style = ScaryType.Body.mixedLanguage())
            } else {
                BasicText(stringResource(R.string.bucket_prompt_last), style = ScaryType.Body)
            }
            BasicText(stringResource(R.string.bucket_prompt_note), style = ScaryType.Caption)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 4.dp)) {
                // Back early, the film left comes first: most likely it was only paused or a stream was being chosen.
                if (!prompt.finished) {
                    ScaryButton(
                        text = stringResource(R.string.bucket_prompt_back, current),
                        onClick = onBackToFilm,
                        primary = true,
                        focusRequester = firstFocus,
                    )
                }
                if (next != null) {
                    ScaryButton(
                        text = stringResource(R.string.bucket_prompt_play, next.displayTitle(language)),
                        onClick = onPlayNext,
                        primary = prompt.finished,
                        focusRequester = if (prompt.finished) firstFocus else null,
                    )
                }
                ScaryButton(
                    text = stringResource(if (prompt.finished) R.string.bucket_prompt_later else R.string.bucket_prompt_stop),
                    onClick = onDismiss,
                    focusRequester = if (prompt.finished && next == null) firstFocus else null,
                )
            }
        }
    }
}
