package com.lhacenmed.khatmah

import android.app.Application

class KhatmahApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        ThemeManager.apply(this)
    }
}
