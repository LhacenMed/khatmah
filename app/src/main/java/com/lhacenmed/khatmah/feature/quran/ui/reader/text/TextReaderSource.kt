package com.lhacenmed.khatmah.feature.quran.ui.reader.text

import android.content.Context
import androidx.fragment.app.Fragment
import com.lhacenmed.khatmah.feature.quran.data.Riwaya
import com.lhacenmed.khatmah.feature.quran.data.QuranTextRepository
import com.lhacenmed.khatmah.feature.quran.ui.reader.PageMeta
import com.lhacenmed.khatmah.feature.quran.ui.reader.ReaderMeta
import com.lhacenmed.khatmah.feature.quran.ui.reader.ReaderMode
import com.lhacenmed.khatmah.feature.quran.ui.reader.ReaderSource
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * [ReaderSource] for the zero-download text reader. Builds the riwaya's pages once (off the bundled
 * verse data) and caches them; the dynamic page count means sessions aren't supported. A per-riwaya
 * singleton so the shell and every [TextPageFragment] share the same built pages.
 */
class TextReaderSource private constructor(
    context: Context,
    override val riwaya: Riwaya,
) : ReaderSource {

    private val appCtx = context.applicationContext
    private val repo = QuranTextRepository(appCtx)
    private val buildLock = Mutex()
    @Volatile private var cached: List<QuranPageData>? = null

    override val mode = ReaderMode.TEXT
    override val supportsSession = false

    /** The riwaya-specific basmala line shown between surahs. */
    val basmala: String = when (riwaya) {
        Riwaya.WARSH -> "بِسْمِ اِ۬للَّهِ اِ۬لرَّحْمَٰنِ اِ۬لرَّحِيمِ"
        Riwaya.HAFS  -> "بِسۡمِ ٱللَّهِ ٱلرَّحۡمَٰنِ ٱلرَّحِيمِ"
    }

    suspend fun pages(): List<QuranPageData> {
        cached?.let { return it }
        return buildLock.withLock {
            cached ?: build().also { cached = it }
        }
    }

    private suspend fun build(): List<QuranPageData> {
        val key = riwaya.dbKey
        return TextPaginator.build(repo.allAyas(key), repo.bismillahMap(key))
    }

    /** The page data for [page] (1-based), or null if out of range. */
    suspend fun pageData(page: Int): QuranPageData? = pages().getOrNull(page - 1)

    override suspend fun prepare(): Int = pages().size

    override suspend fun ayaPageIndex(): Map<Long, Int> {
        val map = HashMap<Long, Int>(6400)
        pages().forEachIndexed { idx, p ->
            for (seg in p.segments) if (seg is QuranSegment.Aya)
                map[(seg.suraNum.toLong() shl 32) or seg.ayaNum.toLong()] = idx
        }
        return map
    }

    override suspend fun pageMeta(): Map<Int, PageMeta> = pages().associate { p ->
        val firstAya = p.segments.firstNotNullOfOrNull { it as? QuranSegment.Aya }
        val juz = firstAya?.let { ReaderMeta.juzForVerse(it.suraNum, it.ayaNum) } ?: 1
        p.pageNum to PageMeta(p.suraName, juz, p.pageNum)
    }

    override fun newPageFragment(page: Int): Fragment = TextPageFragment.newInstance(page)

    companion object {
        private val instances = HashMap<Riwaya, TextReaderSource>()

        fun get(context: Context, riwaya: Riwaya): TextReaderSource = synchronized(instances) {
            instances.getOrPut(riwaya) { TextReaderSource(context.applicationContext, riwaya) }
        }
    }
}
