// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary.core

import io.github.diamondrubinny.scary.core.remote.RemoteProtocol
import io.github.diamondrubinny.scary.core.remote.RemoteProtocol.Command
import io.github.diamondrubinny.scary.core.remote.RemoteProtocol.Verdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

/**
 * The phone remote's protocol. The expected values were computed apart from this code, with Python's hmac and
 * hashlib; remote.js is checked against the same values (tools/remote-js-check.mjs).
 */
class RemoteProtocolTest {
    private val secret = ByteArray(32) { it.toByte() }

    @Test
    fun `signature and encryption match values computed independently`() {
        val sealed = RemoteProtocol.seal(secret, 1700000000000123, Command.SEARCH, "תורשה hello")
        assertTrue(sealed.contains("\"x\":\"lCSMPrVe0q1eDOqSJpeOMw==\""))
        assertTrue(sealed.contains("\"t\":\"67010163bd2667a595d692192b62c5920652e318ba8a6959f2fc6a394a75000e\""))
        assertEquals(
            "8a90b01f0bfc091424cb0905fdad00b29290b0b4218a66fabcac74f7a9772ffb",
            RemoteProtocol.hex(RemoteProtocol.tag(secret, 5, Command.UP, ByteArray(0))),
        )
    }

    @Test
    fun `a signed command opens, with its text`() {
        val session = RemoteProtocol.Session(secret)
        val verdict = session.open(RemoteProtocol.seal(secret, 10, Command.TMDB_KEY, "0123456789abcdef0123456789abcdef"))
        assertEquals(Verdict.Accepted(RemoteProtocol.Request(10, Command.TMDB_KEY, "0123456789abcdef0123456789abcdef")), verdict)
    }

    @Test
    fun `a command played again, signed with another secret, or changed on the way is refused`() {
        val session = RemoteProtocol.Session(secret)
        val first = RemoteProtocol.seal(secret, 10, Command.OK)
        assertTrue(session.open(first) is Verdict.Accepted)
        assertEquals(Verdict.Replayed, session.open(first))
        assertEquals(Verdict.Replayed, session.open(RemoteProtocol.seal(secret, 9, Command.OK)))
        val other = ByteArray(32) { (it + 1).toByte() }
        assertEquals(Verdict.BadSignature, session.open(RemoteProtocol.seal(other, 11, Command.OK)))
        // The command swapped after signing: the signature no longer fits.
        assertEquals(Verdict.BadSignature, session.open(RemoteProtocol.seal(secret, 12, Command.UP).replace("\"up\"", "\"home\"")))
        // The counter changed: the same.
        assertEquals(Verdict.BadSignature, session.open(RemoteProtocol.seal(secret, 13, Command.UP).replace("\"n\":13", "\"n\":99")))
        assertTrue(session.open(RemoteProtocol.seal(secret, 14, Command.DOWN)) is Verdict.Accepted)
    }

    @Test
    fun `anything malformed is refused before it is trusted`() {
        val session = RemoteProtocol.Session(secret)
        assertEquals(Verdict.Malformed, session.open("not json"))
        assertEquals(Verdict.Malformed, session.open("""{"n":1,"c":"rm -rf","x":"","t":"${"0".repeat(64)}"}"""))
        assertEquals(Verdict.Malformed, session.open("""{"n":-1,"c":"up","x":"","t":"${"0".repeat(64)}"}"""))
        assertEquals(Verdict.Malformed, session.open("""{"n":1,"c":"up","x":"","t":"short"}"""))
        assertEquals(Verdict.Malformed, session.open("x".repeat(RemoteProtocol.MAX_BODY + 1)))
        // Text where the command takes none, and control characters, even when signed.
        assertEquals(Verdict.Malformed, session.open(RemoteProtocol.seal(secret, 2, Command.UP, "text")))
        assertEquals(Verdict.Malformed, session.open(RemoteProtocol.seal(secret, 3, Command.SEARCH, "a\nb")))
    }

    @Test
    fun `an AI key from the phone arrives as service, line break, key`() {
        val session = RemoteProtocol.Session(secret)
        val key = "sk-proj-" + "Ab3_x-".repeat(26)
        val verdict = session.open(RemoteProtocol.seal(secret, 20, Command.AI_KEY, "openai\n$key"))
        assertEquals(Verdict.Accepted(RemoteProtocol.Request(20, Command.AI_KEY, "openai\n$key")), verdict)
        // Only that one line break: none, two, an empty part or another control character is refused.
        assertEquals(Verdict.Malformed, session.open(RemoteProtocol.seal(secret, 21, Command.AI_KEY, "openai$key")))
        assertEquals(Verdict.Malformed, session.open(RemoteProtocol.seal(secret, 22, Command.AI_KEY, "openai\n$key\nx")))
        assertEquals(Verdict.Malformed, session.open(RemoteProtocol.seal(secret, 23, Command.AI_KEY, "openai\n")))
        assertEquals(Verdict.Malformed, session.open(RemoteProtocol.seal(secret, 24, Command.AI_KEY, "openai\n\t$key")))
        assertTrue(session.open(RemoteProtocol.seal(secret, 25, Command.AI_KEY, "anthropic\nsk-ant-api03-abc")) is Verdict.Accepted)
    }

    @Test
    fun `a TMDB read access token from the phone arrives whole`() {
        val session = RemoteProtocol.Session(secret)
        val token = "eyJhbGciOiJIUzI1NiJ9." + "eyJhdWQiOiJ".repeat(15) + "." + "Zm9v_-".repeat(7)
        val verdict = session.open(RemoteProtocol.seal(secret, 30, Command.TMDB_KEY, token))
        assertEquals(Verdict.Accepted(RemoteProtocol.Request(30, Command.TMDB_KEY, token)), verdict)
    }

    @Test
    fun `the secret goes in the QR code as URL-safe base64`() {
        val encoded = RemoteProtocol.encodeSecret(ByteArray(32) { 0xFB.toByte() })
        assertTrue(encoded.none { it == '+' || it == '/' || it == '=' })
        assertEquals(32, Base64.getUrlDecoder().decode(encoded).size)
    }

    private val backupText = """{"format":"scary-backup","version":1,"myList":["the-shining-1980","cm_tt0078748"]}"""
    private val bigBackup = """{"format":"scary-backup","version":1,"watched":[""" + (0 until 600).joinToString(",") { "\"f$it\"" } + "]}"

    @Test
    fun `an answer with a backup is encrypted and signed, with values computed independently`() {
        val session = RemoteProtocol.Session(secret)
        val answer = session.sealAnswer(1700000000000456, backupText)
        assertEquals("RS0zrphbi1qii+4tHJdYUD9cszb2vV1nwOIUU0HnlmkHVy33nwf/0gzoUbnaOPQlo69VfHvAmwBmQJrsfYxavU6AW6XThV0n4UH446k3SXMOTA==", answer.cipher)
        assertEquals("1238871ebae82d0b7634d66339faef4ea69b506c0cdc1939f351e2a8d6f8ea9f", answer.tag)
        assertEquals(backupText, RemoteProtocol.openAnswer(secret, 1700000000000456, answer))
        // Another counter, another secret, or a changed text: not opened.
        assertEquals(null, RemoteProtocol.openAnswer(secret, 1700000000000457, answer))
        assertEquals(null, RemoteProtocol.openAnswer(ByteArray(32) { 9 }, 1700000000000456, answer))
        val flipped = Base64.getDecoder().decode(answer.cipher).also { it[3] = (it[3].toInt() xor 1).toByte() }
        assertEquals(null, RemoteProtocol.openAnswer(secret, 1700000000000456, answer.copy(cipher = Base64.getEncoder().encodeToString(flipped))))
        // The answer's keystream is its own: the same counter's command text is encrypted differently.
        assertTrue(!RemoteProtocol.crypt(secret, 5, ByteArray(32)).contentEquals(RemoteProtocol.cryptAnswer(secret, 5, ByteArray(32))))
    }

    @Test
    fun `a backup file is taken only where a large body is expected`() {
        val sealed = RemoteProtocol.seal(secret, 7, Command.RESTORE, bigBackup)
        assertEquals(4139, bigBackup.length)
        assertTrue(sealed.contains("\"t\":\"d12138415c10394ed2fef2d3c5bec647bb3107db36a5f5b0ab625df3b70c6c4d\""))
        assertTrue(sealed.length > RemoteProtocol.MAX_BODY)
        // At the usual address it is refused, and its counter is not spent.
        val session = RemoteProtocol.Session(secret)
        assertEquals(Verdict.Malformed, session.open(sealed))
        assertEquals(Verdict.Malformed, session.open(RemoteProtocol.seal(secret, 8, Command.RESTORE, "{}")))
        assertEquals(Verdict.Accepted(RemoteProtocol.Request(7, Command.RESTORE, bigBackup)), session.open(sealed, large = true))
        assertEquals(Verdict.Replayed, session.open(sealed, large = true))
        // The large address takes no small command: no way around the small limits of the other commands.
        assertEquals(Verdict.Malformed, session.open(RemoteProtocol.seal(secret, 9, Command.TMDB_KEY, "0123456789abcdef0123456789abcdef"), large = true))
        assertEquals(Verdict.Malformed, session.open(RemoteProtocol.seal(secret, 10, Command.UP), large = true))
        // Too long even for a backup, a line break in it, or a forged one.
        assertEquals(Verdict.Malformed, session.open(RemoteProtocol.seal(secret, 11, Command.RESTORE, "x".repeat(RemoteProtocol.MAX_LARGE_TEXT + 1)), large = true))
        assertEquals(Verdict.Malformed, session.open(RemoteProtocol.seal(secret, 12, Command.RESTORE, "{\n}"), large = true))
        assertEquals(Verdict.BadSignature, session.open(RemoteProtocol.seal(ByteArray(32) { 1 }, 13, Command.RESTORE, bigBackup), large = true))
        // The longest backup fits in the largest body.
        val longest = RemoteProtocol.seal(secret, 14, Command.RESTORE, "x".repeat(RemoteProtocol.MAX_LARGE_TEXT))
        assertTrue(longest.length <= RemoteProtocol.MAX_LARGE_BODY)
        assertTrue(session.open(longest, large = true) is Verdict.Accepted)
        // Asking for a backup is a small command with no text.
        assertTrue(session.open(RemoteProtocol.seal(secret, 15, Command.BACKUP)) is Verdict.Accepted)
        assertEquals(Verdict.Malformed, session.open(RemoteProtocol.seal(secret, 16, Command.BACKUP, "x")))
    }

    @Test
    fun `a VPN settings file travels in base64, only where a large body is expected`() {
        val file = "[Interface]\nPrivateKey = " + "A".repeat(43) + "=\nAddress = 10.2.0.2/32\n\n[Peer]\nPublicKey = " + "B".repeat(43) + "=\nEndpoint = vpn.example.org:51820\n"
        val base64 = java.util.Base64.getEncoder().encodeToString(file.toByteArray())
        val session = RemoteProtocol.Session(secret)
        // As it is, with its line breaks, it is refused; in base64 it is one line of plain letters.
        assertEquals(Verdict.Malformed, session.open(RemoteProtocol.seal(secret, 1, Command.VPN_CONFIG, file), large = true))
        assertEquals(Verdict.Malformed, session.open(RemoteProtocol.seal(secret, 2, Command.VPN_CONFIG, base64)))
        assertEquals(Verdict.Accepted(RemoteProtocol.Request(3, Command.VPN_CONFIG, base64)), session.open(RemoteProtocol.seal(secret, 3, Command.VPN_CONFIG, base64), large = true))
        // The largest file SCARY reads fits, in base64, in the largest command.
        assertTrue(io.github.diamondrubinny.scary.core.vpn.WireGuardConfig.MAX_CHARS * 4 / 3 + 4 <= RemoteProtocol.MAX_LARGE_TEXT)
    }
}
