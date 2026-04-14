package com.lhacenmed.khatmah.ui.onboarding

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.lhacenmed.khatmah.R
import com.lhacenmed.khatmah.ui.common.Route
import com.lhacenmed.khatmah.ui.nav.LocalNavController
import com.lhacenmed.khatmah.util.LocationHelper
import com.lhacenmed.khatmah.util.OnboardingPrefs
import kotlinx.coroutines.launch

/**
 * Manual location entry — shown when the user denies location permission or
 * GPS fails. Uses Android's platform [android.location.Geocoder] to resolve
 * a typed city name to coordinates; no bundled JSON required.
 *
 * On success: [OnboardingPrefs.complete] → Main.
 * On skip:    zero coordinates stored → Main (prayer times unavailable).
 */
@Composable
fun ManualLocationPage() {
    val nav     = LocalNavController.current
    val context = LocalContext.current
    val scope   = rememberCoroutineScope()

    var query    by rememberSaveable { mutableStateOf("") }
    var loading  by remember { mutableStateOf(false) }
    var notFound by remember { mutableStateOf(false) }

    fun navigateMain() = nav.navigate(Route.MAIN) { popUpTo(0) { inclusive = true } }

    fun search() {
        if (query.isBlank() || loading) return
        scope.launch {
            loading  = true
            notFound = false
            val loc = LocationHelper.coordsForName(context, query)
            loading = false
            if (loc != null) {
                val city = LocationHelper.cityName(context, loc.latitude, loc.longitude)
                    .ifBlank { query.trim() }   // fall back to what the user typed
                OnboardingPrefs.complete(context, city, loc.latitude, loc.longitude)
                navigateMain()
            } else {
                notFound = true
            }
        }
    }

    OnboardingPage(
        icon          = Icons.Outlined.LocationOn,
        title         = stringResource(R.string.manual_location_title),
        description   = stringResource(R.string.manual_location_desc),
        actionLabel   = if (loading) stringResource(R.string.manual_location_searching)
        else         stringResource(R.string.manual_location_search),
        onAction      = ::search,
        actionEnabled = query.isNotBlank() && !loading,
        skipLabel     = stringResource(R.string.skip),
        onSkip        = {
            OnboardingPrefs.complete(context, "", 0.0, 0.0)
            navigateMain()
        },
        extraContent  = {
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value         = query,
                onValueChange = { query = it; notFound = false },
                placeholder   = { Text(stringResource(R.string.manual_location_hint)) },
                singleLine    = true,
                isError       = notFound,
                supportingText = if (notFound) {
                    { Text(stringResource(R.string.manual_location_not_found)) }
                } else null,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { search() }),
                modifier = Modifier.fillMaxWidth(),
            )
        },
    )
}