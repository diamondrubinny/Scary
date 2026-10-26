// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary.ui.details

import io.github.diamondrubinny.scary.ui.home.WatchOption
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import io.github.diamondrubinny.scary.R
import io.github.diamondrubinny.scary.ui.trailer.AutoTrailer
import io.github.diamondrubinny.scary.ui.trailer.canAutoTrailer
import io.github.diamondrubinny.scary.ui.trailer.TrailerSettings
import io.github.diamondrubinny.scary.core.model.Language
import io.github.diamondrubinny.scary.core.model.Movie
import io.github.diamondrubinny.scary.ui.components.MovieMeta
import io.github.diamondrubinny.scary.ui.components.PosterArt
import io.github.diamondrubinny.scary.ui.components.rememberArtwork
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.gestures.BringIntoViewSpec
import io.github.diamondrubinny.scary.ui.home.RentOrBuyState
import io.github.diamondrubinny.scary.ui.home.ProviderOffer
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import io.github.diamondrubinny.scary.ui.components.PosterCard
import io.github.diamondrubinny.scary.ui.components.ScaryButton
import io.github.diamondrubinny.scary.ui.home.MovieDetails
import io.github.diamondrubinny.scary.ui.home.SidePadding
import io.github.diamondrubinny.scary.ui.home.subgenreLine
import io.github.diamondrubinny.scary.ui.theme.ScaryColors
import io.github.diamondrubinny.scary.ui.theme.ScaryType
import io.github.diamondrubinny.scary.ui.fx.chill
import io.github.diamondrubinny.scary.ui.fx.riseIn
import io.github.diamondrubinny.scary.ui.theme.mixedLanguage

@OptIn(ExperimentalFoundationApi::class)
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
    watched: Boolean = false,
    isWatched: (Movie) -> Boolean = { false },
    onToggleWatched: (Movie) -> Unit = {},
    watchOptions: List<WatchOption> = emptyList(),
    canSearchEverywhere: Boolean = false,
    onSearchIn: (Movie, WatchOption) -> Unit = { _, _ -> },
    onSearchEverywhere: (Movie) -> Unit = {},
    rentOrBuy: RentOrBuyState? = null,
    countryName: (String) -> String = { it },
    onOffer: (Movie, ProviderOffer) -> Unit = { _, _ -> },
    onLoadOffers: (Movie) -> Unit = {},
    /** Changes when the TMDB key, the country or online data change, so the offers load again. */
    offersKey: Any? = null,
    trailer: TrailerSettings = TrailerSettings.Off,
) {
    val movie = details.movie
    val watchFocus = remember { FocusRequester() }
    LaunchedEffect(movie.id) {
        withFrameNanos { }
        runCatching { watchFocus.requestFocus() }
    }
    LaunchedEffect(movie.id, offersKey) { onLoadOffers(movie) }

    // Scrolls when "Rent or buy" and "More like this" need more than one screen; the remote's focus moves it. On a TV,
    // Compose's default would move every focused button up to a third of the screen and cut the title off, so the
    // page scrolls only as far as the focused button needs; the rows inside keep the TV's own behaviour.
    val rowsScroll = LocalBringIntoViewSpec.current
    CompositionLocalProvider(LocalBringIntoViewSpec provides OnlyWhenNeeded) {
        val pageScroll = rememberScrollState()
        // The trailer stops once the page scrolls more than half of it out of view (YouTube: only while visible).
        val halfTrailerPx = with(LocalDensity.current) { (DetailsTrailerSize.height / 2).toPx() }
        val trailerOnScreen by remember(pageScroll, halfTrailerPx) { derivedStateOf { pageScroll.value < halfTrailerPx } }
        // Room for the trailer is kept from the start, so text and buttons never move under the focus when it starts.
        val trailerSlot = canAutoTrailer(movie, isLocked(movie), trailer, DetailsTrailerSize)
        Column(Modifier.fillMaxSize().verticalScroll(pageScroll).riseIn(movie.id).padding(top = 36.dp, bottom = 24.dp)) {
            CompositionLocalProvider(LocalBringIntoViewSpec provides rowsScroll) {
                Row(
                    modifier = Modifier.padding(horizontal = SidePadding),
                    horizontalArrangement = Arrangement.spacedBy(36.dp),
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        BasicText(subgenreLine(movie), style = ScaryType.Eyebrow)
                        MovieTitle(movie, language, locked = isLocked(movie))
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
                        val elsewhere = watchOptions.isNotEmpty() || canSearchEverywhere
                        // One line less of synopsis when the apps row is shown, so "More like this" stays on screen.
                        BasicText(
                            movie.synopsis(language),
                            style = ScaryType.Body.copy(color = ScaryColors.Bone).mixedLanguage(),
                            maxLines = if (elsewhere) 3 else 5,
                            overflow = TextOverflow.Ellipsis,
                        )
                        // Four buttons: in a long language, or at a large system font, the last ones wrap to a second line
                        // instead of being cut.
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.padding(top = 8.dp),
                        ) {
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
                            ScaryButton(
                                text = stringResource(if (watched) R.string.action_watched else R.string.action_mark_watched),
                                onClick = { onToggleWatched(movie) },
                            )
                        }
                        if (elsewhere) {
                            WatchElsewhere(movie, watchOptions, canSearchEverywhere, onSearchIn, onSearchEverywhere)
                        }
                        // A locked movie shows no store either, as it shows no art.
                        if (rentOrBuy != null && !isLocked(movie)) {
                            RentOrBuy(
                                state = rentOrBuy,
                                countryName = countryName,
                                onOffer = { onOffer(movie, it) },
                            )
                        }
                    }
                    // The trailer takes the poster's place while it plays, and gives it back when it ends.
                    AutoTrailer(
                        movie = movie,
                        locked = isLocked(movie),
                        settings = trailer,
                        playerSize = DetailsTrailerSize,
                        modifier = if (trailerSlot) Modifier.width(DetailsTrailerSize.width) else Modifier,
                        visible = trailerOnScreen,
                    ) {
                        Box(
                            Modifier
                                .size(width = 150.dp, height = 225.dp)
                                .clip(RoundedCornerShape(8.dp)),
                        ) {
                            PosterArt(movie, Modifier.fillMaxSize())
                            // A locked movie's art stays hidden, as on its card: the session may have locked again since it opened.
                            val poster = rememberArtwork(movie.posterUrl.takeUnless { isLocked(movie) })
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
                                watched = isWatched(similar),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Scrolls only when the focused item is partly out of view, and then just enough to show it. */
@OptIn(ExperimentalFoundationApi::class)
private val OnlyWhenNeeded = object : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
        val trailingEdge = offset + size
        return when {
            offset >= 0f && trailingEdge <= containerSize -> 0f
            offset < 0f || size > containerSize -> offset
            else -> trailingEdge - containerSize
        }
    }
}

/**
 * The movie's logo art when Cinemeta has one and the title is shown in its original language (a translated title
 * would contradict an English logo). The text title shows until the logo arrives, and whenever it cannot load.
 */
@Composable
private fun MovieTitle(movie: Movie, language: Language, locked: Boolean) {
    val title = movie.displayTitle(language)
    val logo = rememberArtwork(movie.logoUrl.takeIf { title == movie.title && !locked })
    // Keyed on the logo itself: when the title changes language and the logo goes, the text title comes back.
    var logoShown by remember(movie.id, logo) { mutableStateOf(false) }
    // The title shivers as it comes out of the dark; again when the logo art arrives and replaces the text.
    Box(Modifier.semantics(mergeDescendants = true) { heading(); contentDescription = title }.chill(movie.id to logoShown)) {
        if (!logoShown) {
            BasicText(
                text = title,
                style = ScaryType.DisplayFitting.mixedLanguage(),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                autoSize = ScaryType.DisplayFit,
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

/**
 * "Look for it in your apps": a button per streaming app on this TV (search when the app accepts one, otherwise just
 * open it), and Google TV's own search when there is one. SCARY does not know where a movie is available, and the note
 * says so.
 */
@Composable
private fun WatchElsewhere(
    movie: Movie,
    options: List<WatchOption>,
    canSearchEverywhere: Boolean,
    onSearchIn: (Movie, WatchOption) -> Unit,
    onSearchEverywhere: (Movie) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
        BasicText(
            stringResource(R.string.watch_elsewhere),
            style = ScaryType.Eyebrow,
            modifier = Modifier.semantics { heading() },
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            // Room for the focused button to grow.
            contentPadding = PaddingValues(vertical = 6.dp, horizontal = 4.dp),
        ) {
            if (canSearchEverywhere) {
                item(key = "everywhere") {
                    ScaryButton(stringResource(R.string.search_everywhere), onClick = { onSearchEverywhere(movie) })
                }
            }
            items(options, key = { it.packageName }) { app ->
                ScaryButton(
                    text = stringResource(if (app.canSearch) R.string.search_in_app else R.string.open_app, app.label),
                    onClick = { onSearchIn(movie, app) },
                )
            }
        }
        BasicText(stringResource(R.string.watch_elsewhere_note), style = ScaryType.Caption)
    }
}

/** 16:9, and 640 x 360 pixels on a 1080p TV: above YouTube's 200 x 200 minimum. */
private val DetailsTrailerSize = DpSize(320.dp, 180.dp)
