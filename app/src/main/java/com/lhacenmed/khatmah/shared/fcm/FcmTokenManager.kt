package com.lhacenmed.khatmah.shared.fcm

import android.content.Context
import com.google.firebase.messaging.FirebaseMessaging
import com.lhacenmed.khatmah.shared.supabase.SupabaseClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import androidx.core.content.edit

/**
 * Fetches the FCM token and upserts it into Supabase push_tokens.
 * Call [init] once from App.onCreate and after successful onboarding.
 */
object FcmTokenManager {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Fetches current token and uploads it. Safe to call multiple times. */
    fun init(context: Context) {
        FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
            upload(context, token)
        }
    }

    /** Called by [KhatmahFcmService.onNewToken] on token refresh. */
    fun upload(context: Context, token: String) {
        val deviceId = getDeviceId(context)
        scope.launch {
            runCatching {
                SupabaseClient.upsertPushToken(token, deviceId)
            }.onFailure {
                // Non-fatal: notification delivery degrades gracefully
            }
        }
    }

    private fun getDeviceId(context: Context): String {
        val prefs = context.getSharedPreferences("fcm_prefs", Context.MODE_PRIVATE)
        return prefs.getString("device_id", null) ?: run {
            val id = java.util.UUID.randomUUID().toString()
            prefs.edit { putString("device_id", id) }
            id
        }
    }
}