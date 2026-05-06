package com.lhacenmed.khatmah.feature.today

import android.content.Context
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lhacenmed.khatmah.R
import com.lhacenmed.khatmah.core.motion.initialOffset
import com.lhacenmed.khatmah.core.motion.materialSharedAxisX
import com.lhacenmed.khatmah.core.motion.materialSharedAxisZ
import com.lhacenmed.khatmah.core.nav.LocalNavController
import com.lhacenmed.khatmah.core.nav.NavScreen
import com.lhacenmed.khatmah.core.nav.Route
import com.lhacenmed.khatmah.core.ui.theme.WarshFamily
import com.lhacenmed.khatmah.feature.khatmah.data.KhatmahEntity
import com.lhacenmed.khatmah.feature.khatmah.data.KhatmahRepository
import com.lhacenmed.khatmah.feature.khatmah.data.KhatmahSessionEntity
import com.lhacenmed.khatmah.shared.util.AppPrefs
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

// ── ViewModel ─────────────────────────────────────────────────────────────────

@OptIn(ExperimentalCoroutinesApi::class)
class TodayViewModel(private val repo: KhatmahRepository) : ViewModel() {

    data class SessionUi(
        val entity:        KhatmahSessionEntity,
        val startSuraName: String,
        val endSuraName:   String,
        val juzNum:        Int,
        val firstAyaText:  String,
    )

    sealed class UiState {
        object Loading   : UiState()
        object NoKhatmah : UiState()
        data class AllRead(val totalDays: Int) : UiState()
        data class Active(
            val session:   SessionUi,
            val khatmah:   KhatmahEntity,
            val readCount: Int,
        ) : UiState()
    }

    private val _state = MutableStateFlow<UiState>(UiState.Loading)
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repo.activeKhatmahFlow()
                .flatMapLatest { khatmah ->
                    if (khatmah == null) return@flatMapLatest flowOf(UiState.NoKhatmah)
                    combine(
                        repo.currentSession(khatmah.id),
                        repo.readCount(khatmah.id),
                    ) { session, readCount ->
                        if (session == null) {
                            UiState.AllRead(totalDays = khatmah.totalDays)
                        } else {
                            val meta = repo.sessionMeta(
                                session.startSura,
                                session.startAya,
                                session.endSura,
                            )
                            UiState.Active(
                                session   = SessionUi(
                                    entity        = session,
                                    startSuraName = meta.startSuraName,
                                    endSuraName   = meta.endSuraName,
                                    juzNum        = meta.juzNum,
                                    firstAyaText  = meta.firstAyaText,
                                ),
                                khatmah   = khatmah,
                                readCount = readCount,
                            )
                        }
                    }
                }
                .collect { _state.value = it }
        }
    }

    fun markRead(id: Long) {
        viewModelScope.launch { repo.markSessionRead(id) }
    }

    class Factory(private val ctx: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            TodayViewModel(KhatmahRepository(ctx)) as T
    }
}

/** Returns at most [maxWords] space-separated words from [text], appending "…" if truncated. */
private fun truncateAya(text: String, maxWords: Int = 4): String {
    val words = text.trim().split(' ')
    return if (words.size <= maxWords) text
    else words.take(maxWords).joinToString(" ") + "…"
}

// ── Shimmer ───────────────────────────────────────────────────────────────────

@Composable
private fun shimmerBrush(): Brush {
    val color = MaterialTheme.colorScheme.onSurface
    val transition = rememberInfiniteTransition(label = "shimmer")
    val offset by transition.animateFloat(
        initialValue  = -300f,
        targetValue   = 1000f,
        animationSpec = infiniteRepeatable(
            animation  = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmer_offset",
    )
    return Brush.linearGradient(
        colors = listOf(
            color.copy(alpha = 0.08f),
            color.copy(alpha = 0.18f),
            color.copy(alpha = 0.08f),
        ),
        start = Offset(offset, 0f),
        end   = Offset(offset + 300f, 0f),
    )
}

/** Single shimmer block with rounded corners. */
@Composable
private fun SkeletonBox(modifier: Modifier) {
    Box(modifier.clip(RoundedCornerShape(4.dp)).background(shimmerBrush()))
}

// ── NavScreen entry ───────────────────────────────────────────────────────────

val TodayTab = NavScreen(
    route    = Route.TODAY,
    iconRes  = R.drawable.ic_book,
    labelRes = R.string.today,
) { padding -> TodayScreen(padding) }

// ── Screen ────────────────────────────────────────────────────────────────────

@Composable
private fun TodayScreen(padding: PaddingValues) {
    val context     = LocalContext.current
    val nav         = LocalNavController.current
    val vm: TodayViewModel = viewModel(factory = TodayViewModel.Factory(context))
    val state       by vm.state.collectAsState()
    val readerStyle by AppPrefs.readerStyle.collectAsState()
    var showDlDialog by remember { mutableStateOf(false) }

    if (showDlDialog) {
        AlertDialog(
            onDismissRequest = { showDlDialog = false },
            title   = { Text(stringResource(R.string.today_dl_title)) },
            text    = { Text(stringResource(R.string.today_dl_msg)) },
            confirmButton = {
                TextButton(onClick = { showDlDialog = false; nav.navigate(Route.THEME_SETTINGS) }) {
                    Text(stringResource(R.string.today_settings))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDlDialog = false }) {
                    Text(stringResource(R.string.today_cancel))
                }
            },
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // contentKey on session id ensures Compose always detects Active→Active changes.
        AnimatedContent(
            targetState   = state,
            modifier      = Modifier.clipToBounds(),
            contentKey    = { s ->
                when (s) {
                    is TodayViewModel.UiState.Active -> s.session.entity.id
                    else                             -> s::class
                }
            },
            transitionSpec = {
                when {
                    initialState is TodayViewModel.UiState.Loading ||
                            targetState  is TodayViewModel.UiState.Loading ->
                        fadeIn(animationSpec = tween(300)) togetherWith
                                fadeOut(animationSpec = tween(300))

                    initialState is TodayViewModel.UiState.Active &&
                            targetState  is TodayViewModel.UiState.Active ->
                        materialSharedAxisX(
                            initialOffsetX = { (it * initialOffset).toInt() },
                            targetOffsetX  = { -(it * initialOffset).toInt() },
                        )

                    else ->
                        materialSharedAxisZ(forward = true)
                }
            },
            label = "session_card",
        ) { s ->
            when (s) {
                is TodayViewModel.UiState.Loading   -> SkeletonCard()
                is TodayViewModel.UiState.NoKhatmah -> NoKhatmahCard { nav.navigate(Route.NEW_KHATMAH) }
                is TodayViewModel.UiState.AllRead   -> AllReadCard(
                    onDua        = { /* TODO: navigate to dua */ },
                    onNewKhatmah = { nav.navigate(Route.NEW_KHATMAH) },
                )
                is TodayViewModel.UiState.Active    -> SessionCard(
                    state      = s,
                    onMarkRead = { vm.markRead(s.session.entity.id) },
                    onRead     = {
                        if (readerStyle == AppPrefs.ReaderStyle.TEXT) {
                            showDlDialog = true
                        } else {
                            nav.navigate(
                                Route.quranSessionReader(
                                    s.session.entity.startPage,
                                    s.session.entity.endPage,
                                )
                            )
                        }
                    },
                )
            }
        }

        // Bottom strip — full progress when all read, real stats when active, skeleton when loading.
        AnimatedContent(
            targetState   = state,
            contentKey    = { s -> s::class },
            transitionSpec = {
                fadeIn(animationSpec = tween(300)) togetherWith
                        fadeOut(animationSpec = tween(300))
            },
            label = "stats_strip",
        ) { s ->
            when (s) {
                is TodayViewModel.UiState.Active  ->
                    KhatmahStats(readCount = s.readCount, totalCount = s.khatmah.totalDays)
                is TodayViewModel.UiState.AllRead ->
                    KhatmahStats(readCount = s.totalDays, totalCount = s.totalDays)
                is TodayViewModel.UiState.Loading -> SkeletonStats()
                else                              -> Unit
            }
        }
    }
}

// ── Session card ──────────────────────────────────────────────────────────────

@Composable
private fun SessionCard(
    state:      TodayViewModel.UiState.Active,
    onMarkRead: () -> Unit,
    onRead:     () -> Unit,
) {
    val sess = state.session
    val e    = sess.entity

    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {

            // Header row
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text  = stringResource(R.string.today_starts_from),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text  = stringResource(R.string.today_juz, sess.juzNum),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(20.dp))

            // First aya of the session, truncated to max 4 words.
            if (sess.firstAyaText.isNotBlank()) {
                Text(
                    text      = truncateAya(sess.firstAyaText),
                    style     = TextStyle(
                        fontFamily    = WarshFamily,
                        fontSize      = 26.sp,
                        lineHeight    = 42.sp,
                        textDirection = TextDirection.Rtl,
                    ),
                    textAlign = TextAlign.Center,
                    color     = MaterialTheme.colorScheme.primary,
                    modifier  = Modifier.fillMaxWidth(),
                )
            }

            Spacer(Modifier.height(20.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))

            // Start row
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text  = stringResource(R.string.today_sura_aya, sess.startSuraName, e.startAya),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text       = stringResource(R.string.today_page, e.startPage),
                    style      = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            Spacer(Modifier.height(4.dp))

            // End row
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text  = stringResource(R.string.today_to_sura_aya, sess.endSuraName, e.endAya),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text       = stringResource(R.string.today_page, e.endPage),
                    style      = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            Spacer(Modifier.height(16.dp))

            // Action buttons
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick  = onRead,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.today_start_reading), fontWeight = FontWeight.SemiBold)
                }
                Button(
                    onClick  = onMarkRead,
                    modifier = Modifier.weight(1f),
                    colors   = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFFFCA28),
                        contentColor   = Color(0xFF1B1B1B),
                    ),
                ) {
                    Text(stringResource(R.string.today_mark_read), fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

// ── Skeleton placeholders ─────────────────────────────────────────────────────

/**
 * Mirrors [SessionCard] exactly — static labels and buttons shown real,
 * dynamic fields (juz, aya text, sura names, page numbers) replaced with
 * shimmer blocks sized to match their expected rendered dimensions.
 */
@Composable
private fun SkeletonCard() {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {

            // Header row
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically,
            ) {
                Text(
                    text  = stringResource(R.string.today_starts_from),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                ) {
                    Text(
                        text  = stringResource(R.string.today_juz_prefix),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    SkeletonBox(Modifier.size(width = 10.dp, height = 14.dp))
                }
            }

            Spacer(Modifier.height(20.dp))

            // Invisible text drives exact height; skeleton overlays it.
            Box(
                modifier         = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text   = "بِسْمِ اِ۬للَّهِ اِ۬لرَّحْمَٰنِ اِ۬لرَّحِيمِ",
                    style  = TextStyle(
                        fontFamily = WarshFamily,
                        fontSize   = 26.sp,
                        lineHeight = 42.sp,
                    ),
                    modifier = Modifier.alpha(0f),
                )
                SkeletonBox(Modifier.size(width = 220.dp, height = 25.dp))
            }

            Spacer(Modifier.height(20.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))

            // Start row
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                ) {
                    SkeletonBox(Modifier.size(width = 80.dp, height = 14.dp))
                    Text(" - ", style = MaterialTheme.typography.bodyMedium)
                    SkeletonBox(Modifier.size(width = 32.dp, height = 14.dp))
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                ) {
                    Text(
                        text       = stringResource(R.string.today_page_prefix),
                        style      = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    SkeletonBox(Modifier.size(width = 24.dp, height = 14.dp))
                }
            }

            Spacer(Modifier.height(4.dp))

            // End row
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                ) {
                    SkeletonBox(Modifier.size(width = 80.dp, height = 14.dp))
                    Text(" - ", style = MaterialTheme.typography.bodyMedium)
                    SkeletonBox(Modifier.size(width = 32.dp, height = 14.dp))
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                ) {
                    Text(
                        text       = stringResource(R.string.today_page_prefix),
                        style      = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    SkeletonBox(Modifier.size(width = 24.dp, height = 14.dp))
                }
            }

            Spacer(Modifier.height(16.dp))

            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick  = {},
                    enabled  = false,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.today_start_reading), fontWeight = FontWeight.SemiBold)
                }
                Button(
                    onClick  = {},
                    enabled  = false,
                    modifier = Modifier.weight(1f),
                    colors   = ButtonDefaults.buttonColors(
                        disabledContainerColor = Color(0xFFFFCA28).copy(alpha = 0.38f),
                        disabledContentColor   = Color(0xFF1B1B1B).copy(alpha = 0.38f),
                    ),
                ) {
                    Text(stringResource(R.string.today_mark_read), fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

/**
 * Mirrors [KhatmahStats] — static title shown real,
 * progress bar and count labels replaced with shimmer blocks.
 */
@Composable
private fun SkeletonStats() {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text       = stringResource(R.string.today_khatmah_title),
            style      = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier   = Modifier.fillMaxWidth(),
            textAlign  = TextAlign.Start,
        )
        // LinearProgressIndicator track height is 4dp
        SkeletonBox(Modifier.fillMaxWidth().height(4.dp))
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment     = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.today_previous_prefix), style = MaterialTheme.typography.bodySmall)
                SkeletonBox(Modifier.size(width = 20.dp, height = 12.dp))
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment     = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.today_upcoming_prefix), style = MaterialTheme.typography.bodySmall)
                SkeletonBox(Modifier.size(width = 20.dp, height = 12.dp))
            }
        }
    }
}

// ── Khatmah progress strip ────────────────────────────────────────────────────

@Composable
private fun KhatmahStats(readCount: Int, totalCount: Int) {
    val rawProgress  = if (totalCount > 0) readCount.toFloat() / totalCount else 0f
    val animProgress by animateFloatAsState(
        targetValue   = rawProgress,
        animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
        label         = "khatmah_progress",
    )
    val remaining = (totalCount - readCount).coerceAtLeast(0)

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text       = stringResource(R.string.today_khatmah_title),
            style      = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier   = Modifier.fillMaxWidth(),
            textAlign  = TextAlign.Start,
        )
        LinearProgressIndicator(
            progress = { animProgress },
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.today_previous_prefix), style = MaterialTheme.typography.bodySmall)
                Text("$readCount",  style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.today_upcoming_prefix), style = MaterialTheme.typography.bodySmall)
                Text("$remaining",  style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

// ── Placeholder cards ─────────────────────────────────────────────────────────

@Composable
private fun NoKhatmahCard(onCreate: () -> Unit) {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Box {
            Column(modifier = Modifier.padding(16.dp)) {

                // Invisible header row — keeps exact dimensions
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically,
                ) {
                    Text(text = "ض", style = MaterialTheme.typography.labelLarge, modifier = Modifier.alpha(0f))
                    Text(text = "ض", style = MaterialTheme.typography.labelLarge, modifier = Modifier.alpha(0f))
                }

                Spacer(Modifier.height(20.dp))

                // Invisible aya block — keeps exact height via font metrics
                Box(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text     = "ض",
                        style    = TextStyle(fontFamily = WarshFamily, fontSize = 26.sp, lineHeight = 42.sp),
                        modifier = Modifier.alpha(0f),
                    )
                }

                Spacer(Modifier.height(20.dp))

                // Invisible divider — keeps exact height
                HorizontalDivider(modifier = Modifier.alpha(0f))

                Spacer(Modifier.height(12.dp))

                // Invisible info rows
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(text = "ض", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.alpha(0f))
                    Text(text = "ض", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.alpha(0f))
                }

                Spacer(Modifier.height(4.dp))

                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(text = "ض", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.alpha(0f))
                    Text(text = "ض", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.alpha(0f))
                }

                Spacer(Modifier.height(16.dp))

                // Action buttons
                Button(
                    onClick  = onCreate,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.today_create), fontWeight = FontWeight.SemiBold)
                }
            }

            // Absolutely centered overlay — matches card body size, no layout impact
            Box(
                modifier         = Modifier.matchParentSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text      = stringResource(R.string.today_no_khatmah),
                    textAlign = TextAlign.Center,
                    color     = MaterialTheme.colorScheme.primary,
                    maxLines  = 3,
                    overflow  = TextOverflow.Ellipsis,
                    modifier  = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 50.dp),
                )
            }
        }
    }
}

@Composable
private fun AllReadCard(
    onDua:        () -> Unit,
    onNewKhatmah: () -> Unit,
) {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Box {
            Column(modifier = Modifier.padding(16.dp)) {

                // Invisible header row — keeps exact dimensions
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically,
                ) {
                    Text(text = "ض", style = MaterialTheme.typography.labelLarge, modifier = Modifier.alpha(0f))
                    Text(text = "ض", style = MaterialTheme.typography.labelLarge, modifier = Modifier.alpha(0f))
                }

                Spacer(Modifier.height(20.dp))

                // Invisible aya block — keeps exact height via font metrics
                Box(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text     = "ض",
                        style    = TextStyle(fontFamily = WarshFamily, fontSize = 26.sp, lineHeight = 42.sp),
                        modifier = Modifier.alpha(0f),
                    )
                }

                Spacer(Modifier.height(20.dp))

                // Invisible divider — keeps exact height
                HorizontalDivider(modifier = Modifier.alpha(0f))

                Spacer(Modifier.height(12.dp))

                // Invisible info rows
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(text = "ض", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.alpha(0f))
                    Text(text = "ض", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.alpha(0f))
                }

                Spacer(Modifier.height(4.dp))

                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(text = "ض", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.alpha(0f))
                    Text(text = "ض", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.alpha(0f))
                }

                Spacer(Modifier.height(16.dp))

                // Action buttons
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick  = onDua,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.today_dua_khatm), fontWeight = FontWeight.SemiBold)
                    }
                    Button(
                        onClick  = onNewKhatmah,
                        modifier = Modifier.weight(1f),
                        colors   = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFFFCA28),
                            contentColor   = Color(0xFF1B1B1B),
                        ),
                    ) {
                        Text(stringResource(R.string.today_new_khatmah), fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            // Absolutely centered overlay — matches card body size, no layout impact
            Box(
                modifier         = Modifier.matchParentSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text      = stringResource(R.string.today_khatmah_completed),
                    textAlign = TextAlign.Center,
                    color     = MaterialTheme.colorScheme.primary,
                    maxLines  = 3,
                    overflow  = TextOverflow.Ellipsis,
                    modifier  = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 50.dp), // on the Text, not the Box — clips instead of shrinking centering area
                )
            }
        }
    }
}