// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary.core.catalog

import io.github.diamondrubinny.scary.core.cinemeta.Cinemeta
import io.github.diamondrubinny.scary.core.cinemeta.CinemetaMeta
import io.github.diamondrubinny.scary.core.model.MediaKind

/**
 * Fills the library's categories (its curated rows: zombies, vampires, haunted houses…) with more films from
 * Cinemeta's big catalogs, which say a film's genres, country, year, rating and plot. Each category has a rule on
 * those, tuned on 3,500 real films for few wrong guesses: a word in the title or plot ("vampire", "haunted house"),
 * the right genre, and words that rule a film out (a werewolf film is not a slasher). Categories with no fair rule
 * (fear level 5 is the library's own judgement) stay as curated. The curated films always come first.
 */
object Categories {

    /** The categories with a rule: the library's collection ids. */
    val ids: Set<String> get() = RULES.keys + setOf(ASIAN_HORROR, CLASSICS, SERIES_MUST)

    /** Whether [meta] belongs in the category [collectionId]: films for film categories, series for series ones. */
    fun matches(collectionId: String, meta: CinemetaMeta): Boolean {
        if (!Cinemeta.onTheme(meta)) return false
        val series = collectionId.startsWith(SERIES_PREFIX)
        if ((meta.kind == MediaKind.SERIES) != series) return false
        val genres = meta.genres.mapTo(HashSet()) { it.lowercase() }
        val horror = "horror" in genres
        val thriller = "thriller" in genres || "mystery" in genres
        return when (collectionId) {
            ASIAN_HORROR -> horror && meta.country.substringBefore(',').trim().lowercase() in ASIA
            CLASSICS -> horror && (meta.year ?: 0) in 1..1989 && (meta.imdbRating ?: 0.0) >= CLASSIC_RATING
            SERIES_MUST -> horror && (meta.imdbRating ?: 0.0) >= MUST_SEE_RATING
            else -> {
                val rule = RULES[collectionId] ?: return false
                val text = meta.name + ". " + meta.description
                when {
                    rule.horror && !horror -> false
                    rule.thriller && !thriller -> false
                    rule.excludedGenres.any(genres::contains) -> false
                    rule.needsGenre != null && rule.needsGenre !in genres -> false
                    rule.excludedWords?.containsMatchIn(text) == true -> false
                    else -> rule.words.containsMatchIn(text)
                }
            }
        }
    }

    /**
     * [pool] sorted into the categories, in the pool's order (Cinemeta's: most popular first), at most [max] each,
     * and without the films a category already holds ([known] IMDb ids per category).
     */
    fun sort(pool: List<CinemetaMeta>, known: Map<String, Set<String>> = emptyMap(), max: Int = MAX_PER_CATEGORY): Map<String, List<String>> =
        ids.associateWith { id ->
            val skip = known[id].orEmpty()
            pool.asSequence().filter { it.imdbId !in skip && matches(id, it) }.map { it.imdbId }.distinct().take(max).toList()
        }.filterValues { it.isNotEmpty() }

    private class Rule(
        val words: Regex,
        val horror: Boolean = true,
        val thriller: Boolean = false,
        val excludedGenres: Set<String> = emptySet(),
        val excludedWords: Regex? = null,
        val needsGenre: String? = null,
    )

    private fun words(vararg words: String) = Regex("\\b(?:" + words.joinToString("|") + ")", RegexOption.IGNORE_CASE)

    private val monsters = words("werewol", "vampir", "zombie", "ghosts?\\b", "demon", "alien", "creature", "monster", "possess")

    // "ghosts?\\b", not "ghost": Ghostface is Scream's killer, not a ghost.
    private val supernatural = arrayOf(
        "ghosts?\\b", "ghostly", "demon", "possess", "exorcis", "poltergeist", "evil spirit", "supernatural", "satan", "the devil",
        "curse[ds]?", "occult", "malevolent (?:spirit|entity|force)", "evil entity",
    )
    private val hauntedHouse = arrayOf(
        "haunted (?:house|home|mansion|manor|hotel|estate)",
        "(?:house|home|mansion|manor|estate)\\b.{0,60}\\b(?:ghost|haunt|presence|spirit|paranormal|poltergeist)",
        "(?:ghost|haunt|presence|spirit|paranormal|poltergeist)\\w*\\b.{0,60}\\b(?:house|home|mansion|manor)",
    )
    private val zombies = arrayOf("zombie", "undead", "walking dead", "flesh-eating", "the dead (?:rise|return|walk|come back)")
    private val vampires = arrayOf("vampir", "dracula", "nosferatu", "bloodsuck", "blood-suck", "blood sucking")
    private val creatures = arrayOf(
        "creature", "monster", "alien", "shark", "beast", "werewol", "kaiju", "crocodile", "alligator",
        "giant (?:spider|snake|insect|ant|worm)", "mutant", "sea creature", "predator\\b",
    )

    private val RULES: Map<String, Rule> = mapOf(
        "psychological_horror" to Rule(
            words(
                "psycholog", "grief", "griev", "madness", "sanity", "insanity", "paranoi", "delusion", "hallucinat",
                "trauma", "unravel", "her mind", "his mind", "losing (?:her|his) mind",
            ),
            excludedGenres = setOf("action"),
        ),
        "supernatural" to Rule(words(*supernatural)),
        "psychological_thriller" to Rule(
            words("obsess", "manipulat", "psycholog", "paranoi", "amnesia", "gaslight", "mind games", "deceiv", "decepti", "unravel", "doppelg"),
            horror = false,
            thriller = true,
            excludedGenres = setOf("action", "horror", "war", "adventure", "comedy"),
        ),
        "crime_thriller" to Rule(
            words("detective", "investigat", "serial killer", "homicide", "fbi", "police", "murder", "killer", "heist", "crime", "case"),
            horror = false,
            thriller = true,
            excludedGenres = setOf("horror", "comedy", "adventure", "sci-fi", "fantasy"),
            needsGenre = "crime",
        ),
        "social" to Rule(
            words("racis", "racial", "class divide", "gentrif", "privileg", "the rich", "wealthy (?:family|elite|couple)", "elite", "patriarch", "social media", "inequality"),
            excludedGenres = setOf("action"),
        ),
        "survival" to Rule(
            words("surviv", "trapped", "stranded", "siege", "fight for (?:their|his|her) lives", "stay alive", "wilderness", "hunted down"),
            excludedGenres = setOf("action", "comedy"),
            excludedWords = monsters,
        ),
        "slashers" to Rule(
            words(
                "masked", "slasher", "machete", "summer camp", "serial killer", "killer (?:stalks|picks off|targets|terrori)",
                "picks? (?:them|off)", "stalked by", "knife-wielding", "murderous (?:psycho|maniac)", "maniac",
            ),
            excludedWords = words(*supernatural, "werewol", "vampir", "zombie", "alien", "creature", "monster"),
        ),
        "found_footage" to Rule(
            words("found footage", "footage", "camcorder", "video camera", "livestream", "live-stream", "webcam", "vlog", "documentary crew"),
        ),
        "folk_horror" to Rule(
            words("pagan", "cults?\\b", "ritual", "folk", "commune", "sacrific", "ancient (?:tradition|ritual|custom)", "village (?:elders|tradition|secret|ritual)"),
        ),
        // "Undead" is a vampire's word too.
        "zombies" to Rule(words(*zombies), excludedWords = words(*vampires)),
        "vampires" to Rule(words(*vampires)),
        "body_horror" to Rule(
            words("body horror", "mutat", "metamorph", "disfigur", "parasit", "grotesque", "flesh", "his body", "her body", "transformation"),
            excludedWords = words("zombie", "vampir", "werewol"),
        ),
        "haunted_houses" to Rule(words(*hauntedHouse)),
        "creatures" to Rule(words(*creatures), excludedWords = words("vampir", "zombie")),
        // The series tab's rows.
        "series_ghosts" to Rule(words(*supernatural, *hauntedHouse)),
        "series_monsters" to Rule(words(*zombies, *vampires, *creatures)),
        "series_mystery" to Rule(
            words("mystery", "mysterious", "secret", "disappear", "vanish", "investigat", "detective", "murder", "small town", "conspiracy", "paranoi"),
            horror = false,
            thriller = true,
            excludedGenres = setOf("comedy", "action"),
        ),
        "series_games" to Rule(
            words("game", "contest", "competition", "surviv", "trapped", "deadly", "fight for (?:their|his|her) lives", "escape"),
            horror = false,
            thriller = true,
            excludedGenres = setOf("comedy"),
        ),
    )

    private const val ASIAN_HORROR = "asian_horror"
    private const val CLASSICS = "classics"
    private const val SERIES_MUST = "series_must"
    private const val SERIES_PREFIX = "series_"
    private const val CLASSIC_RATING = 7.2
    private const val MUST_SEE_RATING = 8.0
    const val MAX_PER_CATEGORY = 300

    /** A film's first country, as Cinemeta lists it (co-productions name the main one first). */
    private val ASIA = setOf(
        "japan", "south korea", "korea", "china", "hong kong", "taiwan", "thailand", "indonesia", "philippines",
        "malaysia", "india", "vietnam", "singapore",
    )
}
