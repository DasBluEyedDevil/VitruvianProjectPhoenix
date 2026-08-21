package com.ninthlevel.phoenix.domain.model

/**
 * Cable configuration for trainer exercises
 * - SINGLE: One cable only (unilateral - e.g., one-arm row)
 * - DOUBLE: Both cables required (bilateral - e.g., bench press)
 * - EITHER: User can choose single or double (e.g., bicep curls)
 */
enum class CableConfiguration {
    SINGLE,
    DOUBLE,
    EITHER
}

/**
 * Exercise model for picker-selected and routine-stored movements.
 *
 * NOTES:
 * - Cables pull upward from the floor platform
 * - Compatible: Rows, presses, curls, squats, deadlifts, raises
 * - Machine tracks each cable independently (loadA, loadB, posA, posB)
 * - Weight is always specified as "per cable" in the BLE protocol
 */
data class Exercise(
    val name: String,
    val muscleGroup: String,
    val equipment: String = "",
    val defaultCableConfig: CableConfiguration = CableConfiguration.DOUBLE,
    val id: String? = null  // Optional library id
) {
    val displayName: String
        get() = name
}

fun cableConfigurationOf(raw: String): CableConfiguration =
    runCatching { CableConfiguration.valueOf(raw) }.getOrDefault(CableConfiguration.DOUBLE)
