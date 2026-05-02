package com.lhacenmed.khatmah.feature.prayer.notification

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Persists per-prayer [AdhanConfig] to SharedPreferences.
 *
 * Key schema:
 *   adhan_sound_<prayerId>      → [AdhanSound.toKey]
 *   adhan_pre_<prayerId>        → pre-alert minutes (Int)
 *
 * [init] must be called once in [App.onCreate].
 * [version] increments on every [save] so [AdhanScheduler] can detect changes.
 */
object AdhanPrefs {

    private const val PREFS = "adhan_prefs"

    /** Prayer indices mirror [PrayerEngine] output order: 0=Fajr … 5=Isha. */
    const val FAJR    = 0
    const val SUNRISE = 1
    const val DHUHR   = 2
    const val ASR     = 3
    const val MAGHRIB = 4
    const val ISHA    = 5
    const val COUNT   = 6

    @Volatile var version: Int = 0
        private set

    private val _flow = MutableStateFlow(defaultConfigs())
    val flow: StateFlow<List<AdhanConfig>> = _flow.asStateFlow()

    fun init(context: Context) {
        _flow.value = load(context.applicationContext)
    }

    fun get(): List<AdhanConfig> = _flow.value

    fun getFor(prayerId: Int): AdhanConfig = _flow.value.getOrElse(prayerId) { AdhanConfig() }

    fun save(context: Context, prayerId: Int, config: AdhanConfig) {
        val updated = _flow.value.toMutableList().also { it[prayerId] = config }
        prefs(context).edit {
            putString("adhan_sound_$prayerId", config.sound.toKey())
            putInt("adhan_pre_$prayerId", config.preAlertMinutes)
        }
        _flow.value = updated
        version++
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private fun load(context: Context): List<AdhanConfig> {
        val p = prefs(context)
        return List(COUNT) { i ->
            val soundKey = p.getString("adhan_sound_$i", null)
            val sound    = if (soundKey != null) AdhanSound.fromKey(soundKey)
            else if (i == SUNRISE) AdhanSound.Off   // Sunrise off by default
            else AdhanSound.Asset(AdhanSound.DEFAULT_ASSET)
            AdhanConfig(
                sound          = sound,
                preAlertMinutes = p.getInt("adhan_pre_$i", 0),
            )
        }
    }

    private fun defaultConfigs(): List<AdhanConfig> = List(COUNT) { i ->
        AdhanConfig(
            sound = if (i == SUNRISE) AdhanSound.Off
            else AdhanSound.Asset(AdhanSound.DEFAULT_ASSET),
        )
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}