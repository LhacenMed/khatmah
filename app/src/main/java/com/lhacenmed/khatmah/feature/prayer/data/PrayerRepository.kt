package com.lhacenmed.khatmah.feature.prayer.data

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.lhacenmed.khatmah.shared.util.OnboardingPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.LocalDate

private const val TAG = "PrayerRepository"

/**
 * Entry point for prayer times.
 *
 * [PrayerEngine.calculate] is pure math (< 1 ms) so a lightweight session-scoped
 * [HashMap] is sufficient. The cache is keyed by [LocalDate] and automatically
 * invalidated whenever [PrayerSettings.version] changes — i.e. every time the user
 * saves a new calculation setting.
 */
@RequiresApi(Build.VERSION_CODES.O)
class PrayerRepository(context: Context) {

    private val appContext = context.applicationContext
    private val cache      = HashMap<LocalDate, List<PrayerTime>>()
    private val mutex      = Mutex()

    /** Settings version that was used to populate [cache]. */
    private var cachedSettingsVersion = -1

    suspend fun getForDate(date: LocalDate): List<PrayerTime> {
        // Invalidate when settings changed since last computation.
        val currentVersion = PrayerSettings.version
        if (currentVersion != cachedSettingsVersion) {
            mutex.withLock {
                cache.clear()
                cachedSettingsVersion = currentVersion
            }
        }
        cache[date]?.let { return it }
        return mutex.withLock {
            cache[date] ?: compute(date).also { result ->
                if (result.isNotEmpty()) cache[date] = result
            }
        }
    }

    suspend fun refresh(): List<PrayerTime> {
        mutex.withLock { cache.clear(); cachedSettingsVersion = -1 }
        return getForDate(LocalDate.now())
    }

    private suspend fun compute(date: LocalDate): List<PrayerTime> =
        withContext(Dispatchers.Default) {
            val loc = OnboardingPrefs.location(appContext) ?: return@withContext emptyList()
            if (loc.lat == 0.0 && loc.lng == 0.0) return@withContext emptyList()
            val settings = PrayerSettings.get().resolve(loc.countryCode)
            runCatching { PrayerEngine.calculate(loc.lat, loc.lng, date, settings) }
                .getOrElse { e -> Log.e(TAG, "Calculation failed for $date", e); emptyList() }
        }
}