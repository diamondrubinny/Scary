// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary.ui.profiles

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import io.github.diamondrubinny.scary.R
import io.github.diamondrubinny.scary.core.profiles.Profiles
import io.github.diamondrubinny.scary.core.profiles.Profiles.Profile
import io.github.diamondrubinny.scary.ui.components.FocusSurface
import io.github.diamondrubinny.scary.ui.components.ScaryButton
import io.github.diamondrubinny.scary.ui.components.ScaryTextField
import io.github.diamondrubinny.scary.ui.home.ProfileChange
import io.github.diamondrubinny.scary.ui.home.SidePadding
import io.github.diamondrubinny.scary.ui.theme.ScaryColors
import io.github.diamondrubinny.scary.ui.theme.ScaryType

/** Test tags of the profiles screen. */
const val PROFILES_TAG = "profiles"
const val PROFILES_ADD_TAG = "profilesAdd"
const val PROFILE_DIALOG_TAG = "profileDialog"
const val PROFILE_NAME_TAG = "profileName"
const val PROFILE_GENTLE_TAG = "profileGentle"
const val PROFILE_SAVE_TAG = "profileSave"
const val PROFILE_REMOVE_TAG = "profileRemove"
const val PROFILE_REMOVE_CONFIRM_TAG = "profileRemoveConfirm"

/** A profile's card: its id for the test tag. */
fun profileTag(id: String) = "profile:$id"

/** A profile's colour is only a help to the eye: the name is always written beside it. Dark enough for bone letters. */
private val COLOURS = listOf(
    Color(0xFF9B1C1C), Color(0xFF8A5A12), Color(0xFF2F6B3F), Color(0xFF2B5F8A), Color(0xFF6B3F8A), Color(0xFF4A4F57),
)

private fun colourOf(index: Int): Color = COLOURS[index.coerceIn(0, COLOURS.lastIndex)]

@Composable
private fun nameOf(profile: Profile): String = profile.name.ifEmpty { stringResource(R.string.profile_default_name) }

/**
 * "Who is watching?" (docs/PROFILES_PLAN.md): each viewer of the TV is one card; OK chooses them, and their lists,
 * watched films and "Continue watching" are then the ones on screen. Holding OK on a card renames it, changes it or
 * removes it; a card can be added while there are fewer than five. The PIN, when one is set, is asked by the caller
 * for every change, and for leaving a gentle profile.
 */
@Composable
fun ProfilesScreen(
    profiles: List<Profile>,
    activeId: String,
    pinSet: Boolean,
    onChoose: (String) -> Unit,
    onChange: (ProfileChange) -> Unit,
) {
    var adding by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Profile?>(null) }
    if (adding) {
        ProfileDialog(null, Profiles.nextColour(profiles), pinSet, onSave = { name, colour, gentle -> onChange(ProfileChange.Add(name, colour, gentle)) }, onRemove = null) { adding = false }
    }
    editing?.let { profile ->
        ProfileDialog(
            profile, profile.colour, pinSet,
            onSave = { name, colour, gentle -> onChange(ProfileChange.Update(profile.id, name, colour, gentle)) },
            onRemove = if (profile.id == Profiles.FIRST) null else ({ onChange(ProfileChange.Remove(profile.id)) }),
        ) { editing = null }
    }
    val activeFocus = remember { FocusRequester() }
    LaunchedEffect(activeId, profiles.size) {
        withFrameNanos { }
        runCatching { activeFocus.requestFocus() }
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = SidePadding, vertical = 28.dp)
            .testTag(PROFILES_TAG),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        BasicText(
            stringResource(R.string.profiles_title),
            style = ScaryType.Display.copy(fontSize = ScaryType.Display.fontSize * 0.7f),
            modifier = Modifier.semantics { heading() },
        )
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            profiles.forEach { profile ->
                ProfileCard(
                    profile = profile,
                    active = profile.id == activeId,
                    focusRequester = activeFocus.takeIf { profile.id == activeId },
                    onClick = { onChoose(profile.id) },
                    onLongClick = { editing = profile },
                )
            }
        }
        if (profiles.size < Profiles.MAX) {
            ScaryButton(stringResource(R.string.profiles_add), onClick = { adding = true }, modifier = Modifier.testTag(PROFILES_ADD_TAG))
        }
        BasicText(stringResource(R.string.profiles_hint), style = ScaryType.Caption.copy(color = ScaryColors.Ash))
        BasicText(stringResource(R.string.profiles_note), style = ScaryType.Caption.copy(color = ScaryColors.Ash))
    }
}

@Composable
private fun ProfileCard(profile: Profile, active: Boolean, focusRequester: FocusRequester?, onClick: () -> Unit, onLongClick: () -> Unit) {
    val name = nameOf(profile)
    val gentle = stringResource(R.string.profile_gentle_badge)
    val said = listOfNotNull(name, gentle.takeIf { profile.gentle }, stringResource(R.string.profile_current).takeIf { active }).joinToString(", ")
    FocusSurface(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = (if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier).testTag(profileTag(profile.id)),
        shape = RoundedCornerShape(4.dp),
        focusedScale = 1.04f,
        containerColor = ScaryColors.Crypt,
        focusedContainerColor = ScaryColors.Bone,
        contentDescription = said,
    ) { focused ->
        Column(
            Modifier.width(168.dp).padding(horizontal = 14.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(Modifier.size(84.dp).background(colourOf(profile.colour), CircleShape), contentAlignment = Alignment.Center) {
                BasicText(name.take(1).uppercase(), style = ScaryType.Display.copy(color = ScaryColors.Bone, textAlign = TextAlign.Center))
            }
            BasicText(
                name,
                style = ScaryType.BodyStrong.copy(color = if (focused) ScaryColors.Void else ScaryColors.Bone, textDirection = TextDirection.Content, textAlign = TextAlign.Center),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // In words, never by colour alone: who is on screen now, and which profile is the gentle one.
            val marks = listOfNotNull(stringResource(R.string.profile_current).takeIf { active }, gentle.takeIf { profile.gentle }).joinToString(" · ")
            BasicText(marks.ifEmpty { " " }, style = ScaryType.Caption.copy(color = if (focused) ScaryColors.Void else ScaryColors.Candle, textAlign = TextAlign.Center), maxLines = 1)
        }
    }
}

/** A new profile ([profile] null) or one being changed: its name, colour and whether it is gentle; removing asks once more. */
@Composable
private fun ProfileDialog(
    profile: Profile?,
    startColour: Int,
    pinSet: Boolean,
    onSave: (name: String, colour: Int, gentle: Boolean) -> Unit,
    onRemove: (() -> Unit)?,
    onDone: () -> Unit,
) {
    var name by remember { mutableStateOf(profile?.name.orEmpty()) }
    var colour by remember { mutableIntStateOf(startColour) }
    var gentle by remember { mutableStateOf(profile?.gentle ?: false) }
    var badName by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf(false) }
    val first = remember { FocusRequester() }
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    LaunchedEffect(removing) {
        withFrameNanos { }
        runCatching { first.requestFocus() }
    }
    fun save() {
        if (Profiles.cleanName(name) == null) {
            badName = true
        } else {
            onSave(name, colour, gentle)
            onDone()
        }
    }
    val label = stringResource(R.string.profile_name_label)
    Dialog(onDismissRequest = onDone) {
        Column(
            modifier = Modifier
                .widthIn(max = 640.dp)
                .background(ScaryColors.Crypt, RoundedCornerShape(4.dp))
                .padding(22.dp)
                .testTag(PROFILE_DIALOG_TAG),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            BasicText(
                stringResource(if (profile == null) R.string.profile_new_title else R.string.profile_edit_title),
                style = ScaryType.Title,
                modifier = Modifier.semantics { heading() },
            )
            if (removing && onRemove != null && profile != null) {
                BasicText(
                    stringResource(R.string.profile_remove_confirm, nameOf(profile)),
                    style = ScaryType.Body,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
                // "Keep it" first: a second OK by habit must not remove someone's lists.
                ScaryButton(stringResource(R.string.profile_keep), onClick = { removing = false }, focusRequester = first)
                ScaryButton(
                    stringResource(R.string.profile_remove_yes),
                    onClick = { onRemove(); onDone() },
                    modifier = Modifier.testTag(PROFILE_REMOVE_CONFIRM_TAG),
                )
                return@Column
            }
            BasicText(label, style = ScaryType.Caption)
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(ScaryColors.Void, RoundedCornerShape(4.dp))
                    .border(2.dp, if (focused) ScaryColors.Candle else ScaryColors.Bone.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                ScaryTextField(
                    value = name,
                    onValueChange = { name = it.take(Profiles.MAX_NAME); badName = false },
                    textStyle = ScaryType.BodyStrong.copy(textDirection = TextDirection.Content),
                    cursorBrush = SolidColor(ScaryColors.Candle),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { save() }),
                    interactionSource = interaction,
                    modifier = Modifier.fillMaxWidth().focusRequester(first).testTag(PROFILE_NAME_TAG).semantics { contentDescription = label },
                )
            }
            if (badName) {
                BasicText(
                    stringResource(R.string.profile_bad_name),
                    style = ScaryType.BodyStrong.copy(color = ScaryColors.Candle),
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(28.dp).background(colourOf(colour), CircleShape))
                ScaryButton(
                    stringResource(R.string.profile_colour, colour + 1, Profiles.COLOURS),
                    onClick = { colour = (colour + 1) % Profiles.COLOURS },
                )
            }
            ScaryButton(
                stringResource(if (gentle) R.string.profile_gentle_on else R.string.profile_gentle_off),
                onClick = { gentle = !gentle },
                modifier = Modifier.testTag(PROFILE_GENTLE_TAG),
            )
            BasicText(stringResource(R.string.profile_gentle_what), style = ScaryType.Caption.copy(color = ScaryColors.Ash))
            // Said plainly: without a PIN nothing holds anyone in a gentle profile.
            if (gentle && !pinSet) BasicText(stringResource(R.string.profile_gentle_no_pin), style = ScaryType.Caption.copy(color = ScaryColors.Candle))
            ScaryButton(stringResource(R.string.profile_save), onClick = ::save, primary = true, modifier = Modifier.testTag(PROFILE_SAVE_TAG))
            if (onRemove != null) {
                ScaryButton(stringResource(R.string.profile_remove), onClick = { removing = true }, quiet = true, modifier = Modifier.testTag(PROFILE_REMOVE_TAG))
            }
            ScaryButton(stringResource(R.string.pin_cancel), onClick = onDone)
        }
    }
}
