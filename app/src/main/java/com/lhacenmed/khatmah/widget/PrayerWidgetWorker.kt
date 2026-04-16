package com.lhacenmed.khatmah.widget

import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.glance.appwidget.updateAll
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * Periodic worker that refreshes the widget every 15 minutes.
 *
 * Handles:
 * - Prayer-time transitions (next prayer advancing after one passes).
 * - New-day recalculation at midnight.
 * - Settings or language changes applied while the app was closed.
 *
 * The live second countdown is driven by [android.widget.Chronometer] inside
 * the widget layout — this worker is not needed for that.
 */
@RequiresApi(Build.VERSION_CODES.O)
class PrayerWidgetWorker(
    private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        PrayerWidget().updateAll(context)
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "prayer_widget_refresh"

        fun enqueue(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<PrayerWidgetWorker>(15, TimeUnit.MINUTES)
                    .setConstraints(Constraints.NONE)
                    .build()
            )
        }
    }
}