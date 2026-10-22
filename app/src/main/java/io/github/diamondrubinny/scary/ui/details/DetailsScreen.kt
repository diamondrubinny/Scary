// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary.ui.details

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.diamondrubinny.scary.R
import io.github.diamondrubinny.scary.core.model.Language
import io.github.diamondrubinny.scary.core.model.Movie
import io.github.diamondrubinny.scary.ui.components.MovieMeta
import io.github.diamondrubinny.scary.ui.components.PosterArt
import io.github.diamondrubinny.scary.ui.components.rememberArtwork
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import io.github.diamondrubinny.scary.ui.components.PosterCard
import io.github.diamondrubinny.scary.ui.components.ScaryButton
import io.github.diamondrubinny.scary.ui.home.MovieDetails
import io.github.diamondrubinny.scary.ui.home.SidePadding
import io.github.diamondrubinny.scary.ui.home.subgenreLine
import io.github.diamondrubinny.scary.ui.theme.ScaryColors
import io.github.diamondrubinny.scary.ui.theme.ScaryType
import io.github.diamondrubinny.scary.ui.theme.mixedLanguage

@Composable
fun DetailsScreen(
    details: MovieDetails,
    language: Language,
    onWatch: (Movie) -> Unit,
    onTrailer: (Movie) -> Unit,
    onOpenSimilar: (Movie) -> Unit,
    isLocked: (Movie) -> Boolean = { false },
    inMyList: Boolean = false,
    onToggleMyList: (Movie) -> Unit = {},
) {
    val movie = details.movie
    val watchFocus = remember { FocusRequester() }
    LaunchedEffect(movie.id) {
        withFrameNanos { }
        runCatching { watchFocus.requestFocus() }
    }

    Column(Modifier.fillMaxSize().padding(top = 36.dp)) {
        Row(
            modifier = Modifier.padding(horizontal = SidePadding),
            horizontalArrangement = Arrangement.spacedBy(36.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                BasicText(subgenreLine(movie), style = ScaryType.Eyebrow)
                MovieTitle(movie, language)
                if (movie.displayTitle(language) != movie.title) {
                    BasicText(stringResource(R.string.original_title, movie.title), style = ScaryType.Caption)
                }
                MovieMeta(movie)
                if (movie.cast.isNotEmpty()) {
                    BasicText(
                        stringResource(R.string.cast_line, movie.cast.take(4).joinToString(", ")),
                        style = ScaryType.Body.copy(color = ScaryColors.Ash).mixedLanguage(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                BasicText(movie.synopsis(language), style = ScaryType.Body.copy(color = ScaryColors.Bone).mixedLanguage(), maxLines = 5, overflow = TextOverflow.Ellipsis)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 8.dp)) {
                    ScaryButton(
                        text = stringResource(R.string.action_watch_stremio),
                        onClick = { onWatch(movie) },
                        primary = true,
                        focusRequester = watchFocus,
                    )
                    ScaryButton(stringResource(R.string.action_trailer), onClick = { onTrailer(movie) })
                    ScaryButton(
                        text = stringResource(if (inMyList) R.string.action_in_my_list else R.string.action_add_my_list),
                        onClick = { onToggleMyList(movie) },
                    )
                }
            }
            Box(
                Modifier
                    .size(width = 150.dp, height = 225.dp)
                    .clip(RoundedCornerShape(8.dp)),
            ) {
                PosterArt(movie, Modifier.fillMaxSize())
                val poster = rememberArtwork(movie.posterUrl)
                if (poster != null) {
                    AsyncImage(model = poster, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                } else {
                    BasicText(
                        text = movie.title,
                        style = ScaryType.Title.mixedLanguage(),
                        maxLines = 3,
                        modifier = Modifier.align(Alignment.BottomStart).padding(10.dp),
                    )
                }
            }
        }
        if (details.similar.isNotEmpty()) {
            BasicText(
                text = stringResource(R.string.row_more_like),
                style = ScaryType.RowTitle,
                modifier = Modifier
                    .padding(start = SidePadding, end = SidePadding, top = 20.dp)
                    .semantics { heading() },
            )
            LazyRow(
                contentPadding = PaddingValues(horizontal = SidePadding, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                items(details.similar, key = { it.id }) { similar ->
                    PosterCard(
                        movie = similar,
                        onClick = { onOpenSimilar(similar) },
                        onFocused = {},
                        width = 92.dp,
                        locked = isLocked(similar),
                    )
                }
            }
        }
    }
}

/**
 * The movie's logo art when Cinemeta has one and the title is shown in its original language (a translated title
 * would contradict an English logo). The text title shows until the logo arrives, and whenever it cannot load.
 */
@Composable
private fun MovieTitle(movie: Movie, language: Language) {
    val title = movie.displayTitle(language)
    val logo = rememberArtwork(movie.logoUrl.takeIf { title == movie.title })
    var logoShown by remember(movie.id) { mutableStateOf(false) }
    Box(Modifier.semantics(mergeDescendants = true) { heading(); contentDescription = title }) {
        if (!logoShown) {
            BasicText(
                text = title,
                style = ScaryType.Display.mixedLanguage(),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (logo != null) {
            AsyncImage(
                model = logo,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                alignment = Alignment.CenterStart,
                onSuccess = { logoShown = true },
                onError = { logoShown = false },
                modifier = Modifier.heightIn(max = LOGO_MAX_HEIGHT).widthIn(max = LOGO_MAX_WIDTH),
            )
        }
    }
}

private val LOGO_MAX_HEIGHT = 110.dp
private val LOGO_MAX_WIDTH = 480.dp
