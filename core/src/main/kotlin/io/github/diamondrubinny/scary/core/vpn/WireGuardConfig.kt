// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary.core.vpn

/**
 * A WireGuard configuration file the viewer got from their own VPN provider (docs/VPN_PLAN.md), read as untrusted
 * text before it is kept or handed to the tunnel library. SCARY is the client only: it ships no provider, server or
 * key.
 *
 * Only the settings a tunnel needs are kept, and the file is written again from them: the wg-quick hooks that run
 * commands (PreUp, PostUp, PreDown, PostDown), SaveConfig, Table and anything unknown are dropped, never run. Which
 * apps go through the tunnel is SCARY's to say ([forApp]), not the file's.
 */
object WireGuardConfig {
    const val MAX_CHARS = 8 * 1024
    private const val MAX_PEERS = 8
    private const val MAX_LINE = 600
    private val key = Regex("^[A-Za-z0-9+/]{43}=$")
    private val address = Regex("^[0-9A-Fa-f:.]{2,45}(/\\d{1,3})?$")
    private val host = Regex("^(\\[[0-9A-Fa-f:.]{2,45}]|[A-Za-z0-9](?:[A-Za-z0-9.\\-]{0,251}[A-Za-z0-9])?)$")
    private val interfaceKeys = mapOf("privatekey" to "PrivateKey", "address" to "Address", "dns" to "DNS", "mtu" to "MTU")
    private val peerKeys = mapOf(
        "publickey" to "PublicKey", "presharedkey" to "PresharedKey", "allowedips" to "AllowedIPs",
        "endpoint" to "Endpoint", "persistentkeepalive" to "PersistentKeepalive",
    )

    /**
     * What the file says, for the viewer to see before it is used: where it connects to ([endpoints], host and port)
     * and the tunnel's own addresses. Never the keys.
     */
    data class Summary(val endpoints: List<String>, val addresses: List<String>, val dns: List<String>)

    /** [text] is the file as SCARY keeps it: only the settings above, in a fixed order. */
    data class Parsed(val text: String, val summary: Summary) {
        /** The file as the tunnel is started with: only [packageName]'s traffic goes through it. */
        fun forApp(packageName: String): String {
            require(Regex("^[A-Za-z0-9_.]{1,200}$").matches(packageName)) { "invalid package name" }
            return text.replaceFirst("[Interface]\n", "[Interface]\nIncludedApplications = $packageName\n")
        }
    }

    /** The file read strictly, or null when it is not a WireGuard configuration SCARY can use. */
    fun parse(raw: String): Parsed? {
        if (raw.length > MAX_CHARS || raw.any { it.isISOControl() && it != '\n' && it != '\r' && it != '\t' }) return null
        var section: String? = null
        val iface = LinkedHashMap<String, String>()
        val peers = ArrayList<LinkedHashMap<String, String>>()
        for (line in raw.lineSequence()) {
            val clean = line.substringBefore('#').trim()
            if (clean.isEmpty()) continue
            if (clean.length > MAX_LINE) return null
            if (clean.startsWith("[")) {
                section = when (clean.lowercase()) {
                    "[interface]" -> if (iface.isNotEmpty() || section == "interface") return null else "interface"
                    "[peer]" -> {
                        if (peers.size >= MAX_PEERS) return null
                        peers += LinkedHashMap<String, String>()
                        "peer"
                    }
                    else -> return null
                }
                continue
            }
            val name = clean.substringBefore('=').trim().lowercase()
            val value = clean.substringAfter('=', "").trim()
            if (!clean.contains('=') || value.isEmpty()) return null
            when (section) {
                "interface" -> interfaceKeys[name]?.let { known -> iface[known] = join(iface[known], value, known) }
                "peer" -> peerKeys[name]?.let { known -> peers.last()[known] = join(peers.last()[known], value, known) }
                else -> return null
            }
        }
        if (iface["PrivateKey"]?.let(key::matches) != true || peers.isEmpty()) return null
        val addresses = list(iface["Address"]).also { list -> if (list.isEmpty() || !list.all(address::matches)) return null }
        val dns = list(iface["DNS"]).also { list -> if (!list.all { address.matches(it) || host.matches(it) }) return null }
        iface["MTU"]?.let { mtu -> if (mtu.toIntOrNull()?.let { it in 576..65535 } != true) return null }
        val endpoints = ArrayList<String>()
        for (peer in peers) {
            if (peer["PublicKey"]?.let(key::matches) != true) return null
            peer["PresharedKey"]?.let { if (!key.matches(it)) return null }
            if (!list(peer["AllowedIPs"]).all(address::matches)) return null
            peer["PersistentKeepalive"]?.let { keep -> if (keep.toIntOrNull()?.let { it in 0..65535 } != true) return null }
            val endpoint = peer["Endpoint"] ?: return null
            val port = endpoint.substringAfterLast(':', "").toIntOrNull()?.takeIf { it in 1..65535 } ?: return null
            val name = endpoint.substringBeforeLast(':')
            if (!host.matches(name)) return null
            endpoints += "$name:$port"
        }
        val text = buildString {
            append("[Interface]\n")
            iface.forEach { (name, value) -> append(name).append(" = ").append(value).append('\n') }
            peers.forEach { peer ->
                append("\n[Peer]\n")
                peer.forEach { (name, value) -> append(name).append(" = ").append(value).append('\n') }
            }
        }
        return Parsed(text, Summary(endpoints, addresses, dns))
    }

    /** A list setting may be given on several lines; a single one (a key, the endpoint) may not be given twice. */
    private fun join(before: String?, value: String, name: String): String = when {
        before == null -> value
        name == "Address" || name == "DNS" || name == "AllowedIPs" -> "$before, $value"
        else -> "\u0000"
    }

    private fun list(value: String?): List<String> = value.orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() }
}
