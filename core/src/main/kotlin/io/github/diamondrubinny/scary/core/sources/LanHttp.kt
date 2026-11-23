// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary.core.sources

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.URI

/**
 * One plain-HTTP GET to the viewer's own indexer on their home network (docs/TORRENT_SCRAPER_PLAN.md). SCARY is HTTPS
 * only everywhere else, and stays so: Android's cleartext rule is not relaxed for the app. This is the one request
 * that may go unencrypted, and only to an address that is a private IPv4 literal (10.x, 172.16 to 172.31, 192.168.x),
 * because a home Prowlarr or Jackett has no certificate. The indexer's key travels in the clear inside that network,
 * which the settings window says.
 *
 * Only what one answer needs is here: the request's text, and a reader for a response with a Content-Length, a
 * chunked body, or a body that ends when the connection does. No redirects, no compression, no keep-alive.
 */
object LanHttp {
    private val ipv4 = Regex("^(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})$")
    private const val MAX_HEADER_BYTES = 16 * 1024
    private const val MAX_HEADERS = 100

    /** True for the private IPv4 ranges of a home network, written as numbers; a name is never private enough. */
    fun isPrivateAddress(host: String): Boolean {
        val parts = ipv4.matchEntire(host)?.groupValues?.drop(1)?.map { it.toIntOrNull() ?: return false } ?: return false
        if (parts.any { it !in 0..255 }) return false
        val (a, b) = parts
        return a == 10 || (a == 172 && b in 16..31) || (a == 192 && b == 168)
    }

    data class Target(val host: String, val port: Int, val pathAndQuery: String)

    /** Where a plain-HTTP address goes, or null unless it is `http://` at a private address. */
    fun target(url: String): Target? {
        if (!url.startsWith("http://")) return null
        val uri = runCatching { URI(url) }.getOrNull() ?: return null
        val host = uri.host?.takeIf(::isPrivateAddress) ?: return null
        if (uri.rawUserInfo != null || uri.rawFragment != null) return null
        val port = if (uri.port == -1) 80 else uri.port.takeIf { it in 1..65535 } ?: return null
        val path = uri.rawPath?.takeIf { it.isNotEmpty() } ?: "/"
        val full = path + (uri.rawQuery?.let { "?$it" } ?: "")
        if (full.any { it.isWhitespace() || it.isISOControl() }) return null
        return Target(host, port, full)
    }

    /** The request's bytes: the answer is asked for whole and unpacked, and the connection ends with it. */
    fun request(target: Target): ByteArray = request(target.host, target.port, target.pathAndQuery, defaultPort = 80, accept = "application/rss+xml, application/xml")

    /** The same request for any host: [defaultPort] is the one the "Host" line leaves out (443 inside TLS). */
    fun request(host: String, port: Int, pathAndQuery: String, defaultPort: Int, accept: String): ByteArray = buildString {
        require((host + pathAndQuery + accept).none { it == '\r' || it == '\n' }) { "a line break in the request" }
        append("GET ").append(pathAndQuery).append(" HTTP/1.1\r\n")
        append("Host: ").append(host)
        if (port != defaultPort) append(':').append(port)
        append("\r\n")
        append("Accept: ").append(accept).append("\r\n")
        append("Accept-Encoding: identity\r\n")
        append("User-Agent: SCARY-launcher\r\n")
        append("Connection: close\r\n\r\n")
    }.toByteArray(Charsets.ISO_8859_1)

    /** [body] is null when the answer was longer than allowed or was cut short. */
    class Response(val code: Int, val body: String?)

    /** Reads one response, as untrusted bytes: over-long headers or a broken form throw, a body past [maxBytes] is dropped. */
    @Throws(IOException::class)
    fun read(input: InputStream, maxBytes: Int): Response {
        var headerBytes = 0
        fun line(): String {
            val out = ByteArrayOutputStream()
            while (true) {
                val b = input.read()
                if (b < 0) throw IOException("the answer ended in its headers")
                if (++headerBytes > MAX_HEADER_BYTES) throw IOException("headers too long")
                if (b == '\n'.code) break
                if (b != '\r'.code) out.write(b)
            }
            return out.toString(Charsets.ISO_8859_1.name())
        }
        val status = line()
        val code = Regex("^HTTP/1\\.[01] (\\d{3})(?: .*)?$").matchEntire(status)?.groupValues?.get(1)?.toInt() ?: throw IOException("not an HTTP answer")
        var length: Long? = null
        var chunked = false
        var headers = 0
        while (true) {
            val header = line()
            if (header.isEmpty()) break
            if (++headers > MAX_HEADERS) throw IOException("too many headers")
            val name = header.substringBefore(':').trim().lowercase()
            val value = header.substringAfter(':', "").trim()
            when (name) {
                "content-length" -> length = value.toLongOrNull()?.takeIf { it >= 0 } ?: throw IOException("bad length")
                "transfer-encoding" -> chunked = value.lowercase().split(',').any { it.trim() == "chunked" }
            }
        }
        val out = ByteArrayOutputStream()
        var tooLong = false
        fun copy(count: Long): Boolean {
            var left = count
            val buffer = ByteArray(8 * 1024)
            while (left > 0) {
                val n = input.read(buffer, 0, minOf(left, buffer.size.toLong()).toInt())
                if (n < 0) return false
                left -= n
                if (!tooLong && out.size() + n > maxBytes) tooLong = true
                if (!tooLong) out.write(buffer, 0, n)
            }
            return true
        }
        val complete = when {
            chunked -> {
                var whole = true
                while (true) {
                    headerBytes = 0
                    val size = line().substringBefore(';').trim().toLongOrNull(16)?.takeIf { it >= 0 } ?: throw IOException("bad chunk")
                    if (size == 0L) break
                    if (!copy(size)) {
                        whole = false
                        break
                    }
                    headerBytes = 0
                    line()
                }
                whole
            }
            length != null -> copy(length)
            else -> {
                val buffer = ByteArray(8 * 1024)
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    if (!tooLong && out.size() + n > maxBytes) tooLong = true
                    if (!tooLong) out.write(buffer, 0, n)
                }
                true
            }
        }
        return Response(code, if (complete && !tooLong) out.toString(Charsets.UTF_8.name()) else null)
    }
}
