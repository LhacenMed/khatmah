package com.lhacenmed.khatmah.ui.page.settings.prayers.reminders

import android.media.MediaPlayer
import android.os.Build
import android.media.RingtoneManager
import androidx.annotation.RequiresApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeOff
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lhacenmed.khatmah.R
import com.lhacenmed.khatmah.data.prayer.AdhanConfig
import com.lhacenmed.khatmah.data.prayer.AdhanPrefs
import com.lhacenmed.khatmah.data.prayer.AdhanSound
import com.lhacenmed.khatmah.notification.AdhanScheduler
import com.lhacenmed.khatmah.ui.component.AppTopBar
import com.lhacenmed.khatmah.ui.nav.LocalNavController
import com.lhacenmed.khatmah.util.AdhanSoundFiles

// ── Pre-alert options ─────────────────────────────────────────────────────────
private val PRE_ALERT_OPTIONS = listOf(0, 5, 10, 15, 20, 25, 30)

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun AdhanSoundSelectionPage(prayerId: Int) {
    val nav     = LocalNavController.current
    val context = LocalContext.current
    val configs by AdhanPrefs.flow.collectAsState()
    val config   = configs.getOrElse(prayerId) { AdhanConfig() }

    // Discover available asset sounds once.
    val assetSounds: List<AdhanSound.Asset> = remember {
        AdhanSoundFiles.list(context).map { AdhanSound.Asset(it) }
    }

    val prayerNames = listOf(
        R.string.prayer_fajr, R.string.prayer_sunrise, R.string.prayer_dhuhr,
        R.string.prayer_asr,  R.string.prayer_maghrib, R.string.prayer_isha,
    )
    val prayerName = stringResource(prayerNames.getOrElse(prayerId) { R.string.prayers })

    var showPreDialog by remember { mutableStateOf(false) }

    // Active MediaPlayer for previewing sounds — released on selection or page exit.
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    DisposableEffect(Unit) { onDispose { mediaPlayer?.release() } }

    fun saveSound(sound: AdhanSound) {
        mediaPlayer?.release(); mediaPlayer = null
        val updated = config.copy(sound = sound)
        AdhanPrefs.save(context, prayerId, updated)
        AdhanScheduler.schedulePrayer(context, prayerId)
    }

    fun savePreAlert(minutes: Int) {
        val updated = config.copy(preAlertMinutes = minutes)
        AdhanPrefs.save(context, prayerId, updated)
        AdhanScheduler.schedulePrayer(context, prayerId)
    }

    fun previewAsset(filename: String) {
        mediaPlayer?.release()
        mediaPlayer = MediaPlayer().apply {
            val afd = context.assets.openFd("adhan/$filename")
            setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
            afd.close()
            prepare()
            start()
        }
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title      = stringResource(R.string.adhan_alarm_title_format, prayerName),
                isTopLevel = false,
                onBack     = { mediaPlayer?.release(); nav.popBackStack() },
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            // ── Pre-alert section ─────────────────────────────────────────────
            SelectionHeader(stringResource(R.string.adhan_alarm_before_section))

            ListItem(
                modifier = Modifier.clickable(enabled = config.isEnabled) {
                    if (config.isEnabled) showPreDialog = true
                },
                headlineContent   = { Text(stringResource(R.string.adhan_alert_before)) },
                supportingContent = {
                    val alpha = if (config.isEnabled) 1f else 0.38f
                    Text(
                        text  = preAlertLabel(config.preAlertMinutes),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
                    )
                },
            )

            Spacer(Modifier.height(8.dp))
            HorizontalDivider(thickness = 8.dp, color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))

            // ── Sound section ─────────────────────────────────────────────────
            SelectionHeader(stringResource(R.string.adhan_sound_section_format, prayerName))

            // Fixed options: Off, Silent, Device
            FixedSoundItem(
                label     = stringResource(R.string.adhan_sound_stop),
                icon      = Icons.Outlined.NotificationsOff,
                selected  = config.sound is AdhanSound.Off,
                onClick   = { saveSound(AdhanSound.Off) },
            )
            FixedSoundItem(
                label     = stringResource(R.string.adhan_sound_silent),
                icon      = Icons.AutoMirrored.Outlined.VolumeOff,
                selected  = config.sound is AdhanSound.Silent,
                onClick   = { saveSound(AdhanSound.Silent) },
            )
            FixedSoundItem(
                label     = stringResource(R.string.adhan_sound_device),
                icon      = Icons.Outlined.Notifications,
                selected  = config.sound is AdhanSound.Device,
                onClick   = { saveSound(AdhanSound.Device) },
                onPreview = {
                    mediaPlayer?.release()
                    val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                    mediaPlayer = MediaPlayer().apply {
                        setDataSource(context, uri)
                        prepare()
                        start()
                    }
                },
            )

            // Asset sounds — autopopulated from assets/adhan/
            assetSounds.forEach { assetSound ->
                AssetSoundItem(
                    filename  = assetSound.filename,
                    selected  = config.sound is AdhanSound.Asset &&
                            config.sound.filename == assetSound.filename,
                    onSelect  = { saveSound(assetSound) },
                    onPreview = { previewAsset(assetSound.filename) },
                )
            }

            Spacer(Modifier.height(16.dp))
        }
    }

    // ── Pre-alert dialog ──────────────────────────────────────────────────────
    if (showPreDialog) {
        PreAlertDialog(
            currentMinutes = config.preAlertMinutes,
            onConfirm      = { mins -> savePreAlert(mins); showPreDialog = false },
            onDismiss      = { showPreDialog = false },
        )
    }
}

// ─── Composables ──────────────────────────────────────────────────────────────

@Composable
private fun SelectionHeader(text: String) {
    Box(
        modifier         = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(text, style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun FixedSoundItem(
    label:     String,
    icon:      ImageVector,
    selected:  Boolean,
    onClick:   () -> Unit,
    onPreview: (() -> Unit)? = null,
) {
    ListItem(
        modifier          = Modifier.clickable { onClick() },
        headlineContent   = {
            Text(label, color = if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurface)
        },
        leadingContent    = { RadioButton(selected = selected, onClick = null) },
        trailingContent   = {
            if (onPreview != null) {
                IconButton(onClick = onPreview) {
                    Icon(
                        Icons.Outlined.PlayArrow,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                Icon(icon, contentDescription = null,
                    tint = if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
            }
        },
    )
}

@Composable
private fun AssetSoundItem(
    filename:  String,
    selected:  Boolean,
    onSelect:  () -> Unit,
    onPreview: () -> Unit,
) {
    val label = filename.removeSuffix(".mp3")
    ListItem(
        modifier          = Modifier.clickable { onSelect() },
        headlineContent   = {
            Text(label, color = if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurface)
        },
        leadingContent    = { RadioButton(selected = selected, onClick = null) },
        trailingContent   = {
            IconButton(onClick = onPreview) {
                Icon(
                    Icons.Outlined.PlayArrow,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    )
}

@Composable
private fun PreAlertDialog(
    currentMinutes: Int,
    onConfirm:      (Int) -> Unit,
    onDismiss:      () -> Unit,
) {
    var selected by remember { mutableIntStateOf(currentMinutes) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.adhan_alert_before)) },
        text  = {
            Column {
                PRE_ALERT_OPTIONS.forEach { mins ->
                    Row(
                        modifier          = Modifier
                            .fillMaxWidth()
                            .clickable { selected = mins }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = selected == mins, onClick = { selected = mins })
                        Spacer(Modifier.width(8.dp))
                        Text(preAlertLabel(mins))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(selected) }) {
                Text(stringResource(R.string.dialog_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.dialog_cancel))
            }
        },
    )
}

@Composable
private fun preAlertLabel(minutes: Int): String = when (minutes) {
    0    -> stringResource(R.string.adhan_alert_before_off)
    else -> stringResource(R.string.adhan_alert_before_minutes, minutes)
}