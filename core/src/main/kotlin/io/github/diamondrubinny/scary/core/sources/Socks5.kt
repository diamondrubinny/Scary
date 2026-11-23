// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary.core.sources

import java.io.IOException
import java.io.InputStream

/**
 * The few bytes of SOCKS5 (RFC 1928) that a search through Tor needs (docs/TOR_PLAN.md): "no password", then "connect
 * me to this host". The host always travels by name, never as an address SCARY looked up: the lookup then happens
 * inside Tor, and the viewer's internet provider does not see which source is asked.
 */
object Socks5 {
    /** Version 5, one method offered: no authentication. */
    val GREETING: ByteArray = byteArrayOf(5, 1, 0)

    private val hostPattern = Regex("^(?=.{1,253}$)[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?(?:\\.[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?)*$")

    /** Where an `https://` address goes: its host by name, its port and the rest. Null for anything else. */
    data class Target(val host: String, val port: Int, val pathAndQuery: String)

    private val urlPattern = Regex("^https://([^/:?#@\\s]+)(?::(\\d{1,5}))?(/[^#\\s]*)?$")

    fun target(url: String): Target? {
        val match = urlPattern.matchEntire(url) ?: return null
        val host = match.groupValues[1].takeIf(hostPattern::matches) ?: return null
        val port = match.groupValues[2].ifEmpty { "443" }.toInt().takeIf { it in 1..65535 } ?: return null
        val path = match.groupValues[3].ifEmpty { "/" }
        if (path.any(Char::isISOControl)) return null
        return Target(host, port, path)
    }

    /** True when the proxy took the greeting: version 5, "no authentication". */
    fun greeted(input: InputStream): Boolean = input.read() == 5 && input.read() == 0

    /** "Connect to [host]:[port]", the host by name (address type 3). */
    fun connect(host: String, port: Int): ByteArray {
        require(hostPattern.matches(host)) { "invalid host" }
        require(port in 1..65535) { "invalid port" }
        val name = host.toByteArray(Charsets.US_ASCII)
        return byteArrayOf(5, 1, 0, 3, name.size.toByte()) + name + byteArrayOf((port shr 8).toByte(), port.toByte())
    }

    /** Reads the proxy's answer to [connect], to its end; true when the connection was made. */
    fun connected(input: InputStream): Boolean {
        fun byte(): Int = input.read().also { if (it < 0) throw IOException("the proxy's answer ended early") }
        if (byte() != 5) return false
        val reply = byte()
        byte()
        // The address the proxy bound: read past it, whatever its kind.
        val skip = when (byte()) {
            1 -> 4
            4 -> 16
            3 -> byte()
            else -> return false
        } + 2
        repeat(skip) { byte() }
        return reply == 0
    }
}
