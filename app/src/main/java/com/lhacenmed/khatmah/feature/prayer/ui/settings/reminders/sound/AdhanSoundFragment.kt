package com.lhacenmed.khatmah.feature.prayer.ui.settings.reminders.sound

import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.net.toUri
import androidx.core.os.bundleOf
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import com.lhacenmed.khatmah.R
import com.lhacenmed.khatmah.core.ui.collectWhileStarted
import com.lhacenmed.khatmah.core.ui.components.ValueListPreference
import com.lhacenmed.khatmah.core.ui.components.onClick
import com.lhacenmed.khatmah.core.ui.tintIcons
import com.lhacenmed.khatmah.feature.prayer.notification.AdhanConfig
import com.lhacenmed.khatmah.feature.prayer.notification.AdhanPrefs
import com.lhacenmed.khatmah.feature.prayer.notification.AdhanScheduler
import com.lhacenmed.khatmah.feature.prayer.notification.AdhanSound
import com.lhacenmed.khatmah.shared.reminders.ReminderNotifier
import com.lhacenmed.khatmah.shared.util.AdhanSoundFiles

/** How long before the prayer a first, quieter warning can be sent. 0 = none. */
private val PreAlertOptions = intArrayOf(0, 5, 10, 15, 20, 25, 30)

/** The six prayers, in the order [AdhanPrefs] keeps them. */
private val PrayerNames = intArrayOf(
    R.string.prayer_fajr, R.string.prayer_sunrise, R.string.prayer_dhuhr,
    R.string.prayer_asr, R.string.prayer_maghrib, R.string.prayer_isha,
)

/**
 * One prayer's reminder: the warning before it, and the sound that announces it.
 *
 * The sounds are one choice, so they stay laid out on the screen with the chosen one marked rather
 * than folded into a dialog — you are picking by ear, and a list you can play your way down is the
 * whole point. Each row plays without choosing; only the row's own tap commits.
 *
 * Which sounds exist is not fixed: the adhans ship in assets and the rest are files the user picked,
 * so those rows are built here and rebuilt whenever either set changes. Everything is written
 * through [AdhanPrefs], which re-syncs the notification channels, and the prayer is rescheduled
 * after each change so the next call already uses it.
 */
class AdhanSoundFragment : PreferenceFragmentCompat() {

    private val prayerId: Int get() = arguments?.getInt(ARG_PRAYER_ID) ?: 0

    /** Auditions the row under the finger. One at a time, and never past the screen. */
    private var player: MediaPlayer? = null

    private val filePicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) adoptFile(uri)
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.adhan_sound_preferences, rootKey)

        val context = requireContext()
        val prayer = getString(PrayerNames.getOrElse(prayerId) { R.string.prayers })
        findPreference<PreferenceCategory>(KEY_SOUNDS)?.title =
            getString(R.string.adhan_sound_section_format, prayer)

        bindPreAlert()

        // The three fixed choices. Only the device sound has anything to play.
        onClick(key(AdhanSound.Off)) { choose(AdhanSound.Off) }
        onClick(key(AdhanSound.Silent)) { choose(AdhanSound.Silent) }
        onClick(key(AdhanSound.Device)) { choose(AdhanSound.Device) }
        findPreference<SoundPreference>(key(AdhanSound.Device))?.onPreview = { playDevice() }

        addAssetSounds(context)
        onClick(KEY_BROWSE) { filePicker.launch(arrayOf(AUDIO_MIME)) }

        // Last, so it covers the adhan rows added just above as well as the declared ones.
        preferenceScreen.tintIcons()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        collectWhileStarted(AdhanPrefs.flow) { configs ->
            showConfig(configs.getOrElse(prayerId) { AdhanConfig() })
        }
        collectWhileStarted(AdhanPrefs.customSoundsFlow) { showCustomSounds(it) }
    }

    /** Leaving the screen stops the audition — a sound outliving the page that started it. */
    override fun onStop() {
        super.onStop()
        stopPlayback()
    }

    // ── Rows ──────────────────────────────────────────────────────────────────

    private fun bindPreAlert() {
        findPreference<ValueListPreference>(KEY_PRE_ALERT)?.apply {
            entries = PreAlertOptions.map(::preAlertLabel).toTypedArray()
            entryValues = PreAlertOptions.map { it.toString() }.toTypedArray()
            setOnPreferenceChangeListener { _, value ->
                save(config().copy(preAlertMinutes = (value as String).toInt()))
                true
            }
        }
    }

    /** The adhans that ship with the app, discovered rather than declared. */
    private fun addAssetSounds(context: Context) {
        val category = findPreference<PreferenceCategory>(KEY_SOUNDS) ?: return
        AdhanSoundFiles.list(context).forEach { filename ->
            val sound = AdhanSound.Asset(filename)
            category.addPreference(soundRow(
                key = key(sound),
                title = AdhanSoundFiles.getDisplayName(filename),
                sound = sound,
                onPreview = { playAsset(filename) },
            ))
        }
    }

    /** The files the user has picked, rebuilt whenever that set changes. */
    private fun showCustomSounds(sounds: List<AdhanSound.Custom>) {
        val category = findPreference<PreferenceCategory>(KEY_CUSTOMS) ?: return
        category.removeAll()
        sounds.forEach { sound ->
            category.addPreference(soundRow(
                key = key(sound),
                title = sound.displayName,
                sound = sound,
                onPreview = { playUri(sound.uri.toUri()) },
            ))
        }
        // Re-added last so it keeps its place under the files, whatever their number.
        category.addPreference(browseRow())
        // These rows are built after the screen was tinted, so this section is tinted again.
        category.tintIcons()
        showConfig(config())
    }

    private fun soundRow(
        key: String,
        title: String,
        sound: AdhanSound,
        onPreview: () -> Unit,
    ) = SoundPreference(requireContext()).also {
        it.key = key
        it.title = title
        it.icon = requireContext().getDrawable(R.drawable.ic_play_arrow)
        it.onPreview = onPreview
        it.setOnPreferenceClickListener { choose(sound); true }
    }

    private fun browseRow() = Preference(requireContext()).also {
        it.key = KEY_BROWSE
        it.order = BROWSE_ORDER
        it.title = getString(R.string.adhan_sound_custom_browse)
        it.icon = requireContext().getDrawable(R.drawable.ic_folder_open)
        it.setOnPreferenceClickListener { filePicker.launch(arrayOf(AUDIO_MIME)); true }
    }

    // ── State ─────────────────────────────────────────────────────────────────

    /** Marks the chosen sound and sets the pre-alert, which only means anything while a sound plays. */
    private fun showConfig(config: AdhanConfig) {
        val chosen = key(config.sound)
        forEachSoundRow { it.checked = it.key == chosen }

        findPreference<ValueListPreference>(KEY_PRE_ALERT)?.apply {
            isEnabled = config.isEnabled
            value = config.preAlertMinutes.toString()
        }
    }

    private fun forEachSoundRow(action: (SoundPreference) -> Unit) {
        listOf(KEY_SOUNDS, KEY_CUSTOMS).forEach { key ->
            val category = findPreference<PreferenceCategory>(key) ?: return@forEach
            for (index in 0 until category.preferenceCount) {
                (category.getPreference(index) as? SoundPreference)?.let(action)
            }
        }
    }

    private fun choose(sound: AdhanSound) {
        // Choosing settles the question, so whatever was being auditioned stops with it.
        stopPlayback()
        save(config().copy(sound = sound))
    }

    private fun save(config: AdhanConfig) {
        val context = requireContext()
        AdhanPrefs.save(context, prayerId, config)
        AdhanScheduler.schedulePrayer(context, prayerId)
    }

    private fun config(): AdhanConfig = AdhanPrefs.getFor(prayerId)

    /**
     * Takes on a file the user picked: the read permission has to outlive this screen, because the
     * notification channel plays the file long after — and on a device that has rebooted since.
     */
    private fun adoptFile(uri: Uri) {
        val context = requireContext()
        context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val sound = AdhanSound.Custom(uri.toString(), context.audioName(uri))
        ReminderNotifier.ensureCustomAdhanChannel(context, sound.uri, sound.displayName)
        save(config().copy(sound = sound))
    }

    // ── Playback ──────────────────────────────────────────────────────────────

    private fun playDevice() =
        playUri(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))

    private fun playAsset(filename: String) = play {
        requireContext().assets.openFd("$ASSET_DIR/$filename").use {
            setDataSource(it.fileDescriptor, it.startOffset, it.length)
        }
    }

    private fun playUri(uri: Uri) = play { setDataSource(requireContext(), uri) }

    /** One player at a time: starting an audition ends the one before it. */
    private fun play(source: MediaPlayer.() -> Unit) {
        stopPlayback()
        player = MediaPlayer().apply {
            source()
            prepare()
            start()
        }
    }

    private fun stopPlayback() {
        player?.release()
        player = null
    }

    private fun key(sound: AdhanSound) = "sound:${sound.toKey()}"

    private fun preAlertLabel(minutes: Int): String =
        if (minutes == 0) getString(R.string.adhan_alert_before_off)
        else getString(R.string.adhan_alert_before_minutes, minutes)

    companion object {
        private const val ARG_PRAYER_ID = "prayerId"
        private const val KEY_SOUNDS = "sounds"
        private const val KEY_CUSTOMS = "customs"
        private const val KEY_PRE_ALERT = "pre_alert"
        private const val KEY_BROWSE = "browse"
        private const val AUDIO_MIME = "audio/*"
        private const val ASSET_DIR = "adhan"
        /** Past any number of picked files, so browsing stays the last word in its section. */
        private const val BROWSE_ORDER = 100

        fun newInstance(prayerId: Int) = AdhanSoundFragment().apply {
            arguments = bundleOf(ARG_PRAYER_ID to prayerId)
        }
    }
}

/** The display name of a picked file (e.g. "My Adhan.mp3"), for the row and its channel. */
private fun Context.audioName(uri: Uri): String =
    contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        ?.use { if (it.moveToFirst()) it.getString(0) else null }
        ?: uri.lastPathSegment
        ?: "Custom"
