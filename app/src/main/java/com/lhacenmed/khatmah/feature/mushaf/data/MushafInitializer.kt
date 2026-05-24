package com.lhacenmed.khatmah.feature.mushaf.data

import android.content.Context
import androidx.core.content.edit
import com.lhacenmed.khatmah.feature.mushaf.data.db.MushafDb
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.sevenz.SevenZFile
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Seeds [MushafDb] from the bundled `assets/quran.7z` on first launch.
 *
 * The archive contains `hafs.json` and `warsh.json`. Each is parsed once and
 * stored in [MushafDb] under its riwaya key. Idempotent — skips any riwaya
 * that has already been seeded (checked via SharedPreferences version stamp).
 *
 * Called from [App.onCreate] on the background [appScope].
 */
object MushafInitializer {

    private const val PREFS    = "mushaf_init"
    private const val ARCHIVE  = "quran.7z"

    fun init(context: Context, scope: CoroutineScope) {
        scope.launch {
            val riwayat = listOf(Riwaya.HAFS, Riwaya.WARSH)
            for (riwaya in riwayat) {
                seedIfNeeded(context, riwaya)
            }
        }
    }

    private suspend fun seedIfNeeded(context: Context, riwaya: Riwaya) = withContext(Dispatchers.IO) {
        val prefs          = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val key            = riwaya.dbKey
        val seededVersion  = prefs.getInt("${key}_version", 0)

        // Extract JSON from archive
        val json = extractEntry(context, "$key.json") ?: run {
            android.util.Log.e("MushafInit", "Entry $key.json not found in $ARCHIVE")
            return@withContext
        }

        val meta = runCatching { JSONObject(json).toMushafMeta() }.getOrElse {
            android.util.Log.e("MushafInit", "Failed to parse $key.json", it)
            return@withContext
        }

        if (seededVersion >= meta.version) return@withContext   // already up-to-date

        val dao = MushafDb.get(context).dao()
        // Clear stale data before re-seeding on version bump
        dao.clearSurahs(key)
        dao.clearDivisions(key)
        dao.clearSajdaat(key)
        dao.clearPageStarts(key)
        dao.clearVerses(key)

        dao.insertRiwayaData(
            surahs     = meta.surahs.map { it.toEntity(key) },
            divisions  = meta.toDivisionEntities(key),
            sajdaat    = meta.sajdaat.map { it.toEntity(key) },
            pageStarts = meta.pageStarts.map { it.toEntity(key) },
            verses     = meta.verses.map { it.toEntity(key) },
        )

        prefs.edit { putInt("${key}_version", meta.version) }
    }

    /**
     * Copies [ARCHIVE] from assets to a temp file, reads the named [entryName],
     * returns its content as a UTF-8 string, then deletes the temp file.
     */
    private fun extractEntry(context: Context, entryName: String): String? {
        val tmp = File(context.cacheDir, ARCHIVE)
        return try {
            context.assets.open(ARCHIVE).use { it.copyTo(tmp.outputStream()) }
            SevenZFile(tmp).use { sz ->
                var entry = sz.nextEntry
                while (entry != null) {
                    if (entry.name == entryName) {
                        val out = ByteArrayOutputStream()
                        val buf = ByteArray(8192)
                        var n: Int
                        while (sz.read(buf).also { n = it } != -1) out.write(buf, 0, n)
                        return@use out.toString(Charsets.UTF_8.name())
                    }
                    entry = sz.nextEntry
                }
                null
            }
        } catch (e: Exception) {
            android.util.Log.e("MushafInit", "Archive extraction failed", e)
            null
        } finally {
            tmp.delete()
        }
    }
}