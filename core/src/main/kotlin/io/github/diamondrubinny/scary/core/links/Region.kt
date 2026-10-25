// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary.core.links

/** The country rent and buy offers are shown for, and whether the viewer chose it or the device told us. */
data class ResolvedCountry(val code: String, val automatic: Boolean)

/**
 * Picks the viewer's country without asking for location: the one chosen in SCARY's settings, otherwise the mobile
 * network's (phones and tablets), the SIM's, and last the device's region setting. Nothing here leaves the device.
 */
object Region {
    private val code = Regex("^[A-Z]{2}$")

    /** Codes that are valid ISO letters but no real country: user-assigned and "unknown". */
    private val NOT_COUNTRIES = setOf("AA", "QM", "QZ", "XX", "XZ", "ZZ", "EU", "UN")

    /** "il", " IL " and "IL" all become "IL"; anything else (empty, "419", "ZZ") is no country. */
    fun normalize(raw: String?): String? =
        raw?.trim()?.uppercase()?.takeIf { code.matches(it) && it !in NOT_COUNTRIES }

    fun resolve(chosen: String?, network: String?, sim: String?, deviceRegion: String?): ResolvedCountry? {
        normalize(chosen)?.let { return ResolvedCountry(it, automatic = false) }
        return listOf(network, sim, deviceRegion).firstNotNullOfOrNull(::normalize)?.let { ResolvedCountry(it, automatic = true) }
    }
}
