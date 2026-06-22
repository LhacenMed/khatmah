package com.lhacenmed.khatmah.feature.prayer.notification

/**
 * Notification sound choice for a single prayer.
 *
 * [Off]     — no notification at all.
 * [Silent]  — notification posted but with a silent channel (no sound/vibration).
 * [Device]  — notification uses the device's default notification sound.
 * [Asset]   — plays a specific opus from assets/adhan/; [filename] is the bare
 *             filename including extension, e.g. "makkah.opus".
 *             Adding a new sound = drop the opus into assets/adhan/ — nothing else needed.
 * [Custom]  — plays a user-picked audio file via its persisted SAF content URI.
 *             Requires a persistent URI permission taken at pick time.
 */
sealed class AdhanSound {
    object Off    : AdhanSound()
    object Silent : AdhanSound()
    object Device : AdhanSound()
    data class Asset(val filename: String) : AdhanSound()
    data class Custom(val uri: String, val displayName: String) : AdhanSound()

    // ── Persistence ───────────────────────────────────────────────────────────

    fun toKey(): String = when (this) {
        is Off    -> "off"
        is Silent -> "silent"
        is Device -> "device"
        is Asset  -> "asset:$filename"
        // \u0000 never appears in display names or content URIs — safe separator.
        is Custom -> "custom\u0000$displayName\u0000$uri"
    }

    companion object {
        fun fromKey(key: String): AdhanSound = when {
            key == "off"                    -> Off
            key == "silent"                 -> Silent
            key == "device"                 -> Device
            key.startsWith("asset:")        -> {
                val filename = key.removePrefix("asset:").replace(".mp3", ".opus")
                Asset(filename)
            }
            key.startsWith("custom\u0000") -> {
                val parts = key.split('\u0000')
                if (parts.size >= 3) Custom(parts[2], parts[1]) else Asset(DEFAULT_ASSET)
            }
            else -> Asset(DEFAULT_ASSET)
        }

        /** Filename used as the app-wide default. */
        const val DEFAULT_ASSET = "adhan_mr.opus"
    }
}