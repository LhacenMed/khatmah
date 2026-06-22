package com.lhacenmed.khatmah.feature.update.ui

import android.os.Build
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lhacenmed.khatmah.R
import com.lhacenmed.khatmah.feature.update.UpdateInstaller
import com.lhacenmed.khatmah.feature.update.UpdateRegistry
import com.lhacenmed.khatmah.feature.update.UpdateService
import com.lhacenmed.khatmah.feature.update.UpdateState

/**
 * Launch-time update prompt, rendered at the root of [com.lhacenmed.khatmah.core.MainActivity].
 * Appears only once [UpdateRegistry.available] is set and walks the user through download →
 * install while mirroring the live [UpdateState]. Dismissing during a download leaves the
 * foreground service running (the notification carries the progress); re-opening the app re-reads
 * the same flow and resumes the dialog where it left off.
 */
@Composable
fun UpdateGate() {
    val update  by UpdateRegistry.available.collectAsState()
    val state   by UpdateRegistry.state.collectAsState()
    val context  = LocalContext.current
    var dismissed by rememberSaveable { mutableStateOf(false) }

    val available = update ?: return
    if (dismissed) return

    val downloading = state is UpdateState.Connecting || state is UpdateState.Downloading

    AlertDialog(
        // A download in flight is non-cancelable from the scrim; the buttons drive it instead.
        onDismissRequest = { if (!downloading) dismissed = true },
        title = { Text(stringResource(R.string.update_title)) },
        text = {
            Column {
                Text(stringResource(R.string.update_message, available.versionName))
                if (available.notes.isNotBlank()) {
                    Text(
                        text     = available.notes,
                        style    = MaterialTheme.typography.bodySmall,
                        color    = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                when (val s = state) {
                    is UpdateState.Downloading -> {
                        val p = s.progress
                        if (p != null) {
                            LinearProgressIndicator(
                                progress = { p },
                                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                            )
                        } else {
                            LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 16.dp))
                        }
                        Text(s.log, style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 8.dp))
                    }
                    UpdateState.Connecting ->
                        LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 16.dp))
                    is UpdateState.Error ->
                        Text(s.message, color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(top = 12.dp))
                    else -> Unit
                }
            }
        },
        confirmButton = {
            when (val s = state) {
                is UpdateState.Downloaded -> TextButton(onClick = {
                    // Grant present → launch the installer; otherwise send the user to grant it once.
                    if (UpdateInstaller.canInstall(context)) {
                        context.startActivity(UpdateInstaller.installIntent(context, s.apk))
                    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startActivity(UpdateInstaller.requestPermissionIntent(context))
                    }
                }) { Text(stringResource(R.string.update_install)) }

                is UpdateState.Error -> TextButton(onClick = { UpdateService.start(context) }) {
                    Text(stringResource(R.string.update_retry))
                }

                UpdateState.Connecting, is UpdateState.Downloading -> Unit

                else -> TextButton(onClick = { UpdateService.start(context) }) {
                    Text(stringResource(R.string.update_download))
                }
            }
        },
        dismissButton = {
            if (!downloading) {
                TextButton(onClick = { dismissed = true }) {
                    Text(stringResource(R.string.update_later))
                }
            }
        },
    )
}
