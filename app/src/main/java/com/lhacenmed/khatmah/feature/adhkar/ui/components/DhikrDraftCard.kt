package com.lhacenmed.khatmah.feature.adhkar.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lhacenmed.khatmah.R

// ── Dhikr draft card ──────────────────────────────────────────────────────────

@Composable
fun DhikrDraftCard(
    index:     Int,
    draft:     DhikrDraftState,
    canDelete: Boolean,
    onDelete:  () -> Unit,
) {
    Card(
        colors   = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier            = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text     = stringResource(R.string.adhkar_dhikr_n, index),
                    style    = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                AnimatedVisibility(visible = canDelete) {
                    IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Outlined.Close, null, Modifier.size(18.dp))
                    }
                }
            }

            draft.paragraphs.forEachIndexed { pIdx, para ->
                ParagraphDraftRow(
                    draft     = para,
                    canDelete = draft.paragraphs.size > 1,
                    onDelete  = { draft.paragraphs.removeAt(pIdx) },
                )
            }

            TextButton(
                onClick  = { draft.paragraphs.add(ParagraphDraftState()) },
                modifier = Modifier.align(Alignment.Start),
            ) {
                Icon(Icons.Outlined.Add, null, Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.adhkar_add_paragraph), style = MaterialTheme.typography.labelMedium)
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text     = stringResource(R.string.adhkar_repetitions, draft.repetitions),
                    style    = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                FilledTonalIconButton(
                    onClick  = { if (draft.repetitions > 1) draft.repetitions-- },
                    modifier = Modifier.size(32.dp),
                ) { Text("−", style = MaterialTheme.typography.titleMedium) }
                FilledTonalIconButton(
                    onClick  = { draft.repetitions++ },
                    modifier = Modifier.size(32.dp),
                ) { Text("+", style = MaterialTheme.typography.titleMedium) }
            }
        }
    }
}

// ── Paragraph row ─────────────────────────────────────────────────────────────

@Composable
fun ParagraphDraftRow(
    draft:     ParagraphDraftState,
    canDelete: Boolean,
    onDelete:  () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ParagraphDraftType.entries.forEach { type ->
                FilterChip(
                    selected = draft.type == type,
                    onClick  = { draft.type = type },
                    label    = { Text(stringResource(type.labelRes), style = MaterialTheme.typography.labelSmall) },
                )
            }
            if (canDelete) {
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Outlined.Close, null, Modifier.size(16.dp))
                }
            }
        }
        OutlinedTextField(
            value         = draft.text,
            onValueChange = { draft.text = it },
            modifier      = Modifier.fillMaxWidth(),
            minLines      = 3,
            maxLines      = 8,
            textStyle     = MaterialTheme.typography.bodyLarge,
            placeholder   = {
                Text(
                    text  = when (draft.type) {
                        ParagraphDraftType.QURAN -> stringResource(R.string.adhkar_paragraph_quran_hint)
                        ParagraphDraftType.NOTE  -> stringResource(R.string.adhkar_paragraph_note_hint)
                        else                     -> stringResource(R.string.adhkar_paragraph_body_hint)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
        )
    }
}