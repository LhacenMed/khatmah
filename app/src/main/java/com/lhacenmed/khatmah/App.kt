package com.lhacenmed.khatmah

import android.app.Application
import android.os.Build
import androidx.annotation.RequiresApi
import coil.Coil
import coil.ImageLoader
import coil.decode.SvgDecoder
import com.google.android.material.color.DynamicColors
import com.lhacenmed.khatmah.data.prayer.PrayerSettings
import com.lhacenmed.khatmah.data.prefs.AppPrefs
import com.lhacenmed.khatmah.util.LocaleManager
import com.lhacenmed.khatmah.util.ThemeManager
import com.lhacenmed.khatmah.widget.PrayerWidgetWorker
import com.lhacenmed.khatmah.data.prayer.AdhanPrefs
import com.lhacenmed.khatmah.data.prayer.AdhanSound
import com.lhacenmed.khatmah.notification.AdhanScheduler
import com.lhacenmed.khatmah.util.AdhanSoundFiles
import com.lhacenmed.khatmah.util.NotificationHelper

class App : Application() {

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate() {
        super.onCreate()
        // init() loads persisted values from SharedPrefs AND calls
        // AppCompatDelegate.setDefaultNightMode — replaces the old apply() call.
        ThemeManager.init(this)
        DynamicColors.applyToActivitiesIfAvailable(this)
        // Must be called before setLocale so the widget's savedTag() always has a value.
        LocaleManager.init(this)
        // Load persisted prayer calculation settings before any UI is created.
        PrayerSettings.init(this)
        AppPrefs.init(this)
        // Init adhan notification prefs and ensure notification channels exist.
        AdhanPrefs.init(this)
        // Init adhan notification prefs, channels, and alarms.
        AdhanPrefs.init(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationHelper.ensureChannels(this, AdhanSoundFiles.list(this))
            // Recreate custom channels that may have been wiped on CH_VERSION bump.
            AdhanPrefs.get().forEach { cfg ->
                if (cfg.sound is AdhanSound.Custom)
                    NotificationHelper.ensureCustomChannel(this, cfg.sound.uri, cfg.sound.displayName)
            }
            AdhanScheduler.scheduleAll(this)
        }
        PrayerWidgetWorker.enqueue(this)
        // Register SVG decoder so FlagCDN SVGs render via AsyncImage.
        // Coil's default disk + memory cache handles flag caching automatically.
        Coil.setImageLoader {
            ImageLoader.Builder(this)
                .components { add(SvgDecoder.Factory()) }
                .build()
        }
    }
}