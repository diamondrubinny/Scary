// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary.core.sources

import io.github.diamondrubinny.scary.core.realdebrid.Magnets
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull
import java.net.URLEncoder

/**
 * A stream add-on the viewer gave the address of, spoken to in Stremio's add-on protocol as its SDK documents it
 * (github.com/Stremio/stremio-addon-sdk, docs read 21 Nov 2026): GET <address>/stream/movie/<imdb id>.json, answered
 * with {"streams": [...]}. SCARY ships no address of any add-on and recommends none; it never reads the add-ons of the
 * viewer's Stremio account. Only streams that name a torrent's info-hash are kept, each through [Magnets]; a stream
 * that is a link, a YouTube id or anything else is left out, and nothing in an answer is ever opened.
 *
 * The address can hold the viewer's own settings for that add-on in its path, so it is kept and treated as a secret.
 */
object StreamAddon {
    const val MAX_ANSWER_BYTES = 2 * 1024 * 1024
    private const val MAX_URL = 1500
    private const val MAX_STREAMS = 300
    private const val MAX_TEXT = 1000
    private const val MAX_TITLE = 300
    private const val MANIFEST = "/manifest.json"
    private val imdbPattern = Regex("^tt\\d{6,8}$")

    /** https, a host name, an optional port, then a path of printable characters: no user name, query or fragment. */
    private val addressPattern = Regex("^https://[A-Za-z0-9](?:[A-Za-z0-9.\\-]*[A-Za-z0-9])?(?::\\d{1,5})?/[\\x21-\\x7E&&[^?#@\\\\\"<>]]*$")
    private val sizePattern = Regex("(?i)(\\d{1,4}(?:[.,]\\d{1,2})?)\\s*(GB|MB)(?![a-z])")
    private val seedersPattern = Regex("(?:👤|Seeders?:?)\\s*(\\d{1,7})", RegexOption.IGNORE_CASE)
    private val json = Json { ignoreUnknownKeys = true; isLenient = false }

    /** An add-on's address is told from an indexer's by how it ends: the protocol puts its manifest at /manifest.json. */
    fun isManifest(text: String): Boolean = text.trim().endsWith(MANIFEST, ignoreCase = true)

    /** The add-on's base address from its manifest's address, checked: HTTPS, a host, no user name, no query. */
    fun base(text: String): String? {
        val trimmed = text.trim()
        if (!isManifest(trimmed) || trimmed.length !in 30..MAX_URL || !trimmed.startsWith("https://")) return null
        // Read by its own pattern: an add-on's settings in the path use characters (|, =) that a strict URI refuses.
        if (!addressPattern.matches(trimmed)) return null
        return trimmed.dropLast(MANIFEST.length).trimEnd('/')
    }

    /** The add-on's own manifest, for checking that it answers. Never log it: it can hold the viewer's settings. */
    fun manifestUrl(base: String): String = base + MANIFEST

    /** Whether [text] is an add-on's manifest: an object with its id and what it serves, as the protocol asks. */
    fun isManifestAnswer(text: String): Boolean {
        if (text.length > MAX_ANSWER_BYTES) return false
        val root = runCatching { Json.parseToJsonElement(text) }.getOrNull() as? JsonObject ?: return false
        return root.text("id") != null && root["resources"] is kotlinx.serialization.json.JsonArray
    }

    /** The request for one film's streams; null when [imdbId] is not an IMDb number. Never log it: see above. */
    fun movieUrl(base: String, imdbId: String): String? =
        if (imdbPattern.matches(imdbId)) "$base/stream/movie/$imdbId.json" else null

    /** The streams that name a torrent, as releases; null when the answer is not the protocol's. */
    fun parse(text: String): List<Releases.Candidate>? {
        if (text.length > MAX_ANSWER_BYTES) return null
        val root = try {
            json.parseToJsonElement(text) as? JsonObject
        } catch (e: IllegalArgumentException) {
            null
        } ?: return null
        val streams = root["streams"] as? JsonArray ?: return null
        return streams.take(MAX_STREAMS).mapNotNull { (it as? JsonObject)?.let(::candidate) }
    }

    private fun candidate(stream: JsonObject): Releases.Candidate? {
        val hash = stream.text("infoHash") ?: return null
        val name = stream.text("name").orEmpty()
        // "description" took the place of "title"; add-ons send either.
        val description = stream.text("description") ?: stream.text("title").orEmpty()
        val hints = stream["behaviorHints"] as? JsonObject
        val title = (hints?.text("filename") ?: description.lineSequence().firstOrNull { it.isNotBlank() } ?: name)
            .filterNot(Char::isISOControl).trim().take(MAX_TITLE)
        if (title.isBlank()) return null
        val trackers = (stream["sources"] as? JsonArray).orEmpty()
            .mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.takeIf { s -> s.startsWith("tracker:") }?.removePrefix("tracker:") }
        // Built as a link and read back, so the hash, the name and each tracker pass the same checks as a pasted one.
        val link = buildString {
            append("magnet:?xt=urn:btih:").append(hash)
            append("&dn=").append(URLEncoder.encode(title.take(255), "UTF-8"))
            trackers.take(20).forEach { append("&tr=").append(URLEncoder.encode(it, "UTF-8")) }
        }
        val magnet = Magnets.parse(link) ?: return null
        val all = "$name\n$description"
        val size = hints?.number("videoSize")?.takeIf { it > 0 } ?: sizePattern.find(all)?.let { m ->
            val amount = m.groupValues[1].replace(',', '.').toDoubleOrNull() ?: return@let null
            (amount * if (m.groupValues[2].equals("GB", ignoreCase = true)) 1L shl 30 else 1L shl 20).toLong()
        } ?: 0L
        val seeders = seedersPattern.find(all)?.groupValues?.get(1)?.toIntOrNull() ?: -1
        val source = name.lineSequence().firstOrNull().orEmpty().filterNot(Char::isISOControl).trim().take(40)
        return Releases.Candidate(title, magnet, source, size, seeders, Releases.tags("$title $all"))
    }

    private fun JsonObject.text(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull?.takeIf { it.isNotBlank() }?.take(MAX_TEXT)

    private fun JsonObject.number(key: String): Long? =
        (this[key] as? JsonPrimitive)?.takeIf { !it.isString }?.let { it.longOrNull ?: it.doubleOrNull?.toLong() }
}
