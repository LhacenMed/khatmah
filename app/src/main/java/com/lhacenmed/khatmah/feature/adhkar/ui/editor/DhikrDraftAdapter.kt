package com.lhacenmed.khatmah.feature.adhkar.ui.editor

import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.ChipGroup
import com.google.android.material.textfield.TextInputEditText
import com.lhacenmed.khatmah.R

/**
 * The editor's list: the appearance form, one card per dhikr draft, then the add button.
 *
 * The form is passed in already built ([headerView]) rather than inflated here, so the fragment
 * keeps direct hold of its fields and no rebind can ever reach them — a header is a singleton,
 * so it is created once and never recycled.
 *
 * Within a card, paragraph rows are added and removed one view at a time instead of rebinding
 * the whole card. Rebinding would rebuild every row, which costs the user their cursor and the
 * keyboard mid-sentence.
 *
 * [onChanged] fires whenever edited content could change whether the form is saveable or has
 * diverged from its defaults, so the fragment can re-evaluate its toolbar actions.
 */
class DhikrDraftAdapter(
    private val drafts: MutableList<DhikrDraft>,
    private val headerView: View,
    private val onChanged: () -> Unit,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    override fun getItemCount() = drafts.size + 2 // header + drafts + add button

    override fun getItemViewType(position: Int) = when (position) {
        0                -> TYPE_HEADER
        drafts.size + 1  -> TYPE_FOOTER
        else             -> TYPE_DHIKR
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_HEADER -> HeaderHolder(headerView)
            TYPE_FOOTER -> FooterHolder(
                inflater.inflate(R.layout.adhkar_add_dhikr_button, parent, false),
                onAdd = ::addDraft,
            )
            else -> DhikrHolder(
                inflater.inflate(R.layout.adhkar_dhikr_draft_card, parent, false),
                adapter = this,
            )
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is DhikrHolder) holder.bind(drafts[position - 1], position - 1)
    }

    // ── Mutations ─────────────────────────────────────────────────────────────

    private fun addDraft() {
        drafts.add(DhikrDraft())
        notifyItemInserted(drafts.size) // list index + 1 for the header
        // The first card gains a delete button as soon as a second one exists.
        if (drafts.size == 2) notifyItemChanged(1)
        onChanged()
    }

    private fun removeDraft(index: Int) {
        drafts.removeAt(index)
        notifyItemRemoved(index + 1)
        // Every later card is renumbered, and a sole survivor loses its delete button.
        notifyItemRangeChanged(1, drafts.size)
        onChanged()
    }

    // ── Holders ───────────────────────────────────────────────────────────────

    private class HeaderHolder(view: View) : RecyclerView.ViewHolder(view)

    private class FooterHolder(view: View, onAdd: () -> Unit) : RecyclerView.ViewHolder(view) {
        init {
            view.findViewById<MaterialButton>(R.id.add_dhikr).setOnClickListener { onAdd() }
        }
    }

    private class DhikrHolder(
        view: View,
        private val adapter: DhikrDraftAdapter,
    ) : RecyclerView.ViewHolder(view) {

        private val index: TextView             = view.findViewById(R.id.dhikr_index)
        private val delete: MaterialButton      = view.findViewById(R.id.dhikr_delete)
        private val paragraphs: LinearLayout    = view.findViewById(R.id.paragraphs)
        private val addParagraph: MaterialButton = view.findViewById(R.id.add_paragraph)
        private val repetitions: TextView       = view.findViewById(R.id.repetitions)
        private val repMinus: MaterialButton    = view.findViewById(R.id.rep_minus)
        private val repPlus: MaterialButton     = view.findViewById(R.id.rep_plus)

        private lateinit var draft: DhikrDraft

        fun bind(draft: DhikrDraft, position: Int) {
            this.draft = draft

            index.text = itemView.context.getString(R.string.adhkar_dhikr_n, position + 1)
            delete.isVisible = adapter.drafts.size > 1
            delete.setOnClickListener {
                // Read the position at click time — deleting a card shifts every one after it.
                val current = adapterPosition
                if (current != RecyclerView.NO_POSITION) adapter.removeDraft(current - 1)
            }

            paragraphs.removeAllViews()
            draft.paragraphs.forEach(::addParagraphRow)
            refreshParagraphDeletes()

            addParagraph.setOnClickListener {
                val para = ParagraphDraft()
                draft.paragraphs.add(para)
                addParagraphRow(para)
                refreshParagraphDeletes()
            }

            renderRepetitions()
            repMinus.setOnClickListener {
                if (draft.repetitions > 1) {
                    draft.repetitions--
                    renderRepetitions()
                }
            }
            repPlus.setOnClickListener {
                draft.repetitions++
                renderRepetitions()
            }
        }

        private fun renderRepetitions() {
            repetitions.text =
                itemView.context.getString(R.string.adhkar_repetitions, draft.repetitions)
        }

        /** Builds one paragraph row and binds it to [para] for as long as the row lives. */
        private fun addParagraphRow(para: ParagraphDraft) {
            val row = LayoutInflater.from(itemView.context)
                .inflate(R.layout.adhkar_paragraph_draft_row, paragraphs, false)

            val types: ChipGroup            = row.findViewById(R.id.type_group)
            val text: TextInputEditText     = row.findViewById(R.id.paragraph_text)
            val remove: MaterialButton      = row.findViewById(R.id.paragraph_delete)

            types.check(chipIdFor(para.type))
            types.setOnCheckedStateChangeListener { _, checked ->
                val type = typeFor(checked.firstOrNull() ?: return@setOnCheckedStateChangeListener)
                para.type = type
                text.setHint(type.hintRes)
            }
            text.setHint(para.type.hintRes)

            text.setText(para.text)
            text.addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable?) {
                    para.text = s?.toString().orEmpty()
                    adapter.onChanged()
                }

                override fun beforeTextChanged(c: CharSequence?, s: Int, co: Int, a: Int) = Unit
                override fun onTextChanged(c: CharSequence?, s: Int, b: Int, co: Int) = Unit
            })

            remove.setOnClickListener {
                draft.paragraphs.remove(para)
                paragraphs.removeView(row)
                refreshParagraphDeletes()
                adapter.onChanged()
            }

            paragraphs.addView(row)
        }

        /** The last remaining paragraph cannot be removed — a dhikr must keep one. */
        private fun refreshParagraphDeletes() {
            val canDelete = draft.paragraphs.size > 1
            for (i in 0 until paragraphs.childCount) {
                paragraphs.getChildAt(i)
                    .findViewById<MaterialButton>(R.id.paragraph_delete).isVisible = canDelete
            }
        }

        private fun chipIdFor(type: ParagraphType) = when (type) {
            ParagraphType.BODY  -> R.id.type_body
            ParagraphType.QURAN -> R.id.type_quran
            ParagraphType.NOTE  -> R.id.type_note
        }

        private fun typeFor(chipId: Int) = when (chipId) {
            R.id.type_quran -> ParagraphType.QURAN
            R.id.type_note  -> ParagraphType.NOTE
            else            -> ParagraphType.BODY
        }
    }

    private companion object {
        const val TYPE_HEADER = 0
        const val TYPE_DHIKR  = 1
        const val TYPE_FOOTER = 2
    }
}
