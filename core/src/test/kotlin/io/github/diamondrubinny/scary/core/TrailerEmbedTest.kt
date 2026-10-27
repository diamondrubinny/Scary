// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary.core

import io.github.diamondrubinny.scary.core.trailer.TrailerEmbed
import io.github.diamondrubinny.scary.core.trailer.TrailerEvent
import io.github.diamondrubinny.scary.core.trailer.TrailerHost
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.MessageDigest
import java.util.Base64

class TrailerEmbedTest {
    @Test
    fun `accepts only youtube ids`() {
        assertTrue(TrailerEmbed.isValidVideoId("V6wWKNij_1M"))
        assertTrue(TrailerEmbed.isValidVideoId("a-b_c-d_e-f"))
        listOf(null, "", "short", "V6wWKNij_1MX", "\"><script>x", "V6wWKNij 1M", "V6wWKNij'1M", "ünïcödë_123").forEach {
            assertFalse("$it", TrailerEmbed.isValidVideoId(it))
        }
    }

    @Test
    fun `refuses to build a page around anything else`() {
        assertThrows(IllegalArgumentException::class.java) { TrailerEmbed.html("\"><img src=x>", muted = true) }
        assertThrows(IllegalArgumentException::class.java) { TrailerEmbed.html("abc'+alert(1", muted = true) }
    }

    @Test
    fun `page plays the id in privacy-enhanced mode, once, with the chosen sound`() {
        val muted = TrailerEmbed.html("V6wWKNij_1M", muted = true)
        assertTrue(muted.contains("data-video=\"V6wWKNij_1M\""))
        assertTrue(muted.contains("data-muted=\"1\""))
        assertTrue(muted.contains("data-host=\"https://www.youtube-nocookie.com\""))
        assertTrue(TrailerEmbed.html("V6wWKNij_1M", muted = true, TrailerHost.YOUTUBE).contains("data-host=\"https://www.youtube.com\""))
        assertTrue(muted.contains("https://www.youtube.com/iframe_api"))
        assertFalse("never loops", muted.contains("loop"))
        assertTrue(TrailerEmbed.html("V6wWKNij_1M", muted = false).contains("data-muted=\"0\""))
    }

    @Test
    fun `the page's own script is the only inline script the policy allows`() {
        val html = TrailerEmbed.html("V6wWKNij_1M", muted = true)
        val script = html.substringAfter("<script>").substringBefore("</script>")
        val hash = Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-256").digest(script.toByteArray()))
        assertTrue(TrailerEmbed.contentSecurityPolicy.contains("'sha256-$hash'"))
        assertFalse(TrailerEmbed.contentSecurityPolicy.contains("'unsafe-inline' https") || TrailerEmbed.contentSecurityPolicy.contains("script-src 'unsafe"))
        assertEquals("one inline script", 1, Regex("<script").findAll(html).count())
    }

    @Test
    fun `identifies the app to youtube by its app id`() {
        assertEquals("https://io.github.diamondrubinny.scary/", TrailerEmbed.BASE_URL)
        assertEquals("https://io.github.diamondrubinny.scary", TrailerEmbed.ORIGIN)
    }

    @Test
    fun `only known events are understood`() {
        assertEquals(TrailerEvent.PLAYING, TrailerEvent.parse("playing"))
        assertEquals(TrailerEvent.ENDED, TrailerEvent.parse("ended"))
        assertEquals(TrailerEvent.ERROR, TrailerEvent.parse("error"))
        assertEquals(TrailerEvent.READY, TrailerEvent.parse("ready"))
        assertEquals(TrailerEvent.ERROR, TrailerEvent.parse("error:150"))
        assertEquals(TrailerEvent.ERROR, TrailerEvent.parse("error:api"))
        assertEquals("150", TrailerEvent.errorDetail("error:150"))
        assertEquals("api", TrailerEvent.errorDetail("error:api"))
        assertNull(TrailerEvent.errorDetail("error"))
        assertNull(TrailerEvent.parse("error:<script>"))
        assertNull(TrailerEvent.parse("error:12345"))
        assertNull(TrailerEvent.parse("PLAYING"))
        assertNull(TrailerEvent.parse("open:https://evil"))
        assertNull(TrailerEvent.parse(null))
    }
}
