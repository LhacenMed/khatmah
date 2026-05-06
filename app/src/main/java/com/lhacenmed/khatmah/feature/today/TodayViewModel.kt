package com.lhacenmed.khatmah.feature.today

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.lhacenmed.khatmah.feature.khatmah.data.KhatmahEntity
import com.lhacenmed.khatmah.feature.khatmah.data.KhatmahRepository
import com.lhacenmed.khatmah.feature.khatmah.data.KhatmahSessionEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class TodayViewModel(private val repo: KhatmahRepository) : ViewModel() {

    data class SessionUi(
        val entity:        KhatmahSessionEntity,
        val startSuraName: String,
        val endSuraName:   String,
        val juzNum:        Int,
        val firstAyaText:  String,
    )

    sealed class UiState {
        object Loading   : UiState()
        object NoKhatmah : UiState()
        data class AllRead(val totalDays: Int) : UiState()
        data class Active(
            val session:   SessionUi,
            val khatmah:   KhatmahEntity,
            val readCount: Int,
        ) : UiState()
    }

    private val _state = MutableStateFlow<UiState>(UiState.Loading)
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repo.activeKhatmahFlow()
                .flatMapLatest { khatmah ->
                    if (khatmah == null) return@flatMapLatest flowOf(UiState.NoKhatmah)
                    combine(
                        repo.currentSession(khatmah.id),
                        repo.readCount(khatmah.id),
                    ) { session, readCount ->
                        if (session == null) {
                            UiState.AllRead(totalDays = khatmah.totalDays)
                        } else {
                            val meta = repo.sessionMeta(
                                session.startSura,
                                session.startAya,
                                session.endSura,
                            )
                            UiState.Active(
                                session = SessionUi(
                                    entity        = session,
                                    startSuraName = meta.startSuraName,
                                    endSuraName   = meta.endSuraName,
                                    juzNum        = meta.juzNum,
                                    firstAyaText  = meta.firstAyaText,
                                ),
                                khatmah   = khatmah,
                                readCount = readCount,
                            )
                        }
                    }
                }
                .collect { _state.value = it }
        }
    }

    fun markRead(id: Long) {
        viewModelScope.launch { repo.markSessionRead(id) }
    }

    class Factory(private val ctx: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            TodayViewModel(KhatmahRepository(ctx)) as T
    }
}