package com.lhacenmed.khatmah.feature.quran.data

import android.content.Context
import com.lhacenmed.khatmah.feature.quran.data.db.BookmarkEntity
import com.lhacenmed.khatmah.feature.quran.data.db.MushafDb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * Page bookmarks for the QCF4 book reader, scoped by riwaya ([Riwaya.dbKey]). Thin wrapper over the
 * shared [MushafDb] so bookmarks live with the rest of the mushaf data and stay consistent.
 */
class BookmarkRepository(context: Context) {

    private val dao = MushafDb.get(context).dao()

    suspend fun isBookmarked(riwaya: String, page: Int): Boolean =
        withContext(Dispatchers.IO) { dao.isBookmarked(riwaya, page) }

    /** Adds (or replaces) the bookmark for [page] with an optional user [label]. */
    suspend fun add(riwaya: String, page: Int, label: String?) = withContext(Dispatchers.IO) {
        dao.insertBookmark(BookmarkEntity(riwaya, page, System.currentTimeMillis(), label))
    }

    suspend fun remove(riwaya: String, page: Int) =
        withContext(Dispatchers.IO) { dao.deleteBookmark(riwaya, page) }

    /** Live list of bookmarks for [riwaya], most recent first. */
    fun bookmarks(riwaya: String): Flow<List<BookmarkEntity>> = dao.bookmarks(riwaya)
}
