package com.lhacenmed.khatmah.feature.qadaa.ui.components

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lhacenmed.khatmah.R
import com.lhacenmed.khatmah.feature.qadaa.data.EstimationMethod
import com.lhacenmed.khatmah.feature.qadaa.data.Prayer
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun AddPrayersSheet(
    onDismiss: () -> Unit,
    onAdd: (counts: Map<Prayer, Int>) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState       = sheetState,
    ) {
        var tab by rememberSaveable { mutableIntStateOf(0) }
        val tabs = listOf(
            stringResource(R.string.qadaa_tab_custom),
            stringResource(R.string.qadaa_tab_date_range),
            stringResource(R.string.qadaa_tab_by_year),
            stringResource(R.string.qadaa_tab_calendar),
        )

        Column(modifier = Modifier.padding(bottom = 24.dp)) {
            Text(
                stringResource(R.string.qadaa_add_prayers_title),
                style    = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            Spacer(Modifier.height(12.dp))
            ScrollableTabRow(
                selectedTabIndex = tab,
                edgePadding      = 20.dp,
                divider          = {},
            ) {
                tabs.forEachIndexed { i, title ->
                    Tab(selected = tab == i, onClick = { tab = i }) {
                        Text(title, modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp))
                    }
                }
            }
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))

            when (tab) {
                0 -> CustomTab(onAdd = onAdd, onDismiss = onDismiss)
                1 -> DateRangeTab(onAdd = onAdd, onDismiss = onDismiss)
                2 -> ByYearTab(onAdd = onAdd, onDismiss = onDismiss)
                3 -> CalendarTab(onAdd = onAdd, onDismiss = onDismiss)
            }
        }
    }
}

// ── Custom tab ────────────────────────────────────────────────────────────────

@Composable
private fun CustomTab(onAdd: (Map<Prayer, Int>) -> Unit, onDismiss: () -> Unit) {
    val counts = remember { mutableStateMapOf<Prayer, Int>().also { m -> Prayer.entries.forEach { m[it] = 0 } } }
    var showNote by rememberSaveable { mutableStateOf(false) }
    var note by rememberSaveable { mutableStateOf("") }
    val total = counts.values.sum()

    Column(modifier = Modifier.padding(horizontal = 20.dp).verticalScroll(rememberScrollState())) {
        Text(
            stringResource(R.string.qadaa_custom_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        Card(shape = RoundedCornerShape(16.dp)) {
            Prayer.entries.forEachIndexed { i, prayer ->
                if (i > 0) HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                PrayerCountRow(
                    label       = stringResource(prayer.nameRes),
                    count       = counts[prayer] ?: 0,
                    onDecrement = { counts[prayer] = ((counts[prayer] ?: 0) - 1).coerceAtLeast(0) },
                    onIncrement = { counts[prayer] = (counts[prayer] ?: 0) + 1 },
                    active      = (counts[prayer] ?: 0) > 0,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        if (showNote) {
            OutlinedTextField(
                value         = note,
                onValueChange = { note = it },
                placeholder   = { Text(stringResource(R.string.qadaa_note_hint)) },
                modifier      = Modifier.fillMaxWidth(),
                shape         = RoundedCornerShape(12.dp),
                singleLine    = true,
            )
        } else {
            TextButton(onClick = { showNote = true }) { Text(stringResource(R.string.qadaa_add_note)) }
        }
        Spacer(Modifier.height(16.dp))
        AddButton(total = total, label = stringResource(R.string.qadaa_add_to_qadaa)) {
            onAdd(counts.filter { it.value > 0 })
            onDismiss()
        }
        Spacer(Modifier.height(8.dp))
    }
}

// ── Date range tab ────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@RequiresApi(Build.VERSION_CODES.O)
@Composable
private fun DateRangeTab(onAdd: (Map<Prayer, Int>) -> Unit, onDismiss: () -> Unit) {
    var fromMs by rememberSaveable { mutableStateOf<Long?>(null) }
    var untilMs by rememberSaveable { mutableStateOf<Long?>(null) }
    var showFromPicker by remember { mutableStateOf(false) }
    var showUntilPicker by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(Prayer.entries.toSet()) }

    val fromDate = fromMs?.let { LocalDate.ofEpochDay(it / 86_400_000L) }
    val untilDate = untilMs?.let { LocalDate.ofEpochDay(it / 86_400_000L) }
    val dayCount = if (fromDate != null && untilDate != null && !untilDate.isBefore(fromDate))
        ChronoUnit.DAYS.between(fromDate, untilDate).toInt() + 1 else 0
    val total = dayCount * selected.size
    val dateFmt = DateTimeFormatter.ofPattern("MMM d, yyyy")

    val labelFrom  = stringResource(R.string.qadaa_from)
    val labelUntil = stringResource(R.string.qadaa_until)
    val labelSelect = stringResource(R.string.qadaa_date_select)
    val labelAdd   = stringResource(R.string.qadaa_add_to_qadaa)

    Column(modifier = Modifier.padding(horizontal = 20.dp).verticalScroll(rememberScrollState())) {
        Text(
            stringResource(R.string.qadaa_date_range_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            DateField(labelFrom,  fromDate?.format(dateFmt),  Modifier.weight(1f)) { showFromPicker  = true }
            DateField(labelUntil, untilDate?.format(dateFmt), Modifier.weight(1f)) { showUntilPicker = true }
        }
        Spacer(Modifier.height(16.dp))
        PrayerChips(selected = selected, onToggle = { prayer ->
            selected = if (prayer in selected) selected - prayer else selected + prayer
        })
        if (total > 0) {
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(R.string.qadaa_that_is_n_prayers, total),
                style      = MaterialTheme.typography.bodyMedium,
                color      = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(Modifier.height(16.dp))
        AddButton(total = total, label = labelAdd) {
            onAdd(selected.associateWith { dayCount })
            onDismiss()
        }
        Spacer(Modifier.height(8.dp))
    }

    if (showFromPicker) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = fromMs ?: System.currentTimeMillis(),
            selectableDates           = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= System.currentTimeMillis()
            },
        )
        DatePickerDialog(
            onDismissRequest = { showFromPicker = false },
            confirmButton    = {
                TextButton(onClick = { fromMs = state.selectedDateMillis; showFromPicker = false }) {
                    Text(stringResource(R.string.dialog_confirm))
                }
            },
        ) { DatePicker(state = state) }
    }
    if (showUntilPicker) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = untilMs ?: System.currentTimeMillis(),
            selectableDates           = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= System.currentTimeMillis()
            },
        )
        DatePickerDialog(
            onDismissRequest = { showUntilPicker = false },
            confirmButton    = {
                TextButton(onClick = { untilMs = state.selectedDateMillis; showUntilPicker = false }) {
                    Text(stringResource(R.string.dialog_confirm))
                }
            },
        ) { DatePicker(state = state) }
    }
}

// ── By year tab ───────────────────────────────────────────────────────────────

@Composable
private fun ByYearTab(onAdd: (Map<Prayer, Int>) -> Unit, onDismiss: () -> Unit) {
    var years by rememberSaveable { mutableIntStateOf(5) }
    var selected by remember { mutableStateOf(Prayer.entries.toSet()) }
    var method by rememberSaveable { mutableStateOf(EstimationMethod.CONSERVATIVE) }

    val daysPerYear      = if (method == EstimationMethod.CONSERVATIVE) 350 else 365
    val countPerPrayer   = years * daysPerYear
    val total            = countPerPrayer * selected.size

    // Pre-build localized prayer names (Prayer.entries.map is inline — safe in composable scope)
    val prayerNames: List<String> = Prayer.entries.map { stringResource(it.nameRes) }

    Column(modifier = Modifier.padding(horizontal = 20.dp).verticalScroll(rememberScrollState())) {
        Text(
            stringResource(R.string.qadaa_by_year_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.qadaa_duration), style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            CounterButton("-", enabled = years > 1) { years-- }
            Text("$years", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            CounterButton("+") { years++ }
            Text(
                stringResource(R.string.qadaa_years_unit),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.qadaa_prayers_to_include), style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(8.dp))
        PrayerChips(selected = selected, onToggle = { prayer ->
            selected = if (prayer in selected) selected - prayer else selected + prayer
        })
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.qadaa_estimation_method), style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = method == EstimationMethod.CONSERVATIVE,
                onClick  = { method = EstimationMethod.CONSERVATIVE },
                label    = { Text(stringResource(R.string.qadaa_method_conservative)) },
            )
            FilterChip(
                selected = method == EstimationMethod.FULL,
                onClick  = { method = EstimationMethod.FULL },
                label    = { Text(stringResource(R.string.qadaa_method_full)) },
            )
        }
        Text(
            if (method == EstimationMethod.CONSERVATIVE) stringResource(R.string.qadaa_conservative_desc)
            else stringResource(R.string.qadaa_full_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (total > 0) {
            Spacer(Modifier.height(16.dp))
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                shape  = RoundedCornerShape(16.dp),
            ) {
                Column(
                    modifier            = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        "%,d".format(total),
                        style      = MaterialTheme.typography.headlineLarge,
                        color      = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        stringResource(R.string.qadaa_estimated_prayers_label),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    if (selected.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            // joinToString is not inline — use pre-built prayerNames list
                            selected.joinToString(" · ") { prayer ->
                                "${prayerNames[prayer.ordinal]} %,d".format(countPerPrayer)
                            },
                            style     = MaterialTheme.typography.bodySmall,
                            color     = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        AddButton(total = total, label = stringResource(R.string.qadaa_add_to_qadaa)) {
            onAdd(selected.associateWith { countPerPrayer })
            onDismiss()
        }
        Spacer(Modifier.height(8.dp))
    }
}

// ── Calendar tab ──────────────────────────────────────────────────────────────

@RequiresApi(Build.VERSION_CODES.O)
@Composable
private fun CalendarTab(onAdd: (Map<Prayer, Int>) -> Unit, onDismiss: () -> Unit) {
    var displayMonth by remember { mutableStateOf(YearMonth.now()) }
    var selectedDays by remember { mutableStateOf(emptySet<LocalDate>()) }
    val today = LocalDate.now()
    val total = selectedDays.size * Prayer.entries.size

    // Locale-aware day-of-week abbreviations, Monday-first
    val dayHeaders = remember {
        (1..7).map { dow -> DayOfWeek.of(dow).getDisplayName(TextStyle.NARROW, Locale.getDefault()) }
    }

    Column(modifier = Modifier.padding(horizontal = 20.dp).verticalScroll(rememberScrollState())) {
        // Month navigation
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { displayMonth = displayMonth.minusMonths(1) }) {
                Icon(Icons.Outlined.ChevronLeft, contentDescription = null)
            }
            Text(
                displayMonth.format(DateTimeFormatter.ofPattern("MMMM yyyy")),
                modifier  = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                style     = MaterialTheme.typography.titleMedium,
            )
            IconButton(
                enabled = displayMonth < YearMonth.now(),
                onClick = { displayMonth = displayMonth.plusMonths(1) },
            ) {
                Icon(Icons.Outlined.ChevronRight, contentDescription = null)
            }
        }
        // Day-of-week headers
        Row(modifier = Modifier.fillMaxWidth()) {
            dayHeaders.forEach { d ->
                Text(
                    d,
                    modifier  = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style     = MaterialTheme.typography.labelSmall,
                    color     = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        // Build day grid (Monday-first; nulls are empty cells before day 1)
        val firstDay = displayMonth.atDay(1)
        val offset   = (firstDay.dayOfWeek.value - 1) // 0=Mon
        val daysInMonth = displayMonth.lengthOfMonth()
        val cells = buildList<LocalDate?> {
            repeat(offset) { add(null) }
            for (d in 1..daysInMonth) add(firstDay.withDayOfMonth(d))
        }
        cells.chunked(7).forEach { week ->
            Row(modifier = Modifier.fillMaxWidth()) {
                week.forEach { date ->
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        if (date != null) {
                            val isFuture   = date.isAfter(today)
                            val isSelected = date in selectedDays
                            val isToday    = date == today
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .then(when {
                                        isSelected -> Modifier.background(MaterialTheme.colorScheme.primary)
                                        isToday    -> Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary, CircleShape)
                                        else       -> Modifier
                                    })
                                    .then(if (!isFuture) Modifier.clickable {
                                        selectedDays = if (date in selectedDays) selectedDays - date else selectedDays + date
                                    } else Modifier),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    "${date.dayOfMonth}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = when {
                                        isSelected -> MaterialTheme.colorScheme.onPrimary
                                        isFuture   -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                                        else       -> MaterialTheme.colorScheme.onSurface
                                    },
                                )
                            }
                        }
                    }
                }
                // Pad incomplete last row
                repeat(7 - week.size) { Box(modifier = Modifier.weight(1f)) }
            }
            Spacer(Modifier.height(4.dp))
        }
        Spacer(Modifier.height(12.dp))
        if (selectedDays.isNotEmpty()) {
            Text(
                stringResource(R.string.qadaa_that_is_n_prayers, total),
                style      = MaterialTheme.typography.bodyMedium,
                color      = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(8.dp))
        }
        AddButton(total = total, label = stringResource(R.string.qadaa_add_to_qadaa)) {
            onAdd(Prayer.entries.associateWith { selectedDays.size })
            onDismiss()
        }
        Spacer(Modifier.height(8.dp))
    }
}

// ── Shared sub-composables ────────────────────────────────────────────────────

@Composable
private fun PrayerCountRow(
    label: String,
    count: Int,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
    active: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (active) Modifier.background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)) else Modifier)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (active) Box(modifier = Modifier.size(3.dp, 20.dp).clip(RoundedCornerShape(2.dp)).background(MaterialTheme.colorScheme.primary))
        Spacer(Modifier.width(if (active) 10.dp else 0.dp))
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        CounterButton("-", enabled = count > 0, onClick = onDecrement)
        Text(
            "$count",
            modifier   = Modifier.width(40.dp),
            textAlign  = TextAlign.Center,
            style      = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        CounterButton("+", onClick = onIncrement)
    }
}

@Composable
private fun PrayerChips(selected: Set<Prayer>, onToggle: (Prayer) -> Unit) {
    val allSelected = selected.size == Prayer.entries.size
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        FilterChip(
            selected = allSelected,
            onClick  = {
                if (allSelected) Prayer.entries.forEach { onToggle(it) }
                else Prayer.entries.filter { it !in selected }.forEach { onToggle(it) }
            },
            label = { Text(stringResource(R.string.qadaa_all_five)) },
        )
    }
    Spacer(Modifier.height(6.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Prayer.entries.take(3).forEach { prayer ->
            FilterChip(
                selected = prayer in selected,
                onClick  = { onToggle(prayer) },
                label    = { Text(stringResource(prayer.nameRes)) },
            )
        }
    }
    Spacer(Modifier.height(6.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Prayer.entries.drop(3).forEach { prayer ->
            FilterChip(
                selected = prayer in selected,
                onClick  = { onToggle(prayer) },
                label    = { Text(stringResource(prayer.nameRes)) },
            )
        }
    }
}

@Composable
internal fun CounterButton(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    OutlinedIconButton(
        onClick  = onClick,
        enabled  = enabled,
        modifier = Modifier.size(36.dp),
    ) { Text(label, style = MaterialTheme.typography.titleMedium) }
}

@Composable
private fun DateField(label: String, value: String?, modifier: Modifier = Modifier, onClick: () -> Unit) {
    OutlinedTextField(
        value         = value ?: "",
        onValueChange = {},
        readOnly      = true,
        label         = { Text(label) },
        trailingIcon  = { Icon(Icons.Outlined.CalendarMonth, contentDescription = null) },
        placeholder   = { Text(stringResource(R.string.qadaa_date_select)) },
        modifier      = modifier.clickable(onClick = onClick),
        shape         = RoundedCornerShape(12.dp),
        enabled       = false,
        colors        = OutlinedTextFieldDefaults.colors(
            disabledTextColor         = MaterialTheme.colorScheme.onSurface,
            disabledBorderColor       = MaterialTheme.colorScheme.outline,
            disabledPlaceholderColor  = MaterialTheme.colorScheme.onSurfaceVariant,
            disabledLabelColor        = MaterialTheme.colorScheme.onSurfaceVariant,
            disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
    )
}

@Composable
internal fun AddButton(total: Int, label: String, onClick: () -> Unit) {
    Button(
        onClick  = onClick,
        enabled  = total > 0,
        modifier = Modifier.fillMaxWidth().height(52.dp),
        shape    = RoundedCornerShape(50),
    ) { Text(label, style = MaterialTheme.typography.labelLarge) }
}