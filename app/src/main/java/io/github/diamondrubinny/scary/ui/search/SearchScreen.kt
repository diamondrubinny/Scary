package io.github.diamondrubinny.scary.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import io.github.diamondrubinny.scary.R
import io.github.diamondrubinny.scary.core.catalog.Search
import io.github.diamondrubinny.scary.core.model.Movie
import io.github.diamondrubinny.scary.ui.components.PosterCard
import io.github.diamondrubinny.scary.ui.components.SearchIcon
import io.github.diamondrubinny.scary.ui.home.SidePadding
import io.github.diamondrubinny.scary.ui.home.labelRes
import io.github.diamondrubinny.scary.ui.theme.ScaryColors
import io.github.diamondrubinny.scary.ui.theme.ScaryType

/** Offline search over the bundled catalog. The TV keyboard (and its microphone, where present) handles input. */
@Composable
fun SearchScreen(
    movies: List<Movie>,
    query: String,
    onQueryChange: (String) -> Unit,
    onOpenMovie: (Movie) -> Unit,
) {
    val resources = LocalContext.current.resources
    val results = remember(movies, query, resources) {
        // Genre names in the current language, so "רוחות" or "ghosts" both work.
        Search.query(movies, query, labels = { movie ->
            (listOf(movie.genre.labelRes()) + movie.subgenres.map { it.labelRes() }).map(resources::getString)
        })
    }
    val fieldFocus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val fieldInteraction = remember { MutableInteractionSource() }
    val fieldFocused by fieldInteraction.collectIsFocusedAsState()

    LaunchedEffect(Unit) {
        withFrameNanos { }
        // Coming back from a movie keeps the query; only an empty search starts in the text field.
        if (query.isEmpty()) runCatching { fieldFocus.requestFocus() }
    }

    Column(Modifier.fillMaxSize().padding(top = 28.dp)) {
        val searchLabel = stringResource(R.string.search_title)
        Row(
            modifier = Modifier
                .padding(horizontal = SidePadding)
                .fillMaxWidth(0.7f)
                .background(ScaryColors.Crypt, RoundedCornerShape(10.dp))
                .border(2.dp, if (fieldFocused) ScaryColors.Candle else ScaryColors.Bone.copy(alpha = 0.15f), RoundedCornerShape(10.dp))
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SearchIcon(ScaryColors.Ash)
            Box(Modifier.weight(1f)) {
                if (query.isEmpty()) {
                    BasicText(stringResource(R.string.search_hint), style = ScaryType.Body.copy(color = ScaryColors.Ash))
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = ScaryType.BodyStrong.copy(fontSize = ScaryType.Title.fontSize),
                    cursorBrush = SolidColor(ScaryColors.Candle),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {
                        keyboard?.hide()
                        focusManager.moveFocus(FocusDirection.Down)
                    }),
                    interactionSource = fieldInteraction,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(fieldFocus)
                        .semantics { contentDescription = searchLabel },
                )
            }
        }

        val status = when {
            query.isBlank() -> stringResource(R.string.search_prompt, movies.size)
            results.isEmpty() -> stringResource(R.string.search_no_results, query.trim())
            else -> stringResource(R.string.search_count, results.size)
        }
        BasicText(
            text = status,
            style = ScaryType.Body.copy(color = ScaryColors.Ash),
            modifier = Modifier
                .padding(horizontal = SidePadding, vertical = 10.dp)
                .semantics { liveRegion = LiveRegionMode.Polite },
        )

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 104.dp),
            contentPadding = PaddingValues(horizontal = SidePadding, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
            modifier = Modifier.weight(1f),
        ) {
            items(results, key = { it.id }) { movie ->
                PosterCard(movie = movie, onClick = { onOpenMovie(movie) }, onFocused = {})
            }
        }
    }
}
