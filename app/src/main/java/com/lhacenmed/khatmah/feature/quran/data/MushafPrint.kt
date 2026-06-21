package com.lhacenmed.khatmah.feature.quran.data

import androidx.annotation.StringRes

/** How a mushaf is rendered. TEXT ships in-app (no download); QCF4 is a downloadable glyph bundle. */
enum class MushafFormat { TEXT, QCF4 }

/**
 * A selectable mushaf print — a (riwaya × format) pair. The catalogue is generated from those two
 * axes in [MushafRegistry], so the whole system stays riwaya-driven: adding Qaloon is a [Riwaya]
 * entry plus its strings (and, for QCF4, a `RiwayaConfig` row), with no new print classes.
 *
 * [id] is the stable preferences key (e.g. "warsh_text", "hafs_qcf4") — unchanged from the former
 * per-object prints, so existing selections keep resolving.
 */
data class MushafPrint(
    val riwaya: Riwaya,
    val format: MushafFormat,
    @StringRes val nameRes: Int,
    @StringRes val descRes: Int,
) {
    val id: String get() = "${riwaya.dbKey}_${format.name.lowercase()}"
    val requiresDownload: Boolean get() = format == MushafFormat.QCF4
}
