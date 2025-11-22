package com.example.vitruvianredux.domain.model

enum class EccentricLoad(
    val percentage: Int,
    val displayName: String
) {
    LOAD_0(0, "0%"),
    LOAD_25(25, "25%"),
    LOAD_50(50, "50%"),
    LOAD_75(75, "75%"),
    LOAD_100(100, "100%"),
    LOAD_125(125, "125%"),
    LOAD_150(150, "150%")
}
