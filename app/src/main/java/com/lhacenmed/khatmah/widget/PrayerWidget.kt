package com.lhacenmed.khatmah.widget

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.widget.RemoteViews
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.appwidget.AndroidRemoteViews
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.lhacenmed.khatmah.R
import com.lhacenmed.khatmah.data.prayer.PrayerEngine
import com.lhacenmed.khatmah.data.prayer.PrayerSettings
import com.lhacenmed.khatmah.data.prayer.PrayerTime
import com.lhacenmed.khatmah.util.LocaleManager
import com.lhacenmed.khatmah.util.OnboardingPrefs
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@RequiresApi(Build.VERSION_CODES.O)
@SuppressLint("RestrictedApi")
class PrayerWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // Ensure settings are loaded in case this runs outside the app process.
        PrayerSettings.init(context)

        val location = OnboardingPrefs.location(context)
        val prayers = location?.let { loc ->
            runCatching {
                PrayerEngine.calculate(
                    loc.lat, loc.lng, LocalDate.now(),
                    PrayerSettings.get().resolve(loc.countryCode)
                )
            }.getOrDefault(emptyList())
        } ?: emptyList()

        provideContent {
            GlanceTheme { Content(prayers) }
        }
    }

    // ── Root layout ───────────────────────────────────────────────────────────

    @Composable
    private fun Content(prayers: List<PrayerTime>) {
        if (prayers.isEmpty()) {
            Box(
                modifier = GlanceModifier.fillMaxSize()
                    .background(ImageProvider(R.drawable.widget_bg)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Open the app to set your location",
                    style = TextStyle(color = ColorProvider(Color.White), fontSize = 30.sp)
                )
            }
            return
        }

        val isRtl = LocaleManager.getCurrentTag()?.startsWith("ar") == true
        val now = LocalTime.now()

        // Next prayer = first prayer whose time is still ahead; wraps to index 0 at end of day.
        val nextIdx = prayers.indexOfFirst { it.time.isAfter(now) }.let { if (it == -1) 0 else it }
        val next = prayers[nextIdx]
        val msRemaining = Duration.between(now, next.time)
            .let { if (it.isNegative) it.plusDays(1) else it }
            .toMillis()

        // Outer rounded container — the rounded drawable clips both child panels.
        Row(
            modifier = GlanceModifier.fillMaxSize()
                .background(ImageProvider(R.drawable.widget_bg)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val side = GlanceModifier.defaultWeight().fillMaxHeight()
            if (isRtl) {
                PrayerList(prayers, nextIdx, isRtl, side)
                CountdownPanel(next, msRemaining, isRtl, side)
            } else {
                CountdownPanel(next, msRemaining, isRtl, side)
                PrayerList(prayers, nextIdx, isRtl, side)
            }
        }
    }

    // ── Countdown panel ───────────────────────────────────────────────────────
    //
    // Rendered entirely as a native RemoteViews layout so that gravity="center"
    // on the root LinearLayout controls alignment — Glance's horizontalAlignment
    // does not propagate into AndroidRemoteViews children reliably.

    @Composable
    private fun CountdownPanel(
        next: PrayerTime,
        msRemaining: Long,
        isRtl: Boolean,
        modifier: GlanceModifier
    ) {
        val context = LocalContext.current
        val bgRes = if (isRtl) R.drawable.widget_countdown_rtl_bg
        else       R.drawable.widget_countdown_ltr_bg
        val label = if (isRtl) "باقي على ${arabicName(next.name, context)}"
        else       "Till ${next.name}"

        AndroidRemoteViews(
            modifier = modifier,
            remoteViews = RemoteViews(context.packageName, R.layout.widget_countdown).apply {
                // Background — rounded on the outer edge only.
                setInt(R.id.countdown_root, "setBackgroundResource", bgRes)
                // Icon.
                setImageViewResource(R.id.prayer_icon, prayerIcon(next.name))
                // Label.
                setTextViewText(R.id.prayer_label, label)
                // Countdown — Chronometer ticks every second with zero battery overhead.
                val base = SystemClock.elapsedRealtime() + msRemaining
                setChronometer(R.id.chrono, base, null, true)
                setChronometerCountDown(R.id.chrono, true)
            }
        )
    }

    // ── Prayer list panel ─────────────────────────────────────────────────────

    @Composable
    private fun PrayerList(
        prayers: List<PrayerTime>,
        nextIdx: Int,
        isRtl: Boolean,
        modifier: GlanceModifier
    ) {
        val context = LocalContext.current
        Column(
            modifier = modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            prayers.forEachIndexed { i, prayer ->
                val isNext = i == nextIdx
                val color = if (isNext) Color(0xFFFFEB3B) else Color.White
                val name = if (isRtl) arabicName(prayer.name, context) else prayer.name
                val time = prayer.time.format(DateTimeFormatter.ofPattern("HH:mm"))
                Row(
                    modifier = GlanceModifier.fillMaxWidth().padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isRtl) {
                        Text(time, style = rowStyle(color, isNext))
                        Spacer(GlanceModifier.defaultWeight())
                        Text(name, style = rowStyle(color, isNext))
                    } else {
                        Text(name, style = rowStyle(color, isNext))
                        Spacer(GlanceModifier.defaultWeight())
                        Text(time, style = rowStyle(color, isNext))
                    }
                }
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun rowStyle(color: Color, bold: Boolean) = TextStyle(
        color = ColorProvider(color),
        fontSize = 25.sp,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal
    )

    private fun prayerIcon(name: String): Int = when (name.lowercase()) {
        "fajr"    -> R.drawable.ic_fajr
        "sunrise" -> R.drawable.ic_sunrise
        "dhuhr"   -> R.drawable.ic_dhuhr
        "asr"     -> R.drawable.ic_asr
        "maghrib" -> R.drawable.ic_maghrib
        "isha"    -> R.drawable.ic_isha
        else      -> R.drawable.ic_dhuhr
    }

    private fun arabicName(name: String, context: Context): String = when (name.lowercase()) {
        "fajr"    -> context.getString(R.string.prayer_fajr)
        "sunrise" -> context.getString(R.string.prayer_sunrise)
        "dhuhr"   -> context.getString(R.string.prayer_dhuhr)
        "asr"     -> context.getString(R.string.prayer_asr)
        "maghrib" -> context.getString(R.string.prayer_maghrib)
        "isha"    -> context.getString(R.string.prayer_isha)
        else      -> name
    }
}