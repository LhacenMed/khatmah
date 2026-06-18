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
import android.os.Bundle
import com.lhacenmed.khatmah.core.BaseComposeActivity
import com.lhacenmed.khatmah.core.nav.LocalNavigator
import com.lhacenmed.khatmah.feature.prayer.data.JuristicMethod
import com.lhacenmed.khatmah.feature.prayer.data.PrayerSettings
import com.lhacenmed.khatmah.feature.prayer.ui.components.PrayerTimesPreviewBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JuristicScreen() {
    val nav      = LocalNavigator.current
    val context  = LocalContext.current
    val settings by PrayerSettings.flow.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title          = { Text(stringResource(R.string.prayer_settings_juristic)) },
                navigationIcon = {
                    IconButton(onClick = { nav.back() }) {
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
                JuristicMethod.SHAFI_MALIKI_HANBALI to R.string.juristic_shafi,
                JuristicMethod.HANAFI               to R.string.juristic_hanafi,
            )

            options.forEach { (method, labelRes) ->
                val selected = settings.juristic == method
                ListItem(
                    headlineContent = { Text(stringResource(labelRes)) },
                    trailingContent = {
                        RadioButton(
                            selected = selected,
                            onClick  = {
                                if (!selected) PrayerSettings.save(context, settings.copy(juristic = method))
                            },
                        )
                    },
                    modifier = Modifier.clickable {
                        if (!selected) PrayerSettings.save(context, settings.copy(juristic = method))
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

class JuristicActivity : BaseComposeActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setAppContent { JuristicScreen() }
    }
}