// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary.ui.components

import android.view.View
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.onInterceptKeyBeforeSoftKeyboard
import androidx.compose.ui.platform.LocalView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDirection
import io.github.diamondrubinny.scary.core.input.TextFieldExit

/**
 * A one-line text field the remote can leave. Compose's own field moves the focus out with the arrows only for remotes
 * that report themselves as a D-pad; many TV remotes report a keyboard, and the field then kept every arrow for its
 * cursor, so the buttons and tabs around it (Ask SCARY's "Chat", the microphone, "Send") could not be reached. Here up
 * and down always leave, left and right leave at the edge of the text (core TextFieldExit), and OK opens the keyboard.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ScaryTextField(
    value: String,
    onValueChange: (String) -> Unit,
    textStyle: TextStyle,
    cursorBrush: Brush,
    keyboardOptions: KeyboardOptions,
    keyboardActions: KeyboardActions,
    modifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource? = null,
    visualTransformation: VisualTransformation = VisualTransformation.None,
) {
    // The cursor's place is needed to know whether an arrow edits or leaves; the callers keep plain text.
    var kept by remember { mutableStateOf(TextFieldValue(value, TextRange(value.length))) }
    // Text set from outside (dictation, a cleared line) comes in with the cursor at its end.
    val field = if (kept.text == value) kept else TextFieldValue(value, TextRange(value.length))
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val rightToLeft = when (textStyle.textDirection) {
        TextDirection.Ltr -> false
        TextDirection.Rtl -> true
        else -> null
    }
    val view = LocalView.current

    /** Up and down leave the line; left and right leave at the edge of the text; OK opens the keyboard. */
    fun arrows(event: KeyEvent): Boolean {
        if (event.type != KeyEventType.KeyDown) return false
        val arrow = when (event.key) {
            Key.DirectionUp -> TextFieldExit.Arrow.UP
            Key.DirectionDown -> TextFieldExit.Arrow.DOWN
            Key.DirectionLeft -> TextFieldExit.Arrow.LEFT
            Key.DirectionRight -> TextFieldExit.Arrow.RIGHT
            Key.DirectionCenter -> {
                keyboard?.show()
                return true
            }
            else -> return false
        }
        val selection = field.selection
        if (!TextFieldExit.leaves(arrow, field.text, selection.min, selection.max, rightToLeft)) return false
        return focusManager.moveFocus(
            when (arrow) {
                TextFieldExit.Arrow.UP -> FocusDirection.Up
                TextFieldExit.Arrow.DOWN -> FocusDirection.Down
                TextFieldExit.Arrow.LEFT -> FocusDirection.Left
                TextFieldExit.Arrow.RIGHT -> FocusDirection.Right
            },
        )
    }

    BasicTextField(
        value = field,
        onValueChange = {
            kept = it
            if (it.text != value) onValueChange(it.text)
        },
        singleLine = true,
        textStyle = textStyle,
        cursorBrush = cursorBrush,
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        interactionSource = interactionSource,
        // The arrows, first before the on-screen keyboard and then as usual. A keyboard that is hidden but still
        // connected to the line can take them before the app (seen on emulators, and on some TVs), so up never left
        // the line; while the keyboard is on screen, the arrows are its own, to move between its keys.
        modifier = modifier
            .onInterceptKeyBeforeSoftKeyboard { event -> !keyboardShown(view) && arrows(event) }
            .onPreviewKeyEvent(::arrows),
    )
}

/** Whether the on-screen keyboard is showing now, from the window itself (not every screen follows the insets). */
private fun keyboardShown(view: View): Boolean =
    ViewCompat.getRootWindowInsets(view)?.isVisible(WindowInsetsCompat.Type.ime()) == true
