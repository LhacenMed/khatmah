package com.lhacenmed.khatmah.feature.quran.data

import android.content.Context
import com.lhacenmed.khatmah.feature.quran.data.db.BookmarkEntity
import com.lhacenmed.khatmah.feature.quran.data.db.MushafDb
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext

/**
 * Page bookmarks for the QCF4 book reader, scoped by riwaya ([Riwaya.dbKey]). Thin wrapper over the
 * shared [MushafDb] so bookmarks live with the rest of the mushaf data and stay consistent.
 */
class BookmarkRepository(context: Context) {

    private val dao = MushafDb.get(context).dao()

    /** Adds (or replaces) the bookmark for [page] with an optional user [label]. */
    suspend fun add(riwaya: String, page: Int, label: String?) = withContext(Dispatchers.IO) {
        dao.insertBookmark(BookmarkEntity(riwaya, page, System.currentTimeMillis(), label))
    }

    suspend fun remove(riwaya: String, page: Int) =
        withContext(Dispatchers.IO) { dao.deleteBookmark(riwaya, page) }

    /** Live list of bookmarks for [riwaya], most recent first. */
    fun bookmarks(riwaya: String): Flow<List<BookmarkEntity>> = dao.bookmarks(riwaya)

    companion object {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private val cache = mutableMapOf<String, StateFlow<Set<Int>>>()

        /**
         * Live set of bookmarked page numbers for [riwaya] — the single source the reader's page
         * ribbons and the toolbar icon both read, so one DB observation keeps them in lockstep and
         * any add/remove lands everywhere at once.
         */
        fun pages(context: Context, riwaya: String): StateFlow<Set<Int>> = synchronized(cache) {
            cache.getOrPut(riwaya) {
                MushafDb.get(context.applicationContext).dao().bookmarks(riwaya)
                    .map { rows -> buildSet { rows.forEach { add(it.pageNum) } } }
                    .stateIn(scope, SharingStarted.WhileSubscribed(STOP_DELAY_MS), emptySet())
            }
        }

        /** Keeps the query alive briefly across reader restarts (rotation) instead of re-running it. */
        private const val STOP_DELAY_MS = 5_000L
    }
}
