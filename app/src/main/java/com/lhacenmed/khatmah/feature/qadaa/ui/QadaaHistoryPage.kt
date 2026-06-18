package com.lhacenmed.khatmah.feature.qadaa.ui

import android.app.Application
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lhacenmed.khatmah.R
import com.lhacenmed.khatmah.core.nav.LocalNavigator
import com.lhacenmed.khatmah.core.ui.components.AppTopBar
import com.lhacenmed.khatmah.feature.qadaa.data.*
import kotlinx.coroutines.flow.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

// ── ViewModel ─────────────────────────────────────────────────────────────────

enum class LogFilter(@StringRes val labelRes: Int) {
    ALL(R.string.qadaa_filter_all),
    PRAYERS(R.string.qadaa_filter_prayers),
    FASTS(R.string.qadaa_filter_fasts),
}

data class HistoryUiState(
    val grouped: Map<LocalDate, List<QadaaLogItem>> = emptyMap(),
    val filter: LogFilter = LogFilter.ALL,
    val totalPrayersMadeUp: Int = 0,
    val totalFastsMadeUp: Int = 0,
    val longestStreak: Int = 0, // TODO: streak
    val currentStreak: Int = 0, // TODO: streak
)

@RequiresApi(Build.VERSION_CODES.O)
internal class QadaaHistoryViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = QadaaRepository(app)
    private val _filter = MutableStateFlow(LogFilter.ALL)

    val uiState: StateFlow<HistoryUiState> = combine(
        repo.logs(),
        _filter,
        repo.streaks(QadaaPrefs.dailyGoal),
    ) { logs, filter, streaks ->
        val filtered = when (filter) {
            LogFilter.ALL     -> logs
            LogFilter.PRAYERS -> logs.filter { it.type == "PRAYER" }
            LogFilter.FASTS   -> logs.filter { it.type == "FAST" }
        }
        HistoryUiState(
            grouped            = filtered.groupBy { it.completedAt.toLocalDate() },
            filter             = filter,
            totalPrayersMadeUp = logs.filter { it.type == "PRAYER" }.sumOf { it.count },
            totalFastsMadeUp   = logs.filter { it.type == "FAST" }.sumOf { it.count },
            currentStreak      = streaks.first,
            longestStreak      = streaks.second,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUiState())

    fun setFilter(f: LogFilter) { _filter.value = f }

    private fun Long.toLocalDate(): LocalDate =
        Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).toLocalDate()
}

// ── Screen ────────────────────────────────────────────────────────────────────

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun QadaaHistoryScreen() {
    val nav   = LocalNavigator.current
    val vm: QadaaHistoryViewModel = viewModel()
    val state by vm.uiState.collectAsState()
    val dateFmt = remember { DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy") }
    val timeFmt = remember { DateTimeFormatter.ofPattern("h:mm a") }

    Scaffold(
        topBar = {
            AppTopBar(
                title      = stringResource(R.string.qadaa_history),
                isTopLevel = false,
                onBack     = { nav.back() },
            )
        },
    ) { padding ->
        LazyColumn(
            contentPadding      = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier            = Modifier.fillMaxSize().padding(padding),
        ) {
            // Filter chips
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LogFilter.entries.forEach { f ->
                        FilterChip(
                            selected = state.filter == f,
                            onClick  = { vm.setFilter(f) },
                            label    = { Text(stringResource(f.labelRes)) },
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            if (state.grouped.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.qadaa_history_empty),
                        style    = MaterialTheme.typography.bodyMedium,
                        color    = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 24.dp),
                    )
                }
            }

            state.grouped.forEach { (date, items) ->
                item(key = date.toString()) {
                    Text(
                        date.format(dateFmt),
                        style    = MaterialTheme.typography.labelMedium,
                        color    = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 12.dp, bottom = 6.dp),
                    )
                }
                items(items, key = { "log_${it.id}" }) { item ->
                    LogItemCard(item = item, timeFmt = timeFmt)
                }
            }

            // All time summary
            item {
                Spacer(Modifier.height(16.dp))
                AllTimeSummaryCard(state)
            }
        }
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@Composable
private fun LogItemCard(item: QadaaLogItem, timeFmt: DateTimeFormatter) {
    val time = remember(item.completedAt) {
        Instant.ofEpochMilli(item.completedAt)
            .atZone(ZoneId.systemDefault())
            .format(timeFmt)
    }
    val isPrayer = item.type == "PRAYER"
    val description = when {
        item.type == "FAST" -> {
            val daysStr = if (item.count == 1) stringResource(R.string.qadaa_log_fast_day, item.count)
            else stringResource(R.string.qadaa_log_fast_days, item.count)
            "$daysStr${item.fastLabel?.let { " — $it" } ?: ""}"
        }
        item.prayer != null -> stringResource(
            R.string.qadaa_log_prayer, item.count, stringResource(item.prayer.nameRes),
        )
        else -> stringResource(R.string.qadaa_log_full_day, item.count)
    }

    Card(
        shape    = RoundedCornerShape(12.dp),
        colors   = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
    ) {
        Row(
            modifier              = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(if (isPrayer) "🕌" else "🌙", style = MaterialTheme.typography.titleMedium)
            Column(modifier = Modifier.weight(1f)) {
                Text(description, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(time, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    item.note?.let {
                        Text("· $it", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Surface(
                color = if (isPrayer) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.tertiaryContainer,
                shape = RoundedCornerShape(50),
            ) {
                Text(
                    "+${item.count}",
                    modifier   = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    style      = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color      = if (isPrayer) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
        }
    }
}

@Composable
private fun AllTimeSummaryCard(state: HistoryUiState) {
    Card(shape = RoundedCornerShape(20.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                stringResource(R.string.qadaa_all_time_summary),
                style      = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SummaryStatCard(
                    "${state.totalPrayersMadeUp}",
                    stringResource(R.string.qadaa_stat_prayers),
                    MaterialTheme.colorScheme.primaryContainer,
                    Modifier.weight(1f),
                )
                SummaryStatCard(
                    "${state.totalFastsMadeUp}",
                    stringResource(R.string.qadaa_stat_fasts),
                    MaterialTheme.colorScheme.tertiaryContainer,
                    Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SummaryStatCard(
                    "🔥 ${state.longestStreak}",
                    stringResource(R.string.qadaa_stat_longest),
                    MaterialTheme.colorScheme.surfaceContainerHigh,
                    Modifier.weight(1f),
                )
                SummaryStatCard(
                    "🕐 ${state.currentStreak}",
                    stringResource(R.string.qadaa_stat_current),
                    MaterialTheme.colorScheme.surfaceContainerHigh,
                    Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun SummaryStatCard(value: String, label: String, color: Color, modifier: Modifier) {
    Surface(color = color, shape = RoundedCornerShape(16.dp), modifier = modifier) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
