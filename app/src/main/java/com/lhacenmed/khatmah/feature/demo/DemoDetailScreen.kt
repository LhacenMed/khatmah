package com.lhacenmed.khatmah.feature.demo

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.lhacenmed.khatmah.R
import com.lhacenmed.khatmah.core.nav.LocalNavigator

/** Example detail screen, rendered by ScreenHostActivity via Dest.DemoDetail (no Activity). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DemoDetailScreen() {
    val nav = LocalNavigator.current
    Scaffold(
        topBar = {
            TopAppBar(
                title          = { Text(stringResource(R.string.demo_detail)) },
                navigationIcon = {
                    IconButton(onClick = { nav.back() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
    ) { padding ->
        Box(
            modifier         = Modifier.fillMaxSize().padding(padding),
            contentAlignment = Alignment.Center,
        ) {
            Text(stringResource(R.string.demo_detail_body))
        }
    }
}
