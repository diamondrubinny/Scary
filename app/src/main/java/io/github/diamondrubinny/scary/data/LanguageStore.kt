package io.github.diamondrubinny.scary.data

import android.content.Context
import androidx.core.content.edit
import io.github.diamondrubinny.scary.core.model.Language

/**
 * The UI language must be known synchronously in attachBaseContext, before any coroutine can run,
 * so it lives in SharedPreferences rather than DataStore.
 */
object LanguageStore {
    private const val FILE = "locale"
    private const val KEY = "language"

    fun read(context: Context): Language =
        when (context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(KEY, null)) {
            "en" -> Language.EN
            else -> Language.HE
        }

    fun write(context: Context, language: Language) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit {
            putString(KEY, if (language == Language.EN) "en" else "he")
        }
    }
}
