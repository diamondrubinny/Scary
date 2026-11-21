// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary.core

import io.github.diamondrubinny.scary.core.sources.LanHttp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/** The one plain-HTTP request SCARY makes: to the viewer's indexer at a private home address, and nowhere else. */
class LanHttpTest {
    private fun read(text: String, max: Int = 1000) = LanHttp.read(text.replace("\n", "\r\n").byteInputStream(Charsets.UTF_8), max)

    @Test
    fun `only private home addresses written as numbers count`() {
        for (host in listOf("10.0.0.1", "10.255.255.255", "172.16.0.1", "172.31.9.9", "192.168.0.1", "192.168.255.254")) assertTrue(host, LanHttp.isPrivateAddress(host))
        for (host in listOf("8.8.8.8", "172.15.0.1", "172.32.0.1", "192.169.1.1", "127.0.0.1", "169.254.1.1", "0.0.0.0", "192.168.1", "192.168.1.256", "nas.local", "localhost", "192.168.1.5.example", "::1", "")) {
            assertFalse(host, LanHttp.isPrivateAddress(host))
        }
    }

    @Test
    fun `a target is made only from plain http at a private address`() {
        assertEquals(LanHttp.Target("192.168.1.5", 9696, "/1/api?t=search&q=a+b&apikey=k"), LanHttp.target("http://192.168.1.5:9696/1/api?t=search&q=a+b&apikey=k"))
        assertEquals(LanHttp.Target("10.0.0.7", 80, "/"), LanHttp.target("http://10.0.0.7"))
        assertNull(LanHttp.target("https://192.168.1.5/api"))
        assertNull(LanHttp.target("http://example.com/api"))
        assertNull(LanHttp.target("http://user@192.168.1.5/api"))
        assertNull(LanHttp.target("http://192.168.1.5:0/api"))
    }

    @Test
    fun `the request asks for one whole answer`() {
        val text = String(LanHttp.request(LanHttp.Target("192.168.1.5", 9696, "/1/api?t=search")), Charsets.ISO_8859_1)
        assertTrue(text.startsWith("GET /1/api?t=search HTTP/1.1\r\nHost: 192.168.1.5:9696\r\n"))
        assertTrue(text.contains("Accept-Encoding: identity\r\n"))
        assertTrue(text.endsWith("Connection: close\r\n\r\n"))
        assertTrue(String(LanHttp.request(LanHttp.Target("10.0.0.7", 80, "/")), Charsets.ISO_8859_1).contains("Host: 10.0.0.7\r\n"))
    }

    @Test
    fun `a body with a length, in chunks, or until the end is read`() {
        val sized = read("HTTP/1.1 200 OK\nContent-Type: application/rss+xml\nContent-Length: 6\n\n<rss/>ignored")
        assertEquals(200, sized.code)
        assertEquals("<rss/>", sized.body)
        val chunked = read("HTTP/1.1 200 OK\nTransfer-Encoding: chunked\n\n3;ext=1\n<rs\n3\ns/>\n0\n\n")
        assertEquals("<rss/>", chunked.body)
        assertEquals("<rss/>", read("HTTP/1.0 200 OK\n\n<rss/>").body)
        assertEquals("שלום", read("HTTP/1.1 200 OK\nContent-Length: 8\n\nשלום").body)
    }

    @Test
    fun `other answers keep their code, and a body too long or cut short is dropped`() {
        assertEquals(401, read("HTTP/1.1 401 Unauthorized\nContent-Length: 0\n\n").code)
        assertEquals(302, read("HTTP/1.1 302 Found\nLocation: http://evil.example/\nContent-Length: 0\n\n").code)
        assertNull(read("HTTP/1.1 200 OK\nContent-Length: 2000\n\n" + "x".repeat(2000)).body)
        assertNull(read("HTTP/1.1 200 OK\nTransfer-Encoding: chunked\n\n7d0\n" + "x".repeat(2000) + "\n0\n\n").body)
        assertNull(read("HTTP/1.1 200 OK\n\n" + "x".repeat(2000)).body)
        assertNull(read("HTTP/1.1 200 OK\nContent-Length: 50\n\nshort").body)
        assertNull(read("HTTP/1.1 200 OK\nTransfer-Encoding: chunked\n\n10\nshort").body)
    }

    @Test
    fun `a request over a real socket is answered`() {
        // A one-answer server on this machine, chunked as a home indexer answers.
        java.net.ServerSocket(0, 1, java.net.InetAddress.getLoopbackAddress()).use { server ->
            var asked = ""
            val serving = Thread {
                server.accept().use { client ->
                    val reader = client.getInputStream().bufferedReader(Charsets.ISO_8859_1)
                    val lines = generateSequence { reader.readLine() }.takeWhile { it.isNotEmpty() }.toList()
                    asked = lines.first()
                    client.getOutputStream().write("HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\n\r\n6\r\n<rss/>\r\n0\r\n\r\n".toByteArray())
                }
            }.apply { start() }
            val target = LanHttp.Target("127.0.0.1", server.localPort, "/1/api?t=search&q=film")
            val response = java.net.Socket("127.0.0.1", server.localPort).use { socket ->
                socket.getOutputStream().write(LanHttp.request(target))
                LanHttp.read(socket.getInputStream(), 1000)
            }
            serving.join(5000)
            assertEquals("GET /1/api?t=search&q=film HTTP/1.1", asked)
            assertEquals(200, response.code)
            assertEquals("<rss/>", response.body)
        }
    }

    @Test
    fun `what is not an http answer throws`() {
        fun fails(text: String) = assertTrue(text.take(20), runCatching { read(text) }.exceptionOrNull() is IOException)
        fails("")
        fails("SSH-2.0-OpenSSH\n")
        fails("HTTP/1.1 200 OK\nContent-Length: -5\n\n")
        fails("HTTP/1.1 200 OK\nTransfer-Encoding: chunked\n\nzz\nabc\n0\n\n")
        fails("HTTP/1.1 200 OK\nX: " + "a".repeat(20_000) + "\n\n")
        fails("HTTP/1.1 200 OK\n" + "A: b\n".repeat(150) + "\n")
    }
}
