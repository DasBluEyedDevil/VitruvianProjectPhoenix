package com.example.vitruvianredux.domain.model

data class Exercise(
    val name: String,
    val muscleGroup: String,
    val equipment: String = "",
    val defaultCableConfig: CableConfiguration = CableConfiguration.DOUBLE,
    val id: String? = null
) {
    val displayName: String
        get() = name
}
