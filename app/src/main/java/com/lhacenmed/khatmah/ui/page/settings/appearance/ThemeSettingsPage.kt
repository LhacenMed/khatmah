package com.lhacenmed.khatmah.ui.page.settings.appearance

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.core.content.ContextCompat
import com.lhacenmed.khatmah.R
import com.lhacenmed.khatmah.ui.common.Route
import com.lhacenmed.khatmah.ui.component.ActionItem
import com.lhacenmed.khatmah.ui.component.LargeTopAppBar
import com.lhacenmed.khatmah.ui.component.SingleChoiceItem
import com.lhacenmed.khatmah.ui.component.IconButton
import com.lhacenmed.khatmah.ui.nav.LocalNavController
import com.lhacenmed.khatmah.ui.nav.NavPage
import com.lhacenmed.khatmah.util.NotificationHelper
import com.lhacenmed.khatmah.util.ThemeManager

/**
 * Theme settings sub-page.
 * Owns its Scaffold + LargeTopAppBar; animates as a complete screen alongside the main shell.
 * Append ThemeSettingsPage to the pages list in AppEntry to register it.
 */
@RequiresApi(Build.VERSION_CODES.O)
@OptIn(ExperimentalMaterial3Api::class)
val ThemeSettingsPage = NavPage(route = Route.THEME_SETTINGS) {
    val nav            = LocalNavController.current
    val context        = LocalContext.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    val themeOptions = listOf(
        AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM to R.string.theme_system,
        AppCompatDelegate.MODE_NIGHT_NO            to R.string.theme_light,
        AppCompatDelegate.MODE_NIGHT_YES           to R.string.theme_dark,
    )

    // Tooltip anchor tracks the bar's actual height: 20 dp when fully expanded.
    val tooltipAnchorBottom = lerp(20.dp, 55.dp, scrollBehavior.state.collapsedFraction)

    // ── Notification permission ───────────────────────────────────────────────
    // POST_NOTIFICATIONS is a runtime permission on API 33+; request on demand,
    // post immediately once granted. On older APIs post directly — no gate needed.
    val notifPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) NotificationHelper.postTestNotification(context)
    }

    val onTestNotification = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
            if (granted) NotificationHelper.postTestNotification(context)
            else notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            NotificationHelper.postTestNotification(context)
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar   = {
            LargeTopAppBar(
                title          = { Text(stringResource(R.string.theme_settings)) },
                navigationIcon = {
                    IconButton(
                        onClick           = { nav.popBackStack() },
                        tooltipText       = stringResource(R.string.navigate_up),
                        anchorExtraBottom = tooltipAnchorBottom,
                    ) {
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
            themeOptions.forEach { (mode, labelRes) ->
                SingleChoiceItem(
                    label    = stringResource(labelRes),
                    selected = ThemeManager.getMode(context) == mode,
                    onClick  = { ThemeManager.setMode(context, mode) },
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp))
            }

            Spacer(modifier = Modifier.height(16.dp))

            ActionItem(
                label    = stringResource(R.string.notif_test_action),
                subtitle = stringResource(R.string.notif_test_action_subtitle),
                onClick  = onTestNotification,
            )
        }
    }
}