// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary.ui.fx

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import coil3.compose.AsyncImage
import io.github.diamondrubinny.scary.core.model.Movie
import io.github.diamondrubinny.scary.ui.components.rememberArtwork
import io.github.diamondrubinny.scary.ui.theme.LocalReduceMotion
import io.github.diamondrubinny.scary.ui.theme.ScaryColors
import kotlinx.coroutines.delay

/**
 * The selected movie's background art behind the whole screen, darkened on the text side and toward the rows
 * so text stays readable. Waits a moment before switching, so fast scrolling does not decode every image.
 * The art drifts slowly (a Ken Burns zoom and pan) unless motion is reduced.
 */
@Composable
fun MovieBackdrop(movie: Movie?, modifier: Modifier = Modifier) {
    var shown by remember { mutableStateOf(movie) }
    LaunchedEffect(movie) {
        // Same movie with new data (artwork just arrived): update at once. Another movie: wait for scrolling to settle.
        if (shown?.id != movie?.id) delay(SWITCH_DELAY_MS)
        shown = movie
    }
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val drift = kenBurns(enabled = !LocalReduceMotion.current)

    Box(modifier.fillMaxSize()) {
        HeroBackdrop(shown)
        // Some movies have no background art on Cinemeta; their poster still makes a moody backdrop.
        Crossfade(targetState = shown?.let { it.backgroundUrl ?: it.posterUrl }, animationSpec = tween(FADE_MS), label = "backdrop") { url ->
            rememberArtwork(url)?.let { request ->
                AsyncImage(
                    model = request,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    alpha = 0.9f,
                    modifier = Modifier
                        .fillMaxSize()
                        // Read in the draw phase only, so the drift never recomposes the screen.
                        .graphicsLayer {
                            val t = drift()
                            val scale = 1f + KEN_BURNS_ZOOM * t
                            scaleX = scale
                            scaleY = scale
                            // Drift away from the text side, toward the open part of the picture.
                            translationX = (if (rtl) -1f else 1f) * size.width * KEN_BURNS_PAN * t
                        },
                )
            }
        }
        Box(
            Modifier
                .fillMaxSize()
                .drawBehind {
                    val textSide = Brush.horizontalGradient(
                        colors = if (rtl) {
                            listOf(Color.Transparent, ScaryColors.Void.copy(alpha = 0.55f), ScaryColors.Void.copy(alpha = 0.95f))
                        } else {
                            listOf(ScaryColors.Void.copy(alpha = 0.95f), ScaryColors.Void.copy(alpha = 0.55f), Color.Transparent)
                        },
                    )
                    val bottom = Brush.verticalGradient(
                        colors = listOf(Color.Transparent, ScaryColors.Void.copy(alpha = 0.9f), ScaryColors.Void),
                        startY = size.height * 0.35f,
                        endY = size.height * 0.8f,
                    )
                    val top = Brush.verticalGradient(
                        colors = listOf(ScaryColors.Void.copy(alpha = 0.7f), Color.Transparent),
                        endY = size.height * 0.2f,
                    )
                    drawRect(textSide)
                    drawRect(bottom)
                    drawRect(top)
                },
        )
    }
}

/** 0 to 1 and back, slowly; always 0 when motion is reduced. */
@Composable
private fun kenBurns(enabled: Boolean): () -> Float {
    if (!enabled) return { 0f }
    val transition = rememberInfiniteTransition(label = "kenBurns")
    val progress = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(KEN_BURNS_MS, easing = LinearEasing), RepeatMode.Reverse),
        label = "kenBurnsProgress",
    )
    return { progress.value }
}

private const val SWITCH_DELAY_MS = 350L
private const val KEN_BURNS_MS = 24_000
private const val KEN_BURNS_ZOOM = 0.06f
private const val KEN_BURNS_PAN = 0.015f
private const val FADE_MS = 700
