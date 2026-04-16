package com.lhacenmed.khatmah

import android.app.Application
import android.os.Build
import androidx.annotation.RequiresApi
import coil.Coil
import coil.ImageLoader
import coil.decode.SvgDecoder
import com.google.android.material.color.DynamicColors
import com.lhacenmed.khatmah.data.prayer.PrayerSettings
import com.lhacenmed.khatmah.util.ThemeManager
import com.lhacenmed.khatmah.widget.PrayerWidgetWorker

class App : Application() {

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate() {
        super.onCreate()
        ThemeManager.apply(this)
        DynamicColors.applyToActivitiesIfAvailable(this)
        // Load persisted prayer calculation settings before any UI is created.
        PrayerSettings.init(this)
        PrayerWidgetWorker.enqueue(this)
        // Locale is restored automatically by AppCompatDelegate on process start
        // via the persisted per-app locale; no manual call needed here.
        // Register SVG decoder so FlagCDN SVGs render via AsyncImage.
        // Coil's default disk + memory cache handles flag caching automatically.
        Coil.setImageLoader {
            ImageLoader.Builder(this)
                .components { add(SvgDecoder.Factory()) }
                .build()
        }
    }
}