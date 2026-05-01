package com.lhacenmed.khatmah.data.prayer

/**
 * Notification sound choice for a single prayer.
 *
 * [Off]     — no notification at all.
 * [Silent]  — notification posted but with a silent channel (no sound/vibration).
 * [Device]  — notification uses the device's default notification sound.
 * [Asset]   — plays a specific mp3 from assets/adhan/; [filename] is the bare
 *             filename including extension, e.g. "أذان الحرم المكي - كامل.mp3".
 *             Adding a new sound = drop the mp3 into assets/adhan/ — nothing else needed.
 */
sealed class AdhanSound {
    object Off    : AdhanSound()
    object Silent : AdhanSound()
    object Device : AdhanSound()
    data class Asset(val filename: String) : AdhanSound()

    // ── Persistence ───────────────────────────────────────────────────────────

    fun toKey(): String = when (this) {
        is Off    -> "off"
        is Silent -> "silent"
        is Device -> "device"
        is Asset  -> "asset:$filename"
    }

    companion object {
        fun fromKey(key: String): AdhanSound = when {
            key == "off"          -> Off
            key == "silent"       -> Silent
            key == "device"       -> Device
            key.startsWith("asset:") -> Asset(key.removePrefix("asset:"))
            else                  -> Asset(DEFAULT_ASSET)
        }

        /** Filename used as the app-wide default. */
        const val DEFAULT_ASSET = "أذان الحرم المكي - كامل.mp3"
    }
}