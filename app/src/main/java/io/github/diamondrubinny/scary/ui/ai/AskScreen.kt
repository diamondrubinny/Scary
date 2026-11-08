// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary.ui.ai

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.diamondrubinny.scary.R
import io.github.diamondrubinny.scary.ai.AiChip
import io.github.diamondrubinny.scary.ai.AiState
import io.github.diamondrubinny.scary.ai.AiTask
import io.github.diamondrubinny.scary.core.ai.AiMessage
import io.github.diamondrubinny.scary.core.ai.AiPick
import io.github.diamondrubinny.scary.core.model.Language
import io.github.diamondrubinny.scary.core.model.Movie
import io.github.diamondrubinny.scary.ui.components.PosterCard
import io.github.diamondrubinny.scary.ui.components.ScaryButton
import io.github.diamondrubinny.scary.ui.home.MoodChip
import io.github.diamondrubinny.scary.ui.home.SidePadding
import io.github.diamondrubinny.scary.ui.home.subgenreLine
import io.github.diamondrubinny.scary.ui.theme.ScaryColors
import io.github.diamondrubinny.scary.ui.theme.ScaryType
import io.github.diamondrubinny.scary.ui.theme.mixedLanguage

/** Test tags of the "Ask SCARY" screen, its tabs and its results. */
const val ASK_SCREEN_TAG = "askScreen"
const val ASK_CHAT_TAG = "askChat"
fun askTabTag(chat: Boolean): String = if (chat) "askTab:chat" else "askTab:suggest"
fun askPickTag(movieId: String): String = "askPick:$movieId"

class AskActions(
    val onRecommend: (query: String, chip: AiChip?) -> Unit,
    val onOpenMovie: (Movie) -> Unit,
    val onSendChat: (String) -> Unit,
    val onRetryChat: () -> Unit,
    val onNewChat: () -> Unit,
    val onSelectTab: (chat: Boolean) -> Unit,
    val onOpenSettings: () -> Unit,
)

/**
 * "Ask SCARY ✦". Suggestions: one-press chips, or a request in one's own words (typed or said), answered with
 * posters from this TV's catalog, each with a line on why. Chat: an open conversation about horror films (off with
 * the parental lock or family night). The service in use is named at the top, with a way to change it.
 */
@Composable
fun AskScreen(
    state: AiState,
    chat: Boolean,
    serviceLabel: String,
    language: Language,
    isLocked: (Movie) -> Boolean,
    isWatched: (Movie) -> Boolean,
    actions: AskActions,
) {
    val inputFocus = remember { FocusRequester() }
    val firstPickFocus = remember { FocusRequester() }
    LaunchedEffect(chat) {
        withFrameNanos { }
        // A chip pressed on the home screen is already asking: the focus waits on the first result instead.
        if (chat || state.recommend !is AiTask.Loading) runCatching { inputFocus.requestFocus() }
    }
    Column(
        Modifier
            .fillMaxSize()
            .padding(top = 28.dp)
            .testTag(ASK_SCREEN_TAG),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = SidePadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            BasicText(
                stringResource(R.string.ai_title),
                style = ScaryType.Display.copy(fontSize = ScaryType.Display.fontSize * 0.6f),
                modifier = Modifier.semantics { heading() },
            )
            MoodChip(
                label = stringResource(R.string.ai_tab_suggest),
                selected = !chat,
                onClick = { actions.onSelectTab(false) },
                onFocused = {},
                focusRequester = null,
                modifier = Modifier.testTag(askTabTag(false)),
            )
            MoodChip(
                label = stringResource(R.string.ai_tab_chat),
                selected = chat,
                onClick = { actions.onSelectTab(true) },
                onFocused = {},
                focusRequester = null,
                modifier = Modifier.testTag(askTabTag(true)),
            )
            Box(Modifier.weight(1f))
            BasicText(stringResource(R.string.ai_service_line, serviceLabel), style = ScaryType.Caption, maxLines = 1)
            ScaryButton(stringResource(R.string.ai_change), onClick = actions.onOpenSettings)
        }
        if (chat) {
            ChatPane(state, language, inputFocus, actions)
        } else {
            SuggestPane(state, language, inputFocus, firstPickFocus, isLocked, isWatched, actions)
        }
    }
}

@Composable
private fun SuggestPane(
    state: AiState,
    language: Language,
    inputFocus: FocusRequester,
    firstPickFocus: FocusRequester,
    isLocked: (Movie) -> Boolean,
    isWatched: (Movie) -> Boolean,
    actions: AskActions,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val result = state.recommend
    LaunchedEffect(result) {
        if (result is AiTask.Done && result.value.picks.isNotEmpty()) {
            withFrameNanos { }
            runCatching { firstPickFocus.requestFocus() }
        }
    }
    val chipPrompts = AiChip.entries.associateWith { stringResource(it.promptRes()) }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 150.dp),
        contentPadding = PaddingValues(horizontal = SidePadding, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }, key = "input") {
            AiInput(
                value = query,
                onValueChange = { query = it },
                onSubmit = { actions.onRecommend(query, null) },
                label = stringResource(R.string.ai_title),
                hint = stringResource(R.string.ai_hint),
                submitLabel = stringResource(R.string.ai_send),
                language = language,
                focusRequester = inputFocus,
                modifier = Modifier.fillMaxWidth(0.85f),
            )
        }
        item(span = { GridItemSpan(maxLineSpan) }, key = "chips") {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(AiChip.entries, key = { it.key }) { chip ->
                    MoodChip(
                        label = stringResource(chip.labelRes()),
                        selected = state.recommendChip == chip && result !is AiTask.Idle,
                        onClick = {
                            query = ""
                            actions.onRecommend(chipPrompts.getValue(chip), chip)
                        },
                        onFocused = {},
                        focusRequester = null,
                    )
                }
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }, key = "status") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (state.recommendQuery.isNotEmpty() && result !is AiTask.Idle) {
                    BasicText(
                        stringResource(R.string.ai_you_asked, state.recommendQuery),
                        style = ScaryType.Caption.mixedLanguage(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                AiStatus(result, onRetry = { actions.onRecommend(state.recommendQuery, state.recommendChip) }, onSettings = actions.onOpenSettings)
                if (result is AiTask.Done) {
                    val reply = result.value.reply.ifEmpty {
                        if (result.value.picks.isEmpty()) stringResource(R.string.ai_reply_none) else stringResource(R.string.ai_reply_picked)
                    }
                    if (reply.isNotEmpty()) {
                        BasicText(
                            reply,
                            style = ScaryType.BodyStrong.mixedLanguage(),
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        )
                    }
                }
            }
        }
        if (result is AiTask.Done) {
            val picks = result.value.picks
            items(picks, key = { it.movie.id }) { pick ->
                PickCard(pick, isLocked(pick.movie), isWatched(pick.movie), pick === picks.first(), firstPickFocus) { actions.onOpenMovie(pick.movie) }
            }
        }
    }
}

/** A suggested film: its poster, and under it why the AI chose it. */
@Composable
private fun PickCard(pick: AiPick, locked: Boolean, watched: Boolean, first: Boolean, firstFocus: FocusRequester, onOpen: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.testTag(askPickTag(pick.movie.id))) {
        PosterCard(
            movie = pick.movie,
            onClick = onOpen,
            onFocused = {},
            width = 150.dp,
            locked = locked,
            watched = watched,
            focusRequester = firstFocus.takeIf { first },
        )
        // The AI's reason, or (from the free service in a language it writes poorly) the film's own traits.
        val reason = pick.why.ifEmpty {
            (listOfNotNull(pick.movie.fear?.let { stringResource(R.string.fear_short, it) }) + subgenreLine(pick.movie)).joinToString(" · ")
        }
        if (reason.isNotEmpty()) {
            BasicText(
                reason,
                style = ScaryType.Caption.copy(color = ScaryColors.Bone).mixedLanguage(),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.width(150.dp),
            )
        }
    }
}

@Composable
private fun ChatPane(state: AiState, language: Language, inputFocus: FocusRequester, actions: AskActions) {
    var message by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()
    LaunchedEffect(state.chat.size) {
        if (state.chat.isNotEmpty()) listState.animateScrollToItem(state.chat.lastIndex)
    }
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = SidePadding, vertical = 16.dp)
            .testTag(ASK_CHAT_TAG),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AiInput(
            value = message,
            onValueChange = { message = it },
            onSubmit = {
                actions.onSendChat(message)
                message = ""
            },
            label = stringResource(R.string.ai_tab_chat),
            hint = stringResource(R.string.ai_chat_hint),
            submitLabel = stringResource(R.string.ai_chat_send),
            language = language,
            focusRequester = inputFocus,
            modifier = Modifier.fillMaxWidth(0.85f),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (state.chat.isNotEmpty()) ScaryButton(stringResource(R.string.ai_new_chat), onClick = actions.onNewChat)
            BasicText(stringResource(R.string.ai_chat_note), style = ScaryType.Caption)
        }
        AiStatus(state.chatTask, onRetry = actions.onRetryChat, onSettings = actions.onOpenSettings)
        if (state.weakLanguage && state.chat.any { it.role == AiMessage.Role.ASSISTANT }) {
            BasicText(stringResource(R.string.ai_free_language_note), style = ScaryType.Caption)
        }
        LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.weight(1f)) {
            items(state.chat.withIndex().toList(), key = { it.index }) { (_, turn) -> Bubble(turn) }
        }
    }
}

/** One turn: the viewer's on one side, SCARY's on the other; each read out as it arrives. */
@Composable
private fun Bubble(turn: AiMessage) {
    val mine = turn.role == AiMessage.Role.USER
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.Start else Arrangement.End) {
        BasicText(
            text = turn.text,
            style = ScaryType.Body.copy(color = if (mine) ScaryColors.Bone else ScaryColors.Void).mixedLanguage(),
            modifier = Modifier
                .widthIn(max = 720.dp)
                .background(if (mine) ScaryColors.Crypt else ScaryColors.Bone, RoundedCornerShape(12.dp))
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .semantics { if (!mine) liveRegion = LiveRegionMode.Polite }
                // Focusable, so the remote can walk up a long conversation and the list scrolls with it.
                .focusable(),
        )
    }
}
