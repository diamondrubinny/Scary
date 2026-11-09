// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary.core.catalog

import io.github.diamondrubinny.scary.core.cinemeta.Cinemeta
import io.github.diamondrubinny.scary.core.cinemeta.CinemetaMeta
import io.github.diamondrubinny.scary.core.cinemeta.EndlessCursor
import io.github.diamondrubinny.scary.core.model.MediaKind
import io.github.diamondrubinny.scary.core.model.Subgenre

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
            else -> RULES[collectionId]?.let { applies(it, meta, genres) } == true
        }
    }

    private fun applies(rule: Rule, meta: CinemetaMeta, genres: Set<String>): Boolean {
        val horror = "horror" in genres
        val thriller = "thriller" in genres || "mystery" in genres
        val text = meta.name + ". " + meta.description
        return when {
            rule.horror && !horror -> false
            rule.thriller && !thriller -> false
            rule.excludedGenres.any(genres::contains) -> false
            rule.needsGenre != null && rule.needsGenre !in genres -> false
            rule.excludedWords?.containsMatchIn(text) == true -> false
            else -> rule.words.containsMatchIn(text)
        }
    }

    /**
     * What kind of horror or thriller [meta] is, by the same rules as the categories (a film or a series alike): the
     * subgenres a film from the web gets, as the library's films have them from its editors. Empty when off theme.
     */
    fun subgenresOf(meta: CinemetaMeta): List<Subgenre> {
        if (!Cinemeta.onTheme(meta)) return emptyList()
        val genres = meta.genres.mapTo(HashSet()) { it.lowercase() }
        val found = LinkedHashSet<Subgenre>()
        SUBGENRE_RULES.forEach { (id, subgenre) -> if (RULES[id]?.let { applies(it, meta, genres) } == true) found += subgenre }
        EXTRA_RULES.forEach { (subgenre, rule) -> if (applies(rule, meta, genres)) found += subgenre }
        if ("mystery" in genres) found += Subgenre.MYSTERY
        return found.toList()
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

    /** Which subgenre each rule tells. */
    private val SUBGENRE_RULES: List<Pair<String, Subgenre>> = listOf(
        "psychological_horror" to Subgenre.PSYCHOLOGICAL,
        "psychological_thriller" to Subgenre.PSYCHOLOGICAL,
        "supernatural" to Subgenre.SUPERNATURAL,
        "haunted_houses" to Subgenre.HAUNTED_HOUSE,
        "crime_thriller" to Subgenre.CRIME,
        "social" to Subgenre.SOCIAL,
        "survival" to Subgenre.SURVIVAL,
        "slashers" to Subgenre.SLASHER,
        "found_footage" to Subgenre.FOUND_FOOTAGE,
        "folk_horror" to Subgenre.FOLK,
        "zombies" to Subgenre.ZOMBIE,
        "vampires" to Subgenre.VAMPIRE,
        "body_horror" to Subgenre.BODY,
        "creatures" to Subgenre.CREATURE,
        "series_mystery" to Subgenre.MYSTERY,
    )

    /**
     * The horror subgenre a library row is about ("slashers" → slasher), so films of the full index that Wikidata files
     * under it can go on in that row. Null for rows that are not one subgenre, and for the thriller rows (the index
     * holds horror only).
     */
    fun subgenreOfRow(collectionId: String): Subgenre? = INDEX_ONLY_ROWS[collectionId]
        ?: SUBGENRE_RULES.firstOrNull { (id, _) -> id == collectionId && id != "psychological_thriller" && id != "crime_thriller" }?.second

    /** Library rows that go on with films of the full index only (Cinemeta's plots tell these subgenres poorly). */
    private val INDEX_ONLY_ROWS = mapOf(
        "giallo" to Subgenre.GIALLO,
        "witches" to Subgenre.WITCH,
        "cosmic_horror" to Subgenre.COSMIC,
        "animal_attacks" to Subgenre.ANIMAL,
        "horror_comedy" to Subgenre.COMEDY,
    )

    /** Subgenres the library has with no row of their own. */
    private val EXTRA_RULES: List<Pair<Subgenre, Rule>> = listOf(
        Subgenre.GHOST to Rule(words("ghosts?\\b", "ghostly", "poltergeist", "vengeful spirit", "haunted (?:house|home|mansion|manor|hotel|by (?:a|the) (?:ghost|spirit))")),
        Subgenre.POSSESSION to Rule(words("possess", "exorcis")),
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

/**
 * Where a category goes on once its films from the daily pool run out (scrolling to its end): the pages of Cinemeta's
 * catalogs after the ones the pool read, then back through the years, year by year, to [EndlessCursor.FIRST_YEAR];
 * each page sorted by the category's rule. A cursor packs the stage (which catalog) and the place in it.
 */
object CategoryPaging {
    /** Bigger than any skip in one catalog ([Cinemeta.MAX_SKIP]). */
    const val STAGE_STEP = 10_000_000

    /** A page to read: the "top" catalog of [genre], or (genre null) the catalog of [year]; from [skip]. */
    data class Page(val genre: String?, val year: Int?, val skip: Int)

    /**
     * The daily pool the categories are filled from: Cinemeta's "top" catalogs, and how many pages (50 each) of each,
     * about 3,500 films and 1,500 series.
     */
    val POOL: List<Triple<MediaKind, String, Int>> = listOf(
        Triple(MediaKind.MOVIE, "Horror", 36),
        Triple(MediaKind.MOVIE, "Thriller", 30),
        Triple(MediaKind.MOVIE, "Mystery", 10),
        Triple(MediaKind.SERIES, "Horror", 10),
        Triple(MediaKind.SERIES, "Thriller", 10),
        Triple(MediaKind.SERIES, "Mystery", 10),
    )

    /** The "top" catalogs to go on with, and where in each the pool stopped. */
    fun tops(kind: MediaKind): List<Pair<String, Int>> =
        POOL.filter { it.first == kind }.map { (_, genre, pages) -> genre to pages * Cinemeta.PAGE_SIZE }

    /** The page [cursor] reads for a category of [kind], whose year pages start at [startYear]; null past the last. */
    fun page(kind: MediaKind, cursor: Int, startYear: Int): Page? {
        val tops = tops(kind)
        val stage = cursor / STAGE_STEP
        if (stage < tops.size) {
            val (genre, start) = tops[stage]
            return Page(genre, null, start + cursor % STAGE_STEP)
        }
        val inYears = cursor - tops.size * STAGE_STEP
        val year = EndlessCursor.year(startYear, inYears) ?: return null
        return Page(null, year, EndlessCursor.skip(inYears))
    }

    /** The cursor after a page that came back with [pageSize] entries: the next catalog when one runs out. */
    fun after(kind: MediaKind, cursor: Int, pageSize: Int, startYear: Int): Int? {
        val tops = tops(kind)
        val stage = cursor / STAGE_STEP
        if (stage < tops.size) {
            val page = page(kind, cursor, startYear) ?: return null
            val exhausted = pageSize == 0 || page.skip + Cinemeta.PAGE_SIZE > Cinemeta.MAX_SKIP
            return if (exhausted) (stage + 1) * STAGE_STEP else cursor + Cinemeta.PAGE_SIZE
        }
        val inYears = cursor - tops.size * STAGE_STEP
        val next = EndlessCursor.after(inYears, pageSize, yearChain = true) ?: return null
        return if (EndlessCursor.year(startYear, next) == null) null else tops.size * STAGE_STEP + next
    }
}
