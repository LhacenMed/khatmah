package com.lhacenmed.khatmah.ui.page.quran

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import android.app.Application
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lhacenmed.khatmah.data.quran.QuranRepository
import com.lhacenmed.khatmah.data.quran.SearchResult
import com.lhacenmed.khatmah.ui.nav.LocalNavController
import com.lhacenmed.khatmah.ui.theme.WarshFamily
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// ── ViewModel ─────────────────────────────────────────────────────────────────

class QuranSearchViewModel(app: Application) : AndroidViewModel(app) {

    data class State(
        val query:   String             = "",
        val results: List<SearchResult> = emptyList(),
        val loading: Boolean            = false,
    )

    private val repo      = QuranRepository(app)
    private val _state    = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()
    private var searchJob: Job? = null

    /**
     * Updates the search query and triggers a debounced (300 ms) search.
     * Clears results immediately when [query] is blank.
     */
    fun onQueryChange(query: String) {
        _state.update { it.copy(query = query) }
        searchJob?.cancel()
        if (query.isBlank()) { _state.update { it.copy(results = emptyList(), loading = false) }; return }
        searchJob = viewModelScope.launch {
            delay(300)
            _state.update { it.copy(loading = true) }
            _state.update { it.copy(results = repo.search(query), loading = false) }
        }
    }
}

// ── Screen ────────────────────────────────────────────────────────────────────

/**
 * Full-screen Quran search.
 *
 * Navigation contract:
 *  - Back with non-empty query → clear query (stay on page).
 *  - Back with empty query     → pop (return to reader).
 *  - Result tap                → write [KEY_JUMP_SURA] + [KEY_JUMP_AYA] to reader's
 *                                SavedStateHandle, then pop.
 */
@Composable
fun QuranSearchPage() {
    val vm    = viewModel<QuranSearchViewModel>()
    val state by vm.state.collectAsState()
    val nav   = LocalNavController.current
    val focus = remember { FocusRequester() }

    BackHandler(enabled = state.query.isNotEmpty()) { vm.onQueryChange("") }

    Scaffold(
        topBar = {
            SearchBar(query = state.query, onQueryChange = vm::onQueryChange,
                onBack = { nav.popBackStack() }, focusRequester = focus)
        },
    ) { innerPadding ->
        SearchContent(
            state      = state,
            modifier   = Modifier.padding(innerPadding),
            onSelected = { result ->
                nav.previousBackStackEntry?.savedStateHandle?.run {
                    set(KEY_JUMP_SURA, result.suraNum)
                    set(KEY_JUMP_AYA,  result.ayaNum)
                }
                nav.popBackStack()
            },
        )
    }

    LaunchedEffect(Unit) { focus.requestFocus() }
}

// ── Search bar ────────────────────────────────────────────────────────────────

@Composable
private fun SearchBar(
    query: String, onQueryChange: (String) -> Unit,
    onBack: () -> Unit, focusRequester: FocusRequester,
) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shadowElevation = 4.dp) {
        Row(
            modifier          = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(start = 4.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface)
            }
            TextField(
                value         = query,
                onValueChange = onQueryChange,
                modifier      = Modifier.weight(1f).focusRequester(focusRequester),
                placeholder   = {
                    Text("ابحث في القرآن الكريم", style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                },
                singleLine    = true,
                trailingIcon  = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { onQueryChange("") }) {
                            Icon(Icons.Default.Close, contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor   = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor   = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor  = Color.Transparent,
                ),
            )
        }
    }
}

// ── Content area ──────────────────────────────────────────────────────────────

@Composable
private fun SearchContent(
    state: QuranSearchViewModel.State, modifier: Modifier, onSelected: (SearchResult) -> Unit,
) {
    when {
        state.loading -> Box(modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
        state.query.isNotEmpty() && state.results.isEmpty() -> {
            Box(modifier.fillMaxSize(), Alignment.Center) {
                Text("لا توجد نتائج", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        else -> {
            LazyColumn(modifier = modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
                items(state.results, key = { "${it.suraNum}:${it.ayaNum}" }) { result ->
                    SearchResultRow(result, onClick = { onSelected(result) })
                    HorizontalDivider(
                        modifier  = Modifier.padding(horizontal = 16.dp),
                        color     = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        thickness = 0.5.dp,
                    )
                }
            }
        }
    }
}

// ── Result row ────────────────────────────────────────────────────────────────

/**
 * Displays vocalized aya text (Warsh font, ≤3 lines) with surah name and aya number.
 * [SearchResult.spansPair] = true shows two ayas joined by ۝ for cross-boundary hits.
 */
@Composable
private fun SearchResultRow(result: SearchResult, onClick: () -> Unit) {
    ListItem(
        modifier          = Modifier.clickable(onClick = onClick),
        headlineContent   = {
            Text(
                text     = result.ayaText,
                style    = TextStyle(fontFamily = WarshFamily, fontSize = 20.sp,
                    lineHeight = 32.sp, textDirection = TextDirection.Rtl),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                color    = MaterialTheme.colorScheme.onSurface,
            )
        },
        supportingContent = {
            Text("${result.suraName}  ·  آية ${result.ayaNum.toArNums()}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary)
        },
        leadingContent = {
            Text(result.suraNum.toArNums(), style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        },
    )
}