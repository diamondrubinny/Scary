// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary.ui.components

import android.util.Log
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.Key
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import io.github.diamondrubinny.scary.ui.theme.LocalReduceMotion
import io.github.diamondrubinny.scary.ui.theme.ScaryColors

/**
 * Base for everything the remote can land on: springs forward, gets a candlelight border and a warm glow when
 * focused, and dips slightly while OK is held. D-pad center and Enter trigger [onClick]; holding them triggers
 * [onLongClick] when given.
 *
 * OK is handled here rather than by combinedClickable: a TV remote sends a held key as a stream of repeated key-downs,
 * and combinedClickable alone did not turn that into a long click on the TV (the "hold OK to add to My list" gesture
 * did nothing). The click fires when OK is released, unless the hold already counted as a long press, and only for a
 * press that started here: a key still held from a long press on another element (whose dialog then took the focus)
 * never clicks this one.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FocusSurface(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(6.dp),
    focusedScale: Float = 1.08f,
    containerColor: Color = ScaryColors.Crypt,
    focusedContainerColor: Color = containerColor,
    borderColor: Color = Color.Transparent,
    focusedBorderColor: Color = ScaryColors.Candle,
    contentDescription: String? = null,
    onFocusChanged: (Boolean) -> Unit = {},
    onLongClick: (() -> Unit)? = null,
    content: @Composable BoxScope.(focused: Boolean) -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val pressed by interactionSource.collectIsPressedAsState()
    val latestOnFocusChanged by rememberUpdatedState(onFocusChanged)
    LaunchedEffect(focused) { latestOnFocusChanged(focused) }

    val keyHold = remember { KeyHold() }
    val latestOnClick by rememberUpdatedState(onClick)
    val latestOnLongClick by rememberUpdatedState(onLongClick)

    val reduceMotion = LocalReduceMotion.current
    val scale by animateFloatAsState(
        targetValue = when {
            pressed -> 1f + (focusedScale - 1f) * PRESSED_FRACTION
            focused -> focusedScale
            else -> 1f
        },
        // A small overshoot makes focus feel physical; it settles in about a quarter second.
        animationSpec = if (reduceMotion) snap() else spring(dampingRatio = SPRING_DAMPING, stiffness = Spring.StiffnessMedium),
        label = "focusScale",
    )
    val glow by animateFloatAsState(
        targetValue = if (focused) 1f else 0f,
        animationSpec = if (reduceMotion) snap() else tween(DURATION_MS),
        label = "focusGlow",
    )
    val container by animateColorAsState(
        targetValue = if (focused) focusedContainerColor else containerColor,
        animationSpec = if (reduceMotion) snap() else tween(DURATION_MS),
        label = "focusContainer",
    )

    Box(
        modifier = modifier
            .zIndex(if (focused) 1f else 0f)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .drawBehind {
                if (glow > 0f) {
                    val spread = 16.dp.toPx()
                    drawRoundRect(
                        brush = Brush.radialGradient(
                            colors = listOf(ScaryColors.Candle.copy(alpha = 0.32f * glow), Color.Transparent),
                            center = center,
                            radius = maxOf(size.width, size.height) * 0.8f,
                        ),
                        topLeft = Offset(-spread, -spread),
                        size = Size(size.width + spread * 2, size.height + spread * 2),
                        cornerRadius = CornerRadius(spread),
                    )
                }
            }
            .clip(shape)
            .background(container)
            .border(2.dp, if (focused) focusedBorderColor else borderColor, shape)
            .onPreviewKeyEvent { event ->
                keyHold.handle(event, interactionSource, contentDescription, onClick = { latestOnClick() }, onLongClick = latestOnLongClick)
            }
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onLongClick = onLongClick,
                onClick = onClick,
            )
            .then(
                if (contentDescription != null) {
                    Modifier.semantics { this.contentDescription = contentDescription }
                } else {
                    Modifier
                },
            ),
    ) {
        content(focused)
    }
}

/**
 * One OK press: a fresh key-down starts it (the element dips), a repeat of the held key (or the system's long-press
 * flag) fires the long press once when there is one, and the release fires the click only if this element saw the
 * press start and no long press came first. Every OK event is consumed, so combinedClickable never sees them and
 * nothing fires twice.
 */
private class KeyHold {
    private var press: PressInteraction.Press? = null
    private var started = false
    private var longPressed = false

    fun handle(
        event: KeyEvent,
        interactions: MutableInteractionSource,
        label: String?,
        onClick: () -> Unit,
        onLongClick: (() -> Unit)?,
    ): Boolean {
        if (event.key !in SELECT_KEYS) return false
        val native = event.nativeKeyEvent
        // Off unless "setprop log.tag.ScaryKeys DEBUG" (CI's UI tests): what each OK event did, to read a failed run.
        if (Log.isLoggable(KEY_LOG_TAG, Log.DEBUG)) {
            Log.d(KEY_LOG_TAG, "${native.action} repeat=${native.repeatCount} longPress=${native.isLongPress} started=$started long=$longPressed on=$label")
        }
        when (event.type) {
            KeyEventType.KeyDown -> {
                if (native.repeatCount == 0) {
                    started = true
                    longPressed = false
                    release(interactions)
                    press = PressInteraction.Press(Offset.Zero).also(interactions::tryEmit)
                } else if (started && !longPressed && onLongClick != null) {
                    longPressed = true
                    release(interactions)
                    onLongClick()
                }
            }
            KeyEventType.KeyUp -> {
                release(interactions)
                if (started && !longPressed && !native.isCanceled) onClick()
                started = false
                longPressed = false
            }
            else -> return false
        }
        return true
    }

    private fun release(interactions: MutableInteractionSource) {
        press?.let { interactions.tryEmit(PressInteraction.Release(it)) }
        press = null
    }
}

/** The keys that "click", as combinedClickable counts them on a TV: D-pad center and the Enter keys. */
private val SELECT_KEYS = setOf(Key.DirectionCenter, Key.Enter, Key.NumPadEnter)

private const val KEY_LOG_TAG = "ScaryKeys"

private const val DURATION_MS = 160
private const val SPRING_DAMPING = 0.62f
/** While pressed, the card keeps this share of its focus growth. */
private const val PRESSED_FRACTION = 0.35f
