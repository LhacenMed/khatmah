package com.lhacenmed.khatmah.feature.quran.ui.book

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentStatePagerAdapter

/**
 * Backs the book reader's [androidx.viewpager.widget.ViewPager] with one
 * [BookPageFragment] per mushaf page — Quran Android's `QuranPageAdapter` analog.
 *
 * The pager itself stays LTR; the right-to-left mushaf feel comes from reversing the
 * position→page mapping exactly like Quran Android's `getPageFromPosition`
 * (`page = numberOfPages - position`). So position 0 is the last page and swiping
 * left→right advances to the next (higher-numbered) page.
 */
class BookPagerAdapter(
    fm: FragmentManager,
    private val pageCount: Int,
) : FragmentStatePagerAdapter(fm, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT) {

    override fun getCount(): Int = pageCount

    override fun getItem(position: Int): Fragment =
        BookPageFragment.newInstance(pageCount - position)
}
