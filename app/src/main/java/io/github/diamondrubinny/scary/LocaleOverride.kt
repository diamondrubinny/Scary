package io.github.diamondrubinny.scary

import android.content.Context
import android.content.res.Configuration
import io.github.diamondrubinny.scary.core.model.Language
import io.github.diamondrubinny.scary.data.LanguageStore
import java.util.Locale

/** Applies the launcher's own language (Hebrew by default) regardless of the TV's system language. */
object LocaleOverride {
    fun wrap(base: Context): Context {
        val locale = when (LanguageStore.read(base)) {
            Language.HE -> Locale.forLanguageTag("he")
            Language.EN -> Locale.forLanguageTag("en")
        }
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        config.setLayoutDirection(locale)
        return base.createConfigurationContext(config)
    }
}
