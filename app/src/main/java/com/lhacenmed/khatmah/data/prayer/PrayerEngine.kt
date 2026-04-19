package com.lhacenmed.khatmah.data.prayer

import android.os.Build
import androidx.annotation.RequiresApi
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.*

/**
 * On-device Islamic prayer time calculator (USNO solar position algorithm).
 *
 * All intermediate calculations are in UTC decimal hours.
 * The final step converts to the device's local timezone via [ZoneId.systemDefault].
 *
 * Correction layering (applied in order after the astronomical result):
 *  1. [MethodOffsets]     — authority-defined, baked into each [CalcMethod].
 *  2. [ManualCorrections] — user-configurable, stored in [PrayerCalcSettings].
 *
 * Supports:
 *  - 29 calculation methods (angle-based and fixed-minutes Isha)
 *  - Shafi/Maliki/Hanbali and Hanafi Asr
 *  - Automatic / ±1 h DST override
 *  - Method baked-in offsets extracted from the original Khatmah source
 *  - Manual per-prayer minute corrections
 *  - None / Middle-of-night / 1/7-of-night / Angle-based high-latitude fallbacks
 *
 * Returns Fajr · Sunrise · Dhuhr · Asr · Maghrib · Isha in that order.
 * Returns an empty list only when the sun never rises or sets (polar night/day)
 * and no high-latitude fallback is configured.
 *
 * References:
 *   US Naval Observatory simplified solar algorithm
 *   praytimes.org v2.3
 */
@RequiresApi(Build.VERSION_CODES.O)
object PrayerEngine {

    private val NAMES = arrayOf("Fajr", "Sunrise", "Dhuhr", "Asr", "Maghrib", "Isha")

    /**
     * Compute prayer times for [lat]/[lng] on [date] using fully-resolved [settings].
     * Call [PrayerCalcSettings.resolve] before passing settings here.
     */
    fun calculate(
        lat:      Double,
        lng:      Double,
        date:     LocalDate,
        settings: PrayerCalcSettings,
    ): List<PrayerTime> {
        val method      = settings.method
        val madhab      = settings.juristic.madhab
        val higherLat   = settings.higherLatMode
        val offsets     = method.offsets
        val corrections = settings.corrections

        val jd   = julianDay(date.year, date.monthValue, date.dayOfMonth)
        val decl = sunDeclination(jd)
        val eot  = equationOfTime(jd)
        val noon = 12.0 - lng / 15.0 - eot   // UTC decimal hours of solar noon

        // Horizon hour angle — needed for sunrise/sunset and night-duration fallbacks.
        val haHorizon = hourAngle(lat, decl, 90.833)
            ?: return emptyList()   // polar day/night — cannot proceed without a horizon

        val sunrise  = noon - haHorizon
        val sunset   = noon + haHorizon
        val nightDur = (24.0 - sunset + sunrise).let { if (it < 0) it + 24.0 else it }

        // Fajr
        val fajrUtc: Double = hourAngle(lat, decl, 90.0 + method.fajrAngle)
            ?.let { noon - it }
            ?: highLatFallback(higherLat, sunrise, sunset, nightDur, isIsha = false, method.fajrAngle)
            ?: return emptyList()

        // Isha
        val ishaUtc: Double = when (val mode = method.ishaMode) {
            is IshaMode.FixedMinutes -> sunset + mode.minutes / 60.0
            is IshaMode.Angle        -> hourAngle(lat, decl, 90.0 + mode.degrees)
                ?.let { noon + it }
                ?: highLatFallback(higherLat, sunrise, sunset, nightDur, isIsha = true, mode.degrees)
                ?: return emptyList()
        }

        val haAsr = asrHourAngle(lat, decl, madhab) ?: return emptyList()

        val dstExtra = when (settings.dstMode) {
            DstMode.PLUS_ONE  ->  1.0
            DstMode.MINUS_ONE -> -1.0
            DstMode.AUTOMATIC ->  0.0
        }
        val zoneOff = zoneOffsetHours(date) + dstExtra

        val utcTimes = doubleArrayOf(fajrUtc, sunrise, noon, noon + haAsr, sunset, ishaUtc)

        // Layer 1: method's authority-defined offsets (baked in per CalcMethod).
        // Layer 2: user's manual corrections on top.
        val totalMins = intArrayOf(
            offsets.fajr    + corrections.fajr,
            offsets.sunrise + corrections.sunrise,
            offsets.dhuhr   + corrections.dhuhr,
            offsets.asr     + corrections.asr,
            offsets.maghrib + corrections.maghrib,
            offsets.isha    + corrections.isha,
        )

        return utcTimes.mapIndexed { i, utc ->
            PrayerTime(NAMES[i], toLocalTime(utc + zoneOff + totalMins[i] / 60.0))
        }
    }

    // ── High-latitude fallback ────────────────────────────────────────────────

    /**
     * Returns a fallback UTC hour for Fajr/Isha when the sun never reaches the
     * required depression angle. Returns null only for [HigherLatMode.NONE].
     *
     * Reference: praytimes.org — angle/60 as fraction of night duration.
     */
    private fun highLatFallback(
        mode:     HigherLatMode,
        sunrise:  Double,
        sunset:   Double,
        nightDur: Double,
        isIsha:   Boolean,
        angle:    Double,
    ): Double? = when (mode) {
        HigherLatMode.NONE             -> null
        HigherLatMode.MIDDLE_OF_NIGHT  ->
            if (isIsha) sunset + nightDur / 2.0 else sunrise - nightDur / 2.0
        HigherLatMode.SEVENTH_OF_NIGHT ->
            if (isIsha) sunset + nightDur / 7.0 else sunrise - nightDur / 7.0
        HigherLatMode.ANGLE_BASED      -> {
            val portion = (angle / 60.0) * nightDur
            if (isIsha) sunset + portion else sunrise - portion
        }
    }

    // ── Solar math ────────────────────────────────────────────────────────────

    private fun julianDay(year: Int, month: Int, day: Int): Double {
        val y = if (month <= 2) year - 1 else year
        val m = if (month <= 2) month + 12 else month
        val a = y / 100
        val b = 2 - a + a / 4
        return floor(365.25 * (y + 4716)) + floor(30.6001 * (m + 1)) + day + b - 1524.5
    }

    /** Sun's declination in radians. */
    private fun sunDeclination(jd: Double): Double {
        val d = jd - 2451545.0
        val g = rad(357.529 + 0.98560028 * d)
        val q = rad(280.459 + 0.98564736 * d)
        val l = q + rad(1.915) * sin(g) + rad(0.020) * sin(2.0 * g)
        val e = rad(23.439 - 0.00000036 * d)
        return asin(sin(e) * sin(l))
    }

    /** Equation of time in decimal hours. */
    private fun equationOfTime(jd: Double): Double {
        val d  = jd - 2451545.0
        val g  = rad(357.529 + 0.98560028 * d)
        val q  = 280.459 + 0.98564736 * d
        val l  = rad(q + 1.915 * sin(g) + 0.020 * sin(2.0 * g))
        val e  = rad(23.439 - 0.00000036 * d)
        var ra = deg(atan2(cos(e) * sin(l), cos(l))) / 15.0
        ra -= floor(ra / 24.0 + 0.5) * 24.0
        return q / 15.0 - ra
    }

    /**
     * Hour angle (decimal hours) for a given zenith angle.
     * Returns null when the sun never reaches that zenith.
     */
    private fun hourAngle(lat: Double, decl: Double, zenith: Double): Double? {
        val cosT = (cos(rad(zenith)) - sin(rad(lat)) * sin(decl)) /
                (cos(rad(lat)) * cos(decl))
        return if (cosT < -1.0 || cosT > 1.0) null else deg(acos(cosT)) / 15.0
    }

    /**
     * Hour angle for Asr based on madhab shadow multiplier.
     * [madhab] = 1 → Shafi/Maliki/Hanbali; 2 → Hanafi.
     */
    private fun asrHourAngle(lat: Double, decl: Double, madhab: Int): Double? {
        val target = atan(1.0 / (madhab + tan(abs(rad(lat) - decl))))
        return hourAngle(lat, decl, 90.0 - deg(target))
    }

    // ── Timezone / time conversion ────────────────────────────────────────────

    private fun zoneOffsetHours(date: LocalDate): Double =
        ZoneId.systemDefault().rules
            .getOffset(date.atStartOfDay(ZoneId.systemDefault()).toInstant())
            .totalSeconds / 3600.0

    /** Converts a local decimal hour (possibly outside [0, 24)) to [LocalTime]. */
    private fun toLocalTime(localHour: Double): LocalTime {
        val norm = ((localHour % 24) + 24) % 24
        val h    = norm.toInt()
        val m    = ((norm - h) * 60).roundToInt().coerceIn(0, 59)
        return LocalTime.of(h.coerceIn(0, 23), m)
    }

    private fun Double.roundToInt() = round(this).toInt()
    private fun rad(d: Double) = d * PI / 180.0
    private fun deg(r: Double) = r * 180.0 / PI
}