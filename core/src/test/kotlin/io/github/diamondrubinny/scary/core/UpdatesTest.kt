// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary.core

import io.github.diamondrubinny.scary.core.update.UpdateManifest
import io.github.diamondrubinny.scary.core.update.Updates
import io.github.diamondrubinny.scary.core.update.Updates.ApkCheck
import io.github.diamondrubinny.scary.core.update.Updates.Decision
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The in-app update: only the official repository, only official APKs, and the viewer decides when. */
class UpdatesTest {
    private val sha = "a".repeat(64)
    private val official = Updates.OFFICIAL_CERT_SHA256.first()

    private fun manifest(
        repository: String = Updates.OFFICIAL_REPOSITORY,
        channel: String = "stable",
        pkg: String = Updates.PACKAGE_NAME,
        versionCode: String = "205",
        size: String = "3129206",
        sha256: String = "\"$sha\"",
        notes: String = """{"en": "Faster rows.", "he": "שורות מהירות יותר."}""",
    ) = """{"repository": "$repository", "channel": "$channel", "package": "$pkg", "versionCode": $versionCode,
        "versionName": "0.4.0", "sha256": $sha256, "size": $size, "minSdk": 26, "notes": $notes}"""

    @Test
    fun `a manifest from the official stable release is read`() {
        val m = Updates.parse(manifest())!!
        assertEquals(205, m.versionCode)
        assertEquals("0.4.0", m.versionName)
        assertEquals(sha, m.sha256)
        assertEquals(3129206L, m.size)
        assertEquals("שורות מהירות יותר.", m.notesFor("he"))
        // A language with no notes falls back to English.
        assertEquals("Faster rows.", m.notesFor("ru"))
    }

    @Test
    fun `a fork's manifest, another channel or package, or a broken one is refused`() {
        assertNull(Updates.parse(manifest(repository = "someone/Scary")))
        assertNull(Updates.parse(manifest(repository = "diamondrubinny/Scary-fork")))
        assertNull(Updates.parse(manifest(channel = "latest-build")))
        assertNull(Updates.parse(manifest(pkg = "com.evil.scary")))
        assertNull(Updates.parse(manifest(versionCode = "\"205\"")))
        assertNull(Updates.parse(manifest(versionCode = "-1")))
        assertNull(Updates.parse(manifest(size = "10")))
        assertNull(Updates.parse(manifest(size = "999999999999")))
        assertNull(Updates.parse(manifest(sha256 = "\"xyz\"")))
        assertNull(Updates.parse(manifest(sha256 = "null")))
        assertNull(Updates.parse("not json"))
        assertNull(Updates.parse("[]"))
        assertNull(Updates.parse(""))
    }

    @Test
    fun `notes are kept as short plain text`() {
        val hostile = "\"Update!\\u0007\\u202Egnp.exe\\u202C " + "x".repeat(2000) + "\""
        val m = Updates.parse(manifest(notes = """{"en": $hostile, "x-evil": "hi", "he": 5}"""))!!
        val note = m.notesFor("en")
        assertTrue(note.length <= 600)
        assertFalse(note.any { it.isISOControl() && it != '\n' })
        assertFalse('‮' in note)
        assertEquals(setOf("en"), m.notes.keys)
    }

    @Test
    fun `only HTTPS to GitHub's own hosts`() {
        assertTrue(Updates.isAllowedUrl(Updates.MANIFEST_URL))
        assertTrue(Updates.isAllowedUrl(Updates.APK_URL))
        assertTrue(Updates.isAllowedUrl("https://objects.githubusercontent.com/github-production-release-asset/1/2?X=1"))
        assertFalse(Updates.isAllowedUrl("http://github.com/diamondrubinny/Scary/releases/download/stable/scary.apk"))
        assertFalse(Updates.isAllowedUrl("https://evil.com/scary.apk"))
        assertFalse(Updates.isAllowedUrl("https://github.com.evil.com/x"))
        assertFalse(Updates.isAllowedUrl("https://github.com@evil.com/x"))
        assertFalse(Updates.isAllowedUrl("https://user@github.com/x"))
        assertFalse(Updates.isAllowedUrl("https://github.com:8443/x"))
        assertFalse(Updates.isAllowedUrl("file:///sdcard/scary.apk"))
        assertFalse(Updates.isAllowedUrl("not a url"))
        // The addresses are the official repository's, whatever a manifest says.
        assertTrue(Updates.MANIFEST_URL.startsWith("https://github.com/diamondrubinny/Scary/releases/download/stable/"))
        assertTrue(Updates.APK_URL.startsWith("https://github.com/diamondrubinny/Scary/releases/download/stable/"))
    }

    @Test
    fun `newer versions are offered, unless skipped or put off, and check now offers them anyway`() {
        val m = Updates.parse(manifest())!!
        val now = 1_000_000L
        assertEquals(Decision.OFFER, Updates.decide(m, 200, 30, null, null, now))
        assertEquals(Decision.UP_TO_DATE, Updates.decide(m, 205, 30, null, null, now))
        assertEquals(Decision.UP_TO_DATE, Updates.decide(m, 300, 30, null, null, now))
        assertEquals(Decision.TOO_NEW_FOR_DEVICE, Updates.decide(m.copy(minSdk = 31), 200, 30, null, null, now))
        assertEquals(Decision.SKIPPED, Updates.decide(m, 200, 30, 205, null, now))
        assertEquals(Decision.OFFER, Updates.decide(m, 200, 30, 204, null, now))
        assertEquals(Decision.LATER, Updates.decide(m, 200, 30, null, now + 1, now))
        assertEquals(Decision.OFFER, Updates.decide(m, 200, 30, null, now - 1, now))
        assertEquals(Decision.OFFER, Updates.decide(m, 200, 30, 205, now + 1, now, manual = true))
    }

    @Test
    fun `checks are spaced, and a clock that went back checks again`() {
        assertTrue(Updates.checkDue(null, 5))
        assertFalse(Updates.checkDue(1_000, 1_000 + Updates.CHECK_INTERVAL_MS - 1))
        assertTrue(Updates.checkDue(1_000, 1_000 + Updates.CHECK_INTERVAL_MS))
        assertTrue(Updates.checkDue(10_000, 5_000))
    }

    @Test
    fun `only an official APK of this package and the announced version is installed`() {
        val m = Updates.parse(manifest())!!
        val ok = Updates.verifyApk(m, Updates.PACKAGE_NAME, 205, 200, setOf(official), setOf(official))
        assertEquals(ApkCheck.OK, ok)
        assertEquals(ApkCheck.WRONG_PACKAGE, Updates.verifyApk(m, "com.evil", 205, 200, setOf(official), setOf(official)))
        assertEquals(ApkCheck.WRONG_VERSION, Updates.verifyApk(m, Updates.PACKAGE_NAME, 204, 200, setOf(official), setOf(official)))
        // No downgrades, even if the manifest says so.
        assertEquals(ApkCheck.WRONG_VERSION, Updates.verifyApk(m.copy(versionCode = 150), Updates.PACKAGE_NAME, 150, 200, setOf(official), setOf(official)))
        // A fork's APK: signed with another key.
        val fork = "b".repeat(64)
        assertEquals(ApkCheck.NOT_OFFICIAL, Updates.verifyApk(m, Updates.PACKAGE_NAME, 205, 200, setOf(fork), setOf(official)))
        assertEquals(ApkCheck.NOT_OFFICIAL, Updates.verifyApk(m, Updates.PACKAGE_NAME, 205, 200, setOf(official, fork), setOf(official)))
        assertEquals(ApkCheck.NOT_OFFICIAL, Updates.verifyApk(m, Updates.PACKAGE_NAME, 205, 200, emptySet(), setOf(official)))
        // Android 8 cannot read an APK's certificates: Android checks them itself when installing.
        assertEquals(ApkCheck.OK, Updates.verifyApk(m, Updates.PACKAGE_NAME, 205, 200, null, setOf(official)))
    }

    @Test
    fun `a fork's own build never offers the official updates`() {
        assertTrue(Updates.isOfficialBuild(setOf(official)))
        assertFalse(Updates.isOfficialBuild(setOf("c".repeat(64))))
        assertFalse(Updates.isOfficialBuild(emptySet()))
        assertFalse(Updates.isOfficialBuild(null))
        assertNotNull(Updates.parse(manifest()))
    }
}
