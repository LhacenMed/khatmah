package com.lhacenmed.khatmah.core.nav

import com.lhacenmed.khatmah.feature.adhkar.ui.AdhkarDetailPage
import com.lhacenmed.khatmah.feature.adhkar.ui.AdhkarTab
import com.lhacenmed.khatmah.feature.more.MoreTab
import com.lhacenmed.khatmah.feature.mushaf.ui.PrintSelectPage
import com.lhacenmed.khatmah.feature.prayer.ui.PrayersTab
import com.lhacenmed.khatmah.feature.quran.ui.IndexTab
import com.lhacenmed.khatmah.feature.settings.AboutPage
import com.lhacenmed.khatmah.feature.today.TodayTab

/**
 * Single source of truth for all navigation destinations.
 *
 * ── Adding a new tab ──────────────────────────────────────────────────────────
 *  1. `object YourTab : AppTab(iconRes, labelRes, order)` in its feature package.
 *  2. Add to [tabs] below — done.
 *
 * ── Adding a new page ─────────────────────────────────────────────────────────
 *  1. `object YourPage : AppPage()` in its feature package.
 *  2. Add to [pages] below — done.
 *
 * Onboarding pages are excluded intentionally — they form a linear flow with
 * special argument forwarding handled directly in [AppEntry].
 */
object AppRegistry {

    /** Tab-bar destinations sorted by [AppTab.order]. */
    val tabs: List<AppTab> = listOf(
        TodayTab,
        AdhkarTab,
        PrayersTab,
        IndexTab,
        MoreTab,
    ).sortedBy { it.order }

    /**
     * Full-screen page destinations. [AppEntry] registers all entries here
     * automatically via a single NavHost loop — no manual [animatedComposable]
     * blocks needed for any destination listed here.
     *
     * Migrate remaining pages progressively: create the AppPage object in the
     * feature file, add it here, and remove the corresponding manual block
     * from [AppEntry].
     */
    val pages: List<AppPage> = listOf(
        // ── Settings ──────────────────────────────────────────────────────────
        AboutPage,

        // ── Adhkar ────────────────────────────────────────────────────────────
        AdhkarDetailPage,

        // ── Mushaf ────────────────────────────────────────────────────────────
        PrintSelectPage,

        // ── Add new AppPage objects here as files are migrated ─────────────
    )
}