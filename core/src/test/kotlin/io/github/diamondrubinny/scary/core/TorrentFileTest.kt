// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary.core

import io.github.diamondrubinny.scary.core.sources.TorrentFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.security.MessageDigest

/** A .torrent file gives its info-hash, and anything else gives nothing. */
class TorrentFileTest {
    private fun bytes(text: String) = text.toByteArray(Charsets.ISO_8859_1)

    private fun sha1(text: String) = MessageDigest.getInstance("SHA-1").digest(bytes(text)).joinToString("") { "%02x".format(it) }

    private val info = "d6:lengthi12345e4:name8:film.mkv12:piece lengthi16384e6:pieces20:01234567890123456789e"

    @Test
    fun `the info-hash is the SHA-1 of the info dictionary's own bytes`() {
        val torrent = "d8:announce30:udp://tracker.example.org:13374:info${info}e"
        assertEquals(sha1(info), TorrentFile.infoHash(bytes(torrent)))
        // Whatever stands around it (lists, nested dictionaries, negative numbers) does not change it.
        val more = "d8:announce5:udp:/13:announce-listll3:abcel3:defee7:comment3:hey13:creation datei-5e4:info${info}5:nodesld1:a1:beee"
        assertEquals(sha1(info), TorrentFile.infoHash(bytes(more)))
    }

    @Test
    fun `binary piece hashes are read as bytes, not as text`() {
        val pieces = ByteArray(20) { (it * 13 - 128).toByte() }
        val head = bytes("d4:name1:x6:pieces20:")
        val infoBytes = head + pieces + bytes("e")
        val file = bytes("d4:info") + infoBytes + bytes("e")
        assertEquals(MessageDigest.getInstance("SHA-1").digest(infoBytes).joinToString("") { "%02x".format(it) }, TorrentFile.infoHash(file))
    }

    @Test
    fun `what is not a torrent gives nothing`() {
        for (bad in listOf(
            "",
            "<html>Not found</html>",
            "magnet:?xt=urn:btih:0123456789abcdef0123456789abcdef01234567",
            "d8:announce3:udpe",
            "d4:info3:abce",
            "d4:infod4:name3:abc",
            "d4:infod4:name99:abcee",
            "d4:infod1:ai12",
            "d4:infod1:aiee e",
            "l4:infod1:a1:bee",
            "d4:infod1:a1:be",
            "d4:infod1:a99999999999999999999:xee",
        )) {
            assertNull(bad, TorrentFile.infoHash(bytes(bad)))
        }
        // Nesting without end is refused, not followed.
        assertNull(TorrentFile.infoHash(bytes("d4:info" + "l".repeat(5000) + "e".repeat(5000) + "e")))
        assertNull(TorrentFile.infoHash(ByteArray(TorrentFile.MAX_BYTES + 1) { 'd'.code.toByte() }))
    }
}
