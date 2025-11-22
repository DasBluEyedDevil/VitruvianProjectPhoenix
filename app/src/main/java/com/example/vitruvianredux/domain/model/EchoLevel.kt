package com.example.vitruvianredux.domain.model

enum class EchoLevel(
    val levelValue: Int,
    val displayName: String
) {
    HARD(0, "Hard"),
    HARDER(1, "Harder"),
    HARDEST(2, "Hardest"),
    EPIC(3, "Epic")
}
