package com.lhacenmed.khatmah.data.location

/**
 * Process-scoped in-memory cache for location data.
 *
 * Eliminates re-fetches on back-navigation and prevents recomposition-during-animation
 * jank: composables initialize their state directly from cache, so no loading→loaded
 * transition fires while the enter animation is still running.
 *
 * [countries] is @Volatile — written on IO, read on Main.
 * [citiesMap] is synchronized — concurrent writes from different city pages are safe.
 */
object LocationCache {

    @Volatile var countries: List<CountriesApi.Country>? = null

    private val citiesMap = HashMap<String, List<String>>()

    fun getCities(country: String): List<String>? =
        synchronized(citiesMap) { citiesMap[country] }

    fun putCities(country: String, cities: List<String>) =
        synchronized(citiesMap) { citiesMap[country] = cities }
}