package com.lhacenmed.khatmah.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService
import com.lhacenmed.khatmah.R

/**
 * Utility for posting test notifications.
 * Creates the required channel on first call — no-op if it already exists.
 */
object NotificationHelper {

    private const val TEST_CHANNEL_ID  = "channel_test"
    private const val TEST_NOTIF_ID    = 1001

    @RequiresApi(Build.VERSION_CODES.O)
    fun postTestNotification(context: Context) {
        val manager = context.getSystemService<NotificationManager>() ?: return

        val channel = NotificationChannel(
            TEST_CHANNEL_ID,
            context.getString(R.string.notif_channel_test_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        )
        manager.createNotificationChannel(channel)

        val notification = NotificationCompat.Builder(context, TEST_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_name)
            .setContentTitle(context.getString(R.string.notif_test_title))
            .setContentText(context.getString(R.string.notif_test_body))
            .setAutoCancel(true)
            .build()

        manager.notify(TEST_NOTIF_ID, notification)
    }
}