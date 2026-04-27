package com.lhacenmed.khatmah.ui.page.settings.appearance

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import com.lhacenmed.khatmah.R
import com.lhacenmed.khatmah.ui.common.Route
import com.lhacenmed.khatmah.ui.component.LargeTopAppBar
import com.lhacenmed.khatmah.ui.component.PreferenceItem
import com.lhacenmed.khatmah.ui.component.PreferenceSubtitle
import com.lhacenmed.khatmah.ui.component.PreferenceSwitch
import com.lhacenmed.khatmah.ui.component.IconButton
import com.lhacenmed.khatmah.ui.nav.LocalNavController
import com.lhacenmed.khatmah.ui.nav.NavPage
import com.lhacenmed.khatmah.ui.theme.colorPreferences
import com.lhacenmed.khatmah.util.ThemeManager

/**
 * Appearance settings page.
 */
@RequiresApi(Build.VERSION_CODES.O)
@OptIn(ExperimentalMaterial3Api::class)
val ThemeSettingsPage = NavPage(route = Route.THEME_SETTINGS) {
    val nav            = LocalNavController.current
    val context        = LocalContext.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    val isDynamicColor by ThemeManager.dynamicColor.collectAsState()
    val selectedColor  by ThemeManager.colorIndex.collectAsState()
    val themeMode      by ThemeManager.mode.collectAsState()

    // Tooltip anchor tracks the bar's actual height
    val tooltipAnchorBottom = lerp(20.dp, 55.dp, scrollBehavior.state.collapsedFraction)

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar   = {
            LargeTopAppBar(
                title          = { Text(stringResource(R.string.theme_settings)) },
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
            PreferenceSubtitle(text = stringResource(R.string.more_settings))

            PreferenceItem(
                title = stringResource(R.string.theme_dark),
                description = stringResource(
                    when (themeMode) {
                        AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM -> R.string.theme_system
                        AppCompatDelegate.MODE_NIGHT_NO            -> R.string.theme_light
                        else                                       -> R.string.theme_dark
                    }
                ),
                onClick = { nav.navigate(Route.DARK_THEME) }
            )

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PreferenceSwitch(
                    title     = stringResource(R.string.theme_dynamic_color),
                    description = stringResource(R.string.theme_dynamic_color_desc),
                    isChecked = isDynamicColor,
                    onClick   = { ThemeManager.setDynamicColorEnabled(context, !isDynamicColor) }
                )
            }

            PreferenceSubtitle(text = stringResource(R.string.theme_palette_group))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                colorPreferences.forEach { themeColor ->
                    val isSelected = !isDynamicColor && selectedColor == themeColor.index
                    val color = if (isSystemInDarkTheme()) themeColor.primaryDark else themeColor.primaryLight

                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(color)
                            .border(
                                width = 3.dp,
                                color = if (isSelected) MaterialTheme.colorScheme.outline else Color.Transparent,
                                shape = CircleShape
                            )
                            .clickable {
                                ThemeManager.setDynamicColorEnabled(context, false)
                                ThemeManager.setColorIndex(context, themeColor.index)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = if (isSystemInDarkTheme()) Color.Black else Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}