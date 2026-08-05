package com.lhacenmed.khatmah.feature.settings

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import androidx.core.view.isVisible
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import com.google.android.material.color.MaterialColors
import com.lhacenmed.khatmah.R
import com.lhacenmed.khatmah.core.ui.theme.paletteColor
import com.lhacenmed.khatmah.core.ui.theme.paletteOverlays
import com.google.android.material.R as MaterialR

/**
 * The palette picker: every palette shown in its own colour, with the active one ringed.
 *
 * Each swatch is painted from the palette's own theme overlay rather than from a value repeated
 * here, so a swatch always shows exactly what choosing it will do — and adding a palette to
 * `paletteOverlays` adds a swatch with nothing else to change.
 *
 * [selected] is -1 when the device's dynamic colours are in force, which is how no swatch ends up
 * ringed while Material You is on.
 */
class PalettePreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : Preference(context, attrs) {

    init {
        layoutResource = R.layout.pref_palette
        // The swatches take the taps; the row around them is not a target of its own.
        isSelectable = false
    }

    var selected: Int = -1
        set(value) {
            if (field == value) return
            field = value
            notifyChanged()
        }

    var onSelect: ((index: Int) -> Unit)? = null

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        val row = holder.findViewById(R.id.swatches) as? LinearLayout ?: return
        val night = (context.resources.configuration.uiMode and
                android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES

        // Rebuilt rather than diffed: five swatches cost nothing to lay out, and rebuilding keeps
        // the row honest when a palette is added.
        row.removeAllViews()
        val inflater = LayoutInflater.from(context)
        val ringColor = MaterialColors.getColor(context, MaterialR.attr.colorOnSurface, Color.GRAY)

        paletteOverlays.indices.forEach { index ->
            val swatch = inflater.inflate(R.layout.pref_swatch, row, false)
            swatch.findViewById<View>(R.id.swatch).background = circle(paletteColor(context, index, night))
            swatch.findViewById<View>(R.id.ring).apply {
                isVisible = index == selected
                background = ring(ringColor)
            }
            swatch.setOnClickListener { onSelect?.invoke(index) }
            row.addView(swatch)
        }
    }

    private fun circle(color: Int) = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(color)
    }

    private fun ring(color: Int) = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(Color.TRANSPARENT)
        setStroke((RING_WIDTH_DP * context.resources.displayMetrics.density).toInt(), color)
    }

    private companion object {
        const val RING_WIDTH_DP = 2f
    }
}
