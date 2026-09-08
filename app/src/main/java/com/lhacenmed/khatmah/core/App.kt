package com.lhacenmed.khatmah.core

import android.app.Application
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import coil.Coil
import coil.ImageLoader
import coil.decode.SvgDecoder
import com.lhacenmed.khatmah.BuildConfig
import com.lhacenmed.khatmah.feature.debug.buildDynamicColorJson
import com.lhacenmed.khatmah.feature.khatmah.data.KhatmahRepository
import com.lhacenmed.khatmah.feature.quran.data.MushafInitializer
import com.lhacenmed.khatmah.feature.update.UpdateManager
import com.lhacenmed.khatmah.feature.update.UpdatePrefs
import com.lhacenmed.khatmah.feature.quran.data.MushafPrefs
import com.lhacenmed.khatmah.feature.prayer.data.PrayerSettings
import com.lhacenmed.khatmah.feature.prayer.notification.AdhanChannels
import com.lhacenmed.khatmah.feature.prayer.notification.AdhanPrefs
import com.lhacenmed.khatmah.feature.qadaa.data.QadaaPrefs
import com.lhacenmed.khatmah.shared.fcm.FcmTokenManager
import com.lhacenmed.khatmah.shared.reminders.ReminderNotifier
import com.lhacenmed.khatmah.shared.reminders.ReminderPrefs
import com.lhacenmed.khatmah.shared.reminders.ReminderScheduler
import com.lhacenmed.khatmah.shared.util.AppPrefs
import com.lhacenmed.khatmah.shared.util.LocaleManager
import com.lhacenmed.khatmah.shared.util.ThemeManager
import androidx.glance.appwidget.updateAll
import com.lhacenmed.khatmah.widget.PrayerWidget
import com.lhacenmed.khatmah.widget.PrayerWidgetWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

class App : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate() {
        super.onCreate()
        // init() loads persisted values from SharedPrefs AND calls
        // AppCompatDelegate.setDefaultNightMode — replaces the old apply() call.
        ThemeManager.init(this)
        // Keeps every Activity on the current palette; each one applies it in onCreate.
        ThemeManager.attach(this)
        // Must be called before setLocale so the widget's savedTag() always has a value.
        LocaleManager.init(this)
        // Load persisted prayer calculation settings before any UI is created.
        PrayerSettings.init(this)
        AppPrefs.init(this)
        QadaaPrefs.init(this)
        MushafPrefs.init(this)

        // ReminderPrefs must be initialized before AdhanPrefs (which reads from it).
        ReminderPrefs.init(this)
        AdhanPrefs.init(this)

        ReminderNotifier.ensureChannels(this)
        // Create channels only for adhan sounds actually in use; prune the rest.
        AdhanChannels.sync(this)
        ReminderScheduler.scheduleAll(this)
        PrayerWidgetWorker.enqueue(this)
        keepWidgetOnAppAppearance()
        // Register SVG decoder so FlagCDN SVGs render via AsyncImage.
        // Coil's default disk + memory cache handles flag caching automatically.
        Coil.setImageLoader {
            ImageLoader.Builder(this)
                .components { add(SvgDecoder.Factory()) }
                .build()
        }
        // Start FCM token registration (non-blocking)
        FcmTokenManager.init(this)

        // Re-hydrate the update flow from the last session: resume a not-yet-installed download,
        // re-surface a persisted prompt offline, and drop anything the running build has caught up to.
        UpdatePrefs.init(this)
        appScope.launch { UpdateManager.restore(this@App) }

        // Pre-warm the Quran sura-name cache so the Quran tab loads instantly.
        appScope.launch { KhatmahRepository(this@App).warmCache() }
        // Seed MushafDb from bundled riwaya JSON files (hafs.json + warsh.json in quran.7z).
        MushafInitializer.init(this, appScope)

        // Dump Material You dynamic colors to external storage for dev use.
        if (BuildConfig.DEBUG && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            appScope.launch {
                val json = buildDynamicColorJson(this@App)
                getExternalFilesDir(null)
                    ?.resolve("dynamic_colors.json")
                    ?.writeText(json)
                Log.d("DynamicColors", json)
            }
        }
    }

    /**
     * Redraws the prayer widget whenever the app's appearance changes.
     *
     * An Activity is recreated on a palette, night-mode or language change and picks the new one
     * up on the way back in. The widget cannot: it lives in the launcher, and its colours and its
     * language are resolved at the moment it is built — so a change only reaches it when
     * something asks for a redraw. Nothing else does; the periodic worker would take up to
     * fifteen minutes to notice.
     */
    @RequiresApi(Build.VERSION_CODES.O)
    private fun keepWidgetOnAppAppearance() {
        appScope.launch {
            combine(ThemeManager.version, ThemeManager.mode, LocaleManager.tag) { _, _, _ -> }
                .drop(1) // the combined current state, which the widget already shows
                .collect { PrayerWidget().updateAll(this@App) }
        }
    }
}