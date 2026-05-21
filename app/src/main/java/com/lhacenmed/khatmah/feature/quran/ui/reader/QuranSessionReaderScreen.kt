package com.lhacenmed.khatmah.feature.quran.ui.reader

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.BitmapFactory
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
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
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.lhacenmed.khatmah.core.nav.LocalNavController
import com.lhacenmed.khatmah.core.ui.components.OptionSelectBottomSheet
import com.lhacenmed.khatmah.core.ui.components.SheetOption
import com.lhacenmed.khatmah.feature.audio.AyaAudioManager
import com.lhacenmed.khatmah.feature.audio.AyaPlayerBar
import com.lhacenmed.khatmah.feature.audio.DriveAudioRepository
import com.lhacenmed.khatmah.feature.quran.data.WarshPageData
import com.lhacenmed.khatmah.feature.quran.data.WarshXmlRepository
import com.lhacenmed.khatmah.feature.quran.ui.components.ImageTopBar
import com.lhacenmed.khatmah.feature.quran.ui.components.QuranBottomBar
import com.lhacenmed.khatmah.feature.mushaf.data.MushafPrefs
import com.lhacenmed.khatmah.feature.mushaf.data.MushafPrint
import com.lhacenmed.khatmah.feature.quran.data.HafsQcf4Repository
import com.lhacenmed.khatmah.feature.quran.data.WarshQcf4Repository
import com.lhacenmed.khatmah.shared.util.AppPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private const val ANIM = 280

// ─── Entry ────────────────────────────────────────────────────────────────────

/**
 * Renders only the Quran pages belonging to a single Khatmah session
 * ([startPage]..[endPage], 1-based mushaf page numbers).
 *
 * Reader type follows [MushafPrefs.selected]:
 *   WarshImages → JPEG mushaf pages
 *   WarshSvg    → vector XML mushaf pages (with aya tap + audio)
 *   HafsText    → TodayTab should have shown a dialog; renders a fallback message here.
 */
@Composable
fun QuranSessionReaderScreen(startPage: Int, endPage: Int) {
    val context = LocalContext.current
    val print by MushafPrefs.selected.collectAsState()

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        when (print) {
            MushafPrint.WarshImages -> SessionImagePager(startPage, endPage)
            MushafPrint.WarshSvg    -> SessionXmlPager(startPage, endPage)
            MushafPrint.HafsQcf4    -> SessionQcf4Pager(startPage, endPage,
                remember { HafsQcf4Repository.get(context) })
            MushafPrint.WarshQcf4   -> SessionQcf4Pager(startPage, endPage,
                remember { WarshQcf4Repository.get(context) })
            MushafPrint.WarshText    -> {
                // Guard: TodayTab should have prevented this path.
                Box(Modifier.fillMaxSize(), Alignment.Center) {
                    Text("الرجاء اختيار نوع المصحف من الإعدادات")
                }
            }
        }
    }
}

// ─── Image pager ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SessionImagePager(startPage: Int, endPage: Int) {
    val nav        = LocalNavController.current
    val scope      = rememberCoroutineScope()
    val pageCount  = endPage - startPage + 1
    val pagerState = rememberPagerState(0) { pageCount }
    val isDark     = isSystemInDarkTheme()
    var barsVisible by remember { mutableStateOf(true) }

    SessionSyncSystemBars(barsVisible)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(if (isDark) Color.Black else Color.White)
            .pointerInput(Unit) { detectTapGestures { barsVisible = !barsVisible } },
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBarsIgnoringVisibility)
                .windowInsetsPadding(WindowInsets.navigationBarsIgnoringVisibility),
        ) {
            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { idx ->
                SessionMushhafPage(
                    pageNum  = startPage + idx,
                    isDark   = isDark,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        AnimatedVisibility(
            visible  = barsVisible,
            enter    = slideInVertically(tween(ANIM)) { -it } + fadeIn(tween(ANIM)),
            exit     = slideOutVertically(tween(ANIM)) { -it } + fadeOut(tween(ANIM / 2)),
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
            enter    = slideInVertically(tween(ANIM)) { it } + fadeIn(tween(ANIM)),
            exit     = slideOutVertically(tween(ANIM)) { it } + fadeOut(tween(ANIM / 2)),
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
        ) {
            QuranBottomBar(
                currentPage = pagerState.currentPage,
                totalPages  = pageCount,
                onJump      = { t -> scope.launch { pagerState.scrollToPage(t) } },
            )
        }
    }
}

// ─── XML pager ────────────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun SessionXmlPager(startPage: Int, endPage: Int) {
    val nav        = LocalNavController.current
    val context    = LocalContext.current
    val scope      = rememberCoroutineScope()
    val repo       = remember { WarshXmlRepository(context) }
    val pageCount  = endPage - startPage + 1
    val pagerState = rememberPagerState(0) { pageCount }

    var barsVisible by remember { mutableStateOf(true) }
    var selectedAya by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    val audioState  by AyaAudioManager.state.collectAsState()
    val isDark      = isSystemInDarkTheme()
    val primary     = MaterialTheme.colorScheme.primary
    var isZoomed    by remember { mutableStateOf(false) }
    val pageCache   = remember { mutableStateMapOf<Int, WarshPageData>() }
    val showReaderSheet = remember { mutableStateOf(false) }
    val readers         = remember { DriveAudioRepository(context).readers().readers }

    SessionSyncSystemBars(barsVisible)
    DisposableEffect(Unit) { onDispose { AyaAudioManager.stop() } }

    LaunchedEffect(pagerState.settledPage) { selectedAya = null; isZoomed = false }
    LaunchedEffect(audioState.suraNum, audioState.ayaNum) {
        if (audioState.active) selectedAya = audioState.suraNum to audioState.ayaNum
    }
    LaunchedEffect(audioState.active) { if (!audioState.active) selectedAya = null }

    // Pre-fetch current + neighbours
    LaunchedEffect(pagerState.settledPage) {
        val cur = startPage + pagerState.settledPage
        for (p in (cur - 1)..(cur + 1)) {
            if (p in startPage..endPage && p !in pageCache) {
                scope.launch { runCatching { pageCache[p] = repo.pageData(p) } }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(if (isDark) Color.Black else Color.White),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBarsIgnoringVisibility)
                .windowInsetsPadding(WindowInsets.navigationBarsIgnoringVisibility),
        ) {
            HorizontalPager(
                state             = pagerState,
                modifier          = Modifier.fillMaxSize(),
                key               = { it },
                userScrollEnabled = !isZoomed,
            ) { idx ->
                val pageNum  = startPage + idx
                val pageData = pageCache[pageNum]
                if (pageData == null) {
                    LaunchedEffect(pageNum) { runCatching { pageCache[pageNum] = repo.pageData(pageNum) } }
                    Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
                } else {
                    QuranXmlPage(
                        pageData       = pageData,
                        selectedAya    = selectedAya,
                        highlightColor = primary,
                        markerColor    = primary,
                        onBaresTap     = { barsVisible = !barsVisible },
                        onZoomChanged  = { isZoomed = it },
                        onAyaPress     = { sura, aya ->
                            if (selectedAya?.first == sura && selectedAya?.second == aya) {
                                selectedAya = null; AyaAudioManager.stop()
                            } else {
                                selectedAya = sura to aya
                                AyaAudioManager.play(context, sura, aya, "")
                            }
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }

        AnimatedVisibility(
            visible  = barsVisible,
            enter    = slideInVertically(tween(ANIM)) { -it } + fadeIn(tween(ANIM)),
            exit     = slideOutVertically(tween(ANIM)) { -it } + fadeOut(tween(ANIM / 2)),
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
            enter    = slideInVertically(tween(ANIM)) { it } + fadeIn(tween(ANIM)),
            exit     = slideOutVertically(tween(ANIM)) { it } + fadeOut(tween(ANIM / 2)),
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
            title     = "اختر القارئ",
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

// ─── Shared helpers ───────────────────────────────────────────────────────────

@Composable
private fun SessionSyncSystemBars(visible: Boolean) {
    val view = LocalView.current
    val ctrl = remember(view) {
        view.context.sessionActivity()?.window?.let { w ->
            WindowCompat.getInsetsController(w, view)
        }
    }
    DisposableEffect(ctrl, visible) {
        ctrl?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (visible) ctrl?.show(WindowInsetsCompat.Type.systemBars())
        else         ctrl?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose   { ctrl?.show(WindowInsetsCompat.Type.systemBars()) }
    }
}

private tailrec fun Context.sessionActivity(): Activity? = when (this) {
    is Activity       -> this
    is ContextWrapper -> baseContext.sessionActivity()
    else              -> null
}

@Composable
private fun SessionMushhafPage(pageNum: Int, isDark: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val bmp by produceState<ImageBitmap?>(null, pageNum) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                BitmapFactory.decodeFile(
                    File(context.filesDir, "warsh-images/%03d.jpg".format(pageNum)).absolutePath
                )?.asImageBitmap()
            }.getOrNull()
        }
    }
    if (bmp != null) {
        Image(
            bitmap             = bmp!!,
            contentDescription = null,
            contentScale       = ContentScale.FillBounds,
            colorFilter        = if (isDark) ColorFilter.colorMatrix(ColorMatrix(floatArrayOf(
                -1f, 0f, 0f, 0f, 255f,
                0f,-1f, 0f, 0f, 255f,
                0f, 0f,-1f, 0f, 255f,
                0f, 0f, 0f, 1f,   0f,
            ))) else null,
            modifier = modifier,
        )
    } else {
        Box(modifier, Alignment.Center) { CircularProgressIndicator() }
    }
}