package com.lhacenmed.khatmah.feature.trips.data

/**
 * Mirrors the public.trip_requests table columns used in the admin list view.
 */
data class TripRequest(
    val id            : String,
    val fullName      : String,
    val phone         : String,
    val email         : String?,
    val establishment : String?,
    val destination   : String,   // "amci" | "ambassade" | "both"
    val reasonKey     : String,
    val description   : String?,
    val status        : String,   // "pending" | "confirmed" | "rejected" | "done"
    val adminNote     : String?,
    val createdAt     : String,
) {
    val destinationLabel: String get() = when (destination) {
        "amci"      -> "AMCI"
        "ambassade" -> "Ambassade"
        "both"      -> "AMCI + Ambassade"
        else        -> destination
    }

    val statusColor: StatusColor get() = when (status) {
        "confirmed" -> StatusColor.GREEN
        "rejected"  -> StatusColor.RED
        "done"      -> StatusColor.BLUE
        else        -> StatusColor.ORANGE   // pending
    }

    companion object {
        fun fromMap(m: Map<String, Any?>): TripRequest = TripRequest(
            id            = m["id"]            as? String ?: "",
            fullName      = m["full_name"]      as? String ?: "",
            phone         = m["phone"]          as? String ?: "",
            email         = m["email"]          as? String,
            establishment = m["establishment"]  as? String,
            destination   = m["destination"]    as? String ?: "",
            reasonKey     = m["reason_key"]     as? String ?: "",
            description   = m["description"]    as? String,
            status        = m["status"]         as? String ?: "pending",
            adminNote     = m["admin_note"]     as? String,
            createdAt     = m["created_at"]     as? String ?: "",
        )
    }
}

enum class StatusColor { GREEN, RED, BLUE, ORANGE }