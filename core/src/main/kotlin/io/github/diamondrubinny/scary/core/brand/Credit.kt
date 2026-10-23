package io.github.diamondrubinny.scary.core.brand

/**
 * The attribution SCARY shows wherever it can: home screen, settings, documents, screensaver and the movie launch
 * screen. Kept in code rather than in translatable resources, so it reads the same in every language.
 *
 * The Apache License 2.0 (section 4(d)) requires derivative works to keep the NOTICE file and show this attribution.
 * CI fails when it is missing from the code, the NOTICE file or the screen (.github/scripts/check-credit.sh, CreditTest
 * and the UI tests).
 */
object Credit {
    const val NAME = "SCARY"
    const val URL = "https://github.com/diamondrubinny/Scary"
    const val LINE = "Powered by SCARY · Open source · github.com/diamondrubinny/Scary"
    const val LICENSE = "Apache-2.0"

    /** Short form for tight spots (screensaver corner, launch screen). */
    const val SHORT = "Powered by SCARY · Open source"
}
