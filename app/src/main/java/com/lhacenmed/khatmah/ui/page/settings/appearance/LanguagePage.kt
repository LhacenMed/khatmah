package com.lhacenmed.khatmah.ui.page.settings.appearance

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lhacenmed.khatmah.R
import com.lhacenmed.khatmah.ui.common.Route
import com.lhacenmed.khatmah.ui.component.SingleChoiceItem
import com.lhacenmed.khatmah.ui.nav.LocalNavController
import com.lhacenmed.khatmah.ui.nav.NavPage
import com.lhacenmed.khatmah.util.LocaleManager

private data class LangOption(val tag: String?, val labelRes: Int)

private val LANGUAGES = listOf(
    LangOption(null, R.string.language_system_default),
    LangOption("en", R.string.language_english),
    LangOption("ar", R.string.language_arabic),
)

/**
 * Language settings sub-page.
 * Append LanguagePage to the pages list in MainActivity to register it.
 * Route, TopAppBar title, and NavHost entry are all derived automatically.
 */
val LanguagePage = NavPage(
    route    = Route.LANGUAGE,
    titleRes = R.string.language_settings,
) { padding -> LanguageContent(padding) }

@Composable
private fun LanguageContent(padding: PaddingValues) {
    // Snapshot current selection; AppCompatDelegate recreates the activity on change,
    // so this state only needs to survive until the system-triggered recreate.
    var currentTag by remember { mutableStateOf(LocaleManager.getCurrentTag()) }
    val nav = LocalNavController.current

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(vertical = 8.dp),
        ) {
            LANGUAGES.forEach { option ->
                SingleChoiceItem(
                    label    = stringResource(option.labelRes),
                    selected = matchesTag(currentTag, option.tag),
                    onClick  = {
                        currentTag = option.tag
                        LocaleManager.setLocale(option.tag)
                        // AppCompatDelegate.setApplicationLocales() triggers recreate automatically
                    },
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp))
            }
            HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp))
            TextButton(onClick = { nav.navigate(Route.ABOUT) }) {
                Text(stringResource(R.string.about_page))
            }
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