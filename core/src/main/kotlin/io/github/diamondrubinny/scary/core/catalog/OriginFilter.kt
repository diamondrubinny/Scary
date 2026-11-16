// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary.core.catalog

import io.github.diamondrubinny.scary.core.cinemeta.CinemetaMeta
import io.github.diamondrubinny.scary.core.cinemeta.OnlineRow

/**
 * Films that are kept off every shelf: Arab and Indian ones (the owner's choice, 16.11.2026). Cinemeta names a film's
 * countries with the main one first, so a film is hidden when that first country is an Arab League member or India;
 * TMDB's results carry no country but an original language, so a film in Arabic or in one of India's main languages is
 * hidden. A co-production led by another country stays. Search does not use this: a viewer who looks for a film finds it.
 * Everything here is one list, so changing the choice is changing it here.
 */
object OriginFilter {
    /** Countries as IMDb (and so Cinemeta) names them, lower case: the Arab League's members and India. */
    private val COUNTRIES = setOf(
        "india",
        "algeria", "bahrain", "comoros", "djibouti", "egypt", "iraq", "jordan", "kuwait", "lebanon", "libya", "mauritania",
        "morocco", "oman", "palestine", "palestinian territories", "state of palestine", "qatar", "saudi arabia", "somalia",
        "sudan", "syria", "tunisia", "united arab emirates", "yemen",
    )

    /** ISO 639-1 codes: Arabic, and Hindi, Tamil, Telugu, Malayalam, Kannada, Bengali, Marathi, Punjabi, Gujarati, Odia, Assamese. */
    private val LANGUAGES = setOf("ar", "hi", "ta", "te", "ml", "kn", "bn", "mr", "pa", "gu", "or", "as")

    /** Whether the first country of [country] ("India, United States") is a hidden one. */
    fun hidesCountry(country: String): Boolean = country.substringBefore(',').trim().lowercase() in COUNTRIES

    fun hidesLanguage(code: String): Boolean = code.trim().lowercase() in LANGUAGES

    fun hides(meta: CinemetaMeta): Boolean = hidesCountry(meta.country) || hidesLanguage(meta.originalLanguage)

    /** [metas] without the hidden films. */
    fun visible(metas: List<CinemetaMeta>): List<CinemetaMeta> = metas.filterNot(::hides)

    /** [row] without them; null when nothing is left of it. */
    fun visible(row: OnlineRow): OnlineRow? = row.copy(metas = visible(row.metas)).takeIf { it.metas.isNotEmpty() }
}
