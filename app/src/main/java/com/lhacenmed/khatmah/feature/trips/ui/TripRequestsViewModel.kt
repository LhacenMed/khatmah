package com.lhacenmed.khatmah.feature.trips.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lhacenmed.khatmah.feature.trips.data.TripRequest
import com.lhacenmed.khatmah.feature.trips.data.TripRequestRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface TripRequestsState {
    object Loading : TripRequestsState
    data class Success(val items: List<TripRequest>) : TripRequestsState
    data class Error(val message: String)            : TripRequestsState
}

class TripRequestsViewModel : ViewModel() {

    private val repo = TripRequestRepository()

    private val _state = MutableStateFlow<TripRequestsState>(TripRequestsState.Loading)
    val state = _state.asStateFlow()

    init { load() }

    fun refresh() = load()

    private fun load() {
        viewModelScope.launch {
            _state.value = TripRequestsState.Loading
            _state.value = repo.getAll().fold(
                onSuccess = { TripRequestsState.Success(it) },
                onFailure = { TripRequestsState.Error(it.message ?: "Unknown error") },
            )
        }
    }
}