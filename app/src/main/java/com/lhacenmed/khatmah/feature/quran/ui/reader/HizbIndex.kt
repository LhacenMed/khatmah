package com.lhacenmed.khatmah.feature.quran.ui.reader

import android.content.Context
import com.lhacenmed.khatmah.feature.quran.data.DivType
import com.lhacenmed.khatmah.feature.quran.data.db.MushafDb

/**
 * The rub'-al-hizb event that fires when the book reader lands on the page containing the start
 * of a rub' marker. One of four kinds, decided purely by the marker's position within its hizb
 * (1..4) — see [HizbIndex.loadForRiwaya]. [juz] and [hizb] are always 1-based.
 */
sealed class HizbEvent(open val juz: Int, open val hizb: Int) {
    /** posInHizb == 1 — the rub' marker *is* the hizb boundary itself. */
    data class HizbStart(override val juz: Int, override val hizb: Int) : HizbEvent(juz, hizb)
    /** posInHizb == 2 — one quarter into the hizb. */
    data class Quarter(override val juz: Int, override val hizb: Int) : HizbEvent(juz, hizb)
    /** posInHizb == 3 — halfway through the hizb. */
    data class Half(override val juz: Int, override val hizb: Int) : HizbEvent(juz, hizb)
    /** posInHizb == 4 — three quarters through the hizb. */
    data class ThreeQuarters(override val juz: Int, override val hizb: Int) : HizbEvent(juz, hizb)
}

/**
 * Builds and caches page → [HizbEvent] for the book reader's swipe toast, sourced from
 * [DivType.RUB] markers (240 per riwaya) + `mushaf_page_start` — both seeded by
 * [com.lhacenmed.khatmah.feature.quran.data.MushafInitializer] on first launch, so this works
 * without any QCF4 download. Keyed by [com.lhacenmed.khatmah.feature.quran.data.Riwaya.dbKey],
 * matching how [ReaderMeta] resolves the same bundled-JSON data.
 */
object HizbIndex {

    @Volatile
    private var cache: Pair<String, Map<Int, HizbEvent>>? = null

    /** page (1-based, QCF4 pagination) → [HizbEvent] for every rub' start in [riwayaKey]. */
    suspend fun loadForRiwaya(context: Context, riwayaKey: String): Map<Int, HizbEvent> {
        cache?.let { (key, map) -> if (key == riwayaKey) return map }

        val dao = MushafDb.get(context.applicationContext).dao()
        val rubs = dao.divisions(riwayaKey, DivType.RUB) // 240 rows, ordered by num

        val map = HashMap<Int, HizbEvent>(rubs.size)
        for (rub in rubs) {
            val page = dao.pageForVerse(riwayaKey, rub.sura, rub.aya) ?: continue
            val hizb = (rub.num - 1) / 4 + 1
            val juz = (hizb - 1) / 2 + 1
            val event = when ((rub.num - 1) % 4) {
                0 -> HizbEvent.HizbStart(juz, hizb)
                1 -> HizbEvent.Quarter(juz, hizb)
                2 -> HizbEvent.Half(juz, hizb)
                else -> HizbEvent.ThreeQuarters(juz, hizb)
            }
            // Extremely rare: two markers landing on the same page keeps the later (higher-num)
            // one, since rubs are iterated in ascending order and that's the one the swipe "arrives at".
            map[page] = event
        }

        cache = riwayaKey to map
        return map
    }
}

/** Eastern-Arabic-Indic numeral formatting, matching [ReaderMeta]'s own private copy. */
private fun eastNum(n: Int): String =
    n.toString().map { "٠١٢٣٤٥٦٧٨٩"[it - '0'] }.joinToString("")

/**
 * Resolves [HizbEvent] to its toast text via string resources (translatable wording), while the
 * digits themselves are always rendered Eastern-Arabic — matching [PageMeta]'s toolbar/footer/
 * slider conventions regardless of app locale.
 */
fun HizbEvent.toToastText(context: Context): String = when (this) {
    is HizbEvent.HizbStart -> context.getString(
        com.lhacenmed.khatmah.R.string.reader_hizb_start, eastNum(juz), eastNum(hizb),
    )
    is HizbEvent.Quarter -> context.getString(
        com.lhacenmed.khatmah.R.string.reader_hizb_quarter, eastNum(hizb),
    )
    is HizbEvent.Half -> context.getString(
        com.lhacenmed.khatmah.R.string.reader_hizb_half, eastNum(hizb),
    )
    is HizbEvent.ThreeQuarters -> context.getString(
        com.lhacenmed.khatmah.R.string.reader_hizb_three_quarters, eastNum(hizb),
    )
}