package com.lhacenmed.khatmah.ui.page.settings.about

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
import androidx.compose.material3.IconButton
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
import com.lhacenmed.khatmah.R
import com.lhacenmed.khatmah.ui.common.Route
import com.lhacenmed.khatmah.ui.component.LargeTopAppBar
import com.lhacenmed.khatmah.ui.component.PreferenceItem
import com.lhacenmed.khatmah.ui.component.PreferenceSubtitle
import com.lhacenmed.khatmah.ui.nav.LocalNavController
import com.lhacenmed.khatmah.ui.nav.NavPage

/**
 * About sub-page.
 * Owns its Scaffold + LargeTopAppBar; animates as a complete screen alongside the main shell.
 * Append AboutPage to the pages list in AppEntry to register it.
 */
@OptIn(ExperimentalMaterial3Api::class)
val AboutPage = NavPage(route = Route.ABOUT) {
    val nav            = LocalNavController.current
    val context        = LocalContext.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    val versionName = remember(context) {
        context.packageManager
            .getPackageInfo(context.packageName, 0)
            .versionName ?: "—"
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar   = {
            LargeTopAppBar(
                title          = { Text(stringResource(R.string.about_page)) },
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            item {
                PreferenceSubtitle(text = stringResource(R.string.about_app))
            }
            item {
                PreferenceItem(
                    title       = stringResource(R.string.version),
                    description = versionName,
                    icon        = Icons.Outlined.Info,
                )
            }
            item {
                PreferenceSubtitle(text = stringResource(R.string.about_links))
            }
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