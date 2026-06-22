package com.lhacenmed.khatmah.feature.quran.ui.reader

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentStatePagerAdapter

/**
 * Backs the reader's [androidx.viewpager.widget.ViewPager] with one page per mushaf page,
 * delegating page creation to the active [ReaderSource] (book or text).
 *
 * The pager itself stays LTR; the right-to-left mushaf feel comes from reversing the position→page
 * mapping (`page = lastPage - position`), so position 0 is the last page and swiping left→right
 * advances to the next (higher-numbered) page.
 *
 * [count] pages are shown, mapping down from [lastPage]; a full mushaf passes
 * `count = pageCount, lastPage = pageCount`, while a Khatmah session passes the window's size and
 * its highest page so only that range is reachable.
 */
class ReaderPagerAdapter(
    fm: FragmentManager,
    private val count: Int,
    private val lastPage: Int,
    private val source: ReaderSource,
) : FragmentStatePagerAdapter(fm, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT) {

    override fun getCount(): Int = count

    override fun getItem(position: Int): Fragment = source.newPageFragment(lastPage - position)
}
