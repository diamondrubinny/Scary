package io.github.diamondrubinny.scary.ui.pin

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.utf16CodePoint
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import io.github.diamondrubinny.scary.R
import io.github.diamondrubinny.scary.core.security.PinHasher
import io.github.diamondrubinny.scary.ui.components.ScaryButton
import io.github.diamondrubinny.scary.ui.home.PinDialogState
import io.github.diamondrubinny.scary.ui.home.PinStep
import io.github.diamondrubinny.scary.ui.theme.ScaryColors
import io.github.diamondrubinny.scary.ui.theme.ScaryType
import kotlinx.coroutines.delay

/**
 * PIN entry built for a remote: an on-screen keypad navigated with the arrows,
 * plus the number keys when the remote or a keyboard has them.
 */
@Composable
fun PinDialog(state: PinDialogState, onSubmit: (String) -> Unit, onDismiss: () -> Unit) {
    var entered by remember(state.step) { mutableStateOf("") }
    val firstKey = remember { FocusRequester() }

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(state.lockedUntilMillis) {
        while (System.currentTimeMillis() < state.lockedUntilMillis) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
        now = System.currentTimeMillis()
    }
    val waitSeconds = ((state.lockedUntilMillis - now + 999) / 1000).coerceAtLeast(0L).toInt()

    fun type(digit: Char) {
        if (entered.length < PinHasher.MAX_LENGTH) entered += digit
    }
    fun submit() {
        if (state.busy || waitSeconds > 0) return
        onSubmit(entered)
        entered = ""
    }

    Dialog(onDismissRequest = onDismiss) {
        LaunchedEffect(Unit) {
            withFrameNanos { }
            runCatching { firstKey.requestFocus() }
        }
        Column(
            modifier = Modifier
                .width(340.dp)
                .background(ScaryColors.Crypt, RoundedCornerShape(12.dp))
                .border(1.dp, ScaryColors.Blood, RoundedCornerShape(12.dp))
                .padding(22.dp)
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyUp) return@onPreviewKeyEvent false
                    val char = event.utf16CodePoint.toChar()
                    when {
                        char in '0'..'9' -> { type(char); true }
                        event.key == Key.Backspace || event.key == Key.Delete -> { entered = entered.dropLast(1); true }
                        else -> false
                    }
                },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            BasicText(
                text = stringResource(
                    when (state.step) {
                        is PinStep.Verify -> R.string.pin_title_verify
                        PinStep.Create -> R.string.pin_title_create
                        is PinStep.Confirm -> R.string.pin_title_confirm
                    },
                ),
                style = ScaryType.BodyStrong,
                modifier = Modifier.semantics { heading() },
            )

            val enteredDescription = stringResource(R.string.pin_entered_description, entered.length)
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.clearAndSetSemantics { contentDescription = enteredDescription },
            ) {
                repeat(maxOf(PinHasher.MIN_LENGTH, entered.length)) { i ->
                    Box(
                        Modifier
                            .size(14.dp)
                            .background(if (i < entered.length) ScaryColors.Candle else ScaryColors.BloodDim, CircleShape),
                    )
                }
            }

            val message = when {
                state.busy -> stringResource(R.string.pin_checking)
                waitSeconds > 0 -> stringResource(R.string.pin_error_wait, waitSeconds)
                state.error != null && state.error != R.string.pin_error_wait -> stringResource(state.error)
                else -> null
            }
            BasicText(
                text = message.orEmpty(),
                style = ScaryType.Body.copy(color = ScaryColors.Candle),
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
            )

            // A phone-style keypad reads left to right in every language.
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("123", "456", "789").forEachIndexed { rowIndex, row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEachIndexed { col, digit ->
                                KeyButton(
                                    label = digit.toString(),
                                    onClick = { type(digit) },
                                    modifier = if (rowIndex == 0 && col == 0) Modifier.focusRequester(firstKey) else Modifier,
                                )
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        KeyButton(stringResource(R.string.pin_delete), onClick = { entered = entered.dropLast(1) })
                        KeyButton("0", onClick = { type('0') })
                        KeyButton(stringResource(R.string.pin_ok), onClick = ::submit, primary = true)
                    }
                }
            }
            ScaryButton(stringResource(R.string.pin_cancel), onClick = onDismiss, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun KeyButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, primary: Boolean = false) {
    ScaryButton(text = label, onClick = onClick, primary = primary, modifier = modifier.width(88.dp))
}
