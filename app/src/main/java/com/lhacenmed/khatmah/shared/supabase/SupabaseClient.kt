package com.lhacenmed.khatmah.shared.supabase

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Minimal HTTP client for Supabase REST API.
 * Uses only the Android SDK (no third-party HTTP lib needed).
 */
object SupabaseClient {

    private const val URL_BASE = "https://dzxvjkhswkamaxbuibwi.supabase.co/rest/v1"
    private const val ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImR6eHZqa2hzd2thbWF4YnVpYndpIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NzEwMDI0OTksImV4cCI6MjA4NjU3ODQ5OX0.lesPzJbUx3M7QzvyySW8Y2xyxCnZnPnQHrOMXWy8ViM"

    // ── Trip Requests ──────────────────────────────────────────────────────────

    suspend fun fetchTripRequests(): List<Map<String, Any?>> = withContext(Dispatchers.IO) {
        get("$URL_BASE/trip_requests?select=*&order=created_at.desc&deleted_by_user=eq.false")
    }

    // ── Push tokens ────────────────────────────────────────────────────────────

    suspend fun upsertPushToken(token: String, deviceId: String) = withContext(Dispatchers.IO) {
        val body = JSONObject().apply {
            put("token",     token)
            put("device_id", deviceId)
        }.toString()
        post(
            url          = "$URL_BASE/push_tokens",
            body         = body,
            extraHeaders = mapOf(
                "Prefer"      to "resolution=merge-duplicates,return=minimal",
                "on-conflict" to "token",
            ),
        )
    }

    // ── HTTP helpers ───────────────────────────────────────────────────────────

    private fun get(url: String): List<Map<String, Any?>> {
        val conn = openConn(url, "GET")
        val code = conn.responseCode
        if (code != 200) {
            val err = runCatching { conn.errorStream?.bufferedReader()?.readText() }.getOrNull()
            conn.disconnect()
            throw RuntimeException("GET $url → $code: $err")
        }
        val json = conn.inputStream.bufferedReader().readText()
        conn.disconnect()
        return parseArray(json)
    }

    private fun post(url: String, body: String, extraHeaders: Map<String, String> = emptyMap()) {
        val conn = openConn(url, "POST")
        extraHeaders.forEach { (k, v) -> conn.setRequestProperty(k, v) }
        conn.outputStream.use { it.write(body.toByteArray()) }
        val code = conn.responseCode
        conn.disconnect()
        if (code !in 200..299) throw RuntimeException("POST $url → $code")
    }

    private fun openConn(url: String, method: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            setRequestProperty("apikey",        ANON_KEY)
            setRequestProperty("Authorization", "Bearer $ANON_KEY")
            setRequestProperty("Content-Type",  "application/json")
            setRequestProperty("Accept",        "application/json")
            if (method == "POST") doOutput = true
            connectTimeout = 10_000
            readTimeout    = 15_000
        }

    private fun parseArray(json: String): List<Map<String, Any?>> {
        if (json.isBlank() || json == "[]") return emptyList()
        val arr = JSONArray(json)
        return (0 until arr.length()).map { i ->
            val obj = arr.getJSONObject(i)
            obj.keys().asSequence().associateWith { key ->
                if (obj.isNull(key)) null else obj.get(key)
            }
        }
    }
}