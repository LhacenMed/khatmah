package com.lhacenmed.khatmah.feature.prayer.data.changelog

import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import com.lhacenmed.khatmah.BuildConfig
import com.lhacenmed.khatmah.feature.prayer.data.CustomPrayerTimes
import com.lhacenmed.khatmah.feature.prayer.data.ManualCorrections
import com.lhacenmed.khatmah.feature.prayer.data.PrayerSettings
import com.lhacenmed.khatmah.feature.prayer.data.PrayerTimetable
import com.lhacenmed.khatmah.feature.prayer.data.placeKey
import com.lhacenmed.khatmah.shared.supabase.SupabaseClient
import com.lhacenmed.khatmah.shared.util.DeviceId
import com.lhacenmed.khatmah.shared.util.OnboardingPrefs
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import java.util.UUID

/** The names the server knows the prayers by, in [CustomPrayerTimes.inPrayerOrder]. */
private val PRAYERS = listOf("fajr", "sunrise", "dhuhr", "asr", "maghrib", "isha")

private const val SOURCE_MANUAL_EDIT = "manual_edit"
private const val SOURCE_CLEARED     = "cleared"

/** Changes per request. Enough that a long offline spell leaves in a couple of round trips. */
private const val BATCH = 100

/**
 * Keeps the record of every prayer time the user has set by hand, and hands it to the server.
 *
 * Append-only: a change is a fact about a moment, so nothing here is ever edited or reconciled.
 * What each device currently has pinned is read back out of the log rather than kept beside it, so
 * there is one write path and no way for the two to drift.
 *
 * Sending is deliberately quiet. This is the app's own bookkeeping, not something the user asked
 * for, so it must never cost them a message, a spinner or a retry — a change that cannot be sent
 * now simply stays in the outbox until it can.
 */
@RequiresApi(Build.VERSION_CODES.O)
object PrayerTimeChangeLog {

    /** One flush at a time: a change and a reconnection can arrive together. */
    private val sending = Mutex()

    /**
     * Writes down what moved between [before] and [after] — one row per prayer, each carrying the
     * time the app would have said, so the change can later be read as a correction rather than
     * only as a preference.
     *
     * Records nothing when there is no calculated day to measure against: without it a row would
     * say a time was chosen but not what it was chosen instead of, which is the part worth having.
     */
    suspend fun record(context: Context, before: CustomPrayerTimes, after: CustomPrayerTimes) {
        if (!PrayerTimeSharingPrefs.isOn.value) return

        val moved = PRAYERS.indices.filter { before.inPrayerOrder[it] != after.inPrayerOrder[it] }
        if (moved.isEmpty()) return

        val location   = OnboardingPrefs.location(context) ?: return
        // Times belonging to somewhere else are on their way out because the user moved, not
        // because they changed their mind. Recording that as a decision would invent one.
        if (before.placeKey != null && before.placeKey != location.placeKey()) return

        val calculated = PrayerTimetable.calculatedFor(context, LocalDate.now())
        if (calculated.isEmpty()) return

        val settings  = PrayerSettings.get()
        // Resolved, because that is what produced the calculated times; autoSettings then says
        // whether the user chose it or their country did.
        val effective = settings.resolve(location.countryCode)
        val occurredAt = System.currentTimeMillis()

        val changes = moved.map { index ->
            val newMinute = after.inPrayerOrder[index]
            PrayerTimeChange(
                eventId          = UUID.randomUUID().toString(),
                occurredAt       = occurredAt,
                prayer           = PRAYERS[index],
                oldMinute        = before.inPrayerOrder[index],
                newMinute        = newMinute,
                calculatedMinute = calculated[index].time.let { it.hour * 60 + it.minute },
                lat              = location.lat,
                lng              = location.lng,
                city             = location.cityName,
                countryCode      = location.countryCode,
                tzId             = ZoneId.systemDefault().id,
                methodId         = effective.method.methodId,
                juristic         = effective.juristic.name,
                dstMode          = effective.dstMode.name,
                higherLat        = effective.higherLatMode.name,
                corrections      = effective.corrections.asJson(),
                autoSettings     = settings.autoSettings,
                source           = if (newMinute == null) SOURCE_CLEARED else SOURCE_MANUAL_EDIT,
                appVersion       = BuildConfig.VERSION_NAME,
                osApi            = Build.VERSION.SDK_INT,
            )
        }
        PrayerChangeDb.get(context).dao().insert(changes)
    }

    /**
     * Sends everything the server has not taken yet, oldest first, and keeps only what it refuses.
     *
     * Throws if a batch does not land, leaving that batch and everything after it in the outbox —
     * callers treat that as normal and try again at the next opportunity.
     */
    suspend fun flush(context: Context) = sending.withLock {
        val dao      = PrayerChangeDb.get(context).dao()
        val deviceId = DeviceId.of(context)
        while (true) {
            val batch = dao.oldest(BATCH)
            if (batch.isEmpty()) return@withLock
            SupabaseClient.insertPrayerTimeChanges(batch.map { it.asJson(deviceId) })
            dao.delete(batch.map { it.clientSeq })
        }
    }

    /** Drops everything not yet sent, for a user who has just stopped sharing. */
    suspend fun forgetPending(context: Context) = PrayerChangeDb.get(context).dao().clear()
}

// ─── Wire format ──────────────────────────────────────────────────────────────

/** A row as the server's columns, times as wall clock and the moment as UTC. */
@RequiresApi(Build.VERSION_CODES.O)
private fun PrayerTimeChange.asJson(deviceId: String) = JSONObject().apply {
    put("event_id",        eventId)
    put("device_id",       deviceId)
    put("client_seq",      clientSeq)
    put("occurred_at",     Instant.ofEpochMilli(occurredAt).toString())

    put("prayer",          prayer)
    put("old_time",        oldMinute.asWallClock())
    put("new_time",        newMinute.asWallClock())
    put("calculated_time", calculatedMinute.asWallClock())

    put("lat",             lat)
    put("lng",             lng)
    put("city",            city)
    put("country_code",    countryCode)
    put("tz_id",           tzId)

    put("method_id",       methodId)
    put("juristic",        juristic)
    put("dst_mode",        dstMode)
    put("higher_lat",      higherLat)
    put("corrections",     JSONObject(corrections))
    put("auto_settings",   autoSettings)

    put("source",          source)
    put("app_version",     appVersion)
    put("os_api",          osApi)
}

/**
 * Minutes since midnight as the server's `time`; a prayer with no fixed time sends null.
 *
 * Formatted against [Locale.ROOT], not the user's: in Arabic the default would spell the digits
 * Arabic-Indic and the server would refuse a time it cannot read.
 */
private fun Int?.asWallClock(): Any =
    this?.let { String.format(Locale.ROOT, "%02d:%02d:00", it / 60, it % 60) } ?: JSONObject.NULL

private fun ManualCorrections.asJson(): String = JSONObject().apply {
    put("fajr", fajr)
    put("sunrise", sunrise)
    put("dhuhr", dhuhr)
    put("asr", asr)
    put("maghrib", maghrib)
    put("isha", isha)
}.toString()
