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
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lhacenmed.khatmah.ui.common.Route
import com.lhacenmed.khatmah.ui.nav.LocalNavController
import com.lhacenmed.khatmah.ui.theme.WarshFamily
import com.lhacenmed.khatmah.ui.theme.WarshSuraNameFamily
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

// ── Animation constant ────────────────────────────────────────────────────────

private const val ANIM_MS = 280

// ── SavedStateHandle keys shared with QuranSearchPage ────────────────────────

internal const val KEY_JUMP_SURA = "jumpSura"
internal const val KEY_JUMP_AYA  = "jumpAya"

// ── Entry point ───────────────────────────────────────────────────────────────

/**
 * Full-screen Quran reader registered as a standalone NavPage.
 *
 * Forces RTL layout so the pager, slider, and back arrow all match Arabic
 * reading convention without per-component direction overrides.
 *
 * Observes [KEY_JUMP_SURA] / [KEY_JUMP_AYA] on its own [SavedStateHandle] so that
 * [QuranSearchPage] can write a result there before popping back, and the pager
 * will scroll to the correct page seamlessly.
 */
@Composable
fun QuranReaderScreen() {
    val vm:  QuranViewModel = viewModel()
    val nav  = LocalNavController.current
    val state by vm.state.collectAsState()

    // Observe jump requests written by QuranSearchPage before it pops back.
    val backEntry = nav.currentBackStackEntry
    val jumpSura  by remember(backEntry) {
        backEntry?.savedStateHandle?.getStateFlow(KEY_JUMP_SURA, 0)
            ?: MutableStateFlow(0)
    }.collectAsState()

    LaunchedEffect(jumpSura) {
        if (jumpSura > 0) {
            val jumpAya = backEntry?.savedStateHandle?.get<Int>(KEY_JUMP_AYA) ?: 1
            vm.requestJump(jumpSura, jumpAya)
            // Reset to 0 so a second selection of the same aya fires again.
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
 * Root layout: pager content fills the entire screen; bars float above it.
 *
 * Tap detection lives on the root [Box] so the full screen area (including any
 * empty space around the text) toggles bar visibility — not just the text region.
 *
 * Content is vertically centered within the full screen height with no reserved
 * padding for bars; bars animate independently on top without shifting content.
 *
 * In the forced RTL context the search icon sits in [CenterAlignedTopAppBar]'s
 * actions slot — visually on the left — opposite the back arrow on the right.
 *
 * Pending jumps from [QuranSearchPage] are observed via [QuranViewModel.pendingJump]
 * and scroll the pager without disrupting the current bar visibility state.
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

    LaunchedEffect(pagerState.settledPage) { vm.savePage(pagerState.settledPage) }

    // Scroll to pages requested by search result selection.
    val pendingJump by vm.pendingJump.collectAsState()
    LaunchedEffect(pendingJump) {
        pendingJump?.let { page ->
            pagerState.scrollToPage(page)
            vm.consumeJump()
        }
    }

    val curPage = pages[pagerState.settledPage]

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            // Full-screen tap detection — coexists with pager's horizontal drag
            // because detectTapGestures only fires on a clean tap with no drag.
            .pointerInput(Unit) {
                detectTapGestures { barsVisible = !barsVisible }
            },
    ) {
        // ── Content area — full screen, vertically centered ───────────────────
        HorizontalPager(
            state         = pagerState,
            reverseLayout = false, // RTL context handles visual order
            modifier      = Modifier.fillMaxSize(),
            key           = { pages[it].pageNum },
        ) { idx ->
            PageContent(page = pages[idx])
        }

        // ── Floating top bar ──────────────────────────────────────────────────
        AnimatedVisibility(
            visible  = barsVisible,
            enter    = slideInVertically(tween(ANIM_MS)) { -it } + fadeIn(tween(ANIM_MS)),
            exit     = slideOutVertically(tween(ANIM_MS)) { -it } + fadeOut(tween(ANIM_MS / 2)),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth(),
        ) {
            QuranTopBar(
                page     = curPage,
                onBack   = { nav.popBackStack() },
                onSearch = onSearch,
            )
        }

        // ── Floating bottom bar ───────────────────────────────────────────────
        AnimatedVisibility(
            visible  = barsVisible,
            enter    = slideInVertically(tween(ANIM_MS)) { it } + fadeIn(tween(ANIM_MS)),
            exit     = slideOutVertically(tween(ANIM_MS)) { it } + fadeOut(tween(ANIM_MS / 2)),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
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
 * [CenterAlignedTopAppBar] with [statusBarsPadding] so the title column is
 * centred in the visible bar area and never overlaps the system status bar.
 *
 * In the forced RTL context:
 *   [navigationIcon] slot → right side → back arrow.
 *   [actions] slot        → left side  → search icon (opposite the back arrow).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuranTopBar(
    page:     QuranPageData,
    onBack:   () -> Unit,
    onSearch: () -> Unit,
) {
    Surface(
        color           = MaterialTheme.colorScheme.surfaceContainer,
        shadowElevation = 4.dp,
    ) {
        CenterAlignedTopAppBar(
            modifier = Modifier.statusBarsPadding(),
            colors   = TopAppBarDefaults.centerAlignedTopAppBarColors(
                containerColor = Color.Transparent,
            ),
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
                    Icon(
                        imageVector        = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = null,
                        tint               = MaterialTheme.colorScheme.onSurface,
                    )
                }
            },
            actions = {
                IconButton(onClick = onSearch) {
                    Icon(
                        imageVector        = Icons.Default.Search,
                        contentDescription = null,
                        tint               = MaterialTheme.colorScheme.onSurface,
                    )
                }
            },
        )
    }
}

// ── Bottom bar ────────────────────────────────────────────────────────────────

/**
 * Page-jump slider. Dragging and releasing jumps directly to the target page.
 * The slider position tracks [currentPage] except while the user is dragging.
 *
 * In the forced-RTL context the slider runs right→left, matching Quran book
 * direction: right edge = Al-Fatiha, left edge = An-Nas.
 */
@Composable
private fun QuranBottomBar(
    currentPage: Int,
    totalPages:  Int,
    onJump:      (Int) -> Unit,
) {
    var sliderVal  by remember { mutableFloatStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }

    LaunchedEffect(currentPage) {
        if (!isDragging) {
            sliderVal = if (totalPages > 1) currentPage.toFloat() / (totalPages - 1) else 0f
        }
    }

    Surface(
        color           = MaterialTheme.colorScheme.surfaceContainer,
        shadowElevation = 4.dp,
    ) {
        Box(
            modifier         = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center,
        ) {
            Slider(
                value              = sliderVal,
                onValueChange      = { v -> sliderVal = v; isDragging = true },
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

// ── Page content ──────────────────────────────────────────────────────────────

/**
 * Renders one Quran page, always vertically centered in the screen.
 *
 * [QuranPageData.centered] controls aya text alignment:
 *  true  → [TextAlign.Center] — each line is centered within the container,
 *          used for short special pages (Al-Fatiha, Al-Baqarah intro).
 *  false → [TextAlign.Justify] — text stretches edge-to-edge, used for all
 *          normal full pages.
 *
 * Segments are grouped into runs: each [QuranSegment.SuraHeader] and
 * [QuranSegment.Basmala] gets its own centered [Text], while consecutive
 * [QuranSegment.Aya] items are merged into a single flowing [Text].
 */
@Composable
private fun PageContent(page: QuranPageData) {
    val primary = MaterialTheme.colorScheme.primary
    val onBg    = MaterialTheme.colorScheme.onBackground

    // Group segments into renderable runs — avoids recomputing on every recomposition.
    val runs = remember(page.pageNum) { groupSegments(page.segments) }

    Column(
        modifier            = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        runs.forEach { run ->
            when (run) {
                is PageRun.Header  -> SuraHeaderText(name = run.name, primary = primary)
                is PageRun.Basmala -> BasmalaText(primary = primary)
                is PageRun.AyaRun  -> AyaFlowText(
                    annotated = run.annotated(primary, onBg),
                    centered  = page.centered,
                )
            }
        }
    }
}

// ── Segment grouping ──────────────────────────────────────────────────────────

/**
 * Typed render units produced by [groupSegments].
 *
 * [Header]  — a single sura name line, rendered centered.
 * [Basmala] — the basmala line, rendered centered.
 * [AyaRun]  — one or more consecutive ayas merged into a flowing paragraph.
 */
private sealed interface PageRun {
    data class Header(val name: String) : PageRun
    data object Basmala                 : PageRun
    data class AyaRun(val ayas: List<QuranSegment.Aya>) : PageRun {
        /**
         * Builds the [AnnotatedString] for this aya run.
         *
         * No trailing separator after the last aya — doing so would push the
         * justify engine to render a phantom partial line at the end, creating
         * a visible gap before the last ornament in RTL layout.
         */
        fun annotated(primary: Color, onBg: Color): AnnotatedString = buildAnnotatedString {
            ayas.forEachIndexed { i, aya ->
                withStyle(SpanStyle(color = onBg)) { append(aya.text) }
                append(" ")
                withStyle(SpanStyle(color = primary, fontSize = 25.sp)) {
                    append(toArNums(aya.ayaNum))
                }
                // Separator between ayas only — omit after the last to avoid
                // a trailing-whitespace phantom line in the justified layout.
                if (i < ayas.lastIndex) append(" ")
            }
        }
    }
}

/**
 * Converts a flat [QuranSegment] list into [PageRun] items.
 * Consecutive ayas are collapsed into a single [PageRun.AyaRun].
 */
private fun groupSegments(segments: List<QuranSegment>): List<PageRun> {
    val runs   = mutableListOf<PageRun>()
    val ayaBuf = mutableListOf<QuranSegment.Aya>()

    fun flushAyas() {
        if (ayaBuf.isNotEmpty()) {
            runs += PageRun.AyaRun(ayaBuf.toList())
            ayaBuf.clear()
        }
    }

    segments.forEach { seg ->
        when (seg) {
            is QuranSegment.SuraHeader -> { flushAyas(); runs += PageRun.Header(seg.name) }
            is QuranSegment.Basmala    -> { flushAyas(); runs += PageRun.Basmala }
            is QuranSegment.Aya        -> ayaBuf += seg
        }
    }
    flushAyas()
    return runs
}

// ── Segment composables ───────────────────────────────────────────────────────

/** Sura name centered between two dividers, using [WarshSuraNameFamily]. */
@Composable
private fun SuraHeaderText(name: String, primary: Color) {
    Row(
        modifier          = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HorizontalDivider(
            modifier  = Modifier.weight(1f),
            color     = MaterialTheme.colorScheme.outlineVariant,
            thickness = 0.8.dp,
        )
        Text(
            text  = name,
            style = TextStyle(
                fontFamily    = WarshSuraNameFamily,
                fontSize      = 28.sp,
                lineHeight    = 40.sp,
                textDirection = TextDirection.Rtl,
            ),
            color    = primary,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        HorizontalDivider(
            modifier  = Modifier.weight(1f),
            color     = MaterialTheme.colorScheme.outlineVariant,
            thickness = 0.8.dp,
        )
    }
}

/** Basmala line centered and tinted in [primary]. */
@Composable
private fun BasmalaText(primary: Color) {
    Text(
        text      = "بِسْمِ اِ۬للَّهِ اِ۬لرَّحْمَٰنِ اِ۬لرَّحِيمِ",
        style     = TextStyle(
            fontFamily    = WarshFamily,
            fontSize      = 28.sp,
            lineHeight    = 40.sp,
            textDirection = TextDirection.Rtl,
        ),
        textAlign = TextAlign.Center,
        color     = primary,
        modifier  = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
    )
}

/**
 * Flowing aya paragraph.
 *
 * [centered] true  → [TextAlign.Center]: each line centered, used for short
 *                    special pages so ayas don't stretch awkwardly edge-to-edge.
 * [centered] false → [TextAlign.Justify]: lines fill the full width, standard
 *                    Mushaf aesthetic for normal full pages.
 */
@Composable
private fun AyaFlowText(annotated: AnnotatedString, centered: Boolean) {
    Text(
        text      = annotated,
        style     = TextStyle(
            fontFamily    = WarshFamily,
            fontSize      = 28.sp,
            lineHeight    = 40.sp,
            textDirection = TextDirection.Rtl,
            textAlign     = if (centered) TextAlign.Center else TextAlign.Justify,
        ),
        modifier  = Modifier.fillMaxWidth(),
    )
}

// ── Utilities ─────────────────────────────────────────────────────────────────

/** "صفحة ١ ⋅ جزء ٢" label for the top bar subtitle. */
private fun buildPageLabel(page: QuranPageData): String {
    val pageStr = "صفحة ${toArNums(page.pageNum)}"
    if (page.juz.isBlank()) return pageStr
    val juzStr = page.juz.trim().toIntOrNull()?.let { toArNums(it) } ?: page.juz
    return "$pageStr ⋅ جزء $juzStr"
}

/** Converts a non-negative integer to Eastern Arabic-Indic numeral string (٠١٢٣…). */
private fun toArNums(n: Int): String =
    n.toString().map { "٠١٢٣٤٥٦٧٨٩"[it - '0'] }.joinToString("")

/** Shown while the ViewModel is loading ayas and building pages. */
@Composable
internal fun LoadingBox() {
    Box(
        modifier         = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) { CircularProgressIndicator() }
}