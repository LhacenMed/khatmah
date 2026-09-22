package com.lhacenmed.khatmah.shared.reminders

/**
 * The routes a reminder can deep-link to that are not a tab.
 *
 * A reminder's route normally names an [com.lhacenmed.khatmah.core.nav.AppTab] and means "show
 * that tab". These two name a screen that opens *on top* of its tab, so MainActivity has to
 * recognise them before it looks the route up in the tab list. Kept here, beside the reminders
 * that carry them, so the two ends of the contract are written once.
 */
object ReminderRoute {

    /** Today's khatmah wird, opened in the reader over the Quran tab. */
    const val WIRD = "wird"

    private const val ADHKAR_DETAIL_PREFIX = "adhkar_detail/"
    private const val SUNNAH_PREFIX        = "sunnah/"

    /** The route that opens the dhikr reader for [categoryId]. */
    fun adhkarDetail(categoryId: String): String = "$ADHKAR_DETAIL_PREFIX$categoryId"

    /** The category in an [adhkarDetail] route, or null when [route] is not one. */
    fun adhkarDetailCategory(route: String): String? =
        route.removePrefix(ADHKAR_DETAIL_PREFIX).takeIf { it != route }

    /**
     * The route that opens the surah numbered [surahNumber] in the reader.
     *
     * The number, rather than a name or a key, because a reminder may be for any surah in the
     * mushaf — the ones the app suggests by itself are only the three in [SunnahSurah].
     */
    fun sunnah(surahNumber: Int): String = "$SUNNAH_PREFIX$surahNumber"

    /** The surah number in a [sunnah] route, or null when [route] is not one. */
    fun sunnahSurahNumber(route: String): Int? =
        route.removePrefix(SUNNAH_PREFIX).takeIf { it != route }?.toIntOrNull()
}
