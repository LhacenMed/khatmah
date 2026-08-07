package com.lhacenmed.khatmah.feature.update

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Whether a found update announces itself.
 *
 * This governs the prompt and nothing else. The check still runs, the manifest is still saved and a
 * staged APK still resumes — turning it off only means the app waits to be asked rather than
 * interrupting on launch, which is what makes the manual check on the More tab worth having.
 *
 * Shares [UpdateStore]'s preferences file: same feature, same store, one instance between them.
 */
object UpdatePrefs {

    private const val KEY_AUTO_PROMPT = "auto_prompt"

    private val _autoPrompt = MutableStateFlow(true)

    /** True while a found update is allowed to raise its dialog on its own. Default: it is. */
    val autoPrompt: StateFlow<Boolean> = _autoPrompt.asStateFlow()

    /** Seeds the flow from storage. Called once per process, before anything can read it. */
    fun init(context: Context) {
        _autoPrompt.value = UpdateStore.prefs(context).getBoolean(KEY_AUTO_PROMPT, true)
    }

    fun setAutoPrompt(context: Context, enabled: Boolean) {
        UpdateStore.prefs(context).edit { putBoolean(KEY_AUTO_PROMPT, enabled) }
        _autoPrompt.value = enabled
    }
}
