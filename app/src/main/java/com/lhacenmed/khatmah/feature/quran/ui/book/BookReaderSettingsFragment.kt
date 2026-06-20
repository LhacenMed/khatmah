package com.lhacenmed.khatmah.feature.quran.ui.book

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.lhacenmed.khatmah.R
import kotlin.math.ln1p

/**
 * Native reader settings — Quran Android's display + reading preferences for the QCF4 reader.
 *
 * Display: night mode (reader-only via [BookReaderTheme], default follows the app theme) and
 * night-mode text/background brightness — disabled while night mode is off. Reading: the
 * page-info overlay. Brightness uses Quran Android's formula; a live preview shows the result.
 */
class BookReaderSettingsFragment : Fragment(R.layout.book_reader_settings_fragment) {

    private lateinit var brightnessGroup: LinearLayout
    private lateinit var previewBox: FrameLayout
    private lateinit var previewText: TextView
    private lateinit var seekText: SeekBar
    private lateinit var seekBg: SeekBar
    private lateinit var valText: TextView
    private lateinit var valBg: TextView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val ctx = requireContext()

        brightnessGroup = view.findViewById(R.id.brightness_group)
        previewBox = view.findViewById(R.id.preview)
        previewText = view.findViewById(R.id.preview_text)
        seekText = view.findViewById(R.id.seek_text)
        seekBg = view.findViewById(R.id.seek_bg)
        valText = view.findViewById(R.id.val_text)
        valBg = view.findViewById(R.id.val_bg)

        // ── Night mode — reader-only override (default follows the app theme) ──
        val nightCheck = view.findViewById<CheckBox>(R.id.check_night)
        nightCheck.isChecked = BookReaderTheme.effectiveNight(ctx)
        setBrightnessEnabled(nightCheck.isChecked)
        view.findViewById<View>(R.id.row_night).setOnClickListener {
            // Affects only the rendered pages; this screen and the toolbar keep the app theme.
            BookReaderTheme.toggle(ctx)
            val night = BookReaderTheme.effectiveNight(ctx)
            nightCheck.isChecked = night // live, animated
            setBrightnessEnabled(night) // brightness only matters in night mode
        }

        // ── Brightness seek bars ──
        seekText.progress = BookReaderPrefs.textBrightness.value
        seekBg.progress = BookReaderPrefs.backgroundBrightness.value
        valText.text = seekText.progress.toString()
        valBg.text = seekBg.progress.toString()

        seekText.setOnSeekBarChangeListener(object : SimpleSeekListener() {
            override fun onProgressChanged(sb: SeekBar, value: Int, fromUser: Boolean) {
                valText.text = value.toString()
                BookReaderPrefs.setTextBrightness(ctx, value)
                updatePreview()
            }
        })
        seekBg.setOnSeekBarChangeListener(object : SimpleSeekListener() {
            override fun onProgressChanged(sb: SeekBar, value: Int, fromUser: Boolean) {
                valBg.text = value.toString()
                BookReaderPrefs.setBackgroundBrightness(ctx, value)
                updatePreview()
            }
        })

        // ── Page-info overlay ──
        val pageInfoCheck = view.findViewById<CheckBox>(R.id.check_page_info)
        pageInfoCheck.isChecked = BookReaderPrefs.showPageInfo.value
        view.findViewById<View>(R.id.row_page_info).setOnClickListener {
            val next = !pageInfoCheck.isChecked
            pageInfoCheck.isChecked = next
            BookReaderPrefs.setShowPageInfo(ctx, next)
        }

        updatePreview()
    }

    /** Dims and disables the brightness controls when night mode is off (they have no effect). */
    private fun setBrightnessEnabled(enabled: Boolean) {
        brightnessGroup.alpha = if (enabled) 1f else 0.38f
        seekText.isEnabled = enabled
        seekBg.isEnabled = enabled
    }

    /** Renders the preview with the same night-mode brightness formula as [BookPageView]. */
    private fun updatePreview() {
        val bg = seekBg.progress
        val adjusted = (50f * ln1p(bg.toDouble()).toFloat() + seekText.progress)
            .toInt().coerceAtMost(255)
        previewBox.setBackgroundColor(Color.rgb(bg, bg, bg))
        previewText.setTextColor(Color.argb(adjusted, 255, 255, 255))
    }

    /** SeekBar listener that only cares about progress changes. */
    private abstract class SimpleSeekListener : SeekBar.OnSeekBarChangeListener {
        override fun onStartTrackingTouch(seekBar: SeekBar) {}
        override fun onStopTrackingTouch(seekBar: SeekBar) {}
    }
}
