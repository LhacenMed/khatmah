package com.lhacenmed.khatmah.ui.page.settings.appearance

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
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lhacenmed.khatmah.R
import com.lhacenmed.khatmah.ui.common.Route
import com.lhacenmed.khatmah.ui.component.LargeTopAppBar
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
 * Owns its Scaffold + LargeTopAppBar; animates as a complete screen alongside the main shell.
 * Append LanguagePage to the pages list in AppEntry to register it.
 */
@OptIn(ExperimentalMaterial3Api::class)
val LanguagePage = NavPage(route = Route.LANGUAGE) {
    val nav            = LocalNavController.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    // Snapshot current selection; AppCompatDelegate recreates the activity on change,
    // so this state only needs to survive until the system-triggered recreate.
    var currentTag by remember { mutableStateOf(LocaleManager.getCurrentTag()) }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar   = {
            LargeTopAppBar(
                title          = { Text(stringResource(R.string.language_settings)) },
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