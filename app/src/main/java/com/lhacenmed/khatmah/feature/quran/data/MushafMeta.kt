package com.lhacenmed.khatmah.feature.quran.data

import com.lhacenmed.khatmah.feature.quran.data.db.DivisionEntity
import com.lhacenmed.khatmah.feature.quran.data.db.PageStartEntity
import com.lhacenmed.khatmah.feature.quran.data.db.SajdaEntity
import com.lhacenmed.khatmah.feature.quran.data.db.SurahEntity
import com.lhacenmed.khatmah.feature.quran.data.db.VerseEntity
import org.json.JSONObject

// ── Division type constants ───────────────────────────────────────────────────

/** Values for [DivisionEntity.type]. Hizb is derived: hizb_num = ceil(rub_num / 4). */
object DivType {
    const val JUZ   = "juz"
    const val RUB   = "rub"    // rub' al-hizb (1/4 hizb)
    const val THUMN = "thumn"  // thumn al-hizb (1/8 hizb) — Warsh masahif only
}

// ── Arabic normalizer (used for search pre-indexing) ──────────────────────────

/**
 * Strips diacritics and unifies visually equivalent Arabic characters for search.
 * Handles both standard and Uthmani script variants.
 */
internal fun String.normalizeArabic(): String {
    val sb = StringBuilder(length)
    for (c in this) {
        // Strip harakat (U+064B–U+065F), kashida (U+0640), superscript alef (U+0670),
        // Quranic annotation signs (U+06D6–U+06EF)
        if (c in '\u064B'..'\u065F' || c == '\u0640' || c == '\u0670' ||
            c in '\u06D6'..'\u06EF') continue
        sb.append(when (c) {
            'أ', 'إ', 'آ', '\u0671' -> 'ا'   // ٱ (hamza wasl) → ا
            'ؤ'                      -> 'و'
            'ئ'                      -> 'ي'
            'ة'                      -> 'ه'
            'ى'                      -> 'ي'
            else                     -> c
        })
    }
    return sb.toString()
}

// ── JSON models ───────────────────────────────────────────────────────────────

data class MushafMeta(
    val riwaya:     String,
    val version:    Int,
    val bismillah:  String,
    val surahs:     List<MetaSurah>,
    val verses:     List<MetaVerse>,
    val juzaa:      List<MetaMarker>,    // 30 juz' start positions
    val ruba3:      List<MetaMarker>,    // 240 rub' al-hizb start positions
    val athmaan:    List<MetaMarker>,    // thumn al-hizb (Warsh only; empty for Hafs)
    val sajdaat:    List<MetaSajda>,
    val pageStarts: List<MetaPageStart>,
)

data class MetaSurah(
    val num:             Int,
    val name:            String,
    val nameEn:          String,
    val nameComplex:     String,
    val translatedName:  String,
    val type:            String,
    val revelationOrder: Int,
    val ayat:            Int,
    val bismillahPre:    Boolean,
)

data class MetaVerse(val sura: Int, val aya: Int, val text: String)

/** Generic start-position marker for juz / rub / thumn. */
data class MetaMarker(val num: Int, val sura: Int, val aya: Int)

data class MetaSajda(val num: Int, val sura: Int, val aya: Int, val obligatory: Boolean)

data class MetaPageStart(val num: Int, val sura: Int, val aya: Int)

// ── JSON parsing ──────────────────────────────────────────────────────────────

internal fun JSONObject.toMushafMeta(): MushafMeta {
    fun JSONObject.markers(key: String): List<MetaMarker> {
        val arr = optJSONArray(key) ?: return emptyList()
        return (0 until arr.length()).map { i ->
            arr.getJSONObject(i).let { o ->
                MetaMarker(o.getInt("num"), o.getInt("sura"), o.getInt("aya"))
            }
        }
    }

    val surahArr = getJSONArray("surahs")
    val surahs = (0 until surahArr.length()).map { i ->
        surahArr.getJSONObject(i).let { o ->
            MetaSurah(
                num             = o.getInt("num"),
                name            = o.getString("name"),
                nameEn          = o.getString("name_en"),
                nameComplex     = o.getString("name_complex"),
                translatedName  = o.getString("translated_name"),
                type            = o.getString("type"),
                revelationOrder = o.getInt("revelation_order"),
                ayat            = o.getInt("ayat"),
                bismillahPre    = o.optBoolean("bismillah_pre", true),
            )
        }
    }

    val versesArr = getJSONArray("verses")
    val verses = (0 until versesArr.length()).map { i ->
        versesArr.getJSONObject(i).let { o ->
            MetaVerse(o.getInt("sura"), o.getInt("aya"), o.getString("text"))
        }
    }

    val sajdaArr = getJSONArray("sajdaat")
    val sajdaat = (0 until sajdaArr.length()).map { i ->
        sajdaArr.getJSONObject(i).let { o ->
            MetaSajda(
                num        = o.getInt("num"),
                sura       = o.getInt("sura"),
                aya        = o.getInt("aya"),
                obligatory = o.optBoolean("obligatory", false),
            )
        }
    }

    val startsArr = optJSONArray("page_starts") ?: org.json.JSONArray()
    val pageStarts = (0 until startsArr.length()).map { i ->
        startsArr.getJSONObject(i).let { o ->
            MetaPageStart(o.getInt("num"), o.getInt("sura"), o.getInt("aya"))
        }
    }

    // divisions is a nested object: { juzaa: [...], ruba3: [...], athmaan: [...] }
    val divisionsObj = optJSONObject("divisions") ?: JSONObject()

    return MushafMeta(
        riwaya     = getString("riwaya"),
        version    = optInt("version", 1),
        bismillah  = optString("bismillah", ""),
        surahs     = surahs,
        verses     = verses,
        juzaa      = divisionsObj.markers("juzaa"),
        ruba3      = divisionsObj.markers("ruba3"),
        athmaan    = divisionsObj.markers("athmaan"),
        sajdaat    = sajdaat,
        pageStarts = pageStarts,
    )
}

// ── Entity converters ─────────────────────────────────────────────────────────

internal fun MetaSurah.toEntity(riwaya: String) = SurahEntity(
    riwaya          = riwaya,
    num             = num,
    name            = name,
    nameEn          = nameEn,
    nameComplex     = nameComplex,
    translatedName  = translatedName,
    type            = type,
    revelationOrder = revelationOrder,
    ayat            = ayat,
    bismillahPre    = bismillahPre,
)

internal fun MetaVerse.toEntity(riwaya: String) = VerseEntity(
    riwaya     = riwaya,
    sura       = sura,
    aya        = aya,
    text       = text,
    normalized = text.normalizeArabic(),
)

internal fun MetaMarker.toDivisionEntity(riwaya: String, divType: String) = DivisionEntity(
    riwaya = riwaya, type = divType, num = num, sura = sura, aya = aya,
)

internal fun MetaSajda.toEntity(riwaya: String) = SajdaEntity(
    riwaya = riwaya, num = num, sura = sura, aya = aya, obligatory = obligatory,
)

internal fun MetaPageStart.toEntity(riwaya: String) = PageStartEntity(
    riwaya = riwaya, pageNum = num, sura = sura, aya = aya,
)

internal fun MushafMeta.toDivisionEntities(riwaya: String): List<DivisionEntity> = buildList {
    juzaa.forEach   { add(it.toDivisionEntity(riwaya, DivType.JUZ))   }
    ruba3.forEach   { add(it.toDivisionEntity(riwaya, DivType.RUB))   }
    athmaan.forEach { add(it.toDivisionEntity(riwaya, DivType.THUMN)) }
}