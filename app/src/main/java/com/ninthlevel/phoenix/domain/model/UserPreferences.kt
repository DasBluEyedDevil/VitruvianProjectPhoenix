package com.ninthlevel.phoenix.domain.model

/**
 * User preferences data class
 */
data class UserPreferences(
    val weightUnit: WeightUnit = WeightUnit.KG,
    val autoplayEnabled: Boolean = true,
    val stopAtTop: Boolean = false,  // false = stop at bottom (extended), true = stop at top (contracted)
    val beepsEnabled: Boolean = true  // true = play audio cues during workouts, false = haptic only
)
