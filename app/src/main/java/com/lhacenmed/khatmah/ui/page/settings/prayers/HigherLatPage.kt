package com.lhacenmed.khatmah.ui.page.settings.prayers

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
import com.lhacenmed.khatmah.data.prayer.HigherLatMode
import com.lhacenmed.khatmah.data.prayer.PrayerSettings
import com.lhacenmed.khatmah.ui.nav.LocalNavController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HigherLatContent() {
    val nav      = LocalNavController.current
    val context  = LocalContext.current
    val settings by PrayerSettings.flow.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title          = { Text(stringResource(R.string.prayer_settings_higher_lat)) },
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
                HigherLatMode.NONE             to R.string.higher_lat_none,
                HigherLatMode.MIDDLE_OF_NIGHT  to R.string.higher_lat_middle,
                HigherLatMode.SEVENTH_OF_NIGHT to R.string.higher_lat_seventh,
                HigherLatMode.ANGLE_BASED      to R.string.higher_lat_angle,
            )

            options.forEach { (mode, labelRes) ->
                val selected = settings.higherLatMode == mode
                ListItem(
                    headlineContent = { Text(stringResource(labelRes)) },
                    trailingContent = {
                        RadioButton(
                            selected = selected,
                            onClick  = {
                                if (!selected) PrayerSettings.save(context, settings.copy(higherLatMode = mode))
                            },
                        )
                    },
                    modifier = Modifier.clickable {
                        if (!selected) PrayerSettings.save(context, settings.copy(higherLatMode = mode))
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