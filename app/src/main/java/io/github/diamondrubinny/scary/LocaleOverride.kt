package io.github.diamondrubinny.scary

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/**
 * The launcher is Hebrew-first even when the TV's system language is English.
 * A language setting replaces this constant in a later phase.
 */
object LocaleOverride {
    private val hebrew: Locale = Locale.forLanguageTag("he")

    fun wrap(base: Context): Context {
        val config = Configuration(base.resources.configuration)
        config.setLocale(hebrew)
        config.setLayoutDirection(hebrew)
        return base.createConfigurationContext(config)
    }
}
