package com.lhacenmed.khatmah.ui.page.tabs

import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lhacenmed.khatmah.R
import com.lhacenmed.khatmah.ui.common.Route
import com.lhacenmed.khatmah.ui.component.AdhkarCard
import com.lhacenmed.khatmah.ui.nav.LocalNavController
import com.lhacenmed.khatmah.ui.nav.LocalScrollToTop
import com.lhacenmed.khatmah.ui.nav.NavScreen
import com.lhacenmed.khatmah.ui.page.tabs.adhkar.AdhkarViewModel
import com.lhacenmed.khatmah.ui.page.tabs.adhkar.AdhkarUiState

private const val SMOOTH_SCROLL_THRESHOLD = 4

// ── Tab registration ──────────────────────────────────────────────────────────

val AdhkarTab = NavScreen(
    route    = Route.ADHKAR,
    iconRes  = R.drawable.ic_adhkar,
    labelRes = R.string.adhkar,
) { padding -> AdhkarScreen(padding) }

// ── Screen ────────────────────────────────────────────────────────────────────

@Composable
private fun AdhkarScreen(padding: PaddingValues) {
    val activity    = LocalActivity.current as ComponentActivity
    val nav         = LocalNavController.current
    val vm: AdhkarViewModel = viewModel(activity)
    val state: AdhkarUiState by vm.uiState.collectAsState()
    val scrollToTop = LocalScrollToTop.current
    val gridState   = rememberLazyGridState()

    // Scroll-to-top signal from tab re-tap
    LaunchedEffect(scrollToTop) {
        scrollToTop.collect {
            if (gridState.firstVisibleItemIndex > SMOOTH_SCROLL_THRESHOLD)
                gridState.scrollToItem(SMOOTH_SCROLL_THRESHOLD)
            gridState.animateScrollToItem(0)
        }
    }

    if (state.isLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    LazyVerticalGrid(
        state                 = gridState,
        columns               = GridCells.Fixed(2),
        modifier              = Modifier.fillMaxSize(),
        contentPadding        = PaddingValues(
            start  = 8.dp,
            end    = 8.dp,
            top    = padding.calculateTopPadding() + 8.dp,
            bottom = padding.calculateBottomPadding() + 8.dp,
        ),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement   = Arrangement.spacedBy(8.dp),
    ) {
        items(
            items = state.categories,
            key   = { it.id },
            span  = { item -> GridItemSpan(item.span) },
        ) { category ->
            AdhkarCard(
                category      = category,
                selectionMode = state.selectionMode,
                selected      = category.id in state.selectedIds,
                onClick       = {
                    if (state.selectionMode) vm.toggleSelection(category.id)
                    else nav.navigate(Route.adhkarDetail(category.id))
                },
                onLongClick   = { vm.enterSelectionMode(category.id) },
            )
        }
    }
}