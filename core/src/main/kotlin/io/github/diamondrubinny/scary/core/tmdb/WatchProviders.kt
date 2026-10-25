// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary.core.tmdb

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull

/** How a store or service offers a movie in one country. */
enum class OfferKind { RENT, BUY, INCLUDED }

/** A store or streaming service, with every way it offers the movie in that country. */
data class WatchProvider(
    val id: Long,
    val name: String,
    /** A TMDB image URL, or null when the response had no usable logo. */
    val logoUrl: String?,
    val priority: Int,
    val kinds: Set<OfferKind>,
)

/** Where a movie can be rented, bought or watched with a subscription in one country. Data by JustWatch, via TMDB. */
data class RegionOffers(
    val country: String,
    val providers: List<WatchProvider>,
    /** TMDB's page for the movie in that country, which links to each store. Only themoviedb.org links are kept. */
    val link: String?,
) {
    val rentOrBuy: List<WatchProvider> get() = providers.filter { OfferKind.RENT in it.kinds || OfferKind.BUY in it.kinds }
    val included: List<WatchProvider> get() = providers.filter { OfferKind.INCLUDED in it.kinds }
    val isEmpty: Boolean get() = providers.isEmpty()
}

/**
 * TMDB's watch providers. One request returns every country, so the viewer's country is picked on the device and
 * never sent. The response is untrusted: names are plain text with a length limit, ids are numeric, logos go through
 * [Tmdb.imageUrl] and links must point at themoviedb.org.
 */
object WatchProviders {
    private val json = Json { ignoreUnknownKeys = true }
    private val countryCode = Regex("^[A-Z]{2}$")
    private val tmdbLink = Regex("^https://www\\.themoviedb\\.org/[A-Za-z0-9_\\-./?=&%]{1,300}$")
    private val controlChars = Regex("[\\p{Cntrl}\\u200B-\\u200F\\u202A-\\u202E\\u2066-\\u2069]")

    /** TMDB's lists for each kind of offer. "free" and "ads" count as included: there is nothing to pay for. */
    private val LISTS = mapOf(
        "rent" to OfferKind.RENT,
        "buy" to OfferKind.BUY,
        "flatrate" to OfferKind.INCLUDED,
        "free" to OfferKind.INCLUDED,
        "ads" to OfferKind.INCLUDED,
    )

    fun url(tmdbId: Long): String {
        require(tmdbId > 0) { "invalid TMDB id" }
        return "${Tmdb.API_BASE}/movie/$tmdbId/watch/providers"
    }

    /** Every country in the response, keyed by its ISO 3166-1 code. Null when the text is not a watch providers reply. */
    fun parse(text: String): Map<String, RegionOffers>? {
        val root = try {
            json.parseToJsonElement(text).jsonObject
        } catch (e: IllegalArgumentException) {
            return null
        }
        val results = root["results"] as? JsonObject ?: return null
        return results.entries
            .asSequence()
            .filter { (code, _) -> countryCode.matches(code) }
            .take(MAX_COUNTRIES)
            .mapNotNull { (code, value) -> (value as? JsonObject)?.let { code to region(code, it) } }
            .toMap()
    }

    private fun region(country: String, o: JsonObject): RegionOffers {
        val byId = LinkedHashMap<Long, WatchProvider>()
        for ((list, kind) in LISTS) {
            val entries = (o[list] as? JsonArray).orEmpty().take(MAX_PER_LIST)
            for (entry in entries) {
                val provider = (entry as? JsonObject)?.let { provider(it, kind) } ?: continue
                val known = byId[provider.id]
                byId[provider.id] = known?.copy(kinds = known.kinds + kind, priority = minOf(known.priority, provider.priority)) ?: provider
            }
        }
        val link = (o["link"] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull?.trim()?.takeIf(tmdbLink::matches)
        return RegionOffers(
            country = country,
            providers = byId.values.sortedWith(compareBy<WatchProvider> { it.priority }.thenBy { it.name.lowercase() }),
            link = link,
        )
    }

    private fun provider(o: JsonObject, kind: OfferKind): WatchProvider? {
        val id = (o["provider_id"] as? JsonPrimitive)?.longOrNull?.takeIf { it > 0 } ?: return null
        val name = (o["provider_name"] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull
            ?.replace(controlChars, "")?.trim()?.take(MAX_NAME)?.takeIf { it.isNotBlank() } ?: return null
        val logo = (o["logo_path"] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull
        val priority = (o["display_priority"] as? JsonPrimitive)?.intOrNull?.coerceIn(0, 10_000) ?: 10_000
        return WatchProvider(id, name, Tmdb.imageUrl(logo, "w92"), priority, setOf(kind))
    }

    private const val MAX_COUNTRIES = 300
    private const val MAX_PER_LIST = 40
    private const val MAX_NAME = 60
}
