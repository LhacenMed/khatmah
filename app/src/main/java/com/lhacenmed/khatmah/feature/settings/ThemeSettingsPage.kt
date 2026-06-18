package com.lhacenmed.khatmah.feature.settings

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import com.lhacenmed.khatmah.R
import com.lhacenmed.khatmah.core.nav.Dest
import com.lhacenmed.khatmah.core.nav.LocalNavigator
import com.lhacenmed.khatmah.core.ui.components.IconButton
import com.lhacenmed.khatmah.core.ui.components.LargeTopAppBar
import com.lhacenmed.khatmah.core.ui.components.PreferenceItem
import com.lhacenmed.khatmah.core.ui.components.PreferenceSubtitle
import com.lhacenmed.khatmah.core.ui.components.PreferenceSwitch
import com.lhacenmed.khatmah.shared.util.ThemeManager
import com.lhacenmed.khatmah.core.ui.theme.ThemeColor
import com.lhacenmed.khatmah.core.ui.theme.colorPreferences
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight


/** Appearance settings page. */

// ── Screen ────────────────────────────────────────────────────────────────────

@RequiresApi(Build.VERSION_CODES.O)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ThemeSettingsScreen() {
    val nav            = LocalNavigator.current
    val context        = LocalContext.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    val isDynamicColor by ThemeManager.dynamicColor.collectAsState()
    val selectedColor  by ThemeManager.colorIndex.collectAsState()
    val themeMode      by ThemeManager.mode.collectAsState()
    val isDark         = isSystemInDarkTheme()

    // Tooltip anchor tracks the bar's actual height
    val tooltipAnchorBottom = lerp(20.dp, 55.dp, scrollBehavior.state.collapsedFraction)

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar   = {
            LargeTopAppBar(
                title          = { Text(stringResource(R.string.theme_settings)) },
                navigationIcon = {
                    IconButton(
                        onClick           = { nav.back() },
                        tooltipText       = stringResource(R.string.navigate_up),
                        anchorExtraBottom = tooltipAnchorBottom,
                    ) {
                        Icon(
                            imageVector        = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.navigate_up),
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            PreferenceSubtitle(text = stringResource(R.string.more_settings))

            PreferenceItem(
                title = stringResource(R.string.theme_dark),
                description = stringResource(
                    when (themeMode) {
                        AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM -> R.string.theme_system
                        AppCompatDelegate.MODE_NIGHT_NO            -> R.string.theme_light
                        else                                       -> R.string.theme_dark
                    }
                ),
                onClick = { nav.go(Dest.DarkTheme) },
                trailingIcon = {
                    Icon(
                        imageVector        = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                    )
                }
            )

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PreferenceSwitch(
                    title     = stringResource(R.string.theme_dynamic_color),
                    description = stringResource(R.string.theme_dynamic_color_desc),
                    isChecked = isDynamicColor,
                    onClick   = { ThemeManager.setDynamicColorEnabled(context, !isDynamicColor) }
                )
            }

            PreferenceSubtitle(text = stringResource(R.string.theme_palette_group))

            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                itemsIndexed(colorPreferences) { index, themeColor ->
                    ColorButton(
                        themeColor  = themeColor,
                        isDark      = isDark,
                        isSelected  = !isDynamicColor && selectedColor == index,
                        onClick     = {
                            ThemeManager.setDynamicColorEnabled(context, false)
                            ThemeManager.setColorIndex(context, index)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ColorButton(
    themeColor: ThemeColor,
    isDark: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val primaryColor  = if (isDark) themeColor.primaryDark else themeColor.primaryLight
    val containerSize by animateDpAsState(
        targetValue = if (isSelected) 28.dp else 0.dp, label = "container"
    )
    val iconSize by animateDpAsState(
        targetValue = if (isSelected) 16.dp else 0.dp, label = "icon"
    )

    Surface(
        onClick   = onClick,
        modifier  = Modifier.padding(4.dp).size(64.dp),
        shape     = RoundedCornerShape(16.dp),
        color     = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier         = Modifier.fillMaxSize(),
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .drawBehind { drawCircle(primaryColor) },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .size(containerSize)
                        .drawBehind {
                            drawCircle(Color.Black.copy(alpha = 0.2f))
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector        = Icons.Default.Check,
                        contentDescription = null,
                        modifier           = Modifier.size(iconSize),
                        tint               = Color.White,
                    )
                }
            }
        }
    }
}
