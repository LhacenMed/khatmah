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
import com.lhacenmed.khatmah.feature.prayer.data.CustomPrayerTimes
import com.lhacenmed.khatmah.feature.prayer.data.CustomTimesPrefs
import com.lhacenmed.khatmah.feature.prayer.data.PrayerSettings
import com.lhacenmed.khatmah.feature.prayer.data.PrayerTimetable
import com.lhacenmed.khatmah.feature.prayer.data.placeKey
import com.lhacenmed.khatmah.feature.prayer.data.changelog.PrayerTimeChangeLog
import com.lhacenmed.khatmah.feature.prayer.data.changelog.PrayerTimeSharingPrefs
import com.lhacenmed.khatmah.feature.prayer.notification.AdhanChannels
import com.lhacenmed.khatmah.feature.prayer.notification.AdhanPrefs
import com.lhacenmed.khatmah.feature.qadaa.data.QadaaPrefs
import com.lhacenmed.khatmah.shared.fcm.FcmTokenManager
import com.lhacenmed.khatmah.shared.reminders.ReminderNotifier
import com.lhacenmed.khatmah.shared.reminders.ReminderPrefs
import com.lhacenmed.khatmah.shared.reminders.ReminderScheduler
import com.lhacenmed.khatmah.shared.util.AppPrefs
import com.lhacenmed.khatmah.shared.util.LocaleManager
import com.lhacenmed.khatmah.shared.util.NetworkMonitor
import com.lhacenmed.khatmah.shared.util.OnboardingPrefs
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
        // ...and the times the user has fixed, which the alarms scheduled below are set from.
        CustomTimesPrefs.init(this)
        PrayerTimeSharingPrefs.init(this)
        // The place the times are worked out for; announced so a move rebuilds them.
        OnboardingPrefs.init(this)
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
        keepSurfacesOnPrayerTimes()
        forgetPinnedTimesOnMove()
        recordPrayerTimeChanges()
        sendPrayerTimeChanges()
        forgetChangesWhileSharingIsOff()
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
     * Drops pinned times once the user is somewhere they were not set for.
     *
     * A time fixed by hand describes one place. Carried to another it is simply wrong, and wrong
     * in the way that matters most — the app would go on announcing it, and calling people to
     * prayer at an hour nowhere near the right one. So the pins are let go and the calculation,
     * which travels perfectly well, takes the day back.
     *
     * The place is compared rather than the coordinates, so detecting the same spot again and
     * landing a few streets over costs the user nothing. Not dropping the first emission is what
     * makes this self-healing: pins left over from elsewhere by a launch that never finished are
     * cleared on the next one.
     */
    private fun forgetPinnedTimesOnMove() {
        appScope.launch {
            OnboardingPrefs.locationFlow.collect { location ->
                val pinned = CustomTimesPrefs.get()
                if (pinned.hasNone || pinned.placeKey == location?.placeKey()) return@collect
                CustomTimesPrefs.save(this@App, CustomPrayerTimes())
            }
        }
    }

    /**
     * Empties the outbox the moment the user stops sharing, and keeps it empty.
     *
     * Recording is already off by then, so this is about what was written down before they
     * decided. Watched rather than done once at the switch, so the promise holds even if the app
     * dies between the two — the next launch finds anything left and lets it go.
     */
    @RequiresApi(Build.VERSION_CODES.O)
    private fun forgetChangesWhileSharingIsOff() {
        appScope.launch {
            PrayerTimeSharingPrefs.isOn.collect { isOn ->
                if (!isOn) PrayerTimeChangeLog.forgetPending(this@App)
            }
        }
    }

    /**
     * Writes down every prayer time the user sets by hand, and offers it to the server at once.
     *
     * Watched rather than called, so no screen has to remember to report itself and a change made
     * anywhere is recorded the same way. The pair is what carries the meaning: a change is what a
     * time was and what it became, and only consecutive states say that.
     *
     * Sending here covers the case a connectivity change never will — the device was already
     * online, so nothing transitions, and without this the change would wait for the next launch.
     */
    @RequiresApi(Build.VERSION_CODES.O)
    private fun recordPrayerTimeChanges() {
        appScope.launch {
            var before = CustomTimesPrefs.get()
            CustomTimesPrefs.flow.drop(1).collect { after ->
                PrayerTimeChangeLog.record(this@App, before, after)
                before = after
                // Offline, or the server is unreachable: the change keeps until it can be sent.
                runCatching { PrayerTimeChangeLog.flush(this@App) }
            }
        }
    }

    /**
     * Empties the outbox at launch and whenever the device comes back online.
     *
     * [NetworkMonitor.online] opens with the current state, so this is the launch send as well as
     * the reconnection one. Anything that does not land stays where it is and is offered again the
     * next time either happens.
     */
    @RequiresApi(Build.VERSION_CODES.O)
    private fun sendPrayerTimeChanges() {
        appScope.launch {
            NetworkMonitor.online(this@App).collect { online ->
                if (online) runCatching { PrayerTimeChangeLog.flush(this@App) }
            }
        }
    }

    /**
     * Rebuilds everything derived from the prayer times whenever the times change.
     *
     * Three surfaces are built from them and none can notice on its own. The alarms are set once
     * and then simply wait, at a moment that is no longer the right one. The widget is drawn by
     * the launcher, out of reach of whichever screen made the change. Both are also outlived by
     * every Activity, which is why this watches from the application rather than from one of them:
     * prayer settings open in their own Activity, so the screen that changes a time is never the
     * screen that has to react to it, and an observer tied to a visible Activity would sleep
     * through exactly the changes it exists to catch.
     *
     * The tab that lists the times is the exception — it is on screen, so it observes for itself.
     */
    @RequiresApi(Build.VERSION_CODES.O)
    private fun keepSurfacesOnPrayerTimes() {
        appScope.launch {
            PrayerTimetable.changes.collect {
                ReminderScheduler.scheduleAll(this@App)
                PrayerWidget().updateAll(this@App)
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