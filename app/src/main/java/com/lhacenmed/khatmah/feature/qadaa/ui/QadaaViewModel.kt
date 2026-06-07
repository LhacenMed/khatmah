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
    val streak: Int = 0,
)

@RequiresApi(Build.VERSION_CODES.O)
class QadaaViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = QadaaRepository(app)

    private val todayStartMs: Long = LocalDate.now()
        .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    /** Emitted when the top-bar "Add" button is tapped from MainScreen. */
    private val _addPrayersEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val addPrayersEvent: SharedFlow<Unit> = _addPrayersEvent.asSharedFlow()

    val uiState: StateFlow<QadaaUiState> = combine(
        repo.prayerDebts(),
        repo.activeFastDebts(),
        repo.todayPrayersDone(todayStartMs),
        QadaaPrefs.dailyGoal,
        repo.streaks(QadaaPrefs.dailyGoal),
    ) { args: Array<Any> ->
        @Suppress("UNCHECKED_CAST")
        val prayers = args[0] as List<PrayerDebt>
        @Suppress("UNCHECKED_CAST")
        val fasts = args[1] as List<FastDebt>
        val todayDone = args[2] as Int
        val goal = args[3] as Int
        @Suppress("UNCHECKED_CAST")
        val streaks = args[4] as Pair<Int, Int>

        QadaaUiState(
            prayers               = prayers,
            fasts                 = fasts,
            totalPrayersRemaining = prayers.sumOf { it.remaining },
            totalFastsRemaining   = fasts.sumOf { it.remaining },
            todayDone             = todayDone,
            dailyGoal             = goal,
            streak                = streaks.first,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), QadaaUiState())

    fun requestAddPrayers() = viewModelScope.launch { _addPrayersEvent.emit(Unit) }

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