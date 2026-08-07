package com.lhacenmed.khatmah.feature.prayer.ui.settings.reminders

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import androidx.annotation.DrawableRes
import androidx.core.graphics.ColorUtils
import androidx.preference.PreferenceFragmentCompat
import com.google.android.material.color.MaterialColors
import com.lhacenmed.khatmah.R
import com.lhacenmed.khatmah.core.nav.Dest
import com.lhacenmed.khatmah.core.ui.collectWhileStarted
import com.lhacenmed.khatmah.core.ui.components.ValuePreference
import com.lhacenmed.khatmah.core.ui.components.go
import com.lhacenmed.khatmah.core.ui.components.onClick
import com.lhacenmed.khatmah.feature.prayer.notification.AdhanConfig
import com.lhacenmed.khatmah.feature.prayer.notification.AdhanPrefs
import com.lhacenmed.khatmah.feature.prayer.notification.AdhanSound
import com.lhacenmed.khatmah.shared.util.AdhanSoundFiles
import com.google.android.material.R as MaterialR

/** The six prayers, in the order [AdhanPrefs] keeps them. */
private const val PRAYER_COUNT = 6

/**
 * What each prayer announces itself with — one row per prayer, leading to that prayer's own screen.
 *
 * A row says its state twice over, because the two readings answer different questions: the value
 * names the sound chosen, and the icon says whether anything will actually be heard. A silenced
 * prayer and one turned off entirely both make no sound, so they are not left to the same icon.
 *
 * The rows are painted from [AdhanPrefs], which is also written by the screen each row leads to —
 * so this observes rather than reads, and a sound picked next door is already shown on the way back.
 */
class AdhanRemindersFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.adhan_reminders_preferences, rootKey)

        repeat(PRAYER_COUNT) { prayerId ->
            onClick(key(prayerId)) { go(Dest.AdhanSoundSelection(prayerId)) }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        collectWhileStarted(AdhanPrefs.flow) { configs ->
            repeat(PRAYER_COUNT) { prayerId ->
                showPrayer(prayerId, configs.getOrNull(prayerId) ?: return@repeat)
            }
        }
    }

    private fun showPrayer(prayerId: Int, config: AdhanConfig) {
        val row = findPreference<ValuePreference>(key(prayerId)) ?: return
        val audible = config.isEnabled && config.sound !is AdhanSound.Silent

        row.value = soundLabel(config.sound)
        row.icon = context?.getDrawable(iconFor(config))?.mutate()?.apply {
            // A tint list rather than a flat tint, so the icon dims with its row like every other
            // preference icon does (see tintIcons) — this screen only chooses a different colour.
            setTintList(iconTint(audible))
        }
    }

    /** Whether anything is heard, and if not, why — silenced, or off altogether. */
    @DrawableRes
    private fun iconFor(config: AdhanConfig): Int = when {
        !config.isEnabled -> R.drawable.ic_notifications_off
        config.sound is AdhanSound.Silent -> R.drawable.ic_volume_off
        else -> R.drawable.ic_notifications
    }

    /** Audible prayers carry the accent; the rest sit back with the row's own text. */
    private fun iconTint(audible: Boolean): ColorStateList {
        val context = requireContext()
        val color = MaterialColors.getColor(
            context,
            if (audible) MaterialR.attr.colorPrimary else MaterialR.attr.colorOnSurfaceVariant,
            context.getColor(android.R.color.darker_gray),
        )
        return ColorStateList(
            arrayOf(intArrayOf(-android.R.attr.state_enabled), intArrayOf()),
            intArrayOf(ColorUtils.setAlphaComponent(color, DISABLED_ALPHA), color),
        )
    }

    private fun soundLabel(sound: AdhanSound): String = when (sound) {
        is AdhanSound.Off    -> getString(R.string.adhan_status_off)
        is AdhanSound.Silent -> getString(R.string.adhan_sound_silent)
        is AdhanSound.Device -> getString(R.string.adhan_sound_device)
        is AdhanSound.Asset  -> AdhanSoundFiles.getDisplayName(sound.filename)
        is AdhanSound.Custom -> sound.displayName
    }

    private fun key(prayerId: Int) = "prayer:$prayerId"

    private companion object {
        /** Material's disabled opacity (38%), as an alpha channel. */
        const val DISABLED_ALPHA = 97
    }
}
