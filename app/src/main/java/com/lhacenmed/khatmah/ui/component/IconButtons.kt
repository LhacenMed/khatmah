package com.lhacenmed.khatmah.ui.component

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import com.lhacenmed.khatmah.R
import com.lhacenmed.khatmah.ui.common.HapticFeedback.slightHapticFeedback

// AutoMirrored: icon flips automatically in RTL layouts
@Composable
fun BackButton(onClick: () -> Unit) {
    val view = LocalView.current
    IconButton(onClick = {
        onClick()
        view.slightHapticFeedback()
    }) {
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
            contentDescription = stringResource(R.string.back)
        )
    }
}