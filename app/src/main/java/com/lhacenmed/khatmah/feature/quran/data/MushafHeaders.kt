package com.lhacenmed.khatmah.feature.quran.data

import com.lhacenmed.khatmah.feature.quran.data.db.HeaderGlyphEntity
import org.json.JSONObject

/**
 * Parser for a riwaya's `headers.json` — the running-head glyph map downloaded with the QCF4
 * assets. It carries two QCF4_QBSML glyph sets, both BMP Private-Use (one `Char` each):
 *
 *  - `pages`: a run-length map of the per-page sura running head. Each entry sets the glyph(s) from
 *    its `page` onward until the next entry, so the reader resolves any page with a floor lookup.
 *    `codes` is usually one glyph but may be several (drawn in order) for a shared page the font has
 *    no single glyph for. Stored as [HeaderGlyphType.PAGE] rows keyed by the entry's start page.
 *  - `juz`: one glyph per juz number, stored as [HeaderGlyphType.JUZ] rows keyed by juz number.
 *
 * Flattened to [HeaderGlyphEntity] rows keyed by [partitionKey] (the riwaya's `wordKey`), ready
 * for [com.lhacenmed.khatmah.feature.quran.data.db.MushafDao.insertHeaderGlyphs].
 */
object HeaderGlyphType {
    const val PAGE = "page"
    const val JUZ  = "juz"
}

internal fun JSONObject.toHeaderGlyphEntities(partitionKey: String): List<HeaderGlyphEntity> {
    val out = ArrayList<HeaderGlyphEntity>(128)

    val pages = getJSONArray("pages")
    for (i in 0 until pages.length()) {
        val o = pages.getJSONObject(i)
        val codes = o.getJSONArray("codes")
        val char = buildString { for (c in 0 until codes.length()) append(codes.getInt(c).toChar()) }
        out += HeaderGlyphEntity(partitionKey, HeaderGlyphType.PAGE, o.getInt("page"), char)
    }

    val juz = getJSONArray("juz")
    for (i in 0 until juz.length()) {
        val o = juz.getJSONObject(i)
        out += HeaderGlyphEntity(partitionKey, HeaderGlyphType.JUZ, o.getInt("num"), o.getInt("code").toChar().toString())
    }

    return out
}
