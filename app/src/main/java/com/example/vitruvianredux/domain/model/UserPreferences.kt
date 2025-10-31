package com.example.vitruvianredux.domain.model

/**
 * User preferences data class
 */
data class UserPreferences(
    val weightUnit: WeightUnit = WeightUnit.KG,
    val autoplayEnabled: Boolean = true
)
