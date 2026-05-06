package com.lhacenmed.khatmah.feature.today.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lhacenmed.khatmah.R
import com.lhacenmed.khatmah.core.ui.theme.WarshFamily

@Composable
internal fun NoKhatmahCard(onCreate: () -> Unit) {
    CardShell(overlayText = stringResource(R.string.today_no_khatmah)) {
        Button(onClick = onCreate, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.today_create), fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
internal fun AllReadCard(onDua: () -> Unit, onNewKhatmah: () -> Unit) {
    CardShell(overlayText = stringResource(R.string.today_khatmah_completed)) {
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(onClick = onDua, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.today_dua_khatm), fontWeight = FontWeight.SemiBold)
            }
            Button(
                onClick  = onNewKhatmah,
                modifier = Modifier.weight(1f),
                colors   = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFFFCA28),
                    contentColor   = Color(0xFF1B1B1B),
                ),
            ) {
                Text(stringResource(R.string.today_new_khatmah), fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/**
 * Shared invisible scaffold that matches [SessionCard]'s layout exactly,
 * keeping consistent card height across all states.
 * [overlayText] is centered over the card body; [buttons] slot fills the action area.
 */
@Composable
private fun CardShell(
    overlayText: String,
    buttons:     @Composable () -> Unit,
) {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Box {
            Column(modifier = Modifier.padding(16.dp)) {
                // Invisible header — holds labelLarge height
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically,
                ) {
                    Ghost(style = MaterialTheme.typography.labelLarge)
                    Ghost(style = MaterialTheme.typography.labelLarge)
                }

                Spacer(Modifier.height(20.dp))

                // Invisible Warsh block — holds aya font metrics height
                Box(modifier = Modifier.fillMaxWidth()) {
                    Ghost(style = TextStyle(fontFamily = WarshFamily, fontSize = 26.sp, lineHeight = 42.sp))
                }

                Spacer(Modifier.height(20.dp))
                HorizontalDivider(modifier = Modifier.alpha(0f))
                Spacer(Modifier.height(12.dp))

                // Invisible info rows
                repeat(2) {
                    Row(
                        modifier              = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Ghost(style = MaterialTheme.typography.bodyMedium)
                        Ghost(style = MaterialTheme.typography.bodyMedium)
                    }
                    if (it == 0) Spacer(Modifier.height(4.dp))
                }

                Spacer(Modifier.height(16.dp))
                buttons()
            }

            // Centered overlay text — no layout impact
            Box(
                modifier         = Modifier.matchParentSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text      = overlayText,
                    textAlign = TextAlign.Center,
                    color     = MaterialTheme.colorScheme.primary,
                    maxLines  = 3,
                    overflow  = TextOverflow.Ellipsis,
                    modifier  = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 50.dp),
                )
            }
        }
    }
}

/** Invisible placeholder text that occupies the same space as a real label. */
@Composable
private fun Ghost(style: TextStyle) {
    Text(text = "ض", style = style, modifier = Modifier.alpha(0f))
}