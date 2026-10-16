package io.github.diamondrubinny.scary.ui.fx

import android.graphics.Bitmap
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import io.github.diamondrubinny.scary.core.model.Movie
import io.github.diamondrubinny.scary.ui.components.movieTint
import io.github.diamondrubinny.scary.ui.theme.ScaryColors
import kotlinx.coroutines.delay
import kotlin.math.sin
import kotlin.random.Random

/** Tinted light behind the hero text, crossfading when the focused movie changes. */
@Composable
fun HeroBackdrop(movie: Movie?, modifier: Modifier = Modifier) {
    val target = if (movie == null) ScaryColors.Crypt else movieTint(movie, 0.24f)
    val tint by animateColorAsState(target, tween(700), label = "heroTint")
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Box(
        modifier
            .fillMaxSize()
            .drawWithCache {
                // The light sits opposite the text column: on the left in Hebrew, on the right in English.
                val center = Offset(size.width * if (rtl) 0.22f else 0.78f, size.height * 0.28f)
                val light = Brush.radialGradient(
                    colors = listOf(tint.copy(alpha = 0.75f), Color.Transparent),
                    center = center,
                    radius = size.width * 0.55f,
                )
                val floor = Brush.verticalGradient(
                    colors = listOf(Color.Transparent, ScaryColors.Void),
                    startY = size.height * 0.35f,
                    endY = size.height * 0.75f,
                )
                onDrawBehind {
                    drawRect(ScaryColors.Void)
                    drawRect(light)
                    drawRect(floor)
                }
            },
    )
}

private class Puff(val x: Float, val y: Float, val radius: Float, val speed: Float, val alpha: Float)

/**
 * Fog, film grain and a slowly breathing vignette. No flashes: the fastest change is the grain shifting
 * about eight times a second at 6% opacity, and the vignette cycle lasts seven seconds.
 * When [animate] is false everything is drawn once and stays still.
 */
@Composable
fun Atmosphere(animate: Boolean, modifier: Modifier = Modifier) {
    val puffs = remember {
        val random = Random(13)
        List(7) {
            Puff(
                x = random.nextFloat(),
                y = 0.3f + random.nextFloat() * 0.7f,
                radius = 0.18f + random.nextFloat() * 0.22f,
                speed = (0.010f + random.nextFloat() * 0.012f) * if (it % 2 == 0) 1f else -1f,
                alpha = 0.035f + random.nextFloat() * 0.04f,
            )
        }
    }
    val grain = remember { noiseTile(NOISE_TILE_PX) }
    val grainBrush = remember(grain) { ShaderBrush(ImageShader(grain, TileMode.Repeated, TileMode.Repeated)) }
    val time = remember { mutableLongStateOf(0L) }

    LaunchedEffect(animate) {
        if (!animate) return@LaunchedEffect
        val start = withFrameMillis { it } - time.longValue
        while (true) {
            // About 30 fps is plenty for fog and keeps weak TV GPUs cool.
            withFrameMillis { time.longValue = it - start }
            delay(FRAME_DELAY_MS)
        }
    }

    Canvas(modifier.fillMaxSize()) {
        val seconds = time.longValue / 1000f
        val w = size.width
        val h = size.height

        for (p in puffs) {
            val x = ((p.x + p.speed * seconds) % 1.6f + 1.6f) % 1.6f - 0.3f
            val center = Offset(x * w, p.y * h)
            val radius = p.radius * w
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFFC8C3B9).copy(alpha = p.alpha), Color.Transparent),
                    center = center,
                    radius = radius,
                ),
                radius = radius,
                center = center,
            )
        }

        val step = time.longValue / GRAIN_STEP_MS
        val tile = NOISE_TILE_PX.toFloat()
        val dx = (step * 37 % NOISE_TILE_PX).toFloat()
        val dy = (step * 53 % NOISE_TILE_PX).toFloat()
        translate(-dx, -dy) {
            drawRect(grainBrush, size = Size(w + tile, h + tile), alpha = 0.06f)
        }

        val breathe = 0.9f + 0.1f * sin(seconds * (2f * Math.PI.toFloat() / VIGNETTE_PERIOD_S))
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f * breathe)),
                center = Offset(w / 2, h * 0.4f),
                radius = maxOf(w, h) * 0.75f,
            ),
        )
    }
}

private fun noiseTile(sizePx: Int): ImageBitmap {
    val random = Random(7)
    val pixels = IntArray(sizePx * sizePx) {
        val v = random.nextInt(256)
        (random.nextInt(90) shl 24) or (v shl 16) or (v shl 8) or v
    }
    return Bitmap.createBitmap(pixels, sizePx, sizePx, Bitmap.Config.ARGB_8888).asImageBitmap()
}

private const val NOISE_TILE_PX = 128
private const val FRAME_DELAY_MS = 16L
private const val GRAIN_STEP_MS = 120L
private const val VIGNETTE_PERIOD_S = 7f
