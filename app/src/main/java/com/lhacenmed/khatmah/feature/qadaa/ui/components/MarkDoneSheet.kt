package com.lhacenmed.khatmah.feature.qadaa.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lhacenmed.khatmah.R
import com.lhacenmed.khatmah.feature.qadaa.data.FastDebt
import com.lhacenmed.khatmah.feature.qadaa.data.Prayer
import com.lhacenmed.khatmah.feature.qadaa.data.PrayerDebt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MarkDoneSheet(
    prayers: List<PrayerDebt>,
    fasts: List<FastDebt>,
    initialTab: Int = 0,               // 0=Prayers, 1=Fasts
    preselectedFastId: Long? = null,
    onDismiss: () -> Unit,
    onMarkPrayers: (counts: Map<Prayer, Int>, fullDay: Boolean) -> Unit,
    onMarkFasts: (count: Int, fastId: Long) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var tab by rememberSaveable { mutableIntStateOf(initialTab) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.padding(bottom = 24.dp)) {
            Text(
                stringResource(R.string.qadaa_mark_as_made_up),
                style    = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            Spacer(Modifier.height(12.dp))
            val tabPrayers = stringResource(R.string.qadaa_section_prayers)
            val tabFasts   = stringResource(R.string.qadaa_section_fasts)
            TabRow(selectedTabIndex = tab, divider = {}) {
                listOf(tabPrayers, tabFasts).forEachIndexed { i, title ->
                    Tab(selected = tab == i, onClick = { tab = i }) {
                        Text(title, modifier = Modifier.padding(vertical = 10.dp))
                    }
                }
            }
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))
            when (tab) {
                0 -> PrayersTab(
                    prayers = prayers,
                    onMark  = { counts, fullDay -> onMarkPrayers(counts, fullDay); onDismiss() },
                )
                1 -> FastsTab(
                    fasts             = fasts,
                    preselectedFastId = preselectedFastId,
                    onMark            = { count, id -> onMarkFasts(count, id); onDismiss() },
                )
            }
        }
    }
}

// ── Prayers tab ───────────────────────────────────────────────────────────────

@Composable
private fun PrayersTab(prayers: List<PrayerDebt>, onMark: (Map<Prayer, Int>, fullDay: Boolean) -> Unit) {
    val remainingMap = remember(prayers) { prayers.associate { it.prayer to it.remaining } }
    val counts = remember(remainingMap) {
        mutableStateMapOf<Prayer, Int>().also { m ->
            Prayer.entries.forEach { p -> m[p] = 1.coerceAtMost(remainingMap[p] ?: 0) }
        }
    }
    val total       = counts.values.sum()
    val canDoFullDay = Prayer.entries.all { (remainingMap[it] ?: 0) >= 1 }

    Column(
        modifier = Modifier
            .padding(horizontal = 20.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        OutlinedButton(
            onClick  = { onMark(Prayer.entries.associateWith { 1 }, true) },
            enabled  = canDoFullDay,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape    = RoundedCornerShape(50),
        ) { Text(stringResource(R.string.qadaa_full_day_btn)) }

        Spacer(Modifier.height(12.dp))
        Card(shape = RoundedCornerShape(16.dp)) {
            Prayer.entries.forEachIndexed { i, prayer ->
                if (i > 0) HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                Row(
                    modifier          = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val maxRem = remainingMap[prayer] ?: 0
                    Text(
                        stringResource(prayer.nameRes),
                        modifier = Modifier.weight(1f),
                        style    = MaterialTheme.typography.bodyLarge,
                    )
                    CounterButton("-", enabled = (counts[prayer] ?: 0) > 0) {
                        counts[prayer] = ((counts[prayer] ?: 0) - 1).coerceAtLeast(0)
                    }
                    Text(
                        "${counts[prayer] ?: 0}",
                        modifier   = Modifier.width(40.dp),
                        textAlign  = TextAlign.Center,
                        style      = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    CounterButton("+", enabled = (counts[prayer] ?: 0) < maxRem) {
                        counts[prayer] = ((counts[prayer] ?: 0) + 1).coerceAtMost(maxRem)
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        if (total > 0) {
            Text(
                if (total == 1) stringResource(R.string.qadaa_making_up_prayer, total)
                else stringResource(R.string.qadaa_making_up_prayers, total),
                style      = MaterialTheme.typography.bodyMedium,
                color      = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(Modifier.height(12.dp))
        Button(
            onClick  = { onMark(counts.filter { it.value > 0 }, false) },
            enabled  = total > 0,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape    = RoundedCornerShape(50),
        ) { Text(stringResource(R.string.qadaa_confirm)) }
        Spacer(Modifier.height(8.dp))
    }
}

// ── Fasts tab ─────────────────────────────────────────────────────────────────

@Composable
private fun FastsTab(
    fasts: List<FastDebt>,
    preselectedFastId: Long?,
    onMark: (count: Int, fastId: Long) -> Unit,
) {
    var count by rememberSaveable { mutableIntStateOf(1) }
    var selectedId by rememberSaveable {
        mutableStateOf(preselectedFastId ?: fasts.firstOrNull()?.id)
    }
    var showNote by rememberSaveable { mutableStateOf(false) }

    val selectedFast = fasts.find { it.id == selectedId }
    val maxCount     = selectedFast?.remaining ?: 1

    LaunchedEffect(maxCount) {
        if (count > maxCount) count = maxCount.coerceAtLeast(1)
    }

    Column(
        modifier = Modifier
            .padding(horizontal = 20.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        if (fasts.isEmpty()) {
            Text(
                stringResource(R.string.qadaa_no_fast_groups),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            return@Column
        }

        Text(stringResource(R.string.qadaa_how_many_days), style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            CounterButton("-", enabled = count > 1) { count-- }
            Text("$count", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            CounterButton("+", enabled = count < maxCount) { count++ }
            Text(
                if (count == 1) stringResource(R.string.qadaa_day_unit)
                else stringResource(R.string.qadaa_days_unit),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.qadaa_from_which_group), style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(8.dp))
        // Chips for each fast group
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            fasts.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { fast ->
                        FilterChip(
                            selected = selectedId == fast.id,
                            onClick  = { selectedId = fast.id },
                            label    = { Text("${fast.label} (${fast.remaining})") },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }

        if (!showNote) {
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = { showNote = true }) { Text(stringResource(R.string.qadaa_add_note)) }
        }

        Spacer(Modifier.height(16.dp))
        Button(
            onClick  = { selectedId?.let { onMark(count, it) } },
            enabled  = selectedId != null,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape    = RoundedCornerShape(50),
        ) { Text(stringResource(R.string.qadaa_confirm)) }
        Spacer(Modifier.height(8.dp))
    }
}