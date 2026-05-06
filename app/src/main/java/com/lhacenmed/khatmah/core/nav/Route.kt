package com.lhacenmed.khatmah.core.nav

import android.net.Uri

/**
 * Single source of truth for all navigation route strings.
 */
object Route {

    // ── Shell ─────────────────────────────────────────────────────────────────
    const val MAIN = "main"

    // ── Tabs (bottom navigation) ──────────────────────────────────────────────
    const val TODAY   = "today"
    const val ADHKAR  = "adhkar"
    const val PRAYERS = "prayers"
    const val INDEX   = "index"
    const val MORE    = "more"

    // ── Qibla ─────────────────────────────────────────────────────────────────────
    const val QIBLA = "qibla"

    // ── General settings sub-pages ────────────────────────────────────────────
    const val THEME_SETTINGS = "theme_settings"
    const val DARK_THEME     = "dark_theme"
    const val LANGUAGE       = "language"
    const val ABOUT          = "about"

    // ── Prayer settings sub-pages ─────────────────────────────────────────────
    const val PRAYER_SETTINGS           = "prayer_settings"
    const val PRAYER_CALC_METHOD        = "prayer_calc_method"
    const val PRAYER_JURISTIC           = "prayer_juristic"
    const val PRAYER_DST                = "prayer_dst"
    const val PRAYER_MANUAL_CORRECTIONS = "prayer_manual_corrections"
    const val PRAYER_HIGHER_LAT         = "prayer_higher_lat"
    const val ADHAN_REMINDERS           = "adhan_reminders"
    const val ADHAN_SOUND_SELECTION     = "adhan_sound_selection/{prayerId}"

    // ── Quran ─────────────────────────────────────────────────────────────────
    // suraNum = 0 → open at last-read page (SharedPrefs).
    const val DEBUG_WARSH = "debug_warsh"
    // suraNum > 0 → open at the first page of that surah.
    // ayaNum  > 0 → open at that specific aya within the surah (used by search).
    const val QURAN_READER = "quran_reader?suraNum={suraNum}&ayaNum={ayaNum}"
    const val QURAN_SEARCH = "quran_search"

    /** Opens the reader at [suraNum] / [ayaNum]. Defaults to last-read page when both are 0. */
    fun quranReader(suraNum: Int = 0, ayaNum: Int = 0) =
        "quran_reader?suraNum=$suraNum&ayaNum=$ayaNum"

    // ── Adhkar ────────────────────────────────────────────────────────────────
    const val ADHKAR_DETAIL = "adhkar_detail/{categoryId}"

    /**
     * Unified create / edit page for Adhkar categories.
     * [categoryId] absent (empty) → create mode.
     * [categoryId] present         → edit mode.
     */
    const val ADHKAR_EDITOR = "adhkar_editor?categoryId={categoryId}"

    /** Builds the Adhkar detail route for [categoryId]. */
    fun adhkarDetail(categoryId: String) = "adhkar_detail/$categoryId"

    /**
     * Navigates to create mode when [categoryId] is null,
     * or edit mode when a [categoryId] is supplied.
     */
    fun adhkarEditor(categoryId: String? = null) =
        "adhkar_editor?categoryId=${categoryId.orEmpty()}"

    /** Builds the adhan sound selection route for [prayerId]. */
    fun adhanSoundSelection(prayerId: Int) = "adhan_sound_selection/$prayerId"

    // ── Khatmah ───────────────────────────────────────────────────────────────────
    const val NEW_KHATMAH          = "new_khatmah"
    const val QURAN_SESSION_READER = "quran_session_reader?startPage={startPage}&endPage={endPage}"  // ← add

    fun quranSessionReader(startPage: Int, endPage: Int) =                                           // ← add
        "quran_session_reader?startPage=$startPage&endPage=$endPage"

    const val DEBUG_DB    = "debug_db"

    // ── Mushaf print selection ────────────────────────────────────────────────────
    const val MUSHAF_PRINTS = "mushaf_prints"

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