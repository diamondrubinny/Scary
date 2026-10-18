package io.github.diamondrubinny.scary.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.diamondrubinny.scary.R
import io.github.diamondrubinny.scary.core.model.Language
import io.github.diamondrubinny.scary.core.model.Movie
import io.github.diamondrubinny.scary.ui.components.AppTileCard
import io.github.diamondrubinny.scary.ui.components.MovieMeta
import io.github.diamondrubinny.scary.ui.components.PosterCard
import io.github.diamondrubinny.scary.ui.components.ScaryButton
import io.github.diamondrubinny.scary.ui.components.TopBar
import io.github.diamondrubinny.scary.ui.components.WideMovieCard
import io.github.diamondrubinny.scary.ui.theme.ScaryColors
import io.github.diamondrubinny.scary.ui.theme.ScaryType
import io.github.diamondrubinny.scary.ui.theme.mixedLanguage

/** Callbacks from the home screen, grouped so the composable signature stays readable. */
class HomeActions(
    val onCardFocused: (key: String, movieId: String?) -> Unit,
    val onOpenDetails: (Movie) -> Unit,
    val onWatch: (Movie) -> Unit,
    val onTrailer: (Movie) -> Unit,
    val onTile: (AppTile) -> Unit,
    val onToggleEffects: () -> Unit,
    val onOpenSettings: () -> Unit,
    val onOpenSearch: () -> Unit,
)

val SidePadding = 48.dp

@Composable
fun HomeScreen(
    state: HomeUiState,
    language: Language,
    listState: LazyListState,
    rowStates: (String) -> LazyListState,
    restoreKey: String?,
    resets: Int,
    actions: HomeActions,
    showSetHome: Boolean = false,
) {
    val heroFocus = remember { FocusRequester() }
    val restoreFocus = remember { FocusRequester() }

    // Focus lands on "watch tonight's pick" at start and after Home; on the last card when coming back from details.
    LaunchedEffect(state.loading, resets) {
        if (state.loading) return@LaunchedEffect
        withFrameNanos { }
        val restored = restoreKey != null && runCatching { restoreFocus.requestFocus() }.isSuccess
        if (!restored) runCatching { heroFocus.requestFocus() }
    }

    Column(Modifier.fillMaxSize()) {
        TopBar(
            effectsEnabled = state.effectsEnabled,
            onToggleEffects = actions.onToggleEffects,
            onOpenSettings = actions.onOpenSettings,
            onOpenSearch = actions.onOpenSearch,
            modifier = Modifier.padding(horizontal = SidePadding, vertical = 18.dp),
        )
        Hero(
            movie = state.hero,
            isNightPick = state.hero != null && state.hero.id == state.nightPick?.id,
            language = language,
            primaryFocus = heroFocus,
            actions = actions,
        )
        if (state.catalogError) {
            BasicText(
                stringResource(R.string.msg_catalog_error),
                style = ScaryType.BodyStrong,
                modifier = Modifier.padding(horizontal = SidePadding, vertical = 12.dp),
            )
        }
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .fadeTopEdge(72.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            items(state.rows, key = { it.key }) { row ->
                HomeRowView(row, language, rowStates(row.key), restoreKey, restoreFocus, actions, state::isLocked, showSetHome)
            }
        }
    }
}

@Composable
private fun Hero(
    movie: Movie?,
    isNightPick: Boolean,
    language: Language,
    primaryFocus: FocusRequester,
    actions: HomeActions,
) {
    Column(
        modifier = Modifier
            .padding(horizontal = SidePadding)
            .fillMaxWidth(0.62f)
            .height(196.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp, Alignment.Bottom),
    ) {
        if (movie == null) return@Column
        BasicText(
            text = if (isNightPick) stringResource(R.string.night_pick) else subgenreLine(movie),
            style = ScaryType.Eyebrow,
        )
        BasicText(
            text = movie.title,
            style = ScaryType.Display,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.semantics { heading() },
        )
        MovieMeta(movie)
        BasicText(
            text = movie.synopsis(language),
            style = ScaryType.Body.mixedLanguage(),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 4.dp)) {
            ScaryButton(
                text = stringResource(R.string.action_watch_stremio),
                onClick = { actions.onWatch(movie) },
                primary = true,
                focusRequester = primaryFocus,
            )
            ScaryButton(stringResource(R.string.action_trailer), onClick = { actions.onTrailer(movie) })
            ScaryButton(stringResource(R.string.action_details), onClick = { actions.onOpenDetails(movie) })
        }
    }
}

@Composable
private fun HomeRowView(
    row: HomeRow,
    language: Language,
    rowState: LazyListState,
    restoreKey: String?,
    restoreFocus: FocusRequester,
    actions: HomeActions,
    isLocked: (Movie) -> Boolean,
    showSetHome: Boolean,
) {
    val title = when (row) {
        is HomeRow.Recent -> stringResource(R.string.row_recent)
        is HomeRow.Apps -> stringResource(R.string.row_apps)
        is HomeRow.Collection -> row.collection.title(language)
    }
    Column {
        BasicText(
            text = title,
            style = ScaryType.RowTitle,
            modifier = Modifier
                .padding(horizontal = SidePadding)
                .semantics { heading() },
        )
        LazyRow(
            state = rowState,
            // Vertical padding leaves room for the focused card to grow without clipping.
            contentPadding = PaddingValues(horizontal = SidePadding, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            when (row) {
                is HomeRow.Recent -> items(row.movies, key = { it.id }) { movie ->
                    val key = "${row.key}:${movie.id}"
                    WideMovieCard(
                        movie = movie,
                        locked = isLocked(movie),
                        onClick = { actions.onOpenDetails(movie) },
                        onFocused = { actions.onCardFocused(key, movie.id) },
                        focusRequester = restoreFocus.takeIf { key == restoreKey },
                    )
                }
                is HomeRow.Apps -> items(
                    (if (showSetHome) listOf(AppTile.SetAsHome) else emptyList()) + row.tiles,
                    key = { it.key },
                ) { tile ->
                    val key = "${row.key}:${tile.key}"
                    AppTileCard(
                        tile = tile,
                        onClick = { actions.onTile(tile) },
                        onFocused = { actions.onCardFocused(key, null) },
                        focusRequester = restoreFocus.takeIf { key == restoreKey },
                    )
                }
                is HomeRow.Collection -> items(row.movies, key = { it.id }) { movie ->
                    val key = "${row.key}:${movie.id}"
                    PosterCard(
                        movie = movie,
                        locked = isLocked(movie),
                        onClick = { actions.onOpenDetails(movie) },
                        onFocused = { actions.onCardFocused(key, movie.id) },
                        focusRequester = restoreFocus.takeIf { key == restoreKey },
                    )
                }
            }
        }
    }
}

/** Rows scrolled past the top melt into the dark instead of showing as a cut strip under the hero. */
private fun Modifier.fadeTopEdge(height: Dp): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        drawRect(
            brush = Brush.verticalGradient(
                0f to Color.Transparent,
                0.45f to Color.Transparent,
                1f to Color.Black,
                startY = 0f,
                endY = height.toPx(),
            ),
            blendMode = BlendMode.DstIn,
        )
    }

@Composable
fun subgenreLine(movie: Movie): String {
    val genre = stringResource(movie.genre.labelRes())
    val subs = movie.subgenres.map { stringResource(it.labelRes()) }
    return (listOf(genre) + subs).joinToString(" · ")
}

@Composable
fun LoadingView(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        BasicText(stringResource(R.string.loading), style = ScaryType.Title.copy(color = ScaryColors.Ash))
    }
}
