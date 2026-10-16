package io.github.diamondrubinny.scary.ui.components

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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
    onSystemSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        BasicText(stringResource(R.string.app_name), style = ScaryType.Logo)
        Spacer(Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Clock()
            Spacer(Modifier.width(4.dp))
            IconCircleButton(
                contentDescription = stringResource(
                    if (effectsEnabled) R.string.effects_on_description else R.string.effects_off_description,
                ),
                onClick = onToggleEffects,
                dimmed = !effectsEnabled,
            ) { FogIcon(it) }
            IconCircleButton(
                contentDescription = stringResource(R.string.system_settings_description),
                onClick = onSystemSettings,
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

/** Short status line at the bottom of the screen, announced politely by TalkBack. */
@Composable
fun MessageBanner(message: UserMessage?, onDismiss: (Long) -> Unit, modifier: Modifier = Modifier) {
    if (message == null) return
    LaunchedEffect(message.id) {
        delay(MESSAGE_DURATION_MS)
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
        BasicText(stringResource(message.text, *message.args.toTypedArray()), style = ScaryType.BodyStrong)
    }
}

private const val MILLIS_PER_MINUTE = 60_000L
private const val MESSAGE_DURATION_MS = 4_000L
