package com.lhacenmed.khatmah.feature.mushaf.data

/** Single source of truth for all available mushaf prints. */
object MushafRegistry {

    val all: List<MushafPrint> = listOf(
        MushafPrint.WarshText,
        MushafPrint.WarshImages,
        MushafPrint.WarshSvg,
        MushafPrint.HafsQcf4,
    )

    val default: MushafPrint = MushafPrint.WarshText

    fun byId(id: String): MushafPrint = all.find { it.id == id } ?: default

    fun byRiwaya(riwaya: Riwaya): List<MushafPrint> = all.filter { it.riwaya == riwaya }
}