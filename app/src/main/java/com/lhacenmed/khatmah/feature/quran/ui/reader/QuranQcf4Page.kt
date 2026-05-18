package com.lhacenmed.khatmah.feature.quran.ui.reader

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Typeface
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.lhacenmed.khatmah.R
import com.lhacenmed.khatmah.core.nav.LocalNavController
import com.lhacenmed.khatmah.core.ui.components.OptionSelectBottomSheet
import com.lhacenmed.khatmah.core.ui.components.SheetOption
import com.lhacenmed.khatmah.feature.audio.AyaAudioManager
import com.lhacenmed.khatmah.feature.audio.AyaPlayerBar
import com.lhacenmed.khatmah.feature.audio.DriveAudioRepository
import com.lhacenmed.khatmah.feature.quran.data.HafsQcf4Repository
import com.lhacenmed.khatmah.feature.quran.data.Qcf4Page
import com.lhacenmed.khatmah.feature.quran.ui.QuranViewModel
import com.lhacenmed.khatmah.feature.quran.ui.components.ImageTopBar
import com.lhacenmed.khatmah.feature.quran.ui.components.QuranBottomBar
import com.lhacenmed.khatmah.shared.util.AppPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private const val QCF4_ANIM_MS   = 280
private const val MIN_LINE_COUNT = 15
private const val CENTER_SCALE   = 1f

// ── Rendered word ─────────────────────────────────────────────────────────────

private data class WordRender(
    val char:     String,
    val x:        Float,
    val baseline: Float,
    val width:    Float,
    val paint:    android.graphics.Paint,
    val verseKey: String?,
)

private data class LineRender(val words: List<WordRender>)

// ── Page cache entry ──────────────────────────────────────────────────────────

private sealed class PageCacheEntry {
    data class Ready(val page: Qcf4Page, val faces: Map<String, Typeface>) : PageCacheEntry()
    data object Loading : PageCacheEntry()
    data class Err(val msg: String) : PageCacheEntry()
}

// ── Layout computation ────────────────────────────────────────────────────────

/**
 * Computes word positions for every line on the page.
 *
 * Line slot height is capped at [MIN_LINE_COUNT]-density so sparse pages
 * (Al-Fatiha, short end-of-Quran suras) don't produce disproportionately
 * large glyphs. The resulting content block is vertically centered.
 *
 * Normal full lines are RTL-justified across [usableW].
 * Lines whose natural word-width is below [CENTER_RATIO] × usableW are centered.
 * surah_header / bismillah / end words receive [accentArgb]; all others [textArgb].
 */
private fun computeLayout(
    page:       Qcf4Page,
    faces:      Map<String, Typeface>,
    size:       IntSize,
    textArgb:   Int,
    accentArgb: Int,
): List<LineRender> {
    val lineCount = page.lines.size
    if (lineCount == 0 || size == IntSize.Zero) return emptyList()

    val w = size.width.toFloat()
    val h = size.height.toFloat()

    val hPad    = w * 0.02f
    val vPad    = h * 0.14f
    val usableW = w - 2f * hPad
    val usableH = h - 2f * vPad

    // Height-constrained slot, capped so sparse pages don't produce oversized glyphs.
    val lineH = (usableH / lineCount).coerceAtMost(usableH / MIN_LINE_COUNT.toFloat())

    val candWordSz = lineH * 0.65f
    val candHdrSz  = candWordSz * CENTER_SCALE

    // Pass 1 — measure every line at candidate sizes; find the widest.
    // One shared Paint avoids allocations in this probe loop.
    val probe = android.graphics.Paint().apply { isAntiAlias = true }
    var maxLineW = 0f
    for (line in page.lines) {
        var lineW = 0f
        for (word in line.words) {
            probe.typeface = faces[word.font] ?: Typeface.DEFAULT
            probe.textSize = if (word.type == "surah_header") candHdrSz else candWordSz
            lineW += probe.measureText(word.char)
        }
        if (lineW > maxLineW) maxLineW = lineW
    }

    // Derive a single uniform scale so the widest line fits usableW exactly.
    // All lines share this scale → consistent glyph size across the whole page,
    // in both portrait and landscape.
    val fontScale    = if (maxLineW > usableW) usableW / maxLineW else 1f
    val wordFontSz   = candWordSz * fontScale
    val headerFontSz = candHdrSz  * fontScale

    // Vertically center the content block.
    val topOffset = vPad + (usableH - lineH * lineCount) / 2f

    // Pass 2 — render every line centered. No short/full distinction, no per-line scaling.
    return page.lines.mapIndexed { idx, line ->
        val baseline = topOffset + idx * lineH + lineH * 0.78f

        val measured = line.words.map { word ->
            val sz = if (word.type == "surah_header") headerFontSz else wordFontSz
            val p  = android.graphics.Paint().apply {
                typeface    = faces[word.font] ?: Typeface.DEFAULT
                textSize    = sz
                isAntiAlias = true
                color       = when (word.type) {
                    "surah_header", "bismillah", "end" -> accentArgb
                    else                               -> textArgb
                }
            }
            Triple(word, p, p.measureText(word.char))
        }

        // Center the line block. Guaranteed to fit usableW after fontScale.
        val totalW = measured.sumOf { it.third.toDouble() }.toFloat()
        var x      = (w + totalW) / 2f   // RTL: begin from right edge of centered block

        LineRender(measured.map { (word, paint, ww) ->
            WordRender(word.char, x - ww, baseline, ww, paint, word.verseKey)
                .also { x -= ww }
        })
    }
}

/** Returns the first [WordRender] whose bounding box contains [offset], or null. */
private fun hitWord(layout: List<LineRender>, offset: Offset): WordRender? {
    for (line in layout) {
        for (word in line.words) {
            val top    = word.baseline + word.paint.ascent()
            val bottom = word.baseline + word.paint.descent()
            if (offset.x in word.x..(word.x + word.width) && offset.y in top..bottom)
                return word
        }
    }
    return null
}

// ── Single page ───────────────────────────────────────────────────────────────

/**
 * Renders one QCF4 Hafs mushaf page using Android Canvas.
 *
 * Layout is recomputed only when [pageData], composable [size], or colors change.
 * Long-press on a word triggers [onAyaPress] with the word's (sura, aya).
 * Tap anywhere fires [onTap].
 */
@Composable
internal fun QuranQcf4Page(
    pageData:    Qcf4Page,
    typefaces:   Map<String, Typeface>,
    selectedAya: Pair<Int, Int>?,
    onTap:       () -> Unit,
    onAyaPress:  (sura: Int, aya: Int) -> Unit,
) {
    val isDark        = isSystemInDarkTheme()
    val textArgb      = MaterialTheme.colorScheme.onBackground.toArgb()
    val accentArgb    = MaterialTheme.colorScheme.primary.toArgb()
    val highlightArgb = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f).toArgb()

    var size by remember { mutableStateOf(IntSize.Zero) }

    val layout = remember(pageData.page, size, isDark, accentArgb) {
        computeLayout(pageData, typefaces, size, textArgb, accentArgb)
    }

    val hlPaint = remember(highlightArgb) {
        android.graphics.Paint().apply {
            color       = highlightArgb
            style       = android.graphics.Paint.Style.FILL
            isAntiAlias = true
        }
    }

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { size = it }
            .pointerInput(pageData.page) {
                detectTapGestures(
                    onTap       = { onTap() },
                    onLongPress = { offset ->
                        val hit = hitWord(layout, offset) ?: return@detectTapGestures
                        val key = hit.verseKey ?: return@detectTapGestures
                        val parts = key.split(":")
                        if (parts.size == 2) {
                            val sura = parts[0].toIntOrNull() ?: return@detectTapGestures
                            val aya  = parts[1].toIntOrNull() ?: return@detectTapGestures
                            onAyaPress(sura, aya)
                        }
                    },
                )
            },
    ) {
        drawIntoCanvas { canvas ->
            val native = canvas.nativeCanvas
            for (line in layout) {
                for (word in line.words) {
                    if (word.verseKey != null && selectedAya != null) {
                        val parts = word.verseKey.split(":")
                        if (parts.size == 2 &&
                            parts[0].toIntOrNull() == selectedAya.first &&
                            parts[1].toIntOrNull() == selectedAya.second
                        ) {
                            val top    = word.baseline + word.paint.ascent() - 4f
                            val bottom = word.baseline + word.paint.descent() + 4f
                            native.drawRect(word.x, top, word.x + word.width, bottom, hlPaint)
                        }
                    }
                    native.drawText(word.char, word.x, word.baseline, word.paint)
                }
            }
        }
    }
}

// ── Page loader ───────────────────────────────────────────────────────────────

/**
 * Loads [pageNum] into [cache] using [repo]. Tracks loading/error states so
 * the pager can show a retry prompt instead of an infinite spinner.
 */
private suspend fun loadPageIntoCache(
    pageNum: Int,
    repo:    HafsQcf4Repository,
    cache:   androidx.compose.runtime.snapshots.SnapshotStateMap<Int, PageCacheEntry>,
) {
    if (cache[pageNum] is PageCacheEntry.Ready) return
    cache[pageNum] = PageCacheEntry.Loading
    try {
        val data  = repo.pageData(pageNum)
        val faces = data.lines.flatMap { it.words }.map { it.font }.toSet()
            .associateWith { repo.typefaceFor(it) }
        cache[pageNum] = PageCacheEntry.Ready(data, faces)
    } catch (e: Exception) {
        cache[pageNum] = PageCacheEntry.Err(e.message ?: "Unknown error")
    }
}

// ── Main reader pager ─────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
internal fun QuranQcf4Pager(
    pageCount: Int,
    vm:        QuranViewModel,
    onSearch:  () -> Unit,
) {
    val nav        = LocalNavController.current
    val context    = LocalContext.current
    val scope      = rememberCoroutineScope()
    val repo       = remember { HafsQcf4Repository.get(context) }
    val pagerState = rememberPagerState(
        initialPage = vm.savedPage.coerceIn(0, pageCount - 1)
    ) { pageCount }

    val pageCache = remember { androidx.compose.runtime.snapshots.SnapshotStateMap<Int, PageCacheEntry>() }

    var barsVisible by remember { mutableStateOf(true) }
    var selectedAya by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    val audioState  by AyaAudioManager.state.collectAsState()
    val showReaderSheet = remember { mutableStateOf(false) }
    val readers         = remember { DriveAudioRepository(context).readers().readers }

    SyncQcf4SystemBars(barsVisible)
    DisposableEffect(Unit) { onDispose { AyaAudioManager.stop() } }

    LaunchedEffect(pagerState.settledPage) {
        vm.savePage(pagerState.settledPage)
        selectedAya = null
    }
    LaunchedEffect(audioState.suraNum, audioState.ayaNum) {
        if (audioState.active) selectedAya = audioState.suraNum to audioState.ayaNum
    }
    LaunchedEffect(audioState.active) {
        if (!audioState.active) selectedAya = null
    }

    val pendingJump by vm.pendingJump.collectAsState()
    LaunchedEffect(pendingJump) {
        pendingJump?.let { pagerState.scrollToPage(it); vm.consumeJump() }
    }

    // Pre-fetch current page + neighbours
    LaunchedEffect(pagerState.settledPage) {
        val cur = pagerState.settledPage + 1
        for (p in (cur - 1)..(cur + 1)) {
            if (p in 1..pageCount && pageCache[p] !is PageCacheEntry.Ready) {
                scope.launch(Dispatchers.IO) { loadPageIntoCache(p, repo, pageCache) }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        HorizontalPager(
            state    = pagerState,
            modifier = Modifier.fillMaxSize(),
            key      = { it },
        ) { idx ->
            val pageNum = idx + 1
            when (val entry = pageCache[pageNum]) {
                null, PageCacheEntry.Loading -> {
                    // Trigger load if not already in flight
                    LaunchedEffect(pageNum) {
                        scope.launch(Dispatchers.IO) { loadPageIntoCache(pageNum, repo, pageCache) }
                    }
                    Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
                }
                is PageCacheEntry.Err -> {
                    // Show error + retry button
                    Box(Modifier.fillMaxSize(), Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text  = "فشل تحميل الصفحة ${entry.msg}",
                                color = MaterialTheme.colorScheme.error,
                            )
                            Spacer(Modifier.height(8.dp))
                            Button(onClick = {
                                pageCache.remove(pageNum)
                                scope.launch(Dispatchers.IO) { loadPageIntoCache(pageNum, repo, pageCache) }
                            }) { Text("إعادة المحاولة") }
                        }
                    }
                }
                is PageCacheEntry.Ready -> {
                    QuranQcf4Page(
                        pageData    = entry.page,
                        typefaces   = entry.faces,
                        selectedAya = selectedAya,
                        onTap       = { barsVisible = !barsVisible },
                        onAyaPress  = { sura, aya ->
                            if (selectedAya?.first == sura && selectedAya?.second == aya) {
                                selectedAya = null; AyaAudioManager.stop()
                            } else {
                                selectedAya = sura to aya
                                AyaAudioManager.play(context, sura, aya, "")
                            }
                        },
                    )
                }
            }
        }

        AnimatedVisibility(
            visible  = barsVisible,
            enter    = slideInVertically(tween(QCF4_ANIM_MS)) { -it } + fadeIn(tween(QCF4_ANIM_MS)),
            exit     = slideOutVertically(tween(QCF4_ANIM_MS)) { -it } + fadeOut(tween(QCF4_ANIM_MS / 2)),
            modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth(),
        ) {
            ImageTopBar(
                pageNum  = pagerState.settledPage + 1,
                onBack   = { nav.popBackStack() },
                onSearch = onSearch,
            )
        }

        AnimatedVisibility(
            visible  = barsVisible,
            enter    = slideInVertically(tween(QCF4_ANIM_MS)) { it } + fadeIn(tween(QCF4_ANIM_MS)),
            exit     = slideOutVertically(tween(QCF4_ANIM_MS)) { it } + fadeOut(tween(QCF4_ANIM_MS / 2)),
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
        ) {
            Column {
                AnimatedVisibility(
                    visible = audioState.active,
                    enter   = slideInVertically { it } + fadeIn(),
                    exit    = slideOutVertically { it } + fadeOut(),
                ) {
                    AyaPlayerBar(
                        state         = audioState,
                        onToggle      = { AyaAudioManager.togglePlayPause() },
                        onReaderClick = { showReaderSheet.value = true },
                        onClose       = { selectedAya = null; AyaAudioManager.stop() },
                    )
                }
                QuranBottomBar(
                    currentPage = pagerState.currentPage,
                    totalPages  = pageCount,
                    onJump      = { t -> scope.launch { pagerState.scrollToPage(t) } },
                )
            }
        }
    }

    if (showReaderSheet.value) {
        val currentReaderId by AppPrefs.audioReaderId.collectAsState()
        OptionSelectBottomSheet(
            title     = stringResource(R.string.quran_reader_select),
            options   = readers.map { SheetOption(it.id, it.name) },
            selected  = currentReaderId,
            onDismiss = { showReaderSheet.value = false },
            onSelect  = { id ->
                AppPrefs.setAudioReaderId(context, id)
                showReaderSheet.value = false
                if (audioState.active)
                    AyaAudioManager.play(context, audioState.suraNum, audioState.ayaNum, "")
            },
        )
    }
}

// ── Session reader pager ──────────────────────────────────────────────────────

/** Khatmah session variant: renders pages [startPage]..[endPage] (1-based) only. */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
internal fun SessionQcf4Pager(startPage: Int, endPage: Int) {
    val nav        = LocalNavController.current
    val context    = LocalContext.current
    val scope      = rememberCoroutineScope()
    val repo       = remember { HafsQcf4Repository.get(context) }
    val pageCount  = endPage - startPage + 1
    val pagerState = rememberPagerState(0) { pageCount }

    val pageCache       = remember { androidx.compose.runtime.snapshots.SnapshotStateMap<Int, PageCacheEntry>() }
    var barsVisible     by remember { mutableStateOf(true) }
    var selectedAya     by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    val audioState      by AyaAudioManager.state.collectAsState()
    val showReaderSheet = remember { mutableStateOf(false) }
    val readers         = remember { DriveAudioRepository(context).readers().readers }

    SyncQcf4SystemBars(barsVisible)
    DisposableEffect(Unit) { onDispose { AyaAudioManager.stop() } }

    LaunchedEffect(pagerState.settledPage) { selectedAya = null }
    LaunchedEffect(audioState.suraNum, audioState.ayaNum) {
        if (audioState.active) selectedAya = audioState.suraNum to audioState.ayaNum
    }
    LaunchedEffect(audioState.active) { if (!audioState.active) selectedAya = null }

    // Pre-fetch current + neighbours
    LaunchedEffect(pagerState.settledPage) {
        val cur = startPage + pagerState.settledPage
        for (p in (cur - 1)..(cur + 1)) {
            if (p in startPage..endPage && pageCache[p] !is PageCacheEntry.Ready) {
                scope.launch(Dispatchers.IO) { loadPageIntoCache(p, repo, pageCache) }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize(), key = { it }) { idx ->
            val pageNum = startPage + idx
            when (val entry = pageCache[pageNum]) {
                null, PageCacheEntry.Loading -> {
                    LaunchedEffect(pageNum) {
                        scope.launch(Dispatchers.IO) { loadPageIntoCache(pageNum, repo, pageCache) }
                    }
                    Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
                }
                is PageCacheEntry.Err -> {
                    Box(Modifier.fillMaxSize(), Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text  = "فشل تحميل الصفحة ${entry.msg}",
                                color = MaterialTheme.colorScheme.error,
                            )
                            Spacer(Modifier.height(8.dp))
                            Button(onClick = {
                                pageCache.remove(pageNum)
                                scope.launch(Dispatchers.IO) { loadPageIntoCache(pageNum, repo, pageCache) }
                            }) { Text("إعادة المحاولة") }
                        }
                    }
                }
                is PageCacheEntry.Ready -> {
                    QuranQcf4Page(
                        pageData    = entry.page,
                        typefaces   = entry.faces,
                        selectedAya = selectedAya,
                        onTap       = { barsVisible = !barsVisible },
                        onAyaPress  = { sura, aya ->
                            if (selectedAya?.first == sura && selectedAya?.second == aya) {
                                selectedAya = null; AyaAudioManager.stop()
                            } else {
                                selectedAya = sura to aya
                                AyaAudioManager.play(context, sura, aya, "")
                            }
                        },
                    )
                }
            }
        }

        AnimatedVisibility(
            visible  = barsVisible,
            enter    = slideInVertically(tween(QCF4_ANIM_MS)) { -it } + fadeIn(tween(QCF4_ANIM_MS)),
            exit     = slideOutVertically(tween(QCF4_ANIM_MS)) { -it } + fadeOut(tween(QCF4_ANIM_MS / 2)),
            modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth(),
        ) {
            ImageTopBar(
                pageNum  = startPage + pagerState.settledPage,
                onBack   = { nav.popBackStack() },
                onSearch = {},
            )
        }

        AnimatedVisibility(
            visible  = barsVisible,
            enter    = slideInVertically(tween(QCF4_ANIM_MS)) { it } + fadeIn(tween(QCF4_ANIM_MS)),
            exit     = slideOutVertically(tween(QCF4_ANIM_MS)) { it } + fadeOut(tween(QCF4_ANIM_MS / 2)),
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
        ) {
            Column {
                AnimatedVisibility(
                    visible = audioState.active,
                    enter   = slideInVertically { it } + fadeIn(),
                    exit    = slideOutVertically { it } + fadeOut(),
                ) {
                    AyaPlayerBar(
                        state         = audioState,
                        onToggle      = { AyaAudioManager.togglePlayPause() },
                        onReaderClick = { showReaderSheet.value = true },
                        onClose       = { selectedAya = null; AyaAudioManager.stop() },
                    )
                }
                QuranBottomBar(
                    currentPage = pagerState.currentPage,
                    totalPages  = pageCount,
                    onJump      = { t -> scope.launch { pagerState.scrollToPage(t) } },
                )
            }
        }
    }

    if (showReaderSheet.value) {
        val currentReaderId by AppPrefs.audioReaderId.collectAsState()
        OptionSelectBottomSheet(
            title     = stringResource(R.string.quran_reader_select),
            options   = readers.map { SheetOption(it.id, it.name) },
            selected  = currentReaderId,
            onDismiss = { showReaderSheet.value = false },
            onSelect  = { id ->
                AppPrefs.setAudioReaderId(context, id)
                showReaderSheet.value = false
                if (audioState.active)
                    AyaAudioManager.play(context, audioState.suraNum, audioState.ayaNum, "")
            },
        )
    }
}

// ── System bar sync ───────────────────────────────────────────────────────────

@Composable
private fun SyncQcf4SystemBars(visible: Boolean) {
    val view = LocalView.current
    val ctrl = remember(view) {
        view.context.qcf4Activity()?.window?.let { w ->
            WindowCompat.getInsetsController(w, view)
        }
    }
    DisposableEffect(ctrl, visible) {
        ctrl?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (visible) ctrl?.show(WindowInsetsCompat.Type.systemBars())
        else         ctrl?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose { ctrl?.show(WindowInsetsCompat.Type.systemBars()) }
    }
}

private tailrec fun Context.qcf4Activity(): Activity? = when (this) {
    is Activity       -> this
    is ContextWrapper -> baseContext.qcf4Activity()
    else              -> null
}