package com.lhacenmed.khatmah.feature.prayer.data

import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.content.edit
import com.lhacenmed.khatmah.shared.util.OnboardingPrefs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalTime

/**
 * The prayers whose time the user has fixed. Null means the time is calculated.
 *
 * Per prayer rather than all or nothing, because that is the shape of the problem: a timetable is
 * usually right except for the one prayer the local mosque calls differently. Wanting to set the
 * whole day by hand is then just wanting to pin each of them, which the same model already says.
 *
 * Held as minutes since midnight so the type is the same on every API level — [LocalTime] needs
 * 26, and these are read on paths that run below it — and so each one stores as a single int.
 */
data class CustomPrayerTimes(
    val fajr:    Int? = null,
    val sunrise: Int? = null,
    val dhuhr:   Int? = null,
    val asr:     Int? = null,
    val maghrib: Int? = null,
    val isha:    Int? = null,
    /**
     * The place these were set for, or null when nothing is pinned.
     *
     * A time fixed by hand is a fact about one place — the mosque down that road calls Isha at
     * 19:05 — and means nothing anywhere else. Carrying the place with the times is what lets the
     * app tell that it has been taken somewhere they no longer describe.
     */
    val placeKey: String? = null,
) {
    /** In the order [PrayerEngine] returns, so a pin lines up with the prayer it belongs to. */
    val inPrayerOrder: List<Int?> get() = listOf(fajr, sunrise, dhuhr, asr, maghrib, isha)

    /** True when every time is the app's own working. */
    val hasNone: Boolean get() = inPrayerOrder.all { it == null }

    /** True when the day is entirely the user's, and the calculation has nothing left to say. */
    val hasAll: Boolean get() = inPrayerOrder.all { it != null }

    /** [calculated], with every pinned prayer moved to the time the user gave it. */
    @RequiresApi(Build.VERSION_CODES.O)
    fun applyTo(calculated: List<PrayerTime>): List<PrayerTime> {
        if (hasNone) return calculated
        return calculated.mapIndexed { i, prayer ->
            val pinned = inPrayerOrder.getOrNull(i) ?: return@mapIndexed prayer
            prayer.copy(time = LocalTime.of(pinned / 60, pinned % 60))
        }
    }

    /** A copy with the prayer at [index] pinned to [minuteOfDay], or calculated again when null. */
    fun with(index: Int, minuteOfDay: Int?): CustomPrayerTimes = when (index) {
        0    -> copy(fajr    = minuteOfDay)
        1    -> copy(sunrise = minuteOfDay)
        2    -> copy(dhuhr   = minuteOfDay)
        3    -> copy(asr     = minuteOfDay)
        4    -> copy(maghrib = minuteOfDay)
        5    -> copy(isha    = minuteOfDay)
        else -> this
    }
}

/**
 * Persists [CustomPrayerTimes] to SharedPreferences.
 *
 * Mirrors [PrayerSettings], and for the same reason: the times are wanted on paths that cannot
 * afford to wait for storage — the widget being drawn, the alarms being set at boot — so they are
 * held in memory and read from there. [init] must run once in App.onCreate before anything asks.
 *
 * [version] is bumped on every [save] and read through [PrayerTimetable.version], which is what
 * tells a cache its answer is stale.
 */
object CustomTimesPrefs {

    private const val PREFS     = "prayer_custom_times"
    private const val KEY_PLACE = "place_key"

    /** One key per prayer, in [CustomPrayerTimes.inPrayerOrder]. */
    private val KEYS = arrayOf("fajr", "sunrise", "dhuhr", "asr", "maghrib", "isha")

    /** Stands in for "not pinned" — SharedPreferences has no nullable int. */
    private const val NOT_PINNED = -1

    /** Monotonically increasing; bumped by one on every [save]. */
    @Volatile var version: Int = 0
        private set

    private val _flow = MutableStateFlow(CustomPrayerTimes())
    val flow: StateFlow<CustomPrayerTimes> = _flow.asStateFlow()

    /** Load persisted times into memory. Call once from App.onCreate. */
    fun init(context: Context) {
        _flow.value = load(context)
    }

    /** The times in force (in-memory, no I/O). */
    fun get(): CustomPrayerTimes = _flow.value

    /**
     * Persists [times] and broadcasts them to [flow], stamped with where they were set.
     *
     * The caller does not supply the place: pinning a time always means pinning it here, and
     * leaving that to each caller to remember is how it would eventually be forgotten.
     */
    fun save(context: Context, times: CustomPrayerTimes) {
        val stamped = times.copy(
            placeKey = if (times.hasNone) null else OnboardingPrefs.location(context)?.placeKey(),
        )
        prefs(context).edit {
            stamped.inPrayerOrder.forEachIndexed { i, minuteOfDay ->
                putInt(KEYS[i], minuteOfDay ?: NOT_PINNED)
            }
            putString(KEY_PLACE, stamped.placeKey)
        }
        _flow.value = stamped
        version++
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private fun load(context: Context): CustomPrayerTimes {
        val p = prefs(context)
        fun pinned(i: Int): Int? = p.getInt(KEYS[i], NOT_PINNED).takeIf { it != NOT_PINNED }
        return CustomPrayerTimes(
            fajr    = pinned(0),
            sunrise = pinned(1),
            dhuhr   = pinned(2),
            asr     = pinned(3),
            maghrib = pinned(4),
            isha    = pinned(5),
            placeKey = p.getString(KEY_PLACE, null),
        )
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

/**
 * The place pinned times belong to, to within about 11 km.
 *
 * Coarse deliberately. Prayer times differ by seconds across that distance, so a location detected
 * again and landing a few streets over is the same place and must not cost the user their times;
 * somewhere genuinely else reads as different, which is the only case worth reacting to. Rounded
 * through whole numbers so the key reads the same whatever language the app is in.
 */
fun OnboardingPrefs.LocationData.placeKey(): String =
    "${Math.round(lat * 10)},${Math.round(lng * 10)}"
