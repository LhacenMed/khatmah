package com.lhacenmed.khatmah.util

import android.Manifest.permission.ACCESS_FINE_LOCATION
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Looper
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Thin wrapper around [LocationManager] for coroutine-friendly GPS access.
 * No Google Play Services dependency — uses only platform APIs.
 */
object LocationHelper {

    /**
     * Returns the device's best available [Location], or null if:
     *  - Permission is not granted.
     *  - No enabled provider exists (airplane mode, GPS off, etc.).
     *  - No fix is obtained within 20 seconds.
     */
    suspend fun getCurrent(context: Context): Location? {
        if (!hasPermission(context)) return null
        val lm       = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val provider = bestProvider(lm) ?: return null

        // Last-known is instant — accept it when reasonably fresh (< 2 minutes old).
        @Suppress("MissingPermission")
        lm.getLastKnownLocation(provider)
            ?.takeIf { System.currentTimeMillis() - it.time < 120_000L }
            ?.let { return it }

        // Request a fresh fix with a hard timeout.
        return withTimeoutOrNull(20_000L) { freshFix(lm, provider, context) }
    }

    /**
     * Reverse-geocodes [lat]/[lng] to the nearest city name using the platform Geocoder.
     * Returns an empty string when Geocoder is unavailable or the address has no locality.
     */
    suspend fun cityName(context: Context, lat: Double, lng: Double): String =
        withContext(Dispatchers.IO) {
            if (!Geocoder.isPresent()) return@withContext ""
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    cityNameAsync(context, lat, lng)
                } else {
                    @Suppress("DEPRECATION")
                    Geocoder(context)
                        .getFromLocation(lat, lng, 1)
                        ?.firstOrNull()
                        ?.locality
                        .orEmpty()
                }
            }.getOrDefault("")
        }

    /**
     * Forward-geocodes [query] (city name / address) to coordinates.
     * Returns null when nothing is found or Geocoder is unavailable.
     */
    suspend fun coordsForName(context: Context, query: String): Location? =
        withContext(Dispatchers.IO) {
            if (!Geocoder.isPresent()) return@withContext null
            runCatching {
                @Suppress("DEPRECATION")
                Geocoder(context)
                    .getFromLocationName(query.trim(), 1)
                    ?.firstOrNull()
                    ?.let { addr ->
                        Location(LocationManager.GPS_PROVIDER).apply {
                            latitude  = addr.latitude
                            longitude = addr.longitude
                        }
                    }
            }.getOrNull()
        }

    // ── Private ───────────────────────────────────────────────────────────────

    private fun hasPermission(context: Context) =
        ContextCompat.checkSelfPermission(context, ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED

    private fun bestProvider(lm: LocationManager): String? = when {
        lm.isProviderEnabled(LocationManager.GPS_PROVIDER)     -> LocationManager.GPS_PROVIDER
        lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
        else                                                    -> null
    }

    private suspend fun freshFix(lm: LocationManager, provider: String, context: Context): Location? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            freshFixApiR(lm, provider, context)
        } else {
            freshFixLegacy(lm, provider)
        }

    @RequiresApi(Build.VERSION_CODES.R)
    private suspend fun freshFixApiR(lm: LocationManager, provider: String, context: Context): Location? =
        suspendCancellableCoroutine { cont ->
            val cts = android.os.CancellationSignal()
            cont.invokeOnCancellation { cts.cancel() }
            @Suppress("MissingPermission")
            lm.getCurrentLocation(provider, cts, context.mainExecutor) { loc ->
                if (cont.isActive) cont.resume(loc)
            }
        }

    @Suppress("DEPRECATION")
    private suspend fun freshFixLegacy(lm: LocationManager, provider: String): Location? =
        suspendCancellableCoroutine { cont ->
            val listener = object : LocationListener {
                override fun onLocationChanged(loc: Location) {
                    lm.removeUpdates(this)
                    if (cont.isActive) cont.resume(loc)
                }
                override fun onStatusChanged(p: String?, s: Int, e: Bundle?) = Unit
                override fun onProviderEnabled(p: String)  = Unit
                override fun onProviderDisabled(p: String) = Unit
            }
            @Suppress("MissingPermission")
            lm.requestSingleUpdate(provider, listener, Looper.getMainLooper())
            cont.invokeOnCancellation { lm.removeUpdates(listener) }
        }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private suspend fun cityNameAsync(context: Context, lat: Double, lng: Double): String =
        suspendCancellableCoroutine { cont ->
            Geocoder(context).getFromLocation(lat, lng, 1) { addresses ->
                if (cont.isActive) cont.resume(addresses.firstOrNull()?.locality.orEmpty())
            }
        }
}