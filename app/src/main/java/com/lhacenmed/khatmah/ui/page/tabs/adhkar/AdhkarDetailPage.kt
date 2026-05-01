package com.lhacenmed.khatmah.ui.page.tabs.adhkar

import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lhacenmed.khatmah.data.adhkar.Dhikr
import com.lhacenmed.khatmah.ui.common.Route
import com.lhacenmed.khatmah.ui.nav.LocalNavController
import com.lhacenmed.khatmah.ui.page.tabs.adhkar.component.CompletionBody
import com.lhacenmed.khatmah.ui.page.tabs.adhkar.component.DhikrBody
import com.lhacenmed.khatmah.ui.page.tabs.adhkar.component.DhikrBottomBar
import com.lhacenmed.khatmah.ui.page.tabs.adhkar.component.DhikrFontSize
import com.lhacenmed.khatmah.ui.page.tabs.adhkar.component.DhikrProgressHeader
import com.lhacenmed.khatmah.ui.page.tabs.adhkar.component.DhikrTopBar
import com.lhacenmed.khatmah.ui.page.tabs.adhkar.component.next
import kotlinx.coroutines.launch

/**
 * Full-screen dhikr reader for a single [AdhkarCategory].
 *
 * Page count = adhkar.size + 1; the final page is a completion slide that shows
 * once every dhikr in the category has been read.
 *
 * Key design decisions:
 *  • Arc uses [Animatable] rather than [animateFloatAsState]: [snapTo] on page
 *    change ensures the arc resets instantly (no flash of the previous page's
 *    progress), and [animateTo] fires only on actual count increments.
 *  • Progress bar target = page / adhkar.size, so it starts at 0 and reaches 1
 *    only on the completion page — the bar grows as each dhikr is finished.
 *  • [DhikrBottomBar] is placed in Scaffold's bottomBar slot so Material3 extends
 *    its Surface background behind the navigation bar automatically (edge-to-edge).
 *  • The [RepCircle] is always composed; alpha = 0 when repetitions == 1, keeping
 *    the bottom-bar height stable across all dhikr pages.
 *  • [state.version] is included in the dhikr [LaunchedEffect] key so that after
 *    an edit or reset in [AdhkarEditorPage], the detail page automatically
 *    re-fetches the updated dhikr list when the user returns.
 */
@Composable
fun AdhkarDetailPage(categoryId: String) {
    val nav      = LocalNavController.current
    val context  = LocalContext.current
    val activity = LocalActivity.current as ComponentActivity

    val vm: AdhkarViewModel = viewModel(activity)
    val state by vm.uiState.collectAsState()

    // Category title — sourced from the live ViewModel state
    val category = remember(categoryId, state.categories) {
        state.categories.find { it.id == categoryId }
    }
    // Dhikr list — re-fetched whenever the VM version increments (post-edit/reset)
    var adhkar by remember { mutableStateOf<List<Dhikr>>(emptyList()) }
    var isDhikrLoading by remember { mutableStateOf(true) }

    // Session counts survive configuration changes via the activity-scoped VM.
    // startSession resets counts when the category or its content changes.
    val session by vm.session.collectAsState()

    LaunchedEffect(categoryId, state.version) {
        adhkar = vm.getDhikrForCategory(categoryId)
        vm.cacheDhikr(categoryId, adhkar)
        vm.startSession(categoryId)
        isDhikrLoading = false
    }

    // Empty / not-found guard
    if (!isDhikrLoading && adhkar.isEmpty()) {
        LaunchedEffect(Unit) { nav.popBackStack() }
        return
    }
    if (isDhikrLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val categoryName = category?.title.orEmpty()
    val totalPages   = adhkar.size + 1        // last page is the completion slide
    val pagerState   = rememberPagerState { totalPages }
    val scope        = rememberCoroutineScope()

    // Font size persists across configuration changes but resets on new process.
    var fontSize by rememberSaveable { mutableStateOf(DhikrFontSize.MEDIUM) }

    val page             = pagerState.currentPage
    val isCompletionPage = page >= adhkar.size
    val dhikr            = if (!isCompletionPage) adhkar[page] else null
    val repCount         = session.count
    // allDone only for single-rep and completion page — multi-rep auto-navigates on last tap.
    val allDone = isCompletionPage || dhikr == null || dhikr.repetitions <= 1

    // On every page entry: snap arc to 0 (no animated carry-over from prior page)
    // and reset that page's counter so revisiting always starts fresh.
    val arcAnim = remember { Animatable(0f) }
    // Guards LaunchedEffect(page) from snapping arc to 0 mid last-rep animation.
    var isAnimatingLastRep by remember { mutableStateOf(false) }
    // Snap arc instantly to this page's existing progress when navigating.
    LaunchedEffect(page) {
        vm.resetCount()
        if (!isAnimatingLastRep) arcAnim.snapTo(0f)
    }

    // Progress bar: 0 at the first dhikr, grows by 1/N per completed dhikr,
    // reaches 1.0 only on the completion page.
    val barFraction by animateFloatAsState(
        targetValue   = if (isCompletionPage) 1f else page.toFloat() / adhkar.size,
        animationSpec = tween(300),
        label         = "dhikr_bar",
    )

    // ── Actions ───────────────────────────────────────────────────────────────

    fun goNext() = scope.launch {
        if (page < totalPages - 1) pagerState.animateScrollToPage(page + 1)
        else nav.popBackStack()
    }

    fun handleTap() {
        if (isCompletionPage) return
        val reps = dhikr?.repetitions ?: 1
        if (reps <= 1) { goNext(); return }
        val newCount = repCount + 1
        val isLast   = newCount >= reps
        vm.recordRead()
        if (isLast) {
            isAnimatingLastRep = true
            scope.launch {
                arcAnim.animateTo(1f, tween(400, easing = FastOutSlowInEasing))
                arcAnim.snapTo(0f)
                isAnimatingLastRep = false
            }
            goNext()
        } else {
            scope.launch {
                arcAnim.animateTo(
                    targetValue   = newCount.toFloat() / reps,
                    animationSpec = tween(400, easing = FastOutSlowInEasing),
                )
            }
        }
    }

    fun share() {
        dhikr ?: return
        context.startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, dhikr.shareText)
                },
                null,
            )
        )
    }

    // ── UI ────────────────────────────────────────────────────────────────────

    Scaffold(
        topBar = {
            DhikrTopBar(
                title    = categoryName,
                onBack   = { nav.popBackStack() },
                onEdit   = { nav.navigate(Route.adhkarEditor(categoryId)) },
                onResize = { fontSize = fontSize.next() },
            )
        },
        // bottomBar slot: M3 Scaffold places this at the physical screen bottom
        // and extends its background behind the navigation bar (edge-to-edge).
        bottomBar = {
            DhikrBottomBar(
                dhikr            = dhikr,
                repCount         = repCount,
                arcFraction      = arcAnim.value,
                allDone          = allDone,
                isCompletionPage = isCompletionPage,
                onBack           = { nav.popBackStack() },
                onShare          = ::share,
                onAction         = ::handleTap,
                onTap            = ::handleTap,
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            DhikrProgressHeader(
                // Cap at adhkar.size so the counter reads "N/N" on the completion page.
                current  = minOf(page + 1, adhkar.size),
                total    = adhkar.size,
                fraction = barFraction,
                onClick  = ::handleTap,
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .clickable(
                        indication        = null,
                        interactionSource = remember { MutableInteractionSource() },
                    ) { handleTap() },
            ) {
                // All pages stay composed; Compose pager manages lifecycle automatically.
                HorizontalPager(
                    state    = pagerState,
                    modifier = Modifier.fillMaxSize(),
                ) { i ->
                    if (i < adhkar.size) {
                        DhikrBody(dhikr = adhkar[i], fontSize = fontSize)
                    } else {
                        CompletionBody(categoryName = categoryName)
                    }
                }

                // Gradient scrim: fades body content into the bottom bar's surface color,
                // removing the hard visual cut between the scroll area and the bottom bar.
                val scrimColor = MaterialTheme.colorScheme.surface
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, scrimColor),
                            )
                        )
                )
            }
        }
    }
}