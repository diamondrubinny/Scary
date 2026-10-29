// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary.ui.components

import androidx.compose.foundation.layout.Column
import io.github.diamondrubinny.scary.ui.theme.mixedLanguage
import io.github.diamondrubinny.scary.core.brand.Credit
import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import io.github.diamondrubinny.scary.ui.theme.LocalReduceMotion
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.size
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import io.github.diamondrubinny.scary.R
import io.github.diamondrubinny.scary.ui.home.UserMessage
import io.github.diamondrubinny.scary.ui.theme.ScaryColors
import io.github.diamondrubinny.scary.ui.theme.ScaryType
import kotlinx.coroutines.delay
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@Composable
fun TopBar(
    effectsEnabled: Boolean,
    onToggleEffects: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenSearch: () -> Unit,
    onSurprise: () -> Unit,
    modifier: Modifier = Modifier,
    familyNight: Boolean = false,
    sleepAtMillis: Long? = null,
    /** While the rows have the focus: the donation code steps aside so the rows get the room (it shows again above). */
    compact: Boolean = false,
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Column {
            BasicText(stringResource(R.string.app_name), style = ScaryType.Logo)
            CreditLine(short = true)
            AnimatedVisibility(
                visible = !compact,
                enter = if (LocalReduceMotion.current) EnterTransition.None else fadeIn() + expandVertically(),
                exit = if (LocalReduceMotion.current) ExitTransition.None else fadeOut() + shrinkVertically(),
            ) {
                DonateMini(Modifier.padding(top = 8.dp))
            }
        }
        if (familyNight) {
            // Not just a color: the badge says it in words.
            BasicText(
                stringResource(R.string.family_night_badge),
                style = ScaryType.Label.copy(color = ScaryColors.Void),
                modifier = Modifier
                    .padding(start = 16.dp)
                    .background(ScaryColors.Candle, RoundedCornerShape(50))
                    .padding(horizontal = 14.dp, vertical = 5.dp),
            )
        }
        Spacer(Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (sleepAtMillis != null) SleepCountdown(sleepAtMillis)
            Clock()
            Spacer(Modifier.width(4.dp))
            IconCircleButton(
                contentDescription = stringResource(R.string.search_title),
                onClick = onOpenSearch,
            ) { SearchIcon(it) }
            IconCircleButton(
                contentDescription = stringResource(R.string.surprise_me),
                onClick = onSurprise,
            ) { DiceIcon(it) }
            IconCircleButton(
                contentDescription = stringResource(
                    if (effectsEnabled) R.string.effects_on_description else R.string.effects_off_description,
                ),
                onClick = onToggleEffects,
                dimmed = !effectsEnabled,
            ) { FogIcon(it) }
            IconCircleButton(
                contentDescription = stringResource(R.string.settings_title),
                onClick = onOpenSettings,
            ) { GearIcon(it) }
        }
    }
}

@Composable
fun Clock(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val formatter = remember(locale) {
        DateTimeFormatter.ofPattern(if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm", locale)
    }
    var now by remember { mutableStateOf(LocalTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = LocalTime.now()
            delay(MILLIS_PER_MINUTE - System.currentTimeMillis() % MILLIS_PER_MINUTE)
        }
    }
    BasicText(now.format(formatter), style = ScaryType.Clock, modifier = modifier)
}

/** The sleep timer at a glance: a moon and the minutes left. Not focusable; its words are read with the top bar. */
@Composable
fun SleepCountdown(endsAtMillis: Long, modifier: Modifier = Modifier) {
    val minutes by produceState(minutesShown(endsAtMillis), endsAtMillis) {
        while (true) {
            value = minutesShown(endsAtMillis)
            delay(MILLIS_PER_MINUTE / 2)
        }
    }
    val text = stringResource(R.string.sleep_left, minutes)
    Row(
        modifier.semantics(mergeDescendants = true) { contentDescription = text },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        MoonIcon(ScaryColors.Candle, Modifier.size(18.dp))
        BasicText(text, style = ScaryType.Label.copy(color = ScaryColors.Candle))
    }
}

/** Whole minutes until [endsAtMillis], rounded up, so "1 min" shows until the very end. */
fun minutesLeft(endsAtMillis: Long): Int = ((endsAtMillis - System.currentTimeMillis() + 59_999) / 60_000).toInt().coerceAtLeast(0)

/**
 * The minutes the countdown shows: never under 1 while the timer is on. The system may run the alarm a few minutes
 * late (an inexact alarm on Android 12+), and the timer stays on until it has paused playback.
 */
fun minutesShown(endsAtMillis: Long): Int = minutesLeft(endsAtMillis).coerceAtLeast(1)

/** Short status line at the bottom of the screen, announced politely by TalkBack. */
@Composable
fun MessageBanner(message: UserMessage?, onDismiss: (Long) -> Unit, modifier: Modifier = Modifier) {
    if (message == null) return
    val text = stringResource(message.text, *message.args.toTypedArray())
    LaunchedEffect(message.id) {
        // Longer messages stay longer, so there is time to read them (WCAG 2.2.1).
        delay((MESSAGE_DURATION_MS + text.length * MESSAGE_MS_PER_CHAR).coerceAtMost(MESSAGE_MAX_MS))
        onDismiss(message.id)
    }
    Box(
        modifier
            .padding(bottom = 28.dp)
            .background(ScaryColors.Void.copy(alpha = 0.94f), RoundedCornerShape(8.dp))
            .border(1.dp, ScaryColors.Candle, RoundedCornerShape(8.dp))
            .padding(horizontal = 22.dp, vertical = 12.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        BasicText(text, style = ScaryType.BodyStrong)
    }
}

private const val MILLIS_PER_MINUTE = 60_000L
private const val MESSAGE_DURATION_MS = 4_000L
private const val MESSAGE_MS_PER_CHAR = 50L
private const val MESSAGE_MAX_MS = 12_000L

/**
 * "Powered by SCARY · Open source". Required by the NOTICE file (Apache License 2.0, section 4(d)); see core Credit.
 * Its own text direction, so the parts keep their order inside right-to-left screens.
 */
@Composable
fun CreditLine(modifier: Modifier = Modifier, short: Boolean = false) {
    BasicText(
        text = if (short) Credit.SHORT else Credit.LINE,
        style = ScaryType.Caption.mixedLanguage(),
        modifier = modifier,
    )
}
