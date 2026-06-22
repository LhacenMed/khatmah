package com.lhacenmed.khatmah.feature.quran.ui.reciter

import android.view.View
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import com.lhacenmed.khatmah.R
import com.lhacenmed.khatmah.feature.audio.AudioLoadState
import com.lhacenmed.khatmah.feature.audio.AyaAudioState
import com.lhacenmed.khatmah.feature.quran.ui.reader.toArNums

/**
 * Binds the reader's recitation bar (the overlay above the page slider) and reflects playback
 * state into it — visibility, progress, play/pause, and the reader/aya labels. Owns no controller;
 * the host wires the play/close clicks and feeds state via [render].
 */
class ReaderAudioBar(
    private val root: View,
    private val progress: ProgressBar,
    private val play: ImageButton,
    private val title: TextView,
    private val subtitle: TextView,
) {
    /** Reflects [st] into the bar. */
    fun render(st: AyaAudioState) {
        if (!st.active) { root.visibility = View.GONE; return }
        root.visibility = View.VISIBLE

        when (val ls = st.loadState) {
            AudioLoadState.Connecting, AudioLoadState.Idle -> progress.isIndeterminate = true
            is AudioLoadState.Downloading ->
                if (ls.progress < 0f) progress.isIndeterminate = true
                else { progress.isIndeterminate = false; progress.progress = (ls.progress * 1000).toInt() }
            AudioLoadState.Ready -> { progress.isIndeterminate = false; progress.progress = (st.progress * 1000).toInt() }
            is AudioLoadState.Error -> { progress.isIndeterminate = false; progress.progress = 1000 }
        }

        val ready = st.loadState is AudioLoadState.Ready
        play.isEnabled = ready
        play.alpha = if (ready) 1f else 0.4f
        play.setImageResource(if (st.isPlaying) R.drawable.ic_pause else R.drawable.ic_play_arrow)

        title.text = when (val ls = st.loadState) {
            AudioLoadState.Connecting     -> "جارٍ الاتصال…"
            is AudioLoadState.Downloading -> if (ls.progress >= 0f) "جارٍ التحميل… ${(ls.progress * 100).toInt()}٪" else "جارٍ التحميل…"
            is AudioLoadState.Error       -> ls.message
            else                          -> st.readerName
        }
        subtitle.text = if (st.ayaNum > 0) "آية ${toArNums(st.ayaNum)}" else ""
        subtitle.visibility = if (st.ayaNum > 0) View.VISIBLE else View.GONE
    }
}
