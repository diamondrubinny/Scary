// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary.ui.home

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalDensity
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
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import io.github.diamondrubinny.scary.R
import io.github.diamondrubinny.scary.ui.trailer.AutoTrailer
import io.github.diamondrubinny.scary.core.catalog.TimeBudget
import io.github.diamondrubinny.scary.ui.trailer.TrailerSettings
import io.github.diamondrubinny.scary.core.model.Language
import io.github.diamondrubinny.scary.core.model.Movie
import io.github.diamondrubinny.scary.ui.components.AppTileCard
import io.github.diamondrubinny.scary.ui.components.DataCredit
import io.github.diamondrubinny.scary.ui.components.tryRequestFocus
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
    val onDecade: (Int) -> Unit,
    /** The viewer is near the end of a row; endless online rows load their next page. */
    val onNearRowEnd: (rowKey: String) -> Unit = {},
    /** Picks a time budget in "How much time do you have?", or clears it when chosen again. */
    val onTimeBudget: (TimeBudget) -> Unit = {},
)

val SidePadding = 48.dp

/** The list of home rows, for UI tests. */
const val HOME_ROWS_TAG = "homeRows"

/** Test tag of a home row's list of cards. */
fun rowTag(rowKey: String): String = "row:$rowKey"

/** Key of the credit line at the end of the home rows. */
const val CREDIT_KEY = "credit"

@OptIn(ExperimentalFoundationApi::class)
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
    trailer: TrailerSettings = TrailerSettings.Off,
) {
    val heroFocus = remember { FocusRequester() }
    val restoreFocus = remember { FocusRequester() }
    // Set when the last movie of My list is removed from its own row: the row goes away, and focus with it.
    var heroAfterMyList by remember { mutableStateOf(false) }
    val hasMyList = state.rows.any { it is HomeRow.MyList }
    LaunchedEffect(hasMyList, heroAfterMyList) {
        if (heroAfterMyList && !hasMyList) {
            withFrameNanos { }
            heroFocus.tryRequestFocus()
            heroAfterMyList = false
        }
    }

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
        // The card may be gone (removed from My list, filtered by family night, scrolled out of a mood row): then the hero.
        val restored = restoreKey != null && restoreFocus.tryRequestFocus()
        if (!restored) heroFocus.tryRequestFocus()
    }

    // While the remote is in the rows, the top of the screen gives them room: the donation code steps aside and the hero
    // drops its synopsis, so a whole row (title and cards) shows with the next one peeking below it.
    var rowsFocused by remember { mutableStateOf(false) }
    val rowsScroll = LocalBringIntoViewSpec.current
    val pivot = with(LocalDensity.current) { RowPivot.toPx() }
    val rowsPivot = remember(pivot) { FocusedRowPivot(pivot) }

    Column(Modifier.fillMaxSize()) {
        TopBar(
            effectsEnabled = state.effectsEnabled,
            onToggleEffects = actions.onToggleEffects,
            onOpenSettings = actions.onOpenSettings,
            onOpenSearch = actions.onOpenSearch,
            onSurprise = actions.onSurprise,
            modifier = Modifier.padding(horizontal = SidePadding, vertical = if (rowsFocused) 12.dp else 18.dp),
            familyNight = state.familyNight,
            sleepAtMillis = state.sleepAtMillis,
            compact = rowsFocused,
        )
        Hero(
            movie = state.hero,
            isNightPick = state.hero != null && state.hero.id == state.nightPick?.id,
            language = language,
            primaryFocus = heroFocus,
            actions = actions,
            locked = state.hero?.let(state::isLocked) ?: false,
            trailer = trailer,
            compact = rowsFocused,
        )
        if (state.catalogError) {
            EmptyState(
                title = stringResource(R.string.catalog_error_title),
                body = stringResource(R.string.msg_catalog_error),
                modifier = Modifier.padding(horizontal = SidePadding, vertical = 12.dp),
            )
        }
        // The focused row stays at the same height, its title in view; the cards inside keep the TV's own scrolling.
        CompositionLocalProvider(LocalBringIntoViewSpec provides rowsPivot) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .onFocusChanged { rowsFocused = it.hasFocus }
                    .fadeTopEdge(RowsFade)
                    .testTag(HOME_ROWS_TAG),
                contentPadding = PaddingValues(top = 8.dp, bottom = 40.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                items(state.rows, key = { it.key }) { row ->
                    CompositionLocalProvider(LocalBringIntoViewSpec provides rowsScroll) {
                        HomeRowView(row, language, rowStates(row.key), restoreKey, restoreFocus, actions, state::isLocked, state::isWatched, showSetHome) {
                            heroAfterMyList = true
                        }
                    }
                }
                item(key = CREDIT_KEY) {
                    Column(
                        Modifier.padding(horizontal = SidePadding, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        CreditLine()
                        DataCredit()
                    }
                }
            }
        }
    }
}

/**
 * Where a focused card's top settles in the rows: below its row's title and the faded top edge, so moving up or down
 * always shows the whole row, and the next one peeks in below.
 */
private val RowPivot = 60.dp

/** Rows scrolled past melt into the dark over this much, above the focused row's title. */
private val RowsFade = 20.dp

/** Scrolls the rows so the focused card's top sits [pivot] pixels below their top (as far as the list can scroll). */
@OptIn(ExperimentalFoundationApi::class)
private class FocusedRowPivot(private val pivot: Float) : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float = offset - pivot
}

@Composable
private fun Hero(
    movie: Movie?,
    isNightPick: Boolean,
    language: Language,
    primaryFocus: FocusRequester,
    actions: HomeActions,
    locked: Boolean,
    trailer: TrailerSettings,
    /** The rows have the focus: no synopsis and a smaller trailer, so the rows get the room. */
    compact: Boolean = false,
) {
    val height by animateDpAsState(
        targetValue = if (compact) HeroCompactHeight else HeroHeight,
        animationSpec = if (LocalReduceMotion.current) snap() else tween(HERO_RESIZE_MS),
        label = "heroHeight",
    )
    // The trailer window sits on the far side from the text (right in English, left in Hebrew and Arabic), so nothing
    // ever covers it, as YouTube requires.
    Row(
        modifier = Modifier
            .padding(horizontal = SidePadding)
            .fillMaxWidth()
            .height(height),
        verticalAlignment = Alignment.Bottom,
    ) {
        HeroText(movie, isNightPick, language, primaryFocus, actions, Modifier.fillMaxWidth(0.62f), showSynopsis = !compact)
        Spacer(Modifier.weight(1f))
        if (movie != null) {
            AutoTrailer(movie = movie, locked = locked, settings = trailer, playerSize = if (compact) HeroTrailerCompactSize else HeroTrailerSize)
        }
    }
}

private val HeroHeight = 212.dp

/** Title, details and buttons without the synopsis, beside the smaller trailer. */
private val HeroCompactHeight = 164.dp

private const val HERO_RESIZE_MS = 180

/** 16:9, and 608 x 342 pixels on a 1080p TV: above YouTube's 200 x 200 minimum. */
private val HeroTrailerSize = DpSize(304.dp, 171.dp)

/** 16:9 while the rows have the focus: 576 x 324 pixels at 1080p, 384 x 216 even on a 720p TV. */
private val HeroTrailerCompactSize = DpSize(288.dp, 162.dp)

@Composable
private fun HeroText(
    movie: Movie?,
    isNightPick: Boolean,
    language: Language,
    primaryFocus: FocusRequester,
    actions: HomeActions,
    modifier: Modifier,
    showSynopsis: Boolean = true,
) {
    Column(
        modifier = modifier.fillMaxHeight(),
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
                autoSize = ScaryType.DisplayFit,
                modifier = Modifier.semantics { heading() },
            )
            MovieMeta(movie)
            // The film page always has it; here it gives way to the rows while they have the focus.
            if (showSynopsis) {
                BasicText(
                    text = movie.synopsis(language),
                    style = ScaryType.Body.mixedLanguage(),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
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
    isWatched: (Movie) -> Boolean,
    showSetHome: Boolean,
    onMyListEmptied: () -> Unit,
) {
    // Holding OK on a My list card removes it: the card that takes its place gets the focus, so the next one can go too.
    val neighbourFocus = remember { FocusRequester() }
    var removedAt by remember { mutableIntStateOf(-1) }
    var refocusAt by remember { mutableIntStateOf(-1) }
    if (row is HomeRow.MyList) {
        val ids = row.movies.map { it.id }
        LaunchedEffect(ids) {
            if (removedAt < 0) return@LaunchedEffect
            refocusAt = removedAt.coerceAtMost(ids.lastIndex)
            removedAt = -1
            withFrameNanos { }
            neighbourFocus.tryRequestFocus()
            refocusAt = -1
        }
    }
    if (row is HomeRow.Moods) {
        MoodsRowView(row, rowState, restoreKey, restoreFocus, actions, isLocked, isWatched)
        return
    }
    if (row is HomeRow.TimePlan) {
        TimePlanRowView(row, rowState, restoreKey, restoreFocus, actions, isLocked, isWatched)
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
        is HomeRow.TimePlan -> stringResource(R.string.row_time)
        is HomeRow.BecauseYouLoved -> stringResource(R.string.row_because_loved, row.because.displayTitle(language))
    }
    Column {
        Row(
            modifier = Modifier.padding(horizontal = SidePadding),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            BasicText(text = title, style = ScaryType.RowTitle, modifier = Modifier.semantics { heading() })
            // The only row a movie leaves by holding OK: say so where it matters.
            if (row is HomeRow.MyList) BasicText(stringResource(R.string.my_list_hint), style = ScaryType.Caption)
        }
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
                        watched = isWatched(movie),
                        onClick = { actions.onOpenDetails(movie) },
                        onLongClick = { actions.onToggleMyList(movie) },
                        onFocused = { actions.onCardFocused(key, movie.id) },
                        focusRequester = restoreFocus.takeIf { key == restoreKey },
                    )
                }
                is HomeRow.MyList -> itemsIndexed(row.movies, key = { _, movie -> movie.id }) { index, movie ->
                    val key = "${row.key}:${movie.id}"
                    PosterCard(
                        movie = movie,
                        locked = isLocked(movie),
                        watched = isWatched(movie),
                        onClick = { actions.onOpenDetails(movie) },
                        onLongClick = {
                            if (row.movies.size == 1) onMyListEmptied() else removedAt = index
                            actions.onToggleMyList(movie)
                        },
                        onFocused = { actions.onCardFocused(key, movie.id) },
                        focusRequester = when {
                            index == refocusAt -> neighbourFocus
                            key == restoreKey -> restoreFocus
                            else -> null
                        },
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
                is HomeRow.Moods, is HomeRow.TimePlan -> Unit
                is HomeRow.Collection, is HomeRow.Seasonal, is HomeRow.BecauseYouLoved -> items(
                    when (row) {
                        is HomeRow.Collection -> row.movies
                        is HomeRow.BecauseYouLoved -> row.movies
                        else -> (row as HomeRow.Seasonal).movies
                    },
                    key = { it.id },
                ) { movie ->
                    val key = "${row.key}:${movie.id}"
                    PosterCard(
                        movie = movie,
                        locked = isLocked(movie),
                        watched = isWatched(movie),
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
 * "What do you feel like tonight?": a row of mood chips, and under it the decades. Choosing a mood, a decade or
 * both shows their movies in a row below; choosing the same chip again clears it.
 */
@Composable
private fun MoodsRowView(
    row: HomeRow.Moods,
    chipState: LazyListState,
    restoreKey: String?,
    restoreFocus: FocusRequester,
    actions: HomeActions,
    isLocked: (Movie) -> Boolean,
    isWatched: (Movie) -> Boolean,
) {
    val movieState = rememberLazyListState()
    LaunchedEffect(row.selected, row.decade) { movieState.scrollToItem(0) }
    val anyChosen = row.selected != null || row.decade != null
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
        if (row.decades.isNotEmpty()) {
            BasicText(
                text = stringResource(R.string.row_decades),
                style = ScaryType.Caption,
                modifier = Modifier.padding(horizontal = SidePadding),
            )
            LazyRow(
                contentPadding = PaddingValues(horizontal = SidePadding, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(row.decades, key = { it }) { decade ->
                    val key = "${row.key}:decade:$decade"
                    MoodChip(
                        label = stringResource(R.string.decade_label, decade),
                        selected = decade == row.decade,
                        onClick = { actions.onDecade(decade) },
                        onFocused = { actions.onCardFocused(key, null) },
                        focusRequester = restoreFocus.takeIf { key == restoreKey },
                    )
                }
            }
        }
        if (anyChosen && row.movies.isEmpty()) {
            // Family night or the parental limits can leave a mood with nothing to show; so can a mood and a decade
            // that share no movie.
            EmptyState(
                title = stringResource(R.string.mood_empty_title),
                body = stringResource(if (row.selected != null && row.decade != null) R.string.mood_empty_combo else R.string.mood_empty_body),
                modifier = Modifier.padding(horizontal = SidePadding, vertical = 8.dp),
            )
        }
        if (anyChosen && row.movies.isNotEmpty()) {
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
                        watched = isWatched(movie),
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

/**
 * "How much time do you have?": four chips, and for the chosen one the films that fit (up to 90 minutes, up to two
 * hours), or a lineup to watch in order (a double feature, a marathon) with its total length.
 */
@Composable
private fun TimePlanRowView(
    row: HomeRow.TimePlan,
    chipState: LazyListState,
    restoreKey: String?,
    restoreFocus: FocusRequester,
    actions: HomeActions,
    isLocked: (Movie) -> Boolean,
    isWatched: (Movie) -> Boolean,
) {
    val movieState = rememberLazyListState()
    LaunchedEffect(row.selected) { movieState.scrollToItem(0) }
    val plan = row.plan
    Column {
        BasicText(
            text = stringResource(R.string.row_time),
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
            items(TimeBudget.entries, key = { it.key }) { budget ->
                val key = "${row.key}:chip:${budget.key}"
                MoodChip(
                    label = stringResource(budget.labelRes()),
                    selected = budget == row.selected,
                    onClick = { actions.onTimeBudget(budget) },
                    onFocused = { actions.onCardFocused(key, null) },
                    focusRequester = restoreFocus.takeIf { key == restoreKey },
                )
            }
        }
        if (plan != null && plan.movies.isEmpty()) {
            EmptyState(
                title = stringResource(R.string.mood_empty_title),
                body = stringResource(R.string.time_empty),
                modifier = Modifier.padding(horizontal = SidePadding, vertical = 8.dp),
            )
        }
        if (plan != null && plan.movies.isNotEmpty()) {
            if (plan.budget.isLineup) {
                BasicText(
                    text = stringResource(R.string.time_lineup, hoursAndMinutes(plan.totalMinutes)),
                    style = ScaryType.Caption,
                    modifier = Modifier.padding(horizontal = SidePadding),
                )
            }
            LazyRow(
                state = movieState,
                contentPadding = PaddingValues(horizontal = SidePadding, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                itemsIndexed(plan.movies, key = { _, movie -> movie.id }) { index, movie ->
                    val key = "${row.key}:${movie.id}"
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        // A lineup is watched in order: the number says which comes when.
                        if (plan.budget.isLineup) {
                            BasicText(
                                text = stringResource(R.string.time_order, index + 1),
                                style = ScaryType.Label.copy(color = ScaryColors.Candle),
                            )
                        }
                        PosterCard(
                            movie = movie,
                            locked = isLocked(movie),
                            watched = isWatched(movie),
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
}

/** 292 minutes as "4:52". */
fun hoursAndMinutes(minutes: Int): String = "%d:%02d".format(java.util.Locale.ROOT, minutes / 60, minutes % 60)

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
