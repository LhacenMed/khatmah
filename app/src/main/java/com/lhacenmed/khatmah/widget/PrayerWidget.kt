package com.lhacenmed.khatmah.widget

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.os.Build
import android.os.SystemClock
import android.text.Spannable
import android.text.SpannableString
import android.text.style.StyleSpan
import android.view.View
import android.widget.RemoteViews
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.clickable
import androidx.glance.appwidget.AndroidRemoteViews
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.lhacenmed.khatmah.MainActivity
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

    /**
     * The next upcoming prayer and how many milliseconds remain until it starts.
     * Always represents a future prayer — never negative, never "since".
     */
    private data class Countdown(val prayer: PrayerTime, val msRemaining: Long)

    companion object {
        // Highlight color (#FFEB3B) for the active prayer row.
        private val COLOR_HIGHLIGHT = android.graphics.Color.argb(255, 255, 235, 59)
        private const val COLOR_NORMAL = android.graphics.Color.WHITE

        private val TIME_FMT = DateTimeFormatter.ofPattern("HH:mm")

        private val ROW_IDS   = intArrayOf(
            R.id.prayer_row_0,   R.id.prayer_row_1,   R.id.prayer_row_2,
            R.id.prayer_row_3,   R.id.prayer_row_4,   R.id.prayer_row_5,
        )
        private val LEFT_IDS  = intArrayOf(
            R.id.prayer_left_0,  R.id.prayer_left_1,  R.id.prayer_left_2,
            R.id.prayer_left_3,  R.id.prayer_left_4,  R.id.prayer_left_5,
        )
        private val RIGHT_IDS = intArrayOf(
            R.id.prayer_right_0, R.id.prayer_right_1, R.id.prayer_right_2,
            R.id.prayer_right_3, R.id.prayer_right_4, R.id.prayer_right_5,
        )
    }

    // ── Entry point ───────────────────────────────────────────────────────────

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        PrayerSettings.init(context)

        val location = OnboardingPrefs.location(context)
        val prayers  = location?.let { loc ->
            runCatching {
                PrayerEngine.calculate(
                    loc.lat, loc.lng, LocalDate.now(),
                    PrayerSettings.get().resolve(loc.countryCode),
                )
            }.getOrDefault(emptyList())
        } ?: emptyList()

        val countdown: Countdown? = prayers.takeIf { it.isNotEmpty() }
            ?.let { resolveCountdown(it, LocalTime.now()) }

        provideContent {
            GlanceTheme { Content(prayers, countdown) }
        }
    }

    // ── Countdown resolution ──────────────────────────────────────────────────

    /**
     * Finds the next upcoming prayer and returns a [Countdown] with a guaranteed
     * non-negative [Countdown.msRemaining].
     *
     * If all prayers for today have passed, wraps to tomorrow's Fajr so the
     * Chronometer always counts forward — never negative.
     */
    private fun resolveCountdown(prayers: List<PrayerTime>, now: LocalTime): Countdown {
        val next = prayers.firstOrNull { it.time.isAfter(now) } ?: prayers.first()
        val ms   = Duration.between(now, next.time).let { d ->
            if (d.isNegative) d.plusDays(1) else d
        }.toMillis().coerceAtLeast(0L)
        return Countdown(next, ms)
    }

    // ── Root layout ───────────────────────────────────────────────────────────

    @Composable
    private fun Content(prayers: List<PrayerTime>, countdown: Countdown?) {
        val context = LocalContext.current

        val openPrayersAction = actionStartActivity(
            Intent(context, MainActivity::class.java).apply {
                action = WidgetAction.OPEN_PRAYERS
                flags  = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
        )

        if (prayers.isEmpty() || countdown == null) {
            Box(
                modifier         = GlanceModifier.fillMaxSize()
                    .background(ImageProvider(R.drawable.widget_bg))
                    .clickable(openPrayersAction),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text  = "Open the app to set your location",
                    style = TextStyle(color = ColorProvider(Color.White), fontSize = 30.sp),
                )
            }
            return
        }

        val isRtl = LocaleManager.savedTag(context)?.startsWith("ar") == true

        Row(
            modifier          = GlanceModifier.fillMaxSize()
                .background(ImageProvider(R.drawable.widget_bg))
                .clickable(openPrayersAction),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val side = GlanceModifier.defaultWeight().fillMaxHeight()
            if (isRtl) {
                PrayerList(prayers, countdown.prayer.name, isRtl, side)
                CountdownPanel(countdown, isRtl, side)
            } else {
                CountdownPanel(countdown, isRtl, side)
                PrayerList(prayers, countdown.prayer.name, isRtl, side)
            }
        }
    }

    // ── Countdown panel ───────────────────────────────────────────────────────

    @Composable
    private fun CountdownPanel(countdown: Countdown, isRtl: Boolean, modifier: GlanceModifier) {
        val context = LocalContext.current
        val bgRes   = if (isRtl) R.drawable.widget_countdown_rtl_bg
        else       R.drawable.widget_countdown_ltr_bg

        val label = if (isRtl) "باقي على ${prayerName(countdown.prayer.name, context, isRtl)}"
        else       "Till ${prayerName(countdown.prayer.name, context, isRtl)}"

        AndroidRemoteViews(
            modifier    = modifier,
            remoteViews = RemoteViews(context.packageName, R.layout.widget_countdown).apply {
                setInt(R.id.countdown_root, "setBackgroundResource", bgRes)
                setImageViewResource(R.id.prayer_icon, prayerIcon(countdown.prayer.name))
                setTextViewText(R.id.prayer_label, label)
                // Base is always in the future — Chronometer counts down, never negative.
                setChronometer(
                    R.id.chrono,
                    SystemClock.elapsedRealtime() + countdown.msRemaining,
                    null,
                    true,
                )
                setChronometerCountDown(R.id.chrono, true)
            },
        )
    }

    // ── Prayer list panel ─────────────────────────────────────────────────────

    @Composable
    private fun PrayerList(
        prayers:       List<PrayerTime>,
        highlightName: String,
        isRtl:         Boolean,
        modifier:      GlanceModifier,
    ) {
        val context = LocalContext.current
        AndroidRemoteViews(
            modifier    = modifier,
            remoteViews = RemoteViews(context.packageName, R.layout.widget_prayer_list).apply {
                prayers.forEachIndexed { i, prayer ->
                    val isHighlight = prayer.name == highlightName
                    val color       = if (isHighlight) COLOR_HIGHLIGHT else COLOR_NORMAL
                    val nameText    = prayerName(prayer.name, context, isRtl)
                    val timeText    = prayer.time.format(TIME_FMT)

                    val leftText  = if (isRtl) timeText else nameText
                    val rightText = if (isRtl) nameText else timeText

                    setTextViewText(LEFT_IDS[i],  if (isHighlight) boldOf(leftText)  else leftText)
                    setTextViewText(RIGHT_IDS[i], if (isHighlight) boldOf(rightText) else rightText)
                    setTextColor(LEFT_IDS[i],  color)
                    setTextColor(RIGHT_IDS[i], color)
                    setViewVisibility(ROW_IDS[i], View.VISIBLE)
                }
                for (i in prayers.size until ROW_IDS.size) {
                    setViewVisibility(ROW_IDS[i], View.GONE)
                }
            },
        )
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Returns the localized prayer name.
     * Uses the string resource (Arabic when [isRtl], English otherwise) so the
     * result is correct regardless of which process is running the widget.
     */
    private fun prayerName(name: String, context: Context, isRtl: Boolean): String {
        if (!isRtl) return name   // English name from the engine is already correct
        return when (name.lowercase()) {
            "fajr"    -> context.getString(R.string.prayer_fajr)
            "sunrise" -> context.getString(R.string.prayer_sunrise)
            "dhuhr"   -> context.getString(R.string.prayer_dhuhr)
            "asr"     -> context.getString(R.string.prayer_asr)
            "maghrib" -> context.getString(R.string.prayer_maghrib)
            "isha"    -> context.getString(R.string.prayer_isha)
            else      -> name
        }
    }

    private fun boldOf(text: String): SpannableString =
        SpannableString(text).apply {
            setSpan(StyleSpan(Typeface.BOLD), 0, length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

    private fun prayerIcon(name: String): Int = when (name.lowercase()) {
        "fajr"    -> R.drawable.ic_fajr
        "sunrise" -> R.drawable.ic_sunrise
        "dhuhr"   -> R.drawable.ic_dhuhr
        "asr"     -> R.drawable.ic_asr
        "maghrib" -> R.drawable.ic_maghrib
        "isha"    -> R.drawable.ic_isha
        else      -> R.drawable.ic_dhuhr
    }
}