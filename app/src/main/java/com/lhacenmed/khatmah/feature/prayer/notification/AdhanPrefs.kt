package com.lhacenmed.khatmah.feature.prayer.notification

import android.content.Context
import android.os.Build
import androidx.core.content.edit
import com.lhacenmed.khatmah.shared.reminders.ReminderPrefs
import com.lhacenmed.khatmah.shared.reminders.ReminderType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Prayer-specific accessor over [ReminderPrefs].
 * Preserves the same public API consumed by the prayer settings UI.
 * Must be initialised after [ReminderPrefs.init] in [App.onCreate].
 */
object AdhanPrefs {

    // ── Prayer indices — mirror PrayerEngine output order ─────────────────────
    const val FAJR    = 0
    const val SUNRISE = 1
    const val DHUHR   = 2
    const val ASR     = 3
    const val MAGHRIB = 4
    const val ISHA    = 5
    const val COUNT   = 6

    /** Incremented on every [save]; consumed by prayer UI to trigger re-scheduling. */
    @Volatile var version: Int = 0
        private set

    // Custom adhan sounds are stored separately from non-prayer reminder sounds.
    private const val ADHAN_PREFS  = "adhan_custom"
    private const val K_CUSTOM_SND = "adhan_custom_sounds"

    private val _flow = MutableStateFlow(defaultConfigs())
    val flow: StateFlow<List<AdhanConfig>> = _flow.asStateFlow()

    private val _customSoundsFlow = MutableStateFlow<List<AdhanSound.Custom>>(emptyList())
    val customSoundsFlow: StateFlow<List<AdhanSound.Custom>> = _customSoundsFlow.asStateFlow()

    /**
     * Syncs in-memory state from [ReminderPrefs].
     * [ReminderPrefs.init] must be called before this.
     */
    fun init(context: Context) {
        _flow.value = buildConfigs()
        _customSoundsFlow.value = readCustomSounds(context.applicationContext)
    }

    fun get(): List<AdhanConfig> = buildConfigs()

    fun getFor(prayerId: Int): AdhanConfig = buildConfigs().getOrElse(prayerId) { AdhanConfig() }

    fun save(context: Context, prayerId: Int, config: AdhanConfig) {
        val existing = ReminderPrefs.getById("prayer:$prayerId") ?: return
        ReminderPrefs.save(context, existing.copy(
            enabled         = config.isEnabled,
            soundKey        = config.sound.toKey(),
            preAlertMinutes = config.preAlertMinutes,
        ))
        _flow.value = buildConfigs()
        version++
        if (config.sound is AdhanSound.Custom) addCustomSound(context, config.sound)
        // Create the channel for a newly-chosen sound and drop any now-unused one.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) AdhanChannels.sync(context)
    }

    fun getCustomSounds(context: Context): List<AdhanSound.Custom> = _customSoundsFlow.value

    fun addCustomSound(context: Context, sound: AdhanSound.Custom) {
        val p   = adhanPrefs(context)
        val set = p.getStringSet(K_CUSTOM_SND, emptySet())!!.toMutableSet()
        if (set.add(sound.toKey())) {
            p.edit { putStringSet(K_CUSTOM_SND, set) }
            _customSoundsFlow.value = readCustomSounds(context)
        }
    }

    fun removeCustomSound(context: Context, sound: AdhanSound.Custom) {
        val p   = adhanPrefs(context)
        val set = p.getStringSet(K_CUSTOM_SND, emptySet())!!.toMutableSet()
        if (set.remove(sound.toKey())) {
            p.edit { putStringSet(K_CUSTOM_SND, set) }
            _customSoundsFlow.value = readCustomSounds(context)
        }
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private fun buildConfigs(): List<AdhanConfig> = (0 until COUNT).map { i ->
        ReminderPrefs.getById("prayer:$i")?.let {
            AdhanConfig(
                sound           = AdhanSound.fromKey(it.soundKey),
                preAlertMinutes = it.preAlertMinutes,
            )
        } ?: AdhanConfig(sound = if (i == SUNRISE) AdhanSound.Off
        else AdhanSound.Asset(AdhanSound.DEFAULT_ASSET))
    }

    private fun defaultConfigs(): List<AdhanConfig> = List(COUNT) { i ->
        AdhanConfig(sound = if (i == SUNRISE) AdhanSound.Off
        else AdhanSound.Asset(AdhanSound.DEFAULT_ASSET))
    }

    private fun readCustomSounds(context: Context): List<AdhanSound.Custom> =
        adhanPrefs(context).getStringSet(K_CUSTOM_SND, emptySet())!!
            .mapNotNull { AdhanSound.fromKey(it) as? AdhanSound.Custom }
            .sortedBy { it.displayName }

    private fun adhanPrefs(context: Context) =
        context.applicationContext.getSharedPreferences(ADHAN_PREFS, Context.MODE_PRIVATE)
}