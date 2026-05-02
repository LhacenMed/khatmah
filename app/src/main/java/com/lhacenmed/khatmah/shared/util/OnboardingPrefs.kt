package com.lhacenmed.khatmah.shared.util

import android.content.Context
import androidx.core.content.edit

/**
 * Persists onboarding completion state and the user's resolved location.
 *
 * Double values (lat/lng) are stored as Long bit patterns to preserve full
 * IEEE-754 precision — SharedPreferences has no native Double type.
 *
 * [LocationData.countryCode] is the ISO 3166-1 alpha-2 code (e.g. "MA" for Morocco).
 * It drives automatic calculation-method selection in [PrayerSettings].
 */
object OnboardingPrefs {

    private const val PREFS_FILE    = "onboarding"
    private const val KEY_COMPLETE  = "complete"
    private const val KEY_CITY      = "city_name"
    private const val KEY_LAT_BITS  = "lat_bits"
    private const val KEY_LNG_BITS  = "lng_bits"
    private const val KEY_COUNTRY   = "country_code"

    /** Location data stored during onboarding — used by [PrayerRepository]. */
    data class LocationData(
        val cityName:    String,
        val lat:         Double,
        val lng:         Double,
        /** ISO 3166-1 alpha-2 code; empty string if unknown. */
        val countryCode: String = "",
    )

    fun isComplete(context: Context): Boolean =
        prefs(context).getBoolean(KEY_COMPLETE, false)

    fun complete(
        context:     Context,
        cityName:    String,
        lat:         Double,
        lng:         Double,
        countryCode: String = "",
    ) {
        prefs(context).edit {
            putBoolean(KEY_COMPLETE, true)
            putString(KEY_CITY,    cityName)
            putLong(KEY_LAT_BITS,  lat.toBits())
            putLong(KEY_LNG_BITS,  lng.toBits())
            putString(KEY_COUNTRY, countryCode.uppercase())
        }
    }

    /** Returns saved location, or null when onboarding has not been completed. */
    fun location(context: Context): LocationData? {
        val p = prefs(context)
        if (!p.getBoolean(KEY_COMPLETE, false)) return null
        return LocationData(
            cityName    = p.getString(KEY_CITY, "").orEmpty(),
            lat         = Double.fromBits(p.getLong(KEY_LAT_BITS, 0L)),
            lng         = Double.fromBits(p.getLong(KEY_LNG_BITS, 0L)),
            countryCode = p.getString(KEY_COUNTRY, "").orEmpty(),
        )
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)
}