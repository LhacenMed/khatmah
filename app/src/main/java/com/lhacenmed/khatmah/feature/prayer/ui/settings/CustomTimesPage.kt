package com.lhacenmed.khatmah.feature.prayer.ui.settings

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lhacenmed.khatmah.R
import com.lhacenmed.khatmah.core.ui.components.showTimePicker
import com.lhacenmed.khatmah.feature.prayer.data.CustomPrayerTimes
import com.lhacenmed.khatmah.feature.prayer.data.CustomTimesPrefs
import com.lhacenmed.khatmah.feature.prayer.data.PrayerSettings
import com.lhacenmed.khatmah.feature.prayer.data.PrayerTime
import com.lhacenmed.khatmah.feature.prayer.data.PrayerTimetable
import com.lhacenmed.khatmah.feature.prayer.data.toAmPm
import java.time.LocalDate
import java.time.LocalTime

/** In the order the timetable returns, so a row's index is the prayer it stands for. */
private val PRAYER_LABELS = intArrayOf(
    R.string.prayer_fajr, R.string.prayer_sunrise, R.string.prayer_dhuhr,
    R.string.prayer_asr, R.string.prayer_maghrib, R.string.prayer_isha,
)

// Body only — the title + back arrow come from ScreenHostActivity (see Dest.CustomTimes.titleRes).
/**
 * Where the user takes a prayer's time into their own hands.
 *
 * Every row shows the time the app will announce, whether the app worked it out or the user gave
 * it, because that — not which of the two it is — is what someone opens this page to check. The
 * status line underneath says which, and clearing a row hands the prayer back to the calculation.
 *
 * A row keeps its shape either way: the time and the clear button are always both there, the
 * button simply going quiet on a prayer that has nothing to clear.
 */
@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun CustomTimesScreen() {
    val context = LocalContext.current
    val settings    by PrayerSettings.flow.collectAsState()
    val customTimes by CustomTimesPrefs.flow.collectAsState()

    // Today's times as the app will announce them. Recomputed on every save, so a row goes back to
    // showing the calculated time the moment it is cleared. Empty only where there are none to
    // show at all — no location, or a latitude the method cannot answer for.
    val times = remember(settings, customTimes) {
        PrayerTimetable.forDate(context, LocalDate.now())
    }

    // Saving is all this page does: the alarms and the widget are rebuilt from the times by
    // App.keepSurfacesOnPrayerTimes, which watches for exactly this.
    fun commit(next: CustomPrayerTimes) = CustomTimesPrefs.save(context, next)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        ListItem(
            headlineContent = { Text(stringResource(R.string.custom_times_all)) },
            trailingContent = {
                Switch(
                    checked         = customTimes.hasAll,
                    enabled         = times.isNotEmpty(),
                    onCheckedChange = { pinAll ->
                        commit(if (pinAll) times.asCustomTimes() else CustomPrayerTimes())
                    },
                )
            },
        )
        HorizontalDivider()

        PRAYER_LABELS.forEachIndexed { index, labelRes ->
            val time = times.getOrNull(index)?.time
            PrayerTimeRow(
                label    = stringResource(labelRes),
                time     = time,
                isCustom = customTimes.inPrayerOrder[index] != null,
                onPick   = {
                    time?.let { current ->
                        showTimePicker(context, current.hour, current.minute) { hour, minute ->
                            commit(customTimes.with(index, hour * 60 + minute))
                        }
                    }
                },
                onClear  = { commit(customTimes.with(index, null)) },
            )
        }
    }
}

/** Every prayer pinned where it falls today — what turning the switch on means. */
@RequiresApi(Build.VERSION_CODES.O)
private fun List<PrayerTime>.asCustomTimes(): CustomPrayerTimes {
    fun minuteOfDay(index: Int): Int? =
        getOrNull(index)?.time?.let { it.hour * 60 + it.minute }
    return CustomPrayerTimes(
        fajr    = minuteOfDay(0),
        sunrise = minuteOfDay(1),
        dhuhr   = minuteOfDay(2),
        asr     = minuteOfDay(3),
        maghrib = minuteOfDay(4),
        isha    = minuteOfDay(5),
    )
}

// ─── Prayer row ───────────────────────────────────────────────────────────────

@RequiresApi(Build.VERSION_CODES.O)
@Composable
private fun PrayerTimeRow(
    label:    String,
    time:     LocalTime?,
    isCustom: Boolean,
    onPick:   () -> Unit,
    onClear:  () -> Unit,
) {
    ListItem(
        headlineContent   = { Text(label) },
        supportingContent = {
            Text(
                stringResource(
                    if (isCustom) R.string.custom_times_status_custom
                    else R.string.custom_times_status_calculated
                )
            )
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onPick, enabled = time != null) {
                    Text(
                        text  = time?.toAmPm() ?: "—",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                IconButton(
                    onClick  = onClear,
                    enabled  = isCustom,
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        imageVector        = Icons.Outlined.Close,
                        contentDescription = stringResource(R.string.custom_times_clear),
                    )
                }
            }
        },
    )
    HorizontalDivider(
        modifier  = Modifier.padding(horizontal = 16.dp),
        thickness = 0.5.dp,
        color     = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
    )
}
