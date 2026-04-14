package com.lhacenmed.khatmah.util

import android.annotation.SuppressLint
import android.content.Context
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Looper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.coroutines.resume

/**
 * GPS acquisition and reverse geocoding.
 *
 * Acquisition order:
 *  1. Recent last-known fix from any enabled provider (instant).
 *  2. Fresh fix from NETWORK_PROVIDER (seconds) before falling back to GPS (minutes).
 *
 * Reverse geocoding order:
 *  1. Android [Geocoder] (if GMS is present).
 *  2. Nominatim / OSM (no GMS dependency).
 */
object LocationHelper {

    private const val FRESH_MS   = 3 * 60 * 1000L  // last-known freshness window
    private const val TIMEOUT_MS = 12_000L          // max wait for fresh fix

    @SuppressLint("MissingPermission")
    suspend fun getCurrent(context: Context): Location? = withContext(Dispatchers.IO) {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return@withContext null

        recentKnown(lm)?.let { return@withContext it }

        val provider = when {
            lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
            lm.isProviderEnabled(LocationManager.GPS_PROVIDER)     -> LocationManager.GPS_PROVIDER
            else -> return@withContext null
        }

        withTimeoutOrNull(TIMEOUT_MS) { freshFix(lm, provider) }
    }

    /**
     * Reverse-geocodes [lat]/[lng] to a city name.
     * Falls back to Nominatim if Android Geocoder is unavailable.
     */
    suspend fun cityName(context: Context, lat: Double, lng: Double): String =
        withContext(Dispatchers.IO) {
            geocoderCity(context, lat, lng) ?: nominatimCity(lat, lng) ?: ""
        }

    // ── Private ───────────────────────────────────────────────────────────────

    @SuppressLint("MissingPermission")
    private fun recentKnown(lm: LocationManager): Location? =
        listOf(
            LocationManager.NETWORK_PROVIDER,
            LocationManager.GPS_PROVIDER,
            LocationManager.PASSIVE_PROVIDER,
        ).filter { lm.isProviderEnabled(it) }
            .mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
            .filter { System.currentTimeMillis() - it.time < FRESH_MS }
            .minByOrNull { it.accuracy }   // lower value = more accurate

    @SuppressLint("MissingPermission")
    private suspend fun freshFix(lm: LocationManager, provider: String): Location? =
        suspendCancellableCoroutine { cont ->
            val listener = object : LocationListener {
                override fun onLocationChanged(loc: Location) {
                    lm.removeUpdates(this)
                    if (cont.isActive) cont.resume(loc)
                }
                override fun onProviderDisabled(p: String) {
                    lm.removeUpdates(this)
                    if (cont.isActive) cont.resume(null)
                }
            }
            runCatching {
                lm.requestLocationUpdates(provider, 0L, 0f, listener, Looper.getMainLooper())
            }.onFailure { if (cont.isActive) cont.resume(null) }
            cont.invokeOnCancellation { runCatching { lm.removeUpdates(listener) } }
        }

    private suspend fun geocoderCity(context: Context, lat: Double, lng: Double): String? =
        runCatching {
            if (!Geocoder.isPresent()) return@runCatching null
            val geo = Geocoder(context, Locale.getDefault())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                suspendCancellableCoroutine { cont ->
                    geo.getFromLocation(lat, lng, 1) { addresses ->
                        cont.resume(addresses.firstOrNull()?.locality)
                    }
                }
            } else {
                @Suppress("DEPRECATION")
                geo.getFromLocation(lat, lng, 1)?.firstOrNull()?.locality
            }
        }.getOrNull()

    private suspend fun nominatimCity(lat: Double, lng: Double): String? =
        runCatching {
            val conn = URL(
                "https://nominatim.openstreetmap.org/reverse?lat=$lat&lon=$lng&format=json"
            ).openConnection() as HttpURLConnection
            conn.setRequestProperty("User-Agent", "KhatmahApp/1.0")
            val json = try { conn.inputStream.bufferedReader().readText() } finally { conn.disconnect() }
            val addr = JSONObject(json).optJSONObject("address") ?: return@runCatching null
            addr.optString("city").ifEmpty { null }
                ?: addr.optString("town").ifEmpty { null }
                ?: addr.optString("village").ifEmpty { null }
        }.getOrNull()
}