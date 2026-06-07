package com.lhacenmed.khatmah.onboarding

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lhacenmed.khatmah.R
import com.lhacenmed.khatmah.core.ui.components.SingleChoiceItem
import com.lhacenmed.khatmah.core.nav.LocalNavController
import com.lhacenmed.khatmah.core.nav.ShellRoutes
import com.lhacenmed.khatmah.shared.util.LocaleManager

private data class LangOption(val tag: String?, val labelRes: Int)

private val LANGUAGES = listOf(
    LangOption(null, R.string.language_system_default),
    LangOption("en", R.string.language_english),
    LangOption("ar", R.string.language_arabic),
)

/**
 * Onboarding step 0 — Language selection.
 */
@Composable
fun LanguageOnboardingPage() {
    val nav = LocalNavController.current
    var currentTag by remember { mutableStateOf(LocaleManager.getCurrentTag()) }

    fun advance() = nav.navigate(ShellRoutes.ONBOARDING_NOTIFICATIONS) {
        popUpTo(ShellRoutes.ONBOARDING_LANGUAGE) { inclusive = true }
    }

    OnboardingPage(
        icon        = Icons.Outlined.Language,
        title       = stringResource(R.string.language_settings),
        description = stringResource(R.string.onboarding_lang_desc),
        actionLabel = stringResource(R.string.next),
        onAction    = ::advance,
    ) {
        Spacer(Modifier.height(24.dp))
        Column(
            modifier           = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            LANGUAGES.forEachIndexed { index, option ->
                SingleChoiceItem(
                    label    = stringResource(option.labelRes),
                    selected = matchesTag(currentTag, option.tag),
                    onClick  = {
                        currentTag = option.tag
                        LocaleManager.setLocale(option.tag)
                    },
                )
                if (index < LANGUAGES.lastIndex) {
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp))
                }
            }
        }
    }
}

private fun matchesTag(current: String?, option: String?): Boolean {
    if (option == null) return current == null
    if (current == null) return false
    return current.startsWith(option, ignoreCase = true)
}
