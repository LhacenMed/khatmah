package com.lhacenmed.khatmah.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lhacenmed.khatmah.ui.page.tabs.adhkar.AdhkarCategory

/**
 * Colored card for an Adhkar category.
 *
 * Layout uses [Alignment.CenterEnd] for the icon and [Alignment.BottomStart] for the
 * title. Compose resolves Start/End against the ambient [LayoutDirection], so:
 *   RTL (Arabic)  → icon = physical-left,  title = physical-right  (matches design)
 *   LTR (English) → icon = physical-right, title = physical-left   (auto-mirrored)
 *
 * The darkening scrim is a pure-black alpha overlay, which is the most
 * color-neutral strategy — it deepens any hue without adding a competing tint.
 */
@Composable
fun AdhkarCard(
    category: AdhkarCategory,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(130.dp)
            .clip(MaterialTheme.shapes.small)
            .background(category.color)
            .clickable(onClick = onClick),
    ) {
        // Scrim: transparent at top → dimmed at bottom for title legibility.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color(0f, 0f, 0f, 0.3f))
                    )
                )
        )

        // Icon — fills 68 % of card height with a locked 1:1 aspect ratio (≈ 109 dp).
        // CenterEnd → physical left in RTL · physical right in LTR.
        Icon(
            painter            = painterResource(category.iconRes),
            contentDescription = null,
            tint               = Color.White.copy(alpha = 0.5f),
            modifier           = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight(fraction = 1f)
                .aspectRatio(ratio = 1f, matchHeightConstraintsFirst = true)
                .padding(horizontal = 10.dp),
        )

        // Title — BottomStart → physical right in RTL · physical left in LTR.
        Text(
            text     = stringResource(category.titleRes),
            style    = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color    = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(horizontal = 14.dp, vertical = 12.dp),
        )
    }
}