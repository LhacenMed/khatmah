package com.lhacenmed.khatmah.feature.adhkar.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// ── Section label ─────────────────────────────────────────────────────────────

@Composable
fun SectionLabel(
    text:  String,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    Text(
        text  = text,
        style = MaterialTheme.typography.labelLarge,
        color = color,
    )
}