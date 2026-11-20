// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary.core

import io.github.diamondrubinny.scary.core.realdebrid.RdActiveCountResponse
import io.github.diamondrubinny.scary.core.realdebrid.RdTorrentAddResponse
import io.github.diamondrubinny.scary.core.realdebrid.RdTorrentInfo
import io.github.diamondrubinny.scary.core.realdebrid.RdUnrestrictLinkResponse
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The torrent models have generated serializers and read answers that carry fields they do not name. */
class RealDebridTorrentModelsTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `torrent info is decoded with its files`() {
        val info = json.decodeFromString<RdTorrentInfo>(
            """{"id":"ABC123","filename":"Film.2018.1080p","original_filename":"x","hash":"aa11","bytes":2048,
               "status":"downloaded","progress":100,"links":["https://real-debrid.com/d/XYZ"],
               "files":[{"id":1,"path":"/Film.mkv","bytes":2048,"selected":1}]}""",
        )
        assertEquals("ABC123", info.id)
        assertEquals(100.0, info.progress, 0.0)
        assertEquals(listOf("https://real-debrid.com/d/XYZ"), info.links)
        assertEquals("/Film.mkv", info.files.single().path)
        assertEquals(1, info.files.single().selected)
        assertNull(info.seeders)
    }

    @Test
    fun `missing optional fields fall back to defaults`() {
        assertNull(json.decodeFromString<RdTorrentAddResponse>("""{"id":"ABC123"}""").uri)
        assertEquals(RdActiveCountResponse(active = 2, limit = 0), json.decodeFromString<RdActiveCountResponse>("""{"nb":2,"active":2}"""))
        val link = json.decodeFromString<RdUnrestrictLinkResponse>(
            """{"id":"L1","filename":"Film.mkv","mimeType":"video/x-matroska","filesize":2048,
               "link":"https://real-debrid.com/d/XYZ","download":"https://dl.real-debrid.com/f/Film.mkv","streamable":1}""",
        )
        assertEquals("video/x-matroska", link.mimeType)
        assertEquals(1, link.streamable)
    }
}
