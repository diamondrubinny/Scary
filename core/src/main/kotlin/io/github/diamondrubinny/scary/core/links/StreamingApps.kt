// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary.core.links

/**
 * A streaming or TV app SCARY can offer on a movie page ("Search in Netflix"). Recognised by its package name when
 * known, otherwise by words in the app's name, so operator apps whose package names are not public (Israeli TV
 * services, for example) are still found. SCARY never claims a movie is available there: it only searches.
 */
data class StreamingApp(
    val key: String,
    /** Package names known for the TV version. **Not all verified on a device**; the name match covers the rest. */
    val packages: Set<String> = emptySet(),
    /** Whole words or phrases in the app's name, any letter case. */
    val nameHints: List<String> = emptyList(),
    /** For short, common words ("HOT"): the whole name must be one of these, any letter case. */
    val exactNames: Set<String> = emptySet(),
)

object StreamingApps {
    val KNOWN: List<StreamingApp> = listOf(
        StreamingApp("netflix", setOf("com.netflix.ninja", "com.netflix.mediaclient"), listOf("netflix")),
        StreamingApp("disney", setOf("com.disney.disneyplus"), listOf("disney+", "disney plus")),
        StreamingApp("prime", setOf("com.amazon.amazonvideo.livingroom", "com.amazon.avod.thirdpartyclient"), listOf("prime video")),
        StreamingApp("apple_tv", setOf("com.apple.atve.androidtv.appletv"), listOf("apple tv")),
        StreamingApp("max", setOf("com.wbd.stream", "com.hbo.hbonow"), listOf("hbo max", "hbo")),
        StreamingApp("paramount", setOf("com.cbs.ott"), listOf("paramount+", "paramount plus")),
        StreamingApp("youtube", setOf(KnownPackages.YOUTUBE_TV), listOf("youtube")),
        StreamingApp("google_tv", setOf("com.google.android.videos"), listOf("google tv", "play movies")),
        StreamingApp("plex", setOf("com.plexapp.android"), listOf("plex")),
        StreamingApp("kodi", setOf("org.xbmc.kodi"), listOf("kodi")),
        StreamingApp("tubi", setOf("com.tubitv"), listOf("tubi")),
        StreamingApp("pluto", nameHints = listOf("pluto tv")),
        StreamingApp("mubi", nameHints = listOf("mubi")),
        // Israeli TV services: package names are not published, so only their names identify them.
        StreamingApp("yes", nameHints = listOf("yes+", "yes plus", "STINGTV", "sting tv")),
        StreamingApp("hot", nameHints = listOf("הוט", "next tv by hot"), exactNames = setOf("hot", "hot tv", "hot next", "next tv")),
        StreamingApp("cellcom", nameHints = listOf("cellcom", "סלקום")),
        StreamingApp("partner", nameHints = listOf("partner tv", "פרטנר")),
        StreamingApp("freetv", nameHints = listOf("freetv", "free tv")),
    )

    /** Apps that are never offered here: SCARY itself, Stremio (it has its own button) and the store. */
    private val EXCLUDED = setOf(KnownPackages.STREMIO, KnownPackages.PLAY_STORE, "io.github.diamondrubinny.scary")

    /** Children's versions ("YouTube Kids") are not where anyone looks for horror. */
    private val excludedWord = wholeWord("kids")

    /** Compiled once; [match] runs from background threads. */
    private val hintPatterns: Map<StreamingApp, List<Regex>> = KNOWN.associateWith { app -> app.nameHints.map(::wholeWord) }

    /** Which known streaming app this installed app is, if any. */
    fun match(packageName: String, label: String): StreamingApp? {
        if (packageName in EXCLUDED || excludedWord.containsMatchIn(label)) return null
        KNOWN.firstOrNull { packageName in it.packages }?.let { return it }
        val name = label.trim()
        return KNOWN.firstOrNull { app ->
            app.exactNames.any { it.equals(name, ignoreCase = true) } || hintPatterns.getValue(app).any { it.containsMatchIn(name) }
        }
    }

    /** Search text for a movie: the original title works best in the apps' own catalogs. */
    fun query(title: String): String = title.trim().take(MAX_QUERY)

    /** YouTube search text: the year tells the movie apart from reviews and trailers of remakes. */
    fun youTubeQuery(title: String, year: Int): String = "${query(title)} $year"

    private fun wholeWord(hint: String): Regex =
        Regex("(?<![\\p{L}\\p{N}])" + Regex.escape(hint) + "(?![\\p{L}\\p{N}])", RegexOption.IGNORE_CASE)

    private const val MAX_QUERY = 80
}
