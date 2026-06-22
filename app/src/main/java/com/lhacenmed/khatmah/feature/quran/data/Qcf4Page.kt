package com.lhacenmed.khatmah.feature.quran.data

import org.json.JSONObject

data class Qcf4Word(
    val char:     String,
    val font:     String,
    val text:     String,
    val type:     String,         // "word" | "end" | "surah_header" | "bismillah"
    val verseKey: String?,        // "sura:aya" — null for headers
    val sura:     Int?,
    val position: Int?,
)

data class Qcf4Line(
    val line:  Int,
    val words: List<Qcf4Word>,
)

data class Qcf4Page(
    val page:  Int,
    val font:  String,
    val lines: List<Qcf4Line>,
)

// ── JSON parsing ──────────────────────────────────────────────────────────────

internal fun JSONObject.toQcf4Page(): Qcf4Page {
    val linesArr = getJSONArray("lines")
    val lines = (0 until linesArr.length()).map { i ->
        val lineObj  = linesArr.getJSONObject(i)
        val wordsArr = lineObj.getJSONArray("words")
        Qcf4Line(
            line  = lineObj.getInt("line"),
            words = (0 until wordsArr.length()).map { j ->
                val w = wordsArr.getJSONObject(j)
                Qcf4Word(
                    char     = w.optString("char"),
                    font     = w.optString("font"),
                    text     = w.optString("text"),
                    type     = w.optString("type", "word"),
                    verseKey = w.optString("verse_key").ifEmpty { null },
                    // has() returns true even for JSON null values — guard with isNull()
                    sura     = if (w.has("sura")     && !w.isNull("sura"))     w.getInt("sura")     else null,
                    position = if (w.has("position") && !w.isNull("position")) w.getInt("position") else null,
                )
            },
        )
    }
    return Qcf4Page(page = getInt("page"), font = optString("font"), lines = lines)
}