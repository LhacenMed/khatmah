package com.lhacenmed.khatmah.feature.prayer.data.changelog

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Whether this device shares the prayer times its user sets by hand.
 *
 * On unless the user says otherwise, and what it governs is the *recording*, not just the sending:
 * a device that is not sharing writes nothing down, so there is never a store of someone's
 * corrections waiting for them to change their mind. Turning it off also drops whatever has not
 * been sent yet — see [PrayerTimeChangeLog.forgetPending].
 *
 * Times already sent stay where they are; this says what happens from now on, not what should be
 * unsaid.
 */
object PrayerTimeSharingPrefs {

    private const val PREFS = "prayer_time_sharing"
    private const val KEY   = "enabled"

    private val _isOn = MutableStateFlow(true)
    val isOn: StateFlow<Boolean> = _isOn.asStateFlow()

    /** Load the persisted choice into memory. Call once from App.onCreate. */
    fun init(context: Context) {
        _isOn.value = prefs(context).getBoolean(KEY, true)
    }

    fun set(context: Context, on: Boolean) {
        prefs(context).edit { putBoolean(KEY, on) }
        _isOn.value = on
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
