package com.lhacenmed.khatmah.data.prayer

// ─── Isha mode ────────────────────────────────────────────────────────────────

/** How Isha time is computed — angle-based or fixed offset from Maghrib. */
sealed class IshaMode {
    /** Sun depression angle below the horizon (degrees). */
    data class Angle(val degrees: Double) : IshaMode()
    /** Fixed minutes after Maghrib. */
    data class FixedMinutes(val minutes: Int) : IshaMode()
}

// ─── Method offsets ───────────────────────────────────────────────────────────

/**
 * Per-method baked-in minute offsets applied after the astronomical calculation.
 *
 * These are distinct from [ManualCorrections] (user-configurable) — they are
 * authority-defined corrections that make each method match the official timetable
 * published by the corresponding institution.
 *
 * Values extracted from the original Khatmah app's decompiled CalculationMethod.java,
 * where they are stored as `f28351g = new f(fajr, sunrise, dhuhr, asr, maghrib, isha)`.
 *
 * Final time = astronomical_time + [MethodOffsets] + [ManualCorrections]
 */
data class MethodOffsets(
    val fajr:    Int = 0,
    val sunrise: Int = 0,
    val dhuhr:   Int = 0,
    val asr:     Int = 0,
    val maghrib: Int = 0,
    val isha:    Int = 0,
)

// ─── Calculation methods ──────────────────────────────────────────────────────

/**
 * All supported prayer time calculation methods.
 *
 * [methodId]     Stable key used for SharedPreferences persistence.
 * [displayName]  Human-readable name shown in the UI.
 * [fajrAngle]    Sun depression angle for Fajr (degrees below horizon).
 * [ishaMode]     Isha computation — angle or fixed offset from Maghrib.
 * [offsets]      Authority-defined baked-in corrections in minutes.
 *                Applied on top of the astronomical result, before user
 *                [ManualCorrections]. Extracted from the original Khatmah
 *                decompiled source — see [MethodOffsets].
 */
enum class CalcMethod(
    val methodId:    String,
    val displayName: String,
    val fajrAngle:   Double,
    val ishaMode:    IshaMode,
    val offsets:     MethodOffsets = MethodOffsets(),
) {
    // ── No baked-in offsets ───────────────────────────────────────────────────

    UMM_AL_QURA(
        "UMM_AL_QURA",
        "Umm Al-Qura",
        18.5, IshaMode.FixedMinutes(90),
    ),
    UMM_AL_QURA_RAMADAN(
        "UMM_AL_QURA_RAMADAN",
        "Umm Al-Qura (Ramadan)",
        18.5, IshaMode.FixedMinutes(120),
    ),
    KUWAIT(
        "KUWAIT",
        "Kuwait, Ministry of Awqaf and Islamic Affairs",
        18.0, IshaMode.Angle(17.5),
    ),
    BAHRAIN(
        "BAHRAIN",
        "Bahrain Method",
        18.2, IshaMode.Angle(18.0),
    ),
    LEBANON(
        "LEBANON",
        "Lebanon Method",
        20.0, IshaMode.Angle(18.0),
    ),
    LIBYA(
        "LIBYA",
        "Libya Method",
        18.5, IshaMode.Angle(18.0),
    ),
    SUDAN(
        "SUDAN",
        "Sudan Method",
        18.5, IshaMode.Angle(18.0),
    ),
    RUSSIA(
        "RUSSIA",
        "Spiritual Administration of Muslims of Russia",
        16.0, IshaMode.Angle(15.0),
    ),
    FIXED_ISHA(
        "FIXED_ISHA",
        "Fixed Isha Angle Interval",
        19.5, IshaMode.FixedMinutes(90),
    ),
    FRANCE_15(
        "FRANCE_15",
        "France 15°",
        15.0, IshaMode.Angle(15.0),
    ),
    FRANCE_18(
        "FRANCE_18",
        "France 18°",
        18.0, IshaMode.Angle(18.0),
    ),

    // ── Dhuhr +1 (precautionary delay) ───────────────────────────────────────

    MUSLIM_WORLD_LEAGUE(
        "MUSLIM_WORLD_WORLD_LEAGUE_METHOD_ID",
        "Muslim World League (MWL)",
        18.0, IshaMode.Angle(17.0),
        MethodOffsets(dhuhr = 1),
    ),
    EGYPTIAN(
        "EGYPTIAN",
        "Egyptian General Authority",
        19.5, IshaMode.Angle(17.5),
        MethodOffsets(dhuhr = 1),
    ),
    NORTH_AMERICA(
        "NORTH_AMERICA_ISNA",
        "North America (ISNA)",
        15.0, IshaMode.Angle(15.0),
        MethodOffsets(dhuhr = 1),
    ),
    KARACHI(
        "KARACHI_METHOD_ID",
        "Karachi, Islamic University",
        18.0, IshaMode.Angle(18.0),
        MethodOffsets(dhuhr = 1),
    ),
    ALGERIA(
        "ALGERIA_METHOD_ID",
        "Algeria, Ministry of Awqaf and Islamic Affairs",
        18.0, IshaMode.Angle(17.0),
        MethodOffsets(dhuhr = 1),
    ),
    NETHERLANDS_MWL(
        "NETHERLANDS_MWL",
        "Netherlands, Muslim World League (MWL)",
        18.0, IshaMode.Angle(17.0),
        MethodOffsets(fajr = -2),
    ),
    MUIS_SINGAPORE(
        "SINGAPORE_MUIS",
        "MUIS (Majlis Ugama Islam Singapura)",
        20.0, IshaMode.Angle(18.0),
        MethodOffsets(dhuhr = 1),
    ),

    // ── Regional authority offsets ────────────────────────────────────────────

    /**
     * UAE — General Authority of Islamic Affairs and Endowments.
     * offsets: sunrise -3, dhuhr +3, asr +3, maghrib +3.
     * (original Khatmah enum name: DUBAI)
     */
    UAE(
        "UAE",
        "UAE, General Authority of Islamic Affairs and Endowments",
        18.2, IshaMode.Angle(18.2),
        MethodOffsets(sunrise = -3, dhuhr = 3, asr = 3, maghrib = 3),
    ),

    /**
     * Qatar — Ministry of Awqaf and Islamic Affairs.
     * offsets: fajr -1, maghrib +3, isha +3.
     */
    QATAR(
        "QATAR",
        "Qatar, Ministry of Awqaf and Islamic Affairs",
        18.0, IshaMode.FixedMinutes(90),
        MethodOffsets(fajr = -1, maghrib = 3, isha = 3),
    ),

    /**
     * Turkey — Diyanet İşleri Başkanlığı.
     * offsets: sunrise -7, dhuhr +5, asr +4, maghrib +7.
     * This is why changing to Turkey moves Asr significantly.
     */
    TURKEY(
        "TURKEY_METHOD_ID",
        "Turkey, Diyanet Isleri Baskanligi",
        18.0, IshaMode.Angle(17.0),
        MethodOffsets(sunrise = -7, dhuhr = 5, asr = 4, maghrib = 7),
    ),

    /**
     * Moon Sighting Committee.
     * offsets: fajr +5, sunrise +3, maghrib +1, isha +7.
     */
    MOON_SIGHTING(
        "MOON_SIGHTING",
        "Moon Sighting Committee",
        18.0, IshaMode.Angle(18.0),
        MethodOffsets(fajr = 5, sunrise = 3, maghrib = 1, isha = 7),
    ),

    /**
     * Morocco — Muslim World League (MWL).
     * offsets: fajr -7, sunrise -1, dhuhr +5, asr +1, maghrib +3, isha +1.
     * The most significant per-prayer adjustments of all methods.
     */
    MOROCCO_MWL(
        "MOROCCO_MWL",
        "Morocco, Muslim World League (MWL)",
        18.0, IshaMode.Angle(17.0),
        MethodOffsets(fajr = -7, sunrise = -1, dhuhr = 5, asr = 1, maghrib = 3, isha = 1),
    ),

    /**
     * Tunisia Method.
     * offsets: dhuhr +6, maghrib +5.
     */
    TUNISIA(
        "TUNISIA",
        "Tunisia Method",
        18.2, IshaMode.Angle(18.2),
        MethodOffsets(dhuhr = 6, maghrib = 5),
    ),

    /**
     * Jordan Method.
     * offsets: sunrise -7, maghrib +7.
     */
    JORDAN(
        "JORDAN",
        "Jordan Method",
        18.0, IshaMode.Angle(18.0),
        MethodOffsets(sunrise = -7, maghrib = 7),
    ),

    /**
     * Oman Method.
     * offsets: dhuhr +7, asr +7, maghrib +7.
     */
    OMAN(
        "OMAN",
        "Oman Method",
        17.7, IshaMode.Angle(18.0),
        MethodOffsets(dhuhr = 7, asr = 7, maghrib = 7),
    ),

    /**
     * JAKIM, Indonesia (Kemenag).
     * offsets: fajr +2, sunrise -2, dhuhr +3, asr +2, maghrib +2, isha +2.
     */
    JAKIM_INDONESIA(
        "INDONESIA",
        "JAKIM, Indonesia",
        20.0, IshaMode.Angle(18.0),
        MethodOffsets(fajr = 2, sunrise = -2, dhuhr = 3, asr = 2, maghrib = 2, isha = 2),
    ),

    /**
     * JAKIM, Malaysia.
     * offsets: fajr +12, sunrise -2, dhuhr +3, asr +2, maghrib +2, isha +2.
     * Note fajr +12 — JAKIM deliberately shifts Fajr later to be conservative.
     */
    JAKIM_MALAYSIA(
        "MALAYSIA_METHOD_ID",
        "JAKIM, Malaysia",
        20.0, IshaMode.Angle(18.0),
        MethodOffsets(fajr = 12, sunrise = -2, dhuhr = 3, asr = 2, maghrib = 2, isha = 2),
    ),

    /**
     * Organisations Islamiques de France.
     * offsets: fajr -5, sunrise +1, dhuhr +5, maghrib +1, isha +4.
     */
    FRANCE_OIF(
        "FRANCE_OIF",
        "Organisations Islamiques de France",
        12.0, IshaMode.Angle(12.0),
        MethodOffsets(fajr = -5, sunrise = 1, dhuhr = 5, maghrib = 1, isha = 4),
    );

    companion object {
        fun fromId(id: String): CalcMethod =
            entries.firstOrNull { it.methodId == id } ?: MUSLIM_WORLD_LEAGUE
    }
}

// ─── Juristic method ──────────────────────────────────────────────────────────

/** Madhab for Asr shadow multiplier. */
enum class JuristicMethod(val madhab: Int) {
    SHAFI_MALIKI_HANBALI(1),
    HANAFI(2),
}

// ─── DST / higher-lat / corrections ──────────────────────────────────────────

/** Daylight Saving Time offset mode. */
enum class DstMode { AUTOMATIC, PLUS_ONE, MINUS_ONE }

/** High-latitude fallback when the sun depression angle is unreachable. */
enum class HigherLatMode { NONE, MIDDLE_OF_NIGHT, SEVENTH_OF_NIGHT, ANGLE_BASED }

/**
 * Per-prayer manual time corrections in minutes (range −60..+60).
 * Applied after astronomical calculation AND after [MethodOffsets].
 */
data class ManualCorrections(
    val fajr:    Int = 0,
    val sunrise: Int = 0,
    val dhuhr:   Int = 0,
    val asr:     Int = 0,
    val maghrib: Int = 0,
    val isha:    Int = 0,
) {
    val isAllZero: Boolean
        get() = fajr == 0 && sunrise == 0 && dhuhr == 0 && asr == 0 && maghrib == 0 && isha == 0
}

// ─── Root settings ────────────────────────────────────────────────────────────

/**
 * Complete prayer calculation configuration.
 *
 * When [autoSettings] is true, [method] and [juristic] are resolved from the
 * user's country code via [CountryMethodMap]; stored values are preserved so
 * they become the starting point when auto is disabled later.
 */
data class PrayerCalcSettings(
    val autoSettings:  Boolean           = true,
    val method:        CalcMethod        = CalcMethod.MOROCCO_MWL,
    val juristic:      JuristicMethod    = JuristicMethod.SHAFI_MALIKI_HANBALI,
    val dstMode:       DstMode           = DstMode.AUTOMATIC,
    val corrections:   ManualCorrections = ManualCorrections(),
    val higherLatMode: HigherLatMode     = HigherLatMode.NONE,
) {
    /**
     * Returns settings that will actually be used for calculation.
     * When [autoSettings] is true, method/juristic come from [CountryMethodMap]
     * and DST/corrections/higher-lat are defaulted.
     */
    fun resolve(countryCode: String): PrayerCalcSettings {
        if (!autoSettings) return this
        val cfg = CountryMethodMap.configFor(countryCode)
        return copy(
            method        = cfg.method,
            juristic      = cfg.juristic,
            dstMode       = DstMode.AUTOMATIC,
            corrections   = ManualCorrections(),
            higherLatMode = HigherLatMode.NONE,
        )
    }
}