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
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.lhacenmed.khatmah.R
import com.lhacenmed.khatmah.data.adhkar.AdhkarCategory
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
}

// ── Page ──────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddAdhkarPage() {
    val nav      = LocalNavController.current
    val context  = LocalContext.current
    val activity = LocalActivity.current as ComponentActivity
    val vm: AdhkarViewModel = viewModel(activity)

    // Form state
    var title by rememberSaveable { mutableStateOf("") }
    var cardColor by remember { mutableStateOf(Color(0xFF1565C0)) }
    var span by rememberSaveable { mutableIntStateOf(1) }
    var iconLocalPath by rememberSaveable { mutableStateOf<String?>(null) }
    val dhikrList = remember { mutableStateListOf(DhikrDraftState()) }

    // Dialog state
    var showColorPicker by remember { mutableStateOf(false) }

    val isValid = title.isNotBlank() && dhikrList.isNotEmpty() && dhikrList.all { it.isValid }

    // Image picker
    val imageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { vm.persistImage(it) { path -> if (path != null) iconLocalPath = path } }
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    if (showColorPicker) {
        ColorPickerDialog(
            initialColor    = cardColor,
            onColorSelected = { cardColor = it; showColorPicker = false },
            onDismiss       = { showColorPicker = false },
        )
    }

    Scaffold(
        topBar = {
            LargeTopAppBar(
                title          = { Text(stringResource(R.string.add_adhkar)) },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.navigate_up))
                    }
                },
                actions = {
                    TextButton(
                        onClick  = {
                            val category = AdhkarCategory(
                                id         = UUID.randomUUID().toString(),
                                title      = title.trim(),
                                iconSource = iconLocalPath?.let { IconSource.Uri(it) } ?: IconSource.None,
                                color      = cardColor,
                                span       = span,
                            )
                            vm.addCategory(category, dhikrList.map { it.toDhikr() })
                            nav.popBackStack()
                        },
                        enabled  = isValid,
                        colors   = ButtonDefaults.textButtonColors(
                            contentColor         = MaterialTheme.colorScheme.primary,
                            disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                        ),
                    ) { Text(stringResource(R.string.create)) }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            // ── Live preview ──────────────────────────────────────────────────
            SectionLabel(stringResource(R.string.adhkar_preview))
            val previewCategory = AdhkarCategory(
                id         = "preview",
                title      = title.ifBlank { stringResource(R.string.adhkar_title_placeholder) },
                iconSource = iconLocalPath?.let { IconSource.Uri(it) } ?: IconSource.None,
                color      = cardColor,
                span       = 2,
            )
            AdhkarCard(
                category = previewCategory,
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
                if (iconLocalPath != null) {
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
                } else {
                    FilledTonalIconButton(onClick = { imageLauncher.launch("image/*") }) {
                        Icon(Icons.Outlined.Image, stringResource(R.string.adhkar_pick_image))
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
            // Header
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

            // Paragraphs
            draft.paragraphs.forEachIndexed { pIdx, para ->
                ParagraphDraftRow(
                    draft     = para,
                    canDelete = draft.paragraphs.size > 1,
                    onDelete  = { draft.paragraphs.removeAt(pIdx) },
                )
            }

            // Add paragraph button
            TextButton(
                onClick  = { draft.paragraphs.add(ParagraphDraftState()) },
                modifier = Modifier.align(Alignment.Start),
            ) {
                Icon(Icons.Outlined.Add, null, Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.adhkar_add_paragraph), style = MaterialTheme.typography.labelMedium)
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // Repetitions counter
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
                ) {
                    Text("−", style = MaterialTheme.typography.titleMedium)
                }
                FilledTonalIconButton(
                    onClick  = { draft.repetitions++ },
                    modifier = Modifier.size(32.dp),
                ) {
                    Text("+", style = MaterialTheme.typography.titleMedium)
                }
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
        // Type selector chips
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