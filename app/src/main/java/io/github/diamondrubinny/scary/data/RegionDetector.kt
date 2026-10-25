// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary.data

import android.content.Context
import android.content.res.Resources
import android.telephony.TelephonyManager
import io.github.diamondrubinny.scary.core.links.Region
import io.github.diamondrubinny.scary.core.links.ResolvedCountry
import io.github.diamondrubinny.scary.core.model.Language
import java.text.Collator
import java.util.Locale

/**
 * The viewer's country for rent and buy offers, without a location permission and without the network: the mobile
 * network's and the SIM's country (phones and tablets; no permission needed) and the device's region setting.
 */
class RegionDetector(context: Context) {
    private val appContext = context.applicationContext

    fun resolve(chosen: String?): ResolvedCountry? {
        val telephony = runCatching { appContext.getSystemService(TelephonyManager::class.java) }.getOrNull()
        val network = runCatching { telephony?.networkCountryIso }.getOrNull()
        val sim = runCatching { telephony?.simCountryIso }.getOrNull()
        // The system's region, not SCARY's: SCARY overrides its own language, which can drop the region.
        val device = Resources.getSystem().configuration.locales[0]?.country
        return Region.resolve(chosen, network, sim, device)
    }

    /** Every country the viewer can pick, sorted by its name in the UI language. */
    fun countries(language: Language): List<String> {
        val collator = Collator.getInstance(uiLocale(language))
        return Locale.getISOCountries()
            .mapNotNull(Region::normalize)
            .map { it to displayName(it, language) }
            .sortedWith { a, b -> collator.compare(a.second, b.second) }
            .map { it.first }
    }

    fun displayName(code: String, language: Language): String =
        runCatching { Locale.Builder().setRegion(code).build().getDisplayCountry(uiLocale(language)) }
            .getOrNull()?.takeIf { it.isNotBlank() } ?: code

    private fun uiLocale(language: Language): Locale = Locale.forLanguageTag(language.code)
}
