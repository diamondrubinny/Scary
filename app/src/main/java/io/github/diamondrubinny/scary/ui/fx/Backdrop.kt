package io.github.diamondrubinny.scary.ui.fx

import androidx.compose.animation.Crossfade
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import coil.compose.AsyncImage
import io.github.diamondrubinny.scary.core.model.Movie
import io.github.diamondrubinny.scary.ui.components.rememberArtwork
import io.github.diamondrubinny.scary.ui.theme.ScaryColors
import kotlinx.coroutines.delay

/**
 * The selected movie's background art behind the whole screen, darkened on the text side and toward the rows
 * so text stays readable. Waits a moment before switching, so fast scrolling does not decode every image.
 */
@Composable
fun MovieBackdrop(movie: Movie?, modifier: Modifier = Modifier) {
    var shown by remember { mutableStateOf(movie) }
    LaunchedEffect(movie?.id) {
        if (shown?.id != movie?.id) {
            delay(SWITCH_DELAY_MS)
            shown = movie
        }
    }
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl

    Box(modifier.fillMaxSize()) {
        HeroBackdrop(shown)
        Crossfade(targetState = shown?.backgroundUrl, animationSpec = tween(FADE_MS), label = "backdrop") { url ->
            rememberArtwork(url)?.let { request ->
                AsyncImage(
                    model = request,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    alpha = 0.9f,
                    modifier = Modifier.fillMaxSize(),
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

private const val SWITCH_DELAY_MS = 350L
private const val FADE_MS = 700
