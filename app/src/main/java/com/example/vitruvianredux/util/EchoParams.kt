package com.example.vitruvianredux.util

/**
 * Parameters for Echo mode workouts.
 * Controls the behavior and feel of the echo training mode.
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
