// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary.core.stremio

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/**
 * Films and series the viewer took out of SCARY's "Continue in Stremio" row: IMDb id to the moment (ms since the epoch)
 * they were taken out. Kept on the TV only; nothing is written to the Stremio account, where the item stays as it is.
 * An item played again in Stremio after that moment is back in the row: the viewer is watching it again.
 */
object ContinueDismissals {
    /** The most kept; the oldest go first. */
    const val MAX = 200
    private val IMDB = Regex("tt\\d{5,10}")

    /** [items] without those taken out and not played since. */
    fun visible(items: List<StremioItem>, dismissed: Map<String, Long>): List<StremioItem> =
        if (dismissed.isEmpty()) items else items.filterNot { item ->
            val at = dismissed[item.imdbId] ?: return@filterNot false
            maxOf(item.lastWatched, item.modified) <= at
        }

    /** [dismissed] with [imdbId] taken out at [nowMillis]. */
    fun with(dismissed: Map<String, Long>, imdbId: String, nowMillis: Long): LinkedHashMap<String, Long> {
        val next = LinkedHashMap(dismissed)
        next.remove(imdbId)
        if (IMDB.matches(imdbId) && nowMillis > 0) next[imdbId] = nowMillis
        while (next.size > MAX) next.remove(next.keys.first())
        return next
    }

    fun encode(dismissed: Map<String, Long>): String = buildJsonObject { dismissed.forEach { (k, v) -> put(k, v) } }.toString()

    /** What [encode] wrote; anything else, or damaged entries, give fewer or none. */
    fun decode(text: String?): LinkedHashMap<String, Long> {
        val out = LinkedHashMap<String, Long>()
        if (text.isNullOrBlank() || text.length > 20_000) return out
        val root = try {
            Json.parseToJsonElement(text).jsonObject
        } catch (e: Exception) {
            return out
        }
        for ((key, value) in root) {
            val at = (value as? JsonPrimitive)?.takeIf { !it.isString }?.longOrNull?.takeIf { it > 0 } ?: continue
            if (IMDB.matches(key) && out.size < MAX) out[key] = at
        }
        return out
    }
}
