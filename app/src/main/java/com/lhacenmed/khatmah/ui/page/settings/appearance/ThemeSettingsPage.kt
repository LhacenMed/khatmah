package com.lhacenmed.khatmah.ui.page.settings.appearance

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lhacenmed.khatmah.R
import com.lhacenmed.khatmah.ui.common.Route
import com.lhacenmed.khatmah.ui.component.AppTopBar
import com.lhacenmed.khatmah.ui.component.SingleChoiceItem
import com.lhacenmed.khatmah.ui.nav.LocalNavController
import com.lhacenmed.khatmah.ui.nav.NavPage
import com.lhacenmed.khatmah.util.ThemeManager

/**
 * Theme settings sub-page.
 * Owns its Scaffold + AppTopBar; animates as a complete screen alongside the main shell.
 * Append ThemeSettingsPage to the pages list in AppEntry to register it.
 */
val ThemeSettingsPage = NavPage(route = Route.THEME_SETTINGS) {
    val nav     = LocalNavController.current
    val context = LocalContext.current

    Scaffold(
        topBar = {
            AppTopBar(
                title      = stringResource(R.string.theme_settings),
                isTopLevel = false,
                onBack     = { nav.popBackStack() },
            )
        },
    ) { padding ->
        ThemeSettingsContent(
            padding        = padding,
            currentMode    = ThemeManager.getMode(context),
            onModeSelected = { ThemeManager.setMode(context, it) },
        )
    }
}

@Composable
private fun ThemeSettingsContent(
    padding: PaddingValues,
    currentMode: Int,
    onModeSelected: (Int) -> Unit,
) {
    val options = listOf(
        AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM to R.string.theme_system,
        AppCompatDelegate.MODE_NIGHT_NO            to R.string.theme_light,
        AppCompatDelegate.MODE_NIGHT_YES           to R.string.theme_dark,
    )

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(vertical = 8.dp),
        ) {
            options.forEach { (mode, labelRes) ->
                SingleChoiceItem(
                    label    = stringResource(labelRes),
                    selected = currentMode == mode,
                    onClick  = { onModeSelected(mode) },
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp))
            }
        }
    }
}