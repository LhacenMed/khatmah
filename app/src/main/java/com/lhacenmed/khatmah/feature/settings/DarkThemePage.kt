package com.lhacenmed.khatmah.feature.settings

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.RadioButton
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.lhacenmed.khatmah.R
import com.lhacenmed.khatmah.core.ui.components.PreferenceItem
import com.lhacenmed.khatmah.core.ui.components.PreferenceSubtitle
import com.lhacenmed.khatmah.core.ui.components.PreferenceSwitch
import com.lhacenmed.khatmah.shared.util.ThemeManager

// Body only — the title + back arrow come from ScreenHostActivity (see Dest.DarkTheme.titleRes).
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DarkThemeScreen() {
    val context = LocalContext.current

    val themeOptions = listOf(
        AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM to R.string.theme_system,
        AppCompatDelegate.MODE_NIGHT_NO to R.string.theme_light,
        AppCompatDelegate.MODE_NIGHT_YES to R.string.theme_dark,
    )

    val isHighContrast by ThemeManager.highContrast.collectAsState()
    val currentMode = ThemeManager.getMode(context)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        themeOptions.forEach { (mode, labelRes) ->
            PreferenceItem(
                title = stringResource(labelRes),
                onClick = { ThemeManager.setMode(context, mode) },
                trailingIcon = {
                    RadioButton(selected = currentMode == mode, onClick = null)
                }
            )
        }

        PreferenceSubtitle(text = stringResource(R.string.theme_dark_settings_group))

        PreferenceSwitch(
            title = stringResource(R.string.theme_high_contrast),
            isChecked = isHighContrast,
            onClick = { ThemeManager.setHighContrastEnabled(context, !isHighContrast) }
        )
    }
}
