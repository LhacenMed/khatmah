package com.lhacenmed.khatmah.feature.quran.ui.reader.book

import android.content.Context
import androidx.fragment.app.Fragment
import com.lhacenmed.khatmah.feature.mushaf.data.Riwaya
import com.lhacenmed.khatmah.feature.quran.data.Qcf4Repository
import com.lhacenmed.khatmah.feature.quran.data.RiwayaConfig
import com.lhacenmed.khatmah.feature.quran.ui.reader.PageMeta
import com.lhacenmed.khatmah.feature.quran.ui.reader.ReaderMeta
import com.lhacenmed.khatmah.feature.quran.ui.reader.ReaderMode
import com.lhacenmed.khatmah.feature.quran.ui.reader.ReaderSource

/** [ReaderSource] backed by the downloaded QCF4 mushaf — the fixed 604-page, session-capable book. */
class Qcf4ReaderSource(context: Context, override val riwaya: Riwaya) : ReaderSource {

    private val appCtx = context.applicationContext
    private val repo = Qcf4Repository.get(appCtx, riwaya)

    override val mode = ReaderMode.QCF4
    override val supportsSession = true

    override suspend fun prepare(): Int = RiwayaConfig.PAGE_COUNT

    override suspend fun ayaPageIndex(): Map<Long, Int> = repo.ayaPageIndex()

    override suspend fun pageMeta(): Map<Int, PageMeta> = ReaderMeta.loadForRiwaya(appCtx, riwaya.dbKey)

    override fun newPageFragment(page: Int): Fragment = BookPageFragment.newInstance(page)
}
