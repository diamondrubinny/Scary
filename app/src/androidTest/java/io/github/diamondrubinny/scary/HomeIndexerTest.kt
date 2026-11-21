// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.diamondrubinny.scary.data.SecretStore
import io.github.diamondrubinny.scary.data.SourcesRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket

/**
 * The viewer's indexer at a plain-HTTP home address, on a real device: the app's network rule allows no cleartext, so
 * this shows that SCARY's own request to a private address still goes out, is read (chunked, as a home indexer
 * answers), and that the saved indexer is removed again. The "indexer" is a one-answer server on the device's own
 * private address; nothing leaves the device.
 */
@RunWith(AndroidJUnit4::class)
class HomeIndexerTest {
    private fun privateAddress(): Inet4Address? = NetworkInterface.getNetworkInterfaces().asSequence()
        .flatMap { it.inetAddresses.asSequence() }
        .filterIsInstance<Inet4Address>()
        .firstOrNull { it.isSiteLocalAddress }

    @Test
    fun aSearchReachesAnIndexerAtAPrivateHttpAddress() {
        val address = privateAddress()
        assumeTrue("The device has no private IPv4 address", address != null)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val repository = SourcesRepository(SecretStore(context))
        val hash = "a".repeat(40)
        val feed = """<?xml version="1.0" encoding="UTF-8"?><rss version="2.0" xmlns:torznab="http://torznab.com/schemas/2015/feed"><channel>""" +
            """<item><title>Night.of.the.Living.Dead.1968.1080p.BluRay</title><torznab:attr name="infohash" value="$hash"/>""" +
            """<torznab:attr name="seeders" value="12"/></item></channel></rss>"""
        var asked = ""
        ServerSocket(0, 1, address).use { server ->
            val serving = Thread {
                runCatching {
                    server.accept().use { client ->
                        val reader = client.getInputStream().bufferedReader(Charsets.ISO_8859_1)
                        asked = generateSequence { reader.readLine() }.takeWhile { it.isNotEmpty() }.toList().first()
                        val body = feed.toByteArray()
                        client.getOutputStream().apply {
                            write("HTTP/1.1 200 OK\r\nContent-Type: application/rss+xml\r\nTransfer-Encoding: chunked\r\n\r\n".toByteArray())
                            write((body.size.toString(16) + "\r\n").toByteArray())
                            write(body)
                            write("\r\n0\r\n\r\n".toByteArray())
                            flush()
                        }
                    }
                }
            }.apply { start() }
            try {
                val saved = runBlocking { repository.save("http://${address!!.hostAddress}:${server.localPort}/1/api", "0123456789abcdef") }
                assertEquals(SourcesRepository.SaveResult.SAVED, saved)
                val result = runBlocking { repository.search("night of the living dead") }
                serving.join(10_000)
                assertTrue("The indexer was asked: $asked", asked.startsWith("GET /1/api?t=search&") && asked.contains("apikey=0123456789abcdef"))
                assertTrue("The search was answered: $result", result is SourcesRepository.Search.Ready)
                assertEquals(listOf(hash), (result as SourcesRepository.Search.Ready).candidates.map { it.magnet.infoHash })
            } finally {
                runBlocking { repository.remove() }
            }
        }
        // A plain-HTTP address that is not a private one is refused when it is saved, so nothing is ever sent to it.
        assertEquals(SourcesRepository.SaveResult.BAD_ADDRESS, runBlocking { repository.save("http://example.com/1/api", "0123456789abcdef") })
    }
}
