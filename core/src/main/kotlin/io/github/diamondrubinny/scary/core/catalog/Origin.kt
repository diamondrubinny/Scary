// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary.core.catalog

import io.github.diamondrubinny.scary.core.cinemeta.CinemetaMeta

/**
 * Where a film really comes from, for the rows that are about a place (European, Spanish-language, Asian horror,
 * giallo). Cinemeta lists a film's countries in no reliable order: American films often come with a European country
 * first ("Germany, United States" for Constantine, The Cell, Blade II), because a German fund paid for part of them.
 * So the first country only *proposes* a film for a row; when the United States is listed too, the film's original
 * language decides: *Lamb* ("Iceland, Sweden, Poland, United States") is in Icelandic and stays, *Constantine* is in
 * English and goes. With no language known yet the film is held back ([Verdict.UNKNOWN]), never guessed at, and the app
 * asks TMDB for the language of exactly those films.
 */
object Origin {
    enum class Region(val countries: Set<String>, val languages: Set<String>) {
        /** Europe, as the library's European row has it (the United Kingdom and Ireland fill other rows). */
        EUROPE(
            countries = setOf(
                "france", "germany", "italy", "spain", "belgium", "netherlands", "denmark", "sweden", "norway", "finland",
                "iceland", "austria", "switzerland", "poland", "czech republic", "czechia", "hungary", "romania", "bulgaria",
                "greece", "portugal", "serbia", "croatia", "slovenia", "slovakia", "estonia", "latvia", "lithuania", "ukraine",
                "russia", "west germany", "east germany", "luxembourg", "bosnia and herzegovina", "north macedonia", "yugoslavia",
                "czechoslovakia", "monaco", "albania", "montenegro", "belarus", "moldova", "malta", "cyprus",
            ),
            languages = setOf(
                "de", "fr", "it", "es", "pt", "nl", "da", "sv", "no", "nb", "nn", "fi", "is", "pl", "cs", "sk", "hu", "ro",
                "bg", "el", "sr", "hr", "sl", "et", "lv", "lt", "uk", "ru", "sq", "mk", "bs", "ca", "eu", "gl", "lb", "be",
                "mt", "ga", "cy", "yi",
            ),
        ),
        SPANISH(
            countries = setOf(
                "spain", "mexico", "argentina", "chile", "colombia", "peru", "uruguay", "venezuela", "cuba", "bolivia", "ecuador",
                "paraguay", "dominican republic", "costa rica", "guatemala", "puerto rico", "panama", "honduras", "el salvador",
                "nicaragua",
            ),
            languages = setOf("es", "ca", "gl", "eu"),
        ),
        /** Asia without India (hidden, see [OriginFilter]) and without the Arab world. */
        ASIA(
            countries = setOf(
                "japan", "south korea", "korea", "china", "hong kong", "taiwan", "thailand", "indonesia", "philippines",
                "malaysia", "vietnam", "singapore",
            ),
            languages = setOf("ja", "ko", "zh", "cn", "th", "vi", "id", "ms", "tl", "fil", "km", "lo", "my", "mn"),
        ),
        ITALY(countries = setOf("italy"), languages = setOf("it")),
    }

    enum class Verdict {
        /** Belongs to the region. */
        IN,

        /** Does not: its first country is elsewhere, or it is an American film with a European producer. */
        OUT,

        /** Proposed by its first country, but American too and its language is not known yet: held back. */
        UNKNOWN,
    }

    private const val UNITED_STATES = "united states"

    /** A film's countries as Cinemeta lists them, lower case ("Germany, United States" -> [germany, united states]). */
    fun countries(meta: CinemetaMeta): List<String> =
        meta.country.split(',').map { it.trim().lowercase() }.filter { it.isNotEmpty() }

    fun listsUnitedStates(meta: CinemetaMeta): Boolean = UNITED_STATES in countries(meta)

    /** Whether the first country alone proposes [meta] for [region]. */
    fun proposedFor(region: Region, meta: CinemetaMeta): Boolean = countries(meta).firstOrNull() in region.countries

    fun verdict(region: Region, meta: CinemetaMeta): Verdict {
        if (!proposedFor(region, meta)) return Verdict.OUT
        if (!listsUnitedStates(meta)) return Verdict.IN
        val language = meta.originalLanguage.trim().lowercase()
        return when {
            language.isEmpty() -> Verdict.UNKNOWN
            language in region.languages -> Verdict.IN
            else -> Verdict.OUT
        }
    }

    /**
     * How sure the place is: 0 when the countries alone say so (no American producer), 1 when only the language does.
     * Rows list the sure ones first.
     */
    fun fit(region: Region, meta: CinemetaMeta): Int = if (listsUnitedStates(meta)) 1 else 0

    /** Whether the app must look up the original language of [meta]: some region proposes it, and it lists the United States. */
    fun needsLanguage(meta: CinemetaMeta): Boolean =
        meta.originalLanguage.isEmpty() && listsUnitedStates(meta) && Region.entries.any { proposedFor(it, meta) }
}
