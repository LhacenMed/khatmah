package com.lhacenmed.khatmah.feature.update

import org.json.JSONObject

/**
 * Parsed remote version manifest (version.json). [versionCode] is compared against the installed
 * `BuildConfig.VERSION_CODE` to decide whether a newer build exists; [apkUrl] points at the
 * GitHub Releases asset to download.
 *
 * Manifest shape:
 * ```json
 * { "versionCode": 10001, "versionName": "1.0.1",
 *   "apkUrl": "https://github.com/.../releases/download/v1.0.1/khatmah-1.0.1.apk",
 *   "notes": "What's new…" }
 * ```
 */
data class AppUpdate(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
    val notes: String,
) {
    /** Serializes back to the manifest shape so [UpdateStore] can persist it across launches. */
    fun toJson(): String = JSONObject()
        .put("versionCode", versionCode)
        .put("versionName", versionName)
        .put("apkUrl", apkUrl)
        .put("notes", notes)
        .toString()

    companion object {
        fun fromJson(json: String): AppUpdate = JSONObject(json).run {
            AppUpdate(
                versionCode = getInt("versionCode"),
                versionName = getString("versionName"),
                apkUrl      = getString("apkUrl"),
                notes       = optString("notes"),
            )
        }
    }
}
