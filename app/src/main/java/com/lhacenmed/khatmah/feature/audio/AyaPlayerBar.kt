package com.lhacenmed.khatmah.feature.audio

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lhacenmed.khatmah.feature.quran.ui.reader.toArNums

@Composable
fun AyaPlayerBar(
    state:         AyaAudioState,
    onToggle:      () -> Unit,
    onReaderClick: () -> Unit,
    onClose:       () -> Unit,
) {
    val primary   = MaterialTheme.colorScheme.primary
    val trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)

    Surface(
        color           = MaterialTheme.colorScheme.secondaryContainer,
        shadowElevation = 4.dp,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {

            // ── Progress / download indicator ─────────────────────────────────
            when (val ls = state.loadState) {
                is AudioLoadState.Connecting,
                is AudioLoadState.Idle ->
                    LinearProgressIndicator(
                        modifier   = Modifier.fillMaxWidth(),
                        color      = primary,
                        trackColor = trackColor,
                    )

                is AudioLoadState.Downloading -> {
                    if (ls.progress < 0f) {
                        LinearProgressIndicator(
                            modifier   = Modifier.fillMaxWidth(),
                            color      = primary,
                            trackColor = trackColor,
                        )
                    } else {
                        LinearProgressIndicator(
                            progress   = { ls.progress },
                            modifier   = Modifier.fillMaxWidth(),
                            color      = primary,
                            trackColor = trackColor,
                        )
                    }
                }

                is AudioLoadState.Ready ->
                    LinearProgressIndicator(
                        progress   = { state.progress },
                        modifier   = Modifier.fillMaxWidth(),
                        color      = primary,
                        trackColor = trackColor,
                    )

                is AudioLoadState.Error ->
                    LinearProgressIndicator(
                        progress   = { 1f },
                        modifier   = Modifier.fillMaxWidth(),
                        color      = MaterialTheme.colorScheme.error,
                        trackColor = trackColor,
                    )
            }

            Row(
                modifier          = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick  = onToggle,
                    enabled  = state.loadState is AudioLoadState.Ready,
                ) {
                    Icon(
                        imageVector        = if (state.isPlaying) Icons.Default.Pause
                        else Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint               = MaterialTheme.colorScheme.onSecondaryContainer
                            .copy(alpha = if (state.loadState is AudioLoadState.Ready) 1f else 0.4f),
                    )
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable(onClick = onReaderClick, role = Role.Button)
                        .padding(vertical = 4.dp)
                ) {
                    Text(
                        text     = when (val ls = state.loadState) {
                            is AudioLoadState.Connecting             -> "جارٍ الاتصال…"
                            is AudioLoadState.Downloading            -> if (ls.progress >= 0f)
                                "جارٍ التحميل… ${(ls.progress * 100).toInt()}٪"
                            else "جارٍ التحميل…"
                            is AudioLoadState.Error                  -> ls.message
                            else                                     -> state.readerName
                        },
                        style    = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color    = if (state.loadState is AudioLoadState.Error)
                            MaterialTheme.colorScheme.error
                        else
                            MaterialTheme.colorScheme.onSecondaryContainer,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (state.ayaNum > 0) {
                        Text(
                            text  = "آية ${toArNums(state.ayaNum)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f),
                        )
                    }
                }

                IconButton(onClick = onClose) {
                    Icon(
                        imageVector        = Icons.Default.Close,
                        contentDescription = null,
                        tint               = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
        }
    }
}
