package com.ff9.poweliftjudge

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import java.util.*

object LocaleHelper {

    private const val SELECTED_LANGUAGE = "selected_language"

    fun setLocale(context: Context, language: String): Context {
        persist(context, language)

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            updateResources(context, language)
        } else {
            updateResourcesLegacy(context, language)
        }
    }

    fun getLanguage(context: Context): String {
        val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        return prefs.getString(SELECTED_LANGUAGE, "en") ?: "en"
    }

    private fun persist(context: Context, language: String) {
        val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        prefs.edit().putString(SELECTED_LANGUAGE, language).apply()
    }

    private fun updateResources(context: Context, language: String): Context {
        val locale = Locale(language)
        Locale.setDefault(locale)

        val configuration = context.resources.configuration
        configuration.setLocale(locale)
        configuration.setLayoutDirection(locale)

        return context.createConfigurationContext(configuration)
    }

    @Suppress("DEPRECATION")
    private fun updateResourcesLegacy(context: Context, language: String): Context {
        val locale = Locale(language)
        Locale.setDefault(locale)

        val resources = context.resources
        val configuration = resources.configuration
        configuration.locale = locale
        configuration.setLayoutDirection(locale)

        resources.updateConfiguration(configuration, resources.displayMetrics)

        return context
    }

    fun getLanguageName(context: Context, languageCode: String): String {
        return when(languageCode) {
            "en" -> context.getString(R.string.lang_english)
            "it" -> context.getString(R.string.lang_italian)
            "es" -> context.getString(R.string.lang_spanish)
            "ru" -> context.getString(R.string.lang_russian)
            "pt" -> context.getString(R.string.lang_portuguese)
            "de" -> context.getString(R.string.lang_german)
            "fr" -> context.getString(R.string.lang_french)
            "ja" -> context.getString(R.string.lang_japanese)
            else -> context.getString(R.string.lang_english)
        }
    }
}
