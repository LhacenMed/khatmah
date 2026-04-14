package com.lhacenmed.khatmah.data.prayer

import android.os.Build
import androidx.annotation.RequiresApi
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.*

/**
 * On-device Islamic prayer time calculator using the USNO solar position algorithm.
 *
 * All intermediate calculations are in UTC decimal hours. The final step converts to
 * the device's local timezone via [ZoneId.systemDefault], which is correct for users
 * whose system clock is set to their current location — the standard case.
 *
 * Returns Fajr · Sunrise · Dhuhr · Asr · Maghrib · Isha in that order.
 * Returns an empty list when the sun never rises/sets (extreme latitudes).
 *
 * Default angles follow the Muslim World League convention (Fajr 18°, Isha 17°).
 *
 * References: US Naval Observatory simplified algorithm; praytimes.org v2.3.
 */
@RequiresApi(Build.VERSION_CODES.O)
object PrayerEngine {

    private val NAMES = arrayOf("Fajr", "Sunrise", "Dhuhr", "Asr", "Maghrib", "Isha")

    /**
     * @param lat        Latitude in decimal degrees.
     * @param lng        Longitude in decimal degrees.
     * @param date       Gregorian date for which to compute times.
     * @param fajrAngle  Sun depression angle for Fajr (degrees below horizon). Default: 18°.
     * @param ishaAngle  Sun depression angle for Isha (degrees below horizon). Default: 17°.
     * @param madhab     Asr shadow multiplier — 1 = Shafi/Maliki/Hanbali, 2 = Hanafi.
     */
    fun calculate(
        lat: Double,
        lng: Double,
        date: LocalDate,
        fajrAngle: Double = 18.0,
        ishaAngle: Double = 17.0,
        madhab: Int = 1,
    ): List<PrayerTime> {
        val jd   = julianDay(date.year, date.monthValue, date.dayOfMonth)
        val decl = sunDeclination(jd)
        val eot  = equationOfTime(jd)

        // UTC decimal hours of solar noon at this longitude.
        val noon = 12.0 - lng / 15.0 - eot

        // Hour angles (decimal hours east/west of noon). null = sun never reaches that angle.
        val haHorizon = hourAngle(lat, decl, 90.833) ?: return emptyList()
        val haFajr    = hourAngle(lat, decl, 90.0 + fajrAngle) ?: return emptyList()
        val haIsha    = hourAngle(lat, decl, 90.0 + ishaAngle) ?: return emptyList()
        val haAsr     = asrHourAngle(lat, decl, madhab) ?: return emptyList()

        val utcHours = doubleArrayOf(
            noon - haFajr,      // Fajr
            noon - haHorizon,   // Sunrise
            noon,               // Dhuhr
            noon + haAsr,       // Asr
            noon + haHorizon,   // Maghrib
            noon + haIsha,      // Isha
        )

        val offsetHours = zoneOffsetHours(date)
        return utcHours.mapIndexed { i, utc ->
            PrayerTime(NAMES[i], toLocalTime(utc + offsetHours))
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
        ra -= floor(ra / 24.0 + 0.5) * 24.0    // normalize to [-12, 12]
        return q / 15.0 - ra
    }

    /**
     * Hour angle (decimal hours) for a given zenith angle in degrees.
     * Returns null when |cos| > 1, i.e. the sun never reaches that zenith.
     */
    private fun hourAngle(lat: Double, decl: Double, zenith: Double): Double? {
        val cosT = (cos(rad(zenith)) - sin(rad(lat)) * sin(decl)) /
                (cos(rad(lat)) * cos(decl))
        return if (cosT < -1.0 || cosT > 1.0) null else deg(acos(cosT)) / 15.0
    }

    /**
     * Hour angle for Asr based on the madhab shadow multiplier.
     * madhab = 1 → Shafi/Maliki/Hanbali (shadow = 1× object); 2 → Hanafi (2×).
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
        val normalized = ((localHour % 24) + 24) % 24
        val h = normalized.toInt()
        val m = ((normalized - h) * 60).roundToInt().coerceIn(0, 59)
        return LocalTime.of(h.coerceIn(0, 23), m)
    }

    private fun Double.roundToInt() = round(this).toInt()
    private fun rad(d: Double) = d * PI / 180.0
    private fun deg(r: Double) = r * 180.0 / PI
}