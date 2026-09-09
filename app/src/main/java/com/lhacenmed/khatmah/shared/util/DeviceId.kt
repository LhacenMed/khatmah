package com.lhacenmed.khatmah.shared.util

import android.content.Context
import androidx.core.content.edit
import java.util.UUID

/**
 * The id this install is known by on the server.
 *
 * One id for everything the device sends, so a row in one table can be read against a row in
 * another — a prayer time someone changed and the push token they are reachable at are the same
 * person, and only this says so.
 *
 * Minted once and kept where [com.lhacenmed.khatmah.shared.fcm.FcmTokenManager] has always kept
 * it, so installs that already registered a token keep the id they registered it under.
 */
object DeviceId {

    private const val PREFS = "fcm_prefs"
    private const val KEY   = "device_id"

    fun of(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.getString(KEY, null) ?: UUID.randomUUID().toString().also {
            prefs.edit { putString(KEY, it) }
        }
    }
}
