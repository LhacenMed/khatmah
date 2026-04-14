package com.lhacenmed.khatmah.ui.onboarding

import android.Manifest.permission.ACCESS_FINE_LOCATION
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import com.lhacenmed.khatmah.R
import com.lhacenmed.khatmah.ui.common.Route
import com.lhacenmed.khatmah.ui.nav.LocalNavController
import com.lhacenmed.khatmah.util.LocationHelper
import com.lhacenmed.khatmah.util.OnboardingPrefs

/**
 * Location states — deliberately separated so a GPS failure is never
 * confused with the user having actually denied the permission.
 */
private enum class LocationState {
    /** Initial — button launches the system permission dialog. */
    IDLE,
    /** Permission granted; GPS fix in progress. */
    LOCATING,
    /** System permission dialog returned denied. */
    DENIED,
    /** Permission granted but no GPS fix obtained within the timeout. */
    NOT_FOUND,
}

/**
 * Onboarding step 2 — ACCESS_FINE_LOCATION.
 *
 * Happy path:   grant → GPS fix → city name reverse-geocoded → [OnboardingPrefs.complete] → Main.
 * DENIED path:  action = "Choose Manually" → [ManualLocationPage].
 * NOT_FOUND:    GPS timed out despite permission being granted.
 *               Primary action = "Try Again"; secondary = "Choose Manually".
 * Skip:         onboarding marked complete with zero coords → Main (prayer times unavailable).
 *
 * Only the launcher callback may set DENIED; all GPS failures use NOT_FOUND.
 */
@Composable
fun LocationPermissionPage() {
    val nav     = LocalNavController.current
    val context = LocalContext.current

    var locState    by remember { mutableStateOf(LocationState.IDLE) }
    var permGranted by remember { mutableStateOf(false) }
    var retryCount  by remember { mutableIntStateOf(0) }

    fun navigateMain()   = nav.navigate(Route.MAIN) { popUpTo(0) { inclusive = true } }
    fun navigateManual() = nav.navigate(Route.ONBOARDING_MANUAL_LOCATION)
    fun skipLocation() {
        OnboardingPrefs.complete(context, "", 0.0, 0.0)
        navigateMain()
    }

    // Only this callback may set DENIED — it is the authoritative permission result.
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) permGranted = true else locState = LocationState.DENIED
    }

    // GPS flow — re-runs when permission is granted OR on manual retry.
    LaunchedEffect(permGranted, retryCount) {
        if (!permGranted) return@LaunchedEffect

        // Re-check at runtime (safety for permissions revoked mid-session).
        if (ContextCompat.checkSelfPermission(context, ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            locState = LocationState.DENIED
            return@LaunchedEffect
        }

        locState = LocationState.LOCATING

        val loc = LocationHelper.getCurrent(context)
        if (loc == null) {
            locState = LocationState.NOT_FOUND
            return@LaunchedEffect
        }

        val city = LocationHelper.cityName(context, loc.latitude, loc.longitude)
        OnboardingPrefs.complete(context, city, loc.latitude, loc.longitude)
        navigateMain()
    }

    OnboardingPage(
        icon        = Icons.Outlined.LocationOn,
        title       = stringResource(R.string.onboarding_location_title),
        description = when (locState) {
            LocationState.IDLE      -> stringResource(R.string.onboarding_location_desc)
            LocationState.LOCATING  -> stringResource(R.string.onboarding_location_locating)
            LocationState.DENIED    -> stringResource(R.string.onboarding_location_denied)
            LocationState.NOT_FOUND -> stringResource(R.string.onboarding_location_not_found)
        },
        actionLabel = when (locState) {
            LocationState.DENIED    -> stringResource(R.string.onboarding_location_manual)
            LocationState.NOT_FOUND -> stringResource(R.string.onboarding_location_retry)
            else                    -> stringResource(R.string.onboarding_location_action)
        },
        onAction = when (locState) {
            LocationState.IDLE      -> ({ launcher.launch(ACCESS_FINE_LOCATION) })
            LocationState.LOCATING  -> ({})
            LocationState.DENIED    -> ({ navigateManual() })
            LocationState.NOT_FOUND -> ({ retryCount++ })
        },
        actionEnabled = locState != LocationState.LOCATING,
        skipLabel     = stringResource(R.string.skip).takeIf { locState != LocationState.LOCATING },
        onSkip        = ::skipLocation,
        extraContent  = {
            // When GPS timed out, also offer a direct path to manual entry below Retry.
            if (locState == LocationState.NOT_FOUND) {
                TextButton(onClick = ::navigateManual) {
                    Text(stringResource(R.string.onboarding_location_manual))
                }
            }
        },
    )
}