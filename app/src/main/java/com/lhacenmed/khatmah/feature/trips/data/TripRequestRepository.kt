package com.lhacenmed.khatmah.feature.trips.data

import com.lhacenmed.khatmah.shared.supabase.SupabaseClient

class TripRequestRepository {
    suspend fun getAll(): Result<List<TripRequest>> = runCatching {
        SupabaseClient.fetchTripRequests().map(TripRequest::fromMap)
    }
}