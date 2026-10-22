package io.github.diamondrubinny.scary

import android.content.Context
import android.content.res.Configuration
import io.github.diamondrubinny.scary.core.model.Language
import io.github.diamondrubinny.scary.data.LanguageStore
import java.util.Locale

/** Applies the launcher's own language (Hebrew by default) regardless of the TV's system language. */
object LocaleOverride {
    fun wrap(base: Context): Context {
        val locale = Locale.forLanguageTag(localeTag(LanguageStore.read(base)))
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        config.setLayoutDirection(locale)
        return base.createConfigurationContext(config)
    }

    /**
     * Arabic uses Western digits ("ar-u-nu-latn"): years, ratings and the clock are always Western, and
     * formatted counts would otherwise switch to Arabic-Indic digits next to them.
     */
    fun localeTag(language: Language): String = if (language == Language.AR) "ar-u-nu-latn" else language.code
}
