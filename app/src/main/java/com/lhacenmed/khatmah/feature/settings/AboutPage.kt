package com.lhacenmed.khatmah.feature.settings

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.navigation.NavBackStackEntry
import com.lhacenmed.khatmah.R
import com.lhacenmed.khatmah.core.nav.AppPage
import com.lhacenmed.khatmah.core.nav.LocalNavController
import com.lhacenmed.khatmah.core.ui.components.IconButton
import com.lhacenmed.khatmah.core.ui.components.LargeTopAppBar
import com.lhacenmed.khatmah.core.ui.components.PreferenceItem
import com.lhacenmed.khatmah.core.ui.components.PreferenceSubtitle

// ── Navigation destination ────────────────────────────────────────────────────

/**
 * Route auto-derived: "about" — matches "about".
 * Call sites continue to use nav.navigate("about") unchanged.
 */
object AboutPage : AppPage() {
    @Composable override fun Content(back: NavBackStackEntry) = AboutScreen()
}

// ── Screen ────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AboutScreen() {
    val nav = LocalNavController.current
    val context = LocalContext.current
    val scrollBehavior =
        TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    val versionName = remember(context) {
        context.packageManager
            .getPackageInfo(context.packageName, 0)
            .versionName ?: "—"
    }

    val tooltipAnchorBottom = lerp(20.dp, 55.dp, scrollBehavior.state.collapsedFraction)

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(R.string.about_page)) },
                navigationIcon = {
                    IconButton(
                        onClick = { nav.popBackStack() },
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
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
}

// Empty stub — no auto-update mechanism in this app.
@Composable
fun AutoUpdateUnavailableDialog(onDismissRequest: () -> Unit = {}) = Unit