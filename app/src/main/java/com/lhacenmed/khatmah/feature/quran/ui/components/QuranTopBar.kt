package com.lhacenmed.khatmah.feature.quran.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lhacenmed.khatmah.feature.quran.ui.reader.QuranPageData
import com.lhacenmed.khatmah.feature.quran.ui.reader.toArNums

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuranTopBar(page: QuranPageData, onBack: () -> Unit, onSearch: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shadowElevation = 4.dp) {
        CenterAlignedTopAppBar(
            modifier = Modifier.statusBarsPadding(),
            colors   = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent),
            title    = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text     = page.suraName,
                        style    = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color    = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                    )
                    Text(
                        text  = buildPageLabel(page),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
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

/** "صفحة ١ ⋅ جزء ٢" subtitle for the top bar. */
private fun buildPageLabel(page: QuranPageData): String {
    val pageStr = "صفحة ${toArNums(page.pageNum)}"
    if (page.juz.isBlank()) return pageStr
    val juzStr = page.juz.trim().toIntOrNull()?.let { toArNums(it) } ?: page.juz
    return "$pageStr ⋅ جزء $juzStr"
}
