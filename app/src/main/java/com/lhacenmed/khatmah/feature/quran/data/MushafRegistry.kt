package com.lhacenmed.khatmah.feature.quran.data

import com.lhacenmed.khatmah.R

/**
 * The print catalogue, generated as [Riwaya] × [MushafFormat]. Every riwaya offers a zero-download
 * TEXT print and a downloadable QCF4 print; adding a riwaya extends the catalogue automatically.
 */
object MushafRegistry {

    val all: List<MushafPrint> = Riwaya.entries.flatMap { riwaya ->
        MushafFormat.entries.map { format ->
            MushafPrint(riwaya, format, nameRes(format), descRes(riwaya, format))
        }
    }

    /** Warsh text — the lightweight, no-download default. */
    val default: MushafPrint = byId("warsh_text")

    fun byId(id: String): MushafPrint = all.find { it.id == id } ?: default

    fun byRiwaya(riwaya: Riwaya): List<MushafPrint> = all.filter { it.riwaya == riwaya }

    // ── String resources ─────────────────────────────────────────────────────────

    /** The name conveys the format only — the riwaya is already the section header above the card. */
    private fun nameRes(format: MushafFormat): Int = when (format) {
        MushafFormat.TEXT -> R.string.print_text_name
        MushafFormat.QCF4 -> R.string.print_mushaf_name
    }

    private fun descRes(riwaya: Riwaya, format: MushafFormat): Int = when (riwaya to format) {
        Riwaya.WARSH to MushafFormat.TEXT -> R.string.print_warsh_text_desc
        Riwaya.WARSH to MushafFormat.QCF4 -> R.string.print_warsh_qcf4_desc
        Riwaya.HAFS  to MushafFormat.TEXT -> R.string.print_hafs_text_desc
        else                              -> R.string.print_hafs_qcf4_desc
    }
}
