package com.lhacenmed.khatmah.ui.page.settings.appearance

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lhacenmed.khatmah.R
import com.lhacenmed.khatmah.ui.component.SingleChoiceItem

@Composable
fun ThemeSettingsPage(
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