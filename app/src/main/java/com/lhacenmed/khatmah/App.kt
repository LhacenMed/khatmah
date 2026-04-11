package com.lhacenmed.khatmah

import android.app.Application
import com.google.android.material.color.DynamicColors
import com.lhacenmed.khatmah.util.ThemeManager

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        ThemeManager.apply(this)
        DynamicColors.applyToActivitiesIfAvailable(this)
        // Locale is restored automatically by AppCompatDelegate on process start
        // via the persisted per-app locale; no manual call needed here.
    }
}