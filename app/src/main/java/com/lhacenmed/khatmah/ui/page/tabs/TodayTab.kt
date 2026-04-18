package com.lhacenmed.khatmah.ui.page.tabs

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lhacenmed.khatmah.R
import com.lhacenmed.khatmah.data.prayer.PrayerRepository
import com.lhacenmed.khatmah.ui.common.Route
import com.lhacenmed.khatmah.ui.nav.LocalNavController
import com.lhacenmed.khatmah.ui.nav.NavScreen
import com.lhacenmed.khatmah.ui.theme.OutfitFamily
import com.lhacenmed.khatmah.util.LocationHelper
import com.lhacenmed.khatmah.util.OnboardingPrefs
import kotlinx.coroutines.launch

val TodayTab = NavScreen(
    route    = Route.TODAY,
    iconRes  = R.drawable.ic_book,
    labelRes = R.string.today,
) { padding -> TodayScreen(padding) }

@Composable
private fun TodayScreen(padding: PaddingValues) {
    val context = LocalContext.current
    val nav     = LocalNavController.current
    val scope   = rememberCoroutineScope()

    // PrayerRepository is lightweight (wraps a DB singleton) — safe to hold in remember.
    val repo = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) PrayerRepository(context) else null
    }

    var refreshing by remember { mutableStateOf(false) }

    /**
     * 1. Attempts a fresh GPS fix and updates [OnboardingPrefs] when successful,
     *    including the ISO country code so auto prayer settings resolve correctly.
     * 2. Wipes the prayer cache and recomputes today — using new coords if available,
     *    or the previously stored coords if GPS is unavailable.
     */
    fun refresh() {
        scope.launch {
            refreshing = true
            val loc = LocationHelper.getCurrent(context)
            if (loc != null) {
                val info = LocationHelper.geoInfo(context, loc.latitude, loc.longitude)
                val city = info.city.ifBlank { OnboardingPrefs.location(context)?.cityName.orEmpty() }
                OnboardingPrefs.complete(context, city, loc.latitude, loc.longitude, info.countryCode)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                repo?.refresh()
            }
            refreshing = false
        }
    }

    Box(
        modifier         = Modifier
            .fillMaxSize()
            .padding(padding),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(stringResource(R.string.today), fontFamily = OutfitFamily)
            TextButton(onClick = { nav.navigate(Route.THEME_SETTINGS) }) {
                Text(stringResource(R.string.theme_settings))
            }
            TextButton(onClick = { nav.navigate(Route.LANGUAGE) }) {
                Text(stringResource(R.string.language_settings))
            }
            Spacer(Modifier.height(24.dp))

            if (refreshing) {
                CircularProgressIndicator()
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.today_refreshing))
            } else {
                Button(onClick = ::refresh) {
                    Text(stringResource(R.string.today_refresh_prayers))
                }
            }
        }
    }
}