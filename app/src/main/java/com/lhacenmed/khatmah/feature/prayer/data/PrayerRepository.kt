package com.lhacenmed.khatmah.feature.prayer.data

import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.LocalDate

/**
 * Entry point for prayer times.
 *
 * [PrayerTimetable.forDate] is pure math (< 1 ms) so a lightweight session-scoped
 * [HashMap] is sufficient. The cache is keyed by [LocalDate] and automatically
 * invalidated whenever [PrayerTimetable.version] changes — i.e. every time the user
 * saves a new calculation setting or pins a time.
 */
@RequiresApi(Build.VERSION_CODES.O)
class PrayerRepository(context: Context) {

    private val appContext = context.applicationContext
    private val cache      = HashMap<LocalDate, List<PrayerTime>>()
    private val mutex      = Mutex()

    /** Timetable version that was used to populate [cache]. */
    private var cachedTimetableVersion = -1

    suspend fun getForDate(date: LocalDate): List<PrayerTime> {
        // Invalidate when settings or pinned times changed since last computation.
        val currentVersion = PrayerTimetable.version
        if (currentVersion != cachedTimetableVersion) {
            mutex.withLock {
                cache.clear()
                cachedTimetableVersion = currentVersion
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
        mutex.withLock { cache.clear(); cachedTimetableVersion = -1 }
        return getForDate(LocalDate.now())
    }

    private suspend fun compute(date: LocalDate): List<PrayerTime> =
        withContext(Dispatchers.Default) { PrayerTimetable.forDate(appContext, date) }
}
