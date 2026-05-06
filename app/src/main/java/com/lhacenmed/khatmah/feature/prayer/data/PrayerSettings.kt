package com.lhacenmed.khatmah.feature.prayer.data

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Persists [PrayerCalcSettings] to SharedPreferences.
 *
 * [init] must be called once in [App.onCreate] before any UI is created.
 *
 * Exposes [flow] so Compose screens observe changes and recompose immediately
 * when any setting is saved — no restarts required.
 *
 * [version] is incremented on every [save] call; [PrayerRepository] uses it to
 * invalidate its date-keyed cache whenever calculation parameters change.
 */
object PrayerSettings {

    private const val PREFS = "prayer_settings"
    private const val K_AUTO = "auto"
    private const val K_MTH  = "method"
    private const val K_JUR  = "juristic"
    private const val K_DST  = "dst"
    private const val K_HL   = "higher_lat"
    private const val K_CF   = "c_fajr"
    private const val K_CS   = "c_sunrise"
    private const val K_CD   = "c_dhuhr"
    private const val K_CA   = "c_asr"
    private const val K_CM   = "c_maghrib"
    private const val K_CI   = "c_isha"

    /** Monotonically increasing; bumped by one on every [save]. */
    @Volatile var version: Int = 0
        private set

    private val _flow = MutableStateFlow(PrayerCalcSettings())
    val flow: StateFlow<PrayerCalcSettings> = _flow.asStateFlow()

    /** Load persisted settings into memory. Call once from [Application.onCreate]. */
    fun init(context: Context) {
        _flow.value = load(context.applicationContext)
    }

    /** Returns the currently active settings (in-memory, no I/O). */
    fun get(): PrayerCalcSettings = _flow.value

    /**
     * Persists [settings] and broadcasts to [flow].
     * Safe to call from the main thread — SharedPreferences is fast for small data.
     */
    fun save(context: Context, settings: PrayerCalcSettings) {
        prefs(context).edit {
            putBoolean(K_AUTO, settings.autoSettings)
            putString(K_MTH,   settings.method.methodId)
            putString(K_JUR,   settings.juristic.name)
            putString(K_DST,   settings.dstMode.name)
            putString(K_HL,    settings.higherLatMode.name)
            putInt(K_CF, settings.corrections.fajr)
            putInt(K_CS, settings.corrections.sunrise)
            putInt(K_CD, settings.corrections.dhuhr)
            putInt(K_CA, settings.corrections.asr)
            putInt(K_CM, settings.corrections.maghrib)
            putInt(K_CI, settings.corrections.isha)
        }
        _flow.value = settings
        version++
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private fun load(context: Context): PrayerCalcSettings {
        val p = prefs(context)
        return PrayerCalcSettings(
            autoSettings  = p.getBoolean(K_AUTO, true),
            method        = CalcMethod.fromId(
                p.getString(K_MTH, CalcMethod.MOROCCO_MWL.methodId) ?: CalcMethod.MOROCCO_MWL.methodId
            ),
            juristic      = enumOrDefault(p.getString(K_JUR, null), JuristicMethod.SHAFI_MALIKI_HANBALI),
            dstMode       = enumOrDefault(p.getString(K_DST, null), DstMode.AUTOMATIC),
            higherLatMode = enumOrDefault(p.getString(K_HL,  null), HigherLatMode.NONE),
            corrections   = ManualCorrections(
                fajr    = p.getInt(K_CF, 0),
                sunrise = p.getInt(K_CS, 0),
                dhuhr   = p.getInt(K_CD, 0),
                asr     = p.getInt(K_CA, 0),
                maghrib = p.getInt(K_CM, 0),
                isha    = p.getInt(K_CI, 0),
            ),
        )
    }

    private inline fun <reified T : Enum<T>> enumOrDefault(name: String?, default: T): T =
        if (name == null) default else runCatching { enumValueOf<T>(name) }.getOrDefault(default)

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}