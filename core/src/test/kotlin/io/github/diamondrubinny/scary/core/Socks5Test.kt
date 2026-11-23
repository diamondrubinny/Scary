// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary.core

import io.github.diamondrubinny.scary.core.sources.Socks5
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Socks5Test {
    private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }

    @Test
    fun `the host is sent by name, so the lookup happens inside Tor`() {
        assertArrayEquals(bytes(5, 1, 0), Socks5.GREETING)
        assertArrayEquals(
            bytes(5, 1, 0, 3, 11) + "example.org".toByteArray() + bytes(0x01, 0xBB),
            Socks5.connect("example.org", 443),
        )
        assertArrayEquals(bytes(0x23, 0x82), Socks5.connect("a.b", 9090).takeLast(2).toByteArray())
        // Never an address in numbers with a scope, a space, or anything that is not a host name.
        for (host in listOf("", "exa mple.org", "example.org/x", "[::1]", "-a.org", "a..b", "x".repeat(254))) {
            assertTrue(host, runCatching { Socks5.connect(host, 443) }.isFailure)
        }
        assertTrue(runCatching { Socks5.connect("example.org", 0) }.isFailure)
    }

    @Test
    fun `only https addresses with a plain host go through`() {
        assertEquals(Socks5.Target("indexer.example", 443, "/api?t=caps&apikey=k"), Socks5.target("https://indexer.example/api?t=caps&apikey=k"))
        assertEquals(Socks5.Target("addon.example", 8443, "/a|b=c/stream/movie/tt0063350.json"), Socks5.target("https://addon.example:8443/a|b=c/stream/movie/tt0063350.json"))
        assertEquals(Socks5.Target("indexer.example", 443, "/"), Socks5.target("https://indexer.example"))
        for (url in listOf("http://indexer.example/api", "https://user@indexer.example/", "https://indexer.example:99999/", "https://[::1]/", "https://indexer.example/a b", "ftp://x")) {
            assertNull(url, Socks5.target(url))
        }
    }

    @Test
    fun `the proxy's answers are read to their end`() {
        assertTrue(Socks5.greeted(bytes(5, 0).inputStream()))
        assertFalse(Socks5.greeted(bytes(5, 0xFF).inputStream()))
        assertFalse(Socks5.greeted(bytes(4, 0).inputStream()))
        // Made, with an IPv4, an IPv6 and a named bound address; the bytes after it are the connection's own.
        val v4 = (bytes(5, 0, 0, 1, 0, 0, 0, 0, 0, 0) + bytes(72)).inputStream()
        assertTrue(Socks5.connected(v4))
        assertEquals(72, v4.read())
        assertTrue(Socks5.connected((bytes(5, 0, 0, 4) + ByteArray(16) + bytes(0, 0)).inputStream()))
        assertTrue(Socks5.connected((bytes(5, 0, 0, 3, 2, 97, 98, 0, 0)).inputStream()))
        // Refused by the proxy (host unreachable), not SOCKS5, or cut short.
        assertFalse(Socks5.connected(bytes(5, 4, 0, 1, 0, 0, 0, 0, 0, 0).inputStream()))
        assertFalse(Socks5.connected(bytes(72, 84, 84, 80).inputStream()))
        assertTrue(runCatching { Socks5.connected(bytes(5, 0, 0, 1, 0).inputStream()) }.isFailure)
    }
}
