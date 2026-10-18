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
import androidx.compose.runtime.remember
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
import coil.compose.AsyncImage
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
                BasicText(
                    text = movie.displayTitle(language),
                    style = ScaryType.Display,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.semantics { heading() },
                )
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
                        style = ScaryType.Title,
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
