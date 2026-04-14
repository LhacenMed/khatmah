package com.lhacenmed.khatmah.ui.onboarding

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.lhacenmed.khatmah.R
import com.lhacenmed.khatmah.ui.common.Route
import com.lhacenmed.khatmah.ui.nav.LocalNavController
import com.lhacenmed.khatmah.util.LocationHelper
import com.lhacenmed.khatmah.util.OnboardingPrefs
import kotlinx.coroutines.launch

private enum class LocState { Idle, Locating, Failed, Denied }

@Composable
fun LocationPermissionPage() {
    val nav     = LocalNavController.current
    val context = LocalContext.current
    val scope   = rememberCoroutineScope()

    var state by remember { mutableStateOf(LocState.Idle) }

    fun toMain()         = nav.navigate(Route.MAIN) { popUpTo(0) { inclusive = true } }
    fun toCountrySelect() = nav.navigate(Route.ONBOARDING_COUNTRY_SELECT)

    fun locateAndSave() {
        state = LocState.Locating
        scope.launch {
            val loc = LocationHelper.getCurrent(context)
            if (loc != null) {
                val city = LocationHelper.cityName(context, loc.latitude, loc.longitude)
                OnboardingPrefs.complete(context, city, loc.latitude, loc.longitude)
                toMain()
            } else {
                state = LocState.Failed
            }
        }
    }

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val granted = results[Manifest.permission.ACCESS_FINE_LOCATION]   == true
                || results[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) locateAndSave() else state = LocState.Denied
    }

    // If permission was already granted in a previous session, start immediately.
    LaunchedEffect(Unit) {
        val fine   = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
        if (fine == PackageManager.PERMISSION_GRANTED || coarse == PackageManager.PERMISSION_GRANTED) {
            locateAndSave()
        }
    }

    val showManual = state == LocState.Failed || state == LocState.Denied

    OnboardingPage(
        icon        = Icons.Outlined.LocationOn,
        title       = stringResource(R.string.location_title),
        description = when (state) {
            LocState.Denied -> stringResource(R.string.location_denied_desc)
            LocState.Failed -> stringResource(R.string.location_failed_desc)
            else            -> stringResource(R.string.location_desc)
        },
        actionLabel = when (state) {
            LocState.Locating -> stringResource(R.string.location_locating)
            LocState.Failed   -> stringResource(R.string.retry)
            else              -> stringResource(R.string.location_grant)
        },
        onAction = {
            when (state) {
                LocState.Failed   -> locateAndSave()
                LocState.Locating -> Unit
                else -> permLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                    )
                )
            }
        },
        actionEnabled = state != LocState.Locating,
        skipLabel = if (showManual) stringResource(R.string.location_choose_manually)
        else            stringResource(R.string.skip),
        onSkip = {
            if (showManual) toCountrySelect()
            else { OnboardingPrefs.complete(context, "", 0.0, 0.0); toMain() }
        },
        extraContent = {
            if (state == LocState.Locating) {
                Spacer(Modifier.height(16.dp))
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        },
    )
}