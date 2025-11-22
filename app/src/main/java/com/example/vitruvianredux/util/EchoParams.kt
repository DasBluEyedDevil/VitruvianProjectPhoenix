package com.example.vitruvianredux.util

/**
 * Echo parameters data class
 */
data class EchoParams(
    val eccentricPct: Int,
    val concentricPct: Int,
    val smoothing: Float,
    val floor: Float,
    val negLimit: Float,
    val gain: Float,
    val cap: Float
)
