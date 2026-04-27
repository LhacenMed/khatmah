package com.lhacenmed.khatmah.ui.component

import android.graphics.Path
import android.view.animation.PathInterpolator
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.Color
import com.lhacenmed.khatmah.R

// ── Scroll-fraction easing ────────────────────────────────────────────────────
// Drives SmallTopAppBar's title alpha: invisible while large bar is expanded,
// fades in with a curved ease as the bar collapses toward pinned.
private val _easingPath = Path().apply {
    moveTo(0f, 0f)
    lineTo(0.7f, 0.1f)
    cubicTo(0.7f, 0.1f, 0.95f, 0.5f, 1f, 1f)
}
val collapseFraction: (Float) -> Float = {
    PathInterpolator(_easingPath).getInterpolation(it)
}

// ─── Plain top app bar (main shell) ──────────────────────────────────────────

/**
 * Stateless top app bar used by the main shell (tab screen).
 * Accepts optional [actions] for tab-specific toolbar items (e.g. Adhkar add/delete).
 * [containerColor] can be overridden to signal contextual modes (e.g. selection).
 * [subtitle] renders a secondary line below the title when non-null.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTopBar(
    title: String,
    isTopLevel: Boolean,
    onBack: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    subtitle: String? = null,
) {
    TopAppBar(
        title = {
            if (subtitle != null) {
                Column {
                    Text(title)
                    Text(
                        text  = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                Text(title)
            }
        },
        navigationIcon = {
            if (!isTopLevel) {
                IconButton(
                    onClick     = onBack,
                    tooltipText = stringResource(R.string.navigate_up),
                ) {
                    Icon(
                        imageVector        = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.navigate_up),
                    )
                }
            }
        },
        actions = actions,
        colors  = TopAppBarDefaults.topAppBarColors(
            containerColor             = containerColor,
            titleContentColor          = MaterialTheme.colorScheme.onSurface,
            navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
            actionIconContentColor     = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
    )
}

// ─── Collapsible large top app bar (sub-pages) ────────────────────────────────

/**
 * Large top app bar that collapses to a small bar as the user scrolls.
 * Use with [TopAppBarDefaults.exitUntilCollapsedScrollBehavior] and
 * [androidx.compose.ui.input.nestedscroll.nestedScroll] on the host Scaffold.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LargeTopAppBar(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    scrollBehavior: TopAppBarScrollBehavior? = null,
) {
    androidx.compose.material3.LargeTopAppBar(
        modifier       = modifier,
        title          = title,
        navigationIcon = navigationIcon,
        actions        = actions,
        scrollBehavior = scrollBehavior,
    )
}

// ─── Pinned small top app bar ─────────────────────────────────────────────────

/**
 * Pinned small top app bar whose title fades in via [collapseFraction] as
 * [TopAppBarScrollBehavior.state.overlappedFraction] grows.
 * Use standalone when no large bar is present but a fade-in title is desired.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmallTopAppBar(
    modifier: Modifier = Modifier,
    titleText: String = "",
    scrollBehavior: TopAppBarScrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(),
    title: @Composable () -> Unit = {
        Text(
            text     = titleText,
            color    = MaterialTheme.colorScheme.onSurface.copy(
                alpha = collapseFraction(scrollBehavior.state.overlappedFraction),
            ),
            maxLines = 1,
        )
    },
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
) {
    TopAppBar(
        modifier       = modifier,
        title          = title,
        navigationIcon = navigationIcon,
        actions        = actions,
        scrollBehavior = scrollBehavior,
    )
}