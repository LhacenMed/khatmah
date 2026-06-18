package com.lhacenmed.khatmah.core.nav

import com.lhacenmed.khatmah.feature.adhkar.ui.AdhkarTab
import com.lhacenmed.khatmah.feature.demo.DemoTab
import com.lhacenmed.khatmah.feature.more.MoreTab
import com.lhacenmed.khatmah.feature.prayer.ui.PrayersTab
// import com.lhacenmed.khatmah.feature.qadaa.ui.QadaaTab   // temporarily replaced by DemoTab
import com.lhacenmed.khatmah.feature.today.TodayTab

/**
 * Single source of truth for the bottom-navigation tabs, in bar order (left → right).
 * The bottom-nav menu, the pager, the toolbar (title/subtitle/actions) and deep-link
 * routing are all derived from this list in MainActivity.
 *
 * To add or remove a tab, edit this list (and the tab's own `object`). Max 5 entries —
 * a platform limit of BottomNavigationView.
 */
val AppTabs: List<AppTab> = listOf(
    TodayTab,
    AdhkarTab,
    PrayersTab,
    // QadaaTab,   // temporarily replaced by DemoTab
    DemoTab,
    MoreTab,
)
