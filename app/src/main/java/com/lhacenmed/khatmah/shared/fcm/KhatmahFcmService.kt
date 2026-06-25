package com.lhacenmed.khatmah.shared.fcm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.lhacenmed.khatmah.R
import com.lhacenmed.khatmah.core.MainActivity

/**
 * Handles incoming FCM messages and token refreshes.
 *
 * The edge function sends **data-only** messages (no `notification` block) so
 * this callback fires in every app state — foreground, background, and killed —
 * giving us full control over the icon, channel, and expanded layout.
 *
 * Token registration/upload is handled by [FcmTokenManager].
 */
@RequiresApi(Build.VERSION_CODES.O)
class KhatmahFcmService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        FcmTokenManager.upload(applicationContext, token)
    }

    override fun onMessageReceived(msg: RemoteMessage) {
        val data = msg.data

        when (data["type"]) {
            "app_update" -> handleUpdateNotif(data)
            else         -> handleTripRequest(data)
        }
    }

// ── Update notification ────────────────────────────────────────────────────────

    private fun handleUpdateNotif(data: Map<String, String>) {
        val versionName = data["versionName"]?.takeIf { it.isNotBlank() } ?: return
        val apkUrl      = data["apkUrl"]?.takeIf      { it.isNotBlank() } ?: return
        val notes       = data["notes"] ?: ""

        ensureChannel()

        val tapIntent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(apkUrl)).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val pi = PendingIntent.getActivity(
            this, UPDATE_NOTIF_ID, tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val title = getString(R.string.update_notif_title)                  // "Update available"
        val body  = getString(R.string.update_notif_body, versionName)      // "Version %s is ready — tap to download"

        val notif = NotificationCompat.Builder(this, CH_TRIPS)
            .setSmallIcon(R.drawable.ic_stat_name)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText(if (notes.isNotBlank()) "$body\n\n$notes" else body))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pi)
            .build()

        getSystemService<NotificationManager>()?.notify(UPDATE_NOTIF_ID, notif)
    }

// ── Trip request notification (extracted from onMessageReceived) ───────────────

    private fun handleTripRequest(data: Map<String, String>) {
        val requestId   = data["requestId"]
        val fullName    = data["fullName"]?.takeIf    { it.isNotBlank() } ?: return
        val destination = data["destination"]?.takeIf { it.isNotBlank() } ?: return
        val reasonKey   = data["reasonKey"]?.takeIf   { it.isNotBlank() }
        val description = data["description"]?.takeIf { it.isNotBlank() }

        ensureChannel()

        val tapIntent = Intent(this, MainActivity::class.java).apply {
            action = ACTION_TRIP_REQUEST
            requestId?.let { putExtra(EXTRA_REQUEST_ID, it) }
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pi = PendingIntent.getActivity(
            this, requestId.hashCode(), tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val collapsedBody = "$fullName → $destination"
        val expandedBody  = buildString {
            append(collapsedBody)
            reasonKey?.let   { append("\n${getString(R.string.trip_notif_reason)}: $it") }
            description?.let { append("\n${getString(R.string.trip_notif_description)}: $it") }
        }

        val notif = NotificationCompat.Builder(this, CH_TRIPS)
            .setSmallIcon(R.drawable.ic_stat_name)
            .setContentTitle(getString(R.string.trip_notif_title))
            .setContentText(collapsedBody)
            .setStyle(NotificationCompat.BigTextStyle().bigText(expandedBody))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pi)
            .build()

        getSystemService<NotificationManager>()?.notify(requestId.hashCode(), notif)
    }

    private fun ensureChannel() {
        val nm = getSystemService<NotificationManager>() ?: return
        if (nm.getNotificationChannel(CH_TRIPS) != null) return
        nm.createNotificationChannel(
            NotificationChannel(CH_TRIPS, getString(R.string.trip_notif_channel), NotificationManager.IMPORTANCE_HIGH),
        )
    }

    companion object {
        private const val CH_TRIPS          = "trip_requests"
        private const val UPDATE_NOTIF_ID   = -1
        const val ACTION_TRIP_REQUEST       = "com.lhacenmed.khatmah.TRIP_REQUEST"
        const val EXTRA_REQUEST_ID          = "requestId"
    }
}