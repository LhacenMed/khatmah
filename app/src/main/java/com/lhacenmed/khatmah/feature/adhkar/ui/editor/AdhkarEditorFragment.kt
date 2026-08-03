package com.lhacenmed.khatmah.feature.adhkar.ui.editor

import android.graphics.Color as AndroidColor
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.SpannableString
import android.text.Spanned
import android.text.TextWatcher
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.ColorInt
import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.view.MenuProvider
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.card.MaterialCardView
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.google.android.material.textfield.TextInputEditText
import com.lhacenmed.khatmah.R
import com.lhacenmed.khatmah.core.nav.IntentNavigator
import com.lhacenmed.khatmah.core.ui.color.showColorPicker
import com.lhacenmed.khatmah.core.ui.theme.isAppInDarkTheme
import com.lhacenmed.khatmah.core.ui.theme.resolveColorScheme
import com.lhacenmed.khatmah.feature.adhkar.data.AdhkarCategory
import com.lhacenmed.khatmah.feature.adhkar.data.BuiltInDefaults
import com.lhacenmed.khatmah.feature.adhkar.data.IconSource
import com.lhacenmed.khatmah.feature.adhkar.ui.AdhkarViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Create / edit page for one Adhkar category.
 *
 * No argument → create mode: a blank form, ready immediately, no database read.
 * An id       → edit mode: the form is filled from the category and its adhkar.
 *
 * The reader caches a category's adhkar before its edit action becomes reachable, so the
 * normal reader → editor path opens with no wait at all; the database read below only runs
 * on the cold-start path where that cache is empty.
 *
 * Everything the user types lives in this fragment's own fields, and reaches the ViewModel
 * only when Save is pressed — so backing out of a half-written form changes nothing.
 */
class AdhkarEditorFragment : Fragment(R.layout.adhkar_editor_fragment), MenuProvider {

    private val vm: AdhkarViewModel by activityViewModels()

    /** Blank is treated as absent: create mode must never be mistaken for editing "". */
    private val categoryId: String? by lazy {
        arguments?.getString(ARG_CATEGORY_ID)?.takeIf { it.isNotBlank() }
    }
    private val isEditMode get() = categoryId != null

    private val nav by lazy { IntentNavigator(requireActivity()) }
    private val scheme: ColorScheme by lazy {
        resolveColorScheme(requireContext(), isAppInDarkTheme(requireContext()))
    }

    private lateinit var form: RecyclerView
    private lateinit var loading: CircularProgressIndicator

    private lateinit var previewCard: MaterialCardView
    private lateinit var previewIcon: ImageView
    private lateinit var previewScrim: View
    private lateinit var previewTitle: TextView
    private lateinit var titleField: TextInputEditText
    private lateinit var colorSwatch: View
    private lateinit var iconPreview: ImageView
    private lateinit var iconPick: MaterialButton
    private lateinit var iconRemove: MaterialButton
    private lateinit var spanGroup: MaterialButtonToggleGroup

    // ── Form state ────────────────────────────────────────────────────────────
    private var title = ""
    private var cardColor = DEFAULT_COLOR
    private var span = 1

    /** Path to a user-picked image; null means [originalIcon] is in effect. */
    private var iconPath: String? = null

    /** The icon the category was loaded with, kept so a picked image can be undone. */
    private var originalIcon: IconSource = IconSource.None

    private val drafts = mutableListOf<DhikrDraft>()
    private var builtInDefaults: BuiltInDefaults? = null

    /** Last states pushed to the toolbar, so the menu is only rebuilt when they change. */
    private var isSaveable = false
    private var isResettable = false

    private var restored: Bundle? = null

    private val imagePicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri ?: return@registerForActivityResult
        // The copy runs in the Activity-scoped ViewModel, which outlives this view — so the
        // result is recorded first, and only drawn if there is still something to draw on.
        vm.persistImage(uri) { path ->
            path ?: return@persistImage
            iconPath = path
            if (view == null) return@persistImage
            renderIcon()
            renderPreview()
            refreshActions()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        restored = savedInstanceState

        form = view.findViewById(R.id.form)
        loading = view.findViewById(R.id.loading)
        loading.setIndicatorColor(scheme.primary.toArgb())

        viewLifecycleOwner.lifecycleScope.launch { load() }
        requireActivity().addMenuProvider(this, viewLifecycleOwner)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(KEY_TITLE, title)
        outState.putInt(KEY_COLOR, cardColor)
        outState.putInt(KEY_SPAN, span)
        outState.putString(KEY_ICON_PATH, iconPath)
    }

    // ── Loading ───────────────────────────────────────────────────────────────

    private suspend fun load() {
        categoryId?.let { id ->
            // The categories arrive with the ViewModel's first refresh; wait for this one
            // rather than rendering a form that would have to correct itself a frame later.
            val category = vm.uiState
                .mapNotNull { state -> state.categories.firstOrNull { it.id == id } }
                .first()

            title = category.title
            cardColor = category.color.toArgb()
            span = category.span
            iconPath = (category.iconSource as? IconSource.Uri)?.path
            // A picked image is tracked by iconPath alone, so only a built-in icon is retained.
            originalIcon = category.iconSource.takeUnless { it is IconSource.Uri } ?: IconSource.None
            builtInDefaults = vm.getBuiltInDefaults(id)

            val dhikrs = vm.getCachedDhikr(id) ?: vm.getDhikrForCategory(id)
            dhikrs.mapTo(drafts) { DhikrDraft.from(it) }
        }

        // Edits made before a rotation win over the stored values they were derived from.
        restored?.let {
            title = it.getString(KEY_TITLE).orEmpty()
            cardColor = it.getInt(KEY_COLOR)
            span = it.getInt(KEY_SPAN)
            iconPath = it.getString(KEY_ICON_PATH)
        }

        // A category always offers at least one dhikr to write into.
        if (drafts.isEmpty()) drafts.add(DhikrDraft())

        showForm()
    }

    private fun showForm() {
        val header = LayoutInflater.from(requireContext())
            .inflate(R.layout.adhkar_editor_form, form, false)
        bindHeader(header)

        form.adapter = DhikrDraftAdapter(drafts, header, ::refreshActions)

        loading.hide()
        form.isInvisible = false
        refreshActions()
    }

    // ── Header ────────────────────────────────────────────────────────────────

    private fun bindHeader(header: View) {
        previewCard = header.findViewById(R.id.preview_card)
        previewIcon = header.findViewById(R.id.preview_icon)
        previewScrim = header.findViewById(R.id.preview_scrim)
        previewTitle = header.findViewById(R.id.preview_title)
        titleField = header.findViewById(R.id.title_field)
        colorSwatch = header.findViewById(R.id.color_swatch)
        iconPreview = header.findViewById(R.id.icon_preview)
        iconPick = header.findViewById(R.id.icon_pick)
        iconRemove = header.findViewById(R.id.icon_remove)
        spanGroup = header.findViewById(R.id.span_group)

        val accent = scheme.primary.toArgb()
        header.findViewById<TextView>(R.id.label_preview).setTextColor(accent)
        header.findViewById<TextView>(R.id.label_appearance).setTextColor(accent)
        header.findViewById<TextView>(R.id.label_content).setTextColor(accent)

        // Darkens the card's lower half so a white title survives any background colour.
        previewScrim.background = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(AndroidColor.TRANSPARENT, SCRIM_BOTTOM),
        )

        titleField.setText(title)
        titleField.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                title = s?.toString().orEmpty()
                renderPreview()
                refreshActions()
            }

            override fun beforeTextChanged(c: CharSequence?, s: Int, co: Int, a: Int) = Unit
            override fun onTextChanged(c: CharSequence?, s: Int, b: Int, co: Int) = Unit
        })

        colorSwatch.setOnClickListener {
            showColorPicker(requireContext(), cardColor) { picked ->
                cardColor = picked
                renderColor()
                renderIcon() // a built-in icon is tinted from the card colour
                renderPreview()
                refreshActions()
            }
        }

        iconPick.setOnClickListener { imagePicker.launch("image/*") }
        iconRemove.setOnClickListener {
            iconPath = null
            renderIcon()
            renderPreview()
            refreshActions()
        }

        spanGroup.check(if (span == 1) R.id.span_half else R.id.span_full)
        spanGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            span = if (checkedId == R.id.span_half) 1 else 2
            refreshActions()
        }

        renderColor()
        renderIcon()
        renderPreview()
    }

    /** Live preview of the card as it will appear on the Adhkar tab. */
    private fun renderPreview() {
        previewCard.setCardBackgroundColor(cardColor)
        previewTitle.text = title.ifBlank { getString(R.string.adhkar_title_placeholder) }

        val icon = originalIcon
        when {
            iconPath != null -> {
                previewIcon.clearColorFilter()
                previewIcon.load(iconPath)
            }
            icon is IconSource.Res -> {
                previewIcon.setImageResource(icon.resId)
                previewIcon.setColorFilter(AndroidColor.WHITE)
            }
            else -> previewIcon.setImageDrawable(null)
        }
    }

    private fun renderColor() {
        colorSwatch.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(cardColor)
            setStroke(strokeWidthPx(), scheme.outline.toArgb())
        }
    }

    /**
     * The icon row offers exactly one path forward for each state: replace a picked image,
     * replace a built-in icon, or pick a first one.
     */
    private fun renderIcon() {
        val icon = originalIcon
        when {
            iconPath != null -> {
                iconPreview.isVisible = true
                iconPreview.clearColorFilter()
                iconPreview.load(iconPath)
                iconPick.isVisible = false
                iconRemove.isVisible = true
            }
            icon is IconSource.Res -> {
                iconPreview.isVisible = true
                iconPreview.setImageResource(icon.resId)
                iconPreview.setColorFilter(Color(cardColor).copy(alpha = 0.7f).toArgb())
                iconPick.isVisible = true
                iconRemove.isVisible = false
            }
            else -> {
                iconPreview.isVisible = false
                iconPick.isVisible = true
                iconRemove.isVisible = false
            }
        }
    }

    // ── Toolbar actions ───────────────────────────────────────────────────────

    /**
     * Recomputes what the toolbar should offer, and rebuilds the menu only when that answer
     * changes — this runs on every keystroke, so it must cost nothing when nothing moved.
     */
    private fun refreshActions() {
        val saveable = title.isNotBlank() && drafts.isNotEmpty() && drafts.all { it.isValid }
        val resettable = builtInDefaults?.let { defaults ->
            title != defaults.title ||
                cardColor != defaults.color.toArgb() ||
                span != defaults.span ||
                iconPath != null ||
                drafts.map { it.toDhikr() } != defaults.dhikrList
        } ?: false

        if (saveable == isSaveable && resettable == isResettable) return
        isSaveable = saveable
        isResettable = resettable
        requireActivity().invalidateMenu()
    }

    override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
        menuInflater.inflate(R.menu.adhkar_editor_menu, menu)
    }

    override fun onPrepareMenu(menu: Menu) {
        menu.findItem(R.id.action_reset).apply {
            isVisible = isResettable
            tint(scheme.error.toArgb())
        }
        menu.findItem(R.id.action_save).apply {
            setTitle(if (isEditMode) R.string.save else R.string.create)
            isEnabled = isSaveable
            tint(
                if (isSaveable) scheme.primary.toArgb()
                else scheme.onSurface.copy(alpha = DISABLED_ALPHA).toArgb()
            )
        }
    }

    override fun onMenuItemSelected(menuItem: MenuItem): Boolean = when (menuItem.itemId) {
        R.id.action_save -> {
            save()
            true
        }
        R.id.action_reset -> {
            resetToDefaults()
            true
        }
        else -> false
    }

    /** A text action ignores per-item tinting, so its colour is carried by the title itself. */
    private fun MenuItem.tint(@ColorInt color: Int) {
        title = SpannableString(title.toString()).apply {
            setSpan(ForegroundColorSpan(color), 0, length, Spanned.SPAN_INCLUSIVE_EXCLUSIVE)
        }
    }

    // ── Commit ────────────────────────────────────────────────────────────────

    /** A picked image outranks whatever the category was loaded with. */
    private fun effectiveIcon(): IconSource =
        iconPath?.let(IconSource::Uri) ?: originalIcon

    private fun save() {
        val category = AdhkarCategory(
            id         = categoryId ?: UUID.randomUUID().toString(),
            title      = title.trim(),
            iconSource = effectiveIcon(),
            color      = Color(cardColor),
            span       = span,
        )
        val items = drafts.map { it.toDhikr() }
        if (isEditMode) vm.updateCategory(category, items) else vm.addCategory(category, items)
        nav.back()
    }

    private fun resetToDefaults() {
        val id = categoryId ?: return
        vm.resetCategoryToDefaults(id)
        nav.back()
    }

    private fun strokeWidthPx() = (2 * resources.displayMetrics.density).toInt()

    companion object {
        private const val ARG_CATEGORY_ID = "category_id"

        private const val KEY_TITLE     = "title"
        private const val KEY_COLOR     = "color"
        private const val KEY_SPAN      = "span"
        private const val KEY_ICON_PATH = "icon_path"

        /** The tab's default card colour, used for a category that has not chosen one. */
        private const val DEFAULT_COLOR = 0xFF1565C0.toInt()

        /** Bottom of the preview's darkening wash — black at 30%, as on the real card. */
        private const val SCRIM_BOTTOM = 0x4D000000

        private const val DISABLED_ALPHA = 0.38f

        fun newInstance(categoryId: String?) = AdhkarEditorFragment().apply {
            arguments = Bundle(1).apply { putString(ARG_CATEGORY_ID, categoryId) }
        }
    }
}
