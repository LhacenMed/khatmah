package com.lhacenmed.khatmah.widget

import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.glance.appwidget.updateAll
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * Worker that triggers a [PrayerWidget] refresh.
 *
 * Two scheduling modes:
 *
 * **Periodic** ([enqueue]) — fires every 15 minutes via [WorkManager] to handle:
 *   - Prayer-time transitions (next prayer advancing after one passes).
 *   - New-day recalculation at midnight.
 *   - Settings or language changes applied while the app was closed.
 *
 * **One-shot** ([scheduleOneShot]) — fired at a precise delay by [PrayerWidget] when
 *   the widget enters "Since [prayer]" mode. Fires exactly when the 15-minute Since
 *   window closes so the label and Chronometer switch to the next prayer countdown
 *   automatically, without any app relaunch or manual widget refresh.
 *
 * The live second countdown is driven by [android.widget.Chronometer] inside the
 * widget layout — this worker is not needed for sub-second ticking.
 */
@RequiresApi(Build.VERSION_CODES.O)
class PrayerWidgetWorker(
    private val context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        PrayerWidget().updateAll(context)
        return Result.success()
    }

    companion object {
        private const val WORK_NAME          = "prayer_widget_refresh"
        private const val WORK_NAME_ONE_SHOT = "prayer_widget_refresh_once"

        /** Enqueues the recurring 15-minute refresh. Keeps any existing schedule. */
        fun enqueue(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<PrayerWidgetWorker>(15, TimeUnit.MINUTES)
                    .setConstraints(Constraints.NONE)
                    .build()
            )
        }

        /**
         * Schedules a single widget refresh after [delayMs] milliseconds.
         *
         * Uses [ExistingWorkPolicy.REPLACE] so that if [PrayerWidget.provideGlance]
         * is called again while a one-shot is already pending (e.g. due to a settings
         * change), the old request is cancelled and a fresh one with the correct delay
         * is enqueued.
         */
        fun scheduleOneShot(context: Context, delayMs: Long) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME_ONE_SHOT,
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<PrayerWidgetWorker>()
                    .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
                    .build()
            )
        }
    }
}