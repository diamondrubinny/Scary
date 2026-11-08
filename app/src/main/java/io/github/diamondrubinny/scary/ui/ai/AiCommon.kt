// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary.ui.ai

import android.app.Activity
import android.content.Intent
import android.os.SystemClock
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import io.github.diamondrubinny.scary.R
import io.github.diamondrubinny.scary.ai.AiChip
import io.github.diamondrubinny.scary.ai.AiError
import io.github.diamondrubinny.scary.ai.AiTask
import io.github.diamondrubinny.scary.core.model.Language
import io.github.diamondrubinny.scary.ui.components.IconCircleButton
import io.github.diamondrubinny.scary.ui.components.MicIcon
import io.github.diamondrubinny.scary.ui.components.ScaryButton
import io.github.diamondrubinny.scary.ui.theme.ScaryColors
import io.github.diamondrubinny.scary.ui.theme.ScaryType
import kotlinx.coroutines.delay

fun AiChip.labelRes(): Int = when (this) {
    AiChip.LIKE_LOVED -> R.string.ai_chip_like_loved
    AiChip.SHORT -> R.string.ai_chip_short
    AiChip.GENTLE -> R.string.ai_chip_gentle
    AiChip.CLASSIC -> R.string.ai_chip_classic
    AiChip.SCARIEST -> R.string.ai_chip_scariest
}

/** What the chip asks, in the viewer's language: the request sent, as if typed. */
fun AiChip.promptRes(): Int = when (this) {
    AiChip.LIKE_LOVED -> R.string.ai_prompt_like_loved
    AiChip.SHORT -> R.string.ai_prompt_short
    AiChip.GENTLE -> R.string.ai_prompt_gentle
    AiChip.CLASSIC -> R.string.ai_prompt_classic
    AiChip.SCARIEST -> R.string.ai_prompt_scariest
}

/** Test tag of the line that says "Thinking…" or what went wrong. */
const val AI_STATUS_TAG = "aiStatus"

/**
 * "Thinking…", or what went wrong and what to do: with the free service's wait counted down. Read out as it
 * changes. [onRetry] when trying again makes sense; [onSettings] when the fix is in settings.
 */
@Composable
fun AiStatus(task: AiTask<*>, onRetry: (() -> Unit)?, onSettings: (() -> Unit)?, modifier: Modifier = Modifier) {
    if (task !is AiTask.Loading && task !is AiTask.Failed) return
    Column(modifier.testTag(AI_STATUS_TAG), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        val text = when (task) {
            is AiTask.Loading -> stringResource(R.string.ai_thinking)
            is AiTask.Failed -> errorText(task)
            else -> ""
        }
        BasicText(
            text,
            style = ScaryType.BodyStrong.copy(color = if (task is AiTask.Failed) ScaryColors.Candle else ScaryColors.Ash),
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
        if (task is AiTask.Failed) {
            val fixInSettings = task.error in setOf(AiError.NEEDS_KEY, AiError.BAD_KEY, AiError.NO_MODEL, AiError.OFF, AiError.NO_CREDIT)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (onRetry != null && task.error !in setOf(AiError.OFF, AiError.CHAT_BLOCKED, AiError.NO_FILMS)) {
                    ScaryButton(stringResource(R.string.ai_retry), onClick = onRetry, primary = !fixInSettings)
                }
                if (onSettings != null && (fixInSettings || task.error == AiError.WAIT)) {
                    ScaryButton(stringResource(R.string.ai_open_settings), onClick = onSettings, primary = fixInSettings)
                }
            }
        }
    }
}

@Composable
private fun errorText(task: AiTask.Failed): String = when (task.error) {
    AiError.WAIT -> {
        var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
        LaunchedEffect(task.retryAtMillis) {
            while (now < task.retryAtMillis) {
                delay(1000)
                now = SystemClock.elapsedRealtime()
            }
        }
        val seconds = ((task.retryAtMillis - now + 999) / 1000).toInt()
        if (seconds > 0) stringResource(R.string.ai_error_wait, seconds) else stringResource(R.string.ai_error_wait_over)
    }
    AiError.NEEDS_KEY -> stringResource(R.string.ai_error_needs_key)
    AiError.BAD_KEY -> stringResource(R.string.ai_error_bad_key)
    AiError.NO_CREDIT -> stringResource(R.string.ai_error_no_credit)
    AiError.NO_MODEL -> stringResource(R.string.ai_error_no_model)
    AiError.UNREACHABLE -> stringResource(R.string.ai_error_unreachable)
    AiError.FAILED -> stringResource(R.string.ai_error_failed)
    AiError.NOT_UNDERSTOOD -> stringResource(R.string.ai_error_not_understood)
    AiError.OFF -> stringResource(R.string.ai_error_off)
    AiError.CHAT_BLOCKED -> stringResource(R.string.ai_error_chat_blocked)
    AiError.NO_FILMS -> stringResource(R.string.ai_error_no_films)
}

/**
 * A line to type (or dictate, with the TV's own speech recognizer) and a button to send it. [label] is read out;
 * [hint] shows while it is empty.
 */
@Composable
fun AiInput(
    value: String,
    onValueChange: (String) -> Unit,
    onSubmit: () -> Unit,
    label: String,
    hint: String,
    submitLabel: String,
    language: Language,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val voicePrompt = stringResource(R.string.voice_prompt)
    val speechIntent = remember(language, voicePrompt) {
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, language.tmdbTag)
            .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            .putExtra(RecognizerIntent.EXTRA_PROMPT, voicePrompt)
    }
    val voiceAvailable = remember(speechIntent) { speechIntent.resolveActivity(context.packageManager) != null }
    val voice = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.trim()
        if (result.resultCode == Activity.RESULT_OK && !spoken.isNullOrEmpty()) {
            onValueChange(spoken)
            onSubmit()
        }
    }
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            Modifier
                .weight(1f)
                .background(ScaryColors.Crypt, RoundedCornerShape(10.dp))
                .border(2.dp, if (focused) ScaryColors.Candle else ScaryColors.Bone.copy(alpha = 0.15f), RoundedCornerShape(10.dp))
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            if (value.isEmpty()) BasicText(hint, style = ScaryType.Body.copy(color = ScaryColors.Ash), maxLines = 1)
            BasicTextField(
                value = value,
                onValueChange = { onValueChange(it.take(MAX_INPUT)) },
                singleLine = true,
                textStyle = ScaryType.BodyStrong,
                cursorBrush = SolidColor(ScaryColors.Candle),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = {
                    keyboard?.hide()
                    onSubmit()
                }),
                interactionSource = interaction,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .semantics { contentDescription = label },
            )
        }
        if (voiceAvailable) {
            IconCircleButton(contentDescription = stringResource(R.string.voice_search), onClick = { runCatching { voice.launch(speechIntent) } }) { MicIcon(it) }
        }
        ScaryButton(submitLabel, onClick = {
            keyboard?.hide()
            onSubmit()
        }, primary = true, modifier = Modifier.width(IntrinsicSubmitWidth))
    }
}

private val IntrinsicSubmitWidth = 120.dp
private const val MAX_INPUT = 300
