package com.lhacenmed.khatmah.ui.page.quran

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lhacenmed.khatmah.ui.common.Route
import com.lhacenmed.khatmah.ui.nav.LocalNavController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private const val ANIM_MS = 280

// Keys shared with QuranSearchScreen — written there, read here via SavedStateHandle.
internal const val KEY_JUMP_SURA = "jumpSura"
internal const val KEY_JUMP_AYA  = "jumpAya"

// ── Entry point ───────────────────────────────────────────────────────────────

/**
 * Full-screen Quran reader.
 *
 * Forces RTL layout so the pager, slider, and back arrow all match Arabic
 * reading convention without per-component direction overrides.
 *
 * Observes [KEY_JUMP_SURA] / [KEY_JUMP_AYA] on the current back-stack entry so
 * [QuranSearchScreen] can write a result there before popping back.
 */
@Composable
fun QuranReaderScreen() {
    val vm:   QuranViewModel = viewModel()
    val nav   = LocalNavController.current
    val state by vm.state.collectAsState()

    val backEntry = nav.currentBackStackEntry
    val jumpSura  by remember(backEntry) {
        backEntry?.savedStateHandle?.getStateFlow(KEY_JUMP_SURA, 0) ?: MutableStateFlow(0)
    }.collectAsState()

    LaunchedEffect(jumpSura) {
        if (jumpSura > 0) {
            val jumpAya = backEntry?.savedStateHandle?.get<Int>(KEY_JUMP_AYA) ?: 1
            vm.requestJump(jumpSura, jumpAya)
            backEntry?.savedStateHandle?.set(KEY_JUMP_SURA, 0)
        }
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        when (val s = state) {
            is QuranViewModel.State.Loading -> LoadingBox()
            is QuranViewModel.State.Ready   -> QuranPager(
                pages    = s.pages,
                vm       = vm,
                onSearch = { nav.navigate(Route.QURAN_SEARCH) },
            )
        }
    }
}

// ── Pager shell ───────────────────────────────────────────────────────────────

/**
 * Root layout: pager fills the screen; bars float above it.
 *
 * Tap detection on the root [Box] toggles bar visibility across the full screen
 * area — not just the text region. Bars animate independently without shifting content.
 *
 * In the forced RTL context the search icon sits in [CenterAlignedTopAppBar]'s
 * actions slot (visually left), opposite the back arrow (visually right).
 *
 * System bars (status bar + navigation bar) are hidden/shown in sync with
 * [barsVisible] via [WindowInsetsControllerCompat].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuranPager(
    pages:    List<QuranPageData>,
    vm:       QuranViewModel,
    onSearch: () -> Unit,
) {
    val nav        = LocalNavController.current
    val scope      = rememberCoroutineScope()
    val pagerState = rememberPagerState(
        initialPage = vm.savedPage.coerceIn(0, pages.lastIndex),
    ) { pages.size }

    var barsVisible by remember { mutableStateOf(true) }

    // ── System bars sync ──────────────────────────────────────────────────────
    val view = LocalView.current
    val window = (view.context as androidx.activity.ComponentActivity).window
    val insetsController = remember(view) {
        WindowInsetsControllerCompat(window, view).also {
            it.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    LaunchedEffect(barsVisible) {
        if (barsVisible) {
            insetsController.show(WindowInsetsCompat.Type.systemBars())
        } else {
            insetsController.hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    // Restore system bars when leaving this screen.
    DisposableEffect(Unit) {
        onDispose { insetsController.show(WindowInsetsCompat.Type.systemBars()) }
    }
    // ─────────────────────────────────────────────────────────────────────────

    LaunchedEffect(pagerState.settledPage) { vm.savePage(pagerState.settledPage) }

    val pendingJump by vm.pendingJump.collectAsState()
    LaunchedEffect(pendingJump) {
        pendingJump?.let { page -> pagerState.scrollToPage(page); vm.consumeJump() }
    }

    val curPage = pages[pagerState.settledPage]

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .pointerInput(Unit) { detectTapGestures { barsVisible = !barsVisible } },
    ) {
        HorizontalPager(
            state         = pagerState,
            reverseLayout = false,
            modifier      = Modifier.fillMaxSize(),
            key           = { pages[it].pageNum },
        ) { idx -> PageContent(page = pages[idx]) }

        AnimatedVisibility(
            visible  = barsVisible,
            enter    = slideInVertically(tween(ANIM_MS)) { -it } + fadeIn(tween(ANIM_MS)),
            exit     = slideOutVertically(tween(ANIM_MS)) { -it } + fadeOut(tween(ANIM_MS / 2)),
            modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth(),
        ) {
            QuranTopBar(page = curPage, onBack = { nav.popBackStack() }, onSearch = onSearch)
        }

        AnimatedVisibility(
            visible  = barsVisible,
            enter    = slideInVertically(tween(ANIM_MS)) { it } + fadeIn(tween(ANIM_MS)),
            exit     = slideOutVertically(tween(ANIM_MS)) { it } + fadeOut(tween(ANIM_MS / 2)),
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
        ) {
            QuranBottomBar(
                currentPage = pagerState.currentPage,
                totalPages  = pages.size,
                onJump      = { target -> scope.launch { pagerState.scrollToPage(target) } },
            )
        }
    }
}

// ── Top bar ───────────────────────────────────────────────────────────────────

/**
 * [CenterAlignedTopAppBar] with [statusBarsPadding].
 * navigationIcon slot → right (back arrow) · actions slot → left (search icon).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuranTopBar(page: QuranPageData, onBack: () -> Unit, onSearch: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shadowElevation = 4.dp) {
        CenterAlignedTopAppBar(
            modifier = Modifier.statusBarsPadding(),
            colors   = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent),
            title = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text     = page.suraName,
                        style    = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color    = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                    )
                    Text(
                        text  = buildPageLabel(page),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface)
                }
            },
            actions = {
                IconButton(onClick = onSearch) {
                    Icon(Icons.Default.Search, contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface)
                }
            },
        )
    }
}

// ── Bottom bar ────────────────────────────────────────────────────────────────

/**
 * Page-jump slider. In the forced RTL context it runs right→left, matching
 * Quran book direction: right = Al-Fatiha, left = An-Nas.
 */
@Composable
private fun QuranBottomBar(currentPage: Int, totalPages: Int, onJump: (Int) -> Unit) {
    var sliderVal  by remember { mutableFloatStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }

    LaunchedEffect(currentPage) {
        if (!isDragging)
            sliderVal = if (totalPages > 1) currentPage.toFloat() / (totalPages - 1) else 0f
    }

    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shadowElevation = 4.dp) {
        Box(
            modifier         = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center,
        ) {
            Slider(
                value         = sliderVal,
                onValueChange = { v -> sliderVal = v; isDragging = true },
                onValueChangeFinished = {
                    isDragging = false
                    onJump((sliderVal * (totalPages - 1)).roundToInt().coerceIn(0, totalPages - 1))
                },
                valueRange = 0f..1f,
                modifier   = Modifier.fillMaxWidth(),
            )
        }
    }
}

// ── Utilities ─────────────────────────────────────────────────────────────────

/** "صفحة ١ ⋅ جزء ٢" subtitle for the top bar. */
private fun buildPageLabel(page: QuranPageData): String {
    val pageStr = "صفحة ${toArNums(page.pageNum)}"
    if (page.juz.isBlank()) return pageStr
    val juzStr = page.juz.trim().toIntOrNull()?.let { toArNums(it) } ?: page.juz
    return "$pageStr ⋅ جزء $juzStr"
}

@Composable
private fun LoadingBox() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}