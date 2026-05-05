package com.lhacenmed.khatmah.feature.today

import android.content.Context
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lhacenmed.khatmah.R
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

// ── Arabic labels — move to strings.xml when localizing ───────────────────────

private const val S_FROM         = "من قوله تعالى"
private const val S_READ         = "اقرأ الورد"
private const val S_MARK_READ    = "أتممت القراءة"
private const val S_KHATMAH      = "الختمة الحالية"
private const val S_PREV         = "الأوراد السابقة"
private const val S_NEXT         = "الأوراد القادمة"
private const val S_NO_KHATMAH   = "لا توجد ختمة نشطة"
private const val S_CREATE       = "إنشاء ختمة"
private const val S_ALL_READ     = "أحسنت! أتممت جميع الأوراد"
private const val S_DL_TITLE     = "تحميل المصحف"
private const val S_DL_MSG       = "يجب اختيار نوع المصحف من إعدادات القارئ أولاً"
private const val S_SETTINGS     = "الإعدادات"
private const val S_CANCEL       = "إلغاء"
private const val S_PAGE         = "صفحة"
private const val S_BASMALA      = "بِسْمِ اِ۬للَّهِ اِ۬لرَّحْمَٰنِ اِ۬لرَّحِيمِ"

private val JUZ_ORDINALS = arrayOf(
    "الأول","الثاني","الثالث","الرابع","الخامس",
    "السادس","السابع","الثامن","التاسع","العاشر",
    "الحادي عشر","الثاني عشر","الثالث عشر","الرابع عشر","الخامس عشر",
    "السادس عشر","السابع عشر","الثامن عشر","التاسع عشر","العشرون",
    "الحادي والعشرون","الثاني والعشرون","الثالث والعشرون","الرابع والعشرون","الخامس والعشرون",
    "السادس والعشرون","السابع والعشرون","الثامن والعشرون","التاسع والعشرون","الثلاثون",
)
private fun juzLabel(n: Int) = "الجزء " + JUZ_ORDINALS.getOrElse(n - 1) { n.toString() }

// ── ViewModel ─────────────────────────────────────────────────────────────────

@OptIn(ExperimentalCoroutinesApi::class)
class TodayViewModel(private val repo: KhatmahRepository) : ViewModel() {

    data class SessionUi(
        val entity:        KhatmahSessionEntity,
        val startSuraName: String,
        val endSuraName:   String,
        val juzNum:        Int,
    )

    sealed class UiState {
        object Loading   : UiState()
        object NoKhatmah : UiState()
        object AllRead   : UiState()
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
                            UiState.AllRead
                        } else {
                            val meta = repo.sessionMeta(session.startSura, session.endSura)
                            UiState.Active(
                                session   = SessionUi(session, meta.startSuraName, meta.endSuraName, meta.juzNum),
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
            title   = { Text(S_DL_TITLE) },
            text    = { Text(S_DL_MSG) },
            confirmButton = {
                TextButton(onClick = { showDlDialog = false; nav.navigate(Route.THEME_SETTINGS) }) {
                    Text(S_SETTINGS)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDlDialog = false }) { Text(S_CANCEL) }
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
        // Animated session card — slides between sessions, crossfades for other transitions.
        AnimatedContent(
            targetState = state,
            transitionSpec = {
                if (initialState is TodayViewModel.UiState.Active &&
                    targetState  is TodayViewModel.UiState.Active
                ) {
                    (slideInVertically(tween(300)) { it } + fadeIn(tween(220))) togetherWith
                            (slideOutVertically(tween(300)) { -it } + fadeOut(tween(220)))
                } else {
                    fadeIn(tween(300)) togetherWith fadeOut(tween(300))
                }
            },
            label = "session_card",
        ) { s ->
            when (s) {
                is TodayViewModel.UiState.Loading   -> LoadingCard()
                is TodayViewModel.UiState.NoKhatmah -> NoKhatmahCard { nav.navigate(Route.NEW_KHATMAH) }
                is TodayViewModel.UiState.AllRead   -> AllReadCard()
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

        // Khatmah progress strip — only shown while there is an active session.
        if (state is TodayViewModel.UiState.Active) {
            val s = state as TodayViewModel.UiState.Active
            KhatmahStats(readCount = s.readCount, totalCount = s.khatmah.totalDays)
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
                    text  = juzLabel(sess.juzNum),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text  = S_FROM,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(20.dp))

            // Decorative Basmala
            Text(
                text      = S_BASMALA,
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

            Spacer(Modifier.height(20.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))

            // Start row
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text       = "$S_PAGE ${e.startPage}",
                    style      = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text  = "سورة ${sess.startSuraName} - آية ${e.startAya}",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            Spacer(Modifier.height(4.dp))

            // End row
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text       = "$S_PAGE ${e.endPage}",
                    style      = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text  = "إلى سورة ${sess.endSuraName} - آية ${e.endAya}",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            Spacer(Modifier.height(16.dp))

            // Action buttons
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick  = onMarkRead,
                    modifier = Modifier.weight(1f),
                    colors   = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFFFCA28),
                        contentColor   = Color(0xFF1B1B1B),
                    ),
                ) {
                    Text("‹ $S_MARK_READ", fontWeight = FontWeight.SemiBold)
                }
                Button(
                    onClick  = onRead,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(S_READ, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

// ── Khatmah progress strip ────────────────────────────────────────────────────

@Composable
private fun KhatmahStats(readCount: Int, totalCount: Int) {
    val progress  = if (totalCount > 0) readCount.toFloat() / totalCount else 0f
    val remaining = (totalCount - readCount).coerceAtLeast(0)

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text       = S_KHATMAH,
            style      = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier   = Modifier.fillMaxWidth(),
            textAlign  = TextAlign.End,
        )
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("$S_NEXT : $remaining", style = MaterialTheme.typography.bodySmall)
            Text("$S_PREV : $readCount", style = MaterialTheme.typography.bodySmall)
        }
    }
}

// ── Placeholder cards ─────────────────────────────────────────────────────────

@Composable
private fun LoadingCard() {
    Box(
        modifier         = Modifier.fillMaxWidth().height(220.dp),
        contentAlignment = Alignment.Center,
    ) { CircularProgressIndicator() }
}

@Composable
private fun NoKhatmahCard(onCreate: () -> Unit) {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier            = Modifier.padding(24.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(S_NO_KHATMAH, style = MaterialTheme.typography.bodyLarge)
            Button(onClick = onCreate) { Text(S_CREATE) }
        }
    }
}

@Composable
private fun AllReadCard() {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier         = Modifier.padding(32.dp).fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text      = S_ALL_READ,
                style     = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color     = MaterialTheme.colorScheme.primary,
            )
        }
    }
}