// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary.core.realdebrid

import io.github.diamondrubinny.scary.core.player.WatchHistory
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

/**
 * Which film the viewer says a file is, when SCARY's guess from the name was wrong or missing: file key to film id, or
 * "" for "not a film in the library". Kept on the TV only; never sent anywhere. Pure logic; the storage is in the app.
 */
object RdMatches {
    /** The most corrections kept; the oldest go first. */
    const val MAX = 300
    private const val MAX_ID = 80

    /** The film id the viewer chose for [fileKey]: null when they chose nothing, "" when they said it is no film. */
    fun film(corrections: Map<String, String>, fileKey: String): String? = corrections[fileKey]

    /** [corrections] with [fileKey] set to [movieId] (null clears the correction; "" means no film). */
    fun with(corrections: Map<String, String>, fileKey: String, movieId: String?): LinkedHashMap<String, String> {
        val next = LinkedHashMap(corrections)
        next.remove(fileKey)
        if (movieId != null && WatchHistory.isValidKey(fileKey) && movieId.length <= MAX_ID) next[fileKey] = movieId
        while (next.size > MAX) next.remove(next.keys.first())
        return next
    }

    fun encode(corrections: Map<String, String>): String = buildJsonObject { corrections.forEach { (k, v) -> put(k, v) } }.toString()

    /** What [encode] wrote; anything else, or damaged entries, give fewer or none. */
    fun decode(text: String?): LinkedHashMap<String, String> {
        val out = LinkedHashMap<String, String>()
        if (text.isNullOrBlank() || text.length > 200_000) return out
        val root = try {
            Json.parseToJsonElement(text).jsonObject
        } catch (e: Exception) {
            return out
        }
        for ((key, value) in root as JsonObject) {
            val id = (value as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull ?: continue
            if (WatchHistory.isValidKey(key) && id.length <= MAX_ID && out.size < MAX) out[key] = id
        }
        return out
    }
}
