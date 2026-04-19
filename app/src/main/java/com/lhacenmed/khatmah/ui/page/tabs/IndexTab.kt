package com.lhacenmed.khatmah.ui.page.tabs

import com.lhacenmed.khatmah.R
import com.lhacenmed.khatmah.ui.common.Route
import com.lhacenmed.khatmah.ui.nav.NavScreen
import com.lhacenmed.khatmah.ui.page.quran.QuranReaderScreen

val IndexTab = NavScreen(
    route    = Route.INDEX,
    iconRes  = R.drawable.ic_list,
    labelRes = R.string.index,
) { padding -> QuranReaderScreen(padding) }
