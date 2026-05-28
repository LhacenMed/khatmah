package com.lhacenmed.khatmah.feature.quran.data

import android.content.Context
import com.lhacenmed.khatmah.shared.drive.DriveAuth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.yield
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.atomic.AtomicInteger

// ── Download state ────────────────────────────────────────────────────────────

sealed class WarshImageDownloadState {
    object NotDownloaded                              : WarshImageDownloadState()
    object Connecting                                 : WarshImageDownloadState()
    data class Downloading(val progress: Float)       : WarshImageDownloadState()
    object Downloaded                                 : WarshImageDownloadState()
    data class Error(val message: String)             : WarshImageDownloadState()
}

// ── Repository ────────────────────────────────────────────────────────────────

/**
 * Manages parallel download and local caching of the Warsh mushaf JPG pages.
 *
 * Drive layout:
 *   Warsh-images folder — 604 JPEG files (001.jpg … 604.jpg).
 *
 * Cache layout: context.filesDir/warsh-images/001.jpg … 604.jpg
 *
 * Download strategy mirrors [WarshXmlRepository]: parallel coroutines capped at
 * [CONCURRENCY], atomic write via .tmp rename, idempotent (skips existing files).
 */
class WarshImageRepository(private val context: Context) {

    private val dir = File(context.filesDir, "warsh-images")

    private val _downloadState = MutableStateFlow<WarshImageDownloadState>(
        if (isFullyDownloaded()) WarshImageDownloadState.Downloaded
        else WarshImageDownloadState.NotDownloaded
    )
    val downloadState = _downloadState.asStateFlow()

    // ── Public API ────────────────────────────────────────────────────────────

    /** True when all 604 page JPEGs are cached on disk. */
    fun isFullyDownloaded(): Boolean {
        if (!dir.exists()) return false
        return (1..PAGE_COUNT).all { pageFile(it).exists() }
    }

    /** Resets download state to [NotDownloaded] without touching disk. */
    fun resetState() {
        _downloadState.value = WarshImageDownloadState.NotDownloaded
    }

    /** Returns the on-disk path for [pageNum] (1-based). */
    fun pageFile(pageNum: Int): File = File(dir, "%03d.jpg".format(pageNum))

    /**
     * Downloads all 604 pages from Google Drive fresh every time (no resume).
     * Clears the cache directory before starting so no stale files remain.
     * Emits [WarshImageDownloadState] updates and mirrors them to [downloadState].
     */
    fun downloadAll(): Flow<WarshImageDownloadState> = flow {
        emit(WarshImageDownloadState.Connecting)
        _downloadState.value = WarshImageDownloadState.Connecting

        val token = runCatching { DriveAuth.token(context) }.getOrElse {
            val err = WarshImageDownloadState.Error("Auth failed: ${it.message}")
            emit(err); _downloadState.value = err; return@flow
        }

        val fileMap = runCatching { fetchFolderFiles(token) }.getOrElse {
            val err = WarshImageDownloadState.Error("Index failed: ${it.message}")
            emit(err); _downloadState.value = err; return@flow
        }

        if (fileMap.isEmpty()) {
            val err = WarshImageDownloadState.Error("Drive folder returned no files")
            emit(err); _downloadState.value = err; return@flow
        }

        // Fresh start — clear any previous files before writing new ones.
        dir.deleteRecursively()
        dir.mkdirs()

        val total     = fileMap.size
        val completed = AtomicInteger(0)

        fun progress() = (completed.get().toFloat() / total).coerceIn(0f, 1f)

        val initState = WarshImageDownloadState.Downloading(progress())
        emit(initState); _downloadState.value = initState

        var encounteredError: WarshImageDownloadState.Error? = null
        val semaphore = Semaphore(CONCURRENCY)

        coroutineScope {
            fileMap.map { (name, fileId) ->
                async(Dispatchers.IO) {
                    val dest = File(dir, name)
                    try {
                        semaphore.withPermit {
                            yield()
                            download(fileId, token, dest)
                        }
                        if (isActive) {
                            completed.incrementAndGet()
                            _downloadState.value = WarshImageDownloadState.Downloading(progress())
                        }
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        if (isActive) {
                            encounteredError = WarshImageDownloadState.Error(
                                "Failed ($name): ${e.message}"
                            )
                        }
                    }
                }
            }.awaitAll()
        }

        yield()
        encounteredError?.let {
            emit(it); _downloadState.value = it; return@flow
        }

        emit(WarshImageDownloadState.Downloaded)
        _downloadState.value = WarshImageDownloadState.Downloaded
    }.flowOn(Dispatchers.IO)

    // ── Drive helpers ─────────────────────────────────────────────────────────

    /** Lists all .jpg files in the Drive folder. Returns filename → fileId. */
    private fun fetchFolderFiles(token: String): Map<String, String> {
        val files     = mutableMapOf<String, String>()
        var pageToken: String? = null
        do {
            val url = buildString {
                append("https://www.googleapis.com/drive/v3/files")
                append("?q=${enc("'$FOLDER_ID' in parents and trashed=false")}")
                append("&fields=${enc("nextPageToken,files(id,name)")}")
                append("&pageSize=1000")
                if (pageToken != null) append("&pageToken=${enc(pageToken)}")
            }
            val resp  = getJson(url, token)
            val arr   = resp.getJSONArray("files")
            pageToken = resp.optString("nextPageToken").ifEmpty { null }
            for (i in 0 until arr.length()) {
                val obj  = arr.getJSONObject(i)
                val name = obj.getString("name")
                if (name.endsWith(".jpg")) files[name] = obj.getString("id")
            }
        } while (pageToken != null)
        return files
    }

    /** Downloads [fileId] directly (no decompression). Atomic write via .tmp rename. */
    private suspend fun download(fileId: String, token: String, dest: File) {
        val tmp  = File(dest.parent, "${dest.name}.tmp")
        val conn = openConn(fileId, token)
        try {
            conn.inputStream.use { input ->
                tmp.outputStream().use { output ->
                    input.copyToInterruptible(output)
                }
            }
            yield() // Final check before committing the file
            tmp.renameTo(dest)
        } catch (e: Exception) {
            tmp.delete()
            throw e
        } finally {
            conn.disconnect()
        }
    }

    /** Custom copyTo that respects coroutine cancellation. */
    private suspend fun InputStream.copyToInterruptible(out: OutputStream) {
        val buffer = ByteArray(8192)
        var bytes = read(buffer)
        while (bytes >= 0) {
            yield()
            out.write(buffer, 0, bytes)
            bytes = read(buffer)
        }
    }

    private fun openConn(fileId: String, token: String): HttpURLConnection =
        (URL("https://www.googleapis.com/drive/v3/files/$fileId?alt=media")
            .openConnection() as HttpURLConnection).apply {
            setRequestProperty("Authorization", "Bearer $token")
            connectTimeout = 15_000
            readTimeout    = 0
            connect()
        }

    private fun getJson(url: String, token: String): org.json.JSONObject {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            setRequestProperty("Authorization", "Bearer $token")
            connectTimeout = 15_000
            readTimeout    = 30_000
            connect()
        }
        return org.json.JSONObject(conn.inputStream.bufferedReader().readText())
            .also { conn.disconnect() }
    }

    fun clearCache() {
        dir.listFiles()?.forEach { it.delete() }
        _downloadState.value = WarshImageDownloadState.NotDownloaded
    }

    private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")

    // ── Constants ─────────────────────────────────────────────────────────────

    companion object {
        const val PAGE_COUNT      = 604
        private const val CONCURRENCY = 8
        private const val FOLDER_ID   = "10-FYiWO1UgCfprKeh2WRqcimklHW37X5"

        @Volatile private var instance: WarshImageRepository? = null

        /** Returns the process-scoped singleton — state survives ViewModel recreation. */
        fun get(context: Context): WarshImageRepository = instance ?: synchronized(this) {
            instance ?: WarshImageRepository(context.applicationContext).also { instance = it }
        }
    }
}