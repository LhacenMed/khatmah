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

/**
 * Creates notification channels and posts all reminder notifications.
 *
 * Channel IDs:
 *  "reminder_silent"         — IMPORTANCE_LOW, no sound/vibration.
 *  "reminder_device"         — IMPORTANCE_HIGH, system default sound.
 *  "reminder_pre"            — IMPORTANCE_HIGH, pre-adhan alerts.
 *  "adhan_<hash>"            — IMPORTANCE_HIGH, per adhan asset file.
 *  "adhan_custom_<hash>"     — IMPORTANCE_HIGH, per user-picked adhan file.
 *  "reminder_custom_<hash>"  — IMPORTANCE_HIGH, per user-picked non-prayer file.
 *
 * Bump [CH_VERSION] on any channel config change — channels are immutable after creation.
 *
 * AdhanSound is intentionally NOT imported here; sound keys are parsed as plain strings
 * to keep shared/ independent of feature/prayer.
 */
@RequiresApi(Build.VERSION_CODES.O)
object ReminderNotifier {

    private const val CH_VERSION = 1
    private const val PREFS_CH   = "notif_channels"
    private const val K_VER      = "version"

    private const val CH_SILENT = "reminder_silent"
    private const val CH_DEVICE = "reminder_device"
    private const val CH_PRE    = "reminder_pre"

    // ── Notification IDs ──────────────────────────────────────────────────────

    fun mainNotifId(config: ReminderConfig): Int = when (val t = config.type) {
        is ReminderType.Prayer       -> 2000 + t.prayerId
        is ReminderType.Adhkar       -> 2100 + t.categoryId.hashCode().and(0xFF)
        is ReminderType.QuranSunnah  -> 2200 + t.surahKey.hashCode().and(0xFF)
        is ReminderType.DailyKhatmah -> 2300
        is ReminderType.Custom       -> 2400 + t.customId.hashCode().and(0xFF)
    }

    fun preAlertNotifId(prayerId: Int) = 2010 + prayerId

    // ── Channel ID resolution ─────────────────────────────────────────────────

    /**
     * Channel ID for a prayer soundKey.
     * Matches AdhanSound key format without importing AdhanSound.
     */
    fun prayerChannelId(soundKey: String): String = when {
        soundKey == "off" || soundKey == "silent" -> CH_SILENT
        soundKey == "device"                       -> CH_DEVICE
        soundKey.startsWith("asset:")              -> "adhan_${soundKey.removePrefix("asset:").hashCode()}"
        soundKey.startsWith("custom\u0000")        ->
            "adhan_custom_${soundKey.split('\u0000').getOrElse(2) { "" }.hashCode()}"
        else -> CH_DEVICE
    }

    private fun reminderChannelId(soundKey: String): String = when {
        soundKey == "off"                   -> CH_SILENT
        soundKey == "device"                -> CH_DEVICE
        soundKey.startsWith("custom\u0000") ->
            "reminder_custom_${soundKey.split('\u0000').getOrElse(2) { "" }.hashCode()}"
        else -> CH_DEVICE
    }

    // ── Channel creation ──────────────────────────────────────────────────────

    fun ensureChannels(context: Context, adhanFilenames: List<String>) {
        val nm    = context.getSystemService<NotificationManager>() ?: return
        val prefs = context.getSharedPreferences(PREFS_CH, Context.MODE_PRIVATE)

        if (prefs.getInt(K_VER, 0) < CH_VERSION) {
            nm.notificationChannels
                .filter { it.id.startsWith("adhan_") || it.id.startsWith("reminder_") }
                .forEach { nm.deleteNotificationChannel(it.id) }
            prefs.edit { putInt(K_VER, CH_VERSION) }
        }

        val musicAttr = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()

        nm.createNotificationChannel(
            NotificationChannel(CH_SILENT, context.getString(R.string.notif_channel_silent),
                NotificationManager.IMPORTANCE_LOW).apply {
                setSound(null, null); enableVibration(false)
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_DEVICE, context.getString(R.string.notif_channel_device),
                NotificationManager.IMPORTANCE_HIGH)
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_PRE, context.getString(R.string.notif_channel_pre),
                NotificationManager.IMPORTANCE_HIGH)
        )
        adhanFilenames.forEach { filename ->
            val id = "adhan_${filename.hashCode()}"
            if (nm.getNotificationChannel(id) != null) return@forEach
            nm.createNotificationChannel(
                NotificationChannel(id, filename.removeSuffix(".mp3"),
                    NotificationManager.IMPORTANCE_HIGH)
                    .apply { setSound(adhanAssetUri(context, filename), musicAttr) }
            )
        }
    }

    /** Ensures a channel exists for a user-picked adhan (prayer) sound file. */
    fun ensureCustomAdhanChannel(context: Context, uri: String, displayName: String) {
        val nm = context.getSystemService<NotificationManager>() ?: return
        val id = "adhan_custom_${uri.hashCode()}"
        if (nm.getNotificationChannel(id) != null) return
        nm.createNotificationChannel(
            NotificationChannel(id, displayName, NotificationManager.IMPORTANCE_HIGH).apply {
                setSound(Uri.parse(uri), AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            }
        )
    }

    /** Ensures a channel exists for a user-picked non-prayer sound file. */
    fun ensureCustomReminderChannel(context: Context, sound: ReminderSound.Custom) {
        val nm = context.getSystemService<NotificationManager>() ?: return
        val id = "reminder_custom_${sound.uri.hashCode()}"
        if (nm.getNotificationChannel(id) != null) return
        nm.createNotificationChannel(
            NotificationChannel(id, sound.displayName, NotificationManager.IMPORTANCE_HIGH).apply {
                setSound(Uri.parse(sound.uri), AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            }
        )
    }

    // ── Notification posting ──────────────────────────────────────────────────

    /** Posts an adhan notification. No-op if soundKey is "off". */
    fun postPrayer(context: Context, config: ReminderConfig, prayerName: String, timeMs: Long) {
        if (config.soundKey == "off") return
        val nm   = context.getSystemService<NotificationManager>() ?: return
        val time = android.text.format.DateFormat.getTimeFormat(context).format(java.util.Date(timeMs))
        nm.notify(mainNotifId(config), build(
            context    = context,
            channelId  = prayerChannelId(config.soundKey),
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
        if (ReminderSound.fromKey(config.soundKey) is ReminderSound.Off) return
        val nm = context.getSystemService<NotificationManager>() ?: return
        nm.notify(mainNotifId(config), build(
            context  = context,
            channelId = reminderChannelId(config.soundKey),
            title    = title,
            body     = body,
            deepLink = config.deepLink ?: defaultDeepLink(config.type),
        ))
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun defaultDeepLink(type: ReminderType): String = when (type) {
        is ReminderType.Prayer       -> "prayers"
        is ReminderType.Adhkar       -> "adhkar"
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