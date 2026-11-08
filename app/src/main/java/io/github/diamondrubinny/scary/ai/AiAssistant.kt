// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary.ai

import android.os.SystemClock
import io.github.diamondrubinny.scary.core.ai.AiAnswer
import io.github.diamondrubinny.scary.core.ai.AiCandidates
import io.github.diamondrubinny.scary.core.ai.AiMessage
import io.github.diamondrubinny.scary.core.ai.AiPicks
import io.github.diamondrubinny.scary.core.ai.AiPrompt
import io.github.diamondrubinny.scary.core.ai.AiPrompts
import io.github.diamondrubinny.scary.core.ai.AiProvider
import io.github.diamondrubinny.scary.core.catalog.Personalize
import io.github.diamondrubinny.scary.core.model.Language
import io.github.diamondrubinny.scary.core.model.Movie
import io.github.diamondrubinny.scary.data.AiRepository
import io.github.diamondrubinny.scary.data.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Why an AI request gave nothing, in words the screen can say. */
enum class AiError { WAIT, NEEDS_KEY, BAD_KEY, NO_CREDIT, NO_MODEL, UNREACHABLE, FAILED, NOT_UNDERSTOOD, OFF, CHAT_BLOCKED, NO_FILMS }

/** One AI request on screen: not asked, thinking, answered, or not. */
sealed interface AiTask<out T> {
    data object Idle : AiTask<Nothing>
    data object Loading : AiTask<Nothing>
    data class Done<T>(val value: T) : AiTask<T>
    /** [retryAtMillis]: when the free service takes the next request (SystemClock.elapsedRealtime), for [AiError.WAIT]. */
    data class Failed(val error: AiError, val retryAtMillis: Long = 0) : AiTask<Nothing>
}

/** One-press requests on the home screen and the "Ask SCARY" screen; each narrows the shelf on the TV first. */
enum class AiChip(val key: String) {
    LIKE_LOVED("like_loved"),
    SHORT("short"),
    GENTLE("gentle"),
    CLASSIC("classic"),
    SCARIEST("scariest");

    fun allows(movie: Movie): Boolean = when (this) {
        LIKE_LOVED -> true
        SHORT -> movie.runtimeMin in 1..SHORT_MINUTES
        GENTLE -> (movie.fear ?: 3) <= 2
        CLASSIC -> movie.year in 1..CLASSIC_BEFORE
        SCARIEST -> (movie.fear ?: 0) >= 4 || (movie.fear == null && (movie.imdbRating ?: 0.0) >= 7.0)
    }

    companion object {
        const val SHORT_MINUTES = 95
        const val CLASSIC_BEFORE = 1989
        fun fromKey(key: String?): AiChip? = entries.firstOrNull { it.key == key }
    }
}

/**
 * What the AI may choose from right now, given by the home screen: the films and series the viewer may see (the
 * parental lock, family night and "Not for me" already applied), the taste worked out on the TV, and whether the open
 * chat is allowed (not with the parental lock or family night on: a free conversation cannot be kept gentle).
 * [shelf] is the current tab's kind (films or series); [everything] both, for smart search.
 */
class AiContext(
    val shelf: List<Movie>,
    val everything: List<Movie>,
    val taste: Personalize.Taste,
    val watched: Set<String>,
    val language: Language,
    val chatBlocked: Boolean,
)

data class AiState(
    val recommendQuery: String = "",
    val recommendChip: AiChip? = null,
    val recommend: AiTask<AiPicks> = AiTask.Idle,
    val searchQuery: String = "",
    val search: AiTask<AiPicks> = AiTask.Idle,
    val aboutId: String? = null,
    val about: AiTask<String> = AiTask.Idle,
    val chat: List<AiMessage> = emptyList(),
    val chatTask: AiTask<Unit> = AiTask.Idle,
    /** The service the viewer is asked about before anything is sent to it, the first time. */
    val consent: AiProvider? = null,
    /** "Test connection" in settings. */
    val test: AiTask<String> = AiTask.Idle,
)

/**
 * The four AI features: "What should I watch tonight?", smart search, "About this film", and the open chat. Nothing is
 * sent before the viewer agrees to the chosen service (once per service), and only what [AiPrompts] puts in a request.
 */
class AiAssistant(
    private val repository: AiRepository,
    private val settings: SettingsRepository,
    private val scope: CoroutineScope,
    private val context: () -> AiContext?,
) {
    private val _state = MutableStateFlow(AiState())
    val state: StateFlow<AiState> = _state.asStateFlow()

    private var pending: (suspend () -> Unit)? = null
    private var job: Job? = null
    private val aboutCache = HashMap<String, String>()

    fun recommend(query: String, chip: AiChip? = null) {
        val text = query.trim()
        if (text.isEmpty()) return
        _state.update { it.copy(recommendQuery = text, recommendChip = chip) }
        run({ copy(recommend = it) }) { provider, ctx, shareTaste ->
            val pool = if (chip == null) ctx.shelf else ctx.shelf.filter(chip::allows)
            val candidates = AiCandidates.select(pool, text, ctx.taste, ctx.watched)
            if (candidates.isEmpty()) return@run AiTask.Failed(AiError.NO_FILMS)
            val taste = if (shareTaste) AiPrompts.tasteSummary(ctx.taste) else null
            ask(provider, AiPrompts.recommend(text, candidates, ctx.language, taste)) { reply ->
                AiAnswer.parsePicks(reply, candidates, AiPrompts.MAX_PICKS)
            }
        }
    }

    /** [labels]: genre and subgenre names in the viewer's language, so the local search half matches their words. */
    fun smartSearch(query: String, labels: (Movie) -> List<String> = { emptyList() }) {
        val text = query.trim()
        if (text.isEmpty()) return
        _state.update { it.copy(searchQuery = text) }
        run({ copy(search = it) }) { provider, ctx, _ ->
            val candidates = AiPrompts.searchCandidates(ctx.everything, text, labels)
            if (candidates.isEmpty()) return@run AiTask.Failed(AiError.NO_FILMS)
            ask(provider, AiPrompts.smartSearch(text, candidates, ctx.language)) { reply ->
                AiAnswer.parsePicks(reply, candidates, AiPrompts.MAX_SEARCH_PICKS)
            }
        }
    }

    fun clearSearch() = _state.update { it.copy(searchQuery = "", search = AiTask.Idle) }

    fun about(movie: Movie) {
        aboutCache[movie.id]?.let { cached ->
            _state.update { it.copy(aboutId = movie.id, about = AiTask.Done(cached)) }
            return
        }
        _state.update { it.copy(aboutId = movie.id) }
        run({ copy(about = it) }) { provider, ctx, _ ->
            ask(provider, AiPrompts.about(movie, ctx.language)) { reply ->
                AiAnswer.text(reply).takeIf { it.isNotEmpty() }?.also { aboutCache[movie.id] = it }
            }
        }
    }

    fun sendChat(message: String) {
        val text = message.trim()
        if (text.isEmpty()) return
        val ctx = context()
        if (ctx?.chatBlocked == true) {
            _state.update { it.copy(chatTask = AiTask.Failed(AiError.CHAT_BLOCKED)) }
            return
        }
        _state.update { it.copy(chat = it.chat + AiMessage(AiMessage.Role.USER, text.take(AiPrompts.MAX_CHAT_CHARS))) }
        retryChat()
    }

    /** Sends the conversation again (after a wait or an error), without repeating the viewer's message. */
    fun retryChat() {
        if (_state.value.chat.lastOrNull()?.role != AiMessage.Role.USER) return
        run({ copy(chatTask = it) }) { provider, ctx, shareTaste ->
            if (ctx.chatBlocked) return@run AiTask.Failed(AiError.CHAT_BLOCKED)
            val taste = if (shareTaste) AiPrompts.tasteSummary(ctx.taste) else null
            ask(provider, AiPrompts.chat(_state.value.chat, ctx.language, taste)) { reply ->
                AiAnswer.text(reply).takeIf { it.isNotEmpty() }?.also { answer ->
                    _state.update { it.copy(chat = it.chat + AiMessage(AiMessage.Role.ASSISTANT, answer)) }
                }?.let { }
            }
        }
    }

    fun clearChat() {
        job?.cancel()
        _state.update { it.copy(chat = emptyList(), chatTask = AiTask.Idle) }
    }

    /** "Test connection" in settings: a one-word answer from the chosen service. */
    fun testConnection() {
        run({ copy(test = it) }) { provider, ctx, _ ->
            val prompt = AiPrompt(
                system = "Reply with one short friendly sentence in ${AiPrompts.languageName(ctx.language)} confirming you are connected.",
                messages = listOf(AiMessage(AiMessage.Role.USER, "Hello")),
                maxTokens = 60,
            )
            ask(provider, prompt) { reply -> AiAnswer.text(reply, 200).takeIf { it.isNotEmpty() } }
        }
    }

    fun clearTest() = _state.update { it.copy(test = AiTask.Idle) }

    /** The viewer agreed to send requests to the service asked about: remembered, and the request goes ahead. */
    fun agree() {
        val provider = _state.value.consent ?: return
        _state.update { it.copy(consent = null) }
        val action = pending
        pending = null
        scope.launch {
            settings.setAiConsent(provider, true)
            action?.invoke()
        }
    }

    /** "Not now": nothing is sent; the request that asked is dropped. */
    fun decline() {
        pending = null
        _state.update { s ->
            s.copy(
                consent = null,
                recommend = s.recommend.takeUnless { it is AiTask.Loading } ?: AiTask.Idle,
                search = s.search.takeUnless { it is AiTask.Loading } ?: AiTask.Idle,
                about = s.about.takeUnless { it is AiTask.Loading } ?: AiTask.Idle,
                chatTask = s.chatTask.takeUnless { it is AiTask.Loading } ?: AiTask.Idle,
                test = s.test.takeUnless { it is AiTask.Loading } ?: AiTask.Idle,
            )
        }
    }

    /**
     * Runs one request: AI must be on, the viewer must have agreed to the chosen service (asked first if not), and
     * the home screen must have a shelf. [set] puts the task's progress in the state.
     */
    private fun <T> run(
        set: AiState.(AiTask<T>) -> AiState,
        work: suspend (AiProvider, AiContext, Boolean) -> AiTask<T>,
    ) {
        job?.cancel()
        _state.update { it.set(AiTask.Loading) }
        val action: suspend () -> Unit = {
            val prefs = settings.prefs.first()
            val provider = AiProvider.fromKey(prefs.aiProvider) ?: AiProvider.DEFAULT
            val ctx = context()
            val result = when {
                !prefs.aiEnabled -> AiTask.Failed(AiError.OFF)
                ctx == null -> AiTask.Failed(AiError.NO_FILMS)
                else -> work(provider, ctx, prefs.aiShareTaste)
            }
            _state.update { it.set(result) }
        }
        job = scope.launch {
            val prefs = settings.prefs.first()
            val provider = AiProvider.fromKey(prefs.aiProvider) ?: AiProvider.DEFAULT
            if (prefs.aiEnabled && provider.key !in prefs.aiConsented) {
                pending = action
                _state.update { it.copy(consent = provider) }
            } else {
                action()
            }
        }
    }

    /** Sends [prompt]; [read] turns the reply into the result, or null when it cannot be understood. */
    private suspend fun <T> ask(provider: AiProvider, prompt: AiPrompt, read: (String) -> T?): AiTask<T> =
        when (val result = repository.complete(provider, prompt)) {
            is AiRepository.Result.Ok -> read(result.text)?.let { AiTask.Done(it) } ?: AiTask.Failed(AiError.NOT_UNDERSTOOD)
            is AiRepository.Result.Wait -> AiTask.Failed(AiError.WAIT, SystemClock.elapsedRealtime() + result.seconds * 1000L)
            AiRepository.Result.NeedsKey -> AiTask.Failed(AiError.NEEDS_KEY)
            AiRepository.Result.BadKey -> AiTask.Failed(AiError.BAD_KEY)
            AiRepository.Result.NoCredit -> AiTask.Failed(AiError.NO_CREDIT)
            AiRepository.Result.NoModel -> AiTask.Failed(AiError.NO_MODEL)
            AiRepository.Result.Unreachable -> AiTask.Failed(AiError.UNREACHABLE)
            is AiRepository.Result.Failed -> AiTask.Failed(AiError.FAILED)
        }
}
