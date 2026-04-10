package com.lhacenmed.khatmah.ui.page.settings.appearance

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import com.lhacenmed.khatmah.ui.theme.KhatmahTheme
import com.lhacenmed.khatmah.util.ThemeManager

class ThemeSettingsFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = ComposeView(requireContext()).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent {
            KhatmahTheme {
                ThemeSettingsPage(
                    currentMode = ThemeManager.getMode(requireContext()),
                    onModeSelected = { mode -> ThemeManager.setMode(requireContext(), mode) }
                )
            }
        }
    }
}