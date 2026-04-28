package com.lhacenmed.khatmah.ui.page.quran

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
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.lhacenmed.khatmah.R
import com.lhacenmed.khatmah.data.audio.DriveAudioRepository
import com.lhacenmed.khatmah.data.prefs.AppPrefs
import com.lhacenmed.khatmah.ui.common.Route
import com.lhacenmed.khatmah.ui.component.OptionSelectBottomSheet
import com.lhacenmed.khatmah.ui.component.SheetOption
import com.lhacenmed.khatmah.ui.nav.LocalNavController
import com.lhacenmed.khatmah.ui.page.quran.component.AyaPlayerBar
import com.lhacenmed.khatmah.ui.page.quran.component.ImageTopBar
import com.lhacenmed.khatmah.ui.page.quran.component.QuranBottomBar
import com.lhacenmed.khatmah.ui.page.quran.component.QuranTopBar
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

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
                state    = pagerState,
                modifier = Modifier.fillMaxSize(),
            ) { idx ->
                AsyncImage(
                    model              = "file:///android_asset/quran/${idx + 1}.jpg",
                    contentDescription = null,
                    contentScale       = ContentScale.FillBounds,
                    colorFilter        = if (isDark) ColorFilter.colorMatrix(InvertMatrix) else null,
                    modifier           = Modifier.fillMaxSize(),
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

@Composable
private fun LoadingBox() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}
