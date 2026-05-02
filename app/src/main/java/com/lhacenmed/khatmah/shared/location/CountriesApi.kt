package com.lhacenmed.khatmah.shared.location

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Two free, no-auth APIs:
 *  - CountriesNow (countriesnow.space) — country and city lists.
 *  - Nominatim / OSM — city → GPS coordinates.
 *  - FlagCDN (flagcdn.com) — country flag SVGs via iso2 code.
 */
object CountriesApi {

    private const val NOW       = "https://countriesnow.space/api/v0.1"
    private const val NOMINATIM = "https://nominatim.openstreetmap.org"
    private const val FLAG_CDN  = "https://flagcdn.com"

    data class Country(val name: String, val iso2: String) {
        /** SVG flag URL — empty string when iso2 is unknown. */
        val flagUrl: String = if (iso2.isNotEmpty()) "$FLAG_CDN/${iso2.lowercase()}.svg" else ""
    }

    /** All countries sorted alphabetically. */
    suspend fun countries(): List<Country> = withContext(Dispatchers.IO) {
        val arr = get("$NOW/countries/iso").obj().getJSONArray("data")
        (0 until arr.length()).map { i ->
            arr.getJSONObject(i).run { Country(getString("name"), optString("Iso2")) }
        }.sortedBy { it.name }
    }

    /** Cities for [country] sorted alphabetically. */
    suspend fun cities(country: String): List<String> = withContext(Dispatchers.IO) {
        val arr = post("$NOW/countries/cities", """{"country":"$country"}""")
            .obj().getJSONArray("data")
        (0 until arr.length()).map { i -> arr.getString(i) }.sorted()
    }

    /**
     * Resolves [city] + [country] to coordinates via Nominatim.
     * Returns null on network error or if the city cannot be found.
     */
    suspend fun geocode(city: String, country: String): Pair<Double, Double>? =
        withContext(Dispatchers.IO) {
            runCatching {
                val q   = URLEncoder.encode("$city, $country", "UTF-8")
                val arr = JSONArray(get("$NOMINATIM/search?q=$q&format=json&limit=1&addressdetails=0"))
                if (arr.length() == 0) return@runCatching null
                arr.getJSONObject(0).run { Pair(getDouble("lat"), getDouble("lon")) }
            }.getOrNull()
        }

    // ── HTTP ──────────────────────────────────────────────────────────────────

    private fun String.obj() = JSONObject(this)

    private fun get(url: String): String = request(url, method = "GET")

    private fun post(url: String, body: String): String = request(url, method = "POST", body = body)

    private fun request(url: String, method: String, body: String? = null): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.requestMethod = method
        conn.setRequestProperty("User-Agent",   "KhatmahApp/1.0")
        conn.setRequestProperty("Accept",       "application/json")
        if (body != null) {
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.outputStream.write(body.toByteArray())
        }
        return try { conn.inputStream.bufferedReader().readText() } finally { conn.disconnect() }
    }
}