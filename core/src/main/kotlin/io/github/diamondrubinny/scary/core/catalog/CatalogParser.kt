// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary.core.catalog

import io.github.diamondrubinny.scary.core.model.Language
import io.github.diamondrubinny.scary.core.model.Catalog
import kotlinx.serialization.json.Json

class CatalogException(val problems: List<String>) :
    Exception("Invalid catalog: " + problems.joinToString("; "))

object CatalogParser {
    const val SUPPORTED_VERSION = 1

    /** Languages beyond Hebrew and English that every curated entry must be translated into. */
    /** Synopses every curated movie needs besides Hebrew and English: one per further UI language. */
    private val TRANSLATED = Language.entries.map { it.code } - Language.HE.code - Language.EN.code

    private val json = Json { ignoreUnknownKeys = true }
    private val imdbPattern = Regex("^tt\\d{7,8}$")
    private val idPattern = Regex("^[a-z0-9_]{1,64}$")

    /** Parses and validates a catalog. Throws [CatalogException] when the data is malformed or inconsistent. */
    fun parse(text: String): Catalog {
        val catalog = try {
            json.decodeFromString(Catalog.serializer(), text)
        } catch (e: IllegalArgumentException) {
            // SerializationException is a subclass of IllegalArgumentException.
            throw CatalogException(listOf("malformed catalog json: ${e.message}"))
        }
        val problems = validate(catalog)
        if (problems.isNotEmpty()) throw CatalogException(problems)
        return catalog
    }

    fun validate(catalog: Catalog): List<String> = buildList {
        if (catalog.version != SUPPORTED_VERSION) add("unsupported catalog version ${catalog.version}")
        if (catalog.movies.isEmpty()) add("catalog has no movies")

        val seenIds = HashSet<String>()
        val seenImdb = HashSet<String>()
        for (m in catalog.movies) {
            val tag = "movie '${m.id}'"
            if (!idPattern.matches(m.id)) add("$tag: invalid id")
            if (!seenIds.add(m.id)) add("$tag: duplicate id")
            if (!imdbPattern.matches(m.imdbId)) add("$tag: invalid imdbId '${m.imdbId}'")
            if (!seenImdb.add(m.imdbId)) add("$tag: duplicate imdbId '${m.imdbId}'")
            if (m.title.isBlank()) add("$tag: blank title")
            if (!m.curated) add("$tag: bundled movies must be curated")
            if (m.director.isBlank()) add("$tag: blank director")
            if (m.year !in 1895..2100) add("$tag: year out of range")
            if (m.runtimeMin !in 1..600) add("$tag: runtime out of range")
            if (m.fear == null || m.fear !in 1..5) add("$tag: fear must be 1..5")
            if (m.hue !in 0..359) add("$tag: hue must be 0..359")
            if (m.saturation !in 0..100) add("$tag: saturation must be 0..100")
            if (m.subgenres.isEmpty()) add("$tag: needs at least one subgenre")
            if (m.synopsisHe.isBlank() || m.synopsisEn.isBlank()) add("$tag: missing synopsis")
            TRANSLATED.filter { m.synopses[it].isNullOrBlank() }.forEach { add("$tag: missing $it synopsis") }
        }

        val seenCollections = HashSet<String>()
        for (c in catalog.collections) {
            val tag = "collection '${c.id}'"
            if (!idPattern.matches(c.id)) add("$tag: invalid id")
            if (!seenCollections.add(c.id)) add("$tag: duplicate id")
            if (c.titleHe.isBlank() || c.titleEn.isBlank()) add("$tag: missing title")
            TRANSLATED.filter { c.titles[it].isNullOrBlank() }.forEach { add("$tag: missing $it title") }
            if (c.movieIds.isEmpty()) add("$tag: is empty")
            if (c.movieIds.size != c.movieIds.toSet().size) add("$tag: lists a movie twice")
            c.movieIds.filterNot(seenIds::contains).forEach { add("$tag: unknown movie '$it'") }
        }
    }
}
