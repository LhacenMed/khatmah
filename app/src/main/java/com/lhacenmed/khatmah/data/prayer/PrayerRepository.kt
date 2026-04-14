package com.lhacenmed.khatmah.data.prayer

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.lhacenmed.khatmah.util.OnboardingPrefs
import java.time.LocalDate
import java.time.LocalTime

private const val TAG = "PrayerRepository"

/**
 * Single entry point for prayer times.
 *
 * Strategy:
 *  1. Return SQLite-cached rows for [date] if present (fast, offline).
 *  2. Otherwise compute with [PrayerEngine] using the stored coordinates,
 *     persist the result, and return it.
 *
 * [refresh] wipes the cache and recomputes today, used after a location update.
 */
@RequiresApi(Build.VERSION_CODES.O)
class PrayerRepository(context: Context) {

    private val appContext = context.applicationContext
    private val dao        = PrayerDao(PrayerDatabase.get(appContext))

    /** Returns prayer times for [date], computing and caching them on first access. */
    suspend fun getForDate(date: LocalDate): List<PrayerTime> {
        val cached = dao.getForDate(date.toString())
        if (cached.isNotEmpty()) return cached.map { it.toPrayerTime() }
        return computeAndCache(date)
    }

    /**
     * Clears all cached rows and recomputes today's prayer times.
     * Call this after updating the stored location so every tab reflects new coordinates.
     */
    suspend fun refresh(): List<PrayerTime> {
        dao.clearAll()
        return computeAndCache(LocalDate.now())
    }

    // ── Private ───────────────────────────────────────────────────────────────

    private suspend fun computeAndCache(date: LocalDate): List<PrayerTime> {
        val loc = OnboardingPrefs.location(appContext) ?: return emptyList()
        if (loc.lat == 0.0 && loc.lng == 0.0) return emptyList()

        return runCatching {
            PrayerEngine.calculate(loc.lat, loc.lng, date).also { prayers ->
                dao.insertAll(prayers.map { it.toEntity(date) })
            }
        }.getOrElse { e ->
            Log.e(TAG, "Calculation failed for $date", e)
            emptyList()
        }
    }
}

// ── Private extensions ────────────────────────────────────────────────────────

@RequiresApi(Build.VERSION_CODES.O)
private fun PrayerEntity.toPrayerTime() = PrayerTime(name, LocalTime.of(hour, minute))

@RequiresApi(Build.VERSION_CODES.O)
private fun PrayerTime.toEntity(date: LocalDate) = PrayerEntity(
    date   = date.toString(),
    name   = name,
    hour   = time.hour,
    minute = time.minute,
)