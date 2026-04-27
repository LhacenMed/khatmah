package com.lhacenmed.khatmah.util

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.core.content.edit

object LocaleManager {

    private const val PREFS = "locale"
    private const val KEY   = "tag"

    // Stored once in App.onCreate so setLocale can persist without a context param.
    private var appContext: Context? = null

    /**
     * Call once from App.onCreate before any locale is applied.
     *
     * Migrates existing users: if no tag has been persisted yet but AppCompatDelegate
     * already has a locale set (from a previous install), we persist it now so the
     * widget can read it from any process without AppCompatDelegate being initialized.
     */
    fun init(context: Context) {
        appContext = context.applicationContext
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!prefs.contains(KEY)) {
            // One-time migration: persist whatever AppCompatDelegate currently reports.
            val current = AppCompatDelegate.getApplicationLocales()
            val tag = if (current.isEmpty) null else current[0]?.toLanguageTag()
            prefs.edit { putString(KEY, tag) }
        }
    }

    // null = follow system
    fun setLocale(tag: String?) {
        appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            ?.edit()?.putString(KEY, tag)?.apply()
        val list = if (tag.isNullOrEmpty()) LocaleListCompat.getEmptyLocaleList()
        else LocaleListCompat.forLanguageTags(tag)
        AppCompatDelegate.setApplicationLocales(list)
    }

    fun getCurrentTag(): String? {
        val locales = AppCompatDelegate.getApplicationLocales()
        return if (locales.isEmpty) null else locales[0]?.toLanguageTag()
    }

    /**
     * Returns the persisted language tag, safe to call from any process
     * (widget worker, etc.) without AppCompatDelegate being initialized.
     */
    fun savedTag(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, null)
            .takeIf { !it.isNullOrEmpty() }
}