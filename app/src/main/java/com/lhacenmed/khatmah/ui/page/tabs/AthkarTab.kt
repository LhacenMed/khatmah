package com.lhacenmed.khatmah.ui.page.tabs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lhacenmed.khatmah.R
import com.lhacenmed.khatmah.ui.common.Route
import com.lhacenmed.khatmah.ui.component.AdhkarCard
import com.lhacenmed.khatmah.ui.nav.LocalNavController
import com.lhacenmed.khatmah.ui.nav.LocalScrollToTop
import com.lhacenmed.khatmah.ui.nav.NavScreen
import com.lhacenmed.khatmah.ui.page.tabs.adhkar.adhkarCategories

// Items within this distance from the top animate directly; farther ones jump-then-animate.
private const val SMOOTH_SCROLL_THRESHOLD = 4

// ── Tab registration ────────────────────────────────────────────────────────────

val AthkarTab = NavScreen(
    route    = Route.ATHKAR,
    iconRes  = R.drawable.ic_athkar,
    labelRes = R.string.athkar,
) { padding -> AthkarScreen(padding) }

// ── Screen ────────────────────────────────────────────────────────────

@Composable
private fun AthkarScreen(padding: PaddingValues) {
    val nav         = LocalNavController.current
    val gridState   = rememberLazyGridState()
    val scrollToTop = LocalScrollToTop.current

    // Two-phase scroll-to-top: instant jump near the top, then smooth animation.
    LaunchedEffect(scrollToTop) {
        scrollToTop.collect {
            if (gridState.firstVisibleItemIndex > SMOOTH_SCROLL_THRESHOLD) {
                gridState.scrollToItem(SMOOTH_SCROLL_THRESHOLD)
            }
            gridState.animateScrollToItem(0)
        }
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
            items = adhkarCategories,
            key   = { it.id },
            span  = { item -> GridItemSpan(item.span) },
        ) { category ->
            AdhkarCard(
                category = category,
                onClick  = { nav.navigate(Route.adhkarDetail(category.id)) },
            )
        }
    }
}