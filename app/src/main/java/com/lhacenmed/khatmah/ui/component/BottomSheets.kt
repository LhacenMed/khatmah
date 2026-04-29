package com.lhacenmed.khatmah.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

// ── Data ──────────────────────────────────────────────────────────────────────

/**
 * A single selectable option shown inside [OptionSelectBottomSheet].
 *
 * @param key      Unique identifier — used for [selected] comparison.
 * @param title    Primary label shown in the row.
 * @param subtitle Optional secondary label shown below [title].
 */
data class SheetOption<out T>(
    val key: T,
    val title: String,
    val subtitle: String? = null,
    val enabled: Boolean = true,
)

// ── Bottom Sheet ──────────────────────────────────────────────────────────────

/**
 * Material 3 single-select options bottom sheet.
 *
 * Renders a [title] header, then a list of [options]. The currently [selected]
 * option shows a check-mark; it is placed at the end of the row, which is the
 * trailing edge on LTR layouts and the leading edge on RTL layouts — naturally
 * correct via Compose's [Row] + [Arrangement.SpaceBetween] without manual RTL
 * handling.
 *
 * @param title    Sheet header text.
 * @param options  Ordered list of options to display.
 * @param selected Key of the currently selected option.
 * @param onSelect Called with the chosen option's key when the user taps a row.
 *                 Dismiss the sheet inside this callback.
 * @param onDismiss Called when the user swipes down or taps the scrim.
 * @param sheetState Optional externally-hoisted [SheetState].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> OptionSelectBottomSheet(
    title: String,
    options: List<SheetOption<out T>>,
    selected: T,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
) {
    val scope = rememberCoroutineScope()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState       = sheetState,
        dragHandle       = {
            // Precise 3dp height drag handle with tighter vertical spacing
            Box(
                Modifier
                    .padding(horizontal= 8.dp, vertical = 12.dp)
                    .width(32.dp)
                    .height(5.dp)
                    .background(
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        shape = CircleShape
                    )
            )
        }
    ) {
        // ── Header ────────────────────────────────────────────────────────────
        Text(
            text       = title,
            style      = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign  = TextAlign.Center,
            modifier   = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp),
        )

        // ── Options ───────────────────────────────────────────────────────────
        options.forEachIndexed { index, option ->
            OptionRow(
                option   = option,
                checked  = option.key == selected,
                onSelect = {
                    scope.launch {
                        sheetState.hide()
                        onSelect(option.key)
                    }
                },
            )
            if (index < options.lastIndex) {
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            }
        }

        // Respect nav-bar insets so nothing is clipped on gesture-nav devices.
        Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
    }
}

// ── Private ───────────────────────────────────────────────────────────────────

@Composable
private fun <T> OptionRow(
    option: SheetOption<T>,
    checked: Boolean,
    onSelect: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                enabled = option.enabled,
                role    = Role.RadioButton,
                onClick = onSelect
            )
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically,
    ) {
        // Title + optional subtitle — takes all remaining space, leaves room for
        // the check icon. weight(1f) + end padding prevents overlap on long strings.
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 16.dp),
        ) {
            Text(
                text  = option.title,
                style = MaterialTheme.typography.bodyLarge,
                color = when {
                    !option.enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    checked -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.onSurface
                },
                fontWeight = if (checked) FontWeight.Medium else FontWeight.Normal,
            )
            if (option.subtitle != null) {
                Text(
                    text  = option.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                        alpha = if (option.enabled) 1f else 0.38f
                    ),
                )
            }
        }

        // Check mark — visible only for the selected option.
        // Sits at the end of the Row; in RTL layouts Compose automatically
        // mirrors Row direction, so this naturally appears on the leading side.
        if (checked) {
            Icon(
                imageVector        = Icons.Default.Check,
                contentDescription = null,
                tint               = if (option.enabled) MaterialTheme.colorScheme.primary 
                                     else MaterialTheme.colorScheme.primary.copy(alpha = 0.38f),
                modifier           = Modifier.size(20.dp),
            )
        }
    }
}