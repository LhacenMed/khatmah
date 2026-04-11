package com.lhacenmed.khatmah.ui.page.tabs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lhacenmed.khatmah.R
import com.lhacenmed.khatmah.ui.common.Route
import com.lhacenmed.khatmah.ui.nav.LocalNavController
import com.lhacenmed.khatmah.ui.nav.NavScreen

val TodayTab = NavScreen(
    route    = Route.TODAY,
    iconRes  = R.drawable.ic_home,
    labelRes = R.string.today,
) { padding -> TodayScreen(padding) }

@Composable
private fun TodayScreen(padding: PaddingValues) {
    val nav = LocalNavController.current
    Box(
        modifier         = Modifier.fillMaxSize().padding(padding),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(stringResource(R.string.today))
            TextButton(onClick = { nav.navigate(Route.THEME_SETTINGS) }) {
                Text(stringResource(R.string.theme_settings))
            }
            TextButton(onClick = { nav.navigate(Route.LANGUAGE) }) {
                Text(stringResource(R.string.language_settings))
            }
        }
    }
}