package com.lhacenmed.khatmah.shared.util

import android.content.Context

/**
 * Manages the available adhan sound files from assets/adhan/.
 */
object AdhanSoundFiles {

    /**
     * Map of filename to its Arabic display name.
     * New sounds should be added here to be visible in the app.
     */
    private val soundsMap = listOf(
        "short_alert.opus" to "صوت تنبيه قصير",
        "adhan_mr.opus" to "أذان الموريتانية",
        "madinah.opus" to "أذان المدينة",
        "makkah.opus" to "أذان الحرم المكي - كامل",
        "aqsa.opus" to "أذان الأقصى",
        "qutami.opus" to "أذان - ناصر القطامي",
        "abdulbasit.opus" to "أذان - عبد الباسط عبد الصمد",
        "assafi_1.opus" to "أذان - مشاري العسافي 1",
        "assafi_2.opus" to "أذان - مشاري العسافي 2",
        "nafis_1.opus" to "أذان - أحمد النفيس 1",
        "nafis_2.opus" to "أذان - أحمد النفيس 2",
    )

    /**
     * Returns the adhan opus filenames in the controlled order.
     * Only returns files that actually exist in the assets.
     */
    fun list(context: Context): List<String> {
        val assetsList = context.assets.list("adhan")?.toSet() ?: emptySet()
        return soundsMap.map { it.first }.filter { it in assetsList }
    }

    /**
     * Returns the display name for a given filename.
     */
    fun getDisplayName(filename: String): String {
        return soundsMap.find { it.first == filename }?.second 
            ?: filename.removeSuffix(".opus")
    }
}