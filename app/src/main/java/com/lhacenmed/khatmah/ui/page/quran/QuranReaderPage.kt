package com.lhacenmed.khatmah.ui.page.quran

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.lhacenmed.khatmah.data.quran.QuranAya
import com.lhacenmed.khatmah.ui.nav.LocalNavController
import com.lhacenmed.khatmah.ui.theme.DinNextLtFamily
import com.lhacenmed.khatmah.ui.theme.WarshFamily
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

// ── Estimated bar heights used for page-area calculation ─────────────────────
private val TOP_BAR_DP    = 72.dp
private val BOTTOM_BAR_DP = 64.dp
private val PAGE_PAD_H_DP = 16.dp  // per side → 32 dp total
private val PAGE_PAD_V_DP = 8.dp   // per side → 16 dp total

// ── Entry point ───────────────────────────────────────────────────────────────

/**
 * Full-screen Quran reader registered as a standalone NavPage.
 *
 * Forces RTL layout so the pager, slider, and back arrow all behave correctly
 * for Arabic Quran reading without any per-component direction overrides.
 */
@Composable
fun QuranReaderScreen() {
    val vm: QuranViewModel = viewModel()
    val state by vm.state.collectAsState()

    // Force RTL for the entire reader — Quran is always Arabic/RTL
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        when (val s = state) {
            is QuranViewModel.State.Loading -> LoadingBox()
            is QuranViewModel.State.Ready   -> ReaderContent(ayas = s.ayas, vm = vm)
        }
    }
}

// ── Content after ayas are loaded ─────────────────────────────────────────────

@Composable
private fun ReaderContent(
    ayas: List<QuranAya>,
    vm: QuranViewModel,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val density      = LocalDensity.current
        val textMeasurer = rememberTextMeasurer()

        // Compute pixel dimensions of the readable text area
        val topBarPx  = with(density) { TOP_BAR_DP.roundToPx() }
        val botBarPx  = with(density) { BOTTOM_BAR_DP.roundToPx() }
        val padHPx    = with(density) { PAGE_PAD_H_DP.roundToPx() * 2 }
        val padVPx    = with(density) { PAGE_PAD_V_DP.roundToPx() * 2 }
        val pageW     = constraints.maxWidth - padHPx
        val pageH     = constraints.maxHeight - topBarPx - botBarPx - padVPx
        val cacheKey  = pageW.toLong() shl 20 or pageH.toLong()

        // Resolve colours from MaterialTheme while still in Composition context
        val onBg    = MaterialTheme.colorScheme.onBackground
        val primary = MaterialTheme.colorScheme.primary

        // Text styles used both for building pages and rendering them
        val ayaStyle = TextStyle(
            fontFamily    = WarshFamily,
            fontSize      = 20.sp,
            lineHeight    = 38.sp,
            textDirection = TextDirection.Rtl,
            textAlign     = TextAlign.Justify,
            color         = onBg,
        )
        val headerStyle = TextStyle(
            fontFamily    = DinNextLtFamily,
            fontSize      = 13.sp,
            textDirection = TextDirection.Rtl,
            textAlign     = TextAlign.Center,
            color         = primary,
        )
        val basmalaStyle = TextStyle(
            fontFamily    = WarshFamily,
            fontSize      = 18.sp,
            lineHeight    = 32.sp,
            textDirection = TextDirection.Rtl,
            textAlign     = TextAlign.Center,
            color         = primary,
        )
        val ayaNumSpan = SpanStyle(
            color    = primary,
            fontSize = 14.sp,
        )

        var pages by remember { mutableStateOf(vm.getCachedPages(cacheKey)) }

        LaunchedEffect(ayas, cacheKey) {
            if (pages == null) {
                val gapPx = with(density) { 8.dp.roundToPx() }
                // Build pages on Dispatchers.Default to avoid blocking the UI thread.
                // TextMeasurer.measure() is a pure text-layout computation and is safe
                // to call from a non-main thread.
                val built = withContext(Dispatchers.Default) {
                    QuranPageBuilder.build(
                        ayas         = ayas,
                        measurer     = textMeasurer,
                        pageWidth    = pageW,
                        pageHeight   = pageH,
                        ayaStyle     = ayaStyle,
                        headerStyle  = headerStyle,
                        basmalaStyle = basmalaStyle,
                        ayaNumSpan   = ayaNumSpan,
                        gapPx        = gapPx,
                    )
                }
                vm.cachePages(cacheKey, built)
                pages = built
            }
        }

        val built = pages
        if (built == null) LoadingBox()
        else QuranPager(
            pages        = built,
            savedPage    = vm.savedPage,
            onSavePage   = vm::savePage,
            ayaStyle     = ayaStyle,
            headerStyle  = headerStyle,
            basmalaStyle = basmalaStyle,
        )
    }
}

// ── Pager ─────────────────────────────────────────────────────────────────────

@Composable
private fun QuranPager(
    pages:        List<QuranPageData>,
    savedPage:    Int,
    onSavePage:   (Int) -> Unit,
    ayaStyle:     TextStyle,
    headerStyle:  TextStyle,
    basmalaStyle: TextStyle,
) {
    if (pages.isEmpty()) return

    val nav        = LocalNavController.current
    val scope      = rememberCoroutineScope()
    val pagerState = rememberPagerState(
        initialPage = savedPage.coerceIn(0, pages.lastIndex),
    ) { pages.size }

    // Persist reading position once the animated scroll settles
    LaunchedEffect(pagerState.settledPage) { onSavePage(pagerState.settledPage) }

    Scaffold(
        topBar = {
            QuranTopBar(
                suraName = pages[pagerState.settledPage].suraName,
                pageNum  = pages[pagerState.settledPage].pageNum,
                juz      = pages[pagerState.settledPage].juz,
                onBack   = { nav.popBackStack() },
            )
        },
        bottomBar = {
            QuranBottomBar(
                currentPage = pagerState.currentPage,
                totalPages  = pages.size,
                onJump      = { target -> scope.launch { pagerState.scrollToPage(target) } },
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        // reverseLayout=false: in the forced-RTL context Compose already places
        // page 0 on the physical right (Al-Fatiha), which is correct.
        HorizontalPager(
            state         = pagerState,
            reverseLayout = false,
            modifier      = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            key           = { pages[it].pageNum },
        ) { idx ->
            PageContent(
                page         = pages[idx],
                ayaStyle     = ayaStyle,
                headerStyle  = headerStyle,
                basmalaStyle = basmalaStyle,
            )
        }
    }
}

// ── Top bar ───────────────────────────────────────────────────────────────────

@Composable
private fun QuranTopBar(
    suraName: String,
    pageNum:  Int,
    juz:      String,
    onBack:   () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer),
        contentAlignment = Alignment.Center,
    ) {
        // Back button – in forced RTL, AutoMirrored renders as → (correct Arabic UX)
        IconButton(
            onClick  = onBack,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(4.dp),
        ) {
            Icon(
                imageVector        = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = null,
                tint               = MaterialTheme.colorScheme.onSurface,
            )
        }

        // Centred title: sura name + page/juz line
        Column(
            modifier            = Modifier.padding(horizontal = 52.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text       = suraName,
                style      = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color      = MaterialTheme.colorScheme.onSurface,
                maxLines   = 1,
            )
            Text(
                text  = "صفحة $pageNum , جزء $juz",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ── Bottom bar (page slider) ──────────────────────────────────────────────────

@Composable
private fun QuranBottomBar(
    currentPage: Int,
    totalPages:  Int,
    onJump:      (Int) -> Unit,
) {
    var sliderValue by remember { mutableFloatStateOf(0f) }
    var isDragging  by remember { mutableStateOf(false) }

    // Keep slider in sync with pager while the user is not dragging
    LaunchedEffect(currentPage) {
        if (!isDragging) {
            sliderValue = if (totalPages > 1) currentPage.toFloat() / (totalPages - 1) else 0f
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Slider(
            value    = sliderValue,
            onValueChange = { v ->
                sliderValue = v
                isDragging  = true
            },
            onValueChangeFinished = {
                isDragging = false
                val target = (sliderValue * (totalPages - 1)).roundToInt()
                    .coerceIn(0, totalPages - 1)
                onJump(target)
            },
            valueRange = 0f..1f,
            modifier   = Modifier.fillMaxWidth(),
        )
    }
}

// ── Page content ──────────────────────────────────────────────────────────────

@Composable
private fun PageContent(
    page:         QuranPageData,
    ayaStyle:     TextStyle,
    headerStyle:  TextStyle,
    basmalaStyle: TextStyle,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = PAGE_PAD_H_DP, vertical = PAGE_PAD_V_DP),
    ) {
        page.segments.forEach { seg ->
            Spacer(Modifier.height(4.dp))
            when (seg) {
                is QuranSegment.Basmala    -> BasmalaRow(basmalaStyle)
                is QuranSegment.SuraHeader -> SuraHeaderRow(seg, headerStyle)
                is QuranSegment.AyaFlow    -> AyaFlowRow(seg, ayaStyle)
            }
        }
    }
}

@Composable
private fun BasmalaRow(style: TextStyle) {
    Text(
        text     = "بِسْمِ اللَّهِ الرَّحْمَٰنِ الرَّحِيمِ",
        style    = style,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    )
}

@Composable
private fun SuraHeaderRow(seg: QuranSegment.SuraHeader, style: TextStyle) {
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
            text     = "  ${seg.name}  ",
            style    = style,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        HorizontalDivider(
            modifier  = Modifier.weight(1f),
            color     = MaterialTheme.colorScheme.outlineVariant,
            thickness = 0.8.dp,
        )
    }
}

@Composable
private fun AyaFlowRow(seg: QuranSegment.AyaFlow, style: TextStyle) {
    Text(
        text     = seg.text,
        style    = style,
        modifier = Modifier.fillMaxWidth(),
    )
}

// ── Shared ────────────────────────────────────────────────────────────────────

@Composable
internal fun LoadingBox() {
    Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
}