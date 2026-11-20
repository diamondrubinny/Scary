// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary.ui.person

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.diamondrubinny.scary.R
import io.github.diamondrubinny.scary.core.model.Movie
import io.github.diamondrubinny.scary.ui.components.EmptyState
import io.github.diamondrubinny.scary.ui.details.PersonPhoto
import io.github.diamondrubinny.scary.ui.components.PosterCard
import io.github.diamondrubinny.scary.ui.home.PersonUi
import io.github.diamondrubinny.scary.ui.home.SidePadding
import io.github.diamondrubinny.scary.ui.theme.ScaryType
import io.github.diamondrubinny.scary.ui.theme.mixedLanguage
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.aspectRatio

/** Test tags of an actor's screen. */
const val PERSON_SCREEN_TAG = "personScreen"
const val PERSON_STATUS_TAG = "personStatus"
const val PERSON_PHOTO_TAG = "personPhoto"

/**
 * "Films with <actor>": the films SCARY holds whose cast names the actor and, with the viewer's TMDB key, the
 * actor's horror, thrillers and mysteries from TMDB (docs/CAST_PLAN.md). Opened from the cast row of a film's page.
 * [person] is null, or another actor's, for the moment it takes to work out: the screen asks for its own ([onLoad]).
 */
@Composable
fun PersonScreen(
    name: String,
    person: PersonUi?,
    onLoad: (String) -> Unit,
    onOpenMovie: (Movie) -> Unit,
    /** The actor's photo from TMDB, when they were chosen from a cast TMDB listed; null otherwise. */
    photo: String? = null,
    /** TMDB's id of the actor, when they were chosen from a cast TMDB listed: [person] must be theirs, not a namesake's. */
    personId: Long? = null,
    isLocked: (Movie) -> Boolean = { false },
    isWatched: (Movie) -> Boolean = { false },
    gridState: LazyGridState = rememberLazyGridState(),
    /** The film opened from this screen, if any: coming back from it, the focus lands on its card. */
    returnTo: String? = null,
) {
    LaunchedEffect(name) { onLoad(name) }
    val shown = person?.takeIf { it.name == name && it.personId == personId }
    val movies = shown?.movies.orEmpty()
    val firstFocus = remember { FocusRequester() }
    val returnFocus = remember { FocusRequester() }
    // Once the films are there: back on the card the viewer came from, else on the first one.
    LaunchedEffect(movies.isNotEmpty()) {
        if (movies.isEmpty()) return@LaunchedEffect
        withFrameNanos { }
        if (!runCatching { returnFocus.requestFocus() }.getOrDefault(false)) runCatching { firstFocus.requestFocus() }
    }
    Column(Modifier.fillMaxSize().padding(top = 28.dp).testTag(PERSON_SCREEN_TAG)) {
        Row(Modifier.padding(horizontal = SidePadding), verticalAlignment = Alignment.CenterVertically) {
            if (photo != null) {
                PersonPhoto(
                    name,
                    photo,
                    Modifier.padding(end = 16.dp).width(52.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(4.dp)).testTag(PERSON_PHOTO_TAG),
                )
            }
            BasicText(
                stringResource(R.string.person_title, name),
                style = ScaryType.Display.copy(fontSize = ScaryType.Display.fontSize * 0.7f).mixedLanguage(),
                maxLines = 2,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
        }
        BasicText(
            text = when {
                shown == null -> ""
                shown.loading -> stringResource(R.string.person_loading)
                shown.fromTmdb -> stringResource(R.string.person_note_tmdb, movies.size.toString())
                else -> stringResource(R.string.person_note_local, movies.size.toString())
            },
            style = ScaryType.Caption,
            modifier = Modifier
                .padding(horizontal = SidePadding, vertical = 10.dp)
                .testTag(PERSON_STATUS_TAG)
                .semantics { liveRegion = LiveRegionMode.Polite },
        )
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 104.dp),
            contentPadding = PaddingValues(horizontal = SidePadding, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
            modifier = Modifier.weight(1f),
            state = gridState,
        ) {
            if (shown != null && !shown.loading && movies.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }, key = "empty") {
                    EmptyState(title = stringResource(R.string.person_empty_title), body = stringResource(R.string.person_empty_body))
                }
            }
            items(movies, key = { it.id }) { movie ->
                PosterCard(
                    movie = movie,
                    onClick = { onOpenMovie(movie) },
                    onFocused = {},
                    locked = isLocked(movie),
                    watched = isWatched(movie),
                    focusRequester = when {
                        movie.id == returnTo -> returnFocus
                        movie === movies.first() -> firstFocus
                        else -> null
                    },
                )
            }
        }
    }
}
