// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary.core.realdebrid

import java.net.URLDecoder
import java.net.URLEncoder

/**
 * Magnet links the viewer hands to their own Real-Debrid account (docs/TORRENT_SCRAPER_PLAN.md). A link is read as
 * untrusted text: only a BitTorrent v1 info-hash, a display name and plain tracker addresses are kept, and the link sent
 * on is built again from those, so nothing else in the pasted text travels.
 */
object Magnets {
    /** [infoHash] is 40 lower-case hex digits. */
    data class Magnet(val infoHash: String, val name: String? = null, val trackers: List<String> = emptyList()) {
        /** The link as SCARY sends it: rebuilt, never the pasted text. */
        val uri: String
            get() = buildString {
                append("magnet:?xt=urn:btih:").append(infoHash)
                name?.let { append("&dn=").append(URLEncoder.encode(it, "UTF-8")) }
                trackers.forEach { append("&tr=").append(URLEncoder.encode(it, "UTF-8")) }
            }
    }

    const val MAX_LENGTH = 4096
    private const val MAX_NAME = 255
    private const val MAX_TRACKERS = 20
    private const val MAX_TRACKER = 256
    private val hex = Regex("^[0-9a-fA-F]{40}$")
    private val base32 = Regex("^[A-Za-z2-7]{32}$")
    private val tracker = Regex("^(udp|http|https|wss)://[A-Za-z0-9.\\-\\[\\]:]+(/[A-Za-z0-9._~/\\-]*)?$")

    /** A magnet link, or a bare info-hash; null for anything else (a v2-only link, an over-long or broken one). */
    fun parse(text: String): Magnet? {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || trimmed.length > MAX_LENGTH || trimmed.any { it.isWhitespace() || it.isISOControl() }) return null
        hash(trimmed)?.let { return Magnet(it) }
        if (!trimmed.startsWith("magnet:?", ignoreCase = true)) return null
        var infoHash: String? = null
        var name: String? = null
        val trackers = LinkedHashSet<String>()
        for (part in trimmed.substring("magnet:?".length).split('&')) {
            val key = part.substringBefore('=').lowercase().substringBefore('.')
            val value = decode(part.substringAfter('=', "")) ?: continue
            when (key) {
                "xt" -> if (infoHash == null && value.startsWith("urn:btih:", ignoreCase = true)) infoHash = hash(value.substring("urn:btih:".length))
                "dn" -> if (name == null) name = value.filterNot(Char::isISOControl).trim().take(MAX_NAME).ifBlank { null }
                "tr" -> if (trackers.size < MAX_TRACKERS && value.length <= MAX_TRACKER && tracker.matches(value)) trackers += value
            }
        }
        return infoHash?.let { Magnet(it, name, trackers.toList()) }
    }

    /** 40 hex digits as they are, or 32 base32 letters turned into them. */
    private fun hash(text: String): String? = when {
        hex.matches(text) -> text.lowercase()
        base32.matches(text) -> base32ToHex(text.uppercase())
        else -> null
    }

    private fun base32ToHex(text: String): String {
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
        val bits = StringBuilder(160)
        text.forEach { bits.append(alphabet.indexOf(it).toString(2).padStart(5, '0')) }
        return bits.chunked(4).joinToString("") { it.toInt(2).toString(16) }
    }

    private fun decode(value: String): String? = try {
        URLDecoder.decode(value, "UTF-8")
    } catch (e: IllegalArgumentException) {
        null
    }
}
