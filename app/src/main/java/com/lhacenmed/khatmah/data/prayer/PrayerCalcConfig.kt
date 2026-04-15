package com.lhacenmed.khatmah.data.prayer

// ─── Isha mode ────────────────────────────────────────────────────────────────

/** How Isha time is computed — angle-based or fixed offset from Maghrib. */
sealed class IshaMode {
    /** Sun depression angle below the horizon (degrees). */
    data class Angle(val degrees: Double) : IshaMode()
    /** Fixed minutes after Maghrib. */
    data class FixedMinutes(val minutes: Int) : IshaMode()
}

// ─── Calculation methods ──────────────────────────────────────────────────────

/**
 * All supported prayer time calculation methods.
 *
 * [methodId]   Stable key used for SharedPreferences persistence.
 * [fajrAngle]  Sun depression angle for Fajr (degrees below horizon).
 * [ishaMode]   Isha computation — angle or fixed offset from Maghrib.
 */
enum class CalcMethod(
    val methodId:    String,
    val displayName: String,
    val fajrAngle:   Double,
    val ishaMode:    IshaMode,
) {
    UMM_AL_QURA(
        "UMM_AL_QURA", "Umm Al-Qura",
        18.5, IshaMode.FixedMinutes(90),
    ),
    UMM_AL_QURA_RAMADAN(
        "UMM_AL_QURA_RAMADAN", "Umm Al-Qura (Ramadan)",
        18.5, IshaMode.FixedMinutes(120),
    ),
    MUSLIM_WORLD_LEAGUE(
        "MUSLIM_WORLD_WORLD_LEAGUE_METHOD_ID", "Muslim World League (MWL)",
        18.0, IshaMode.Angle(17.0),
    ),
    EGYPTIAN(
        "EGYPTIAN", "Egyptian General Authority",
        19.5, IshaMode.Angle(17.5),
    ),
    UAE(
        "UAE", "UAE, General Authority of Islamic Affairs and Endowments",
        18.2, IshaMode.Angle(18.2),
    ),
    KUWAIT(
        "KUWAIT", "Kuwait, Ministry of Awqaf and Islamic Affairs",
        18.0, IshaMode.Angle(17.5),
    ),
    QATAR(
        "QATAR", "Qatar, Ministry of Awqaf and Islamic Affairs",
        18.0, IshaMode.FixedMinutes(90),
    ),
    NORTH_AMERICA(
        "NORTH_AMERICA_ISNA", "North America (ISNA)",
        15.0, IshaMode.Angle(15.0),
    ),
    TURKEY(
        "TURKEY_METHOD_ID", "Turkey, Diyanet Isleri Baskanligi",
        18.0, IshaMode.Angle(17.0),
    ),
    MOON_SIGHTING(
        "MOON_SIGHTING", "Moon Sighting Committee",
        18.0, IshaMode.Angle(18.0),
    ),
    KARACHI(
        "KARACHI_METHOD_ID", "Karachi, Islamic University",
        18.0, IshaMode.Angle(18.0),
    ),
    MOROCCO_MWL(
        "MOROCCO_MWL", "Morocco, Muslim World League (MWL)",
        18.0, IshaMode.Angle(17.0),
    ),
    ALGERIA(
        "ALGERIA_METHOD_ID", "Algeria, Ministry of Awqaf and Islamic Affairs",
        18.0, IshaMode.Angle(17.0),
    ),
    TUNISIA(
        "TUNISIA", "Tunisia Method",
        18.2, IshaMode.Angle(18.2),
    ),
    JORDAN(
        "JORDAN", "Jordan Method",
        18.0, IshaMode.Angle(18.0),
    ),
    BAHRAIN(
        "BAHRAIN", "Bahrain Method",
        18.2, IshaMode.Angle(18.0),
    ),
    OMAN(
        "OMAN", "Oman Method",
        17.7, IshaMode.Angle(18.0),
    ),
    LEBANON(
        "LEBANON", "Lebanon Method",
        20.0, IshaMode.Angle(18.0),
    ),
    LIBYA(
        "LIBYA", "Libya Method",
        18.5, IshaMode.Angle(18.0),
    ),
    SUDAN(
        "SUDAN", "Sudan Method",
        18.5, IshaMode.Angle(18.0),
    ),
    JAKIM_INDONESIA(
        "INDONESIA", "JAKIM, Indonesia",
        20.0, IshaMode.Angle(18.0),
    ),
    JAKIM_MALAYSIA(
        "MALAYSIA_METHOD_ID", "JAKIM, Malaysia",
        20.0, IshaMode.Angle(18.0),
    ),
    NETHERLANDS_MWL(
        "NETHERLANDS_MWL", "Netherlands, Muslim World League (MWL)",
        18.0, IshaMode.Angle(17.0),
    ),
    MUIS_SINGAPORE(
        "SINGAPORE_MUIS", "MUIS (Majlis Ugama Islam Singapura)",
        20.0, IshaMode.Angle(18.0),
    ),
    RUSSIA(
        "RUSSIA", "Spiritual Administration of Muslims of Russia",
        16.0, IshaMode.Angle(15.0),
    ),
    FIXED_ISHA(
        "FIXED_ISHA", "Fixed Isha Angle Interval",
        19.5, IshaMode.FixedMinutes(90),
    ),
    FRANCE_OIF(
        "FRANCE_OIF", "Organisations Islamiques de France",
        12.0, IshaMode.Angle(12.0),
    ),
    FRANCE_15(
        "FRANCE_15", "France 15°",
        15.0, IshaMode.Angle(15.0),
    ),
    FRANCE_18(
        "FRANCE_18", "France 18°",
        18.0, IshaMode.Angle(18.0),
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
 * Applied after all astronomical calculations.
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
 * user's country code via [CountryMethodMap]; stored values are preserved so they
 * become the starting point when auto is disabled later.
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
     * When [autoSettings] is true, method/juristic come from the country map
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