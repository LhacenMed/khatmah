package com.lhacenmed.khatmah.ui.page.tabs.adhkar

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.lhacenmed.khatmah.R
import com.lhacenmed.khatmah.ui.nav.LocalNavController

/**
 * Placeholder detail screen for an Adhkar category.
 * Displays the category title centered until actual content is implemented.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdhkarDetailPage(categoryId: String) {
    val nav      = LocalNavController.current
    val category = adhkarCategories.find { it.id == categoryId }

    Scaffold(
        topBar = {
            TopAppBar(
                title          = { Text(if (category != null) stringResource(category.titleRes) else "") },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(
                            imageVector        = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.navigate_up),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Box(
            modifier         = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.Center,
        ) {
            if (category != null) {
                Text(
                    text      = stringResource(category.titleRes),
                    style     = MaterialTheme.typography.headlineMedium,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}