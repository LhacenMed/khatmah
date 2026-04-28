package com.lhacenmed.khatmah.ui.page.quran

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.widget.Toast
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.lhacenmed.khatmah.R
import com.lhacenmed.khatmah.ui.common.Route
import com.lhacenmed.khatmah.ui.nav.LocalNavController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import androidx.compose.ui.res.stringResource

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
 *
 * Observes [KEY_JUMP_SURA] / [KEY_JUMP_AYA] on the current back-stack entry so
 * [QuranSearchScreen] can write a result there before popping back.
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

/**
 * Root layout: pager fills the screen; bars float above it.
 *
 * Tap anywhere (including aya text) toggles bar visibility.
 * Long-pressing an aya highlights it and starts audio playback.
 * The bottom bar is a [Column] of [AyaPlayerBar] (animated) + [QuranBottomBar].
 *
 * [selectedAya] is kept in sync with [AyaAudioManager.state] so that when
 * playback auto-advances to the next aya the highlight follows automatically.
 */
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
    val audioNotAvailableMsg = stringResource(R.string.audio_not_available)
    val pagerState = rememberPagerState(
        initialPage = vm.savedPage.coerceIn(0, pages.lastIndex),
    ) { pages.size }

    var barsVisible by remember { mutableStateOf(true) }
    var selectedAya by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    val audioState  by AyaAudioManager.state.collectAsState()

    SyncReaderSystemBars(barsVisible)

    LaunchedEffect(pagerState.settledPage) { vm.savePage(pagerState.settledPage) }

    // Clear aya selection when the user swipes to a different page.
    LaunchedEffect(pagerState.settledPage) { selectedAya = null }

    // Sync highlight with auto-advance: when the manager moves to the next aya
    // (without the user long-pressing), update selectedAya to match.
    LaunchedEffect(audioState.suraNum, audioState.ayaNum) {
        if (audioState.active) {
            selectedAya = audioState.suraNum to audioState.ayaNum
        }
    }

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
            // Tap on non-aya areas (padding/empty space) toggles bars.
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
                    // Aya tap propagates up to toggle bars, same as tapping empty space.
                    onTap          = { barsVisible = !barsVisible },
                    onAyaLongPress = { suraNum, ayaNum ->
                        // Toggle off if the same aya is long-pressed again.
                        if (selectedAya?.first == suraNum && selectedAya?.second == ayaNum) {
                            selectedAya = null
                            AyaAudioManager.stop()
                            return@PageContent
                        }
                        selectedAya = suraNum to ayaNum

                        // Resolve the bare surah name (without "سورة ") from the page
                        // segments — use the SuraHeader whose num matches the pressed aya,
                        // falling back to the page's dominant suraName.
                        val surahName = pages[idx].segments
                            .filterIsInstance<QuranSegment.SuraHeader>()
                            .firstOrNull { it.num == suraNum }
                            ?.name
                            ?: pages[idx].suraName

                        val ok = AyaAudioManager.play(context, suraNum, ayaNum, surahName)
                        if (!ok) {
                            Toast.makeText(
                                context,
                                audioNotAvailableMsg,
                                Toast.LENGTH_SHORT,
                            ).show()
                            selectedAya = null
                        }
                    },
                )
            }
        }

        // ── Top bar ───────────────────────────────────────────────────────────
        AnimatedVisibility(
            visible  = barsVisible,
            enter    = slideInVertically(tween(ANIM_MS)) { -it } + fadeIn(tween(ANIM_MS)),
            exit     = slideOutVertically(tween(ANIM_MS)) { -it } + fadeOut(tween(ANIM_MS / 2)),
            modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth(),
        ) {
            QuranTopBar(page = curPage, onBack = { nav.popBackStack() }, onSearch = onSearch)
        }

        // ── Bottom bar: player (animated) stacked above page slider ───────────
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
                        state    = audioState,
                        onToggle = { AyaAudioManager.togglePlayPause() },
                        onClose  = {
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
}

// ── Image pager shell ─────────────────────────────────────────────────────────

/**
 * Full-screen mushaf image reader (604 pages from assets/quran/).
 *
 * Identical chrome to [QuranPager] (tap-to-toggle bars, slider, system bars sync).
 * Each page renders assets/quran/{pageNum}.jpg filling the full screen.
 * Jump-to-aya from search and index tab works via the same [QuranViewModel.pendingJump]
 * mechanism — the VM resolves (suraNum, ayaNum) → mushaf page index via page_aya.
 * In dark theme the image colors are inverted so white paper becomes dark.
 */
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

// ── Top bar (text mode) ───────────────────────────────────────────────────────

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
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = null,
                        tint               = MaterialTheme.colorScheme.onSurface,
                    )
                }
            },
            actions = {
                IconButton(onClick = onSearch) {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = null,
                        tint               = MaterialTheme.colorScheme.onSurface,
                    )
                }
            },
        )
    }
}

// ── Top bar (image mode) ──────────────────────────────────────────────────────

/** Minimal top bar for image mode — shows the mushaf page number. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ImageTopBar(pageNum: Int, onBack: () -> Unit, onSearch: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shadowElevation = 4.dp) {
        CenterAlignedTopAppBar(
            modifier = Modifier.statusBarsPadding(),
            colors   = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent),
            title    = {
                Text(
                    text  = "صفحة ${toArNums(pageNum)}",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = null,
                        tint               = MaterialTheme.colorScheme.onSurface,
                    )
                }
            },
            actions = {
                IconButton(onClick = onSearch) {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = null,
                        tint               = MaterialTheme.colorScheme.onSurface,
                    )
                }
            },
        )
    }
}

// ── Audio player bar ──────────────────────────────────────────────────────────

/**
 * Compact player bar that sits directly above the page slider.
 * Layout: thin progress line at top · play/pause · reader name + aya number · close.
 */
@Composable
private fun AyaPlayerBar(
    state:    AyaAudioState,
    onToggle: () -> Unit,
    onClose:  () -> Unit,
) {
    Surface(
        color           = MaterialTheme.colorScheme.secondaryContainer,
        shadowElevation = 4.dp,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            LinearProgressIndicator(
                progress   = { state.progress },
                modifier   = Modifier.fillMaxWidth(),
                color      = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
            )
            Row(
                modifier          = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onToggle) {
                    Icon(
                        imageVector        = if (state.isPlaying) Icons.Default.Pause
                        else Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint               = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text     = state.readerName,
                        style    = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color    = MaterialTheme.colorScheme.onSecondaryContainer,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text  = "آية ${toArNums(state.ayaNum)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f),
                    )
                }
                IconButton(onClick = onClose) {
                    Icon(
                        imageVector        = Icons.Default.Close,
                        contentDescription = null,
                        tint               = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
        }
    }
}

// ── Bottom bar (page slider) ──────────────────────────────────────────────────

/**
 * Page-jump slider. In the forced RTL context it runs right→left, matching
 * Quran book direction: right = Al-Fatiha, left = An-Nas.
 */
@Composable
private fun QuranBottomBar(currentPage: Int, totalPages: Int, onJump: (Int) -> Unit) {
    var sliderVal  by remember { mutableFloatStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }

    LaunchedEffect(currentPage) {
        if (!isDragging) {
            sliderVal = if (totalPages > 1) currentPage.toFloat() / (totalPages - 1) else 0f
        }
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