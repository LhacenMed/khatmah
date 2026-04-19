package com.lhacenmed.khatmah.widget

/**
 * Intent contract between [PrayerWidget] and [MainActivity].
 *
 * Keeping these constants here (rather than in [MainActivity] or [Route]) ensures
 * the widget package owns its own protocol and neither the UI nor the data layer
 * needs to depend on this detail.
 */
internal object WidgetAction {

    /**
     * Action fired by [PrayerWidget] when the user taps the widget.
     * Received by [MainActivity] in [onCreate] / [onNewIntent].
     */
    const val OPEN_PRAYERS = "com.lhacenmed.khatmah.action.OPEN_PRAYERS"
}