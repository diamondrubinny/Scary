// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary.core.ai

import io.github.diamondrubinny.scary.core.model.Movie
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject

/** A film the AI chose, with its reason. */
data class AiPick(val movie: Movie, val why: String)

/** The AI's choices and its one-line reply. */
data class AiPicks(val picks: List<AiPick>, val reply: String)

/**
 * Reads what an AI service answered, as hostile input: whatever it says, only films from the list SCARY sent (by their
 * number) come out, each once, and all text is plain and short: no links, no markup, no invisible direction marks.
 * The answer cannot make SCARY do anything; it only names films the viewer could already see.
 */
object AiAnswer {
    private const val MAX_WHY = 160
    private const val MAX_REPLY = 240
    const val MAX_TEXT = 1_500

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** The picks in [text] among [candidates] (numbered from 1), at most [max]; null when it cannot be read at all. */
    fun parsePicks(text: String, candidates: List<Movie>, max: Int): AiPicks? {
        val root = jsonObjectIn(text) ?: return loosePicks(text, candidates, max)
        val picks = (root["picks"] as? JsonArray).orEmpty().mapNotNull { element ->
            val pick = element as? JsonObject ?: return@mapNotNull null
            val number = (pick["n"] ?: pick["number"] ?: pick["id"]) as? JsonPrimitive ?: return@mapNotNull null
            val index = (number.intOrNull ?: number.content.trim().removePrefix("#").toIntOrNull()) ?: return@mapNotNull null
            val movie = candidates.getOrNull(index - 1) ?: return@mapNotNull null
            val why = (pick["why"] as? JsonPrimitive)?.takeIf { it.isString }?.content.orEmpty()
            AiPick(movie, plain(why, MAX_WHY, keepLines = false))
        }.distinctBy { it.movie.id }.take(max)
        val reply = (root["reply"] as? JsonPrimitive)?.takeIf { it.isString }?.content.orEmpty()
        return AiPicks(picks, plain(reply, MAX_REPLY, keepLines = false))
    }

    private val loosePick = Regex("\"n\"\\s*:\\s*\"?(\\d{1,4})\"?\\s*(?:,\\s*\"why\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\")?")
    private val looseReply = Regex("\"reply\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"")

    /**
     * Small models sometimes send JSON with a slip (a stray character after a string): the picks are then read one by
     * one with the same rules (numbers from the list only, each once, plain text). Null when there is not even one.
     */
    private fun loosePicks(text: String, candidates: List<Movie>, max: Int): AiPicks? {
        val picks = loosePick.findAll(text).mapNotNull { match ->
            val movie = match.groupValues[1].toIntOrNull()?.let { candidates.getOrNull(it - 1) } ?: return@mapNotNull null
            AiPick(movie, plain(unescape(match.groupValues[2]), MAX_WHY, keepLines = false))
        }.distinctBy { it.movie.id }.take(max).toList()
        if (picks.isEmpty()) return null
        val reply = looseReply.find(text)?.groupValues?.get(1)?.let(::unescape).orEmpty()
        return AiPicks(picks, plain(reply, MAX_REPLY, keepLines = false))
    }

    private fun unescape(text: String): String = text.replace("\\\"", "\"").replace("\\n", " ").replace("\\\\", "\\")

    /** Free text (chat, "about this film"): plain, at most [max] characters. */
    fun text(text: String, max: Int = MAX_TEXT): String = plain(text, max, keepLines = true)

    /** The JSON object in a reply that may wrap it in a code fence or a sentence. */
    private fun jsonObjectIn(text: String): JsonObject? {
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return try {
            json.parseToJsonElement(text.substring(start, end + 1)).jsonObject
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    private val links = Regex("(?i)\\b(?:https?://|www\\.)\\S+")
    private val markup = Regex("[*_`#>|~\\[\\]<>]")
    private val invisible = Regex("[\\u200B-\\u200F\\u202A-\\u202E\\u2066-\\u2069\\uFEFF]")
    private val spaces = Regex("[ \\t]+")
    private val blankLines = Regex("\\n{3,}")

    private fun plain(text: String, max: Int, keepLines: Boolean): String {
        var clean = text.replace(links, "").replace(invisible, "").replace(markup, "")
        clean = clean.filter { it == '\n' || !it.isISOControl() }
        clean = if (keepLines) clean.replace(spaces, " ").replace(blankLines, "\n\n") else clean.replace(Regex("\\s+"), " ")
        clean = clean.lines().joinToString("\n") { it.trim() }.trim()
        return if (clean.length <= max) clean else clean.take(max).trimEnd() + "…"
    }
}
