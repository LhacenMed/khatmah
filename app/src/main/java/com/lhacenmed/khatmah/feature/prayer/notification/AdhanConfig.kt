package com.lhacenmed.khatmah.feature.prayer.notification

/**
 * Full notification config for one prayer.
 *
 * [sound]          — what plays at prayer time (or [AdhanSound.Off] to disable).
 * [preAlertMinutes]— 0 = disabled; 5/10/15/20/25/30 = reminder N minutes before.
 *                    Pre-alert always uses the device default sound.
 *                    Ignored when [sound] is [AdhanSound.Off].
 */
data class AdhanConfig(
    val sound: AdhanSound = AdhanSound.Asset(AdhanSound.DEFAULT_ASSET),
    val preAlertMinutes: Int = 0,
) {
    val isEnabled: Boolean get() = sound !is AdhanSound.Off
}