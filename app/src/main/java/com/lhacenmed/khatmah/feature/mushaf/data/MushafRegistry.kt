package com.lhacenmed.khatmah.feature.mushaf.data

object MushafRegistry {

    val all: List<MushafPrint> = listOf(
        MushafPrint.WarshText,
        MushafPrint.WarshImages,
        MushafPrint.WarshSvg,
        MushafPrint.WarshQcf4,
        MushafPrint.HafsText,
        MushafPrint.HafsQcf4,
    )

    val default: MushafPrint = MushafPrint.WarshText

    fun byId(id: String): MushafPrint = all.find { it.id == id } ?: default

    fun byRiwaya(riwaya: Riwaya): List<MushafPrint> = all.filter { it.riwaya == riwaya }
}