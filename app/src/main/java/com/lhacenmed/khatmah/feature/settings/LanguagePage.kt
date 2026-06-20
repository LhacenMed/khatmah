package com.lhacenmed.khatmah.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lhacenmed.khatmah.R
import com.lhacenmed.khatmah.core.nav.Dest
import com.lhacenmed.khatmah.core.nav.LocalNavigator
import com.lhacenmed.khatmah.core.ui.components.SingleChoiceItem
import com.lhacenmed.khatmah.shared.util.LocaleManager

private data class LangOption(val tag: String?, val labelRes: Int)

private val LANGUAGES = listOf(
    LangOption(null, R.string.language_system_default),
    LangOption("en", R.string.language_english),
    LangOption("ar", R.string.language_arabic),
)

// Body only — the title + back arrow come from ScreenHostActivity (see Dest.Language.titleRes).
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LanguageScreen() {
    val nav = LocalNavigator.current

    // Snapshot current selection; AppCompatDelegate recreates the activity on change,
    // so this state only needs to survive until the system-triggered recreate.
    var currentTag by remember { mutableStateOf(LocaleManager.getCurrentTag()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = 8.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        LANGUAGES.forEach { option ->
            SingleChoiceItem(
                label = stringResource(option.labelRes),
                selected = matchesTag(currentTag, option.tag),
                onClick = {
                    currentTag = option.tag
                    LocaleManager.setLocale(option.tag)
                    // AppCompatDelegate.setApplicationLocales() triggers recreate automatically
                },
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp))
        }
        HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp))
        TextButton(onClick = { nav.go(Dest.About) }) {
            Text(stringResource(R.string.about_page))
        }
    }
}

/**
 * Matches the stored locale tag against the option tag, handling
 * language-only comparison (e.g. "en-US" matches option tag "en").
 */
private fun matchesTag(current: String?, option: String?): Boolean {
    if (option == null) return current == null
    if (current == null) return false
    return current.startsWith(option, ignoreCase = true)
}
