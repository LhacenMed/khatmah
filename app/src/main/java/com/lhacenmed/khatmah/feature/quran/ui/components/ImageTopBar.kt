package com.lhacenmed.khatmah.feature.quran.ui.components

import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lhacenmed.khatmah.feature.quran.ui.reader.toArNums

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageTopBar(pageNum: Int, onBack: () -> Unit, onSearch: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shadowElevation = 4.dp) {
        CenterAlignedTopAppBar(
            modifier = Modifier.statusBarsPadding(),
            colors   = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent),
            title    = {
                Text(
                    text  = "صفحة ${toArNums(pageNum)}",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = null,
                        tint               = MaterialTheme.colorScheme.onSurface,
                    )
                }
            },
            actions = {
                IconButton(onClick = onSearch) {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = null,
                        tint               = MaterialTheme.colorScheme.onSurface,
                    )
                }
            },
        )
    }
}
