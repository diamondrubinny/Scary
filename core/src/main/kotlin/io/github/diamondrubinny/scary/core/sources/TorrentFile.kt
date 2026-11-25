// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary.core.sources

import java.security.MessageDigest

/**
 * A .torrent file, read for one thing: its info-hash, which is what a magnet link is made of. Some indexers give a
 * release only as a link to such a file at the viewer's own indexer, with no magnet link and no hash; the file is
 * fetched from there and reduced to its hash here. Nothing in it is opened, run or kept.
 *
 * The file is untrusted bytes: it is walked strictly (bencode's four forms, a depth limit, every length checked
 * against the file's end) and anything that is not a well-formed torrent gives null.
 */
object TorrentFile {
    /** A .torrent file is a list of piece hashes: a film's is tens of kilobytes, a whole collection's under this. */
    const val MAX_BYTES = 4 * 1024 * 1024
    private const val MAX_DEPTH = 40
    private const val MAX_DIGITS = 18

    /** The info-hash (40 lower-case hex digits: SHA-1 of the "info" dictionary's own bytes), or null. */
    fun infoHash(bytes: ByteArray): String? {
        if (bytes.isEmpty() || bytes.size > MAX_BYTES || bytes[0] != 'd'.code.toByte()) return null
        return try {
            var at = 1
            var info: IntRange? = null
            while (true) {
                if (at >= bytes.size) return null
                if (bytes[at] == 'e'.code.toByte()) break
                val keyEnd = string(bytes, at)
                val key = String(bytes, keyStart(bytes, at), keyEnd - keyStart(bytes, at), Charsets.ISO_8859_1)
                val valueEnd = skip(bytes, keyEnd, 1)
                // The first "info" of the top dictionary is the torrent's; it must itself be a dictionary.
                if (key == "info" && info == null) {
                    if (bytes[keyEnd] != 'd'.code.toByte()) return null
                    info = keyEnd until valueEnd
                }
                at = valueEnd
            }
            val span = info ?: return null
            MessageDigest.getInstance("SHA-1").digest(bytes.copyOfRange(span.first, span.last + 1)).joinToString("") { "%02x".format(it) }
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    /** The index after the element that starts at [at]; throws for anything that is not bencode within the file. */
    private fun skip(bytes: ByteArray, at: Int, depth: Int): Int {
        require(depth <= MAX_DEPTH && at < bytes.size) { "too deep or past the end" }
        return when (bytes[at].toInt().toChar()) {
            'i' -> {
                var i = at + 1
                if (i < bytes.size && bytes[i] == '-'.code.toByte()) i++
                val digits = i
                while (i < bytes.size && bytes[i].toInt().toChar().isDigit()) i++
                require(i > digits && i - digits <= MAX_DIGITS && i < bytes.size && bytes[i] == 'e'.code.toByte()) { "bad integer" }
                i + 1
            }
            'l' -> {
                var i = at + 1
                while (true) {
                    require(i < bytes.size) { "list past the end" }
                    if (bytes[i] == 'e'.code.toByte()) return i + 1
                    i = skip(bytes, i, depth + 1)
                }
                @Suppress("UNREACHABLE_CODE")
                i
            }
            'd' -> {
                var i = at + 1
                while (true) {
                    require(i < bytes.size) { "dictionary past the end" }
                    if (bytes[i] == 'e'.code.toByte()) return i + 1
                    i = skip(bytes, string(bytes, i), depth + 1)
                }
                @Suppress("UNREACHABLE_CODE")
                i
            }
            else -> string(bytes, at)
        }
    }

    /** Where the bytes of the string that starts at [at] begin (after "<length>:"). */
    private fun keyStart(bytes: ByteArray, at: Int): Int {
        var i = at
        while (i < bytes.size && bytes[i].toInt().toChar().isDigit()) i++
        return i + 1
    }

    /** The index after the string ("<length>:<bytes>") that starts at [at]. */
    private fun string(bytes: ByteArray, at: Int): Int {
        var i = at
        var length = 0L
        while (i < bytes.size && bytes[i].toInt().toChar().isDigit()) {
            length = length * 10 + (bytes[i] - '0'.code.toByte())
            require(length <= MAX_BYTES) { "string too long" }
            i++
        }
        require(i > at && i - at <= MAX_DIGITS && i < bytes.size && bytes[i] == ':'.code.toByte()) { "bad string" }
        val end = i + 1 + length.toInt()
        require(end <= bytes.size) { "string past the end" }
        return end
    }
}
