package com.lhacenmed.khatmah.feature.quran.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

@Composable
fun QuranBottomBar(currentPage: Int, totalPages: Int, onJump: (Int) -> Unit) {
    var sliderVal  by remember { mutableFloatStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }

    LaunchedEffect(currentPage) {
        if (!isDragging) {
            sliderVal = if (totalPages > 1) currentPage.toFloat() / (totalPages - 1) else 0f
        }
    }

    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shadowElevation = 4.dp) {
        Box(
            modifier         = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center,
        ) {
            Slider(
                value                 = sliderVal,
                onValueChange         = { v -> sliderVal = v; isDragging = true },
                onValueChangeFinished = {
                    isDragging = false
                    onJump((sliderVal * (totalPages - 1)).roundToInt().coerceIn(0, totalPages - 1))
                },
                valueRange = 0f..1f,
                modifier   = Modifier.fillMaxWidth(),
            )
        }
    }
}
