package com.lhacenmed.khatmah.core.ui.components.bottomnav

import android.annotation.SuppressLint
import android.view.View
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lhacenmed.khatmah.core.nav.NavTab

/**
 * Full-width bottom navigation bar driven by a NavTab list.
 *
 * @param screens      Ordered tab list; left→right order matches the list order.
 * @param currentRoute Active NavHost route; determines which tab appears selected.
 * @param onNavigate   Called with the destination route when a tab is tapped.
 * @param style        Visual style — [NavBarStyle.M2] (unbounded ripple, original) or
 *                     [NavBarStyle.M3] (pill indicator, bounded ripple).
 * @param anchorViewAt Lazily resolves the invisible tooltip-anchor View for a tab index.
 * @param circleScale  M2 only — ripple radius as a fraction of slot width.
 * @param modifier     Optional outer modifier.
 */
@Composable
fun BottomNavBar(
    screens: List<NavTab>,
    currentRoute: String?,
    onNavigate: (String) -> Unit,
    style: NavBarStyle = NavBarStyle.M2,
    anchorViewAt: (Int) -> View? = { null },
    circleScale: Float = 1.2f,
    @SuppressLint("ModifierParameter") modifier: Modifier = Modifier,
) {
    val bgColor         = MaterialTheme.colorScheme.surfaceContainer
    val selectedColor   = MaterialTheme.colorScheme.primary
    val unselectedColor = MaterialTheme.colorScheme.onSurfaceVariant
    val rippleColor     = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
    // M3 pill fill — secondaryContainer gives the tonal surface the spec recommends.
    val pillColor       = MaterialTheme.colorScheme.secondaryContainer

    // ⚠️  No clipToBounds for M2 — unbounded ripple overflows the bar's top edge.
    Surface(
        modifier        = modifier.fillMaxWidth(),
        color           = bgColor,
        shadowElevation = 8.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment     = Alignment.CenterVertically,
        ) {
            screens.forEachIndexed { index, screen ->
                val selected = screen.route == currentRoute
                when (style) {
                    NavBarStyle.M2 -> NavButtonM2(
                        icon            = painterResource(screen.iconRes),
                        label           = stringResource(screen.labelRes),
                        selected        = selected,
                        selectedColor   = selectedColor,
                        unselectedColor = unselectedColor,
                        rippleColor     = rippleColor,
                        circleScale     = circleScale,
                        anchorProvider  = { anchorViewAt(index) },
                        modifier        = Modifier.weight(1f),
                        onClick         = { onNavigate(screen.route) },
                    )
                    NavBarStyle.M3 -> NavButtonM3(
                        icon            = painterResource(screen.iconRes),
                        label           = stringResource(screen.labelRes),
                        selected        = selected,
                        selectedColor   = selectedColor,
                        unselectedColor = unselectedColor,
                        pillColor       = pillColor,
                        // Lambda reads directly from the live array at touch time —
                        // never a stale list snapshot that would return null permanently.
                        anchorProvider  = { anchorViewAt(index) },
                        modifier        = Modifier.weight(1f),
                        onClick         = { onNavigate(screen.route) },
                    )
                }
            }
        }
    }
}