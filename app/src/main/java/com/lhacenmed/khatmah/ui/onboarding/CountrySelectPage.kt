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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lhacenmed.khatmah.R
import com.lhacenmed.khatmah.data.location.CountriesApi
import com.lhacenmed.khatmah.ui.common.Route
import com.lhacenmed.khatmah.ui.nav.LocalNavController
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CountrySelectPage() {
    val nav   = LocalNavController.current
    val scope = rememberCoroutineScope()

    var countries by remember { mutableStateOf<List<CountriesApi.Country>?>(null) }
    var query     by remember { mutableStateOf("") }
    var loading   by remember { mutableStateOf(true) }
    var error     by remember { mutableStateOf(false) }

    fun load() {
        loading = true; error = false
        scope.launch {
            runCatching { CountriesApi.countries() }
                .onSuccess  { countries = it; loading = false }
                .onFailure  { loading = false; error = true }
        }
    }

    LaunchedEffect(Unit) { load() }

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
                        ListItem(
                            headlineContent = { Text(country.name) },
                            modifier = Modifier.clickable {
                                nav.navigate(Route.citySelect(country.name))
                            },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}