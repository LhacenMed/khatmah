package com.lhacenmed.khatmah.ui.page.tabs.adhkar

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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
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
import com.lhacenmed.khatmah.data.adhkar.AdhkarCategory
import com.lhacenmed.khatmah.data.adhkar.BuiltInDefaults
import com.lhacenmed.khatmah.data.adhkar.Dhikr
import com.lhacenmed.khatmah.data.adhkar.DhikrParagraph
import com.lhacenmed.khatmah.data.adhkar.IconSource
import com.lhacenmed.khatmah.ui.component.AdhkarCard
import com.lhacenmed.khatmah.ui.component.ColorPickerDialog
import com.lhacenmed.khatmah.ui.component.LargeTopAppBar
import com.lhacenmed.khatmah.ui.nav.LocalNavController
import java.util.UUID

// ── Draft models (composition-scoped, not persisted) ─────────────────────────

private enum class ParagraphDraftType(val labelRes: Int) {
    BODY(R.string.adhkar_paragraph_body),
    QURAN(R.string.adhkar_paragraph_quran),
    NOTE(R.string.adhkar_paragraph_note),
}

private class ParagraphDraftState {
    var type by mutableStateOf(ParagraphDraftType.BODY)
    var text by mutableStateOf("")
    val key = UUID.randomUUID().toString()

    fun toParagraph(): DhikrParagraph? =
        if (text.isBlank()) null else when (type) {
            ParagraphDraftType.BODY  -> DhikrParagraph.Body(text)
            ParagraphDraftType.QURAN -> DhikrParagraph.Quran(text)
            ParagraphDraftType.NOTE  -> DhikrParagraph.Note(text)
        }
}

private class DhikrDraftState {
    val key = UUID.randomUUID().toString()
    var repetitions by mutableIntStateOf(1)
    val paragraphs = mutableStateListOf(ParagraphDraftState())

    val isValid: Boolean get() = paragraphs.any { it.text.isNotBlank() }

    fun toDhikr(): Dhikr = Dhikr(
        paragraphs  = paragraphs.mapNotNull { it.toParagraph() },
        repetitions = repetitions,
    )

    companion object {
        /** Builds a draft pre-filled from an existing [Dhikr]. */
        fun from(dhikr: Dhikr): DhikrDraftState = DhikrDraftState().apply {
            repetitions = dhikr.repetitions
            paragraphs.clear()
            paragraphs.addAll(dhikr.paragraphs.map { para ->
                ParagraphDraftState().apply {
                    when (para) {
                        is DhikrParagraph.Body  -> { type = ParagraphDraftType.BODY;  text = para.text }
                        is DhikrParagraph.Quran -> { type = ParagraphDraftType.QURAN; text = para.text }
                        is DhikrParagraph.Note  -> { type = ParagraphDraftType.NOTE;  text = para.text }
                    }
                }
            })
        }
    }
}

// ── Entry point ───────────────────────────────────────────────────────────────

/**
 * Unified Adhkar create / edit page.
 *
 * [categoryId] == null → create mode (blank form).
 * [categoryId] != null → edit mode (form pre-filled from the category + its dhikr).
 *
 * The composable waits for dhikr to load before rendering the form in edit mode,
 * so all [remember] / [rememberSaveable] values are initialised with the correct
 * data on first composition — no LaunchedEffect patching is required.
 */
@Composable
fun AdhkarEditorPage(categoryId: String?) {
    val activity = LocalActivity.current as ComponentActivity
    val vm: AdhkarViewModel = viewModel(activity)
    val state by vm.uiState.collectAsState()

    val isEditMode = categoryId != null

    // Category data is already in VM state — no DB round-trip needed.
    val category = remember(categoryId, state.categories) {
        categoryId?.let { id -> state.categories.firstOrNull { it.id == id } }
    }

    // Dhikr list requires an async query; null = not yet loaded.
    var loadedDhikrs by remember { mutableStateOf<List<Dhikr>?>(if (!isEditMode) emptyList() else null) }
    LaunchedEffect(categoryId) {
        if (categoryId != null) loadedDhikrs = vm.getDhikrForCategory(categoryId)
    }

    if (isEditMode && (category == null || loadedDhikrs == null)) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    // Built-in defaults resolved synchronously from in-memory data.
    val builtInDefaults = remember(categoryId) {
        categoryId?.let { vm.getBuiltInDefaults(it) }
    }

    // key() ensures form state resets if categoryId ever changes within the same
    // back-stack entry (edge-case safety).
    key(categoryId) {
        AdhkarEditorContent(
            categoryId      = categoryId,
            isEditMode      = isEditMode,
            initialTitle    = category?.title   ?: "",
            initialColor    = category?.color   ?: Color(0xFF1565C0),
            initialSpan     = category?.span    ?: 1,
            initialIconSrc  = category?.iconSource ?: IconSource.None,
            initialDhikrs   = loadedDhikrs      ?: emptyList(),
            builtInDefaults = builtInDefaults,
            vm              = vm,
        )
    }
}

// ── Editor form ───────────────────────────────────────────────────────────────

/**
 * The actual editor form.
 *
 * By the time this composable is created, all initial data is available, so
 * every [remember] / [rememberSaveable] initialises with the correct values and
 * survives configuration changes via the saved-state mechanism.
 *
 * Top-bar action logic:
 *  Create mode  → "Create" button only.
 *  Edit mode    → "Save" button always; "Reset to Default" button when
 *                 [builtInDefaults] != null AND the form diverges from defaults.
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
    val nav = LocalNavController.current

    // ── Form state ────────────────────────────────────────────────────────────
    var title     by rememberSaveable { mutableStateOf(initialTitle) }
    var cardColor by remember        { mutableStateOf(initialColor)  }
    var span      by rememberSaveable { mutableIntStateOf(initialSpan) }

    // Path to a user-picked custom image; null = use originalIconSrc.
    var iconLocalPath by rememberSaveable {
        // Initialise from the category's Uri icon in edit mode; null otherwise.
        mutableStateOf((initialIconSrc as? IconSource.Uri)?.path)
    }

    // Fixed reference to the source we loaded with — used to display the Res
    // preview and to compute the effective icon for saving.
    // Uri sources are covered by iconLocalPath; treat them as None here.
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
    val isSaveable = title.isNotBlank() && dhikrList.isNotEmpty() && dhikrList.all { it.isValid }

    /**
     * True when the current form state differs from the original built-in defaults.
     * Uses [derivedStateOf] so it recomputes lazily only when the snapshot state
     * objects it reads actually change — including nested paragraph text/type.
     */
    val hasChanges by remember(builtInDefaults) {
        derivedStateOf {
            if (builtInDefaults == null) false
            else {
                title != builtInDefaults.title          ||
                        cardColor != builtInDefaults.color      ||
                        span != builtInDefaults.span            ||
                        iconLocalPath != null                   ||  // any custom image = icon changed
                        dhikrList.map { it.toDhikr() } != builtInDefaults.dhikrList
            }
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
        val dhikrItems = dhikrList.map { it.toDhikr() }
        if (isEditMode && categoryId != null) vm.updateCategory(category, dhikrItems)
        else vm.addCategory(category, dhikrItems)
        nav.popBackStack()
    }

    fun resetToDefaults() {
        if (categoryId != null) {
            vm.resetCategoryToDefaults(categoryId)
            nav.popBackStack()
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
                    IconButton(onClick = { nav.popBackStack() }) {
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
        Column(
            modifier            = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {

            // ── Live preview ──────────────────────────────────────────────────
            SectionLabel(stringResource(R.string.adhkar_preview))
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

            // ── Card appearance ───────────────────────────────────────────────
            SectionLabel(stringResource(R.string.adhkar_card_section))

            OutlinedTextField(
                value           = title,
                onValueChange   = { title = it },
                label           = { Text(stringResource(R.string.adhkar_title_label)) },
                placeholder     = { Text(stringResource(R.string.adhkar_title_placeholder)) },
                singleLine      = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier        = Modifier.fillMaxWidth(),
            )

            // Color picker row
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

            // Icon picker row
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

            // Span selector
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

            // ── Adhkar content ────────────────────────────────────────────────
            HorizontalDivider()
            SectionLabel(stringResource(R.string.adhkar_content_section))

            dhikrList.forEachIndexed { idx, draft ->
                DhikrDraftCard(
                    index     = idx + 1,
                    draft     = draft,
                    canDelete = dhikrList.size > 1,
                    onDelete  = { dhikrList.removeAt(idx) },
                )
            }

            OutlinedButton(
                onClick  = { dhikrList.add(DhikrDraftState()) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Outlined.Add, null)
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.adhkar_add_dhikr))
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

// ── Dhikr draft card ──────────────────────────────────────────────────────────

@Composable
private fun DhikrDraftCard(
    index: Int,
    draft: DhikrDraftState,
    canDelete: Boolean,
    onDelete: () -> Unit,
) {
    Card(
        colors   = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier            = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text     = stringResource(R.string.adhkar_dhikr_n, index),
                    style    = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                AnimatedVisibility(visible = canDelete) {
                    IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Outlined.Close, null, Modifier.size(18.dp))
                    }
                }
            }

            draft.paragraphs.forEachIndexed { pIdx, para ->
                ParagraphDraftRow(
                    draft     = para,
                    canDelete = draft.paragraphs.size > 1,
                    onDelete  = { draft.paragraphs.removeAt(pIdx) },
                )
            }

            TextButton(
                onClick  = { draft.paragraphs.add(ParagraphDraftState()) },
                modifier = Modifier.align(Alignment.Start),
            ) {
                Icon(Icons.Outlined.Add, null, Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.adhkar_add_paragraph), style = MaterialTheme.typography.labelMedium)
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text     = stringResource(R.string.adhkar_repetitions, draft.repetitions),
                    style    = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                FilledTonalIconButton(
                    onClick  = { if (draft.repetitions > 1) draft.repetitions-- },
                    modifier = Modifier.size(32.dp),
                ) { Text("−", style = MaterialTheme.typography.titleMedium) }
                FilledTonalIconButton(
                    onClick  = { draft.repetitions++ },
                    modifier = Modifier.size(32.dp),
                ) { Text("+", style = MaterialTheme.typography.titleMedium) }
            }
        }
    }
}

// ── Paragraph row ─────────────────────────────────────────────────────────────

@Composable
private fun ParagraphDraftRow(
    draft: ParagraphDraftState,
    canDelete: Boolean,
    onDelete: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ParagraphDraftType.entries.forEach { type ->
                FilterChip(
                    selected = draft.type == type,
                    onClick  = { draft.type = type },
                    label    = { Text(stringResource(type.labelRes), style = MaterialTheme.typography.labelSmall) },
                )
            }
            if (canDelete) {
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Outlined.Close, null, Modifier.size(16.dp))
                }
            }
        }
        OutlinedTextField(
            value         = draft.text,
            onValueChange = { draft.text = it },
            modifier      = Modifier.fillMaxWidth(),
            minLines      = 3,
            maxLines      = 8,
            textStyle     = MaterialTheme.typography.bodyLarge,
            placeholder   = {
                Text(
                    text  = when (draft.type) {
                        ParagraphDraftType.QURAN -> stringResource(R.string.adhkar_paragraph_quran_hint)
                        ParagraphDraftType.NOTE  -> stringResource(R.string.adhkar_paragraph_note_hint)
                        else                     -> stringResource(R.string.adhkar_paragraph_body_hint)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
        )
    }
}

// ── Section label ─────────────────────────────────────────────────────────────

@Composable
private fun SectionLabel(text: String) {
    Text(
        text  = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
    )
}