package com.lhacenmed.khatmah.feature.update

import android.content.Context
import androidx.core.content.pm.PackageInfoCompat
import com.lhacenmed.khatmah.BuildConfig
import com.lhacenmed.khatmah.shared.util.SpeedTracker
import com.lhacenmed.khatmah.shared.util.formatDownloadLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.yield
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Stateless engine that streams one APK to the cache, emitting [UpdateState] as a cold [Flow] —
 * [UpdateService] collects it on a background scope so the download outlives the dialog. Mirrors
 * [com.lhacenmed.khatmah.feature.quran.data.download.MushafDownloader]'s byte-level streaming,
 * minus the extraction/DB phases since an APK needs none.
 */
object ApkDownloader {

    /** Where the downloaded APK lands. Stable name so a re-download overwrites the last attempt. */
    fun apkFile(context: Context): File =
        File(context.cacheDir, "updates").apply { mkdirs() }.resolve("khatmah-update.apk")

    /**
     * Deletes the staged APK once it is no longer needed: after a successful install the running
     * build's versionCode has caught up to (or passed) the staged one, so it is safe to remove. A
     * newer, not-yet-installed APK is kept. Called on launch — never mid-install, since the system
     * installer runs in its own process and finishes long before the app is next started. Cheap
     * no-op when nothing is staged.
     */
    fun clearStaleApk(context: Context) {
        val apk = apkFile(context)
        if (!apk.exists()) return
        val staged = context.packageManager.getPackageArchiveInfo(apk.path, 0)
            ?.let { PackageInfoCompat.getLongVersionCode(it) } ?: 0L
        if (staged <= BuildConfig.VERSION_CODE) apk.delete()
    }

    fun download(context: Context, update: AppUpdate): Flow<UpdateState> = flow {
        val ctx = context.applicationContext
        val apk = apkFile(ctx)
        apk.delete()

        emit(UpdateState.Connecting)

        val speed = SpeedTracker()
        try {
            val conn = openWithRedirects(update.apkUrl)
            val totalBytes = conn.contentLengthLong.takeIf { it > 0 }
            var received = 0L
            try {
                conn.inputStream.use { input ->
                    apk.outputStream().use { out ->
                        val buf = ByteArray(65_536)
                        var n: Int
                        while (input.read(buf).also { n = it } != -1) {
                            yield()
                            out.write(buf, 0, n)
                            received += n
                            speed.add(n.toLong())
                            emit(UpdateState.Downloading(
                                progress = totalBytes?.let { (received.toFloat() / it).coerceIn(0f, 1f) },
                                log = formatDownloadLog(speed.bytesPerSec(), received, totalBytes),
                            ))
                        }
                    }
                }
            } finally {
                conn.disconnect()
            }
        } catch (e: CancellationException) {
            apk.delete(); throw e
        } catch (e: Exception) {
            apk.delete()
            emit(UpdateState.Error("Download failed: ${e.message}")); return@flow
        }

        emit(UpdateState.Downloaded(apk))
    }.flowOn(Dispatchers.IO)

    /**
     * Opens a connection to [url], manually following cross-host redirects (Android's
     * HttpURLConnection only auto-follows same-host ones — GitHub release assets redirect to a CDN).
     */
    private fun openWithRedirects(url: String): HttpURLConnection {
        var location = url
        repeat(5) {
            val conn = (URL(location).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = 30_000
                readTimeout = 30_000
            }
            conn.connect()
            if (conn.responseCode in 300..399) {
                val next = conn.getHeaderField("Location")
                conn.disconnect()
                if (next != null) { location = next; return@repeat }
            }
            return conn
        }
        error("Too many redirects for $url")
    }
}
