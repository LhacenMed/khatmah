package com.lhacenmed.khatmah.feature.qadaa.data

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object QadaaPrefs {

    private const val PREFS_FILE     = "qadaa_prefs"
    private const val KEY_DAILY_GOAL = "daily_goal"

    private val _dailyGoal = MutableStateFlow(5)
    val dailyGoal: StateFlow<Int> = _dailyGoal.asStateFlow()

    fun init(context: Context) {
        _dailyGoal.value = context.applicationContext
            .getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)
            .getInt(KEY_DAILY_GOAL, 5)
    }

    fun setDailyGoal(context: Context, goal: Int) {
        context.applicationContext
            .getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)
            .edit { putInt(KEY_DAILY_GOAL, goal) }
        _dailyGoal.value = goal
    }
}