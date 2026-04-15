package com.lhacenmed.khatmah.ui.page.settings.prayers

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lhacenmed.khatmah.data.prayer.PrayerCalcSettings
import com.lhacenmed.khatmah.data.prayer.PrayerEngine
import com.lhacenmed.khatmah.data.prayer.toAmPm
import com.lhacenmed.khatmah.util.OnboardingPrefs
import java.time.LocalDate

/**
 * Horizontal strip showing today's 6 prayer times computed live from [settings].
 *
 * Placed at the top of every prayer-settings sub-page so the user can instantly
 * see how each option affects today's times without leaving the page.
 *
 * Renders nothing on API < 26 — the settings page still functions correctly.
 */
@Composable
fun PrayerTimesPreviewBar(settings: PrayerCalcSettings, modifier: Modifier = Modifier) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        PreviewBarImpl(settings, modifier)
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@Composable
private fun PreviewBarImpl(settings: PrayerCalcSettings, modifier: Modifier) {
    val context = LocalContext.current
    val loc     = remember { OnboardingPrefs.location(context) }

    // The computation is pure math (< 1 ms) so it runs synchronously inside remember.
    val times = remember(settings, loc) {
        if (loc == null || (loc.lat == 0.0 && loc.lng == 0.0)) emptyList()
        else runCatching {
            PrayerEngine.calculate(loc.lat, loc.lng, LocalDate.now(), settings.resolve(loc.countryCode))
        }.getOrDefault(emptyList())
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        color    = MaterialTheme.colorScheme.primaryContainer,
    ) {
        if (times.isEmpty()) {
            Box(
                modifier         = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text  = "—",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f),
                )
            }
        } else {
            Row(
                modifier              = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                times.forEach { prayer ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text  = prayer.name.take(3).uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.65f),
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text  = prayer.time.toAmPm(),
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
            }
        }
    }
}