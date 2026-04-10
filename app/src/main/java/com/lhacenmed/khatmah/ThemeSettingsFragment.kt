package com.lhacenmed.khatmah

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import com.lhacenmed.khatmah.ui.theme.KhatmahTheme

class ThemeSettingsFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = ComposeView(requireContext()).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent {
            KhatmahTheme {
                ThemeSettingsScreen(
                    currentMode = ThemeManager.getMode(requireContext()),
                    onModeSelected = { mode -> ThemeManager.setMode(requireContext(), mode) }
                )
            }
        }
    }
}

@Composable
private fun ThemeSettingsScreen(currentMode: Int, onModeSelected: (Int) -> Unit) {
    val options = listOf(
        AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM to R.string.theme_system,
        AppCompatDelegate.MODE_NIGHT_NO              to R.string.theme_light,
        AppCompatDelegate.MODE_NIGHT_YES             to R.string.theme_dark,
    )

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(vertical = 8.dp)) {
            options.forEach { (mode, labelRes) ->
                ThemeOptionRow(
                    label = stringResource(labelRes),
                    selected = currentMode == mode,
                    onClick = { onModeSelected(mode) }
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp))
            }
        }
    }
}

@Composable
private fun ThemeOptionRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge
        )
        RadioButton(selected = selected, onClick = onClick)
    }
}
