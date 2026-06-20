package com.lhacenmed.khatmah.feature.demo

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.lhacenmed.khatmah.R
import com.lhacenmed.khatmah.core.nav.AppTab
import com.lhacenmed.khatmah.core.nav.Dest
import com.lhacenmed.khatmah.core.nav.LocalNavigator

/**
 * Example tab. Its body has a single button that navigates to [Dest.DemoDetail]
 * (rendered by DemoDetailActivity) — showing the full tab → detail-screen flow.
 */
object DemoTab : AppTab(
    iconRes  = R.drawable.ic_list,
    titleRes = R.string.demo_tab,
    route    = "demo",
) {
    @Composable override fun Content(padding: PaddingValues) {
        val nav = LocalNavigator.current
        Box(
            modifier         = Modifier.fillMaxSize().padding(padding),
            contentAlignment = Alignment.Center,
        ) {
            Button(onClick = { nav.go(Dest.DemoDetail) }) {
                Text(stringResource(R.string.demo_open))
            }
        }
    }
}
