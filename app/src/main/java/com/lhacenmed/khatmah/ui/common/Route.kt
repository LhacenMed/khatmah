package com.lhacenmed.khatmah.ui.common

/**
 * Single source of truth for all navigation route strings.
 *
 * Tab NavScreen vals in ui/page/tabs/ and sub-page NavPage vals in ui/page/
 * both reference these constants so every callsite uses the same string.
 *
 * Whether a route is top-level (tab) or a sub-page is determined at runtime by
 * whether it appears in the tabs list declared in MainActivity — no separate set
 * to maintain.
 */
object Route {

    // ── Shell ─────────────────────────────────────────────────────────────────
    // Root destination that contains all tabs + chrome (TopAppBar + BottomNavBar).
    // Animates as a single unit when navigating to/from sub-pages.
    const val MAIN    = "main"

    // ── Tabs (bottom navigation) ──────────────────────────────────────────────
    const val TODAY   = "today"
    const val ATHKAR  = "athkar"
    const val PRAYERS = "prayers"
    const val INDEX   = "index"
    const val MORE    = "more"

    // ── Sub-pages ─────────────────────────────────────────────────────────────
    const val THEME_SETTINGS = "theme_settings"
    const val LANGUAGE       = "language"
    const val ABOUT          = "about"
}