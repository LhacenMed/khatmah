package com.lhacenmed.khatmah.ui.common

/**
 * Single source of truth for all navigation route strings.
 *
 * Tab NavScreen vals in ui/page/tabs/ reference these constants so the route
 * declared here and the route registered in NavHost are always the same string.
 */
object Route {

    // ── Tabs (bottom navigation) ──────────────────────────────────────────────
    const val TODAY   = "today"
    const val ATHKAR  = "athkar"
    const val PRAYERS = "prayers"
    const val INDEX   = "index"
    const val MORE    = "more"

    /**
     * Set of all top-level tab routes.
     * Used to determine TopAppBar state (back arrow vs. none) and BottomNavBar
     * visibility — any route absent from this set is treated as a sub-page.
     */
    val TABS = setOf(TODAY, ATHKAR, PRAYERS, INDEX, MORE)

    // ── Sub-pages ─────────────────────────────────────────────────────────────
    const val THEME_SETTINGS = "theme_settings"
    const val LANGUAGE       = "language"
}