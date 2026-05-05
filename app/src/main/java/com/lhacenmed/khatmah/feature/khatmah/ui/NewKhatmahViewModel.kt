package com.lhacenmed.khatmah.feature.khatmah.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.lhacenmed.khatmah.feature.khatmah.data.KhatmahRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class NewKhatmahState(
    val step: Int = 1,
    val startJuz: Int = 1,
    val durationDays: Int = 30,
    val dailyAjza: Int = 1,
    val dailyArba: Int = 0,
    val isSaving: Boolean = false,
    val savedKhatmahId: Long? = null,
)

class NewKhatmahViewModel(private val repo: KhatmahRepository) : ViewModel() {

    private val _state = MutableStateFlow(NewKhatmahState())
    val state: StateFlow<NewKhatmahState> = _state.asStateFlow()

    // ── Step 1 ────────────────────────────────────────────────────────────────

    fun setStartJuz(juz: Int) { _state.update { it.copy(startJuz = juz) } }

    fun goToStep2() {
        val juz       = _state.value.startJuz
        val (ajza, arba) = repo.computeDailyAmount(juz, 30)
        val days      = repo.computeDays(juz, ajza, arba)
        _state.update { it.copy(step = 2, dailyAjza = ajza, dailyArba = arba, durationDays = days) }
    }

    // ── Step 2 ────────────────────────────────────────────────────────────────

    fun setDuration(days: Int) {
        val juz     = _state.value.startJuz
        val clamped = days.coerceIn(repo.minDays(juz), repo.maxDays(juz))
        val (ajza, arba) = repo.computeDailyAmount(juz, clamped)
        _state.update { it.copy(durationDays = clamped, dailyAjza = ajza, dailyArba = arba) }
    }

    fun setDailyAmount(ajza: Int, arba: Int) {
        if (ajza == 0 && arba == 0) return
        val days = repo.computeDays(_state.value.startJuz, ajza, arba)
        _state.update { it.copy(dailyAjza = ajza, dailyArba = arba, durationDays = days) }
    }

    fun goBack() { _state.update { it.copy(step = 1) } }

    fun save() {
        val s = _state.value
        if (s.isSaving) return
        _state.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            val id = repo.createKhatmah(s.startJuz, s.dailyAjza, s.dailyArba)
            _state.update { it.copy(isSaving = false, savedKhatmahId = id) }
        }
    }

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            NewKhatmahViewModel(KhatmahRepository(context)) as T
    }
}