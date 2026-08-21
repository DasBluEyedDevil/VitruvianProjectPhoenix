package com.ninthlevel.phoenix.domain.model

/**
 * Canonical muscle-group and equipment tokens used by the built-in exercise
 * list, picker chips, and Insights radar buckets.
 */
object MuscleGroup {
    const val Chest = "Chest"
    const val Back = "Back"
    const val Legs = "Legs"
    const val Shoulders = "Shoulders"
    const val Arms = "Arms"
    const val Core = "Core"

    val all: List<String> = listOf(Chest, Back, Legs, Shoulders, Arms, Core)
}

object Equipment {
    const val LongBar = "Long Bar"
    const val ShortBar = "Short Bar"
    const val Handles = "Handles"
    const val Rope = "Rope"
    const val Belt = "Belt"
    const val AnkleStrap = "Ankle Strap"
    const val Bench = "Bench"
    const val Bodyweight = "Bodyweight"

    val all: List<String> = listOf(
        LongBar, ShortBar, Handles, Rope, Belt, AnkleStrap, Bench, Bodyweight
    )
}

fun matchesEquipmentFilter(equipmentCsv: String, filter: String): Boolean {
    if (filter.isBlank() || filter == "All") return true
    return equipmentCsv.split(",")
        .map { it.trim() }
        .any { it.equals(filter, ignoreCase = false) }
}
