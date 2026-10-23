// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary.ui.details

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import io.github.diamondrubinny.scary.R
import io.github.diamondrubinny.scary.core.model.Language
import io.github.diamondrubinny.scary.ui.components.CreditLine
import io.github.diamondrubinny.scary.ui.components.rememberArtwork
import io.github.diamondrubinny.scary.ui.fx.chill
import io.github.diamondrubinny.scary.ui.home.LaunchingState
import io.github.diamondrubinny.scary.ui.theme.ScaryColors
import io.github.diamondrubinny.scary.ui.theme.ScaryType
import io.github.diamondrubinny.scary.ui.theme.mixedLanguage

/**
 * The moment between pressing Watch and the movie opening: the screen goes dark, the movie's logo (or its title)
 * shivers in the middle, and a line says which app is opening. It takes no focus and lasts about a second and a half.
 */
@Composable
fun LaunchOverlay(state: LaunchingState, language: Language, modifier: Modifier = Modifier) {
    val movie = state.movie
    val title = movie.displayTitle(language)
    val backdrop = rememberArtwork(movie.backgroundUrl)
    val logo = rememberArtwork(movie.logoUrl.takeIf { title == movie.title })
    var logoShown by remember(movie.id, logo) { mutableStateOf(false) }

    Box(modifier.fillMaxSize().background(ScaryColors.Void)) {
        if (backdrop != null) {
            AsyncImage(
                model = backdrop,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alpha = BACKDROP_ALPHA,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Column(
            modifier = Modifier.align(Alignment.Center).padding(horizontal = 64.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            Box(Modifier.chill(movie.id to logoShown, repeat = false), contentAlignment = Alignment.Center) {
                if (!logoShown) {
                    BasicText(
                        text = title,
                        // Its own direction for English titles, centred either way.
                        style = ScaryType.Display.mixedLanguage().copy(textAlign = TextAlign.Center),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (logo != null) {
                    AsyncImage(
                        model = logo,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        onSuccess = { logoShown = true },
                        onError = { logoShown = false },
                        modifier = Modifier.heightIn(max = 170.dp).widthIn(max = 620.dp),
                    )
                }
            }
            BasicText(
                text = stringResource(R.string.launch_opening, state.appLabel),
                style = ScaryType.BodyStrong.copy(color = ScaryColors.Candle),
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        // At the top: the bottom is where status messages appear ("Stremio is not installed…") and would cover it.
        CreditLine(Modifier.align(Alignment.TopCenter).padding(top = 32.dp), short = true)
    }
}

private const val BACKDROP_ALPHA = 0.22f
