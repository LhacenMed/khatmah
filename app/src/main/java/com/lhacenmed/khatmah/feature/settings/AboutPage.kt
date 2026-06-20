package com.lhacenmed.khatmah.feature.settings

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.lhacenmed.khatmah.R
import com.lhacenmed.khatmah.core.ui.components.PreferenceItem
import com.lhacenmed.khatmah.core.ui.components.PreferenceSubtitle

// ── Screen ────────────────────────────────────────────────────────────────────
// Body only — the title and back arrow come from ScreenHostActivity's native top bar
// (see Dest.About.titleRes).

@Composable
internal fun AboutScreen() {
    val context = LocalContext.current

    val versionName = remember(context) {
        context.packageManager
            .getPackageInfo(context.packageName, 0)
            .versionName ?: "—"
    }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item { PreferenceSubtitle(text = stringResource(R.string.about_app)) }
        item {
            PreferenceItem(
                title       = stringResource(R.string.version),
                description = versionName,
                icon        = Icons.Outlined.Info,
            )
        }
        item { PreferenceSubtitle(text = stringResource(R.string.about_links)) }
        item {
            PreferenceItem(
                title       = stringResource(R.string.about_translate),
                description = stringResource(R.string.about_translate_desc),
                icon        = Icons.Outlined.Translate,
            )
        }
        item {
            PreferenceItem(
                title       = stringResource(R.string.about_source_code),
                description = stringResource(R.string.about_source_code_desc),
                icon        = Icons.Outlined.Code,
            )
        }
    }
}

// Empty stub — no auto-update mechanism in this app.
@Composable
fun AutoUpdateUnavailableDialog(onDismissRequest: () -> Unit = {}) = Unit
