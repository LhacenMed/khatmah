package com.lhacenmed.khatmah.ui.common

import android.net.Uri

/**
 * Single source of truth for all navigation route strings.
 */
object Route {

    // ── Shell ─────────────────────────────────────────────────────────────────
    const val MAIN = "main"

    // ── Tabs (bottom navigation) ──────────────────────────────────────────────
    const val TODAY   = "today"
    const val ATHKAR  = "athkar"
    const val PRAYERS = "prayers"
    const val INDEX   = "index"
    const val MORE    = "more"

    // ── Settings sub-pages ────────────────────────────────────────────────────
    const val THEME_SETTINGS = "theme_settings"
    const val LANGUAGE       = "language"
    const val ABOUT          = "about"

    // ── Onboarding ────────────────────────────────────────────────────────────
    const val ONBOARDING_NOTIFICATIONS  = "onboarding_notifications"
    const val ONBOARDING_LOCATION       = "onboarding_location"
    const val ONBOARDING_COUNTRY_SELECT = "onboarding_country_select"
    const val ONBOARDING_CITY_SELECT    = "onboarding_city_select?country={country}"

    /** Builds the city-select route for [country], URL-encoding the name. */
    fun citySelect(country: String) =
        "onboarding_city_select?country=${Uri.encode(country)}"
}