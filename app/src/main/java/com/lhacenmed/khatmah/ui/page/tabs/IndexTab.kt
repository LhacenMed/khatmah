package com.lhacenmed.khatmah.ui.page.tabs

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.lhacenmed.khatmah.R
import com.lhacenmed.khatmah.ui.common.Route
import com.lhacenmed.khatmah.ui.nav.NavScreen

val IndexTab = NavScreen(
    route    = Route.INDEX,
    iconRes  = R.drawable.ic_settings,
    labelRes = R.string.index,
) { padding -> IndexScreen(padding) }

@Composable
private fun IndexScreen(padding: PaddingValues) {
    Box(
        modifier         = Modifier.fillMaxSize().padding(padding),
        contentAlignment = Alignment.Center,
    ) {
        Text(stringResource(R.string.index))
    }
}