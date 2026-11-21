// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary.ui.components

import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.graphics.Color
import android.text.format.DateFormat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.diamondrubinny.scary.R
import io.github.diamondrubinny.scary.core.brand.Credit
import io.github.diamondrubinny.scary.core.model.MediaKind
import io.github.diamondrubinny.scary.ui.home.UserMessage
import io.github.diamondrubinny.scary.ui.theme.LocalReduceMotion
import io.github.diamondrubinny.scary.ui.theme.ScaryColors
import io.github.diamondrubinny.scary.ui.theme.ScaryType
import io.github.diamondrubinny.scary.ui.theme.mixedLanguage
import io.github.diamondrubinny.scary.ui.theme.scaryLayout
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay

/** Test tag of the top bar's "Ask SCARY ✦" button. */
const val ASK_BUTTON_TAG = "askButton"

/** Test tag of the top bar's profile name. */
const val PROFILE_NAV_TAG = "profileNav"

/** Test tag of the top bar's "Real-Debrid" word. */
const val REAL_DEBRID_NAV_TAG = "realDebridNav"

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TopBar(
    onOpenSettings: () -> Unit,
    onOpenSearch: () -> Unit,
    onSurprise: () -> Unit,
    modifier: Modifier = Modifier,
    familyNight: Boolean = false,
    sleepAtMillis: Long? = null,
    /** While the rows have the focus: the donation code steps aside so the rows get the room (it shows again above). */
    compact: Boolean = false,
    /** The home screen's tabs (films, series); none on other screens. */
    tab: MediaKind? = null,
    onSelectTab: (MediaKind) -> Unit = {},
    /** The bucket list's button, with how many films it holds; none when null. */
    onOpenBucket: (() -> Unit)? = null,
    bucketCount: Int = 0,
    /** "Ask SCARY ✦"; none when AI is off. */
    onAsk: (() -> Unit)? = null,
    /** The viewer's Real-Debrid account and its videos, one press from the home screen; none when null. */
    onOpenRealDebrid: (() -> Unit)? = null,
    /** "Who is watching?", shown as the name of the viewer on screen; none on a TV with one viewer. */
    profileName: String? = null,
    onOpenProfiles: () -> Unit = {},
) {
    if (scaryLayout.narrow) {
        NarrowTopBar(
            onOpenSettings, onOpenSearch, onSurprise, modifier, familyNight, sleepAtMillis,
            tab, onSelectTab, onOpenBucket, bucketCount, onAsk, onOpenRealDebrid, profileName, onOpenProfiles,
        )
        return
    }
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
        if (tab != null) HomeTabs(tab, onSelectTab, Modifier.padding(start = 20.dp))
        // The words take the room that is left and wrap to a second line when a language's words are long (Russian,
        // Portuguese) or the text is enlarged: none is ever pushed off the bar, and the clock is never squeezed. The
        // logo's side is three lines tall anyway, so a second line costs no height.
        FlowRow(
            modifier = Modifier.weight(1f).padding(start = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp, Alignment.End),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            TopBarButtons(onOpenSettings, onOpenSearch, onSurprise, onOpenBucket, bucketCount, onAsk, onOpenRealDebrid, profileName, onOpenProfiles)
            Row(Modifier.padding(start = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                if (sleepAtMillis != null) SleepCountdown(sleepAtMillis)
                Clock()
            }
        }
    }
}

/**
 * The top bar on a tablet or phone: the logo and the clock, then the tabs and the buttons in a row of their own that
 * scrolls sideways when it does not fit. The donation code stays on the TV: a phone cannot scan its own screen.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NarrowTopBar(
    onOpenSettings: () -> Unit,
    onOpenSearch: () -> Unit,
    onSurprise: () -> Unit,
    modifier: Modifier,
    familyNight: Boolean,
    sleepAtMillis: Long?,
    tab: MediaKind?,
    onSelectTab: (MediaKind) -> Unit,
    onOpenBucket: (() -> Unit)?,
    bucketCount: Int,
    onAsk: (() -> Unit)?,
    onOpenRealDebrid: (() -> Unit)?,
    profileName: String?,
    onOpenProfiles: () -> Unit,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                BasicText(stringResource(R.string.app_name), style = ScaryType.Logo)
                CreditLine(short = true)
            }
            if (sleepAtMillis != null) SleepCountdown(sleepAtMillis, Modifier.padding(end = 12.dp))
            Clock()
        }
        // Wraps to another line rather than scrolling sideways: a button past the edge would be one nobody finds.
        FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            if (familyNight) {
                BasicText(
                    stringResource(R.string.family_night_badge),
                    style = ScaryType.Label.copy(color = ScaryColors.Void),
                    modifier = Modifier
                        .background(ScaryColors.Candle, RoundedCornerShape(50))
                        .padding(horizontal = 14.dp, vertical = 5.dp),
                )
            }
            if (tab != null) HomeTabs(tab, onSelectTab)
            TopBarButtons(onOpenSettings, onOpenSearch, onSurprise, onOpenBucket, bucketCount, onAsk, onOpenRealDebrid, profileName, onOpenProfiles)
        }
    }
}

@Composable
private fun TopBarButtons(
    onOpenSettings: () -> Unit,
    onOpenSearch: () -> Unit,
    onSurprise: () -> Unit,
    onOpenBucket: (() -> Unit)?,
    bucketCount: Int,
    onAsk: (() -> Unit)?,
    onOpenRealDebrid: (() -> Unit)?,
    profileName: String?,
    onOpenProfiles: () -> Unit,
) {
    // Whose lists these are, and the way to someone else's: first, where the eye starts.
    if (profileName != null) {
        NavWord(profileName, stringResource(R.string.profiles_switch, profileName), onOpenProfiles, Modifier.testTag(PROFILE_NAV_TAG))
    }
    // Noir: the top bar is words. Each says where it goes; nothing has to be remembered by its picture. The longer
    // names stay as what a screen reader says (and what the tests look for).
    if (onOpenBucket != null) {
        NavWord(
            text = if (bucketCount > 0) stringResource(R.string.nav_bucket_count, bucketCount) else stringResource(R.string.bucket_title),
            description = if (bucketCount > 0) stringResource(R.string.bucket_button_count, bucketCount) else stringResource(R.string.bucket_title),
            onClick = onOpenBucket,
            modifier = Modifier.testTag(BUCKET_BUTTON_TAG),
        )
    }
    if (onAsk != null) {
        NavWord(stringResource(R.string.nav_ask), stringResource(R.string.ai_title), onAsk, Modifier.testTag(ASK_BUTTON_TAG))
    }
    NavWord(stringResource(R.string.nav_search), stringResource(R.string.search_title), onOpenSearch)
    NavWord(stringResource(R.string.nav_surprise), stringResource(R.string.surprise_me), onSurprise)
    // The viewer's own files, one press away instead of a row far down Settings (asked for by the owner, 0.6.2).
    if (onOpenRealDebrid != null) {
        NavWord(stringResource(R.string.rd_title), stringResource(R.string.rd_title), onOpenRealDebrid, Modifier.testTag(REAL_DEBRID_NAV_TAG))
    }
    // The effects switch is in Settings: one word fewer here, and the bar fits in every language.
    NavWord(stringResource(R.string.nav_settings), stringResource(R.string.settings_title), onOpenSettings)
}

/**
 * One word of the top bar: ash, bone when it is the current place or has the focus, with a short red line under the
 * current one. [description] is what a screen reader says (the full name).
 */
@Composable
internal fun NavWord(
    text: String,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    current: Boolean = false,
) {
    FocusSurface(
        onClick = onClick,
        modifier = modifier.heightIn(min = if (scaryLayout.tv) 0.dp else TOUCH_TARGET),
        shape = RoundedCornerShape(3.dp),
        focusedScale = 1.04f,
        containerColor = Color.Transparent,
        contentDescription = description,
    ) { focused ->
        Column(
            modifier = Modifier.align(Alignment.Center).padding(horizontal = 8.dp, vertical = 5.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            BasicText(
                text = text,
                style = ScaryType.Nav.copy(color = if (focused || current) ScaryColors.Bone else ScaryColors.Ash),
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.testTag(NAV_WORD_TAG).clearAndSetSemantics { },
            )
            Box(Modifier.padding(top = 3.dp).size(width = 16.dp, height = 2.dp).background(if (current) ScaryColors.Accent else Color.Transparent))
        }
    }
}

/** The visible word of a top bar item (its node says nothing to a screen reader: the item itself carries the name). */
const val NAV_WORD_TAG = "navWord"

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
    // One line always: a clock squeezed for room must not fall apart into a column of digits.
    BasicText(now.format(formatter), style = ScaryType.Clock, modifier = modifier, maxLines = 1, softWrap = false)
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
            .background(ScaryColors.Void.copy(alpha = 0.94f), RoundedCornerShape(4.dp))
            .border(1.dp, ScaryColors.Candle, RoundedCornerShape(4.dp))
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

/** Test tags of the bucket list's button and of the films and series tabs. */
const val BUCKET_BUTTON_TAG = "bucketButton"
fun tabTag(kind: MediaKind): String = "tab:${kind.name.lowercase()}"

/** Films or series: two words beside the logo. The chosen one is bone with a red line under it, and says so to TalkBack. */
@Composable
private fun HomeTabs(selected: MediaKind, onSelect: (MediaKind) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        for (kind in MediaKind.entries) {
            val chosen = kind == selected
            val name = stringResource(if (kind == MediaKind.SERIES) R.string.tab_series else R.string.tab_movies)
            NavWord(
                text = name,
                description = name,
                onClick = { onSelect(kind) },
                modifier = Modifier
                    .testTag(tabTag(kind))
                    .semantics {
                        role = Role.Tab
                        this.selected = chosen
                    },
                current = chosen,
            )
        }
    }
}
