package com.lhacenmed.khatmah.feature.prayer.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.content.edit
import androidx.core.content.getSystemService
import com.lhacenmed.khatmah.R

/**
 * Centralised notification utility.
 *
 * Channel strategy — one channel per [AdhanSound] variant:
 *   • "adhan_silent"        — IMPORTANCE_LOW, no sound/vibration.
 *   • "adhan_device"        — IMPORTANCE_HIGH, system default sound.
 *   • "adhan_<hashCode>"    — IMPORTANCE_HIGH, custom asset sound per file.
 *   • "adhan_pre"           — IMPORTANCE_HIGH, device default, for pre-adhan alerts.
 *   • "channel_test"        — legacy test channel.
 *
 * [CH_VERSION] must be incremented whenever channel configuration changes (e.g. sound
 * URI scheme migration). On version bump all adhan channels are deleted and recreated
 * so the new sound URI takes effect — channels are immutable once created on Android.
 */
object NotificationHelper {

    // ── Channel config ────────────────────────────────────────────────────────
    private const val CH_VERSION   = 4   // Bump when channel config changes.
    private const val PREFS_NOTIF  = "notif_prefs"
    private const val K_CH_VERSION = "ch_version"

    // ── Channel IDs ───────────────────────────────────────────────────────────
    private const val CH_TEST   = "channel_test"
    private const val CH_SILENT = "adhan_silent"
    private const val CH_DEVICE = "adhan_device"
    private const val CH_PRE    = "adhan_pre"

    // ── Notification IDs ──────────────────────────────────────────────────────
    private const val NOTIF_TEST = 1001
    // Prayer main: 2000-2005 | Pre-alert: 2010-2015
    fun mainNotifId(prayerId: Int) = 2000 + prayerId
    fun preNotifId(prayerId: Int)  = 2010 + prayerId

    // ── Channel ID for a given sound ──────────────────────────────────────────
    fun channelId(sound: AdhanSound): String = when (sound) {
        is AdhanSound.Off    -> "adhan_off"
        is AdhanSound.Silent -> CH_SILENT
        is AdhanSound.Device -> CH_DEVICE
        is AdhanSound.Asset  -> "adhan_${sound.filename.hashCode()}"
        is AdhanSound.Custom -> "adhan_custom_${sound.uri.hashCode()}"
    }

    // ── Ensure all needed channels exist ─────────────────────────────────────

    @RequiresApi(Build.VERSION_CODES.O)
    fun ensureChannels(context: Context, assetFilenames: List<String>) {
        val nm    = context.getSystemService<NotificationManager>() ?: return
        val prefs = context.getSharedPreferences(PREFS_NOTIF, Context.MODE_PRIVATE)

        // Migrate: delete all stale adhan channels when provider/config changed.
        if (prefs.getInt(K_CH_VERSION, 0) < CH_VERSION) {
            nm.notificationChannels
                .filter { it.id.startsWith("adhan_") }
                .forEach { nm.deleteNotificationChannel(it.id) }
            prefs.edit { putInt(K_CH_VERSION, CH_VERSION) }
        }

        // Silent channel
        nm.createNotificationChannel(
            NotificationChannel(
                CH_SILENT,
                context.getString(R.string.notif_channel_silent),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                setSound(null, null)
                enableVibration(false)
            }
        )

        // Device-default channel
        nm.createNotificationChannel(
            NotificationChannel(
                CH_DEVICE,
                context.getString(R.string.notif_channel_device),
                NotificationManager.IMPORTANCE_HIGH,
            )
        )

        // Pre-alert channel
        nm.createNotificationChannel(
            NotificationChannel(
                CH_PRE,
                context.getString(R.string.notif_channel_pre),
                NotificationManager.IMPORTANCE_HIGH,
            )
        )

        // One channel per asset file
        val audioAttr = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()

        assetFilenames.forEach { filename ->
            val id = channelId(AdhanSound.Asset(filename))
            if (nm.getNotificationChannel(id) != null) return@forEach
            nm.createNotificationChannel(
                NotificationChannel(
                    id,
                    filename.removeSuffix(".mp3"),
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply {
                    setSound(assetUri(context, filename), audioAttr)
                }
            )
        }

        // Legacy test channel
        nm.createNotificationChannel(
            NotificationChannel(
                CH_TEST,
                context.getString(R.string.notif_channel_test_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            )
        )
    }

    /**
     * Creates (or no-ops if already present) a notification channel for a user-picked
     * audio file. Call this immediately after the user selects a file, and also on
     * app startup for any already-saved [AdhanSound.Custom] configs (channels are
     * deleted on [CH_VERSION] bumps and must be recreated).
     */
    @RequiresApi(Build.VERSION_CODES.O)
    fun ensureCustomChannel(context: Context, uri: String, displayName: String) {
        val nm = context.getSystemService<NotificationManager>() ?: return
        val id = channelId(AdhanSound.Custom(uri, displayName))
        if (nm.getNotificationChannel(id) != null) return
        val audioAttr = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
        nm.createNotificationChannel(
            NotificationChannel(id, displayName, NotificationManager.IMPORTANCE_HIGH).apply {
                setSound(Uri.parse(uri), audioAttr)
            }
        )
    }

    // ── Post prayer notification ──────────────────────────────────────────────

    @RequiresApi(Build.VERSION_CODES.O)
    fun postAdhanNotification(
        context:    Context,
        prayerId:   Int,
        prayerName: String,
        timeMs:     Long,
        sound:      AdhanSound,
    ) {
        if (sound is AdhanSound.Off) return
        val nm = context.getSystemService<NotificationManager>() ?: return

        val formattedTime = android.text.format.DateFormat.getTimeFormat(context).format(java.util.Date(timeMs))
        val title = context.getString(R.string.notif_adhan_title, prayerName, formattedTime)
        val body  = context.getString(R.string.notif_adhan_body, prayerName)

        val notification = NotificationCompat.Builder(context, channelId(sound))
            .setSmallIcon(R.drawable.ic_stat_name)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            // Channel owns the sound for Asset and Device; only suppress for Silent.
            .setSilent(sound is AdhanSound.Silent)
            .build()

        nm.notify(mainNotifId(prayerId), notification)
    }

    // ── Post pre-alert notification ───────────────────────────────────────────

    @RequiresApi(Build.VERSION_CODES.O)
    fun postPreAlertNotification(
        context:       Context,
        prayerId:      Int,
        prayerName:    String,
        minutesBefore: Int,
    ) {
        val nm = context.getSystemService<NotificationManager>() ?: return
        val notification = NotificationCompat.Builder(context, CH_PRE)
            .setSmallIcon(R.drawable.ic_stat_name)
            .setContentTitle(prayerName)
            .setContentText(context.getString(R.string.notif_pre_alert_body, minutesBefore, prayerName))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        nm.notify(preNotifId(prayerId), notification)
    }

    // ── Legacy test notification ──────────────────────────────────────────────

    @RequiresApi(Build.VERSION_CODES.O)
    fun postTestNotification(context: Context) {
        val nm = context.getSystemService<NotificationManager>() ?: return
        val notification = NotificationCompat.Builder(context, CH_TEST)
            .setSmallIcon(R.drawable.ic_stat_name)
            .setContentTitle(context.getString(R.string.notif_test_title))
            .setContentText(context.getString(R.string.notif_test_body))
            .setAutoCancel(true)
            .build()
        nm.notify(NOTIF_TEST, notification)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Builds a content:// URI for an adhan asset file via [AdhanAssetProvider].
     * [Uri.Builder.appendPath] handles Arabic filenames and spaces safely.
     */
    fun assetUri(context: Context, filename: String): Uri =
        Uri.Builder()
            .scheme("content")
            .authority("${context.packageName}.adhan_provider")
            .appendPath(filename)
            .build()
}