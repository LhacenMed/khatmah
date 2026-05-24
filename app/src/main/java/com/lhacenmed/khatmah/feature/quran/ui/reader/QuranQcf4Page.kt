package com.lhacenmed.khatmah.feature.quran.ui.reader

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Typeface
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.ui.graphics.graphicsLayer
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
import com.lhacenmed.khatmah.feature.mushaf.data.Riwaya
import com.lhacenmed.khatmah.feature.quran.data.Qcf4Page
import com.lhacenmed.khatmah.feature.quran.data.Qcf4PageSource
import com.lhacenmed.khatmah.feature.quran.ui.QuranViewModel
import com.lhacenmed.khatmah.feature.quran.ui.components.ImageTopBar
import com.lhacenmed.khatmah.feature.quran.ui.components.QuranBottomBar
import com.lhacenmed.khatmah.shared.util.AppPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.core.content.res.ResourcesCompat

private const val QCF4_ANIM_MS   = 280
private const val MIN_LINE_COUNT = 15
private const val HDR_LINE_SCALE = 1.6f  // header slot height multiplier vs normal line
private const val HDR_NAME_SCALE = 0.50f // name glyph height as fraction of lineH
private const val ZOOM_SCALE     = 2.5f

// Proportional x-offset of the glyph's built-in circle centers from each edge (0..1).
// Calibrated to the QCF2_QBSML font design — tune if circles drift.
private const val CIRCLE_OFFSET = 0.21f

private const val CIRCLE_LABEL_SCALE = 0.12f  // label font as fraction of containerH — decrease to shrink
private const val CIRCLE_NUM_SCALE   = 0.09f  // number font as fraction of containerH — decrease to shrink
private const val CIRCLE_GAP_SCALE   = -0.05f  // gap between label and number as fraction of containerH

// ── Surah info ────────────────────────────────────────────────────────────────

private val EAST_ARABIC_DIGITS = charArrayOf('٠','١','٢','٣','٤','٥','٦','٧','٨','٩')

private val SURA_AYA_COUNTS_HAFS = intArrayOf(
    7,   286, 200, 176, 120, 165, 206,  75, 129, 109,
    123, 111,  43,  52,  99, 128, 111, 110,  98, 135,
    112,  78, 118,  64,  77, 227,  93,  88,  69,  60,
    34,  30,  73,  54,  45,  83, 182,  88,  75,  85,
    54,  53,  89,  59,  37,  35,  38,  29,  18,  45,
    60,  49,  62,  55,  78,  96,  29,  22,  24,  13,
    14,  11,  11,  18,  12,  12,  30,  52,  52,  44,
    28,  28,  20,  56,  40,  31,  50,  40,  46,  42,
    29,  19,  36,  25,  22,  17,  19,  26,  30,  20,
    15,  21,  11,   8,   8,  19,   5,   8,   8,  11,
    11,   8,   3,   9,   5,   4,   7,   3,   6,   3,
    5,   4,   5,   6,
)

private val SURA_AYA_COUNTS_WARSH = intArrayOf(
    7,   285, 200, 175, 122, 167, 206,  76, 130, 109,
    121, 111,  44,  54,  99, 128, 110, 105,  99, 134,
    111,  76, 119,  62,  77, 226,  95,  88,  69,  59,
    33,  30,  73,  54,  46,  82, 182,  86,  72,  84,
    53,  50,  89,  56,  36,  34,  39,  29,  18,  45,
    60,  47,  61,  55,  77,  99,  28,  21,  24,  13,
    14,  11,  11,  18,  12,  12,  31,  52,  52,  44,
    30,  28,  18,  55,  39,  31,  50,  40,  45,  42,
    29,  19,  36,  25,  22,  17,  19,  26,  32,  20,
    15,  21,  11,   8,   8,  20,   5,   8,   9,  11,
    10,   8,   3,   9,   5,   5,   6,   3,   6,   3,
    5,   4,   5,   6,
)

private fun ayaCount(riwaya: Riwaya, suraNum: Int): Int =
    if (suraNum in 1..114)
        (if (riwaya == Riwaya.HAFS) SURA_AYA_COUNTS_HAFS else SURA_AYA_COUNTS_WARSH)[suraNum - 1]
    else 0

private fun toEastArabic(n: Int): String =
    n.toString().map { EAST_ARABIC_DIGITS[it - '0'] }.joinToString("")

// ── Rendered word ─────────────────────────────────────────────────────────────

private data class WordRender(
    val char:     String,
    val x:        Float,
    val baseline: Float,
    val width:    Float,
    val paint:    android.graphics.Paint,
    val verseKey: String?,
)

/** Surah header ornamental frame, drawn behind the name glyph. */
private data class ContainerRender(
    val x:        Float,
    val baseline: Float,
    val paint:    android.graphics.Paint,
    /** Non-null when sura is valid; text is placed inside the glyph's own circle slots. */
    val circles:  SurahCircles? = null,
)

/**
 * Numbers and labels positioned inside the two ornamental circle slots already present in
 * [CONTAINER_CHAR]. Label uses KFGQPC calligraphic font; number uses system medium face.
 *
 * [orderCx] — canvas-right circle = sura order ("ترتيبها" + number)
 * [ayaCx]   — canvas-left  circle = aya count  ("آياتها" + number)
 */
private class SurahCircles(
    val orderCx:    Float,
    val ayaCx:      Float,
    val labelY:     Float,      // baseline for the calligraphic label
    val numY:       Float,      // baseline for the east-arabic number
    val orderStr:   String,
    val ayaStr:     String,
    val labelPaint: android.graphics.Paint,
    val numPaint:   android.graphics.Paint,
)

private data class LineRender(
    val words:     List<WordRender>,
    val container: ContainerRender? = null,
)

// ── Container glyph ───────────────────────────────────────────────────────────

/** Surah header frame glyph in QCF2_QBSML (U+00F2 / U+FC20). */
private const val CONTAINER_CHAR = "\u00F2"

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
    riwaya:     Riwaya,
    kfgqpcFace: Typeface,
): List<LineRender> {
    val lineCount = page.lines.size
    if (lineCount == 0 || size == IntSize.Zero) return emptyList()

    val w = size.width.toFloat()
    val h = size.height.toFloat()

    val hPad    = w * 0.02f
    val vPad    = h * 0.14f
    val usableW = w - 2f * hPad
    val usableH = h - 2f * vPad

    val lineH      = (usableH / lineCount).coerceAtMost(usableH / MIN_LINE_COUNT.toFloat())
    val candWordSz = lineH * 0.65f

    // Pass 1 — measure only non-header lines.
    // Header lines use an independently sized name glyph and don't affect fontScale.
    val probe = android.graphics.Paint().apply { isAntiAlias = true }
    var maxLineW = 0f
    for (line in page.lines) {
        if (line.words.any { it.type == "surah_header" }) continue
        var lineW = 0f
        for (word in line.words) {
            probe.typeface = faces[word.font] ?: Typeface.DEFAULT
            probe.textSize = candWordSz
            lineW += probe.measureText(word.char)
        }
        if (lineW > maxLineW) maxLineW = lineW
    }

    val fontScale  = if (maxLineW > usableW) usableW / maxLineW else 1f
    val wordFontSz = candWordSz * fontScale
    // Name glyph: fixed fraction of lineH, never affected by fontScale.
    val nameFontSz = lineH * HDR_NAME_SCALE

    // Header lines reserve extra vertical room so the container glyph isn't clipped.
    val slotHeights   = page.lines.map { line ->
        if (line.words.any { it.type == "surah_header" }) lineH * HDR_LINE_SCALE else lineH
    }
    val totalContentH = slotHeights.sum()
    val topOffset     = vPad + (usableH - totalContentH) / 2f

    // Pass 2 — cumulative Y, per-line slot heights.
    var cumY = 0f
    return page.lines.mapIndexed { idx, line ->
        val slotH   = slotHeights[idx]
        val slotTop = topOffset + cumY
        cumY += slotH

        val isHeader = line.words.any { it.type == "surah_header" }

        // Build ornamental container for surah-header lines, centered in the slot.
        val container: ContainerRender? = if (isHeader) run {
            val qcf2Face = faces["QCF2_QBSML"] ?: return@run null
            val p = android.graphics.Paint().apply {
                typeface    = qcf2Face
                isAntiAlias = true
                textSize    = 1f
                color       = accentArgb
            }
            val natW = p.measureText(CONTAINER_CHAR)
            if (natW <= 0f) return@run null
            p.textSize = usableW / natW
            // Center: baseline = slotMidY - (ascent + descent) / 2
            val ctrBaseline = slotTop + slotH / 2f - (p.ascent() + p.descent()) / 2f

            // ── Numbers inside the glyph's built-in circle slots ──────────────
            // The circles are negative space (holes) in the frame glyph.
            // We measure the rendered container height to size the numbers correctly,
            // then place them at the fixed proportional offsets from each edge.
            val suraNum = line.words.firstOrNull()?.sura ?: 0
            val circles: SurahCircles? = if (suraNum in 1..114) {
                val fm         = p.fontMetrics
                val containerH = fm.descent - fm.ascent
                val circleCy   = ctrBaseline + (fm.ascent + fm.descent) / 2f

                val labelFontSz = containerH * CIRCLE_LABEL_SCALE
                val numFontSz   = containerH * CIRCLE_NUM_SCALE

                val labelP = android.graphics.Paint().apply {
                    isAntiAlias = true
                    color       = accentArgb
                    textSize    = labelFontSz
                    textAlign   = android.graphics.Paint.Align.CENTER
                    typeface    = kfgqpcFace
                }
                val numP = android.graphics.Paint().apply {
                    isAntiAlias = true
                    color       = accentArgb
                    textSize    = numFontSz
                    textAlign   = android.graphics.Paint.Align.CENTER
                    typeface    = android.graphics.Typeface.create(
                        "sans-serif-medium", android.graphics.Typeface.NORMAL
                    )
                }

                // Stack label above number, center the block at circleCy.
                val labelLineH = labelP.descent() - labelP.ascent()
                val numLineH   = numP.descent()   - numP.ascent()
                val gap        = containerH * CIRCLE_GAP_SCALE
                val stackTop   = circleCy - (labelLineH + gap + numLineH) / 2f
                val labelY     = stackTop - labelP.ascent()
                val numY       = stackTop + labelLineH + gap - numP.ascent()

                SurahCircles(
                    orderCx    = hPad + usableW * (1f - CIRCLE_OFFSET),
                    ayaCx      = hPad + usableW * CIRCLE_OFFSET,
                    labelY     = labelY,
                    numY       = numY,
                    orderStr   = toEastArabic(suraNum),
                    ayaStr     = toEastArabic(ayaCount(riwaya, suraNum)),
                    labelPaint = labelP,
                    numPaint   = numP,
                )
            } else null

            ContainerRender(x = hPad, baseline = ctrBaseline, paint = p, circles = circles)
        } else null

        // Baseline: name glyph aligned to slot vertical center; normal lines use 78% rule.
        val baseline: Float = if (isHeader && container != null) {
            probe.typeface = faces["QCF4_QBSML"] ?: Typeface.DEFAULT
            probe.textSize = nameFontSz
            slotTop + slotH / 2f - (probe.ascent() + probe.descent()) / 2f
        } else {
            slotTop + slotH * 0.78f
        }

        val measured = line.words.map { word ->
            val sz = if (isHeader) nameFontSz else wordFontSz
            val p  = android.graphics.Paint().apply {
                typeface    = faces[word.font] ?: Typeface.DEFAULT
                textSize    = sz
                isAntiAlias = true
                color       = when (word.type) {
                    "surah_header", "bismillah", "end", "aya_end" -> accentArgb
                    else                                           -> textArgb
                }
            }
            Triple(word, p, p.measureText(word.char))
        }

        // Center the line block horizontally (RTL: start from right edge of centered block).
        val totalW = measured.sumOf { it.third.toDouble() }.toFloat()
        var x      = (w + totalW) / 2f

        LineRender(
            words = measured.map { (word, paint, ww) ->
                WordRender(word.char, x - ww, baseline, ww, paint, word.verseKey)
                    .also { x -= ww }
            },
            container = container,
        )
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
 * Renders one QCF4 mushaf page using Android Canvas.
 *
 * Layout is recomputed only when [pageData], composable [size], or colors change.
 * Long-press on a word triggers [onAyaPress] with the word's (sura, aya).
 * Tap anywhere fires [onTap]; double-tap toggles zoom.
 */
@Composable
internal fun QuranQcf4Page(
    pageData:    Qcf4Page,
    typefaces:   Map<String, Typeface>,
    selectedAya: Pair<Int, Int>?,
    isZoomed:    Boolean,
    zoomOffset:  Offset,
    riwaya:      Riwaya,
    onTap:       () -> Unit,
    onDoubleTap: () -> Unit,
    onDrag:      (Offset) -> Unit,
    onAyaPress:  (sura: Int, aya: Int) -> Unit,
) {
    val isDark        = isSystemInDarkTheme()
    val textArgb      = MaterialTheme.colorScheme.onBackground.toArgb()
    val accentArgb    = MaterialTheme.colorScheme.primary.toArgb()
    val highlightArgb = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f).toArgb()
    val context    = LocalContext.current
    val calligraphicFace = remember(riwaya) {
        val fontRes = if (riwaya == Riwaya.HAFS) R.font.kfgqpc_hafs_uthmanic else R.font.kfgqpc_warsh_uthmanic
        ResourcesCompat.getFont(context, fontRes) ?: Typeface.DEFAULT
    }

    var size by remember { mutableStateOf(IntSize.Zero) }

    val layout = remember(pageData.page, size, isDark, accentArgb, riwaya) {
        computeLayout(pageData, typefaces, size, textArgb, accentArgb, riwaya, calligraphicFace)
    }

    val hlPaint = remember(highlightArgb) {
        android.graphics.Paint().apply {
            color       = highlightArgb
            style       = android.graphics.Paint.Style.FILL
            isAntiAlias = true
        }
    }

    Box(modifier = Modifier.fillMaxSize().onSizeChanged { size = it }) {
        // ── Zoomed canvas ──────────────────────────────────────────────────────
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val s = if (isZoomed) ZOOM_SCALE else 1f
                    scaleX       = s
                    scaleY       = s
                    translationX = if (isZoomed) zoomOffset.x else 0f
                    translationY = if (isZoomed) zoomOffset.y else 0f
                },
        ) {
            drawIntoCanvas { canvas ->
                val native = canvas.nativeCanvas
                for (line in layout) {
                    line.container?.let { ctr ->
                        // Draw the ornamental frame glyph (includes the circular slots).
                        native.drawText(CONTAINER_CHAR, ctr.x, ctr.baseline, ctr.paint)
                        // Place sura order + aya count inside the glyph's existing circle slots.
                        ctr.circles?.let { c ->
                            // Right circle: sura order label + number
                            native.drawText("ترتيبها", c.orderCx, c.labelY, c.labelPaint)
                            native.drawText(c.orderStr, c.orderCx, c.numY,   c.numPaint)
                            // Left circle: aya count label + number
                            native.drawText("آياتها",  c.ayaCx,   c.labelY, c.labelPaint)
                            native.drawText(c.ayaStr,  c.ayaCx,   c.numY,   c.numPaint)
                        }
                    }
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

        // ── Gesture overlay (original coords, no transform) ────────────────────
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(isZoomed) {
                    if (isZoomed) detectDragGestures { _, drag -> onDrag(drag) }
                }
                .pointerInput(pageData.page) {
                    detectTapGestures(
                        onTap       = { onTap() },
                        onDoubleTap = { onDoubleTap() },
                        onLongPress = { offset ->
                            if (isZoomed) return@detectTapGestures
                            val hit   = hitWord(layout, offset) ?: return@detectTapGestures
                            val key   = hit.verseKey ?: return@detectTapGestures
                            val parts = key.split(":")
                            if (parts.size == 2) {
                                val sura = parts[0].toIntOrNull() ?: return@detectTapGestures
                                val aya  = parts[1].toIntOrNull() ?: return@detectTapGestures
                                onAyaPress(sura, aya)
                            }
                        },
                    )
                },
        )
    }
}

// ── Page loader ───────────────────────────────────────────────────────────────

/**
 * Loads [pageNum] into [cache] using [repo]. Tracks loading/error states so
 * the pager can show a retry prompt instead of an infinite spinner.
 */
private suspend fun loadPageIntoCache(
    pageNum: Int,
    repo:    Qcf4PageSource,
    cache:   androidx.compose.runtime.snapshots.SnapshotStateMap<Int, PageCacheEntry>,
) {
    if (cache[pageNum] is PageCacheEntry.Ready) return
    cache[pageNum] = PageCacheEntry.Loading
    try {
        val data      = repo.pageData(pageNum)
        val fontNames = data.lines.flatMap { it.words }.map { it.font }.toMutableSet()
        // Include the shared container font for pages that have a surah header line.
        if (data.lines.any { l -> l.words.any { it.type == "surah_header" } }) {
            fontNames += "QCF2_QBSML"
        }
        val faces = fontNames.associateWith { repo.typefaceFor(it) }
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
    repo:      Qcf4PageSource,
    vm:        QuranViewModel,
    onSearch:  () -> Unit,
) {
    val nav        = LocalNavController.current
    val context    = LocalContext.current
    val scope      = rememberCoroutineScope()
    val pagerState = rememberPagerState(
        initialPage = vm.savedPage.coerceIn(0, pageCount - 1)
    ) { pageCount }

    val pageCache = remember { androidx.compose.runtime.snapshots.SnapshotStateMap<Int, PageCacheEntry>() }

    var barsVisible by remember { mutableStateOf(true) }
    var selectedAya by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var isZoomed    by remember { mutableStateOf(false) }
    var zoomOffset  by remember { mutableStateOf(Offset.Zero) }
    var pageSize    by remember { mutableStateOf(IntSize.Zero) }
    val audioState  by AyaAudioManager.state.collectAsState()
    val showReaderSheet = remember { mutableStateOf(false) }
    val readers         = remember { DriveAudioRepository(context).readers().readers }

    SyncQcf4SystemBars(barsVisible)
    DisposableEffect(Unit) { onDispose { AyaAudioManager.stop() } }

    LaunchedEffect(pagerState.settledPage) {
        vm.savePage(pagerState.settledPage)
        selectedAya = null
        isZoomed    = false
        zoomOffset  = Offset.Zero
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
            state             = pagerState,
            modifier          = Modifier.fillMaxSize().onSizeChanged { pageSize = it },
            key               = { it },
            userScrollEnabled = !isZoomed,
        ) { idx ->
            val pageNum = idx + 1
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
                        isZoomed    = isZoomed,
                        zoomOffset  = zoomOffset,
                        riwaya      = repo.riwaya,
                        onTap       = { barsVisible = !barsVisible },
                        onDoubleTap = {
                            isZoomed   = !isZoomed
                            zoomOffset = Offset.Zero
                        },
                        onDrag      = { drag ->
                            val maxX = pageSize.width  * (ZOOM_SCALE - 1) / 2f
                            val maxY = pageSize.height * (ZOOM_SCALE - 1) / 2f
                            zoomOffset = Offset(
                                (zoomOffset.x + drag.x).coerceIn(-maxX, maxX),
                                (zoomOffset.y + drag.y).coerceIn(-maxY, maxY),
                            )
                        },
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
internal fun SessionQcf4Pager(startPage: Int, endPage: Int, repo: Qcf4PageSource) {
    val nav        = LocalNavController.current
    val context    = LocalContext.current
    val scope      = rememberCoroutineScope()
    val pageCount  = endPage - startPage + 1
    val pagerState = rememberPagerState(0) { pageCount }

    val pageCache       = remember { androidx.compose.runtime.snapshots.SnapshotStateMap<Int, PageCacheEntry>() }
    var barsVisible     by remember { mutableStateOf(true) }
    var selectedAya     by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var isZoomed        by remember { mutableStateOf(false) }
    var zoomOffset      by remember { mutableStateOf(Offset.Zero) }
    var pageSize        by remember { mutableStateOf(IntSize.Zero) }
    val audioState      by AyaAudioManager.state.collectAsState()
    val showReaderSheet = remember { mutableStateOf(false) }
    val readers         = remember { DriveAudioRepository(context).readers().readers }

    SyncQcf4SystemBars(barsVisible)
    DisposableEffect(Unit) { onDispose { AyaAudioManager.stop() } }

    LaunchedEffect(pagerState.settledPage) {
        selectedAya = null
        isZoomed    = false
        zoomOffset  = Offset.Zero
    }
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
        HorizontalPager(
            state             = pagerState,
            modifier          = Modifier.fillMaxSize().onSizeChanged { pageSize = it },
            key               = { it },
            userScrollEnabled = !isZoomed,
        ) { idx ->
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
                        isZoomed    = isZoomed,
                        zoomOffset  = zoomOffset,
                        riwaya      = repo.riwaya,
                        onTap       = { barsVisible = !barsVisible },
                        onDoubleTap = {
                            isZoomed   = !isZoomed
                            zoomOffset = Offset.Zero
                        },
                        onDrag      = { drag ->
                            val maxX = pageSize.width  * (ZOOM_SCALE - 1) / 2f
                            val maxY = pageSize.height * (ZOOM_SCALE - 1) / 2f
                            zoomOffset = Offset(
                                (zoomOffset.x + drag.x).coerceIn(-maxX, maxX),
                                (zoomOffset.y + drag.y).coerceIn(-maxY, maxY),
                            )
                        },
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