package com.lhacenmed.khatmah.shared.util

import android.content.Context

/**
 * Lists available adhan sound files from assets/adhan/.
 * Adding a new sound: drop the .mp3 into assets/adhan/ — zero code changes.
 */
object AdhanSoundFiles {

    /** Returns all adhan mp3 filenames sorted alphabetically. */
    fun list(context: Context): List<String> =
        context.assets.list("adhan")
            ?.filter { it.endsWith(".mp3") }
            ?.sorted()
            ?: emptyList()
}