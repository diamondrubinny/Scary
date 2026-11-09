// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import coil3.compose.AsyncImage
import io.github.diamondrubinny.scary.R
import io.github.diamondrubinny.scary.core.model.Language
import io.github.diamondrubinny.scary.core.player.WatchEntry
import io.github.diamondrubinny.scary.ui.components.FocusSurface
import io.github.diamondrubinny.scary.ui.components.PosterArt
import io.github.diamondrubinny.scary.ui.components.ScaryButton
import io.github.diamondrubinny.scary.ui.components.rememberArtwork
import io.github.diamondrubinny.scary.ui.home.ContinueItem
import io.github.diamondrubinny.scary.ui.home.ResumeChoice
import io.github.diamondrubinny.scary.ui.theme.ScaryColors
import io.github.diamondrubinny.scary.ui.theme.ScaryType
import io.github.diamondrubinny.scary.ui.theme.dialogWidth
import io.github.diamondrubinny.scary.ui.theme.mixedLanguage
import java.util.Locale

/** Test tags. */
const val RESUME_DIALOG_TAG = "resumeDialog"
const val CONTINUE_CARD_TAG = "continueCard"

/** "1:42:07" or "42:07". */
fun clock(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = total % 3600 / 60
    val s = total % 60
    return if (h > 0) String.format(Locale.ROOT, "%d:%02d:%02d", h, m, s) else String.format(Locale.ROOT, "%d:%02d", m, s)
}

/** What a "Continue watching" entry says under its title: where it continues from, or how many spots are marked. */
@Composable
fun continueDetail(entry: WatchEntry): String = when {
    entry.positionMs > 0 -> stringResource(R.string.continue_from, clock(entry.positionMs))
    else -> pluralStringResource(R.plurals.continue_bookmarks, entry.bookmarks.size, entry.bookmarks.size)
}

/**
 * A card under "Continue watching": the film's art when it was played for a SCARY film, its file name otherwise;
 * where it continues from, and how far in (a bar along the bottom). OK continues; holding OK takes it off the row.
 */
@Composable
fun ContinueCard(
    item: ContinueItem,
    language: Language,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onFocused: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
) {
    val entry = item.entry
    val movie = item.movie
    val title = movie?.displayTitle(language) ?: entry.name
    val detail = continueDetail(entry)
    FocusSurface(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = (if (focusRequester != null) modifier.focusRequester(focusRequester) else modifier)
            .width(176.dp)
            .aspectRatio(16f / 9f)
            .testTag(CONTINUE_CARD_TAG),
        contentDescription = "$title, $detail",
        borderColor = ScaryColors.Bone.copy(alpha = 0.08f),
        onFocusChanged = { if (it) onFocused() },
    ) {
        if (movie != null) {
            PosterArt(movie, Modifier.fillMaxSize())
            rememberArtwork(movie.backgroundUrl)?.let { request ->
                AsyncImage(model = request, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
        } else {
            Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(ScaryColors.Crypt, ScaryColors.Blood.copy(alpha = 0.45f)))))
        }
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f)))))
        Column(Modifier.align(Alignment.BottomStart).padding(start = 9.dp, end = 9.dp, bottom = 11.dp)) {
            BasicText(
                title,
                style = ScaryType.CardTitle.mixedLanguage(),
                maxLines = if (movie == null) 2 else 1,
                overflow = TextOverflow.Ellipsis,
            )
            BasicText(detail, style = ScaryType.Caption.copy(color = ScaryColors.Candle))
        }
        // How far in.
        Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(4.dp).background(Color.White.copy(alpha = 0.2f))) {
            Box(Modifier.fillMaxWidth(entry.progress).fillMaxHeight().background(ScaryColors.Blood))
        }
    }
}

/** "Continue from 1:12:40" or "From the start", for a file with a saved spot. Back plays nothing. */
@Composable
fun ResumeDialog(choice: ResumeChoice, onContinue: () -> Unit, onFromStart: () -> Unit, onDismiss: () -> Unit) {
    val firstFocus = remember { FocusRequester() }
    Dialog(onDismissRequest = onDismiss) {
        LaunchedEffect(Unit) {
            withFrameNanos { }
            runCatching { firstFocus.requestFocus() }
        }
        Column(
            modifier = Modifier
                .dialogWidth(560.dp)
                .background(ScaryColors.Crypt, RoundedCornerShape(12.dp))
                .border(1.dp, ScaryColors.Candle.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                .verticalScroll(rememberScrollState())
                .padding(24.dp)
                .testTag(RESUME_DIALOG_TAG),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            BasicText(stringResource(R.string.resume_title), style = ScaryType.Title, modifier = Modifier.semantics { heading() })
            BasicText(choice.file.name, style = ScaryType.Body.mixedLanguage(), maxLines = 2, overflow = TextOverflow.Ellipsis)
            Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 4.dp)) {
                ScaryButton(stringResource(R.string.continue_from, clock(choice.positionMs)), onClick = onContinue, primary = true, focusRequester = firstFocus)
                ScaryButton(stringResource(R.string.resume_from_start), onClick = onFromStart)
            }
        }
    }
}
