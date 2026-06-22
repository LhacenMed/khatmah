package com.lhacenmed.khatmah.shared.reminders

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.content.edit
import androidx.core.content.getSystemService
import com.lhacenmed.khatmah.R
import com.lhacenmed.khatmah.core.MainActivity
import com.lhacenmed.khatmah.shared.util.AdhanSoundFiles

/**
 * Creates notification channels and posts all reminder notifications.
 *
 * Channels are organised by **purpose**, not by sound:
 *  "reminder_silent"   — صامت, IMPORTANCE_LOW, no sound/vibration.
 *  "reminder_alert"    — صوت منبه, IMPORTANCE_HIGH, device default sound.
 *  "reminder_pre"      — تنبيه قبل الأذان, IMPORTANCE_HIGH, pre-adhan alerts.
 *  "reminder_adhkar"   — منبهات الأذكار, IMPORTANCE_HIGH (adhkar reminders).
 *  "reminder_wird"     — منبهات الورد اليومي, IMPORTANCE_HIGH (daily khatmah).
 *  "reminder_khatmah"  — ختمة, IMPORTANCE_HIGH (sunnah surahs + custom reminders).
 *
 * Adhan sounds need a per-sound channel (Android binds sound to channel). Those are created
 * **on demand** — only for a sound a prayer actually uses — by [ensureAdhanAssetChannel] /
 * [ensureCustomAdhanChannel], and orphans are removed by [pruneAdhanChannels]. The prayer feature
 * drives that sync (it owns AdhanSound); this file stays independent of feature/prayer.
 *
 * Sound keys are parsed as plain strings (the "custom" form is "custom"+[SEP]+name+[SEP]+uri) so
 * shared/ never imports AdhanSound. Bump [CH_VERSION] on any fixed-channel config change.
 */
@RequiresApi(Build.VERSION_CODES.O)
object ReminderNotifier {

    private const val CH_VERSION = 2
    private const val PREFS_CH   = "notif_channels"
    private const val K_VER      = "version"

    private const val CH_SILENT  = "reminder_silent"
    private const val CH_ALERT   = "reminder_alert"
    private const val CH_PRE     = "reminder_pre"
    private const val CH_ADHKAR  = "reminder_adhkar"
    private const val CH_WIRD    = "reminder_wird"
    private const val CH_KHATMAH = "reminder_khatmah"

    /** Separator inside a "custom" sound key — the NUL char, never present in names or URIs. */
    private val SEP = 0.toChar()
    private val CUSTOM_PREFIX = "custom$SEP"

    // ── Notification IDs ──────────────────────────────────────────────────────

    fun mainNotifId(config: ReminderConfig): Int = when (val t = config.type) {
        is ReminderType.Prayer       -> 2000 + t.prayerId
        is ReminderType.Adhkar       -> 2100 + t.categoryId.hashCode().and(0xFF)
        is ReminderType.QuranSunnah  -> 2200 + t.surahKey.hashCode().and(0xFF)
        // Use alarmCode to differentiate multiple khatmah slots (alarmCodes 25-29 → 2325-2329).
        is ReminderType.DailyKhatmah -> 2300 + config.alarmCode
        is ReminderType.Custom       -> 2400 + t.customId.hashCode().and(0xFF)
    }

    fun preAlertNotifId(prayerId: Int) = 2010 + prayerId

    // ── Channel ID resolution ─────────────────────────────────────────────────

    /** Stable channel id for an adhan asset file (so the same sound reuses its channel). */
    fun adhanAssetChannelId(filename: String): String = "adhan_${filename.hashCode()}"

    /** Stable channel id for a user-picked adhan sound, keyed by its content URI. */
    fun customAdhanChannelId(uri: String): String = "adhan_custom_${uri.hashCode()}"

    /** Channel id for a prayer soundKey (AdhanSound key format, parsed as a plain string). */
    fun prayerChannelId(soundKey: String): String = when {
        soundKey == "off" || soundKey == "silent" -> CH_SILENT
        soundKey == "device"                       -> CH_ALERT
        soundKey.startsWith("asset:")              -> adhanAssetChannelId(assetFile(soundKey))
        soundKey.startsWith(CUSTOM_PREFIX)         -> customAdhanChannelId(customUri(soundKey))
        else                                       -> CH_ALERT
    }

    /** Channel id for a non-prayer reminder — by type when it has sound, silent when off. */
    private fun reminderChannelId(soundKey: String, typeKey: String): String = when {
        soundKey == "off"                  -> CH_SILENT
        soundKey.startsWith(CUSTOM_PREFIX) -> "reminder_custom_${customUri(soundKey).hashCode()}"
        typeKey.startsWith("adhkar:")      -> CH_ADHKAR
        typeKey == "khatmah"               -> CH_WIRD
        else                               -> CH_KHATMAH   // sunnah + custom + fallback
    }

    // ── Fixed channel creation ────────────────────────────────────────────────

    fun ensureChannels(context: Context) {
        val nm    = context.getSystemService<NotificationManager>() ?: return
        val prefs = context.getSharedPreferences(PREFS_CH, Context.MODE_PRIVATE)

        if (prefs.getInt(K_VER, 0) < CH_VERSION) {
            nm.notificationChannels
                .filter { it.id.startsWith("adhan_") || it.id.startsWith("reminder_") }
                .forEach { nm.deleteNotificationChannel(it.id) }
            prefs.edit { putInt(K_VER, CH_VERSION) }
        }

        nm.createNotificationChannel(
            NotificationChannel(CH_SILENT, context.getString(R.string.notif_channel_silent),
                NotificationManager.IMPORTANCE_LOW).apply { setSound(null, null); enableVibration(false) }
        )
        listOf(
            CH_ALERT   to R.string.notif_channel_alert,
            CH_PRE     to R.string.notif_channel_pre,
            CH_ADHKAR  to R.string.notif_channel_adhkar,
            CH_WIRD    to R.string.notif_channel_wird,
            CH_KHATMAH to R.string.notif_channel_khatmah,
        ).forEach { (id, nameRes) ->
            nm.createNotificationChannel(
                NotificationChannel(id, context.getString(nameRes), NotificationManager.IMPORTANCE_HIGH)
            )
        }
    }

    // ── On-demand adhan channels ──────────────────────────────────────────────

    /** Ensures the channel for an adhan asset exists, named by its display name. */
    fun ensureAdhanAssetChannel(context: Context, filename: String) {
        val nm = context.getSystemService<NotificationManager>() ?: return
        val id = adhanAssetChannelId(filename)
        if (nm.getNotificationChannel(id) != null) return
        nm.createNotificationChannel(
            NotificationChannel(id, AdhanSoundFiles.getDisplayName(filename),
                NotificationManager.IMPORTANCE_HIGH)
                .apply { setSound(adhanAssetUri(context, filename), musicAttributes()) }
        )
    }

    /** Ensures a channel exists for a user-picked adhan (prayer) sound file. */
    fun ensureCustomAdhanChannel(context: Context, uri: String, displayName: String) {
        val nm = context.getSystemService<NotificationManager>() ?: return
        val id = customAdhanChannelId(uri)
        if (nm.getNotificationChannel(id) != null) return
        nm.createNotificationChannel(
            NotificationChannel(id, displayName, NotificationManager.IMPORTANCE_HIGH)
                .apply { setSound(Uri.parse(uri), musicAttributes()) }
        )
    }

    /** Ensures a channel exists for a user-picked non-prayer sound file. */
    fun ensureCustomReminderChannel(context: Context, sound: ReminderSound.Custom) {
        val nm = context.getSystemService<NotificationManager>() ?: return
        val id = "reminder_custom_${sound.uri.hashCode()}"
        if (nm.getNotificationChannel(id) != null) return
        nm.createNotificationChannel(
            NotificationChannel(id, sound.displayName, NotificationManager.IMPORTANCE_HIGH)
                .apply { setSound(Uri.parse(sound.uri), musicAttributes()) }
        )
    }

    /** Deletes every adhan channel whose id is not in [keep] — i.e. no prayer uses it anymore. */
    fun pruneAdhanChannels(context: Context, keep: Set<String>) {
        val nm = context.getSystemService<NotificationManager>() ?: return
        nm.notificationChannels
            .filter { it.id.startsWith("adhan_") && it.id !in keep }
            .forEach { nm.deleteNotificationChannel(it.id) }
    }

    // ── Notification posting ──────────────────────────────────────────────────

    /** Posts an adhan notification. No-op if soundKey is "off". */
    fun postPrayer(context: Context, config: ReminderConfig, prayerName: String, timeMs: Long) {
        if (config.soundKey == "off") return
        val nm   = context.getSystemService<NotificationManager>() ?: return
        val time = android.text.format.DateFormat.getTimeFormat(context).format(java.util.Date(timeMs))
        nm.notify(mainNotifId(config), build(
            context    = context,
            channelId  = ensurePrayerChannel(context, config.soundKey),
            title      = context.getString(R.string.notif_adhan_title, prayerName, time),
            body       = context.getString(R.string.notif_adhan_body, prayerName),
            isSilent   = config.soundKey == "silent",
            deepLink   = config.deepLink ?: defaultDeepLink(config.type),
        ))
    }

    /** Posts a pre-adhan alert using the shared pre-alert channel. */
    fun postPreAlert(context: Context, config: ReminderConfig, prayerName: String) {
        val nm       = context.getSystemService<NotificationManager>() ?: return
        val prayerId = (config.type as ReminderType.Prayer).prayerId
        nm.notify(preAlertNotifId(prayerId), build(
            context   = context,
            channelId = CH_PRE,
            title     = prayerName,
            body      = context.getString(R.string.notif_pre_alert_body, config.preAlertMinutes, prayerName),
            deepLink  = config.deepLink ?: defaultDeepLink(config.type),
        ))
    }

    /** Posts a fixed-time reminder (adhkar, sunnah, khatmah, custom). No-op if sound is "off". */
    fun postReminder(context: Context, config: ReminderConfig, title: String, body: String) {
        val sound = ReminderSound.fromKey(config.soundKey)
        if (sound is ReminderSound.Off) return
        val nm = context.getSystemService<NotificationManager>() ?: return
        if (sound is ReminderSound.Custom) ensureCustomReminderChannel(context, sound)
        nm.notify(mainNotifId(config), build(
            context  = context,
            channelId = reminderChannelId(config.soundKey, config.type.toKey()),
            title    = title,
            body     = body,
            deepLink = config.deepLink ?: defaultDeepLink(config.type),
        ))
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Ensures the channel backing a prayer [soundKey] exists, then returns its id. */
    private fun ensurePrayerChannel(context: Context, soundKey: String): String {
        when {
            soundKey.startsWith("asset:") -> ensureAdhanAssetChannel(context, assetFile(soundKey))
            soundKey.startsWith(CUSTOM_PREFIX) -> ensureCustomAdhanChannel(
                context, customUri(soundKey), soundKey.split(SEP).getOrElse(1) { "" })
        }
        return prayerChannelId(soundKey)
    }

    /** Bare asset filename from an "asset:<file>" key (legacy ".mp3" keys map to ".opus"). */
    private fun assetFile(soundKey: String): String =
        soundKey.removePrefix("asset:").replace(".mp3", ".opus")

    /** Content URI from a "custom"+SEP+name+SEP+uri key. */
    private fun customUri(soundKey: String): String =
        soundKey.split(SEP).getOrElse(2) { "" }

    private fun musicAttributes(): AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()

    private fun defaultDeepLink(type: ReminderType): String = when (type) {
        is ReminderType.Prayer       -> "prayers"
        // Morning and evening adhkar deep-link directly to their detail pages.
        is ReminderType.Adhkar       -> when (type.categoryId) {
            "morning" -> "adhkar_detail/morning"
            "evening" -> "adhkar_detail/evening"
            else      -> "adhkar"
        }
        is ReminderType.QuranSunnah  -> "main"
        is ReminderType.DailyKhatmah -> "today"
        is ReminderType.Custom       -> "main"
    }

    private fun build(
        context:   Context,
        channelId: String,
        title:     String,
        body:      String,
        isSilent:  Boolean = false,
        deepLink:  String? = null,
    ) = NotificationCompat.Builder(context, channelId)
        .setSmallIcon(R.drawable.ic_stat_name)
        .setContentTitle(title)
        .setContentText(body)
        .setAutoCancel(true)
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setSilent(isSilent)
        .apply { if (deepLink != null) setContentIntent(tapIntent(context, deepLink)) }
        .build()

    private fun tapIntent(context: Context, route: String): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = "com.lhacenmed.khatmah.REMINDER"
            putExtra("route", route)
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(context, route.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    fun adhanAssetUri(context: Context, filename: String): Uri =
        Uri.Builder().scheme("content")
            .authority("${context.packageName}.adhan_provider")
            .appendPath(filename).build()
}
