// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary.core.realdebrid

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject

/**
 * Other titles of films (TMDB's "alternative titles") by IMDb id, kept on the TV so a file of the viewer's that is
 * named by another title of a film is still recognised as that film (docs/UPGRADE_0.7_PLAN.md). A film asked about
 * that has no other titles is kept with none, so it is not asked about again. Never changes: [with] gives a new one.
 */
class AltTitles private constructor(private val byImdb: Map<String, List<String>>) {
    val size: Int get() = byImdb.size

    /** Whether the film was asked about already (it may have no other titles). */
    fun knows(imdbId: String): Boolean = imdbId in byImdb

    fun of(imdbId: String): List<String> = byImdb[imdbId].orEmpty()

    /** With [titles] for the film, the newest last; beyond [MAX_FILMS] the oldest leave. An invalid id changes nothing. */
    fun with(imdbId: String, titles: List<String>): AltTitles {
        if (!imdbPattern.matches(imdbId)) return this
        val next = LinkedHashMap(byImdb)
        next.remove(imdbId)
        next[imdbId] = clean(titles)
        while (next.size > MAX_FILMS) next.remove(next.keys.first())
        return AltTitles(next)
    }

    fun encode(): String = buildJsonObject {
        put("version", JsonPrimitive(VERSION))
        put("films", buildJsonObject { for ((id, titles) in byImdb) put(id, buildJsonArray { titles.forEach { add(JsonPrimitive(it)) } }) })
    }.toString()

    override fun equals(other: Any?): Boolean = other is AltTitles && other.byImdb == byImdb
    override fun hashCode(): Int = byImdb.hashCode()

    companion object {
        val NONE = AltTitles(emptyMap())
        const val MAX_FILMS = 800
        const val MAX_TITLES = 40
        const val MAX_TITLE = 120
        private const val VERSION = 1
        private val imdbPattern = Regex("^tt\\d{6,10}$")
        private val json = Json { ignoreUnknownKeys = true }

        private fun clean(titles: List<String>): List<String> =
            titles.asSequence().map { it.filterNot(Char::isISOControl).trim() }
                .filter { it.length in 2..MAX_TITLE }.distinctBy { it.lowercase() }.take(MAX_TITLES).toList()

        /** What [encode] wrote. A file that is damaged, of another version or not this at all gives [NONE]. */
        fun decode(text: String): AltTitles {
            if (text.length > MAX_CHARS) return NONE
            val root = try {
                json.parseToJsonElement(text).jsonObject
            } catch (e: IllegalArgumentException) {
                return NONE
            }
            if ((root["version"] as? JsonPrimitive)?.takeIf { !it.isString }?.intOrNull != VERSION) return NONE
            val films = root["films"] as? JsonObject ?: return NONE
            val kept = LinkedHashMap<String, List<String>>()
            for ((id, value) in films) {
                if (kept.size >= MAX_FILMS) break
                if (!imdbPattern.matches(id)) continue
                val titles = (value as? JsonArray ?: continue).asSequence().take(MAX_TITLES * 4)
                    .mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.contentOrNull }.toList()
                kept[id] = clean(titles)
            }
            return AltTitles(kept)
        }

        /** Above any file [encode] can write (800 films of 40 titles of 120 characters is far more than is ever kept). */
        private const val MAX_CHARS = 6_000_000
    }
}
