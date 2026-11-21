// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary.core.sources

import io.github.diamondrubinny.scary.core.realdebrid.Magnets
import org.xml.sax.Attributes
import org.xml.sax.InputSource
import org.xml.sax.SAXException
import org.xml.sax.helpers.DefaultHandler
import java.io.IOException
import java.io.StringReader
import java.net.URI
import java.net.URLEncoder
import javax.xml.parsers.ParserConfigurationException
import javax.xml.parsers.SAXParserFactory

/**
 * The Torznab search of the viewer's own indexer (Prowlarr, Jackett), as its specification gives it
 * (torznab.github.io/spec-1.3-draft, read 20 Nov 2026): GET <address>?t=search&q=…&apikey=…, answered with an RSS feed
 * whose items carry <torznab:attr name="…" value="…"/> elements. SCARY ships no address of any indexer or site: the
 * address and the key are the viewer's.
 *
 * The answer is untrusted. A document that declares a DOCTYPE is refused whole, so no entity can be defined; only items
 * with a magnet link or an info-hash are kept, and each link goes through [Magnets]. Links to .torrent files are never
 * followed.
 */
object Torznab {
    const val MAX_ANSWER_BYTES = 2 * 1024 * 1024
    const val MAX_QUERY = 120
    private const val MAX_URL = 512
    private const val MAX_KEY = 128
    private const val MAX_ITEMS = 200
    private const val MAX_TITLE = 300
    private const val MAX_FIELD = 4200

    /** Movies and TV, as the specification numbers them. */
    private const val CATEGORIES = "2000,5000"
    private const val MOVIES = "2000"
    private val imdbPattern = Regex("^tt\\d{6,8}$")
    private val keyPattern = Regex("^[A-Za-z0-9_\\-]{8,$MAX_KEY}$")

    /**
     * The indexer's address as the viewer gave it, checked: a host, no user name, no query of its own, and HTTPS, or
     * plain HTTP only at a private home-network address written as numbers (LanHttp), where a home indexer lives.
     */
    fun endpoint(text: String): String? {
        val trimmed = text.trim().trimEnd('/')
        if (trimmed.length !in 12..MAX_URL || trimmed.any { it.isWhitespace() || it.isISOControl() }) return null
        val secure = trimmed.startsWith("https://")
        if (!secure && !trimmed.startsWith("http://")) return null
        val uri = runCatching { URI(trimmed) }.getOrNull() ?: return null
        if (uri.host.isNullOrBlank() || uri.rawUserInfo != null || uri.rawQuery != null || uri.rawFragment != null) return null
        if (!secure && LanHttp.target(trimmed) == null) return null
        return trimmed
    }

    fun apiKey(text: String): String? = text.trim().takeIf(keyPattern::matches)

    /** The search to send; null when there is nothing to search for. The address holds the key: never log it. */
    fun searchUrl(endpoint: String, apiKey: String, query: String): String? {
        val words = cleanQuery(query) ?: return null
        return "$endpoint?t=search&cat=$CATEGORIES&extended=1&limit=100&q=${encode(words)}&apikey=${encode(apiKey)}"
    }

    /**
     * The search for one film by its IMDb number ("tt0063350"), or null when [imdbId] is not one. Prowlarr reads the
     * number with or without "tt", and Jackett passes it on as it came (their source, read 20 Nov 2026).
     */
    fun movieUrl(endpoint: String, apiKey: String, imdbId: String): String? {
        if (!imdbPattern.matches(imdbId)) return null
        return "$endpoint?t=movie&cat=$MOVIES&extended=1&limit=100&imdbid=$imdbId&apikey=${encode(apiKey)}"
    }

    /** The search text as it is sent: one line, single spaces, of sane length. */
    fun cleanQuery(query: String): String? =
        query.filterNot(Char::isISOControl).trim().replace(Regex("\\s+"), " ").take(MAX_QUERY).takeIf { it.length >= 2 }

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")

    sealed interface Answer {
        data class Results(val candidates: List<Releases.Candidate>) : Answer

        /** The indexer answered with an error (a wrong key, a limit reached); [code] is its own number, 0 if none. */
        data class Refused(val code: Int) : Answer

        /** Not a feed SCARY can read. */
        data object Unreadable : Answer
    }

    fun parse(text: String): Answer {
        if (text.length > MAX_ANSWER_BYTES || text.contains("<!DOCTYPE", ignoreCase = true) || text.contains("<!ENTITY", ignoreCase = true)) return Answer.Unreadable
        val handler = FeedHandler()
        try {
            val factory = SAXParserFactory.newInstance().apply { isNamespaceAware = true }
            // Not every platform's parser knows these switches; the DOCTYPE check above holds on all of them.
            runCatching { factory.setFeature("http://xml.org/sax/features/external-general-entities", false) }
            runCatching { factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
            factory.newSAXParser().parse(InputSource(StringReader(text)), handler)
        } catch (e: SAXException) {
            return Answer.Unreadable
        } catch (e: IOException) {
            return Answer.Unreadable
        } catch (e: ParserConfigurationException) {
            return Answer.Unreadable
        }
        handler.error?.let { return Answer.Refused(it) }
        return if (handler.isFeed) Answer.Results(handler.candidates) else Answer.Unreadable
    }

    private class FeedHandler : DefaultHandler() {
        val candidates = ArrayList<Releases.Candidate>()
        var error: Int? = null
        var isFeed = false
        private var depth = 0
        private var inItem = false
        private var items = 0
        private val text = StringBuilder()
        private var capture = false
        private var title = ""
        private var links = ArrayList<String>()
        private var size = 0L
        private var seeders = -1
        private var infoHash: String? = null
        private var source = ""

        private fun name(localName: String?, qName: String?): String =
            (localName?.takeIf { it.isNotEmpty() } ?: qName.orEmpty().substringAfterLast(':')).lowercase()

        override fun startElement(uri: String?, localName: String?, qName: String?, attributes: Attributes) {
            depth++
            val element = name(localName, qName)
            if (depth == 1) {
                if (element == "error") error = attributes.getValue("code")?.toIntOrNull() ?: 0
                isFeed = element == "rss"
                return
            }
            if (!isFeed) return
            if (element == "item") {
                inItem = items++ < MAX_ITEMS
                title = ""
                links = ArrayList()
                size = 0L
                seeders = -1
                infoHash = null
                source = ""
                return
            }
            if (!inItem) return
            when (element) {
                "title", "link", "guid", "size", "jackettindexer", "prowlarrindexer" -> {
                    capture = true
                    text.setLength(0)
                }
                "enclosure" -> {
                    attributes.getValue("url")?.takeIf { it.length <= MAX_FIELD }?.let { links += it }
                    if (size == 0L) size = attributes.getValue("length")?.toLongOrNull()?.coerceAtLeast(0) ?: 0L
                }
                "attr" -> {
                    val value = attributes.getValue("value")?.takeIf { it.length <= MAX_FIELD } ?: return
                    when (attributes.getValue("name")?.lowercase()) {
                        "seeders" -> seeders = value.toIntOrNull()?.coerceIn(0, 10_000_000) ?: seeders
                        "size" -> value.toLongOrNull()?.takeIf { it > 0 }?.let { size = it }
                        "infohash" -> infoHash = value
                        "magneturl" -> links.add(0, value)
                    }
                }
            }
        }

        override fun characters(ch: CharArray, start: Int, length: Int) {
            if (capture && text.length + length <= MAX_FIELD) text.append(ch, start, length)
        }

        override fun endElement(uri: String?, localName: String?, qName: String?) {
            depth--
            val element = name(localName, qName)
            if (capture) {
                val value = text.toString().trim()
                when (element) {
                    "title" -> title = value.filterNot(Char::isISOControl).take(MAX_TITLE)
                    "link", "guid" -> links += value
                    "size" -> if (size == 0L) size = value.toLongOrNull()?.coerceAtLeast(0) ?: 0L
                    "jackettindexer", "prowlarrindexer" -> source = value.filterNot(Char::isISOControl).take(60)
                }
                capture = false
            }
            if (element == "item" && inItem) {
                inItem = false
                if (title.isBlank()) return
                // A magnet link the indexer gave, or one built from the info-hash alone; a .torrent address is neither.
                val magnet = links.firstNotNullOfOrNull { it.takeIf { l -> l.startsWith("magnet:", ignoreCase = true) }?.let(Magnets::parse) }
                    ?: infoHash?.let(Magnets::parse)
                    ?: return
                candidates += Releases.Candidate(title, magnet.copy(name = magnet.name ?: title.take(255)), source, size, seeders, Releases.tags(title))
            }
        }
    }
}
