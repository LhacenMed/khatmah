package com.lhacenmed.khatmah.core.ui.color

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import androidx.annotation.ColorInt
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.lhacenmed.khatmah.R

/**
 * HSV colour picker dialog.
 *
 * Hue, saturation and value are the single source of truth; the field, the slider, the preview
 * swatch and the hex box are all views onto them. Every edit routes through [apply], so the four
 * can never disagree — including hex, which writes back into HSV rather than keeping a colour of
 * its own. [onPicked] fires only on confirm, so dragging never commits anything.
 */
fun showColorPicker(
    context: Context,
    @ColorInt initialColor: Int,
    onPicked: (Int) -> Unit,
) {
    val view = LayoutInflater.from(context).inflate(R.layout.color_picker_dialog, null)
    val satVal: SatValView         = view.findViewById(R.id.sat_val)
    val hueSlider: HueSliderView   = view.findViewById(R.id.hue)
    val swatchOld: View            = view.findViewById(R.id.swatch_old)
    val swatchNew: View            = view.findViewById(R.id.swatch_new)
    val hexLayout: TextInputLayout = view.findViewById(R.id.hex_layout)
    val hex: TextInputEditText     = view.findViewById(R.id.hex)

    val hsv = FloatArray(3).also { Color.colorToHSV(initialColor, it) }
    var picked = initialColor

    swatchOld.background = swatch(initialColor)
    swatchNew.background = swatch(initialColor)

    // Guards the hex box against being rewritten under the cursor by its own edit.
    var isSyncingHex = false

    fun apply(updateHex: Boolean) {
        picked = Color.HSVToColor(hsv)
        satVal.hue = hsv[0]
        satVal.saturation = hsv[1]
        satVal.value = hsv[2]
        hueSlider.hue = hsv[0]
        swatchNew.background = swatch(picked)
        if (updateHex) {
            isSyncingHex = true
            hex.setText(String.format("%06X", picked and 0xFFFFFF))
            isSyncingHex = false
        }
    }

    satVal.onChange = { saturation, value ->
        hsv[1] = saturation
        hsv[2] = value
        apply(updateHex = true)
    }
    hueSlider.onChange = { hue ->
        hsv[0] = hue
        apply(updateHex = true)
    }

    hex.addTextChangedListener(object : TextWatcher {
        override fun afterTextChanged(s: Editable?) {
            if (isSyncingHex) return
            val text = s?.toString().orEmpty()
            val parsed = runCatching { Color.parseColor("#$text") }.getOrNull()
                .takeIf { text.length == HEX_LENGTH }
            hexLayout.error = if (parsed == null && text.isNotEmpty()) " " else null
            parsed ?: return
            Color.colorToHSV(parsed, hsv)
            // The hex box is the origin of this change, so it is left exactly as typed.
            apply(updateHex = false)
        }

        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
    })

    apply(updateHex = true)

    MaterialAlertDialogBuilder(context)
        .setTitle(R.string.color_picker_title)
        .setView(view)
        .setPositiveButton(R.string.ok) { _, _ -> onPicked(picked) }
        .setNegativeButton(R.string.cancel, null)
        .show()
}

/** Circular swatch, built in code so it can carry an arbitrary fill. */
private fun swatch(@ColorInt color: Int) = GradientDrawable().apply {
    shape = GradientDrawable.OVAL
    setColor(color)
}

private const val HEX_LENGTH = 6
