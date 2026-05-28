package com.lhacenmed.khatmah.feature.debug

import android.annotation.SuppressLint
import android.os.Build
import android.view.MotionEvent
import androidx.annotation.RequiresApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import android.widget.ImageButton
import androidx.activity.compose.BackHandler
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lhacenmed.khatmah.R
import androidx.navigation.NavBackStackEntry
import com.lhacenmed.khatmah.core.nav.AppPage
import com.lhacenmed.khatmah.core.nav.LocalNavController
import com.lhacenmed.khatmah.core.ui.components.AppTopBar
import com.lhacenmed.khatmah.core.ui.components.createRippleDrawable
import com.lhacenmed.khatmah.core.ui.components.popupmenu.MenuItem
import com.lhacenmed.khatmah.core.ui.components.popupmenu.PopupMenu
import com.lhacenmed.khatmah.core.ui.components.popupmenu.PopupStyle
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

// ── Popup menu items for the top bar ─────────────────────────────────────────
private val TOP_BAR_MENU_ITEMS = listOf(
    MenuItem("Refresh"),
)

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun FileBrowserScreen() {
    val ctx = LocalContext.current
    val nav = LocalNavController.current
    val vm  = viewModel<FileBrowserViewModel>(factory = FileBrowserViewModel.Factory(ctx))
    val s   by vm.state.collectAsState()

    val atRoot    = s.currentDir.canonicalPath == vm.rootDir.canonicalPath
    val listState = remember(s.currentDir.path) { LazyListState() }

    // Intercept system back / predictive back gesture while inside a subdirectory
    BackHandler(enabled = !atRoot) {
        vm.openDir(s.currentDir.parentFile!!)
    }

    var selected    by remember { mutableStateOf<File?>(null) }
    var showOptions by remember { mutableStateOf(false) }
    var showRename  by remember { mutableStateOf(false) }
    var showInfo    by remember { mutableStateOf(false) }
    var renameText  by remember { mutableStateOf(TextFieldValue("")) }

    // Sync VM info signal → local dialog flag
    val infoText = s.infoText
    LaunchedEffect(infoText) { if (infoText != null) showInfo = true }

    Scaffold(
        topBar = {
            FileBrowserTopBar(
                dir            = s.currentDir,
                onBack         = {
                    if (!atRoot) vm.openDir(s.currentDir.parentFile!!)
                    else nav.popBackStack()
                },
                onMenuSelected = { item -> if (item == "Refresh") vm.openDir(s.currentDir) },
            )
        },
    ) { pad ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(pad)
        ) {
            when {
                s.loading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    CircularProgressIndicator()
                }
                s.files.isEmpty() -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    Text("Empty directory")
                }
                else -> LazyColumn(state = listState) {
                    items(s.files) { f ->
                        FileBrowserRow(
                            file        = f,
                            onClick     = { if (f.isDirectory) vm.openDir(f) },
                            onLongClick = { selected = f; showOptions = true },
                        )
                        HorizontalDivider()
                    }
                }
            }

            // ── Dialogs ───────────────────────────────────────────────────────
            FileBrowserOptionsDialog(
                visible   = showOptions && selected != null,
                file      = selected,
                onDismiss = { showOptions = false },
                onRename  = {
                    renameText  = TextFieldValue(selected!!.name)
                    showOptions = false
                    showRename  = true
                },
                onDelete  = {
                    showOptions = false
                    val ok = vm.delete(selected!!.absolutePath)
                    if (!ok) vm.showInfo("Delete failed")
                },
                onInfo    = {
                    showOptions = false
                    val meta = vm.metadata(selected!!.absolutePath)
                    vm.showInfo(meta?.let { formatMetadata(it) } ?: "Failed to load info")
                },
            )

            FileBrowserRenameDialog(
                visible   = showRename,
                value     = renameText,
                onDismiss = { showRename = false },
                onRename  = {
                    val ok = vm.rename(selected!!.absolutePath, renameText.text.trim())
                    if (!ok) vm.showInfo("Rename failed")
                    showRename = false
                },
                onValue   = { renameText = it },
            )

            if (showInfo && infoText != null) {
                AlertDialog(
                    onDismissRequest = { showInfo = false; vm.dismissInfo() },
                    title            = { Text("Info") },
                    text             = { Text(infoText) },
                    confirmButton    = {
                        TextButton(onClick = { showInfo = false; vm.dismissInfo() }) { Text("OK") }
                    },
                )
            }
        }
    }
}

// ── Top bar ───────────────────────────────────────────────────────────────────

@SuppressLint("ClickableViewAccessibility")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FileBrowserTopBar(
    dir:            File,
    onBack:         () -> Unit,
    onMenuSelected: (String) -> Unit,
) {
    val iconColor  = MaterialTheme.colorScheme.onSurface.toArgb()
    val popupStyle = PopupStyle(
        backgroundColor = MaterialTheme.colorScheme.onSecondary.toArgb(),
        contentColor    = MaterialTheme.colorScheme.onSurface.toArgb(),
        iconAlign       = false,
    )
    val popupRef = remember { arrayOfNulls<PopupMenu>(1) }

    AppTopBar(
        title      = dir.name.ifEmpty { "/" },
        subtitle   = dir.path,
        isTopLevel = false,
        onBack     = onBack,
        actions    = {
            AndroidView(
                factory = { ctx ->
                    ImageButton(ctx).apply {
                        setImageDrawable(ContextCompat.getDrawable(ctx, R.drawable.ic_ellipsis_vertical))
                        background    = createRippleDrawable(iconColor)
                        contentDescription = "More options"
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) tooltipText = "More options"
                    }
                },
                update = { view ->
                    view.setColorFilter(iconColor)
                    view.setOnClickListener {
                        if (popupRef[0]?.isShowing == true) return@setOnClickListener
                        PopupMenu(view.context, TOP_BAR_MENU_ITEMS, popupStyle) { item ->
                            onMenuSelected(item.title)
                        }.also { menu ->
                            menu.setOnDismissListener { popupRef[0] = null }
                            popupRef[0] = menu
                            menu.show(view)
                        }
                    }
                    view.setOnTouchListener { anchor, event ->
                        when (event.actionMasked) {
                            MotionEvent.ACTION_DOWN -> { view.onTouchEvent(event); true }
                            MotionEvent.ACTION_UP   -> { view.onTouchEvent(event); true }
                            else                    -> false
                        }
                    }
                },
                modifier = Modifier.size(38.dp),
            )
        },
    )
}

// ── File row ──────────────────────────────────────────────────────────────────

@Composable
private fun FileBrowserRow(
    file:        File,
    onClick:     () -> Unit,
    onLongClick: () -> Unit,
) {
    val df           = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    val lastModified = df.format(Date(file.lastModified()))

    Row(
        modifier          = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector        = if (file.isDirectory) Icons.Default.Folder else Icons.Default.Description,
            contentDescription = null,
            modifier           = Modifier.size(24.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(file.name, maxLines = 1)
            Row(
                modifier          = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text  = if (file.isDirectory) "Directory" else readableSize(file.length()),
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text  = lastModified,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

// ── Dialogs ───────────────────────────────────────────────────────────────────

@Composable
private fun FileBrowserOptionsDialog(
    visible:   Boolean,
    file:      File?,
    onDismiss: () -> Unit,
    onRename:  () -> Unit,
    onDelete:  () -> Unit,
    onInfo:    () -> Unit,
) {
    if (!visible || file == null) return
    val options = listOf("Rename" to onRename, "Delete" to onDelete, "Info" to onInfo)
    AlertDialog(
        onDismissRequest = onDismiss,
        title            = { Text(file.name) },
        text             = {
            Column {
                options.forEach { (label, action) ->
                    TextButton(
                        onClick  = action,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(label, style = MaterialTheme.typography.bodyMedium) }
                }
            }
        },
        confirmButton    = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
private fun FileBrowserRenameDialog(
    visible:   Boolean,
    value:     TextFieldValue,
    onDismiss: () -> Unit,
    onRename:  () -> Unit,
    onValue:   (TextFieldValue) -> Unit,
) {
    if (!visible) return
    AlertDialog(
        onDismissRequest = onDismiss,
        title            = { Text("Rename") },
        text             = {
            OutlinedTextField(
                value         = value,
                onValueChange = onValue,
                singleLine    = true,
                modifier      = Modifier.fillMaxWidth(),
            )
        },
        confirmButton    = { TextButton(onClick = onRename) { Text("Rename") } },
        dismissButton    = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

object FileBrowserPage : AppPage() {
    override val route = "files_browser"
    @Composable override fun Content(back: NavBackStackEntry) = FileBrowserScreen()
}