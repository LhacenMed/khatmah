package com.lhacenmed.khatmah.core.ui

import android.content.Context
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import androidx.appcompat.widget.AppCompatTextView
import com.google.android.material.theme.MaterialComponentsViewInflater

/** The `android:` XML namespace, for reading attributes off an unresolved [AttributeSet]. */
private const val ANDROID_NS = "http://schemas.android.com/apk/res/android"

/**
 * Leaves the system's long-press tooltips on the platform font while the rest of the app keeps the
 * theme's family.
 *
 * The app sets `android:fontFamily` on the theme, and the theme is the *last* place a TextView
 * looks when resolving its typeface — later than the `textAppearance` its layout declares. A
 * tooltip is an ordinary TextView, inflated through the activity's own inflater, so that fallback
 * reaches it along with everything else. No style can opt it out: the layout belongs to the
 * framework, and by the time it is inflated there is nothing of ours in the chain — except this,
 * the class AppCompat asks to build every view (`viewInflaterClass`).
 *
 * Extending Material's inflater rather than AppCompat's keeps the widget substitutions the Material
 * theme depends on (MaterialButton, MaterialCheckBox and friends); only the tooltip is diverted.
 */
class TooltipFontViewInflater : MaterialComponentsViewInflater() {

    override fun createTextView(context: Context, attrs: AttributeSet): AppCompatTextView =
        super.createTextView(context, attrs).apply {
            // Set after construction, so it lands after the theme fallback the constructor applied.
            if (attrs.isTooltipMessage()) typeface = Typeface.DEFAULT
        }
}

/**
 * Whether these attributes are the message view of a tooltip — the only TextView the app hands back
 * to the platform font.
 *
 * Each tooltip is recognised by what its own layout declares, so the test is two integer compares on
 * attributes already parsed:
 *
 * - **API 26+** uses the framework's `tooltip.xml`, whose message view carries the public
 *   `@android:id/message`. The app builds its dialogs with Material, never the platform's, so no
 *   other view it inflates claims that id.
 * - **API 24–25** falls back to AppCompat's `abc_tooltip.xml`, whose message view declares the
 *   public `TextAppearance.AppCompat.Tooltip` — an appearance nothing else uses, which keeps it
 *   clear of the dialog message view sharing AppCompat's `message` id.
 */
private fun AttributeSet.isTooltipMessage(): Boolean =
    getAttributeResourceValue(ANDROID_NS, "id", View.NO_ID) == android.R.id.message ||
        getAttributeResourceValue(ANDROID_NS, "textAppearance", View.NO_ID) ==
        androidx.appcompat.R.style.TextAppearance_AppCompat_Tooltip
