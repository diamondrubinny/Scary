// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary.core.catalog

import io.github.diamondrubinny.scary.core.cinemeta.Cinemeta
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * What outlives the hand-picked library (legacy.json, written by tools/catalog/build_legacy.py before the library was
 * removed; docs/CATALOG_REBUILD_PLAN.md):
 *
 *  - its ids. Viewers' lists and backups hold them ("psycho"), and a film is "cm_" + IMDb id from now on, so
 *    [newId] turns the one into the other and leaves every other id as it is;
 *  - its fear levels, which no public source gives. The parental lock and family night depend on them: a film
 *    without one is "unknown", never "mild".
 *
 * No titles and no synopses. The file ships in the APK and is still read as untrusted: an entry that breaks a rule
 * is dropped.
 */
class LegacyLibrary private constructor(private val imdbByOldId: Map<String, String>, private val fearByImdb: Map<String, Int>) {
    val size: Int get() = imdbByOldId.size

    /** The id a film has now: an old library id becomes "cm_" + its IMDb id; any other id is returned unchanged. */
    fun newId(id: String): String = imdbByOldId[id]?.let { Cinemeta.ID_PREFIX + it } ?: id

    /** The fear level (1 to 5) the library gave the film with this IMDb id, or null. */
    fun fear(imdbId: String): Int? = fearByImdb[imdbId]

    @Serializable
    private data class File(val version: Int, val films: Map<String, Entry> = emptyMap())

    @Serializable
    private data class Entry(val imdb: String = "", val fear: Int? = null)

    companion object {
        const val SUPPORTED_VERSION = 1
        private const val MAX_FILMS = 2_000
        private val idPattern = Regex("^[a-z0-9][a-z0-9_-]{0,79}$")
        private val imdbPattern = Regex("^tt\\d{7,8}$")
        private val json = Json { ignoreUnknownKeys = true }

        /** A library that knows nothing: every id stays as it is, no film has a fear level. */
        val EMPTY = LegacyLibrary(emptyMap(), emptyMap())

        /** Parses [text]. Throws [CatalogException] when the file as a whole cannot be read. */
        fun parse(text: String): LegacyLibrary {
            val file = try {
                json.decodeFromString(File.serializer(), text)
            } catch (e: IllegalArgumentException) {
                throw CatalogException(listOf("malformed legacy json: ${e.message}"))
            }
            if (file.version != SUPPORTED_VERSION || file.films.size > MAX_FILMS) throw CatalogException(listOf("unsupported legacy file"))
            val good = file.films.filter { (id, entry) -> idPattern.matches(id) && imdbPattern.matches(entry.imdb) && !id.startsWith(Cinemeta.ID_PREFIX) }
            return LegacyLibrary(
                good.mapValues { it.value.imdb },
                good.values.mapNotNull { entry -> entry.fear?.takeIf { it in 1..5 }?.let { entry.imdb to it } }.toMap(),
            )
        }
    }
}
