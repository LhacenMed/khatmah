package com.lhacenmed.khatmah.shared.reminders

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Single source of truth for all reminder configurations.
 *
 * Call [init] once from [App.onCreate] before any other reminder code.
 * [version] increments on each [save] so [ReminderScheduler] can detect changes.
 *
 * On first launch it seeds defaults and migrates any existing adhan_prefs data.
 */
object ReminderPrefs {

    private const val PREFS       = "reminder_prefs"
    private const val K_IDS       = "ids"
    private const val K_CUSTOM    = "custom_sounds"

    // Matches AdhanSound.Asset(AdhanSound.DEFAULT_ASSET).toKey()
    private const val DEFAULT_PRAYER_SOUND = "asset:makkah.mp3"

    @Volatile var version: Int = 0
        private set

    private val _flow = MutableStateFlow<List<ReminderConfig>>(emptyList())
    val flow: StateFlow<List<ReminderConfig>> = _flow.asStateFlow()

    private val _customSoundsFlow = MutableStateFlow<List<ReminderSound.Custom>>(emptyList())
    val customSoundsFlow: StateFlow<List<ReminderSound.Custom>> = _customSoundsFlow.asStateFlow()

    /** Loads or seeds reminder configs. Safe to call multiple times — always reloads. */
    fun init(context: Context) {
        val ctx = context.applicationContext
        val p   = prefs(ctx)
        val ids = p.getStringSet(K_IDS, null)

        if (ids.isNullOrEmpty()) {
            val defaults = buildDefaults().toMutableList()
            migrateAdhanPrefs(ctx, defaults)
            writeAll(p, defaults)
            _flow.value = defaults
        } else {
            val configs = ids.mapNotNull { readOne(p, it) }.toMutableList()
            migrateKhatmahSlots(p, configs)
            _flow.value = configs.sortedBy { it.alarmCode }
        }
        _customSoundsFlow.value = readCustomSounds(p)
    }

    fun getAll(): List<ReminderConfig> = _flow.value

    fun getById(id: String): ReminderConfig? = _flow.value.find { it.id == id }

    fun save(context: Context, config: ReminderConfig) {
        val p       = prefs(context.applicationContext)
        val current = _flow.value.toMutableList()
        val idx     = current.indexOfFirst { it.id == config.id }

        writeOne(p, config)
        if (idx >= 0) current[idx] = config
        else { current.add(config); writeIds(p, current.map { it.id }) }

        _flow.value = current
        version++

        if (config.type !is ReminderType.Prayer) {
            val sound = ReminderSound.fromKey(config.soundKey)
            if (sound is ReminderSound.Custom) addCustomSound(context, sound)
        }
    }

    fun addCustomSound(context: Context, sound: ReminderSound.Custom) {
        val p   = prefs(context.applicationContext)
        val set = p.getStringSet(K_CUSTOM, emptySet())!!.toMutableSet()
        if (set.add(sound.toKey())) {
            p.edit { putStringSet(K_CUSTOM, set) }
            _customSoundsFlow.value = readCustomSounds(p)
        }
    }

    fun removeCustomSound(context: Context, sound: ReminderSound.Custom) {
        val p   = prefs(context.applicationContext)
        val set = p.getStringSet(K_CUSTOM, emptySet())!!.toMutableSet()
        if (set.remove(sound.toKey())) {
            p.edit { putStringSet(K_CUSTOM, set) }
            _customSoundsFlow.value = readCustomSounds(p)
        }
    }

    // ── Defaults ──────────────────────────────────────────────────────────────

    private fun buildDefaults(): List<ReminderConfig> = buildList {
        // Prayer reminders — alarmCodes 0-5, pre-alert codes 10-15
        repeat(6) { i ->
            add(ReminderConfig(
                id              = "prayer:$i",
                type            = ReminderType.Prayer(i),
                enabled         = i != 1,   // Sunrise off by default
                soundKey        = if (i == 1) "off" else DEFAULT_PRAYER_SOUND,
                preAlertMinutes = 0,
                alarmCode       = i,
            ))
        }
        // Adhkar — alarmCodes 20-21
        add(fixedOff("adhkar:morning",  ReminderType.Adhkar("morning"),  7,  0, 20))
        add(fixedOff("adhkar:evening",  ReminderType.Adhkar("evening"), 17, 30, 21))
        // Quran sunnah — alarmCodes 22-24
        add(fixedOff("sunnah:al_mulk",    ReminderType.QuranSunnah("al_mulk"),   21,  0, 22))
        add(fixedOff("sunnah:al_baqarah", ReminderType.QuranSunnah("al_baqarah"),20, 30, 23))
        add(fixedOff("sunnah:al_kahf",    ReminderType.QuranSunnah("al_kahf"),   13,  0, 24))
        // Daily khatmah — 5 preset slots, alarmCodes 25-29
        addAll(buildKhatmahSlots())
    }

    /**
     * Five daily khatmah reminder slots at 16:00–20:00.
     * Slot 0 is enabled by default; slots 1–4 start disabled.
     */
    private fun buildKhatmahSlots(): List<ReminderConfig> = buildList {
        add(ReminderConfig(
            id        = "khatmah:0",
            type      = ReminderType.DailyKhatmah,
            enabled   = true,
            timeHour  = 16,
            timeMinute = 0,
            soundKey  = "device",
            alarmCode = 25,
        ))
        for (i in 1..4) add(ReminderConfig(
            id        = "khatmah:$i",
            type      = ReminderType.DailyKhatmah,
            enabled   = false,
            timeHour  = 16 + i,
            timeMinute = 0,
            soundKey  = "device",
            alarmCode = 25 + i,
        ))
    }

    private fun fixedOff(id: String, type: ReminderType, h: Int, m: Int, code: Int) =
        ReminderConfig(id = id, type = type, enabled = false,
            timeHour = h, timeMinute = m, soundKey = "device", alarmCode = code)

    // ── Migrations ────────────────────────────────────────────────────────────

    /**
     * One-time migration: reads prayer sound/pre-alert values from the old adhan_prefs
     * SharedPreferences file (used before this central system existed).
     */
    private fun migrateAdhanPrefs(context: Context, configs: MutableList<ReminderConfig>) {
        val old = context.getSharedPreferences("adhan_prefs", Context.MODE_PRIVATE)
        if (!old.contains("adhan_sound_0")) return
        for (i in 0 until 6) {
            val soundKey = old.getString("adhan_sound_$i", null) ?: continue
            val pre      = old.getInt("adhan_pre_$i", 0)
            val idx      = configs.indexOfFirst { it.id == "prayer:$i" }
            if (idx >= 0) configs[idx] = configs[idx].copy(
                enabled         = soundKey != "off",
                soundKey        = soundKey,
                preAlertMinutes = pre,
            )
        }
        old.edit { clear() }
    }

    /**
     * One-time migration: seeds the five khatmah slots for users who were on the old
     * single "khatmah" config before multi-slot support was added.
     */
    private fun migrateKhatmahSlots(p: SharedPreferences, configs: MutableList<ReminderConfig>) {
        if (configs.any { it.id.startsWith("khatmah:") }) return
        val slots = buildKhatmahSlots()
        slots.forEach { writeOne(p, it) }
        configs.removeAll { it.id == "khatmah" }
        configs.addAll(slots)
        writeIds(p, configs.map { it.id })
    }

    // ── I/O ───────────────────────────────────────────────────────────────────

    private fun writeAll(p: SharedPreferences, configs: List<ReminderConfig>) {
        configs.forEach { writeOne(p, it) }
        writeIds(p, configs.map { it.id })
    }

    private fun writeOne(p: SharedPreferences, c: ReminderConfig) = p.edit {
        putString("${c.id}_type", c.type.toKey())
        putBoolean("${c.id}_en",  c.enabled)
        putInt("${c.id}_h",       c.timeHour)
        putInt("${c.id}_m",       c.timeMinute)
        putString("${c.id}_snd",  c.soundKey)
        putInt("${c.id}_pre",     c.preAlertMinutes)
        putInt("${c.id}_code",    c.alarmCode)
        if (c.deepLink != null) putString("${c.id}_link", c.deepLink) else remove("${c.id}_link")
    }

    private fun readOne(p: SharedPreferences, id: String): ReminderConfig? {
        val typeKey = p.getString("${id}_type", null) ?: return null
        return runCatching {
            ReminderConfig(
                id              = id,
                type            = ReminderType.fromKey(typeKey),
                enabled         = p.getBoolean("${id}_en",  true),
                timeHour        = p.getInt("${id}_h",       7),
                timeMinute      = p.getInt("${id}_m",       0),
                soundKey        = p.getString("${id}_snd",  "device") ?: "device",
                preAlertMinutes = p.getInt("${id}_pre",     0),
                alarmCode       = p.getInt("${id}_code",    0),
                deepLink        = p.getString("${id}_link", null),
            )
        }.getOrNull()
    }

    private fun writeIds(p: SharedPreferences, ids: List<String>) =
        p.edit { putStringSet(K_IDS, ids.toSet()) }

    private fun readCustomSounds(p: SharedPreferences): List<ReminderSound.Custom> =
        p.getStringSet(K_CUSTOM, emptySet())!!
            .mapNotNull { ReminderSound.fromKey(it) as? ReminderSound.Custom }
            .sortedBy { it.displayName }

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}