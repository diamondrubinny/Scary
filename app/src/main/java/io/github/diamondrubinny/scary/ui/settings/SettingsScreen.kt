// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.diamondrubinny.scary.R
import io.github.diamondrubinny.scary.ai.AiTask
import io.github.diamondrubinny.scary.core.ai.AiProvider
import io.github.diamondrubinny.scary.core.model.Language
import io.github.diamondrubinny.scary.ui.components.CreditLine
import io.github.diamondrubinny.scary.ui.components.DataCredit
import io.github.diamondrubinny.scary.ui.components.FearMeter
import io.github.diamondrubinny.scary.ui.components.FocusSurface
import io.github.diamondrubinny.scary.ui.components.SupportScary
import io.github.diamondrubinny.scary.ui.components.minutesShown
import io.github.diamondrubinny.scary.ui.home.DocumentKind
import io.github.diamondrubinny.scary.ui.home.SidePadding
import io.github.diamondrubinny.scary.ui.theme.ScaryColors
import io.github.diamondrubinny.scary.ui.theme.ScaryType
import io.github.diamondrubinny.scary.ui.theme.mixedLanguage
import io.github.diamondrubinny.scary.ui.theme.paneWidth
import io.github.diamondrubinny.scary.ui.theme.scaryLayout
import io.github.diamondrubinny.scary.ui.trailer.TrailerDiagnostics
import io.github.diamondrubinny.scary.update.UpdateState
import java.text.DateFormat
import java.util.Calendar
import java.util.Date
import kotlinx.coroutines.delay

class SettingsActions(
    val onToggleLanguage: () -> Unit,
    val onToggleEffects: () -> Unit,
    val onCycleNightFear: () -> Unit,
    val onToggleSeasonalRows: () -> Unit,
    val onToggleAutoWatched: () -> Unit,
    val onToggleHideWatched: () -> Unit = {},
    val onSystemSettings: () -> Unit,
    val onToggleSounds: () -> Unit,
    val onToggleTrailerAutoplay: () -> Unit,
    val onToggleTrailerSound: () -> Unit,
    val onCycleSleepTimer: () -> Unit,
    val onOpenStats: () -> Unit,
    val onSetupPin: () -> Unit,
    val onCycleLockLevel: () -> Unit,
    val onTurnOffLock: () -> Unit,
    val onToggleFamilyNight: () -> Unit,
    val onToggleOnline: () -> Unit,
    val onRequestDefaultHome: () -> Unit,
    val onOpenAccessibility: () -> Unit,
    val onOpenTmdb: () -> Unit,
    val onOpenPhoneRemote: () -> Unit = {},
    val onOpenTmdbAccount: () -> Unit = {},
    val onOpenRealDebrid: () -> Unit = {},
    val onOpenStremioAccount: () -> Unit = {},
    val onOpenTastePicker: () -> Unit = {},
    val onClearRecent: () -> Unit = {},
    val onCheckForUpdates: () -> Unit = {},
    val onToggleAi: () -> Unit = {},
    val onAiProvider: () -> Unit = {},
    val onAiKey: () -> Unit = {},
    val onAiModel: () -> Unit = {},
    val onAiUrl: () -> Unit = {},
    val onAiTest: () -> Unit = {},
    val onToggleAiTaste: () -> Unit = {},
    val onToggleAiHomeRow: () -> Unit = {},
    val onDeleteAiKeys: () -> Unit = {},
    val onToggleAutoUpdate: () -> Unit = {},
    val onOpenRentCountry: () -> Unit,
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
    trailerAutoplay: Boolean,
    trailerSound: Boolean,
    /** When the sleep timer ends (wall clock), or null when it is off. */
    sleepAtMillis: Long?,
    parentalEnabled: Boolean,
    lockFromFear: Int,
    familyNight: Boolean,
    seasonalRows: Boolean,
    autoWatched: Boolean,
    hideWatched: Boolean = false,
    /** The TMDB account's name when signed in, null otherwise. */
    tmdbAccountName: String? = null,
    /** The connected Real-Debrid account's name; empty when connected without one; null when not connected. */
    realDebridName: String? = null,
    stremioConnected: Boolean = false,
    onlineEnabled: Boolean,
    isDefaultHome: Boolean,
    homeButtonServiceOn: Boolean,
    tmdbKeyMask: String?,
    /** The country rent and buy offers are for; null when neither the settings nor the device name one. */
    rentCountryName: String?,
    rentCountryAutomatic: Boolean,
    versionName: String,
    actions: SettingsActions,
    returningFrom: DocumentKind? = null,
    returningFromStats: Boolean = false,
    returningFromRemote: Boolean = false,
    phoneRemoteOn: Boolean = false,
    lovedCount: Int = 0,
    recentCount: Int = 0,
    returningFromTaste: Boolean = false,
    updateState: UpdateState = UpdateState.Idle,
    autoUpdateCheck: Boolean = true,
    ai: AiSettingsState = AiSettingsState(),
) {
    val firstFocus = remember { FocusRequester() }
    val privacyFocus = remember { FocusRequester() }
    val statementFocus = remember { FocusRequester() }
    val statsFocus = remember { FocusRequester() }
    val remoteFocus = remember { FocusRequester() }
    val tasteFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        // Back from a document lands on the row that opened it, not at the top.
        val target = when {
            returningFromStats -> statsFocus
            returningFromRemote -> remoteFocus
            returningFromTaste -> tasteFocus
            returningFrom == DocumentKind.Privacy -> privacyFocus
            returningFrom == DocumentKind.Accessibility -> statementFocus
            else -> firstFocus
        }
        runCatching { target.requestFocus() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = SidePadding, vertical = 28.dp)
            .paneWidth(scaryLayout, 0.7f),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        BasicText(
            stringResource(R.string.settings_title),
            style = ScaryType.Display.copy(fontSize = ScaryType.Display.fontSize * 0.7f),
            modifier = Modifier.padding(bottom = 8.dp).semantics { heading() },
        )
        // The home screen and the remote's Home button are a TV's: on a phone or a tablet SCARY is an ordinary app.
        val tv = scaryLayout.tv
        if (tv) {
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
        }
        SettingRow(
            label = stringResource(R.string.settings_online),
            value = stringResource(if (onlineEnabled) R.string.value_on else R.string.value_off),
            onClick = actions.onToggleOnline,
            modifier = if (tv) Modifier else Modifier.focusRequester(firstFocus),
        )
        BasicText(stringResource(R.string.settings_online_note), style = ScaryType.Caption)
        SettingRow(
            label = stringResource(R.string.settings_tmdb),
            value = tmdbKeyMask ?: stringResource(R.string.value_tmdb_none),
            onClick = actions.onOpenTmdb,
        )
        SettingRow(
            label = stringResource(R.string.settings_tmdb_account),
            value = tmdbAccountName ?: stringResource(R.string.value_tmdb_account_none),
            onClick = actions.onOpenTmdbAccount,
            modifier = Modifier.testTag(TMDB_ACCOUNT_ROW_TAG),
        )
        // The credit is English only; keep its punctuation in place inside a right-to-left layout.
        BasicText(stringResource(R.string.tmdb_attribution), style = ScaryType.Caption.mixedLanguage())
        AiSection(ai, actions)
        SettingRow(
            label = stringResource(R.string.settings_realdebrid),
            value = realDebridName?.ifBlank { stringResource(R.string.rd_connected) } ?: stringResource(R.string.value_rd_none),
            onClick = actions.onOpenRealDebrid,
            modifier = Modifier.testTag(REAL_DEBRID_ROW_TAG),
        )
        SettingRow(
            label = stringResource(R.string.settings_stremio_account),
            value = stringResource(if (stremioConnected) R.string.stremio_connected else R.string.stremio_signed_out),
            onClick = actions.onOpenStremioAccount,
            modifier = Modifier.testTag(STREMIO_ROW_TAG),
        )
        // The phone remote lets a phone steer the TV.
        if (tv) {
            SettingRow(
                label = stringResource(R.string.settings_phone_remote),
                value = stringResource(if (phoneRemoteOn) R.string.value_on else R.string.value_off),
                onClick = actions.onOpenPhoneRemote,
                modifier = Modifier.focusRequester(remoteFocus).testTag(PHONE_REMOTE_ROW_TAG),
            )
            BasicText(stringResource(R.string.settings_phone_remote_note), style = ScaryType.Caption)
        }
        SettingRow(
            label = stringResource(R.string.settings_rent_country),
            value = when {
                rentCountryName == null -> stringResource(R.string.value_country_unknown)
                rentCountryAutomatic -> stringResource(R.string.value_country_auto, rentCountryName)
                else -> rentCountryName
            },
            onClick = actions.onOpenRentCountry,
        )
        BasicText(stringResource(R.string.settings_rent_country_note), style = ScaryType.Caption)
        SettingRow(
            label = stringResource(R.string.settings_language),
            value = language.nativeName,
            onClick = actions.onToggleLanguage,
            modifier = Modifier.testTag(LANGUAGE_ROW_TAG),
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
            label = stringResource(R.string.settings_auto_watched),
            value = stringResource(if (autoWatched) R.string.value_on else R.string.value_off),
            onClick = actions.onToggleAutoWatched,
        )
        BasicText(stringResource(R.string.settings_auto_watched_note), style = ScaryType.Caption)
        SettingRow(
            label = stringResource(R.string.settings_hide_watched),
            value = stringResource(if (hideWatched) R.string.value_on else R.string.value_off),
            onClick = actions.onToggleHideWatched,
            modifier = Modifier.testTag(HIDE_WATCHED_ROW_TAG),
        )
        BasicText(stringResource(R.string.settings_hide_watched_note), style = ScaryType.Caption)
        SettingRow(
            label = stringResource(R.string.taste_title),
            value = pluralStringResource(R.plurals.settings_taste_value, lovedCount, lovedCount),
            onClick = actions.onOpenTastePicker,
            modifier = Modifier.focusRequester(tasteFocus).testTag(TASTE_ROW_TAG),
        )
        // Clearing asks once more; moving away takes the question back.
        var confirmClearRecent by remember { mutableStateOf(false) }
        SettingRow(
            label = stringResource(if (confirmClearRecent) R.string.settings_clear_recent_confirm else R.string.settings_clear_recent),
            value = if (recentCount == 0) stringResource(R.string.settings_clear_recent_empty) else pluralStringResource(R.plurals.settings_clear_recent_value, recentCount, recentCount),
            onClick = {
                when {
                    recentCount == 0 -> Unit
                    confirmClearRecent -> {
                        confirmClearRecent = false
                        actions.onClearRecent()
                    }
                    else -> confirmClearRecent = true
                }
            },
            modifier = Modifier
                .onFocusChanged { if (!it.hasFocus) confirmClearRecent = false }
                .testTag(CLEAR_RECENT_ROW_TAG),
        )
        BasicText(stringResource(R.string.settings_clear_recent_note), style = ScaryType.Caption)
        SettingRow(
            label = stringResource(R.string.settings_stats),
            value = null,
            onClick = actions.onOpenStats,
            modifier = Modifier.focusRequester(statsFocus).testTag(STATS_ROW_TAG),
        )
        SettingRow(
            label = stringResource(R.string.settings_sounds),
            value = stringResource(if (soundsEnabled) R.string.value_on else R.string.value_off),
            onClick = actions.onToggleSounds,
        )
        SettingRow(
            label = stringResource(R.string.settings_trailer_autoplay),
            value = stringResource(if (trailerAutoplay) R.string.value_on else R.string.value_off),
            onClick = actions.onToggleTrailerAutoplay,
            modifier = Modifier.testTag(TRAILER_AUTOPLAY_ROW_TAG),
        )
        if (trailerAutoplay) {
            SettingRow(
                label = stringResource(R.string.settings_trailer_sound),
                value = stringResource(if (trailerSound) R.string.value_on else R.string.value_off),
                onClick = actions.onToggleTrailerSound,
            )
        }
        BasicText(stringResource(R.string.settings_trailer_note), style = ScaryType.Caption)
        // What happened to the last trailer, for when they don't play (the TV's WebView, or YouTube's answer).
        val lastTrailer by TrailerDiagnostics.last.collectAsState()
        lastTrailer?.let { status ->
            BasicText(stringResource(R.string.settings_trailer_last, status), style = ScaryType.Caption.mixedLanguage())
        }
        SettingRow(
            label = stringResource(R.string.settings_screensaver),
            value = stringResource(R.string.value_screensaver),
            onClick = actions.onOpenScreensaver,
        )
        BasicText(stringResource(R.string.settings_screensaver_note), style = ScaryType.Caption)
        // The countdown refreshes on its own while settings stay open.
        val sleepMinutes by produceState(sleepAtMillis?.let(::minutesShown) ?: 0, sleepAtMillis) {
            while (true) {
                value = sleepAtMillis?.let(::minutesShown) ?: 0
                delay(30_000)
            }
        }
        SettingRow(
            label = stringResource(R.string.settings_sleep_timer),
            value = if (sleepMinutes > 0) stringResource(R.string.sleep_left, sleepMinutes) else stringResource(R.string.sleep_off),
            onClick = actions.onCycleSleepTimer,
            modifier = Modifier.testTag(SLEEP_ROW_TAG),
        )
        BasicText(stringResource(R.string.settings_sleep_timer_note), style = ScaryType.Caption)
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
        if (updateState != UpdateState.NotOfficial) {
            SettingRow(
                label = stringResource(R.string.settings_update_check),
                value = when (updateState) {
                    UpdateState.Checking -> stringResource(R.string.settings_update_checking)
                    is UpdateState.UpToDate -> stringResource(R.string.settings_update_up_to_date, updateState.versionCode, checkedAt(updateState.checkedAt))
                    is UpdateState.Available -> stringResource(R.string.settings_update_available, updateState.manifest.versionCode)
                    is UpdateState.Downloading -> stringResource(R.string.update_downloading, updateState.percent)
                    is UpdateState.Failed -> stringResource(R.string.settings_update_failed)
                    else -> null
                },
                onClick = actions.onCheckForUpdates,
                modifier = Modifier.testTag(UPDATE_CHECK_ROW_TAG),
            )
            SettingRow(
                label = stringResource(R.string.settings_update_auto),
                value = stringResource(if (autoUpdateCheck) R.string.value_on else R.string.value_off),
                onClick = actions.onToggleAutoUpdate,
            )
            BasicText(stringResource(R.string.settings_update_note), style = ScaryType.Caption)
        } else {
            // A fork's or a developer's own build: the official repository's updates could not install over it.
            BasicText(stringResource(R.string.settings_update_not_official), style = ScaryType.Caption)
        }
        // Where the movies come from, said plainly: SCARY holds no video and only hands movies to installed apps.
        BasicText(stringResource(R.string.settings_content_notice), style = ScaryType.Body)
        CreditLine()
        DataCredit()
        SupportScary(Modifier.padding(vertical = 8.dp))
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

/** Test tag of the phone remote's row. */
const val PHONE_REMOTE_ROW_TAG = "phoneRemoteRow"
const val HIDE_WATCHED_ROW_TAG = "hideWatchedRow"
const val TMDB_ACCOUNT_ROW_TAG = "tmdbAccountRow"
const val REAL_DEBRID_ROW_TAG = "realDebridRow"
const val STREMIO_ROW_TAG = "stremioRow"
const val TASTE_ROW_TAG = "tasteRow"
const val CLEAR_RECENT_ROW_TAG = "clearRecentRow"
const val UPDATE_CHECK_ROW_TAG = "updateCheckRow"
const val AI_ENABLED_ROW_TAG = "aiEnabledRow"
const val LANGUAGE_ROW_TAG = "languageRow"
const val AI_PROVIDER_ROW_TAG = "aiProviderRow"

/** The AI section's values, as settings show them. */
data class AiSettingsState(
    val enabled: Boolean = true,
    val provider: AiProvider = AiProvider.DEFAULT,
    val keyMask: String? = null,
    val model: String? = null,
    val customUrl: String = "",
    val shareTaste: Boolean = false,
    val homeRow: Boolean = true,
    val test: AiTask<String> = AiTask.Idle,
)

/**
 * AI: on or off; the service (Pollinations' free one, or one with the viewer's key); its key, model, and for a custom
 * service its address; a connection test; whether a short taste summary may be sent; the home screen's chips; and
 * deleting every key (asked twice).
 */
@Composable
private fun AiSection(ai: AiSettingsState, actions: SettingsActions) {
    BasicText(
        stringResource(R.string.settings_ai_section),
        style = ScaryType.RowTitle,
        modifier = Modifier.padding(top = 14.dp).semantics { heading() },
    )
    SettingRow(
        label = stringResource(R.string.settings_ai_enabled),
        value = stringResource(if (ai.enabled) R.string.value_on else R.string.value_off),
        onClick = actions.onToggleAi,
        modifier = Modifier.testTag(AI_ENABLED_ROW_TAG),
    )
    if (!ai.enabled) return
    SettingRow(
        label = stringResource(R.string.settings_ai_provider),
        value = ai.provider.displayName,
        onClick = actions.onAiProvider,
        modifier = Modifier.testTag(AI_PROVIDER_ROW_TAG),
    )
    if (ai.provider == AiProvider.CUSTOM) {
        SettingRow(
            label = stringResource(R.string.settings_ai_url),
            value = ai.customUrl.ifEmpty { stringResource(R.string.value_ai_none) },
            onClick = actions.onAiUrl,
        )
    }
    if (!ai.provider.free) {
        SettingRow(
            label = stringResource(R.string.settings_ai_key),
            value = ai.keyMask ?: stringResource(R.string.value_ai_none),
            onClick = actions.onAiKey,
        )
        SettingRow(
            label = stringResource(R.string.settings_ai_model),
            value = ai.model ?: stringResource(R.string.ai_model_auto),
            onClick = actions.onAiModel,
        )
    }
    SettingRow(
        label = stringResource(R.string.settings_ai_test),
        value = when (ai.test) {
            AiTask.Loading -> stringResource(R.string.ai_thinking)
            is AiTask.Done -> stringResource(R.string.value_ai_test_ok)
            is AiTask.Failed -> stringResource(R.string.value_ai_test_failed)
            else -> null
        },
        onClick = actions.onAiTest,
    )
    (ai.test as? AiTask.Done)?.let { BasicText(it.value, style = ScaryType.Caption.mixedLanguage()) }
    (ai.test as? AiTask.Failed)?.let { failed -> io.github.diamondrubinny.scary.ui.ai.AiStatus(failed, onRetry = null, onSettings = null) }
    SettingRow(
        label = stringResource(R.string.settings_ai_share_taste),
        value = stringResource(if (ai.shareTaste) R.string.value_on else R.string.value_off),
        onClick = actions.onToggleAiTaste,
    )
    BasicText(stringResource(R.string.settings_ai_share_taste_note), style = ScaryType.Caption)
    SettingRow(
        label = stringResource(R.string.settings_ai_home_row),
        value = stringResource(if (ai.homeRow) R.string.value_on else R.string.value_off),
        onClick = actions.onToggleAiHomeRow,
    )
    var confirmDelete by remember { mutableStateOf(false) }
    SettingRow(
        label = stringResource(if (confirmDelete) R.string.settings_ai_delete_keys_confirm else R.string.settings_ai_delete_keys),
        value = null,
        onClick = {
            if (confirmDelete) {
                confirmDelete = false
                actions.onDeleteAiKeys()
            } else {
                confirmDelete = true
            }
        },
        modifier = Modifier.onFocusChanged { if (!it.hasFocus) confirmDelete = false },
    )
    BasicText(stringResource(R.string.settings_ai_note), style = ScaryType.Caption)
}

/** Test tags of the sleep timer and "Your year in fear" rows. */
const val SLEEP_ROW_TAG = "sleepTimerRow"
const val STATS_ROW_TAG = "statsRow"

/** Test tag of the "Play trailers by themselves" row. */
const val TRAILER_AUTOPLAY_ROW_TAG = "trailerAutoplayRow"

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

/** When the last check ran, in the viewer's language: the time today, the date and time before that. */
@Composable
private fun checkedAt(millis: Long): String {
    val locale = LocalLocale.current.platformLocale
    val now = Calendar.getInstance()
    val then = Calendar.getInstance().apply { timeInMillis = millis }
    val today = now.get(Calendar.YEAR) == then.get(Calendar.YEAR) && now.get(Calendar.DAY_OF_YEAR) == then.get(Calendar.DAY_OF_YEAR)
    val format = if (today) {
        DateFormat.getTimeInstance(DateFormat.SHORT, locale)
    } else {
        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, locale)
    }
    return format.format(Date(millis))
}
