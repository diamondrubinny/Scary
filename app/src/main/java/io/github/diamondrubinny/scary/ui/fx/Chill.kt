// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary.ui.fx

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import io.github.diamondrubinny.scary.ui.theme.LocalReduceMotion
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * A shiver: the element rises out of the dark, trembles, and its light dips twice like a dying bulb. With [repeat],
 * a smaller shiver comes back every 7 to 11 seconds. A new [key] starts it again.
 *
 * Safe for photosensitive viewers (WCAG 2.3.1): the dips only dim the element itself (to 55%, never to black) and
 * come at most twice a second. With effects off or "Remove animations" on, the element simply shows.
 */
@Composable
fun Modifier.chill(key: Any?, repeat: Boolean = true): Modifier {
    if (LocalReduceMotion.current) return this
    val alpha = remember(key) { Animatable(0f) }
    val dx = remember(key) { Animatable(0f) }
    val dy = remember(key) { Animatable(0f) }
    val scale = remember(key) { Animatable(REVEAL_SCALE) }
    val tremblePx = with(LocalDensity.current) { TREMBLE.toPx() }

    LaunchedEffect(key) {
        val random = Random(key.hashCode())
        coroutineScope {
            launch { alpha.animateTo(1f, tween(REVEAL_MS, easing = FastOutSlowInEasing)) }
            launch { scale.animateTo(1f, tween(REVEAL_MS, easing = FastOutSlowInEasing)) }
            launch { tremble(dx, dy, tremblePx, TREMBLE_MS, random) }
        }
        flicker(alpha)
        while (repeat) {
            delay(random.nextLong(REPEAT_MIN_MS, REPEAT_MAX_MS))
            coroutineScope {
                launch { tremble(dx, dy, tremblePx * AFTERSHOCK, AFTERSHOCK_MS, random) }
                launch {
                    delay(AFTERSHOCK_MS / 2)
                    dip(alpha, AFTERSHOCK_DIP)
                }
            }
        }
    }

    return graphicsLayer {
        this.alpha = alpha.value
        translationX = dx.value
        translationY = dy.value
        scaleX = scale.value
        scaleY = scale.value
    }
}

/** Small random jolts that fade out over [durationMs]. */
private suspend fun tremble(dx: Animatable<Float, *>, dy: Animatable<Float, *>, amplitude: Float, durationMs: Long, random: Random) {
    var elapsed = 0L
    while (elapsed < durationMs) {
        val strength = amplitude * (1f - elapsed.toFloat() / durationMs)
        dx.snapTo((random.nextFloat() * 2f - 1f) * strength)
        dy.snapTo((random.nextFloat() * 2f - 1f) * strength * VERTICAL_SHARE)
        delay(JOLT_MS)
        elapsed += JOLT_MS
    }
    dx.snapTo(0f)
    dy.snapTo(0f)
}

/** Two quick dips in brightness, half a second apart: a bulb about to go out. */
private suspend fun flicker(alpha: Animatable<Float, *>) {
    dip(alpha, FIRST_DIP)
    delay(DIP_GAP_MS)
    dip(alpha, SECOND_DIP)
}

private suspend fun dip(alpha: Animatable<Float, *>, to: Float) {
    alpha.snapTo(to)
    delay(DIP_MS)
    alpha.animateTo(1f, tween(DIP_RECOVER_MS))
}

private val TREMBLE = 3.dp
private const val VERTICAL_SHARE = 0.5f
private const val REVEAL_MS = 1_400
private const val REVEAL_SCALE = 0.96f
private const val TREMBLE_MS = 1_100L
private const val JOLT_MS = 45L
private const val FIRST_DIP = 0.55f
private const val SECOND_DIP = 0.7f
private const val DIP_MS = 70L
private const val DIP_RECOVER_MS = 120
private const val DIP_GAP_MS = 500L
private const val AFTERSHOCK = 0.5f
private const val AFTERSHOCK_MS = 350L
private const val AFTERSHOCK_DIP = 0.75f
private const val REPEAT_MIN_MS = 7_000L
private const val REPEAT_MAX_MS = 11_000L
