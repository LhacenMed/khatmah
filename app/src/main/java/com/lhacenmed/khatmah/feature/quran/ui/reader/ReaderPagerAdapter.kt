package com.lhacenmed.khatmah.feature.quran.ui.reader

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter

/**
 * Backs the reader's [androidx.viewpager2.widget.ViewPager2] with one page per mushaf page,
 * delegating page creation to the active [ReaderSource] (book or text).
 *
 * The pager is pinned to LTR (see the layout); the right-to-left mushaf feel comes from reversing
 * the position→page mapping (`page = lastPage - position`), so position 0 is the last page and
 * swiping left→right advances to the next (higher-numbered) page. Keeping the reversal here rather
 * than in the layout direction also puts the wird's end at the pager's leading edge, which is where
 * [WirdWall] hangs off.
 *
 * [count] pages are shown, mapping down from [lastPage]; a full mushaf passes
 * `count = pageCount, lastPage = pageCount`, while a Khatmah session passes the window's size and
 * its highest page so only that range is reachable.
 */
class ReaderPagerAdapter(
    activity: FragmentActivity,
    private val count: Int,
    private val lastPage: Int,
    private val source: ReaderSource,
) : FragmentStateAdapter(activity) {

    override fun getItemCount(): Int = count

    override fun createFragment(position: Int): Fragment = source.newPageFragment(lastPage - position)
}
