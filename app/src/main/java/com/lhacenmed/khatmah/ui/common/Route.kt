package com.lhacenmed.khatmah.ui.common

/**
 * Single source of truth for all navigation destination identifiers.
 * Used as reference when migrating to Compose Navigation; Fragment nav uses R.id.action_* for now.
 */
object Route {
    // Bottom nav tabs
    const val TODAY    = "today"
    const val ATHKAR   = "athkar"
    const val PRAYERS  = "prayers"
    const val INDEX    = "index"
    const val MORE     = "more"

    // Settings sub-pages (reachable from Today)
    const val THEME_SETTINGS = "theme_settings"
    const val LANGUAGE       = "language"
}