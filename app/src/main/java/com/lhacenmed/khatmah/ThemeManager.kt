package com.lhacenmed.khatmah

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.edit

object ThemeManager {

    private const val PREFS_NAME = "theme_prefs"
    private const val KEY_MODE = "night_mode"

    fun apply(context: Context) {
        AppCompatDelegate.setDefaultNightMode(getMode(context))
    }

    fun setMode(context: Context, mode: Int) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit {
                putInt(KEY_MODE, mode)
            }
        AppCompatDelegate.setDefaultNightMode(mode)
    }

    fun getMode(context: Context): Int =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getInt(KEY_MODE, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
}
