package com.lhacenmed.khatmah.ui.page.about

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lhacenmed.khatmah.R
import com.lhacenmed.khatmah.ui.common.Route
import com.lhacenmed.khatmah.ui.component.SingleChoiceItem
import com.lhacenmed.khatmah.ui.nav.NavPage
import com.lhacenmed.khatmah.util.LocaleManager

/**
 * About settings sub-page.
 * Append AboutPage to the pages list in MainActivity to register it.
 * Route, TopAppBar title, and NavHost entry are all derived automatically.
 */
val AboutPage = NavPage(
    route    = Route.ABOUT,
    titleRes = R.string.about_page,
) { padding -> AboutContent(padding) }

@Composable
private fun AboutContent(padding: PaddingValues) {
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(vertical = 8.dp),
        ) {
            Text(text = "About Page")
        }
    }
}
