// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary.ui.ai

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import io.github.diamondrubinny.scary.R
import io.github.diamondrubinny.scary.core.ai.AiProvider
import io.github.diamondrubinny.scary.data.AiRepository
import io.github.diamondrubinny.scary.ui.components.FocusSurface
import io.github.diamondrubinny.scary.ui.components.ScaryButton
import io.github.diamondrubinny.scary.ui.theme.ScaryColors
import io.github.diamondrubinny.scary.ui.theme.ScaryType
import kotlinx.coroutines.launch

/** Test tags of the AI dialogs. */
const val AI_CONSENT_TAG = "aiConsent"
const val AI_PROVIDER_TAG = "aiProviders"
const val AI_KEY_TAG = "aiKey"
fun aiProviderTag(provider: AiProvider): String = "aiProvider:${provider.key}"

/** Where each service's terms and privacy policy are, said in the consent question. */
private fun AiProvider.termsHost(): String = when (this) {
    AiProvider.POLLINATIONS_FREE, AiProvider.POLLINATIONS -> "pollinations.ai/terms"
    AiProvider.OPENAI -> "openai.com/policies"
    AiProvider.ANTHROPIC -> "anthropic.com/legal"
    AiProvider.GEMINI -> "ai.google.dev/gemini-api/terms"
    AiProvider.GROQ -> "groq.com/terms-of-use"
    AiProvider.OPENROUTER -> "openrouter.ai/terms"
    AiProvider.MISTRAL -> "mistral.ai/terms"
    AiProvider.DEEPSEEK -> "deepseek.com"
    AiProvider.CUSTOM -> "—"
}

@Composable
private fun DialogBox(tag: String, width: Int = 600, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .width(width.dp)
            .background(ScaryColors.Crypt, RoundedCornerShape(12.dp))
            .border(1.dp, ScaryColors.Candle.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
            .testTag(tag),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) { content() }
}

/**
 * Asked once per service, before anything is sent to it: where the request goes, what goes (and what never does), and
 * where its terms are. "Use my own key" leads to the AI settings instead.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AiConsentDialog(provider: AiProvider, onAgree: () -> Unit, onLater: () -> Unit, onOwnKey: () -> Unit) {
    val agreeFocus = remember { FocusRequester() }
    Dialog(onDismissRequest = onLater) {
        LaunchedEffect(Unit) {
            withFrameNanos { }
            runCatching { agreeFocus.requestFocus() }
        }
        DialogBox(AI_CONSENT_TAG) {
            BasicText(stringResource(R.string.ai_consent_title, provider.displayName), style = ScaryType.Title, modifier = Modifier.semantics { heading() })
            BasicText(stringResource(R.string.ai_consent_body, provider.displayName, provider.termsHost()), style = ScaryType.Body)
            BasicText(stringResource(R.string.ai_consent_private), style = ScaryType.Body)
            if (provider.free) BasicText(stringResource(R.string.ai_consent_free_note), style = ScaryType.Caption)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ScaryButton(stringResource(R.string.ai_consent_agree), onClick = onAgree, primary = true, focusRequester = agreeFocus)
                ScaryButton(stringResource(R.string.ai_consent_later), onClick = onLater)
                if (provider.free) ScaryButton(stringResource(R.string.ai_consent_own_key), onClick = onOwnKey)
            }
        }
    }
}

/** The services, each with its state: free, key saved, or needs a key. The chosen one is marked. */
@Composable
fun AiProviderDialog(current: AiProvider, hasKey: (AiProvider) -> Boolean, onPick: (AiProvider) -> Unit, onDismiss: () -> Unit) {
    val currentFocus = remember { FocusRequester() }
    Dialog(onDismissRequest = onDismiss) {
        LaunchedEffect(Unit) {
            withFrameNanos { }
            runCatching { currentFocus.requestFocus() }
        }
        DialogBox(AI_PROVIDER_TAG) {
            BasicText(stringResource(R.string.ai_provider_title), style = ScaryType.Title, modifier = Modifier.semantics { heading() })
            AiProvider.entries.forEach { provider ->
                val status = when {
                    provider.free -> stringResource(R.string.ai_provider_free)
                    hasKey(provider) -> stringResource(R.string.ai_provider_has_key)
                    provider == AiProvider.CUSTOM -> stringResource(R.string.ai_provider_custom)
                    else -> stringResource(R.string.ai_provider_needs_key)
                }
                ChoiceRow(
                    label = provider.displayName,
                    value = status,
                    selected = provider == current,
                    onClick = { onPick(provider) },
                    modifier = Modifier.testTag(aiProviderTag(provider)),
                    focusRequester = currentFocus.takeIf { provider == current },
                )
            }
        }
    }
}

/**
 * A key for [provider]: typed here (hidden as it is typed, kept left to right), or on the phone. Checked with the
 * service before it is kept; the outcome is read out.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AiKeyDialog(
    provider: AiProvider,
    keyMask: String?,
    onSave: suspend (String) -> AiRepository.KeyResult,
    onDelete: () -> Unit,
    onUsePhone: () -> Unit,
    onDismiss: () -> Unit,
) {
    var input by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var outcome by remember { mutableStateOf<AiRepository.KeyResult?>(null) }
    val scope = rememberCoroutineScope()
    val fieldFocus = remember { FocusRequester() }
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val label = stringResource(R.string.ai_key_title, provider.displayName)
    val save = {
        if (input.isNotBlank() && !busy) {
            busy = true
            scope.launch {
                val result = onSave(input)
                busy = false
                outcome = result
                if (result == AiRepository.KeyResult.SAVED) onDismiss()
            }
        }
    }
    Dialog(onDismissRequest = onDismiss) {
        LaunchedEffect(Unit) {
            withFrameNanos { }
            runCatching { fieldFocus.requestFocus() }
        }
        DialogBox(AI_KEY_TAG, width = 560) {
            BasicText(label, style = ScaryType.Title, modifier = Modifier.semantics { heading() })
            provider.keyPage?.let { BasicText(stringResource(R.string.ai_key_where, it), style = ScaryType.Body) }
            BasicText(stringResource(R.string.ai_key_billing), style = ScaryType.Caption)
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(ScaryColors.Void, RoundedCornerShape(8.dp))
                    .border(2.dp, if (focused) ScaryColors.Candle else ScaryColors.Bone.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                if (input.isEmpty()) BasicText(keyMask ?: stringResource(R.string.ai_key_hint), style = ScaryType.Body.copy(color = ScaryColors.Ash))
                BasicTextField(
                    value = input,
                    onValueChange = { input = it.trim().take(400) },
                    singleLine = true,
                    textStyle = ScaryType.BodyStrong.copy(textDirection = TextDirection.Ltr),
                    cursorBrush = SolidColor(ScaryColors.Candle),
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { save() }),
                    interactionSource = interaction,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(fieldFocus)
                        .semantics { contentDescription = label },
                )
            }
            val message = when {
                busy -> stringResource(R.string.ai_key_checking)
                outcome == AiRepository.KeyResult.INVALID -> stringResource(R.string.ai_key_invalid)
                outcome == AiRepository.KeyResult.REJECTED -> stringResource(R.string.ai_key_rejected)
                outcome == AiRepository.KeyResult.UNREACHABLE -> stringResource(R.string.ai_key_unreachable)
                outcome == AiRepository.KeyResult.STORAGE -> stringResource(R.string.ai_key_storage)
                else -> null
            }
            if (message != null) {
                BasicText(message, style = ScaryType.BodyStrong.copy(color = ScaryColors.Candle), modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ScaryButton(stringResource(R.string.ai_key_save), onClick = { save() }, primary = true)
                ScaryButton(stringResource(R.string.ai_key_phone), onClick = onUsePhone)
                if (keyMask != null) ScaryButton(stringResource(R.string.ai_key_delete), onClick = onDelete)
            }
        }
    }
}

/** The custom service's HTTPS address. */
@Composable
fun AiUrlDialog(current: String, onSave: (String) -> Boolean, onDismiss: () -> Unit) {
    var input by remember { mutableStateOf(current) }
    var invalid by remember { mutableStateOf(false) }
    val fieldFocus = remember { FocusRequester() }
    val label = stringResource(R.string.ai_url_title)
    Dialog(onDismissRequest = onDismiss) {
        LaunchedEffect(Unit) {
            withFrameNanos { }
            runCatching { fieldFocus.requestFocus() }
        }
        DialogBox("aiUrl", width = 600) {
            BasicText(label, style = ScaryType.Title, modifier = Modifier.semantics { heading() })
            BasicText(stringResource(R.string.ai_url_body), style = ScaryType.Body)
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(ScaryColors.Void, RoundedCornerShape(8.dp))
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                if (input.isEmpty()) BasicText("https://…/v1", style = ScaryType.Body.copy(color = ScaryColors.Ash))
                BasicTextField(
                    value = input,
                    onValueChange = { input = it.trim().take(300) },
                    singleLine = true,
                    textStyle = ScaryType.BodyStrong.copy(textDirection = TextDirection.Ltr),
                    cursorBrush = SolidColor(ScaryColors.Candle),
                    visualTransformation = VisualTransformation.None,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { if (onSave(input)) onDismiss() else invalid = true }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(fieldFocus)
                        .semantics { contentDescription = label },
                )
            }
            if (invalid) BasicText(stringResource(R.string.ai_url_invalid), style = ScaryType.BodyStrong.copy(color = ScaryColors.Candle), modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            ScaryButton(stringResource(R.string.ai_key_save), onClick = { if (onSave(input)) onDismiss() else invalid = true }, primary = true)
        }
    }
}

/** The service's models, as it lists them, with "Automatic" (SCARY picks a quick one) first. */
@Composable
fun AiModelDialog(provider: AiProvider, current: String?, load: suspend () -> List<String>, onPick: (String?) -> Unit, onDismiss: () -> Unit) {
    val models by produceState<List<String>?>(null, provider) { value = load() }
    val firstFocus = remember { FocusRequester() }
    Dialog(onDismissRequest = onDismiss) {
        LaunchedEffect(Unit) {
            withFrameNanos { }
            runCatching { firstFocus.requestFocus() }
        }
        DialogBox("aiModels", width = 640) {
            BasicText(stringResource(R.string.ai_model_title), style = ScaryType.Title, modifier = Modifier.semantics { heading() })
            ChoiceRow(stringResource(R.string.ai_model_auto), null, current == null, { onPick(null) }, focusRequester = firstFocus)
            when (val list = models) {
                null -> BasicText(stringResource(R.string.ai_model_loading), style = ScaryType.Body, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                else -> if (list.isEmpty()) {
                    BasicText(stringResource(R.string.ai_model_none), style = ScaryType.Body)
                } else {
                    LazyColumn(Modifier.heightIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(list, key = { it }) { model -> ChoiceRow(model, null, model == current, { onPick(model) }) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChoiceRow(
    label: String,
    value: String?,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
) {
    FocusSurface(
        onClick = onClick,
        modifier = (if (focusRequester != null) modifier.focusRequester(focusRequester) else modifier).fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        focusedScale = 1.02f,
        containerColor = if (selected) ScaryColors.Blood.copy(alpha = 0.5f) else ScaryColors.Void,
        focusedContainerColor = ScaryColors.Void,
        borderColor = if (selected) ScaryColors.Candle else ScaryColors.Bone.copy(alpha = 0.1f),
        contentDescription = listOfNotNull(label, value, if (selected) "✓" else null).joinToString(", "),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            BasicText((if (selected) "✓ " else "") + label, style = ScaryType.BodyStrong.copy(textDirection = TextDirection.Ltr), modifier = Modifier.weight(1f))
            if (value != null) BasicText(value, style = ScaryType.Label.copy(color = ScaryColors.Candle))
        }
    }
}
