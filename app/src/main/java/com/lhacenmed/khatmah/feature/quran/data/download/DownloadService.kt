package com.lhacenmed.khatmah.feature.quran.data.download

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.app.TaskStackBuilder
import androidx.core.content.ContextCompat
import com.lhacenmed.khatmah.R
import com.lhacenmed.khatmah.core.MainActivity
import com.lhacenmed.khatmah.core.ScreenHostActivity
import com.lhacenmed.khatmah.core.nav.Dest
import com.lhacenmed.khatmah.feature.quran.data.Riwaya
import com.lhacenmed.khatmah.shared.util.LocaleManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach

/**
 * Foreground service that runs one QCF4 download to completion, independently of the UI: it
 * survives the print screen closing and the app being backgrounded. Progress is published to
 * [DownloadRegistry] (for the UI) and mirrored to a notification with a Stop action (for the shade).
 *
 * One download at a time — two large bundles importing at once would only fight over IO. A start
 * request while busy is ignored; the active download keeps running.
 */
class DownloadService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // Read on the main thread (onStartCommand) and written from the flow (Default dispatcher),
    // so both are @Volatile. [active] is the authority on which riwaya owns the notification and
    // registry: clearing it instantly disowns a cancelled job's late, in-flight emissions.
    @Volatile private var job: Job? = null
    @Volatile private var active: Riwaya? = null

    /** Wrap the base context so notification text follows the app locale, not the system one. */
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(LocaleManager.applyTo(base))
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val riwaya = intent?.getStringExtra(EXTRA_RIWAYA)
            ?.let { runCatching { Riwaya.valueOf(it) }.getOrNull() }
            ?: run { stopSelf(); return START_NOT_STICKY }

        // startForegroundService() requires startForeground() within 5s on every delivery — so
        // promote first, showing whichever download is (or is about to be) active.
        val shown = active ?: riwaya
        goForeground(progressNotification(shown, DownloadRegistry.stateOf(shown)))

        when {
            intent.action == ACTION_CANCEL -> cancelDownload(riwaya)
            job?.isActive == true          -> Unit              // busy — ignore the new request
            else                           -> startDownload(riwaya)
        }
        return START_STICKY
    }

    private fun startDownload(riwaya: Riwaya) {
        active = riwaya
        DownloadRegistry.update(riwaya, DownloadState.Connecting)
        job = MushafDownloader.download(applicationContext, riwaya.config)
            .onEach { state ->
                // Drop emissions from a download that was cancelled mid-flight: without this, the
                // last in-flight progress could re-write the registry and re-post the notification
                // right after Stop, leaving a stuck "paused" state.
                if (active !== riwaya) return@onEach
                DownloadRegistry.update(riwaya, state)
                if (state == DownloadState.Connecting || state is DownloadState.Downloading) {
                    notify(NOTIF_PROGRESS, progressNotification(riwaya, state))
                }
            }
            .onCompletion { cause ->
                active = null; job = null
                // Normal completion (Downloaded/Error) leaves a dismissible result; a cancellation
                // (cause != null) leaves nothing behind.
                if (cause == null) postResult(riwaya)
                stopNow()
            }
            .launchIn(scope)
    }

    private fun cancelDownload(riwaya: Riwaya) {
        when (active) {
            riwaya -> {
                active = null                 // disown late emissions before they can re-post
                job?.cancel(); job = null
                DownloadRegistry.update(riwaya, DownloadState.NotDownloaded)
                stopNow()
            }
            null   -> stopNow()               // nothing running — just drop the placeholder notif
            else   -> Unit                    // a different download is active; leave it alone
        }
    }

    /** Removes the ongoing notification and stops the service. Safe to call more than once. */
    private fun stopNow() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    // ── Notifications ─────────────────────────────────────────────────────────

    private fun postResult(riwaya: Riwaya) {
        when (val s = DownloadRegistry.stateOf(riwaya)) {
            DownloadState.Downloaded -> notify(NOTIF_RESULT, result(riwaya, getString(R.string.download_notif_done)))
            is DownloadState.Error   -> notify(NOTIF_RESULT, result(riwaya, s.message))
            else                     -> Unit
        }
    }

    private fun base(riwaya: Riwaya): NotificationCompat.Builder {
        ensureChannel()
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_name)
            .setContentTitle(getString(R.string.download_notif_title, getString(riwaya.nameRes)))
            .setContentIntent(openPrintsIntent())
            .setOnlyAlertOnce(true)
            .setSilent(true)
    }

    private fun progressNotification(riwaya: Riwaya, state: DownloadState): Notification {
        val log = when (state) {
            is DownloadState.Downloading -> state.log.ifBlank { getString(R.string.print_processing) }
            else                         -> getString(R.string.print_connecting)
        }
        val progress = (state as? DownloadState.Downloading)?.progress
        return base(riwaya)
            .setContentText(log)
            .setOngoing(true)
            .setProgress(100, ((progress ?: 0f) * 100).toInt(), progress == null)
            .addAction(0, getString(R.string.print_stop), cancelIntent(riwaya))
            .build()
    }

    private fun result(riwaya: Riwaya, text: String): Notification =
        base(riwaya).setContentText(text).setAutoCancel(true).build()

    /** Opens the print-select screen, with the app home synthesised beneath it for natural back. */
    private fun openPrintsIntent(): PendingIntent {
        val host = Intent(this, ScreenHostActivity::class.java)
            .putExtra(ScreenHostActivity.EXTRA_DEST, Dest.MushafPrints)
        return TaskStackBuilder.create(this)
            .addNextIntent(Intent(this, MainActivity::class.java))
            .addNextIntent(host)
            .getPendingIntent(1, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)!!
    }

    private fun cancelIntent(riwaya: Riwaya): PendingIntent {
        val intent = Intent(this, DownloadService::class.java)
            .setAction(ACTION_CANCEL)
            .putExtra(EXTRA_RIWAYA, riwaya.name)
        return PendingIntent.getService(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, getString(R.string.download_notif_channel),
                    NotificationManager.IMPORTANCE_LOW).apply { setShowBadge(false) }
            )
        }
    }

    private fun notify(id: Int, notification: Notification) {
        val nm = NotificationManagerCompat.from(this)
        if (nm.areNotificationsEnabled()) nm.notify(id, notification)
    }

    private fun goForeground(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(
                this, NOTIF_PROGRESS, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            startForeground(NOTIF_PROGRESS, notification)
        }
    }

    companion object {
        private const val CHANNEL = "mushaf_download"
        private const val NOTIF_PROGRESS = 4100
        private const val NOTIF_RESULT = 4101
        private const val EXTRA_RIWAYA = "riwaya"
        private const val ACTION_CANCEL = "com.lhacenmed.khatmah.action.CANCEL_DOWNLOAD"

        /** Starts the download for [riwaya] (no-op if one is already running). */
        fun start(context: Context, riwaya: Riwaya) {
            val intent = Intent(context, DownloadService::class.java).putExtra(EXTRA_RIWAYA, riwaya.name)
            ContextCompat.startForegroundService(context, intent)
        }

        /** Requests cancellation of the in-flight download for [riwaya]. */
        fun cancel(context: Context, riwaya: Riwaya) {
            val intent = Intent(context, DownloadService::class.java)
                .setAction(ACTION_CANCEL)
                .putExtra(EXTRA_RIWAYA, riwaya.name)
            ContextCompat.startForegroundService(context, intent)
        }
    }
}
