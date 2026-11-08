// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary.core.ai

import io.github.diamondrubinny.scary.core.catalog.Personalize
import io.github.diamondrubinny.scary.core.catalog.Search
import io.github.diamondrubinny.scary.core.model.Language
import io.github.diamondrubinny.scary.core.model.MediaKind
import io.github.diamondrubinny.scary.core.model.Movie

/** One turn of a conversation with an AI service. */
data class AiMessage(val role: Role, val text: String) {
    enum class Role { USER, ASSISTANT }
}

/** What is sent: instructions, the conversation, and a cap on the answer's length. */
data class AiPrompt(val system: String, val messages: List<AiMessage>, val maxTokens: Int)

/**
 * The requests SCARY sends, one per feature. What goes in is decided here and only here (and tested):
 * - the viewer's own words, cut to a sane length;
 * - public facts about films from the catalog (title, year, subgenres, fear level, length), numbered;
 * - a taste summary ([tasteSummary]) only when the viewer agreed to share it, never film names or ids.
 * Watch history, "Loved it", My list, device or account details never go in.
 */
object AiPrompts {
    const val MAX_QUERY_CHARS = 300
    const val MAX_CHAT_CHARS = 500
    const val MAX_CHAT_TURNS = 12
    const val MAX_PICKS = 8
    const val MAX_SEARCH_PICKS = 10

    fun languageName(language: Language): String = when (language) {
        Language.HE -> "Hebrew"
        Language.EN -> "English"
        Language.PT -> "Brazilian Portuguese"
        Language.RU -> "Russian"
        Language.AR -> "Arabic"
    }

    /** "What should I watch tonight?": up to [MAX_PICKS] of [candidates], by number, each with a short why. */
    fun recommend(query: String, candidates: List<Movie>, language: Language, tasteSummary: String?): AiPrompt {
        val lang = languageName(language)
        val system = buildString {
            append("You are SCARY, a horror and thriller guide on a TV. The viewer asks what to watch. ")
            append("Choose up to $MAX_PICKS titles ONLY from the numbered list below that best fit the request, best first. ")
            append("Never invent titles and never use a number that is not in the list. ")
            append("For each pick, \"why\" is 3 to 8 simple everyday words in $lang saying why it fits (for example: slow dread, ghosts, almost no blood), with no spoilers. ")
            append(JSON_SHAPE.replace("{lang}", lang))
            if (tasteSummary != null) append("\nThe viewer's taste, from their own choices on this TV: ").append(tasteSummary).append('.')
            append("\n\nTitles:\n").append(numbered(candidates))
        }
        return AiPrompt(system, listOf(AiMessage(AiMessage.Role.USER, clip(query, MAX_QUERY_CHARS))), maxTokens = 900)
    }

    /** "Smart search": the viewer describes a film; up to [MAX_SEARCH_PICKS] of [candidates] that match. */
    fun smartSearch(description: String, candidates: List<Movie>, language: Language): AiPrompt {
        val lang = languageName(language)
        val system = buildString {
            append("You are SCARY's search on a TV. The viewer describes a horror or thriller title they are trying to find ")
            append("(a scene, the plot, an actor, the era). Return up to $MAX_SEARCH_PICKS titles ONLY from the numbered list below ")
            append("that match the description, best match first; return none if nothing matches. Never invent titles. ")
            append("For each, \"why\" is 3 to 8 simple everyday words in $lang saying what matches, with no spoilers. ")
            append(JSON_SHAPE.replace("{lang}", lang))
            append("\n\nTitles:\n").append(numbered(candidates, synopsis = true))
        }
        return AiPrompt(system, listOf(AiMessage(AiMessage.Role.USER, clip(description, MAX_QUERY_CHARS))), maxTokens = 900)
    }

    /** "About this film": why watch it, who it is for, how scary, with no spoilers. */
    fun about(movie: Movie, language: Language): AiPrompt {
        val lang = languageName(language)
        val system = "You are SCARY, a horror and thriller guide on a TV. In $lang, with absolutely no spoilers " +
            "(no twists, no ending, no character deaths, nothing past the first act), write three short paragraphs: " +
            "why watch it, who it is for, and how scary it is (dread, jump scares, gore). At most 100 words in all. " +
            "Plain text only: no markdown, no lists, no links. $SIMPLE_WORDS"
        val facts = buildString {
            append(movie.title).append(" (").append(movie.yearsLabel).append("), ")
            append(if (movie.kind == MediaKind.SERIES) "series" else "film")
            traitsOf(movie).takeIf { it.isNotEmpty() }?.let { append(". ").append(it) }
            movie.director.takeIf { it.isNotBlank() }?.let { append(". Directed by ").append(it) }
            movie.synopsisEn.takeIf { it.isNotBlank() }?.let { append(". Synopsis: ").append(clip(it, 700)) }
        }
        return AiPrompt(system, listOf(AiMessage(AiMessage.Role.USER, facts)), maxTokens = 500)
    }

    /** The open chat: the last [MAX_CHAT_TURNS] turns, each cut to [MAX_CHAT_CHARS]. */
    fun chat(turns: List<AiMessage>, language: Language, tasteSummary: String?): AiPrompt {
        val lang = languageName(language)
        val system = buildString {
            append("You are SCARY, a friendly horror and thriller guide on a TV. Answer in $lang, briefly (at most 120 words), ")
            append("in plain text: no markdown, no lists, no links. No spoilers unless the viewer clearly asks for them. ")
            append("Talk about films, series and horror culture only; politely steer anything else back to films. ")
            append(SIMPLE_WORDS)
            if (tasteSummary != null) append(" The viewer's taste, from their own choices on this TV: ").append(tasteSummary).append('.')
        }
        val kept = turns.takeLast(MAX_CHAT_TURNS).map { it.copy(text = clip(it.text, MAX_CHAT_CHARS)) }
            .dropWhile { it.role != AiMessage.Role.USER }
        return AiPrompt(system, kept, maxTokens = 600)
    }

    /**
     * A few words on what the viewer likes and avoids, from [Personalize.Taste.leanings]: subgenres, moods, decades.
     * Null while the taste is not known. No film names, no people, no ids.
     */
    fun tasteSummary(taste: Personalize.Taste): String? {
        if (!taste.known) return null
        val liked = taste.leanings(5, liked = true).mapNotNull(::traitWords)
        val avoided = taste.leanings(2, liked = false).mapNotNull(::traitWords)
        if (liked.isEmpty() && avoided.isEmpty()) return null
        return buildString {
            if (liked.isNotEmpty()) append("likes ").append(liked.joinToString(", "))
            if (avoided.isNotEmpty()) {
                if (isNotEmpty()) append("; ")
                append("avoids ").append(avoided.joinToString(", "))
            }
        }
    }

    private fun traitWords(trait: String): String? {
        val value = trait.substringAfter(':')
        return when (trait.substringBefore(':')) {
            "s", "t" -> value.lowercase().replace('_', ' ')
            "d" -> value.toIntOrNull()?.let { "${it * 10}s" }
            "g" -> if (value == "HORROR") "horror" else "thrillers"
            else -> null
        }
    }

    /** "12. The Conjuring (2013), film · supernatural, possession · fear 4/5 · 112 min · IMDb 7.5". */
    fun numbered(candidates: List<Movie>, synopsis: Boolean = false): String = candidates.mapIndexed { index, movie ->
        buildString {
            append(index + 1).append(". ").append(clip(movie.title, 80)).append(" (").append(movie.yearsLabel).append(")")
            if (movie.kind == MediaKind.SERIES) append(", series")
            traitsOf(movie).takeIf { it.isNotEmpty() }?.let { append(" · ").append(it) }
            movie.fear?.let { append(" · fear ").append(it).append("/5") }
            movie.runtimeMin.takeIf { it > 0 }?.let { append(" · ").append(it).append(" min") }
            movie.imdbRating?.let { append(" · IMDb ").append(it) }
            if (synopsis) movie.synopsisEn.takeIf { it.isNotBlank() }?.let { append(" · ").append(clip(it, 160)) }
        }
    }.joinToString("\n")

    private fun traitsOf(movie: Movie): String =
        (movie.subgenres.map { it.name.lowercase().replace('_', ' ') } + movie.genres.map { it.lowercase() })
            .distinct().take(5).joinToString(", ")

    private fun clip(text: String, max: Int): String {
        val clean = text.filter { it == '\n' || !it.isISOControl() }.trim()
        return if (clean.length <= max) clean else clean.take(max).trimEnd() + "…"
    }

    /** Small models invent words in Hebrew, Arabic or Russian; asking for plain words helped (checked 8 Nov 2026). */
    private const val SIMPLE_WORDS =
        "Write the way a native speaker would: short sentences and common everyday words only; never invent words, " +
            "and if unsure of a word, use a simpler one."

    private const val JSON_SHAPE =
        "Answer with JSON only, exactly in this shape: {\"picks\":[{\"n\":12,\"why\":\"...\"}],\"reply\":\"one short friendly sentence in {lang}\"}. " +
            "If nothing fits, return an empty picks list and say so in reply."

    /** For smart search: the films the local search finds, then the ones whose plot shares words with the description. */
    fun searchCandidates(movies: List<Movie>, description: String, labels: (Movie) -> List<String>, limit: Int = 120): List<Movie> {
        val local = Search.query(movies, description, labels, limit = 40)
        val terms = AiCandidates.terms(description)
        val byPlot = if (terms.isEmpty()) emptyList() else movies.asSequence()
            .map { it to AiCandidates.textScore(it, terms) }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
            .map { it.first }
            .take(limit)
            .toList()
        val popular = movies.sortedByDescending { it.imdbRating ?: 0.0 }
        return (local + byPlot + popular).distinctBy { it.id }.take(limit)
    }
}

/**
 * The films an AI request chooses from, picked on the TV: those allowed (parental lock, family night, "Not for me" are
 * the caller's [allowed]), the ones whose words match the request first, then the viewer's taste, then the best
 * rated, preferring films not watched yet. Kept to [limit] so the request stays small (and cheap with a paid key).
 */
object AiCandidates {
    const val LIMIT = 80

    fun select(
        movies: List<Movie>,
        query: String,
        taste: Personalize.Taste,
        watched: Set<String>,
        limit: Int = LIMIT,
    ): List<Movie> {
        if (movies.isEmpty()) return emptyList()
        val terms = terms(query)
        val ranked = movies.map { movie ->
            val text = if (terms.isEmpty()) 0.0 else textScore(movie, terms).toDouble()
            val fit = if (taste.known) taste.score(movie).coerceIn(-4.0, 4.0) / 4.0 else 0.0
            val rating = ((movie.imdbRating ?: 6.5) - 6.5) / 3.0
            val unseen = if (movie.id in watched) -0.8 else 0.0
            movie to (2.0 * text + fit + rating + unseen)
        }.sortedByDescending { it.second }.map { it.first }
        // Half for the words of the request, half for taste and quality: the AI still sees a varied shelf.
        val byWords = if (terms.isEmpty()) emptyList() else ranked.filter { textScore(it, terms) > 0 }.take(limit / 2)
        return (byWords + ranked).distinctBy { it.id }.take(limit)
    }

    internal fun terms(text: String): List<String> =
        Search.normalize(text).split(' ').filter { it.length >= 3 && it !in STOP_WORDS }.distinct()

    internal fun textScore(movie: Movie, terms: List<String>): Int {
        val haystack = Search.normalize(
            listOf(movie.title, movie.titleHe, movie.synopsisEn, movie.synopsisHe, movie.director)
                .plus(movie.titles.values).plus(movie.synopses.values)
                .plus(movie.subgenres.map { it.name.replace('_', ' ') }).plus(movie.genres)
                .joinToString(" "),
        )
        return terms.count { haystack.contains(it) }
    }

    private val STOP_WORDS = setOf(
        "the", "and", "with", "for", "something", "like", "movie", "film", "films", "movies", "want", "watch", "tonight",
        "that", "this", "not", "but", "about", "some", "very", "more", "less", "scary", "horror",
    )
}
