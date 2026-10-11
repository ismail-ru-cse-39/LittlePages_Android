package com.littlepages.app

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import java.util.Locale

object AppLanguage {
    private const val PREFERENCES = "app_preferences"
    private const val LANGUAGE_KEY = "language_tag"

    fun set(activity: Activity, languageTag: String) {
        val tag = if (languageTag.startsWith("bn", ignoreCase = true)) "bn" else "en"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            activity.getSystemService(android.app.LocaleManager::class.java)
                .applicationLocales = android.os.LocaleList.forLanguageTags(tag)
        } else {
            activity.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                .edit().putString(LANGUAGE_KEY, tag).apply()
            activity.recreate()
        }
    }

    fun wrap(context: Context): Context {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return context
        val tag = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .getString(LANGUAGE_KEY, "en") ?: "en"
        val configuration = Configuration(context.resources.configuration)
        configuration.setLocale(Locale.forLanguageTag(tag))
        return context.createConfigurationContext(configuration)
    }
}
