package com.example.vitruvianredux.domain.model

/**
 * User preferences data class
 */
data class UserPreferences(
    val weightUnit: WeightUnit = WeightUnit.KG,
    val autoplayEnabled: Boolean = true,
    val stopAtTop: Boolean = false  // false = stop at bottom (extended), true = stop at top (contracted)
)
