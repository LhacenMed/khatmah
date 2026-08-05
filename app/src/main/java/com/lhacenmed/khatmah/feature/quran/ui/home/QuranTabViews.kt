package com.lhacenmed.khatmah.feature.quran.ui.home

import android.view.View
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.isVisible
import com.lhacenmed.khatmah.R
import com.lhacenmed.khatmah.databinding.QuranSurahTileBinding
import com.lhacenmed.khatmah.databinding.QuranTabBinding
import com.lhacenmed.khatmah.feature.quran.data.Riwaya
import com.lhacenmed.khatmah.feature.quran.data.SurahInfo
import com.lhacenmed.khatmah.feature.quran.ui.home.QuranHomeViewModel.KhatmahState
import com.lhacenmed.khatmah.feature.quran.ui.home.QuranHomeViewModel.Resume

/** Max words of the resume verse shown as a taste of the position. */
private const val AYA_WORDS = 5

/**
 * Renders the Quran tab's views. It owns the inflated layout and nothing else: state arrives
 * through the `render` calls and is written straight into the views, so there is one place to look
 * for what any part of the tab is showing and no second copy of the state to fall out of step.
 *
 * The click callbacks are properties rather than constructor arguments because what a tap means
 * depends on state the tab is still collecting — they are re-pointed on every render.
 */
class QuranTabViews(private val binding: QuranTabBinding) {

    val root: View get() = binding.root

    var onContinueReading: () -> Unit = {}
    var onSurahClick: (suraNum: Int) -> Unit = {}
    var onFullIndex: () -> Unit = {}
    var onKhatmahClick: () -> Unit = {}

    private val context = binding.root.context
    private val tiles = listOf(binding.tileFirst, binding.tileSecond, binding.tileThird)

    init {
        binding.continueReading.setOnClickListener { onContinueReading() }
        binding.fullIndex.setOnClickListener { onFullIndex() }
        binding.khatmahStrip.setOnClickListener { onKhatmahClick() }
    }

    /** Where reading stopped. Null while the anchor is still being resolved — the card stays blank. */
    fun renderResume(resume: Resume?) {
        val card = binding
        if (resume == null) return
        card.resumeSura.text = context.getString(R.string.quran_sura_title, resume.suraName)
        card.resumeMeta.text = context.getString(R.string.quran_resume_meta, resume.page, resume.juz)
        card.resumeAya.apply {
            isVisible = resume.ayaText.isNotBlank()
            typeface = ResourcesCompat.getFont(context, resume.riwaya.fontRes)
            text = truncateAya(resume.ayaText)
        }
    }

    /** The recent surahs, most recent first. Fewer than three leaves the spare tiles hidden. */
    fun renderQuickIndex(surahs: List<SurahInfo>, pageFor: (suraNum: Int) -> Int) {
        binding.quickAccess.isVisible = surahs.isNotEmpty()
        binding.surahTiles.isVisible = surahs.isNotEmpty()
        tiles.forEachIndexed { index, tile ->
            val surah = surahs.getOrNull(index)
            tile.root.isVisible = surah != null
            if (surah != null) bindTile(tile, surah, pageFor(surah.num))
        }
    }

    private fun bindTile(tile: QuranSurahTileBinding, surah: SurahInfo, page: Int) {
        tile.surahName.text = surah.name.removePrefix("سورة ").trim()
        tile.surahPage.text = context.getString(R.string.today_page, page)
        tile.root.setOnClickListener { onSurahClick(surah.num) }
    }

    /**
     * The khatmah in one line. Each state answers the same two questions — what this khatmah is,
     * and how far it has come — so the strip never changes shape, only its words.
     */
    fun renderKhatmah(state: KhatmahState) {
        binding.khatmahStrip.isVisible = state !is KhatmahState.Loading
        val (titleRes, subtitle, progress) = when (state) {
            is KhatmahState.Active -> Triple(
                R.string.today_khatmah_title,
                context.getString(
                    R.string.khatmah_strip_wird,
                    state.juz,
                    (state.khatmah.totalDays - state.readCount).coerceAtLeast(0),
                ),
                if (state.khatmah.totalDays > 0) {
                    state.readCount * 100 / state.khatmah.totalDays
                } else 0,
            )
            is KhatmahState.Done -> Triple(
                R.string.today_khatmah_completed,
                context.getString(R.string.today_new_khatmah),
                100,
            )
            // None — Loading never reaches here (the strip is hidden until the khatmah resolves).
            else -> Triple(
                R.string.today_no_khatmah,
                context.getString(R.string.today_create),
                0,
            )
        }
        binding.khatmahTitle.setText(titleRes)
        binding.khatmahSubtitle.text = subtitle
        binding.khatmahProgress.setProgressCompat(progress, true)
    }
}

/** The mushaf font each riwaya is set in — the reader's own faces, used here for the resume verse. */
private val Riwaya.fontRes: Int
    get() = when (this) {
        Riwaya.WARSH -> R.font.kfgqpc_warsh_uthmanic
        Riwaya.HAFS  -> R.font.kfgqpc_hafs_uthmanic
    }

/** Returns at most [AYA_WORDS] space-separated words from [text], appending "…" if truncated. */
private fun truncateAya(text: String): String {
    val words = text.trim().split(' ')
    return if (words.size <= AYA_WORDS) text else words.take(AYA_WORDS).joinToString(" ") + "…"
}
