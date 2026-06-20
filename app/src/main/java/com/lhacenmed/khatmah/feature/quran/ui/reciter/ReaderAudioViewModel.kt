package com.lhacenmed.khatmah.feature.quran.ui.reciter

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.lhacenmed.khatmah.feature.audio.BookAudioController

/**
 * Retains the reader's [BookAudioController] across configuration changes (e.g. rotation), so
 * recitation keeps playing when the device turns to landscape. The activity is recreated, but this
 * ViewModel — and its live MediaPlayer + playback state — survive; [onCleared] releases the player
 * only when the reader is actually finishing.
 */
class ReaderAudioViewModel(app: Application) : AndroidViewModel(app) {

    val controller = BookAudioController(app)

    override fun onCleared() {
        controller.release()
    }
}
