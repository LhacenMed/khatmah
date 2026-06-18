package com.lhacenmed.khatmah.feature.adhkar.ui

import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.lhacenmed.khatmah.R
import com.lhacenmed.khatmah.core.nav.LocalNavigator
import com.lhacenmed.khatmah.core.ui.components.ColorPickerDialog
import com.lhacenmed.khatmah.core.ui.components.LargeTopAppBar
import com.lhacenmed.khatmah.feature.adhkar.data.AdhkarCategory
import com.lhacenmed.khatmah.feature.adhkar.data.BuiltInDefaults
import com.lhacenmed.khatmah.feature.adhkar.data.Dhikr
import com.lhacenmed.khatmah.feature.adhkar.data.IconSource
import com.lhacenmed.khatmah.feature.adhkar.ui.components.AdhkarCard
import com.lhacenmed.khatmah.feature.adhkar.ui.components.DhikrDraftCard
import com.lhacenmed.khatmah.feature.adhkar.ui.components.DhikrDraftState
import com.lhacenmed.khatmah.feature.adhkar.ui.components.SectionLabel
import java.util.UUID

// ── Entry point ───────────────────────────────────────────────────────────────

/**
 * Unified Adhkar create / edit page.
 *
 * [categoryId] == null or blank  → create mode (blank form, no IO).
 * [categoryId] != null and not blank → edit mode (pre-filled from VM cache + state).
 *
 * Edit mode guarantee: the edit button in [AdhkarDetailPage] is only rendered after that
 * page's LaunchedEffect completes, which always calls [AdhkarViewModel.cacheDhikr] first.
 * Therefore [AdhkarViewModel.getCachedDhikr] is guaranteed to return data for the normal
 * detail → edit flow, producing zero loading frames.
 *
 * Deep-link / cold-start fallback: if the cache misses (rare), a brief spinner shows
 * while the database fetch completes, then the form renders with correct initial values.
 */
@Composable
fun AdhkarEditorScreen(categoryId: String?) {
    val activity = LocalActivity.current as ComponentActivity
    val vm: AdhkarViewModel = viewModel(activity)
    val state by vm.uiState.collectAsState()

    // routeFor(null) produces "adhkar_editor?categoryId=" → NavArgs delivers "" not null.
    // Treat blank the same as null so create mode is never mistaken for edit mode.
    val isEditMode = !categoryId.isNullOrBlank()
    val safeId     = categoryId?.takeIf { it.isNotBlank() }

    // Category metadata — sourced from the live ViewModel state (always populated when the
    // Activity-scoped VM is warm; guaranteed present when arriving from the detail page).
    val category = remember(safeId, state.categories) {
        safeId?.let { id -> state.categories.firstOrNull { it.id == id } }
    }

    // Synchronous cache read.
    // Create mode  → emptyList() instantly, no IO needed.
    // Edit + cache hit → instant (detail page caches before the edit button is tappable).
    // Edit + cache miss → null, LaunchedEffect below fetches from DB (deep-link / cold start).
    var dhikrs by remember(safeId) {
        mutableStateOf(if (!isEditMode) emptyList<Dhikr>() else vm.getCachedDhikr(safeId!!))
    }

    // Fallback: only executes on a cache miss (deep-link / cold-start edge case).
    LaunchedEffect(safeId) {
        if (safeId != null && dhikrs == null) {
            dhikrs = vm.getDhikrForCategory(safeId)
        }
    }

    // Built-in defaults resolved synchronously from in-memory data — safe inside remember.
    val builtInDefaults = remember(safeId) {
        safeId?.let { vm.getBuiltInDefaults(it) }
    }

    // Only block rendering when edit data is genuinely unavailable (deep-link / cold start).
    // Create mode:      never blocks — isEditMode is false.
    // Edit + cache hit: never blocks — dhikrs is non-null from cache.
    // Edit + cache miss: brief spinner until LaunchedEffect resolves.
    if (isEditMode && (category == null || dhikrs == null)) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    // key() ensures form state fully resets if safeId ever changes within the same back-stack entry.
    key(safeId) {
        AdhkarEditorContent(
            categoryId      = safeId,
            isEditMode      = isEditMode,
            initialTitle    = category?.title      ?: "",
            initialColor    = category?.color      ?: Color(0xFF1565C0),
            initialSpan     = category?.span       ?: 1,
            initialIconSrc  = category?.iconSource ?: IconSource.None,
            initialDhikrs   = dhikrs               ?: emptyList(),
            builtInDefaults = builtInDefaults,
            vm              = vm,
        )
    }
}

// ── Editor form ───────────────────────────────────────────────────────────────

/**
 * Stateful editor form — called only once all initial data is resolved.
 *
 * Uses [LazyColumn] so only the dhikr draft cards currently visible on screen are
 * composed. This keeps the first composition frame lightweight regardless of how
 * many dhikr the category contains, preserving smooth navigation transition animations.
 *
 * All draft state ([dhikrList]) lives in this composable's scope, not inside the
 * LazyColumn items, so state is preserved across item recycling automatically.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AdhkarEditorContent(
    categoryId:      String?,
    isEditMode:      Boolean,
    initialTitle:    String,
    initialColor:    Color,
    initialSpan:     Int,
    initialIconSrc:  IconSource,
    initialDhikrs:   List<Dhikr>,
    builtInDefaults: BuiltInDefaults?,
    vm:              AdhkarViewModel,
) {
    val nav = LocalNavigator.current

    // ── Form state ────────────────────────────────────────────────────────────
    var title     by rememberSaveable { mutableStateOf(initialTitle) }
    var cardColor by remember        { mutableStateOf(initialColor)  }
    var span      by rememberSaveable { mutableIntStateOf(initialSpan) }

    // Path to a user-picked custom image; null = use originalIconSrc.
    var iconLocalPath by rememberSaveable {
        // Initialise from the category's Uri icon in edit mode; null otherwise.
        mutableStateOf((initialIconSrc as? IconSource.Uri)?.path)
    }

    // Fixed reference to the source loaded with — used to display the Res preview
    // and compute the effective icon for saving. Uri sources are tracked by iconLocalPath.
    val originalIconSrc: IconSource = remember {
        if (initialIconSrc is IconSource.Uri) IconSource.None else initialIconSrc
    }

    val dhikrList = remember {
        mutableStateListOf<DhikrDraftState>().also { list ->
            if (initialDhikrs.isEmpty()) list.add(DhikrDraftState())
            else list.addAll(initialDhikrs.map { DhikrDraftState.from(it) })
        }
    }

    // ── Derived UI state ──────────────────────────────────────────────────────
    val isSaveable by remember {
        derivedStateOf {
            title.isNotBlank() && dhikrList.isNotEmpty() && dhikrList.all { it.isValid }
        }
    }

    /**
     * True when the current form state diverges from the original built-in defaults.
     * Uses [derivedStateOf] so it recomputes lazily only when observed snapshot state changes.
     */
    val hasChanges by remember(builtInDefaults) {
        derivedStateOf {
            if (builtInDefaults == null) false
            else title != builtInDefaults.title     ||
                    cardColor != builtInDefaults.color ||
                    span != builtInDefaults.span       ||
                    iconLocalPath != null              ||
                    dhikrList.map { it.toDhikr() } != builtInDefaults.dhikrList
        }
    }

    // ── Dialog state ──────────────────────────────────────────────────────────
    var showColorPicker by remember { mutableStateOf(false) }
    if (showColorPicker) {
        ColorPickerDialog(
            initialColor    = cardColor,
            onColorSelected = { cardColor = it; showColorPicker = false },
            onDismiss       = { showColorPicker = false },
        )
    }

    // ── Image picker ──────────────────────────────────────────────────────────
    val imageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { vm.persistImage(it) { path -> if (path != null) iconLocalPath = path } }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────
    /** Resolves the icon source to persist: custom image > original Res/None. */
    fun effectiveIconSrc(): IconSource =
        if (iconLocalPath != null) IconSource.Uri(iconLocalPath!!) else originalIconSrc

    fun save() {
        val category = AdhkarCategory(
            id         = categoryId ?: UUID.randomUUID().toString(),
            title      = title.trim(),
            iconSource = effectiveIconSrc(),
            color      = cardColor,
            span       = span,
        )
        val items = dhikrList.map { it.toDhikr() }
        if (isEditMode && categoryId != null) vm.updateCategory(category, items)
        else vm.addCategory(category, items)
        nav.back()
    }

    fun resetToDefaults() {
        if (categoryId != null) {
            vm.resetCategoryToDefaults(categoryId)
            nav.back()
        }
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        topBar = {
            LargeTopAppBar(
                title          = {
                    Text(
                        if (isEditMode) initialTitle
                        else stringResource(R.string.add_adhkar)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { nav.back() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.navigate_up))
                    }
                },
                actions        = {
                    // "Reset to Default" — only for built-in categories when changed
                    AnimatedVisibility(visible = isEditMode && builtInDefaults != null && hasChanges) {
                        TextButton(
                            onClick = ::resetToDefaults,
                            colors  = ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.error,
                            ),
                        ) { Text(stringResource(R.string.adhkar_reset_defaults)) }
                    }
                    // Primary action: "Create" or "Save"
                    TextButton(
                        onClick  = ::save,
                        enabled  = isSaveable,
                        colors   = ButtonDefaults.textButtonColors(
                            contentColor         = MaterialTheme.colorScheme.primary,
                            disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                        ),
                    ) {
                        Text(stringResource(if (isEditMode) R.string.save else R.string.create))
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
    ) { padding ->
        // LazyColumn: only the dhikr draft cards currently on screen are composed,
        // making the initial frame cheap and the enter-transition animation smooth.
        // Draft state lives in dhikrList above, so it survives item recycling.
        LazyColumn(
            modifier            = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding      = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            // ── Live preview ──────────────────────────────────────────────────
            item(key = "lbl_preview") {
                SectionLabel(stringResource(R.string.adhkar_preview))
            }
            item(key = "preview_card") {
                AdhkarCard(
                    category = AdhkarCategory(
                        id         = "preview",
                        title      = title.ifBlank { stringResource(R.string.adhkar_title_placeholder) },
                        iconSource = effectiveIconSrc(),
                        color      = cardColor,
                        span       = 2,
                    ),
                    onClick  = {},
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // ── Card appearance ───────────────────────────────────────────────
            item(key = "lbl_appearance") {
                SectionLabel(stringResource(R.string.adhkar_card_section))
            }
            item(key = "title_field") {
                OutlinedTextField(
                    value           = title,
                    onValueChange   = { title = it },
                    label           = { Text(stringResource(R.string.adhkar_title_label)) },
                    placeholder     = { Text(stringResource(R.string.adhkar_title_placeholder)) },
                    singleLine      = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier        = Modifier.fillMaxWidth(),
                )
            }
            item(key = "color_row") {
                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        stringResource(R.string.adhkar_color_label),
                        style    = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Box(
                        Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(cardColor)
                            .border(2.dp, MaterialTheme.colorScheme.outline, CircleShape)
                            .clickable { showColorPicker = true }
                    )
                }
            }
            item(key = "icon_row") {
                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        stringResource(R.string.adhkar_icon_label),
                        style    = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    when {
                        // User picked a custom image
                        iconLocalPath != null -> {
                            AsyncImage(
                                model              = iconLocalPath,
                                contentDescription = null,
                                modifier           = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                            )
                            IconButton(onClick = { iconLocalPath = null }) {
                                Icon(Icons.Outlined.Close, stringResource(R.string.adhkar_remove_icon))
                            }
                        }
                        // Built-in Res icon shown as a tinted read-only preview; user can replace it
                        originalIconSrc is IconSource.Res -> {
                            Icon(
                                painter            = painterResource(originalIconSrc.resId),
                                contentDescription = null,
                                tint               = cardColor.copy(alpha = 0.7f),
                                modifier           = Modifier.size(40.dp),
                            )
                            FilledTonalIconButton(onClick = { imageLauncher.launch("image/*") }) {
                                Icon(Icons.Outlined.Image, stringResource(R.string.adhkar_replace_icon))
                            }
                        }
                        // No icon — offer to pick one
                        else -> {
                            FilledTonalIconButton(onClick = { imageLauncher.launch("image/*") }) {
                                Icon(Icons.Outlined.Image, stringResource(R.string.adhkar_pick_image))
                            }
                        }
                    }
                }
            }
            item(key = "span_selector") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        stringResource(R.string.adhkar_card_width_label),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        SegmentedButton(
                            selected = span == 1,
                            onClick  = { span = 1 },
                            shape    = SegmentedButtonDefaults.itemShape(0, 2),
                            label    = { Text(stringResource(R.string.adhkar_half_width)) },
                        )
                        SegmentedButton(
                            selected = span == 2,
                            onClick  = { span = 2 },
                            shape    = SegmentedButtonDefaults.itemShape(1, 2),
                            label    = { Text(stringResource(R.string.adhkar_full_width)) },
                        )
                    }
                }
            }

            // ── Adhkar content ────────────────────────────────────────────────
            item(key = "divider") { HorizontalDivider() }
            item(key = "lbl_content") {
                SectionLabel(stringResource(R.string.adhkar_content_section))
            }

            // Draft cards — only visible items are composed at any time.
            // dhikrList lives in the parent scope; state survives LazyColumn recycling.
            itemsIndexed(
                items = dhikrList,
                key   = { _, draft -> draft.key },
            ) { idx, draft ->
                DhikrDraftCard(
                    index     = idx + 1,
                    draft     = draft,
                    canDelete = dhikrList.size > 1,
                    onDelete  = { dhikrList.removeAt(idx) },
                )
            }

            item(key = "add_dhikr_btn") {
                OutlinedButton(
                    onClick  = { dhikrList.add(DhikrDraftState()) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Outlined.Add, null)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.adhkar_add_dhikr))
                }
            }

            item(key = "bottom_spacer") { Spacer(Modifier.height(16.dp)) }
        }
    }
}
