package com.lhacenmed.khatmah.feature.prayer.data

/**
 * Auto-detection map: ISO 3166-1 alpha-2 code → [com.lhacenmed.khatmah.data.prayer.CalcMethod] + [com.lhacenmed.khatmah.data.prayer.JuristicMethod].
 *
 * Used when [com.lhacenmed.khatmah.data.prayer.PrayerCalcSettings.autoSettings] is true to select the official
 * calculation authority for the user's country without any manual input.
 * Countries not listed fall back to [DEFAULT].
 */
object CountryMethodMap {

    data class CountryConfig(val method: CalcMethod, val juristic: JuristicMethod)

    private val DEFAULT = CountryConfig(
        method   = CalcMethod.MUSLIM_WORLD_LEAGUE,
        juristic = JuristicMethod.SHAFI_MALIKI_HANBALI,
    )

    // Convenience shorthands
    private val MWL_S = DEFAULT
    private val MWL_H = CountryConfig(CalcMethod.MUSLIM_WORLD_LEAGUE, JuristicMethod.HANAFI)
    private val KHI_H = CountryConfig(CalcMethod.KARACHI,             JuristicMethod.HANAFI)
    private val KHI_S = CountryConfig(CalcMethod.KARACHI,             JuristicMethod.SHAFI_MALIKI_HANBALI)

    private val map: Map<String, CountryConfig> = buildMap {
        // ── Arabian Peninsula ─────────────────────────────────────────────────
        put("SA", CountryConfig(CalcMethod.UMM_AL_QURA,   JuristicMethod.SHAFI_MALIKI_HANBALI))
        put("AE", CountryConfig(CalcMethod.UAE,           JuristicMethod.SHAFI_MALIKI_HANBALI))
        put("KW", CountryConfig(CalcMethod.KUWAIT,        JuristicMethod.SHAFI_MALIKI_HANBALI))
        put("QA", CountryConfig(CalcMethod.QATAR,         JuristicMethod.SHAFI_MALIKI_HANBALI))
        put("BH", CountryConfig(CalcMethod.BAHRAIN,       JuristicMethod.SHAFI_MALIKI_HANBALI))
        put("OM", CountryConfig(CalcMethod.OMAN,          JuristicMethod.SHAFI_MALIKI_HANBALI))
        put("YE", CountryConfig(CalcMethod.MOON_SIGHTING, JuristicMethod.SHAFI_MALIKI_HANBALI))

        // ── Levant / Iraq ─────────────────────────────────────────────────────
        put("JO", CountryConfig(CalcMethod.JORDAN,        JuristicMethod.SHAFI_MALIKI_HANBALI))
        put("LB", CountryConfig(CalcMethod.LEBANON,       JuristicMethod.SHAFI_MALIKI_HANBALI))
        put("SY", CountryConfig(CalcMethod.MOON_SIGHTING, JuristicMethod.SHAFI_MALIKI_HANBALI))
        put("IQ", CountryConfig(CalcMethod.MOON_SIGHTING, JuristicMethod.SHAFI_MALIKI_HANBALI))
        put("PS", CountryConfig(CalcMethod.MOON_SIGHTING, JuristicMethod.SHAFI_MALIKI_HANBALI))
        put("IL", CountryConfig(CalcMethod.MOON_SIGHTING, JuristicMethod.SHAFI_MALIKI_HANBALI))

        // ── North Africa ──────────────────────────────────────────────────────
        put("MA", CountryConfig(CalcMethod.MOROCCO_MWL,   JuristicMethod.SHAFI_MALIKI_HANBALI))
        put("DZ", CountryConfig(CalcMethod.ALGERIA,       JuristicMethod.SHAFI_MALIKI_HANBALI))
        put("TN", CountryConfig(CalcMethod.TUNISIA,       JuristicMethod.SHAFI_MALIKI_HANBALI))
        put("LY", CountryConfig(CalcMethod.LIBYA,         JuristicMethod.SHAFI_MALIKI_HANBALI))
        put("EG", CountryConfig(CalcMethod.EGYPTIAN,      JuristicMethod.SHAFI_MALIKI_HANBALI))
        put("SD", CountryConfig(CalcMethod.SUDAN,         JuristicMethod.SHAFI_MALIKI_HANBALI))
        put("MR", MWL_S)

        // ── West Africa ───────────────────────────────────────────────────────
        put("SN", MWL_S); put("ML", MWL_S); put("NE", MWL_S)
        put("GN", MWL_S); put("GM", MWL_S); put("GW", MWL_S)
        put("SL", MWL_S); put("LR", MWL_S); put("CI", MWL_S)
        put("GH", MWL_S); put("TG", MWL_S); put("BJ", MWL_S)
        put("NG", MWL_S); put("BF", MWL_S)

        // ── Central / East Africa ─────────────────────────────────────────────
        put("TD", MWL_S); put("CM", MWL_S); put("CF", MWL_S)
        put("SO", KHI_S); put("ET", KHI_S); put("ER", KHI_S)
        put("DJ", KHI_S); put("KE", KHI_S); put("TZ", KHI_S)
        put("UG", KHI_S); put("MZ", KHI_S); put("MW", KHI_S)
        put("MG", KHI_S); put("KM", KHI_S)

        // ── South Asia (predominantly Hanafi) ─────────────────────────────────
        put("AF", KHI_H); put("PK", KHI_H); put("IN", KHI_H)
        put("BD", KHI_H); put("NP", KHI_H); put("BT", KHI_H)
        put("LK", KHI_S); put("MV", KHI_S)

        // ── Southeast Asia ────────────────────────────────────────────────────
        put("MY", CountryConfig(CalcMethod.JAKIM_MALAYSIA,  JuristicMethod.SHAFI_MALIKI_HANBALI))
        put("ID", CountryConfig(CalcMethod.JAKIM_INDONESIA, JuristicMethod.SHAFI_MALIKI_HANBALI))
        put("SG", CountryConfig(CalcMethod.MUIS_SINGAPORE,  JuristicMethod.SHAFI_MALIKI_HANBALI))
        put("BN", CountryConfig(CalcMethod.JAKIM_MALAYSIA,  JuristicMethod.SHAFI_MALIKI_HANBALI))
        put("PH", CountryConfig(CalcMethod.JAKIM_INDONESIA, JuristicMethod.SHAFI_MALIKI_HANBALI))
        put("TH", MWL_S); put("MM", MWL_S); put("VN", MWL_S); put("KH", MWL_S)

        // ── Turkey & Cyprus ───────────────────────────────────────────────────
        put("TR", CountryConfig(CalcMethod.TURKEY, JuristicMethod.HANAFI))
        put("CY", CountryConfig(CalcMethod.TURKEY, JuristicMethod.HANAFI))

        // ── Central Asia (Hanafi) ─────────────────────────────────────────────
        put("UZ", KHI_H); put("KZ", KHI_H); put("TJ", KHI_H)
        put("KG", KHI_H); put("TM", KHI_H)

        // ── Caucasus ──────────────────────────────────────────────────────────
        put("AZ", KHI_H); put("GE", MWL_S); put("AM", MWL_S)

        // ── Russia ────────────────────────────────────────────────────────────
        put("RU", CountryConfig(CalcMethod.RUSSIA, JuristicMethod.HANAFI))

        // ── Balkans ───────────────────────────────────────────────────────────
        put("BA", MWL_H); put("AL", MWL_H); put("MK", MWL_H)
        put("RS", MWL_H); put("XK", MWL_H); put("ME", MWL_H)

        // ── Western Europe ────────────────────────────────────────────────────
        put("FR", CountryConfig(CalcMethod.FRANCE_OIF,      JuristicMethod.SHAFI_MALIKI_HANBALI))
        put("NL", CountryConfig(CalcMethod.NETHERLANDS_MWL, JuristicMethod.SHAFI_MALIKI_HANBALI))
        put("GR", CountryConfig(CalcMethod.MUSLIM_WORLD_LEAGUE, JuristicMethod.HANAFI))
        put("GB", MWL_S); put("DE", MWL_S); put("BE", MWL_S)
        put("CH", MWL_S); put("AT", MWL_S); put("IT", MWL_S)
        put("ES", MWL_S); put("PT", MWL_S); put("SE", MWL_S)
        put("NO", MWL_S); put("DK", MWL_S); put("FI", MWL_S)
        put("PL", MWL_S); put("IE", MWL_S); put("LU", MWL_S)

        // ── North America ─────────────────────────────────────────────────────
        put("US", CountryConfig(CalcMethod.NORTH_AMERICA, JuristicMethod.SHAFI_MALIKI_HANBALI))
        put("CA", CountryConfig(CalcMethod.NORTH_AMERICA, JuristicMethod.SHAFI_MALIKI_HANBALI))
        put("MX", MWL_S)

        // ── Iran ──────────────────────────────────────────────────────────────
        put("IR", KHI_S)

        // ── China ─────────────────────────────────────────────────────────────
        put("CN", KHI_H)

        // ── Oceania ───────────────────────────────────────────────────────────
        put("AU", MWL_S); put("NZ", MWL_S)

        // ── Americas ──────────────────────────────────────────────────────────
        put("BR", MWL_S); put("AR", MWL_S); put("CO", MWL_S)
        put("VE", MWL_S); put("CL", MWL_S); put("PE", MWL_S)
        put("AI", MWL_S)

        // ── Rest of Africa ────────────────────────────────────────────────────
        put("AO", MWL_S); put("CD", MWL_S); put("CG", MWL_S)
        put("GA", MWL_S); put("ZA", MWL_S); put("ZM", MWL_S)
        put("ZW", MWL_S); put("BW", MWL_S); put("NA", MWL_S)
        put("LS", MWL_S); put("SZ", MWL_S); put("RW", MWL_S)
        put("BI", MWL_S); put("SS", MWL_S)
    }

    /** Returns the official config for [iso2] (uppercase), or a MWL/Shafi fallback if unknown. */
    fun configFor(iso2: String): CountryConfig = map[iso2.uppercase()] ?: DEFAULT
}