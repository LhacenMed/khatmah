package com.lhacenmed.khatmah.feature.quran.ui.home

import com.lhacenmed.khatmah.R
import com.lhacenmed.khatmah.core.nav.AppTab

/**
 * The app's home: reading the Quran. Native throughout — [QuranTabFragment] collects the state and
 * [QuranTabViews] draws `quran_tab.xml`, so the tab's corner radii, ripples and palette are the
 * platform's own.
 */
object QuranTab : AppTab(
    iconRes  = R.drawable.ic_book,
    titleRes = R.string.quran,
    route    = "quran",
) {
    override fun newFragment() = QuranTabFragment()
}
