package com.lhacenmed.khatmah.onboarding

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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.lhacenmed.khatmah.R
import com.lhacenmed.khatmah.shared.location.CountriesApi
import com.lhacenmed.khatmah.shared.location.LocationCache
import com.lhacenmed.khatmah.core.nav.Route
import com.lhacenmed.khatmah.core.nav.LocalNavController
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CountrySelectPage() {
    val nav   = LocalNavController.current
    val scope = rememberCoroutineScope()

    // Initialize directly from cache — if data is present, loading never becomes true,
    // so no recomposition fires during the enter animation on back-navigation.
    var countries by remember { mutableStateOf(LocationCache.countries) }
    var query     by remember { mutableStateOf("") }
    var loading   by remember { mutableStateOf(countries == null) }
    var error     by remember { mutableStateOf(false) }

    fun load() {
        loading = true; error = false
        scope.launch {
            runCatching { CountriesApi.countries() }
                .onSuccess  { result ->
                    LocationCache.countries = result   // populate cache for future visits
                    countries = result
                    loading = false
                }
                .onFailure  { loading = false; error = true }
        }
    }

    // Only fetch when cache is empty — avoids any state change during enter animation
    // on revisits, which is what caused the jank.
    LaunchedEffect(Unit) { if (countries == null) load() }

    val filtered = remember(countries, query) {
        countries?.let { list ->
            if (query.isBlank()) list
            else list.filter { it.name.contains(query, ignoreCase = true) }
        } ?: emptyList()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.select_country)) },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
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

            when {
                loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                error -> Column(
                    Modifier.fillMaxSize(),
                    verticalArrangement   = Arrangement.Center,
                    horizontalAlignment   = Alignment.CenterHorizontally,
                ) {
                    Text(stringResource(R.string.network_error))
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = ::load) { Text(stringResource(R.string.retry)) }
                }
                filtered.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.no_results))
                }
                else -> LazyColumn {
                    items(filtered, key = { it.iso2.ifEmpty { it.name } }) { country ->
                        CountryItem(
                            country = country,
                            // Pass iso2 so CitySelectPage can store the country code
                            onClick = {
                                val fromSettings = nav.currentBackStackEntry?.arguments?.getBoolean("fromSettings") ?: false
                                nav.navigate(Route.citySelect(country.name, country.iso2, fromSettings))
                            },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

// ─── Country list item ────────────────────────────────────────────────────────

@Composable
private fun CountryItem(
    country: CountriesApi.Country,
    onClick: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(country.name) },
        leadingContent  = {
            if (country.flagUrl.isNotEmpty()) {
                val context = LocalContext.current
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(country.flagUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = country.name,
                    contentScale       = ContentScale.FillBounds,
                    modifier           = Modifier
                        .size(width = 32.dp, height = 24.dp)
                        .clip(MaterialTheme.shapes.extraSmall),
                )
            }
        },
        modifier = Modifier.clickable { onClick() },
    )
}