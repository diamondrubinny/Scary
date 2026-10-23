// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary.ui.settings

import io.github.diamondrubinny.scary.ui.components.CreditLine
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.diamondrubinny.scary.R
import io.github.diamondrubinny.scary.core.model.Language
import io.github.diamondrubinny.scary.ui.components.FearMeter
import io.github.diamondrubinny.scary.ui.components.FocusSurface
import io.github.diamondrubinny.scary.ui.home.DocumentKind
import io.github.diamondrubinny.scary.ui.home.SidePadding
import io.github.diamondrubinny.scary.ui.theme.ScaryColors
import io.github.diamondrubinny.scary.ui.theme.ScaryType
import io.github.diamondrubinny.scary.ui.theme.mixedLanguage

class SettingsActions(
    val onToggleLanguage: () -> Unit,
    val onToggleEffects: () -> Unit,
    val onCycleNightFear: () -> Unit,
    val onToggleSeasonalRows: () -> Unit,
    val onSystemSettings: () -> Unit,
    val onToggleSounds: () -> Unit,
    val onSetupPin: () -> Unit,
    val onCycleLockLevel: () -> Unit,
    val onTurnOffLock: () -> Unit,
    val onToggleFamilyNight: () -> Unit,
    val onToggleOnline: () -> Unit,
    val onRequestDefaultHome: () -> Unit,
    val onOpenAccessibility: () -> Unit,
    val onOpenTmdb: () -> Unit,
    val onOpenScreensaver: () -> Unit,
    val onOpenPrivacy: () -> Unit,
    val onOpenAccessibilityStatement: () -> Unit,
)

@Composable
fun SettingsScreen(
    language: Language,
    effectsEnabled: Boolean,
    nightMaxFear: Int,
    soundsEnabled: Boolean,
    parentalEnabled: Boolean,
    lockFromFear: Int,
    familyNight: Boolean,
    seasonalRows: Boolean,
    onlineEnabled: Boolean,
    isDefaultHome: Boolean,
    homeButtonServiceOn: Boolean,
    tmdbKeyMask: String?,
    versionName: String,
    actions: SettingsActions,
    returningFrom: DocumentKind? = null,
) {
    val firstFocus = remember { FocusRequester() }
    val privacyFocus = remember { FocusRequester() }
    val statementFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        // Back from a document lands on the row that opened it, not at the top.
        val target = when (returningFrom) {
            DocumentKind.Privacy -> privacyFocus
            DocumentKind.Accessibility -> statementFocus
            null -> firstFocus
        }
        runCatching { target.requestFocus() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = SidePadding, vertical = 28.dp)
            .fillMaxWidth(0.7f),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        BasicText(
            stringResource(R.string.settings_title),
            style = ScaryType.Display.copy(fontSize = ScaryType.Display.fontSize * 0.7f),
            modifier = Modifier.padding(bottom = 8.dp).semantics { heading() },
        )
        BasicText(
            stringResource(R.string.settings_home_section),
            style = ScaryType.RowTitle,
            modifier = Modifier.semantics { heading() },
        )
        SettingRow(
            label = stringResource(R.string.settings_default_home),
            value = stringResource(if (isDefaultHome) R.string.value_home_yes else R.string.value_home_no),
            onClick = actions.onRequestDefaultHome,
            modifier = Modifier.focusRequester(firstFocus),
        )
        SettingRow(
            label = stringResource(R.string.settings_home_button),
            value = stringResource(if (homeButtonServiceOn) R.string.value_home_button_on else R.string.value_home_button_off),
            onClick = actions.onOpenAccessibility,
        )
        BasicText(stringResource(R.string.settings_home_note), style = ScaryType.Caption)
        SettingRow(
            label = stringResource(R.string.settings_online),
            value = stringResource(if (onlineEnabled) R.string.value_on else R.string.value_off),
            onClick = actions.onToggleOnline,
        )
        BasicText(stringResource(R.string.settings_online_note), style = ScaryType.Caption)
        SettingRow(
            label = stringResource(R.string.settings_tmdb),
            value = tmdbKeyMask ?: stringResource(R.string.value_tmdb_none),
            onClick = actions.onOpenTmdb,
        )
        // The credit is English only; keep its punctuation in place inside a right-to-left layout.
        BasicText(stringResource(R.string.tmdb_attribution), style = ScaryType.Caption.mixedLanguage())
        SettingRow(
            label = stringResource(R.string.settings_language),
            value = language.nativeName,
            onClick = actions.onToggleLanguage,
        )
        SettingRow(
            label = stringResource(R.string.settings_effects),
            value = stringResource(if (effectsEnabled) R.string.value_on else R.string.value_off),
            onClick = actions.onToggleEffects,
        )
        SettingRow(
            label = stringResource(R.string.settings_night_fear),
            value = stringResource(R.string.settings_night_fear_value, nightMaxFear),
            onClick = actions.onCycleNightFear,
            trailing = { FearMeter(nightMaxFear) },
        )
        SettingRow(
            label = stringResource(R.string.settings_seasonal),
            value = stringResource(if (seasonalRows) R.string.value_on else R.string.value_off),
            onClick = actions.onToggleSeasonalRows,
        )
        SettingRow(
            label = stringResource(R.string.settings_sounds),
            value = stringResource(if (soundsEnabled) R.string.value_on else R.string.value_off),
            onClick = actions.onToggleSounds,
        )
        SettingRow(
            label = stringResource(R.string.settings_screensaver),
            value = stringResource(R.string.value_screensaver),
            onClick = actions.onOpenScreensaver,
        )
        BasicText(stringResource(R.string.settings_screensaver_note), style = ScaryType.Caption)
        SettingRow(
            label = stringResource(R.string.settings_parental),
            value = stringResource(if (parentalEnabled) R.string.value_lock_on else R.string.value_lock_off),
            onClick = if (parentalEnabled) actions.onTurnOffLock else actions.onSetupPin,
        )
        if (parentalEnabled) {
            SettingRow(
                label = stringResource(R.string.settings_lock_level),
                value = stringResource(R.string.settings_lock_level_value, lockFromFear),
                onClick = actions.onCycleLockLevel,
                trailing = { FearMeter(lockFromFear) },
            )
            SettingRow(
                label = stringResource(R.string.settings_change_pin),
                value = null,
                onClick = actions.onSetupPin,
            )
        }
        BasicText(stringResource(R.string.settings_parental_note), style = ScaryType.Caption)
        SettingRow(
            label = stringResource(R.string.settings_family_night),
            value = stringResource(if (familyNight) R.string.value_on else R.string.value_off),
            onClick = actions.onToggleFamilyNight,
        )
        BasicText(
            stringResource(if (parentalEnabled) R.string.settings_family_night_note else R.string.settings_family_night_note_no_pin),
            style = ScaryType.Caption,
        )
        SettingRow(
            label = stringResource(R.string.settings_system),
            value = null,
            onClick = actions.onSystemSettings,
        )
        BasicText(
            stringResource(R.string.settings_about),
            style = ScaryType.RowTitle,
            modifier = Modifier.padding(top = 14.dp).semantics { heading() },
        )
        BasicText(stringResource(R.string.settings_about_text, versionName), style = ScaryType.Body)
        // Where the movies come from, said plainly: SCARY holds no video and only hands movies to installed apps.
        BasicText(stringResource(R.string.settings_content_notice), style = ScaryType.Body)
        CreditLine()
        SettingRow(
            label = stringResource(R.string.settings_privacy),
            value = null,
            onClick = actions.onOpenPrivacy,
            modifier = Modifier.focusRequester(privacyFocus),
        )
        SettingRow(
            label = stringResource(R.string.settings_accessibility_statement),
            value = null,
            onClick = actions.onOpenAccessibilityStatement,
            modifier = Modifier.focusRequester(statementFocus),
        )
    }
}

@Composable
private fun SettingRow(
    label: String,
    value: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    FocusSurface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        focusedScale = 1.02f,
        containerColor = ScaryColors.Crypt,
        focusedContainerColor = ScaryColors.Crypt,
        borderColor = ScaryColors.Bone.copy(alpha = 0.1f),
        contentDescription = if (value != null) "$label: $value" else label,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            BasicText(label, style = ScaryType.BodyStrong, modifier = Modifier.weight(1f))
            trailing?.invoke()
            if (value != null) BasicText(value, style = ScaryType.Label.copy(color = ScaryColors.Candle))
        }
    }
}
