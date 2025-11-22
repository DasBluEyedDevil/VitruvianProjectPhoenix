package com.example.vitruvianredux.util

/**
 * Predefined color schemes for the Vitruvian LED display.
 */
object ColorSchemes {
    val BLUE = ColorScheme(
        name = "Blue",
        brightness = 0.4f,
        colors = listOf(
            RGBColor(0, 168, 221),
            RGBColor(0, 206, 252),
            RGBColor(93, 223, 252)
        )
    )

    val GREEN = ColorScheme(
        name = "Green",
        brightness = 0.4f,
        colors = listOf(
            RGBColor(125, 193, 71),
            RGBColor(161, 216, 106),
            RGBColor(186, 224, 148)
        )
    )

    val TEAL = ColorScheme(
        name = "Teal",
        brightness = 0.4f,
        colors = listOf(
            RGBColor(62, 154, 183),
            RGBColor(129, 190, 209),
            RGBColor(194, 223, 232)
        )
    )

    val YELLOW = ColorScheme(
        name = "Yellow",
        brightness = 0.4f,
        colors = listOf(
            RGBColor(255, 144, 81),
            RGBColor(255, 214, 71),
            RGBColor(255, 183, 0)
        )
    )

    val PINK = ColorScheme(
        name = "Pink",
        brightness = 0.4f,
        colors = listOf(
            RGBColor(255, 0, 76),
            RGBColor(255, 35, 136),
            RGBColor(255, 136, 136)
        )
    )

    val RED = ColorScheme(
        name = "Red",
        brightness = 0.4f,
        colors = listOf(
            RGBColor(255, 0, 0),
            RGBColor(255, 85, 85),
            RGBColor(255, 170, 170)
        )
    )

    val PURPLE = ColorScheme(
        name = "Purple",
        brightness = 0.4f,
        colors = listOf(
            RGBColor(128, 0, 255),
            RGBColor(170, 85, 255),
            RGBColor(221, 170, 255)
        )
    )

    val ALL = listOf(BLUE, GREEN, TEAL, YELLOW, PINK, RED, PURPLE)
}
