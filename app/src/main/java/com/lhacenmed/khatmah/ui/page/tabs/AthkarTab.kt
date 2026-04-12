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

val AthkarTab = NavScreen(
    route    = Route.ATHKAR,
    iconRes  = R.drawable.ic_athkar,
    labelRes = R.string.athkar,
) { padding -> AthkarScreen(padding) }

@Composable
private fun AthkarScreen(padding: PaddingValues) {
    Box(
        modifier         = Modifier.fillMaxSize().padding(padding),
        contentAlignment = Alignment.Center,
    ) {
        Text(stringResource(R.string.athkar))
    }
}