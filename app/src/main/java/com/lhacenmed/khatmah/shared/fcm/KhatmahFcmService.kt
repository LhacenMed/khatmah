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
        val data      = msg.data
        val requestId = data["requestId"]

        // Required fields — bail silently if missing.
        val fullName    = data["fullName"]?.takeIf { it.isNotBlank() } ?: return
        val destination = data["destination"]?.takeIf { it.isNotBlank() } ?: return

        val reasonKey   = data["reasonKey"]?.takeIf { it.isNotBlank() }
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

        // Collapsed line: "Lhacen → Ambassade"
        val collapsedBody = "$fullName → $destination"

        // Expanded body: adds reason and description when available.
        val expandedBody = buildString {
            append(collapsedBody)
            reasonKey?.let  { append("\n${getString(R.string.trip_notif_reason)}: $it") }
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

        getSystemService<NotificationManager>()
            ?.notify(requestId.hashCode(), notif)
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
        const val ACTION_TRIP_REQUEST       = "com.lhacenmed.khatmah.TRIP_REQUEST"
        const val EXTRA_REQUEST_ID          = "requestId"
    }
}