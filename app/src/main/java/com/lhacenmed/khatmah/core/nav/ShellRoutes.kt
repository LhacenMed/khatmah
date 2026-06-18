package com.lhacenmed.khatmah.core.nav

import android.net.Uri

/**
 * Internal route strings for the one-time onboarding wizard, hosted by
 * [com.lhacenmed.khatmah.onboarding.OnboardingActivity]'s self-contained NavHost.
 * (The main app uses Activities + [Dest]; this small linear flow keeps a NavHost.)
 */
object ShellRoutes {
    const val ONBOARDING_LANGUAGE       = "onboarding_language"
    const val ONBOARDING_NOTIFICATIONS  = "onboarding_notifications"
    const val ONBOARDING_LOCATION       = "onboarding_location"
    const val ONBOARDING_COUNTRY_SELECT = "onboarding_country_select?fromSettings={fromSettings}"
    const val ONBOARDING_CITY_SELECT    = "onboarding_city_select?country={country}&iso2={iso2}&fromSettings={fromSettings}"

    fun countrySelect(fromSettings: Boolean = false) =
        "onboarding_country_select?fromSettings=$fromSettings"

    fun citySelect(country: String, iso2: String, fromSettings: Boolean = false) =
        "onboarding_city_select?country=${Uri.encode(country)}&iso2=${Uri.encode(iso2)}&fromSettings=$fromSettings"
}
