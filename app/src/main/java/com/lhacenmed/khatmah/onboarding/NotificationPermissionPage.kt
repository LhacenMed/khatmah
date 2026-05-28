package com.lhacenmed.khatmah.onboarding

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import com.lhacenmed.khatmah.R
import com.lhacenmed.khatmah.core.nav.LocalNavController
import com.lhacenmed.khatmah.core.nav.ShellRoutes

/**
 * Onboarding step 1 — POST_NOTIFICATIONS runtime permission (API 33+).
 *
 * Auto-advances if permission is already granted (e.g. after a reinstall).
 * "Skip" and the post-denial action both advance without the permission,
 * so the rest of the app works — notifications simply won't fire.
 */
@Composable
fun NotificationPermissionPage() {
    val nav     = LocalNavController.current
    val context = LocalContext.current

    // Navigate to the location step, removing this page from the back stack.
    fun advance() = nav.navigate(ShellRoutes.ONBOARDING_LOCATION) {
        popUpTo(ShellRoutes.ONBOARDING_NOTIFICATIONS) { inclusive = true }
    }

    // Auto-advance when already granted or on pre-TIRAMISU devices.
    LaunchedEffect(Unit) {
        val alreadyGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        if (alreadyGranted) advance()
    }

    var denied by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) advance() else denied = true }

    OnboardingPage(
        icon        = Icons.Outlined.Notifications,
        title       = stringResource(R.string.onboarding_notif_title),
        description = if (denied) stringResource(R.string.onboarding_notif_denied)
        else        stringResource(R.string.onboarding_notif_desc),
        actionLabel = stringResource(
            if (denied) R.string.onboarding_notif_skip else R.string.onboarding_notif_action
        ),
        onAction = {
            when {
                denied -> advance()
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ->
                    launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                else -> advance()
            }
        },
        skipLabel = stringResource(R.string.skip),
        onSkip    = ::advance,
    )
}