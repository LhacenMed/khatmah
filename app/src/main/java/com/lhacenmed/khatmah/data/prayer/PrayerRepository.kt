package com.lhacenmed.khatmah.data.prayer

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.lhacenmed.khatmah.util.OnboardingPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.LocalDate

private const val TAG = "PrayerRepository"

/**
 * Entry point for prayer times.
 *
 * [PrayerEngine.calculate] is pure math (< 1 ms), so a lightweight session-scoped
 * [HashMap] is sufficient — no SQLite persistence is needed.
 *
 * [refresh] clears the cache and recomputes today; call it after a location update.
 */
@RequiresApi(Build.VERSION_CODES.O)
class PrayerRepository(context: Context) {

    private val appContext = context.applicationContext
    private val cache      = HashMap<LocalDate, List<PrayerTime>>()
    private val mutex      = Mutex()

    suspend fun getForDate(date: LocalDate): List<PrayerTime> {
        cache[date]?.let { return it }
        return mutex.withLock {
            cache[date] ?: compute(date).also { result ->
                if (result.isNotEmpty()) cache[date] = result
            }
        }
    }

    suspend fun refresh(): List<PrayerTime> {
        mutex.withLock { cache.clear() }
        return getForDate(LocalDate.now())
    }

    private suspend fun compute(date: LocalDate): List<PrayerTime> =
        withContext(Dispatchers.Default) {
            val loc = OnboardingPrefs.location(appContext) ?: return@withContext emptyList()
            if (loc.lat == 0.0 && loc.lng == 0.0) return@withContext emptyList()
            runCatching { PrayerEngine.calculate(loc.lat, loc.lng, date) }
                .getOrElse { e -> Log.e(TAG, "Calculation failed for $date", e); emptyList() }
        }
}