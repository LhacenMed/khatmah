package com.lhacenmed.khatmah.util

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.File
import java.io.FileNotFoundException

/**
 * Serves files from assets/adhan/ as content://<package>.adhan_provider/<filename> URIs.
 *
 * An exported ContentProvider is required for custom notification channel sounds on
 * API 26+ — the notification system process reads the URI directly and cannot access
 * FileProvider URIs (which require explicit per-caller URI grants).
 *
 * Files are cached in cacheDir on first access for reliable random-access reads by
 * the system media server. Adding a new sound = drop the .mp3 in assets/adhan/.
 */
class AdhanAssetProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        val filename = uri.lastPathSegment
            ?: throw FileNotFoundException("Missing filename in URI: $uri")
        val ctx = context ?: throw FileNotFoundException("Provider not attached")

        // Cached copy ensures reliable random-access reads by the system media server.
        val cached = File(ctx.cacheDir, "adhan_$filename")
        if (!cached.exists()) {
            ctx.assets.open("adhan/$filename").use { it.copyTo(cached.outputStream()) }
        }
        return ParcelFileDescriptor.open(cached, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun getType(uri: Uri): String = "audio/mpeg"

    // Unused stubs
    override fun query(u: Uri, p: Array<String>?, s: String?, a: Array<String>?, o: String?): Cursor? = null
    override fun insert(u: Uri, v: ContentValues?): Uri? = null
    override fun delete(u: Uri, s: String?, a: Array<String>?): Int = 0
    override fun update(u: Uri, v: ContentValues?, s: String?, a: Array<String>?): Int = 0
}