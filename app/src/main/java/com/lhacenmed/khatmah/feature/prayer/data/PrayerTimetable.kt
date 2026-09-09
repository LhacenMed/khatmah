package com.lhacenmed.khatmah.feature.prayer.data

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.lhacenmed.khatmah.shared.util.OnboardingPrefs
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import java.time.LocalDate

private const val TAG = "PrayerTimetable"

/**
 * The app's single answer to what time each prayer is.
 *
 * Every surface that shows or schedules a prayer asks here — the Prayers tab, the widget, the
 * adhan alarms, the settings preview. Each used to call [PrayerEngine] itself, which was fine
 * while a time was only ever calculated: the same inputs gave them the same answer. It stops being
 * fine the moment a time can also be the user's own, because then agreeing means four places
 * having been taught the same rule.
 *
 * The rule is one line, and it lives here: calculate, then let [CustomPrayerTimes] have the last
 * word.
 */
@RequiresApi(Build.VERSION_CODES.O)
object PrayerTimetable {

    /**
     * Changes whenever something saved could change a time — a calculation setting, or a pinned
     * time. Both counters only ever climb, so their sum does too, and a cache holding an older
     * answer can tell by comparing alone.
     */
    val version: Int get() = PrayerSettings.version + CustomTimesPrefs.version

    /**
     * Emits whenever [forDate] could start giving a different answer — a calculation setting, a
     * time the user fixed, or the place they are worked out for.
     *
     * Three inputs decide a prayer time and every one of them is editable while the app is
     * running, so everything built from the times has to be told when to build itself again: the
     * alarms that announce them, the widget that shows them, the tab that lists them. They are
     * gathered here rather than each observing the three, because "the times changed" is one fact
     * and it belongs to the thing that owns the times.
     *
     * The state at the moment of subscribing is not emitted — a collector already reflects it.
     */
    val changes: Flow<Unit> =
        combine(
            PrayerSettings.flow,
            CustomTimesPrefs.flow,
            OnboardingPrefs.locationFlow,
        ) { _, _, _ -> }.drop(1)

    /**
     * Fajr · Sunrise · Dhuhr · Asr · Maghrib · Isha for [date], in that order.
     *
     * Empty when there is nowhere to compute for, or when the sun does not reach the angles the
     * method asks for and no high-latitude fallback is set. A pinned time cannot fill that in:
     * without a calculated day there is no list to pin onto.
     *
     * [settings] defaults to the saved ones. The settings preview passes the values being edited,
     * which are not saved yet — it is showing what saving them would do.
     */
    fun forDate(
        context:  Context,
        date:     LocalDate,
        settings: PrayerCalcSettings = PrayerSettings.get(),
    ): List<PrayerTime> =
        CustomTimesPrefs.get().applyTo(calculatedFor(context, date, settings))

    /**
     * The same day as the app works it out, before any time the user has fixed.
     *
     * What [forDate] would say if nothing were pinned, which is the only way to tell how far a
     * pinned time sits from the app's own answer — and the change log is written in exactly that
     * distance.
     */
    fun calculatedFor(
        context:  Context,
        date:     LocalDate,
        settings: PrayerCalcSettings = PrayerSettings.get(),
    ): List<PrayerTime> {
        val location = OnboardingPrefs.location(context) ?: return emptyList()
        if (location.lat == 0.0 && location.lng == 0.0) return emptyList()

        return runCatching {
            PrayerEngine.calculate(
                location.lat, location.lng, date, settings.resolve(location.countryCode),
            )
        }.getOrElse { e ->
            Log.e(TAG, "Calculation failed for $date", e)
            emptyList()
        }
    }
}
