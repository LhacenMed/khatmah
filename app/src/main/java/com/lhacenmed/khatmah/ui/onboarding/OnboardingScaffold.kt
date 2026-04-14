package com.lhacenmed.khatmah.ui.onboarding

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Shared full-screen layout for all onboarding permission pages.
 *
 * Layout (portrait):
 *  ┌──────────────────────────┐
 *  │                  [Skip]  │  ← only when skipLabel != null
 *  │         [Icon]           │
 *  │          Title           │
 *  │       Description        │
 *  │    [extraContent slot]   │
 *  │    [Primary  Button]     │
 *  └──────────────────────────┘
 */
@Composable
fun OnboardingPage(
    icon: ImageVector,
    title: String,
    description: String,
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
    actionEnabled: Boolean = true,
    skipLabel: String? = null,
    onSkip: (() -> Unit)? = null,
    extraContent: @Composable ColumnScope.() -> Unit = {},
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(horizontal = 24.dp),
    ) {
        // Skip ─ top end
        if (skipLabel != null && onSkip != null) {
            TextButton(
                onClick  = onSkip,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 8.dp),
            ) { Text(skipLabel) }
        }

        // Central content ─ vertically centered with room for the button
        Column(
            modifier            = Modifier
                .align(Alignment.Center)
                .padding(bottom = 88.dp),   // clears the action button
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector        = icon,
                contentDescription = null,
                modifier           = Modifier.size(80.dp),
                tint               = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(28.dp))
            Text(
                text  = title,
                style = MaterialTheme.typography.headlineSmall,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text      = description,
                style     = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color     = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            extraContent()
        }

        // Primary action ─ bottom
        Button(
            onClick  = onAction,
            enabled  = actionEnabled,
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp),
        ) { Text(actionLabel) }
    }
}