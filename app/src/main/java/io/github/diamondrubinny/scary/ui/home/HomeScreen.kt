// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary.ui.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.rememberUpdatedState
import io.github.diamondrubinny.scary.ui.components.CreditLine
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import io.github.diamondrubinny.scary.ui.theme.LocalReduceMotion
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.selected
import io.github.diamondrubinny.scary.core.catalog.Mood
import io.github.diamondrubinny.scary.ui.components.EmptyState
import io.github.diamondrubinny.scary.ui.components.FocusSurface
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
import androidx.compose.ui.platform.testTag
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
    /** Long press on a movie card: add to or remove from "My list". */
    val onToggleMyList: (Movie) -> Unit,
    val onSurprise: () -> Unit,
    /** Picks a mood in "What do you feel like tonight?", or clears it when chosen again. */
    val onMood: (Mood) -> Unit,
    /** The viewer is near the end of a row; endless online rows load their next page. */
    val onNearRowEnd: (rowKey: String) -> Unit = {},
)

val SidePadding = 48.dp

/** The list of home rows, for UI tests. */
const val HOME_ROWS_TAG = "homeRows"

/** Test tag of a home row's list of cards. */
fun rowTag(rowKey: String): String = "row:$rowKey"

/** Key of the credit line at the end of the home rows. */
const val CREDIT_KEY = "credit"

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
    scrollToRow: String? = null,
    onScrolledToRow: () -> Unit = {},
) {
    val heroFocus = remember { FocusRequester() }
    val restoreFocus = remember { FocusRequester() }

    // Debug intents only: bring a row into view and focus the requested card in it.
    LaunchedEffect(scrollToRow, state.loading) {
        if (scrollToRow == null || state.loading) return@LaunchedEffect
        val index = state.rows.indexOfFirst { it.key == scrollToRow }
        if (index >= 0) {
            listState.scrollToItem(index)
            withFrameNanos { }
            runCatching { restoreFocus.requestFocus() }
        }
        onScrolledToRow()
    }

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
            onSurprise = actions.onSurprise,
            modifier = Modifier.padding(horizontal = SidePadding, vertical = 18.dp),
            familyNight = state.familyNight,
        )
        Hero(
            movie = state.hero,
            isNightPick = state.hero != null && state.hero.id == state.nightPick?.id,
            language = language,
            primaryFocus = heroFocus,
            actions = actions,
        )
        if (state.catalogError) {
            EmptyState(
                title = stringResource(R.string.catalog_error_title),
                body = stringResource(R.string.msg_catalog_error),
                modifier = Modifier.padding(horizontal = SidePadding, vertical = 12.dp),
            )
        }
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .fadeTopEdge(72.dp)
                .testTag(HOME_ROWS_TAG),
            contentPadding = PaddingValues(top = 8.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            items(state.rows, key = { it.key }) { row ->
                HomeRowView(row, language, rowStates(row.key), restoreKey, restoreFocus, actions, state::isLocked, showSetHome)
            }
            item(key = CREDIT_KEY) {
                CreditLine(Modifier.padding(horizontal = SidePadding, vertical = 12.dp))
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
            .height(212.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp, Alignment.Bottom),
    ) {
        if (movie == null) return@Column
        BasicText(
            text = if (isNightPick) stringResource(R.string.night_pick) else subgenreLine(movie),
            style = ScaryType.Eyebrow,
        )
        // The text block yields space first, so the buttons below always keep their full height,
        // even when a longer language wraps the synopsis to two lines.
        Column(
            modifier = Modifier.weight(1f, fill = false),
            verticalArrangement = Arrangement.spacedBy(7.dp, Alignment.Bottom),
        ) {
            BasicText(
                text = movie.displayTitle(language),
                // Titles are often English inside the Hebrew UI: "28 Days Later" must not become "Days Later 28".
                style = ScaryType.Display.mixedLanguage(),
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
        }
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
    if (row is HomeRow.Moods) {
        MoodsRowView(row, rowState, restoreKey, restoreFocus, actions, isLocked)
        return
    }
    if (row is HomeRow.Collection) {
        // Endless online rows: ask for the next page while there are still a few cards to go.
        val size by rememberUpdatedState(row.movies.size)
        val onNearEnd by rememberUpdatedState(actions.onNearRowEnd)
        LaunchedEffect(rowState, row.key) {
            snapshotFlow { rowState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
                .collect { last -> if (last >= 0 && last >= size - LOAD_AHEAD) onNearEnd(row.key) }
        }
    }
    val title = when (row) {
        is HomeRow.Recent -> stringResource(R.string.row_recent)
        is HomeRow.MyList -> stringResource(R.string.row_my_list)
        is HomeRow.Apps -> stringResource(R.string.row_apps)
        is HomeRow.Collection -> row.collection.title(language)
        is HomeRow.Seasonal -> stringResource(row.season.titleRes())
        is HomeRow.Moods -> stringResource(R.string.row_moods)
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
            modifier = Modifier.testTag(rowTag(row.key)),
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
                        onLongClick = { actions.onToggleMyList(movie) },
                        onFocused = { actions.onCardFocused(key, movie.id) },
                        focusRequester = restoreFocus.takeIf { key == restoreKey },
                    )
                }
                is HomeRow.MyList -> items(row.movies, key = { it.id }) { movie ->
                    val key = "${row.key}:${movie.id}"
                    PosterCard(
                        movie = movie,
                        locked = isLocked(movie),
                        onClick = { actions.onOpenDetails(movie) },
                        onLongClick = { actions.onToggleMyList(movie) },
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
                is HomeRow.Moods -> Unit
                is HomeRow.Collection, is HomeRow.Seasonal -> items(
                    if (row is HomeRow.Collection) row.movies else (row as HomeRow.Seasonal).movies,
                    key = { it.id },
                ) { movie ->
                    val key = "${row.key}:${movie.id}"
                    PosterCard(
                        movie = movie,
                        locked = isLocked(movie),
                        onClick = { actions.onOpenDetails(movie) },
                        onLongClick = { actions.onToggleMyList(movie) },
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

/**
 * The shape of the home screen while the library loads: hero lines and two rows of cards, pulsing slowly
 * (about once every 1.4 seconds, far from flashing; still when motion is reduced). TalkBack hears "Loading".
 */
@Composable
fun LoadingView(modifier: Modifier = Modifier) {
    val loading = stringResource(R.string.loading)
    val pulse = if (LocalReduceMotion.current) {
        { SKELETON_ALPHA_MIN }
    } else {
        val transition = rememberInfiniteTransition(label = "skeleton")
        val alpha = transition.animateFloat(
            initialValue = SKELETON_ALPHA_MIN,
            targetValue = SKELETON_ALPHA_MAX,
            animationSpec = infiniteRepeatable(tween(SKELETON_PULSE_MS), RepeatMode.Reverse),
            label = "skeletonAlpha",
        );
        { alpha.value }
    }
    Column(
        modifier
            .fillMaxSize()
            .semantics(mergeDescendants = true) {
                contentDescription = loading
                liveRegion = LiveRegionMode.Polite
            }
            .graphicsLayer { alpha = pulse() }
            .padding(horizontal = SidePadding),
    ) {
        Spacer(Modifier.height(150.dp))
        SkeletonBlock(Modifier.width(140.dp).height(16.dp))
        Spacer(Modifier.height(14.dp))
        SkeletonBlock(Modifier.width(460.dp).height(64.dp))
        Spacer(Modifier.height(14.dp))
        SkeletonBlock(Modifier.width(380.dp).height(18.dp))
        Spacer(Modifier.height(10.dp))
        SkeletonBlock(Modifier.width(560.dp).height(18.dp))
        Spacer(Modifier.height(22.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            SkeletonBlock(Modifier.width(190.dp).height(48.dp))
            SkeletonBlock(Modifier.width(120.dp).height(48.dp))
            SkeletonBlock(Modifier.width(120.dp).height(48.dp))
        }
        Spacer(Modifier.height(40.dp))
        repeat(2) {
            SkeletonBlock(Modifier.width(180.dp).height(20.dp))
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                repeat(SKELETON_CARDS) { SkeletonBlock(Modifier.width(104.dp).height(156.dp)) }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SkeletonBlock(modifier: Modifier) {
    Box(modifier.background(ScaryColors.Crypt, RoundedCornerShape(6.dp)))
}

/** Cards from the end of a row at which its next page starts loading. */
private const val LOAD_AHEAD = 8

private const val SKELETON_ALPHA_MIN = 0.45f
private const val SKELETON_ALPHA_MAX = 0.9f
private const val SKELETON_PULSE_MS = 700
private const val SKELETON_CARDS = 12

/**
 * "What do you feel like tonight?": a row of mood chips; once one is chosen, its movies appear in a row below.
 * Choosing the same mood again closes the row.
 */
@Composable
private fun MoodsRowView(
    row: HomeRow.Moods,
    chipState: LazyListState,
    restoreKey: String?,
    restoreFocus: FocusRequester,
    actions: HomeActions,
    isLocked: (Movie) -> Boolean,
) {
    val movieState = rememberLazyListState()
    LaunchedEffect(row.selected) { movieState.scrollToItem(0) }
    Column {
        BasicText(
            text = stringResource(R.string.row_moods),
            style = ScaryType.RowTitle,
            modifier = Modifier
                .padding(horizontal = SidePadding)
                .semantics { heading() },
        )
        LazyRow(
            state = chipState,
            contentPadding = PaddingValues(horizontal = SidePadding, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(Mood.entries, key = { it.key }) { mood ->
                val key = "${row.key}:chip:${mood.key}"
                MoodChip(
                    label = stringResource(mood.labelRes()),
                    selected = mood == row.selected,
                    onClick = { actions.onMood(mood) },
                    onFocused = { actions.onCardFocused(key, null) },
                    focusRequester = restoreFocus.takeIf { key == restoreKey },
                )
            }
        }
        if (row.selected != null && row.movies.isEmpty()) {
            // Family night or the parental limits can leave a mood with nothing to show.
            EmptyState(
                title = stringResource(R.string.mood_empty_title),
                body = stringResource(R.string.mood_empty_body),
                modifier = Modifier.padding(horizontal = SidePadding, vertical = 8.dp),
            )
        }
        if (row.selected != null && row.movies.isNotEmpty()) {
            LazyRow(
                state = movieState,
                contentPadding = PaddingValues(horizontal = SidePadding, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                items(row.movies, key = { it.id }) { movie ->
                    val key = "${row.key}:${movie.id}"
                    PosterCard(
                        movie = movie,
                        locked = isLocked(movie),
                        onClick = { actions.onOpenDetails(movie) },
                        onLongClick = { actions.onToggleMyList(movie) },
                        onFocused = { actions.onCardFocused(key, movie.id) },
                        focusRequester = restoreFocus.takeIf { key == restoreKey },
                    )
                }
            }
        }
    }
}

@Composable
private fun MoodChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    onFocused: () -> Unit,
    focusRequester: FocusRequester?,
) {
    FocusSurface(
        onClick = onClick,
        onFocusChanged = { if (it) onFocused() },
        modifier = (if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .semantics { this.selected = selected },
        shape = RoundedCornerShape(50),
        focusedScale = 1.06f,
        containerColor = if (selected) ScaryColors.Blood else ScaryColors.Void.copy(alpha = 0.6f),
        focusedContainerColor = ScaryColors.Bone,
        borderColor = if (selected) ScaryColors.Candle else ScaryColors.Bone.copy(alpha = 0.25f),
        focusedBorderColor = ScaryColors.Candle,
    ) { focused ->
        BasicText(
            text = label,
            style = ScaryType.Label.copy(color = if (focused) ScaryColors.Void else ScaryColors.Bone),
            maxLines = 1,
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 22.dp, vertical = 10.dp),
        )
    }
}
