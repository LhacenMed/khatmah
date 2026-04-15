package com.lhacenmed.khatmah.ui.onboarding

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lhacenmed.khatmah.R
import com.lhacenmed.khatmah.data.location.CountriesApi
import com.lhacenmed.khatmah.data.location.LocationCache
import com.lhacenmed.khatmah.ui.common.Route
import com.lhacenmed.khatmah.ui.nav.LocalNavController
import com.lhacenmed.khatmah.util.OnboardingPrefs
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CitySelectPage(country: String, iso2: String) {
    val nav           = LocalNavController.current
    val context       = LocalContext.current
    val scope         = rememberCoroutineScope()
    val snackbarState = remember { SnackbarHostState() }

    // Initialize directly from cache — no loading→loaded transition fires during
    // the enter animation on back-navigation.
    var cities    by remember { mutableStateOf(LocationCache.getCities(country)) }
    var query     by remember { mutableStateOf("") }
    var loading   by remember { mutableStateOf(cities == null) }
    var error     by remember { mutableStateOf(false) }
    var geocoding by remember { mutableStateOf(false) }

    // Captured outside coroutine scope (must be read in Composition)
    val geocodeErrorMsg = stringResource(R.string.geocode_error)

    fun load() {
        loading = true; error = false
        scope.launch {
            runCatching { CountriesApi.cities(country) }
                .onSuccess  { result ->
                    LocationCache.putCities(country, result)  // populate cache for future visits
                    cities = result
                    loading = false
                }
                .onFailure  { loading = false; error = true }
        }
    }

    // Only fetch when cache is empty — avoids state changes during enter animation on revisits.
    LaunchedEffect(Unit) { if (cities == null) load() }

    val filtered = remember(cities, query) {
        cities?.let { list ->
            if (query.isBlank()) list
            else list.filter { it.contains(query, ignoreCase = true) }
        } ?: emptyList()
    }

    fun selectCity(city: String) {
        geocoding = true
        scope.launch {
            val coords = CountriesApi.geocode(city, country)
            geocoding = false
            if (coords != null) {
                OnboardingPrefs.complete(context, city, coords.first, coords.second, iso2)
                nav.navigate(Route.MAIN) { popUpTo(0) { inclusive = true } }
            } else {
                snackbarState.showSnackbar(geocodeErrorMsg)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(country) },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarState) },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            OutlinedTextField(
                value         = query,
                onValueChange = { query = it },
                placeholder   = { Text(stringResource(R.string.search)) },
                singleLine    = true,
                modifier      = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )

            // Geocoding progress bar (shown after a city is tapped)
            if (geocoding) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            when {
                loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                error -> Column(
                    Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(stringResource(R.string.network_error))
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = ::load) { Text(stringResource(R.string.retry)) }
                }
                filtered.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.no_results))
                }
                else -> LazyColumn {
                    items(filtered, key = { it }) { city ->
                        ListItem(
                            headlineContent = { Text(city) },
                            modifier = Modifier.clickable(enabled = !geocoding) { selectCity(city) },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}