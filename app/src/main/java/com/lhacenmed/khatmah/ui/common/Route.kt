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

    // ── General settings sub-pages ────────────────────────────────────────────
    const val THEME_SETTINGS = "theme_settings"
    const val LANGUAGE       = "language"
    const val ABOUT          = "about"

    // ── Prayer settings sub-pages ─────────────────────────────────────────────
    const val PRAYER_SETTINGS           = "prayer_settings"
    const val PRAYER_CALC_METHOD        = "prayer_calc_method"
    const val PRAYER_JURISTIC           = "prayer_juristic"
    const val PRAYER_DST                = "prayer_dst"
    const val PRAYER_MANUAL_CORRECTIONS = "prayer_manual_corrections"
    const val PRAYER_HIGHER_LAT         = "prayer_higher_lat"

    // ── Quran ─────────────────────────────────────────────────────────────────
    const val QURAN_READER = "quran_reader"

    // ── Adhkar detail ─────────────────────────────────────────────────────────
    const val ADHKAR_DETAIL = "adhkar_detail/{categoryId}"

    /** Builds the Adhkar detail route for [categoryId]. */
    fun adhkarDetail(categoryId: String) = "adhkar_detail/$categoryId"

    // ── Onboarding ────────────────────────────────────────────────────────────
    const val ONBOARDING_LANGUAGE       = "onboarding_language"
    const val ONBOARDING_NOTIFICATIONS  = "onboarding_notifications"
    const val ONBOARDING_LOCATION       = "onboarding_location"
    const val ONBOARDING_COUNTRY_SELECT = "onboarding_country_select?fromSettings={fromSettings}"
    const val ONBOARDING_CITY_SELECT    = "onboarding_city_select?country={country}&iso2={iso2}&fromSettings={fromSettings}"

    /** Builds the city-select route for [country] + [iso2], URL-encoding both. */
    fun citySelect(country: String, iso2: String, fromSettings: Boolean = false) =
        "onboarding_city_select?country=${Uri.encode(country)}&iso2=${Uri.encode(iso2)}&fromSettings=$fromSettings"
}