package com.lhacenmed.khatmah.ui.page.settings.appearance

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lhacenmed.khatmah.R
import com.lhacenmed.khatmah.ui.common.Route
import com.lhacenmed.khatmah.ui.component.LargeTopAppBar
import com.lhacenmed.khatmah.ui.component.SingleChoiceItem
import com.lhacenmed.khatmah.ui.nav.LocalNavController
import com.lhacenmed.khatmah.ui.nav.NavPage
import com.lhacenmed.khatmah.util.ThemeManager

/**
 * Theme settings sub-page.
 * Owns its Scaffold + LargeTopAppBar; animates as a complete screen alongside the main shell.
 * Append ThemeSettingsPage to the pages list in AppEntry to register it.
 */
@OptIn(ExperimentalMaterial3Api::class)
val ThemeSettingsPage = NavPage(route = Route.THEME_SETTINGS) {
    val nav            = LocalNavController.current
    val context        = LocalContext.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    val options = listOf(
        AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM to R.string.theme_system,
        AppCompatDelegate.MODE_NIGHT_NO            to R.string.theme_light,
        AppCompatDelegate.MODE_NIGHT_YES           to R.string.theme_dark,
    )

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar   = {
            LargeTopAppBar(
                title          = { Text(stringResource(R.string.theme_settings)) },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(
                            imageVector        = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.navigate_up),
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(vertical = 8.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            options.forEach { (mode, labelRes) ->
                SingleChoiceItem(
                    label    = stringResource(labelRes),
                    selected = ThemeManager.getMode(context) == mode,
                    onClick  = { ThemeManager.setMode(context, mode) },
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp))
            }
        }
    }
}