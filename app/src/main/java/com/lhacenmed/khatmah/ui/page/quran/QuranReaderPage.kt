package com.lhacenmed.khatmah.ui.page.quran

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lhacenmed.khatmah.ui.theme.WarshFamily

// Uthmani-style Basmala — matches the glyph style used throughout the app
private const val BASMALA_TEXT = "بِسْمِ اِ۬للَّهِ اِ۬لرَّحْمَٰنِ اِ۬لرَّحِيمِ"

// ── Entry point ───────────────────────────────────────────────────────────────

@Composable
fun QuranReaderScreen(padding: PaddingValues) {
    val vm: QuranViewModel = viewModel()
    val state by vm.state.collectAsState()

    Box(
        modifier         = Modifier
            .fillMaxSize()
            .padding(padding),
        contentAlignment = Alignment.Center,
    ) {
        when (val s = state) {
            QuranViewModel.State.Loading -> CircularProgressIndicator()
            is QuranViewModel.State.Ready -> if (s.pages.isNotEmpty()) {
                QuranPager(
                    pages      = s.pages,
                    initPage   = vm.savedPage,
                    onSavePage = vm::savePage,
                )
            }
        }
    }
}

// ── Pager ─────────────────────────────────────────────────────────────────────

@Composable
private fun QuranPager(
    pages:      List<QuranPage>,
    initPage:   Int,
    onSavePage: (Int) -> Unit,
) {
    val isRtl      = LocalLayoutDirection.current == LayoutDirection.Rtl
    val pagerState = rememberPagerState(
        initialPage = initPage.coerceIn(0, pages.lastIndex),
    ) { pages.size }

    // Persist position only after the swipe gesture fully settles (not mid-fling)
    LaunchedEffect(pagerState.settledPage) { onSavePage(pagerState.settledPage) }

    Column(modifier = Modifier.fillMaxSize()) {
        PageBar(
            current = pagerState.currentPage + 1,
            total   = pages.size,
            isRtl   = isRtl,
        )
        HorizontalDivider()
        // reverseLayout = true makes page 0 (Al-Fatiha) appear on the physical
        // RIGHT side, matching Arabic book convention. The pagerState index is
        // always 0...N regardless of layout direction.
        HorizontalPager(
            state         = pagerState,
            reverseLayout = isRtl,
            modifier      = Modifier
                .weight(1f)
                .fillMaxWidth(),
            key           = { pages[it].number },
        ) { idx ->
            PageContent(page = pages[idx])
        }
    }
}

// ── Page number bar ───────────────────────────────────────────────────────────

@Composable
private fun PageBar(current: Int, total: Int, isRtl: Boolean) {
    Box(
        modifier         = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text  = if (isRtl) "${arabicNum(current)} / ${arabicNum(total)}"
            else       "$current / $total",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ── Page content ──────────────────────────────────────────────────────────────

@Composable
private fun PageContent(page: QuranPage) {
    LazyColumn(
        modifier            = Modifier.fillMaxSize(),
        contentPadding      = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items(page.items, key = ::itemKey) { item ->
            when (item) {
                is QuranPageItem.Basmala    -> BasmalaRow()
                is QuranPageItem.SuraHeader -> SuraHeaderRow(item)
                is QuranPageItem.Aya        -> AyaRow(item)
            }
        }
    }
}

// ── Item rows ─────────────────────────────────────────────────────────────────

/** Decorative Basmala shown at the top of each sura (except 1 and 9). */
@Composable
private fun BasmalaRow() {
    Text(
        text       = BASMALA_TEXT,
        fontFamily = WarshFamily,
        fontSize   = 18.sp,
        lineHeight = 30.sp,
        textAlign  = TextAlign.Center,
        color      = MaterialTheme.colorScheme.primary,
        modifier   = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
    )
}

/** Sura name centred between two horizontal dividers. */
@Composable
private fun SuraHeaderRow(item: QuranPageItem.SuraHeader) {
    Row(
        modifier          = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color    = MaterialTheme.colorScheme.outlineVariant,
        )
        Text(
            text       = item.name,
            style      = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color      = MaterialTheme.colorScheme.primary,
            modifier   = Modifier.padding(horizontal = 12.dp),
        )
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color    = MaterialTheme.colorScheme.outlineVariant,
        )
    }
}

/**
 * Aya text followed by its number in Quranic ornate brackets ﴿١﴾.
 * Justified alignment spreads Arabic text naturally across the full line width.
 */
@Composable
private fun AyaRow(item: QuranPageItem.Aya) {
    Text(
        text       = "${item.text} ${arabicNum(item.ayaNum)}",
        fontFamily = WarshFamily,
        fontSize   = 19.sp,
        lineHeight = 36.sp,
        textAlign  = TextAlign.Justify,
        color      = MaterialTheme.colorScheme.onBackground,
        modifier   = Modifier.fillMaxWidth(),
    )
}

// ── Helpers ───────────────────────────────────────────────────────────────────

/** Converts a Western integer to Eastern Arabic numerals (٠١٢٣٤٥٦٧٨٩). */
private fun arabicNum(n: Int): String = n.toString().map {
    "٠١٢٣٤٥٦٧٨٩"[it - '0']
}.joinToString("")

/** Stable, globally unique key per item for LazyColumn composition. */
private fun itemKey(item: QuranPageItem): String = when (item) {
    is QuranPageItem.Basmala    -> "b${item.suraNum}"
    is QuranPageItem.SuraHeader -> "s${item.num}"
    is QuranPageItem.Aya        -> "a${item.suraNum}_${item.ayaNum}"
}