// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary.core

import io.github.diamondrubinny.scary.core.player.WatchHistory
import io.github.diamondrubinny.scary.core.realdebrid.Magnets
import io.github.diamondrubinny.scary.core.realdebrid.RealDebrid.TorrentState
import io.github.diamondrubinny.scary.core.torbox.TorBox
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** TorBox's API as documented (api.torbox.app/openapi.json, 25 Nov 2026), and hostile answers. */
class TorBoxTest {
    private val token = "0123456789abcdef-0123456789abcdef"
    private val hash = "0123456789abcdef0123456789abcdef01234567"

    private fun ok(data: String) = """{"success":true,"error":null,"detail":"ok","data":$data}"""

    private fun assertThrows(block: () -> Unit) {
        try {
            block()
        } catch (e: IllegalArgumentException) {
            return
        }
        throw AssertionError("expected a refusal")
    }

    @Test
    fun `requests follow the documentation`() {
        assertEquals("https://api.torbox.app/v1/api/user/auth/device/start?app=SCARY", TorBox.DEVICE_START_URL)
        assertEquals("""{"device_code":"abcDEF123456"}""", TorBox.deviceTokenBody("abcDEF123456"))
        assertEquals("https://api.torbox.app/v1/api/torrents/mylist?offset=0&limit=500", TorBox.torrentsUrl())
        assertEquals("https://api.torbox.app/v1/api/torrents/mylist?offset=500&limit=500&bypass_cache=true", TorBox.torrentsUrl(500, fresh = true))
        assertEquals("https://api.torbox.app/v1/api/torrents/mylist?id=42&bypass_cache=true", TorBox.torrentUrl(42))
        assertEquals(
            "https://api.torbox.app/v1/api/torrents/requestdl?token=$token&torrent_id=42&file_id=3",
            TorBox.linkUrl(token, 42, 3),
        )
        assertEquals("""{"torrent_id":42,"operation":"delete"}""", TorBox.deleteBody(42))
        assertThrows { TorBox.deviceTokenBody("a\"b") }
        assertThrows { TorBox.linkUrl("tok&x=1", 42, 0) }
        assertThrows { TorBox.linkUrl(token, -1, 0) }
        assertThrows { TorBox.torrentUrl(-5) }
    }

    @Test
    fun `a magnet is sent as a multipart form, not zipped`() {
        val magnet = Magnets.parse("magnet:?xt=urn:btih:$hash&dn=A+Film")!!
        val boundary = "scaryBoundary0123456789"
        assertEquals("multipart/form-data; boundary=$boundary", TorBox.multipartType(boundary))
        val body = TorBox.createBody(magnet, boundary)
        assertEquals(
            "--$boundary\r\nContent-Disposition: form-data; name=\"magnet\"\r\n\r\n${magnet.uri}\r\n" +
                "--$boundary\r\nContent-Disposition: form-data; name=\"allow_zip\"\r\n\r\nfalse\r\n--$boundary--\r\n",
            body,
        )
        assertThrows { TorBox.createBody(magnet, "short") }
        assertThrows { TorBox.createBody(magnet.copy(name = boundary), boundary) }
    }

    @Test
    fun `the device code is read strictly and only TorBox's own page is shown`() {
        val answer = ok(
            """{"device_code":"abcDEF123456","code":"123456","interval":0,"expires_in":600,
               "verification_url":"https://torbox.app/oauth/device","friendly_verification_url":"https://evil.example/x"}""",
        )
        val code = TorBox.parseDeviceCode(answer)!!
        assertEquals("123456", code.userCode)
        assertEquals("https://torbox.app/oauth/device", code.verificationUrl)
        // The example's interval of 0 never becomes a busy loop.
        assertEquals(5, code.intervalSeconds)
        assertEquals(600, code.expiresInSeconds)
        assertNull(TorBox.parseDeviceCode(ok("""{"device_code":"abcDEF123456","code":"123456","verification_url":"http://torbox.app/x"}""")))
        assertNull(TorBox.parseDeviceCode(ok("""{"device_code":"abcDEF123456","code":"12 34","verification_url":"https://torbox.app/x"}""")))
        assertNull(TorBox.parseDeviceCode(ok("""{"device_code":"abcDEF123456","code":"123456","verification_url":"https://torbox.app.evil.example/x"}""")))
        assertNull(TorBox.parseDeviceCode("not json"))
    }

    @Test
    fun `the token comes only from an answer without an error`() {
        assertEquals(token, TorBox.parseToken(ok("""{"access_token":"$token","token_type":"Bearer"}""")))
        assertNull(TorBox.parseToken(ok("""{"access_token":"has space","token_type":"Bearer"}""")))
        assertNull(TorBox.parseToken(ok("""{"access_token":"$token","token_type":"mac"}""")))
        val waiting = """{"success":false,"error":"DEVICE_CODE_NOT_USED","detail":"Not used yet.","data":null}"""
        assertNull(TorBox.parseToken(waiting))
        assertEquals(TorBox.NOT_APPROVED_YET, TorBox.errorName(waiting))
        assertTrue(TorBox.isToken(token))
        assertFalse(TorBox.isToken("short"))
    }

    @Test
    fun `the account says its plan`() {
        val user = TorBox.parseUser(ok("""{"id":1,"email":"viewer@example.org","plan":2,"premium_expires_at":"2027-01-01T00:00:00Z"}"""))!!
        assertTrue(user.paid)
        assertEquals("2027-01-01T00:00:00Z", user.expiration)
        assertFalse(TorBox.parseUser(ok("""{"plan":0}"""))!!.paid)
        assertNull(TorBox.parseUser(ok("""{"plan":"2"}""")))
    }

    @Test
    fun `errors are named for the viewer, and an error beside success still counts`() {
        fun error(name: String, success: Boolean = false) = """{"success":$success,"error":"$name","detail":"x","data":null}"""
        assertNull(TorBox.problem(ok("null")))
        assertEquals(TorBox.Problem.SLOTS_FULL, TorBox.problem(error("ACTIVE_LIMIT")))
        assertEquals(TorBox.Problem.LIMIT, TorBox.problem(error("MONTHLY_LIMIT")))
        assertEquals(TorBox.Problem.TOO_BIG, TorBox.problem(error("DOWNLOAD_TOO_LARGE")))
        assertEquals(TorBox.Problem.NOT_ALLOWED, TorBox.problem(error("PLAN_RESTRICTED_FEATURE")))
        assertEquals(TorBox.Problem.SIGNED_OUT, TorBox.problem(error("BAD_TOKEN")))
        assertEquals(TorBox.Problem.INVALID, TorBox.problem(error("BOZO_TORRENT")))
        assertEquals(TorBox.Problem.BUSY, TorBox.problem(error("RATE_LIMIT_EXCEEDED")))
        assertEquals(TorBox.Problem.UNAVAILABLE, TorBox.problem(error("DOWNLOAD_SERVER_OFFLINE")))
        assertEquals(TorBox.Problem.INVALID, TorBox.problem(error("DOWNLOAD_METADATA_ERROR", success = true)))
        assertEquals(TorBox.Problem.OTHER, TorBox.problem(error("SOMETHING_NEW")))
        assertEquals(TorBox.Problem.OTHER, TorBox.problem("<html>"))
        assertEquals(TorBox.Problem.OTHER, TorBox.problem(null))
        assertEquals(TorBox.Problem.OTHER, TorBox.problem("""{"success":false,"error":null,"data":null}"""))
    }

    @Test
    fun `a finished torrent gives its videos with their subtitles and without the sample`() {
        val answer = ok(
            """[{"id":7,"name":"A.Film.2020.1080p","size":4000000000,"download_state":"uploading","progress":1,
                 "download_finished":true,"download_present":true,"files":[
                   {"id":0,"name":"A.Film.2020.1080p/A.Film.2020.1080p.mkv","short_name":"A.Film.2020.1080p.mkv","size":3900000000,"mimetype":"video/x-matroska"},
                   {"id":1,"name":"A.Film.2020.1080p/Sample/sample.mkv","short_name":"sample.mkv","size":20000000,"mimetype":"video/x-matroska"},
                   {"id":2,"name":"A.Film.2020.1080p/A.Film.2020.1080p.en.srt","short_name":"A.Film.2020.1080p.en.srt","size":90000,"mimetype":"application/x-subrip"},
                   {"id":3,"name":"A.Film.2020.1080p/readme.txt","short_name":"readme.txt","size":100,"mimetype":"text/plain"}]}]""",
        )
        val torrent = TorBox.parseTorrents(answer).single()
        assertEquals(TorrentState.READY, torrent.state)
        assertEquals(100, torrent.progress)
        val video = torrent.videos().single()
        assertEquals("torbox:7:0", video.key)
        assertEquals("A.Film.2020.1080p.mkv", video.name)
        assertEquals(listOf("torbox:7:2"), video.subtitles.map { it.link })
        assertEquals(7L to 0, TorBox.parseFileRef(video.key))
        assertEquals("torbox7", torrent.status().id)
        assertEquals(7L, TorBox.parseStatusId("torbox7"))
        assertNull(TorBox.parseStatusId("ABC123"))
        assertNull(TorBox.parseFileRef("t:ABC:0"))
        assertNull(TorBox.parseFileRef("torbox:7:-1"))
    }

    @Test
    fun `a torrent is finished by its flag, never by its state's name`() {
        fun one(fields: String) = TorBox.parseTorrents(ok("""{"id":9,"name":"x","size":10,$fields,"files":[]}""")).single()
        assertEquals(TorrentState.FINISHING, one(""""download_state":"completed","download_finished":false,"progress":1""").state)
        assertEquals(TorrentState.CONVERTING, one(""""download_state":"metaDL","download_finished":false,"progress":0""").state)
        assertEquals(TorrentState.DOWNLOADING, one(""""download_state":"stalled (no seeds)","download_finished":false,"progress":0.25""").state)
        assertEquals(25, one(""""download_state":"downloading","download_finished":false,"progress":0.25""").progress)
        assertEquals(40, one(""""download_state":"downloading","download_finished":false,"progress":40""").progress)
        assertEquals(TorrentState.QUEUED, one(""""download_state":"paused","download_finished":false""").state)
        assertEquals(TorrentState.FAILED, one(""""download_state":"error","download_finished":false""").state)
        // A state SCARY does not know is never a failure: a failed torrent is deleted.
        assertEquals(TorrentState.DOWNLOADING, one(""""download_state":"somethingNew","download_finished":false""").state)
        // Finished but no longer held: nothing to play.
        assertTrue(one(""""download_state":"cached","download_finished":true,"download_present":false""").videos().isEmpty())
    }

    @Test
    fun `hostile lists are dropped item by item`() {
        assertTrue(TorBox.parseTorrents("[]").isEmpty())
        assertTrue(TorBox.parseTorrents(ok("null")).isEmpty())
        assertTrue(TorBox.parseTorrents("""{"success":true,"error":"ITEM_NOT_FOUND","detail":"No torrents found for this user.","data":[{"id":1,"name":"x"}]}""").isEmpty())
        val mixed = ok("""[{"id":"7","name":"x"},{"id":8},{"id":9,"name":"good","files":[{"id":-1,"short_name":"a.mkv"},"junk"]},7]""")
        val torrents = TorBox.parseTorrents(mixed)
        assertEquals(listOf(9L), torrents.map { it.id })
        assertTrue(torrents.single().files.isEmpty())
    }

    @Test
    fun `an added magnet gives its torrent id`() {
        assertEquals(31L, TorBox.parseCreated("""{"success":true,"error":null,"detail":"Torrent Added Successfully","data":{"hash":"$hash","torrent_id":31,"auth_id":"a"}}"""))
        assertNull(TorBox.parseCreated(ok("""{"hash":"$hash","name":"queued","queued_id":4}""")))
        assertNull(TorBox.parseCreated(ok("""{"torrent_id":"31"}""")))
    }

    @Test
    fun `a file's link is https and never carries the account's token`() {
        assertEquals("https://cdn.example.net/dl/abc/file.mkv", TorBox.parseLink(ok("\"https://cdn.example.net/dl/abc/file.mkv\""), token))
        assertNull(TorBox.parseLink(ok("\"http://cdn.example.net/file.mkv\""), token))
        assertNull(TorBox.parseLink(ok("\"https://user:pw@cdn.example.net/file.mkv\""), token))
        assertNull(TorBox.parseLink(ok("\"https://cdn.example.net/file.mkv?token=$token\""), token))
        assertNull(TorBox.parseLink(ok("""{"url":"https://cdn.example.net/file.mkv"}"""), token))
        assertNull(TorBox.parseLink("""{"success":false,"error":"LINK_OFFLINE","detail":"x","data":"https://cdn.example.net/f.mkv"}""", token))
    }

    @Test
    fun `a TorBox file keeps its place in the watch history like any other`() {
        val key = TorBox.fileRef(7, 3)
        assertTrue(WatchHistory.isValidKey(key))
        val entries = WatchHistory.progress(emptyList(), key, "Show.S01E01.mkv", null, 60_000, 3_600_000, 1)
        assertEquals(key, entries.single().key)
        assertEquals(entries, WatchHistory.decode(WatchHistory.encode(entries)))
        assertFalse(WatchHistory.isValidKey("torbox:7"))
        assertFalse(WatchHistory.isValidKey("torbox:x:1"))
        assertFalse(WatchHistory.isValidKey("torbox:7:1:2"))
    }
}
