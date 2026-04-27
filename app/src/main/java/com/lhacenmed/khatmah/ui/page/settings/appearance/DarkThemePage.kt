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
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import com.lhacenmed.khatmah.R
import com.lhacenmed.khatmah.ui.common.Route
import com.lhacenmed.khatmah.ui.component.LargeTopAppBar
import com.lhacenmed.khatmah.ui.component.PreferenceSubtitle
import com.lhacenmed.khatmah.ui.component.PreferenceSwitch
import com.lhacenmed.khatmah.ui.component.SingleChoiceItem
import com.lhacenmed.khatmah.ui.component.IconButton
import com.lhacenmed.khatmah.ui.nav.LocalNavController
import com.lhacenmed.khatmah.ui.nav.NavPage
import com.lhacenmed.khatmah.util.ThemeManager

@OptIn(ExperimentalMaterial3Api::class)
val DarkThemePage = NavPage(route = Route.DARK_THEME) {
    val nav            = LocalNavController.current
    val context        = LocalContext.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    val themeOptions = listOf(
        AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM to R.string.theme_system,
        AppCompatDelegate.MODE_NIGHT_NO            to R.string.theme_light,
        AppCompatDelegate.MODE_NIGHT_YES           to R.string.theme_dark,
    )

    val isHighContrast by ThemeManager.highContrast.collectAsState()
    val currentMode    = ThemeManager.getMode(context)

    // Tooltip anchor tracks the bar's actual height
    val tooltipAnchorBottom = lerp(20.dp, 55.dp, scrollBehavior.state.collapsedFraction)

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar   = {
            LargeTopAppBar(
                title          = { Text(stringResource(R.string.theme_dark)) },
                navigationIcon = {
                    IconButton(
                        onClick           = { nav.popBackStack() },
                        tooltipText       = stringResource(R.string.navigate_up),
                        anchorExtraBottom = tooltipAnchorBottom,
                    ) {
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
                .verticalScroll(rememberScrollState()),
        ) {
            themeOptions.forEach { (mode, labelRes) ->
                SingleChoiceItem(
                    label    = stringResource(labelRes),
                    selected = currentMode == mode,
                    onClick  = { ThemeManager.setMode(context, mode) },
                )
            }

            PreferenceSubtitle(text = stringResource(R.string.theme_dark_settings_group))

            PreferenceSwitch(
                title     = stringResource(R.string.theme_high_contrast),
                isChecked = isHighContrast,
                onClick   = { ThemeManager.setHighContrastEnabled(context, !isHighContrast) }
            )
        }
    }
}