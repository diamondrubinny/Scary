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
    fun `the secret goes in the QR code as URL-safe base64`() {
        val encoded = RemoteProtocol.encodeSecret(ByteArray(32) { 0xFB.toByte() })
        assertTrue(encoded.none { it == '+' || it == '/' || it == '=' })
        assertEquals(32, Base64.getUrlDecoder().decode(encoded).size)
    }
}
