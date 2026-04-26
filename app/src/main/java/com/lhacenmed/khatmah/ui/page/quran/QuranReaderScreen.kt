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
import androidx.compose.ui.platform.LocalDensity
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

private const val ANIM_MS = 280

internal const val KEY_JUMP_SURA = "jumpSura"
internal const val KEY_JUMP_AYA  = "jumpAya"

// ── Entry point ───────────────────────────────────────────────────────────────

/**
 * Full-screen Quran reader. Forces RTL layout.
 *
 * Uses [BoxWithConstraints] to resolve the usable page area before calling
 * [QuranViewModel.init], providing exact pixel dimensions to [QuranPaginator].
 * [LocalDensity.fontScale] is forwarded so StaticLayout measurements stay in
 * sync with Compose's sp→px conversion on all font-scale settings.
 *
 * Observes [KEY_JUMP_SURA] / [KEY_JUMP_AYA] from [QuranSearchScreen] to scroll
 * to a specific aya after returning from search.
 */
@Composable
fun QuranReaderScreen() {
    val vm    = viewModel<QuranViewModel>()
    val nav   = LocalNavController.current
    val state by vm.state.collectAsState()

    val backEntry = nav.currentBackStackEntry
    val jumpSura by remember(backEntry) {
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
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val density      = LocalDensity.current
            val pageHeightPx = with(density) { maxHeight.toPx().toInt() }
            val contentWidPx = with(density) { (maxWidth - 32.dp).toPx().toInt() } // matches padding(horizontal=16.dp)

            LaunchedEffect(pageHeightPx, contentWidPx) {
                // fontScale ensures StaticLayout uses the same sp→px factor as Compose.
                vm.init(pageHeightPx, contentWidPx, density.density, density.fontScale)
            }

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
}

// ── Pager shell ───────────────────────────────────────────────────────────────

/**
 * Root layout: pager fills the full screen; top and bottom bars float above it.
 *
 * Tap anywhere → toggle bar visibility.
 * [QuranViewModel.pendingJump] triggers instant pager scroll after search result selection.
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
 * [CenterAlignedTopAppBar] with page and juz info.
 * In RTL: navigationIcon slot → right (back arrow), actions slot → left (search).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuranTopBar(page: QuranPageData, onBack: () -> Unit, onSearch: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shadowElevation = 4.dp) {
        CenterAlignedTopAppBar(
            modifier = Modifier.statusBarsPadding(),
            colors   = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent),
            title    = {
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
 * Page-jump slider. In the forced-RTL context the slider runs right→left,
 * matching Quran book direction: right = Al-Fatiha, left = An-Nas.
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
                value                 = sliderVal,
                onValueChange         = { v -> sliderVal = v; isDragging = true },
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
 * Renders one page. Content is vertically centered within the full screen height.
 *
 * [QuranPageData.centered] → [TextAlign.Center] for special opening pages.
 * Otherwise → [TextAlign.Justify] for the standard Mushaf aesthetic.
 *
 * Consecutive [QuranSegment.Aya] items are merged into a single flowing [Text].
 * [QuranSegment.Aya.showOrnament] suppresses the aya-number ornament on all but
 * the final slice of a split aya.
 */
@Composable
private fun PageContent(page: QuranPageData) {
    val primary = MaterialTheme.colorScheme.primary
    val onBg    = MaterialTheme.colorScheme.onBackground

    val runs = remember(page.pageNum) { buildRuns(page.segments) }

    Column(
        modifier            = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        runs.forEach { run ->
            when (run) {
                is PageRun.Header  -> SuraHeaderText(run.name, primary)
                is PageRun.Basmala -> BasmalaText(primary)
                is PageRun.AyaRun  -> AyaFlowText(run.annotated(primary, onBg), page.centered)
            }
        }
    }
}

// ── Segment grouping ──────────────────────────────────────────────────────────

private sealed interface PageRun {
    data class Header(val name: String) : PageRun
    data object Basmala : PageRun
    data class AyaRun(val ayas: List<QuranSegment.Aya>) : PageRun {
        /**
         * Builds the [AnnotatedString] for this aya run.
         * The aya-end ornament is only appended when [QuranSegment.Aya.showOrnament] is true,
         * so split ayas show the ornament only on their final slice.
         *
         * No trailing separator after the last item to avoid a phantom justify line.
         */
        fun annotated(primary: Color, onBg: Color): AnnotatedString = buildAnnotatedString {
            ayas.forEachIndexed { i, aya ->
                withStyle(SpanStyle(color = onBg)) { append(aya.text) }
                if (aya.showOrnament) {
                    append(" ")
                    withStyle(SpanStyle(color = primary, fontSize = 25.sp)) {
                        append(aya.ayaNum.toArNums())
                    }
                }
                if (i < ayas.lastIndex) append(" ")
            }
        }
    }
}

private fun buildRuns(segments: List<QuranSegment>): List<PageRun> {
    val runs   = mutableListOf<PageRun>()
    val ayaBuf = mutableListOf<QuranSegment.Aya>()

    fun flushAyas() {
        if (ayaBuf.isNotEmpty()) { runs += PageRun.AyaRun(ayaBuf.toList()); ayaBuf.clear() }
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

@Composable
private fun SuraHeaderText(name: String, primary: Color) {
    Row(
        modifier          = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HorizontalDivider(Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.8.dp)
        Text(
            text     = name,
            style    = TextStyle(fontFamily = WarshSuraNameFamily, fontSize = 28.sp,
                lineHeight = 40.sp, textDirection = TextDirection.Rtl),
            color    = primary,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        HorizontalDivider(Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.8.dp)
    }
}

@Composable
private fun BasmalaText(primary: Color) {
    Text(
        text      = "بِسْمِ اِ۬للَّهِ اِ۬لرَّحْمَٰنِ اِ۬لرَّحِيمِ",
        style     = TextStyle(fontFamily = WarshFamily, fontSize = 28.sp,
            lineHeight = 40.sp, textDirection = TextDirection.Rtl),
        textAlign = TextAlign.Center,
        color     = primary,
        modifier  = Modifier.fillMaxWidth().padding(vertical = 2.dp),
    )
}

@Composable
private fun AyaFlowText(annotated: AnnotatedString, centered: Boolean) {
    Text(
        text  = annotated,
        style = TextStyle(
            fontFamily    = WarshFamily,
            fontSize      = 28.sp,
            lineHeight    = 40.sp,
            textDirection = TextDirection.Rtl,
            textAlign     = if (centered) TextAlign.Center else TextAlign.Justify,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}

// ── Utilities ─────────────────────────────────────────────────────────────────

private fun buildPageLabel(page: QuranPageData): String {
    val pageStr = "صفحة ${page.pageNum.toArNums()}"
    if (page.juz.isBlank()) return pageStr
    val juzStr = page.juz.trim().toIntOrNull()?.toArNums() ?: page.juz
    return "$pageStr ⋅ جزء $juzStr"
}

@Composable
internal fun LoadingBox() {
    Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
}