package com.lhacenmed.khatmah.feature.settings

import android.os.Bundle
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import com.lhacenmed.khatmah.R
import com.lhacenmed.khatmah.core.BaseComposeActivity
import com.lhacenmed.khatmah.core.nav.Dest
import com.lhacenmed.khatmah.core.nav.LocalNavigator
import com.lhacenmed.khatmah.core.ui.components.IconButton
import com.lhacenmed.khatmah.core.ui.components.LargeTopAppBar
import com.lhacenmed.khatmah.core.ui.components.SingleChoiceItem
import com.lhacenmed.khatmah.shared.util.LocaleManager

private data class LangOption(val tag: String?, val labelRes: Int)

private val LANGUAGES = listOf(
    LangOption(null, R.string.language_system_default),
    LangOption("en", R.string.language_english),
    LangOption("ar", R.string.language_arabic),
)

// ── Activity host ─────────────────────────────────────────────────────────────

/** Language settings sub-page. Owns its Scaffold + LargeTopAppBar. */
class LanguageActivity : BaseComposeActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setAppContent { LanguageScreen() }
    }
}

// ── Screen ────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LanguageScreen() {
    val nav = LocalNavigator.current
    val scrollBehavior =
        TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    // Snapshot current selection; AppCompatDelegate recreates the activity on change,
    // so this state only needs to survive until the system-triggered recreate.
    var currentTag by remember { mutableStateOf(LocaleManager.getCurrentTag()) }

    // Tooltip anchor tracks the bar's actual height: 20 dp when fully expanded.
    val tooltipAnchorBottom = lerp(20.dp, 55.dp, scrollBehavior.state.collapsedFraction)

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(R.string.language_settings)) },
                navigationIcon = {
                    IconButton(
                        onClick = { nav.back() },
                        tooltipText = stringResource(R.string.navigate_up),
                        anchorExtraBottom = tooltipAnchorBottom,
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
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
