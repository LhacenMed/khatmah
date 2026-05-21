package com.lhacenmed.khatmah.feature.quran.ui.reader

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lhacenmed.khatmah.R
import com.lhacenmed.khatmah.feature.quran.ui.components.ImageTopBar
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.lhacenmed.khatmah.core.nav.LocalNavController
import com.lhacenmed.khatmah.core.nav.Route
import com.lhacenmed.khatmah.core.ui.components.OptionSelectBottomSheet
import com.lhacenmed.khatmah.core.ui.components.SheetOption
import com.lhacenmed.khatmah.feature.audio.AyaAudioManager
import com.lhacenmed.khatmah.feature.audio.AyaPlayerBar
import com.lhacenmed.khatmah.feature.audio.DriveAudioRepository
import com.lhacenmed.khatmah.feature.mushaf.data.MushafPrint
import com.lhacenmed.khatmah.feature.quran.data.HafsQcf4Repository
import com.lhacenmed.khatmah.feature.quran.data.Qcf4PageSource
import com.lhacenmed.khatmah.feature.quran.data.WarshPageData
import com.lhacenmed.khatmah.feature.quran.data.WarshQcf4Repository
import com.lhacenmed.khatmah.feature.quran.data.WarshXmlRepository
import com.lhacenmed.khatmah.feature.quran.ui.QuranViewModel
import com.lhacenmed.khatmah.feature.quran.ui.components.QuranBottomBar
import com.lhacenmed.khatmah.feature.quran.ui.components.QuranTopBar
import com.lhacenmed.khatmah.shared.util.AppPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private const val ANIM_MS = 280

// Invert RGB channels, preserve alpha.
private val InvertMatrix = ColorMatrix(floatArrayOf(
    -1f,  0f,  0f, 0f, 255f,
    0f, -1f,  0f, 0f, 255f,
    0f,  0f, -1f, 0f, 255f,
    0f,  0f,  0f, 1f,   0f,
))

// Keys shared with QuranSearchScreen — written there, read here via SavedStateHandle.
internal const val KEY_JUMP_SURA = "jumpSura"
internal const val KEY_JUMP_AYA  = "jumpAya"

// ── Entry point ───────────────────────────────────────────────────────────────

/**
 * Full-screen Quran reader.
 *
 * Forces RTL layout so the pager, slider, and back arrow all match Arabic
 * reading convention without per-component direction overrides.
 */
@Composable
fun QuranReaderScreen() {
    val vm: QuranViewModel = viewModel()
    val nav   = LocalNavController.current
    val state by vm.state.collectAsState()
    val context = LocalContext.current

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

    // Stop audio when leaving the reader entirely.
    DisposableEffect(Unit) {
        onDispose { AyaAudioManager.stop() }
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        when (val s = state) {
            is QuranViewModel.State.Loading    -> LoadingBox()
            is QuranViewModel.State.Ready      -> QuranPager(
                pages    = s.pages,
                vm       = vm,
                onSearch = { nav.navigate(Route.QURAN_SEARCH) },
            )
            is QuranViewModel.State.ImageReady -> QuranImagePager(
                pageCount = s.pageCount,
                vm        = vm,
                onSearch  = { nav.navigate(Route.QURAN_SEARCH) },
            )
            is QuranViewModel.State.XmlReady   -> QuranXmlPager(
                pageCount = s.pageCount,
                vm        = vm,
                onSearch  = { nav.navigate(Route.QURAN_SEARCH) },
            )
            is QuranViewModel.State.Qcf4Ready -> {
                val repo = remember(s.print) {
                    when (s.print) {
                        MushafPrint.WarshQcf4 -> WarshQcf4Repository.get(context) as Qcf4PageSource
                        else                  -> HafsQcf4Repository.get(context)  as Qcf4PageSource
                    }
                }
                QuranQcf4Pager(
                    pageCount = s.pageCount,
                    repo      = repo,
                    vm        = vm,
                    onSearch  = { nav.navigate(Route.QURAN_SEARCH) },
                )
            }
        }
    }
}

// ── System bars sync ──────────────────────────────────────────────────────────

@Composable
private fun SyncReaderSystemBars(visible: Boolean) {
    val view = LocalView.current
    val controller = remember(view) {
        view.context.findActivity()?.window?.let { window ->
            WindowCompat.getInsetsController(window, view)
        }
    }

    DisposableEffect(controller, visible) {
        controller?.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (visible) controller?.show(WindowInsetsCompat.Type.systemBars())
        else         controller?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose   { controller?.show(WindowInsetsCompat.Type.systemBars()) }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity       -> this
    is ContextWrapper -> baseContext.findActivity()
    else              -> null
}

// ── Text pager shell ──────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun QuranPager(
    pages:    List<QuranPageData>,
    vm:       QuranViewModel,
    onSearch: () -> Unit,
) {
    val nav        = LocalNavController.current
    val context    = LocalContext.current
    val scope      = rememberCoroutineScope()
    val pagerState = rememberPagerState(
        initialPage = vm.savedPage.coerceIn(0, pages.lastIndex),
    ) { pages.size }

    var barsVisible by remember { mutableStateOf(true) }
    var selectedAya by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    val audioState  by AyaAudioManager.state.collectAsState()

    SyncReaderSystemBars(barsVisible)

    LaunchedEffect(pagerState.settledPage) { vm.savePage(pagerState.settledPage) }
    LaunchedEffect(pagerState.settledPage) { selectedAya = null }

    LaunchedEffect(audioState.suraNum, audioState.ayaNum) {
        if (audioState.active) {
            selectedAya = audioState.suraNum to audioState.ayaNum
        }
    }

    LaunchedEffect(audioState.active) {
        if (!audioState.active) selectedAya = null
    }

    val pendingJump by vm.pendingJump.collectAsState()
    LaunchedEffect(pendingJump) {
        pendingJump?.let { page ->
            pagerState.scrollToPage(page)
            vm.consumeJump()
        }
    }

    val curPage = pages[pagerState.settledPage]
    val readers = remember { DriveAudioRepository(context).readers().readers }
    val showReaderSheet = remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .pointerInput(Unit) { detectTapGestures { barsVisible = !barsVisible } },
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBarsIgnoringVisibility)
                .windowInsetsPadding(WindowInsets.navigationBarsIgnoringVisibility),
        ) {
            HorizontalPager(
                state         = pagerState,
                reverseLayout = false,
                modifier      = Modifier.fillMaxSize(),
                key           = { pages[it].pageNum },
            ) { idx ->
                PageContent(
                    page           = pages[idx],
                    selectedAya    = selectedAya,
                    onTap          = { barsVisible = !barsVisible },
                    onAyaLongPress = { suraNum, ayaNum ->
                        if (selectedAya?.first == suraNum && selectedAya?.second == ayaNum) {
                            selectedAya = null
                            AyaAudioManager.stop()
                            return@PageContent
                        }
                        selectedAya = suraNum to ayaNum

                        val surahName = pages[idx].segments
                            .filterIsInstance<QuranSegment.SuraHeader>()
                            .firstOrNull { it.num == suraNum }
                            ?.name ?: pages[idx].suraName

                        AyaAudioManager.play(context, suraNum, ayaNum, surahName)
                    },
                )
            }
        }

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
                        onClose       = {
                            selectedAya = null
                            AyaAudioManager.stop()
                        },
                    )
                }
                QuranBottomBar(
                    currentPage = pagerState.currentPage,
                    totalPages  = pages.size,
                    onJump      = { target -> scope.launch { pagerState.scrollToPage(target) } },
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
            onSelect  = { readerId ->
                AppPrefs.setAudioReaderId(context, readerId)
                showReaderSheet.value = false
                if (audioState.active) {
                    val surahName = pages[pagerState.currentPage].segments
                        .filterIsInstance<QuranSegment.SuraHeader>()
                        .firstOrNull { it.num == audioState.suraNum }
                        ?.name ?: pages[pagerState.currentPage].suraName

                    AyaAudioManager.play(context, audioState.suraNum, audioState.ayaNum, surahName)
                }
            }
        )
    }
}

// ── Mushaf image cache ────────────────────────────────────────────────────────

/** On-disk path for [pageNum] inside the WarshImageRepository cache. */
private fun mushafFile(context: Context, pageNum: Int): File =
    File(context.filesDir, "warsh-images/%03d.jpg".format(pageNum))

@Composable
private fun MushhafPage(pageNum: Int, isDark: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val bmp by produceState<ImageBitmap?>(null, pageNum) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                BitmapFactory.decodeFile(mushafFile(context, pageNum).absolutePath)
                    ?.asImageBitmap()
            }.getOrNull()
        }
    }
    if (bmp != null) {
        Image(
            bitmap             = bmp!!,
            contentDescription = null,
            contentScale       = ContentScale.Fit,
            colorFilter        = if (isDark) ColorFilter.colorMatrix(InvertMatrix) else null,
            modifier           = modifier,
        )
    } else {
        Box(modifier, Alignment.Center) { CircularProgressIndicator() }
    }
}

// ── Image pager shell ─────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuranImagePager(
    pageCount: Int,
    vm:        QuranViewModel,
    onSearch:  () -> Unit,
) {
    val nav        = LocalNavController.current
    val scope      = rememberCoroutineScope()
    val pagerState = rememberPagerState(
        initialPage = vm.savedPage.coerceIn(0, pageCount - 1),
    ) { pageCount }

    var barsVisible by remember { mutableStateOf(true) }
    val isDark      = isSystemInDarkTheme()

    SyncReaderSystemBars(barsVisible)

    LaunchedEffect(pagerState.settledPage) { vm.savePage(pagerState.settledPage) }

    val pendingJump by vm.pendingJump.collectAsState()
    LaunchedEffect(pendingJump) {
        pendingJump?.let { page ->
            pagerState.scrollToPage(page)
            vm.consumeJump()
        }
    }

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
            HorizontalPager(
                state         = pagerState,
                reverseLayout = false,
                modifier      = Modifier.fillMaxSize(),
            ) { idx ->
                MushhafPage(
                    pageNum  = idx + 1,
                    isDark   = isDark,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        AnimatedVisibility(
            visible  = barsVisible,
            enter    = slideInVertically(tween(ANIM_MS)) { -it } + fadeIn(tween(ANIM_MS)),
            exit     = slideOutVertically(tween(ANIM_MS)) { -it } + fadeOut(tween(ANIM_MS / 2)),
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
            enter    = slideInVertically(tween(ANIM_MS)) { it } + fadeIn(tween(ANIM_MS)),
            exit     = slideOutVertically(tween(ANIM_MS)) { it } + fadeOut(tween(ANIM_MS / 2)),
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
        ) {
            QuranBottomBar(
                currentPage = pagerState.currentPage,
                totalPages  = pageCount,
                onJump      = { target -> scope.launch { pagerState.scrollToPage(target) } },
            )
        }
    }
}

// ── Xml pager shell ───────────────────────────────────────────────────────────

/**
 * Full-screen pager for the Warsh Xml mushaf.
 *
 * Each page is rendered in its own [QuranXmlPage] using an [android.graphics.Picture]
 * (vector display list) — no fixed-resolution Bitmap, no WebView. The picture is drawn
 * via [android.graphics.Canvas.drawPicture] scaled to fill the composable at the device's
 * native pixel density, giving true vector sharpness on all screens.
 *
 * Adjacent pages are pre-fetched via [WarshXmlRepository.prefetch] to keep swiping smooth.
 *
 * Aya tap → audio: tapping any polygon highlights that aya and starts playback
 * via [AyaAudioManager], exactly like the text reader's long-press.
 * Tapping the same aya again stops playback and clears the highlight.
 */
@SuppressLint("ConfigurationScreenWidthHeight")
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun QuranXmlPager(
    pageCount: Int,
    vm:        QuranViewModel,
    onSearch:  () -> Unit,
) {
    val nav        = LocalNavController.current
    val context    = LocalContext.current
    val scope      = rememberCoroutineScope()
    val repo       = remember { WarshXmlRepository(context) }
    val pagerState = rememberPagerState(
        initialPage = vm.savedPage.coerceIn(0, pageCount - 1),
    ) { pageCount }

    var barsVisible by remember { mutableStateOf(true) }
    var selectedAya by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    val audioState  by AyaAudioManager.state.collectAsState()
    val isDark      = isSystemInDarkTheme()
    val primary     = MaterialTheme.colorScheme.primary

    // Cache of page data keyed by 1-based page number.
    val pageCache = remember { mutableStateMapOf<Int, WarshPageData>() }

    var isZoomed by remember { mutableStateOf(false) }

    SyncReaderSystemBars(barsVisible)

    LaunchedEffect(pagerState.settledPage) {
        vm.savePage(pagerState.settledPage)
        selectedAya = null
        isZoomed = false
    }

    // Sync highlight with audio auto-advance.
    LaunchedEffect(audioState.suraNum, audioState.ayaNum) {
        if (audioState.active) selectedAya = audioState.suraNum to audioState.ayaNum
    }
    LaunchedEffect(audioState.active) {
        if (!audioState.active) selectedAya = null
    }

    val pendingJump by vm.pendingJump.collectAsState()
    LaunchedEffect(pendingJump) {
        pendingJump?.let { page ->
            pagerState.scrollToPage(page)
            vm.consumeJump()
        }
    }

    // Load current page and pre-fetch neighbours.
    // Picture rendering is resolution-independent — no screen width needed.
    LaunchedEffect(pagerState.settledPage) {
        val cur = pagerState.settledPage + 1   // 1-based page number
        for (p in (cur - 1)..(cur + 1)) {
            if (p in 1..pageCount && p !in pageCache) {
                scope.launch {
                    try {
                        pageCache[p] = repo.pageData(p)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }
    }

    val showReaderSheet = remember { mutableStateOf(false) }
    val readers         = remember { DriveAudioRepository(context).readers().readers }

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
                state    = pagerState,
                modifier = Modifier.fillMaxSize(),
                // Each page gets a stable key so UI instances are reused correctly.
                key      = { it },
                userScrollEnabled = !isZoomed,
            ) { idx ->
                val pageNum  = idx + 1   // 1-based
                val pageData = pageCache[pageNum]

                if (pageData == null) {
                    // Trigger load and show spinner while waiting.
                    LaunchedEffect(pageNum) {
                        try {
                            pageCache[pageNum] = repo.pageData(pageNum)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    QuranXmlPage(
                        pageData       = pageData,
                        selectedAya    = selectedAya,
                        highlightColor = primary,
                        markerColor    = primary,
                        onBaresTap     = { barsVisible = !barsVisible },
                        onZoomChanged  = { isZoomed = it },
                        onAyaPress     = { surahNum, ayahNum ->
                            if (selectedAya?.first == surahNum && selectedAya?.second == ayahNum) {
                                // Second tap on same aya → deselect and stop.
                                selectedAya = null
                                AyaAudioManager.stop()
                            } else {
                                selectedAya = surahNum to ayahNum
                                AyaAudioManager.play(context, surahNum, ayahNum, "")
                            }
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }

        AnimatedVisibility(
            visible  = barsVisible,
            enter    = slideInVertically(tween(ANIM_MS)) { -it } + fadeIn(tween(ANIM_MS)),
            exit     = slideOutVertically(tween(ANIM_MS)) { -it } + fadeOut(tween(ANIM_MS / 2)),
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
            enter    = slideInVertically(tween(ANIM_MS)) { it } + fadeIn(tween(ANIM_MS)),
            exit     = slideOutVertically(tween(ANIM_MS)) { it } + fadeOut(tween(ANIM_MS / 2)),
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
                        onClose       = {
                            selectedAya = null
                            AyaAudioManager.stop()
                        },
                    )
                }
                QuranBottomBar(
                    currentPage = pagerState.currentPage,
                    totalPages  = pageCount,
                    onJump      = { target -> scope.launch { pagerState.scrollToPage(target) } },
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
            onSelect  = { readerId ->
                AppPrefs.setAudioReaderId(context, readerId)
                showReaderSheet.value = false
                if (audioState.active) {
                    AyaAudioManager.play(
                        context,
                        audioState.suraNum,
                        audioState.ayaNum,
                        "",
                    )
                }
            },
        )
    }
}

@Composable
private fun LoadingBox() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}