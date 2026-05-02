package com.lhacenmed.khatmah.feature.prayer.ui.settings.calculations

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lhacenmed.khatmah.R
import com.lhacenmed.khatmah.core.nav.LocalNavController
import com.lhacenmed.khatmah.feature.prayer.data.DstMode
import com.lhacenmed.khatmah.feature.prayer.data.PrayerSettings
import com.lhacenmed.khatmah.feature.prayer.ui.components.PrayerTimesPreviewBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DstContent() {
    val nav      = LocalNavController.current
    val context  = LocalContext.current
    val settings by PrayerSettings.flow.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title          = { Text(stringResource(R.string.prayer_settings_dst)) },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.navigate_up))
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
            PrayerTimesPreviewBar(settings = settings)
            HorizontalDivider()

            val options = listOf(
                DstMode.AUTOMATIC to R.string.dst_automatic,
                DstMode.PLUS_ONE  to R.string.dst_plus_one,
                DstMode.MINUS_ONE to R.string.dst_minus_one,
            )

            options.forEach { (mode, labelRes) ->
                val selected = settings.dstMode == mode
                ListItem(
                    headlineContent = { Text(stringResource(labelRes)) },
                    trailingContent = {
                        RadioButton(
                            selected = selected,
                            onClick  = {
                                if (!selected) PrayerSettings.save(context, settings.copy(dstMode = mode))
                            },
                        )
                    },
                    modifier = Modifier.clickable {
                        if (!selected) PrayerSettings.save(context, settings.copy(dstMode = mode))
                    },
                )
                HorizontalDivider(
                    modifier  = Modifier.padding(horizontal = 16.dp),
                    thickness = 0.5.dp,
                    color     = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                )
            }
        }
    }
}