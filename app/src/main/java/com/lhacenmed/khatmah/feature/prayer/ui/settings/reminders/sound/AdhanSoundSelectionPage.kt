package com.lhacenmed.khatmah.feature.prayer.ui.settings.reminders.sound

import android.content.Intent
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeOff
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lhacenmed.khatmah.R
import com.lhacenmed.khatmah.core.ui.components.PreferenceItem
import com.lhacenmed.khatmah.core.ui.components.PreferenceSubtitle
import com.lhacenmed.khatmah.feature.prayer.notification.AdhanConfig
import com.lhacenmed.khatmah.feature.prayer.notification.AdhanPrefs
import com.lhacenmed.khatmah.feature.prayer.notification.AdhanScheduler
import com.lhacenmed.khatmah.feature.prayer.notification.AdhanSound
import com.lhacenmed.khatmah.shared.reminders.ReminderNotifier
import com.lhacenmed.khatmah.shared.util.AdhanSoundFiles
import com.lhacenmed.khatmah.core.ui.theme.applyOpacity
import androidx.core.net.toUri

// ── Pre-alert options ─────────────────────────────────────────────────────────
private val PRE_ALERT_OPTIONS = listOf(0, 5, 10, 15, 20, 25, 30)

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun AdhanSoundSelectionScreen(prayerId: Int) {
    val context = LocalContext.current
    val configs by AdhanPrefs.flow.collectAsState()
    val customSounds by AdhanPrefs.customSoundsFlow.collectAsState()
    val config = configs.getOrElse(prayerId) { AdhanConfig() }

    // Discover available asset sounds once.
    val assetSounds: List<AdhanSound.Asset> = remember {
        AdhanSoundFiles.list(context).map { AdhanSound.Asset(it) }
    }

    val prayerNames = listOf(
        R.string.prayer_fajr, R.string.prayer_sunrise, R.string.prayer_dhuhr,
        R.string.prayer_asr, R.string.prayer_maghrib, R.string.prayer_isha,
    )
    val prayerName = stringResource(prayerNames.getOrElse(prayerId) { R.string.prayers })

    var showPreDialog by remember { mutableStateOf(false) }

    // Active MediaPlayer for previewing sounds — released on selection or page exit.
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    DisposableEffect(Unit) { onDispose { mediaPlayer?.release() } }

    // ── File picker ───────────────────────────────────────────────────────────
    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        // Persist access so we can read the file across reboots (notification channel).
        context.contentResolver.takePersistableUriPermission(
            uri, Intent.FLAG_GRANT_READ_URI_PERMISSION,
        )
        val displayName = context.resolveAudioName(uri)
        val custom = AdhanSound.Custom(uri.toString(), displayName)
        ReminderNotifier.ensureCustomAdhanChannel(context, uri.toString(), displayName)
        saveSound(context, prayerId, config, custom)
        AdhanScheduler.schedulePrayer(context, prayerId)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    fun saveSound(sound: AdhanSound) {
        mediaPlayer?.release(); mediaPlayer = null
        AdhanPrefs.save(context, prayerId, config.copy(sound = sound))
        AdhanScheduler.schedulePrayer(context, prayerId)
    }

    fun savePreAlert(minutes: Int) {
        AdhanPrefs.save(context, prayerId, config.copy(preAlertMinutes = minutes))
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

    fun previewCustom(uri: String) {
        mediaPlayer?.release()
        mediaPlayer = MediaPlayer().apply {
            setDataSource(context, uri.toUri())
            prepare()
            start()
        }
    }

    // ── UI ────────────────────────────────────────────────────────────────────

    // Body only — the dynamic title + back come from ScreenHostActivity (see Dest.AdhanSoundSelection).
    // (Back releases the MediaPlayer via the DisposableEffect's onDispose above.)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        // ── Pre-alert section ─────────────────────────────────────────────
        PreferenceSubtitle(text = stringResource(R.string.adhan_alarm_before_section))

        PreferenceItem(
            title = stringResource(R.string.adhan_alert_before),
            enabled = config.isEnabled,
            onClick = { if (config.isEnabled) showPreDialog = true },
            trailingIcon = {
                Text(
                    text = preAlertLabel(config.preAlertMinutes),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.applyOpacity(config.isEnabled)
                )
            }
        )

        HorizontalDivider()

        // ── Built-in sound section ────────────────────────────────────────
        PreferenceSubtitle(text = stringResource(R.string.adhan_sound_section_format, prayerName))

        FixedSoundItem(
            label = stringResource(R.string.adhan_sound_stop),
            icon = Icons.Outlined.NotificationsOff,
            selected = config.sound is AdhanSound.Off,
            onClick = { saveSound(AdhanSound.Off) },
        )
        FixedSoundItem(
            label = stringResource(R.string.adhan_sound_silent),
            icon = Icons.AutoMirrored.Outlined.VolumeOff,
            selected = config.sound is AdhanSound.Silent,
            onClick = { saveSound(AdhanSound.Silent) },
        )
        FixedSoundItem(
            label = stringResource(R.string.adhan_sound_device),
            icon = Icons.Outlined.Notifications,
            selected = config.sound is AdhanSound.Device,
            onClick = { saveSound(AdhanSound.Device) },
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
                filename = assetSound.filename,
                displayName = AdhanSoundFiles.getDisplayName(assetSound.filename),
                selected = config.sound is AdhanSound.Asset &&
                        config.sound.filename == assetSound.filename,
                onSelect = { saveSound(assetSound) },
                onPreview = { previewAsset(assetSound.filename) },
            )
        }

        HorizontalDivider()

        // ── Custom file section ───────────────────────────────────────────
        PreferenceSubtitle(text = stringResource(R.string.adhan_sound_custom_section))

        // Show all previously picked custom sounds
        customSounds.forEach { custom ->
            CustomSoundItem(
                displayName = custom.displayName,
                selected = config.sound is AdhanSound.Custom && config.sound.uri == custom.uri,
                onSelect = { saveSound(custom) },
                onPreview = { previewCustom(custom.uri) },
            )
        }

        BrowseItem { filePicker.launch(arrayOf("audio/*")) }

        Spacer(Modifier.height(16.dp))
    }

    // ── Pre-alert dialog ──────────────────────────────────────────────────────
    if (showPreDialog) {
        PreAlertDialog(
            currentMinutes = config.preAlertMinutes,
            onConfirm = { mins -> savePreAlert(mins); showPreDialog = false },
            onDismiss = { showPreDialog = false },
        )
    }
}

// ── Private helpers ───────────────────────────────────────────────────────────

/** Resolves the display name of a content URI (e.g. "My Adhan.mp3"). */
private fun android.content.Context.resolveAudioName(uri: Uri): String =
    contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        ?.use { if (it.moveToFirst()) it.getString(0) else null }
        ?: uri.lastPathSegment
        ?: "Custom"

/** Saves a sound without touching preAlertMinutes — avoids duplicating scheduler call. */
private fun saveSound(
    context: android.content.Context,
    prayerId: Int,
    config: AdhanConfig,
    sound: AdhanSound,
) {
    AdhanPrefs.save(context, prayerId, config.copy(sound = sound))
}

// ─── Composables ──────────────────────────────────────────────────────────────

@Composable
private fun FixedSoundItem(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    onPreview: (() -> Unit)? = null,
) {
    PreferenceItem(
        title = label,
        onClick = onClick,
        leadingIcon = {
            if (onPreview != null) {
                Box(
                    modifier = Modifier
                        .padding(start = 16.dp, end = 20.dp)
                        .size(24.dp)
                        .clickable(
                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                            indication = null,
                            onClick = onPreview
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Outlined.PlayArrow, contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Icon(
                    icon, contentDescription = null,
                    modifier = Modifier
                        .padding(start = 16.dp, end = 20.dp)
                        .size(24.dp),
                    tint = if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            }
        },
        trailingIcon = {
            RadioButton(
                selected = selected,
                onClick = null,
                modifier = Modifier.padding(end = 8.dp)
            )
        }
    )
}

@Composable
private fun AssetSoundItem(
    filename: String,
    displayName: String,
    selected: Boolean,
    onSelect: () -> Unit,
    onPreview: () -> Unit,
) {
    PreferenceItem(
        title = displayName,
        onClick = onSelect,
        leadingIcon = {
            Box(
                modifier = Modifier
                    .padding(start = 16.dp, end = 20.dp)
                    .size(24.dp)
                    .clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null,
                        onClick = onPreview
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Outlined.PlayArrow, contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        trailingIcon = {
            RadioButton(
                selected = selected,
                onClick = null,
                modifier = Modifier.padding(end = 8.dp)
            )
        }
    )
}

@Composable
private fun CustomSoundItem(
    displayName: String,
    selected: Boolean,
    onSelect: () -> Unit,
    onPreview: () -> Unit,
) {
    PreferenceItem(
        title = displayName,
        onClick = onSelect,
        leadingIcon = {
            Box(
                modifier = Modifier
                    .padding(start = 16.dp, end = 20.dp)
                    .size(24.dp)
                    .clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null,
                        onClick = onPreview
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Outlined.PlayArrow, contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        trailingIcon = {
            RadioButton(
                selected = selected,
                onClick = null,
                modifier = Modifier.padding(end = 8.dp)
            )
        }
    )
}

@Composable
private fun BrowseItem(onClick: () -> Unit) {
    PreferenceItem(
        title = stringResource(R.string.adhan_sound_custom_browse),
        icon = Icons.Outlined.FolderOpen,
        onClick = onClick,
    )
}

@Composable
private fun PreAlertDialog(
    currentMinutes: Int,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var selected by remember { mutableIntStateOf(currentMinutes) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.adhan_alert_before)) },
        text = {
            Column {
                PRE_ALERT_OPTIONS.forEach { mins ->
                    Row(
                        modifier = Modifier
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
    0 -> stringResource(R.string.adhan_alert_before_off)
    else -> stringResource(R.string.adhan_alert_before_minutes, minutes)
}
