package com.lhacenmed.khatmah.feature.more

import com.lhacenmed.khatmah.R
import com.lhacenmed.khatmah.core.nav.AppTab

/**
 * Everything outside the four reading tabs. Native throughout — [MoreTabFragment] builds it on the
 * androidx Preference framework, the same one the reader's settings use.
 */
object MoreTab : AppTab(
    iconRes  = R.drawable.ic_profile,
    titleRes = R.string.more,
    route    = "more",
) {
    override fun newFragment() = MoreTabFragment()
}
