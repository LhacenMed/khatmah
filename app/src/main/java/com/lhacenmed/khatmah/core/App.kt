package com.lhacenmed.khatmah.core

import android.app.Application
import android.os.Build
import androidx.annotation.RequiresApi
import coil.Coil
import coil.ImageLoader
import coil.decode.SvgDecoder
import com.google.android.material.color.DynamicColors
import com.lhacenmed.khatmah.feature.khatmah.data.KhatmahRepository
import com.lhacenmed.khatmah.feature.mushaf.data.MushafPrefs
import com.lhacenmed.khatmah.feature.prayer.data.PrayerSettings
import com.lhacenmed.khatmah.feature.prayer.notification.AdhanPrefs
import com.lhacenmed.khatmah.feature.prayer.notification.AdhanSound
import com.lhacenmed.khatmah.shared.reminders.ReminderNotifier
import com.lhacenmed.khatmah.shared.reminders.ReminderPrefs
import com.lhacenmed.khatmah.shared.reminders.ReminderScheduler
import com.lhacenmed.khatmah.shared.util.AdhanSoundFiles
import com.lhacenmed.khatmah.shared.util.AppPrefs
import com.lhacenmed.khatmah.shared.util.LocaleManager
import com.lhacenmed.khatmah.shared.util.ThemeManager
import com.lhacenmed.khatmah.widget.PrayerWidgetWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class App : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

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
        MushafPrefs.init(this)

        // ReminderPrefs must be initialised before AdhanPrefs (which reads from it).
        ReminderPrefs.init(this)
        AdhanPrefs.init(this)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ReminderNotifier.ensureChannels(this, AdhanSoundFiles.list(this))
            // Recreate custom adhan channels that may have been wiped on CH_VERSION bump.
            AdhanPrefs.get().forEach { cfg ->
                if (cfg.sound is AdhanSound.Custom)
                    ReminderNotifier.ensureCustomAdhanChannel(this, cfg.sound.uri, cfg.sound.displayName)
            }
            ReminderScheduler.scheduleAll(this)
        }
        PrayerWidgetWorker.Companion.enqueue(this)
        // Register SVG decoder so FlagCDN SVGs render via AsyncImage.
        // Coil's default disk + memory cache handles flag caching automatically.
        Coil.setImageLoader {
            ImageLoader.Builder(this)
                .components { add(SvgDecoder.Factory()) }
                .build()
        }

        // Pre-warm the Quran sura-name cache so TodayTab loads instantly.
        appScope.launch { KhatmahRepository(this@App).warmCache() }
    }
}