package com.lhacenmed.khatmah.feature.mushaf.data

import androidx.annotation.StringRes
import com.lhacenmed.khatmah.R

/**
 * Represents a selectable Quran mushaf print.
 *
 * To add a new print: add a [data object] here, register it in [MushafRegistry],
 * handle its download in [PrintSelectViewModel], and add strings.
 */
sealed class MushafPrint(
    val id: String,
    val riwaya: Riwaya,
    @StringRes val nameRes: Int,
    @StringRes val descRes: Int,
    val requiresDownload: Boolean,
) {
    data object WarshText : MushafPrint(
        id               = "warsh_text",
        riwaya           = Riwaya.WARSH,
        nameRes          = R.string.print_warsh_text,
        descRes          = R.string.print_warsh_text_desc,
        requiresDownload = false,
    )

    data object HafsText : MushafPrint(
        id               = "hafs_text",
        riwaya           = Riwaya.HAFS,
        nameRes          = R.string.print_hafs_text,
        descRes          = R.string.print_hafs_text_desc,
        requiresDownload = false,
    )

    data object WarshImages : MushafPrint(
        id               = "warsh_images",
        riwaya           = Riwaya.WARSH,
        nameRes          = R.string.print_warsh_images,
        descRes          = R.string.print_warsh_images_desc,
        requiresDownload = true,
    )

    data object WarshSvg : MushafPrint(
        id               = "warsh_svg",
        riwaya           = Riwaya.WARSH,
        nameRes          = R.string.print_warsh_svg,
        descRes          = R.string.print_warsh_svg_desc,
        requiresDownload = true,
    )

    data object WarshQcf4 : MushafPrint(
        id               = "warsh_qcf4",
        riwaya           = Riwaya.WARSH,
        nameRes          = R.string.print_warsh_qcf4,
        descRes          = R.string.print_warsh_qcf4_desc,
        requiresDownload = true,
    )

    data object HafsQcf4 : MushafPrint(
        id               = "hafs_qcf4",
        riwaya           = Riwaya.HAFS,
        nameRes          = R.string.print_hafs_qcf4,
        descRes          = R.string.print_hafs_qcf4_desc,
        requiresDownload = true,
    )
}