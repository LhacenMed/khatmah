package com.lhacenmed.khatmah.feature.quran.ui.reader

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.lhacenmed.khatmah.R

/**
 * Shown when an action needs the page-based QCF4 book reader but a text print is selected — e.g.
 * opening a Khatmah session or a sunnah surah. Offers jumping to the mushaf print picker.
 *
 * Shared by the Today and More tabs so the "needs QCF4" prompt is identical everywhere.
 */
@Composable
fun MushafDownloadDialog(onSettings: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title            = { Text(stringResource(R.string.today_dl_title)) },
        text             = { Text(stringResource(R.string.today_dl_msg)) },
        confirmButton    = {
            TextButton(onClick = onSettings) { Text(stringResource(R.string.today_settings)) }
        },
        dismissButton    = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.today_cancel)) }
        },
    )
}
