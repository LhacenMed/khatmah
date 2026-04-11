package com.lhacenmed.khatmah.ui.page.about

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lhacenmed.khatmah.R
import com.lhacenmed.khatmah.ui.common.Route
import com.lhacenmed.khatmah.ui.component.AppTopBar
import com.lhacenmed.khatmah.ui.nav.LocalNavController
import com.lhacenmed.khatmah.ui.nav.NavPage

/**
 * About sub-page.
 * Owns its Scaffold + AppTopBar; animates as a complete screen alongside the main shell.
 * Append AboutPage to the pages list in AppEntry to register it.
 */
val AboutPage = NavPage(route = Route.ABOUT) {
    val nav = LocalNavController.current

    Scaffold(
        topBar = {
            AppTopBar(
                title      = stringResource(R.string.about_page),
                isTopLevel = false,
                onBack     = { nav.popBackStack() },
            )
        },
    ) { padding ->
        AboutContent(padding = padding)
    }
}

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