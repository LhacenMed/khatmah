package com.lhacenmed.khatmah.feature.quran.data.download

import android.content.Context
import androidx.core.content.edit
import com.lhacenmed.khatmah.feature.quran.data.Qcf4Page
import com.lhacenmed.khatmah.feature.quran.data.Qcf4Repository
import com.lhacenmed.khatmah.feature.quran.data.RiwayaConfig
import com.lhacenmed.khatmah.feature.quran.data.db.MushafDb
import com.lhacenmed.khatmah.feature.quran.data.db.PageEntity
import com.lhacenmed.khatmah.feature.quran.data.db.VersePage
import com.lhacenmed.khatmah.feature.quran.data.db.WordEntity
import com.lhacenmed.khatmah.feature.quran.data.toQcf4Page
import com.lhacenmed.khatmah.shared.util.SpeedTracker
import com.lhacenmed.khatmah.shared.util.formatDownloadLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.yield
import org.apache.commons.compress.archivers.sevenz.SevenZFile
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Stateless engine that downloads and installs one riwaya's QCF4 assets. Emits [DownloadState]
 * as a cold [Flow] — the [DownloadService] collects it on a background scope so the work outlives
 * the UI. Reading the installed result is [Qcf4Repository]'s job; this only writes.
 *
 * The single .7z bundle ([RiwayaConfig.bundleUrl]) contains:
 *   fonts/ — TTF glyph files written to [RiwayaConfig.fontsDir];
 *   pages/ — JSON parsed into [MushafDb] (mushaf_page / mushaf_word), then a verse→page index.
 * Always a fresh, no-resume download: any previous partial data is cleared first.
 */
object MushafDownloader {

    fun download(context: Context, config: RiwayaConfig): Flow<DownloadState> = flow {
        val ctx = context.applicationContext
        val dao = MushafDb.get(ctx).dao()
        val fontsDir = config.fontsDir(ctx)
        val prefs = config.prefs(ctx)

        emit(DownloadState.Connecting)

        // Fresh start — discard any previous partial data before writing new.
        prefs.edit { putBoolean(RiwayaConfig.KEY_DB_READY, false) }
        Qcf4Repository.get(ctx, config.riwaya).invalidateTypefaces()
        fontsDir.deleteRecursively()
        fontsDir.mkdirs()
        dao.clearPages(config.wordKey)
        dao.clearWords(config.wordKey)
        dao.clearVersePages(config.wordKey)

        val tmpBundle = File(ctx.cacheDir, "${config.wordKey}_qcf4_bundle.7z")
        tmpBundle.delete()

        // ── Phase 1: Stream bundle to disk with byte-level progress ───────────
        val speed = SpeedTracker()
        try {
            val conn = openWithRedirects(config.bundleUrl)
            val totalBytes = conn.contentLengthLong.takeIf { it > 0 }
            var received = 0L
            try {
                conn.inputStream.use { input ->
                    tmpBundle.outputStream().use { out ->
                        val buf = ByteArray(65_536)
                        var n: Int
                        while (input.read(buf).also { n = it } != -1) {
                            yield()
                            out.write(buf, 0, n)
                            received += n
                            speed.add(n.toLong())
                            emit(DownloadState.Downloading(
                                progress = totalBytes?.let { (received.toFloat() / it).coerceIn(0f, 0.99f) },
                                log = formatDownloadLog(speed.bytesPerSec(), received, totalBytes),
                            ))
                        }
                    }
                }
            } finally {
                conn.disconnect()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            tmpBundle.delete()
            emit(DownloadState.Error("Download failed: ${e.message}")); return@flow
        }

        // ── Phase 2: Extract fonts to disk; parse and insert page JSONs ────────
        var sz: SevenZFile? = null
        var pagesDone = 0
        try {
            emit(DownloadState.Downloading(null, "Extracting fonts..."))
            sz = SevenZFile(tmpBundle)
            var entry = sz.nextEntry
            while (entry != null) {
                yield()
                if (!entry.isDirectory) {
                    val name = entry.name.replace('\\', '/')
                    when {
                        name.startsWith("fonts/") && name.endsWith(".ttf") -> {
                            val dest = File(fontsDir, name.removePrefix("fonts/"))
                            val tmp = File(fontsDir, "${dest.name}.tmp")
                            tmp.outputStream().use { out ->
                                val buf = ByteArray(8_192); var n: Int
                                while (sz.read(buf).also { n = it } != -1) { yield(); out.write(buf, 0, n) }
                            }
                            tmp.renameTo(dest)
                        }
                        name.startsWith("pages/") && name.endsWith(".json") -> {
                            val baos = ByteArrayOutputStream()
                            val buf = ByteArray(8_192); var n: Int
                            while (sz.read(buf).also { n = it } != -1) { yield(); baos.write(buf, 0, n) }
                            runCatching {
                                val page = JSONObject(baos.toString("UTF-8")).toQcf4Page()
                                dao.insertPageWithWords(
                                    PageEntity(config.wordKey, page.page, page.font),
                                    buildWordEntities(config.wordKey, page),
                                )
                            }
                            pagesDone++
                            if ((pagesDone % 60 == 0 || pagesDone == RiwayaConfig.PAGE_COUNT) &&
                                currentCoroutineContext().isActive
                            ) {
                                emit(DownloadState.Downloading(
                                    null, "Importing pages: $pagesDone / ${RiwayaConfig.PAGE_COUNT}",
                                ))
                            }
                        }
                    }
                }
                entry = sz.nextEntry
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emit(DownloadState.Error("Extract failed: ${e.message}")); return@flow
        } finally {
            runCatching { sz?.close() }
            runCatching { tmpBundle.delete() }
        }

        // Rebuild verse→page index from all words now in DB.
        emit(DownloadState.Downloading(null, "Building verse index..."))
        rebuildVersePages(dao, config.wordKey)
        prefs.edit { putBoolean(RiwayaConfig.KEY_DB_READY, true) }
        emit(DownloadState.Downloaded)
    }.flowOn(Dispatchers.IO)

    /**
     * Opens a connection to [url], manually following cross-host redirects
     * (Android's HttpURLConnection only auto-follows same-host redirects).
     */
    private fun openWithRedirects(url: String): HttpURLConnection {
        var location = url
        repeat(5) {
            val conn = (URL(location).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = 30_000
                readTimeout = 0
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

    /**
     * Derives the aya→page index from all words in DB (MIN page per aya, so long ayas that
     * span pages map to their first page) and inserts into mushaf_verse_page.
     */
    private suspend fun rebuildVersePages(dao: com.lhacenmed.khatmah.feature.quran.data.db.MushafDao, wordKey: String) {
        val verseMap = HashMap<Long, Int>(6400)
        for (row in dao.verseKeyRows(wordKey)) {
            val aya = row.verseKey.substringAfter(':').toIntOrNull() ?: continue
            val key = row.sura.toLong() shl 32 or aya.toLong()
            verseMap.merge(key, row.pageNum) { old, new -> minOf(old, new) }
        }
        val pages = verseMap.entries.map { (key, pageNum) ->
            VersePage(wordKey, (key ushr 32).toInt(), (key and 0xFFFFFFFFL).toInt(), pageNum)
        }
        dao.clearVersePages(wordKey)
        if (pages.isNotEmpty()) dao.insertVersePages(pages)
    }

    /** Flattens a [Qcf4Page] into [WordEntity] rows ready for DB insertion. */
    private fun buildWordEntities(wordKey: String, page: Qcf4Page): List<WordEntity> {
        val entities = ArrayList<WordEntity>(page.lines.sumOf { it.words.size })
        page.lines.forEachIndexed { lineIdx, line ->
            line.words.forEachIndexed { wordIdx, word ->
                entities += WordEntity(
                    riwaya = wordKey,
                    pageNum = page.page,
                    lineIdx = lineIdx,
                    lineNum = line.line,
                    wordIdx = wordIdx,
                    char = word.char,
                    font = word.font,
                    text = word.text,
                    type = word.type,
                    verseKey = word.verseKey,
                    // Derive sura from verse_key when the JSON omits the sura field.
                    sura = word.sura ?: word.verseKey?.substringBefore(':')?.toIntOrNull(),
                    position = word.position,
                )
            }
        }
        return entities
    }
}
