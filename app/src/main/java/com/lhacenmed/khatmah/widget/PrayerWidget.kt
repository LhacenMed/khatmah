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

    // ── Countdown mode ────────────────────────────────────────────────────────

    /**
     * Determines how the countdown panel renders at any given moment.
     *
     * [Till]  — prayer is upcoming; Chronometer counts down to it.
     * [Since] — prayer just passed (within [SINCE_WINDOW_MS]); Chronometer counts up
     *           from when it started. A one-shot worker is scheduled for exactly when
     *           the window closes, automatically switching back to [Till] for the
     *           next prayer without any app relaunch or manual widget refresh.
     */
    private sealed class CountdownMode {
        abstract val prayer: PrayerTime
        data class Till(override val prayer: PrayerTime, val msRemaining: Long) : CountdownMode()
        data class Since(override val prayer: PrayerTime, val msPassed: Long)   : CountdownMode()
    }

    companion object {
        /** Duration to show "Since [prayer]" before switching to the next countdown. */
        private const val SINCE_WINDOW_MS = 15 * 60 * 1000L

        // Highlight color (#FFEB3B) for the active prayer row — matches the original design.
        private val COLOR_HIGHLIGHT = android.graphics.Color.argb(255, 255, 235, 59)
        private const val COLOR_NORMAL    = android.graphics.Color.WHITE

        private val TIME_FMT = DateTimeFormatter.ofPattern("HH:mm")

        // Parallel arrays indexed by prayer order (0 = Fajr … 5 = Isha).
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

        val mode: CountdownMode? = if (prayers.isNotEmpty()) {
            resolveMode(prayers, LocalTime.now())
        } else null

        // When in Since mode, fire a one-shot update at the exact moment the 15-min
        // window closes so the label and Chronometer switch to the next prayer
        // automatically — no app relaunch or manual refresh required.
        if (mode is CountdownMode.Since) {
            val msUntilSwitch = SINCE_WINDOW_MS - mode.msPassed
            if (msUntilSwitch > 0) PrayerWidgetWorker.scheduleOneShot(context, msUntilSwitch)
        }

        provideContent {
            GlanceTheme { Content(prayers, mode) }
        }
    }

    // ── Mode resolution ───────────────────────────────────────────────────────

    /**
     * Resolves [CountdownMode] for the current moment:
     *
     * - If a prayer passed within the last [SINCE_WINDOW_MS] → [CountdownMode.Since]
     *   pointing at that prayer (the most recent if several qualify, though in practice
     *   prayer times are spaced far enough apart that only one can qualify).
     * - Otherwise → [CountdownMode.Till] pointing at the next upcoming prayer,
     *   wrapping to Fajr if all prayers for today have passed.
     */
    private fun resolveMode(prayers: List<PrayerTime>, now: LocalTime): CountdownMode {
        val recentlyPassed = prayers.lastOrNull { prayer ->
            val elapsed = Duration.between(prayer.time, now)
            !elapsed.isNegative && elapsed.toMillis() < SINCE_WINDOW_MS
        }

        if (recentlyPassed != null) {
            return CountdownMode.Since(
                prayer   = recentlyPassed,
                msPassed = Duration.between(recentlyPassed.time, now).toMillis(),
            )
        }

        val next = prayers.firstOrNull { it.time.isAfter(now) } ?: prayers.first()
        val ms   = Duration.between(now, next.time).let { d ->
            if (d.isNegative) d.plusDays(1) else d
        }.toMillis()
        return CountdownMode.Till(prayer = next, msRemaining = ms)
    }

    // ── Root layout ───────────────────────────────────────────────────────────

    @Composable
    private fun Content(prayers: List<PrayerTime>, mode: CountdownMode?) {
        val context = LocalContext.current

        // Tapping anywhere on the widget opens the app directly on the Prayers tab.
        // FLAG_ACTIVITY_SINGLE_TOP: reuses an existing MainActivity instead of stacking
        // a new instance, so onNewIntent is called when the app is already open.
        val openPrayersAction = actionStartActivity(
            Intent(context, MainActivity::class.java).apply {
                action = WidgetAction.OPEN_PRAYERS
                flags  = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
        )

        if (prayers.isEmpty() || mode == null) {
            Box(
                modifier = GlanceModifier.fillMaxSize()
                    .background(ImageProvider(R.drawable.widget_bg))
                    .clickable(openPrayersAction),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text  = "Open the app to set your location",
                    style = TextStyle(color = ColorProvider(Color.White), fontSize = 30.sp)
                )
            }
            return
        }

        val isRtl = LocaleManager.getCurrentTag()?.startsWith("ar") == true

        // Outer rounded container — the rounded drawable clips both child panels.
        Row(
            modifier = GlanceModifier.fillMaxSize()
                .background(ImageProvider(R.drawable.widget_bg))
                .clickable(openPrayersAction),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val side = GlanceModifier.defaultWeight().fillMaxHeight()
            if (isRtl) {
                PrayerList(prayers, mode.prayer.name, isRtl, side)
                CountdownPanel(mode, isRtl, side)
            } else {
                CountdownPanel(mode, isRtl, side)
                PrayerList(prayers, mode.prayer.name, isRtl, side)
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
        mode: CountdownMode,
        isRtl: Boolean,
        modifier: GlanceModifier
    ) {
        val context = LocalContext.current
        val bgRes = if (isRtl) R.drawable.widget_countdown_rtl_bg
        else       R.drawable.widget_countdown_ltr_bg

        val label = when (mode) {
            is CountdownMode.Till  ->
                if (isRtl) "باقي على ${arabicName(mode.prayer.name, context)}"
                else       "Till ${mode.prayer.name}"
            is CountdownMode.Since ->
                if (isRtl) "منذ ${arabicName(mode.prayer.name, context)}"
                else       "Since ${mode.prayer.name}"
        }

        AndroidRemoteViews(
            modifier = modifier,
            remoteViews = RemoteViews(context.packageName, R.layout.widget_countdown).apply {
                // Background — rounded on the outer edge only.
                setInt(R.id.countdown_root, "setBackgroundResource", bgRes)
                // Icon.
                setImageViewResource(R.id.prayer_icon, prayerIcon(mode.prayer.name))
                // Label — "Till X" or "Since X".
                setTextViewText(R.id.prayer_label, label)
                // Chronometer direction and base depend on mode:
                //   Till  → count DOWN to the prayer (base is in the future).
                //   Since → count UP from the prayer  (base is in the past).
                // The Chronometer ticks every second with zero battery overhead.
                when (mode) {
                    is CountdownMode.Till  -> {
                        setChronometer(R.id.chrono, SystemClock.elapsedRealtime() + mode.msRemaining, null, true)
                        setChronometerCountDown(R.id.chrono, true)
                    }
                    is CountdownMode.Since -> {
                        setChronometer(R.id.chrono, SystemClock.elapsedRealtime() - mode.msPassed, null, true)
                        setChronometerCountDown(R.id.chrono, false)
                    }
                }
            }
        )
    }

    // ── Prayer list panel ─────────────────────────────────────────────────────
    //
    // Rendered as RemoteViews using widget_prayer_list.xml.
    //
    // Bold highlight: setTextViewText accepts CharSequence, and SpannableString with
    // StyleSpan(BOLD) is a ParcelableSpan — it survives the RemoteViews IPC boundary
    // without any API-level restrictions or extra style resources.

    @Composable
    private fun PrayerList(
        prayers: List<PrayerTime>,
        highlightName: String,
        isRtl: Boolean,
        modifier: GlanceModifier
    ) {
        val context = LocalContext.current
        AndroidRemoteViews(
            modifier = modifier,
            remoteViews = RemoteViews(context.packageName, R.layout.widget_prayer_list).apply {
                prayers.forEachIndexed { i, prayer ->
                    val isHighlight = prayer.name == highlightName
                    val color       = if (isHighlight) COLOR_HIGHLIGHT else COLOR_NORMAL
                    val nameText    = if (isRtl) arabicName(prayer.name, context) else prayer.name
                    val timeText    = prayer.time.format(TIME_FMT)

                    // In LTR: left = name, right = time.
                    // In RTL: left = time, right = name (mirrors natural reading direction).
                    val leftText  = if (isRtl) timeText else nameText
                    val rightText = if (isRtl) nameText else timeText

                    // Highlighted prayer gets bold weight; normal prayers use plain strings.
                    setTextViewText(LEFT_IDS[i],  if (isHighlight) boldOf(leftText)  else leftText)
                    setTextViewText(RIGHT_IDS[i], if (isHighlight) boldOf(rightText) else rightText)
                    setTextColor(LEFT_IDS[i],  color)
                    setTextColor(RIGHT_IDS[i], color)
                    setViewVisibility(ROW_IDS[i], View.VISIBLE)
                }
                // Hide rows beyond the actual prayer count (safety net).
                for (i in prayers.size until ROW_IDS.size) {
                    setViewVisibility(ROW_IDS[i], View.GONE)
                }
            }
        )
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Wraps [text] in a [SpannableString] with a bold [StyleSpan] covering the full
     * string. [StyleSpan] implements [android.text.ParcelableSpan], so it survives
     * the RemoteViews serialization boundary on all supported API levels.
     */
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