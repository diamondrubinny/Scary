package io.github.diamondrubinny.scary.ui.settings

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
import io.github.diamondrubinny.scary.ui.home.SidePadding
import io.github.diamondrubinny.scary.ui.theme.ScaryColors
import io.github.diamondrubinny.scary.ui.theme.ScaryType

class SettingsActions(
    val onToggleLanguage: () -> Unit,
    val onToggleEffects: () -> Unit,
    val onCycleNightFear: () -> Unit,
    val onSystemSettings: () -> Unit,
    val onToggleSounds: () -> Unit,
    val onSetupPin: () -> Unit,
    val onCycleLockLevel: () -> Unit,
    val onTurnOffLock: () -> Unit,
    val onToggleOnline: () -> Unit,
    val onRequestDefaultHome: () -> Unit,
    val onOpenAccessibility: () -> Unit,
)

@Composable
fun SettingsScreen(
    language: Language,
    effectsEnabled: Boolean,
    nightMaxFear: Int,
    soundsEnabled: Boolean,
    parentalEnabled: Boolean,
    lockFromFear: Int,
    onlineEnabled: Boolean,
    isDefaultHome: Boolean,
    homeButtonServiceOn: Boolean,
    versionName: String,
    actions: SettingsActions,
) {
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { firstFocus.requestFocus() }
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
            label = stringResource(R.string.settings_language),
            value = stringResource(if (language == Language.HE) R.string.language_name_he else R.string.language_name_en),
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
            label = stringResource(R.string.settings_sounds),
            value = stringResource(if (soundsEnabled) R.string.value_on else R.string.value_off),
            onClick = actions.onToggleSounds,
        )
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
