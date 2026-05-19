package com.lhacenmed.khatmah.feature.qadaa.ui

import android.app.Application
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lhacenmed.khatmah.feature.qadaa.data.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

data class QadaaUiState(
    val prayers: List<PrayerDebt> = emptyList(),
    val fasts: List<FastDebt> = emptyList(),
    val totalPrayersRemaining: Int = 0,
    val totalFastsRemaining: Int = 0,
    val todayDone: Int = 0,
    val dailyGoal: Int = 5,
    val streak: Int = 0, // TODO: populate when streak feature is implemented
)

@RequiresApi(Build.VERSION_CODES.O)
class QadaaViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = QadaaRepository(app)

    private val todayStartMs: Long = LocalDate.now()
        .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    val uiState: StateFlow<QadaaUiState> = combine(
        repo.prayerDebts(),
        repo.activeFastDebts(),
        repo.todayPrayersDone(todayStartMs),
        QadaaPrefs.dailyGoal,
    ) { prayers, fasts, todayDone, goal ->
        QadaaUiState(
            prayers               = prayers,
            fasts                 = fasts,
            totalPrayersRemaining = prayers.sumOf { it.remaining },
            totalFastsRemaining   = fasts.sumOf { it.remaining },
            todayDone             = todayDone,
            dailyGoal             = goal,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), QadaaUiState())

    fun addPrayers(counts: Map<Prayer, Int>) =
        viewModelScope.launch { repo.addPrayers(counts) }

    fun addFasts(count: Int, reason: FastReason, label: String, ramadanYear: Int? = null) =
        viewModelScope.launch { repo.addFasts(count, reason, label, ramadanYear) }

    fun markPrayersDone(counts: Map<Prayer, Int>, note: String? = null, fullDay: Boolean = false) =
        viewModelScope.launch { repo.markPrayersDone(counts, note, fullDay) }

    fun markFastsDone(count: Int, fastId: Long, note: String? = null) =
        viewModelScope.launch { repo.markFastsDone(count, fastId, note) }

    fun setDailyGoal(goal: Int) =
        QadaaPrefs.setDailyGoal(getApplication(), goal)
}