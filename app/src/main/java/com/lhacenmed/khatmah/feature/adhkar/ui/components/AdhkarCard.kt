package com.lhacenmed.khatmah.feature.adhkar.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.lhacenmed.khatmah.feature.adhkar.data.AdhkarCategory
import com.lhacenmed.khatmah.feature.adhkar.data.IconSource

/**
 * Colored card for an Adhkar category.
 *
 * Supports three icon source types via [IconSource]:
 *  [IconSource.Res]  → built-in vector drawable (tinted white).
 *  [IconSource.Uri]  → user image file (PNG/JPG/SVG loaded via Coil).
 *  [IconSource.None] → no icon, full card area for title.
 *
 * When [selectionMode] is true:
 *  • Long-press is disabled (already in selection).
 *  • A selection indicator appears at [Alignment.TopStart] (title side, avoids icon).
 *  • Background dims slightly when not selected.
 *
 * Layout is direction-aware: Start/End resolved by ambient [LayoutDirection]:
 *  RTL → icon at physical-left, title + checkbox at physical-right.
 *  LTR → icon at physical-right, title + checkbox at physical-left.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AdhkarCard(
    category: AdhkarCategory,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selectionMode: Boolean = false,
    selected: Boolean = false,
    onLongClick: () -> Unit = {},
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(130.dp)
            .clip(MaterialTheme.shapes.small)
            .background(
                color = if (selectionMode && !selected)
                    category.color.copy(alpha = 0.6f)
                else
                    category.color
            )
            .combinedClickable(
                onClick     = onClick,
                onLongClick = onLongClick.takeUnless { selectionMode },
            ),
    ) {
        // ── Icon ──────────────────────────────────────────────────────────────
        // CenterEnd → physical left in RTL · physical right in LTR.
        when (val src = category.iconSource) {
            is IconSource.Res -> Icon(
                painter            = painterResource(src.resId),
                contentDescription = null,
                tint               = Color.White.copy(alpha = 0.5f),
                modifier           = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .aspectRatio(1f, matchHeightConstraintsFirst = true)
                    .padding(horizontal = 10.dp),
            )
            is IconSource.Uri -> AsyncImage(
                model              = src.path,
                contentDescription = null,
                contentScale       = ContentScale.Fit,
                alpha              = 0.5f,
                modifier           = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .aspectRatio(1f, matchHeightConstraintsFirst = true)
                    .padding(horizontal = 10.dp),
            )
            is IconSource.None -> Unit
        }

        // ── Gradient scrim ────────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(listOf(Color.Transparent, Color(0f, 0f, 0f, 0.3f)))
                )
        )

        // ── Title ─────────────────────────────────────────────────────────────
        // BottomStart → physical right in RTL · physical left in LTR.
        Text(
            text     = category.title,
            style    = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color    = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(horizontal = 14.dp, vertical = 12.dp),
        )

        // ── Selection indicator ───────────────────────────────────────────────
        // TopStart → title side, never overlaps the icon at CenterEnd.
        AnimatedVisibility(
            visible  = selectionMode,
            enter    = fadeIn() + scaleIn(initialScale = 0.7f),
            exit     = fadeOut() + scaleOut(targetScale = 0.7f),
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(8.dp),
        ) {
            Box(
                modifier         = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(
                        if (selected) MaterialTheme.colorScheme.primary
                        else Color.White.copy(alpha = 0.75f)
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (selected) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint        = Color.White,
                        modifier    = Modifier.size(14.dp),
                    )
                }
            }
        }
    }
}