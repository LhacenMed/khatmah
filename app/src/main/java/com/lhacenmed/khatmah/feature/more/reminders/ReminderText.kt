package com.lhacenmed.khatmah.feature.more.reminders

import android.content.Context
import com.lhacenmed.khatmah.R
import com.lhacenmed.khatmah.shared.reminders.ReminderConfig
import com.lhacenmed.khatmah.shared.reminders.ReminderType
import com.lhacenmed.khatmah.shared.reminders.SunnahSurah
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

/**
 * How a reminder writes itself out — its name, and its schedule. The one place that turns an hour,
 * a minute and a day of the week into something to read, so a row, the editor sheet and the picker
 * all say it the same way.
 *
 * The weekday names are the platform's, in the app's own locale, rather than seven more strings to
 * translate: the calendar already knows what Friday is called in every language the app speaks.
 */

/**
 * What a reminder calls itself.
 *
 * One the user added carries the name of whatever they picked. The three surahs the app suggests
 * are named from the string table instead, so they follow the app's language the way their own
 * rows do.
 */
fun reminderName(context: Context, config: ReminderConfig): String {
    config.label?.let { return it }
    val surah = (config.type as? ReminderType.QuranSunnah)?.let { SunnahSurah.of(it.surahKey) }
    return context.getString(surah?.nameRes ?: R.string.reminders_title)
}

/** The time a reminder is set for, as a 24-hour clock reading — "08:10". */
fun clockText(config: ReminderConfig): String =
    "%02d:%02d".format(config.timeHour, config.timeMinute)

/** How often it comes back — "Daily", or the weekday it lands on. */
fun repeatText(context: Context, repeatDayOfWeek: Int): String =
    if (repeatDayOfWeek == ReminderConfig.REPEAT_DAILY) context.getString(R.string.reminders_repeat_daily)
    else weekdayName(repeatDayOfWeek)

/** Both together, as a reminder's row reports itself — "08:10 · Friday". */
fun scheduleText(context: Context, config: ReminderConfig): String =
    "${clockText(config)} · ${repeatText(context, config.repeatDayOfWeek)}"

/** Every repeat a reminder can be set to, in the order the editor offers them. */
fun repeatOptions(context: Context): List<Pair<Int, String>> =
    listOf(ReminderConfig.REPEAT_DAILY to context.getString(R.string.reminders_repeat_daily)) +
        DayOfWeek.values().map { it.value to weekdayName(it.value) }

private fun weekdayName(dayOfWeek: Int): String =
    DayOfWeek.of(dayOfWeek).getDisplayName(TextStyle.FULL, Locale.getDefault())
