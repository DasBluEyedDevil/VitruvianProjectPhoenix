package com.example.vitruvianredux.util

import com.example.vitruvianredux.domain.model.EchoLevel
import com.example.vitruvianredux.domain.model.ProgramMode
import com.example.vitruvianredux.domain.model.WorkoutParameters
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Protocol Builder - Builds binary protocol frames for Vitruvian device communication
 * Ported from protocol.js and modes.js in the reference web application
 */
object ProtocolBuilder {

    /**
     * Build the initial 4-byte command sent before INIT
     */
    fun buildInitCommand(): ByteArray {
        return byteArrayOf(0x0A, 0x00, 0x00, 0x00)
    }

    /**
     * Build the INIT preset frame with coefficient table (34 bytes)
     */
    fun buildInitPreset(): ByteArray {
        return byteArrayOf(
            0x11, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00,
            0xCD.toByte(), 0xCC.toByte(), 0xCC.toByte(), 0x3E.toByte(), // 0.4 as float32 LE
            0xFF.toByte(), 0x00, 0x4C, 0xFF.toByte(),
            0x23, 0x8C.toByte(), 0xFF.toByte(), 0x8C.toByte(),
            0x8C.toByte(), 0xFF.toByte(), 0x00, 0x4C,
            0xFF.toByte(), 0x23, 0x8C.toByte(), 0xFF.toByte(),
            0x8C.toByte(), 0x8C.toByte()
        )
    }

    /**
     * Build the 96-byte program parameters frame
     * CRITICAL: Working web app uses command 0x04 (verified from console logs)
     */
    fun buildProgramParams(params: WorkoutParameters): ByteArray {
        val frame = ByteArray(96)
        val buffer = ByteBuffer.wrap(frame).order(ByteOrder.LITTLE_ENDIAN)

        // Header section - Command 0x04 for PROGRAM mode (verified from working web app)
        frame[0] = 0x04
        frame[1] = 0x00
        frame[2] = 0x00
        frame[3] = 0x00

        // Reps field at offset 0x04
        // For Just Lift, use 0xFF; for others, use reps+warmup+1
        // The +1 compensates for completeCounter incrementing at START of concentric (not end)
        // Without it, machine releases tension as you BEGIN the final rep
        frame[0x04] = if (params.isJustLift) 0xFF.toByte() else (params.reps + params.warmupReps + 1).toByte()

        // Some constant values from the working capture
        frame[5] = 0x03
        frame[6] = 0x03
        frame[7] = 0x00

        // Float values at 0x08, 0x0c, 0x1c (appear to be constant 5.0)
        buffer.putFloat(0x08, 5.0f)
        buffer.putFloat(0x0c, 5.0f)
        buffer.putFloat(0x1c, 5.0f)

        // Fill in some other fields from the working capture
        frame[0x14] = 0xFA.toByte()
        frame[0x15] = 0x00
        frame[0x16] = 0xFA.toByte()
        frame[0x17] = 0x00
        frame[0x18] = 0xC8.toByte()
        frame[0x19] = 0x00
        frame[0x1a] = 0x1E
        frame[0x1b] = 0x00

        // Repeat pattern
        frame[0x24] = 0xFA.toByte()
        frame[0x25] = 0x00
        frame[0x26] = 0xFA.toByte()
        frame[0x27] = 0x00
        frame[0x28] = 0xC8.toByte()
        frame[0x29] = 0x00
        frame[0x2a] = 0x1E
        frame[0x2b] = 0x00

        frame[0x2c] = 0xFA.toByte()
        frame[0x2d] = 0x00
        frame[0x2e] = 0x50
        frame[0x2f] = 0x00

        // Get the mode profile block (32 bytes for offsets 0x30-0x4F)
        // For Just Lift, use the base mode; otherwise use the mode directly
        val profileMode = when (val workoutType = params.workoutType) {
            is com.example.vitruvianredux.domain.model.WorkoutType.Program -> {
                if (params.isJustLift) {
                    // For Just Lift, use Old School as base mode
                    ProgramMode.OldSchool
                } else {
                    workoutType.mode
                }
            }
            is com.example.vitruvianredux.domain.model.WorkoutType.Echo -> {
                // Echo mode uses Old School as base profile
                ProgramMode.OldSchool
            }
        }
        val profile = getModeProfile(profileMode)
        System.arraycopy(profile, 0, frame, 0x30, profile.size)

        // Calculate weights for protocol
        // FIRMWARE QUIRK: Machine applies progression starting from "rep 0" (before first rep)
        // To get correct behavior where first working rep has base weight,
        // we must subtract progression from base weight when sending to firmware
        val adjustedWeightPerCable = if (params.progressionRegressionKg != 0f) {
            params.weightPerCableKg - params.progressionRegressionKg
        } else {
            params.weightPerCableKg
        }

        val totalWeightKg = adjustedWeightPerCable
        val effectiveKg = adjustedWeightPerCable + 10.0f

        timber.log.Timber.d("=== WEIGHT DEBUG ===")
        timber.log.Timber.d("Per-cable weight (input): ${params.weightPerCableKg} kg")
        timber.log.Timber.d("Progression: ${params.progressionRegressionKg} kg")
        timber.log.Timber.d("Adjusted weight (compensated): $adjustedWeightPerCable kg")
        timber.log.Timber.d("Total weight (sent to 0x58): $totalWeightKg kg")
        timber.log.Timber.d("Effective weight (sent to 0x54): $effectiveKg kg")

        // Effective weight at offset 0x54
        buffer.putFloat(0x54, effectiveKg)

        // Total weight at offset 0x58
        buffer.putFloat(0x58, totalWeightKg)

        // Progression/Regression at offset 0x5C (kg per rep)
        buffer.putFloat(0x5c, params.progressionRegressionKg)

        return frame
    }

    /**
     * Build Echo mode control frame (32 bytes)
     */
    fun buildEchoControl(
        level: EchoLevel,
        warmupReps: Int = 3,
        targetReps: Int = 2,
        isJustLift: Boolean = false,
        eccentricPct: Int = 75
    ): ByteArray {
        val frame = ByteArray(32)
        val buffer = ByteBuffer.wrap(frame).order(ByteOrder.LITTLE_ENDIAN)

        // Command ID at 0x00 (u32) = 0x4E (78 decimal)
        buffer.putInt(0x00, 0x0000004E)

        // Warmup (0x04) and working reps (0x05)
        frame[0x04] = warmupReps.toByte()

        // For Just Lift Echo mode, use 0xFF; otherwise use targetReps+1
        // The +1 compensates for completeCounter incrementing at START of concentric (not end)
        // Without it, machine releases tension as you BEGIN the final rep
        frame[0x05] = if (isJustLift) 0xFF.toByte() else (targetReps + 1).toByte()

        // Reserved at 0x06-0x07 (u16 = 0)
        buffer.putShort(0x06, 0)

        // Get Echo parameters for this level
        val echoParams = getEchoParams(level, eccentricPct)

        // Eccentric % at 0x08 (u16)
        buffer.putShort(0x08, echoParams.eccentricPct.toShort())

        // Concentric % at 0x0A (u16)
        buffer.putShort(0x0a, echoParams.concentricPct.toShort())

        // Smoothing at 0x0C (f32)
        buffer.putFloat(0x0c, echoParams.smoothing)

        // Gain at 0x10 (f32)
        buffer.putFloat(0x10, echoParams.gain)

        // Cap at 0x14 (f32)
        buffer.putFloat(0x14, echoParams.cap)

        // Floor at 0x18 (f32)
        buffer.putFloat(0x18, echoParams.floor)

        // Neg limit at 0x1C (f32)
        buffer.putFloat(0x1c, echoParams.negLimit)

        return frame
    }

    /**
     * Build a 34-byte color scheme packet
     */
    fun buildColorScheme(brightness: Float, colors: List<RGBColor>): ByteArray {
        require(colors.size == 3) { "Color scheme must have exactly 3 colors" }

        val frame = ByteArray(34)
        val buffer = ByteBuffer.wrap(frame).order(ByteOrder.LITTLE_ENDIAN)

        // Command ID: 0x00000011
        buffer.putInt(0, 0x00000011)

        // Reserved fields
        buffer.putInt(4, 0)
        buffer.putInt(8, 0)

        // Brightness (float32)
        buffer.putFloat(12, brightness)

        // Colors: 6 RGB triplets (3 colors repeated twice for left/right mirroring)
        var offset = 16
        for (@Suppress("UNUSED_VARIABLE") i in 0 until 2) {
            // Repeat twice
            for (color in colors) {
                frame[offset++] = color.r.toByte()
                frame[offset++] = color.g.toByte()
                frame[offset++] = color.b.toByte()
            }
        }

        return frame
    }

    /**
     * Get mode profile block for program modes (32 bytes)
     */
    private fun getModeProfile(mode: ProgramMode): ByteArray {
        val buffer = ByteBuffer.allocate(32).order(ByteOrder.LITTLE_ENDIAN)

        when (mode) {
            is ProgramMode.OldSchool -> {
                buffer.putShort(0x00, 0)
                buffer.putShort(0x02, 20)
                buffer.putFloat(0x04, 3.0f)
                buffer.putShort(0x08, 75)
                buffer.putShort(0x0a, 600)
                buffer.putFloat(0x0c, 50.0f)
                buffer.putShort(0x10, -1300)
                buffer.putShort(0x12, -1200)
                buffer.putFloat(0x14, 100.0f)
                buffer.putShort(0x18, -260)
                buffer.putShort(0x1a, -110)
                buffer.putFloat(0x1c, 0.0f)
            }
            is ProgramMode.Pump -> {
                buffer.putShort(0x00, 50)
                buffer.putShort(0x02, 450)
                buffer.putFloat(0x04, 10.0f)
                buffer.putShort(0x08, 500)
                buffer.putShort(0x0a, 600)
                buffer.putFloat(0x0c, 50.0f)
                buffer.putShort(0x10, -700)
                buffer.putShort(0x12, -550)
                buffer.putFloat(0x14, 1.0f)
                buffer.putShort(0x18, -100)
                buffer.putShort(0x1a, -50)
                buffer.putFloat(0x1c, 1.0f)
            }
            is ProgramMode.TUT -> {
                buffer.putShort(0x00, 250)
                buffer.putShort(0x02, 350)
                buffer.putFloat(0x04, 7.0f)
                buffer.putShort(0x08, 450)
                buffer.putShort(0x0a, 600)
                buffer.putFloat(0x0c, 50.0f)
                buffer.putShort(0x10, -900)
                buffer.putShort(0x12, -700)
                buffer.putFloat(0x14, 70.0f)
                buffer.putShort(0x18, -100)
                buffer.putShort(0x1a, -50)
                buffer.putFloat(0x1c, 14.0f)
            }
            is ProgramMode.TUTBeast -> {
                buffer.putShort(0x00, 150)
                buffer.putShort(0x02, 250)
                buffer.putFloat(0x04, 7.0f)
                buffer.putShort(0x08, 350)
                buffer.putShort(0x0a, 450)
                buffer.putFloat(0x0c, 50.0f)
                buffer.putShort(0x10, -900)
                buffer.putShort(0x12, -700)
                buffer.putFloat(0x14, 70.0f)
                buffer.putShort(0x18, -100)
                buffer.putShort(0x1a, -50)
                buffer.putFloat(0x1c, 28.0f)
            }
            is ProgramMode.EccentricOnly -> {
                buffer.putShort(0x00, 50)
                buffer.putShort(0x02, 550)
                buffer.putFloat(0x04, 50.0f)
                buffer.putShort(0x08, 650)
                buffer.putShort(0x0a, 750)
                buffer.putFloat(0x0c, 10.0f)
                buffer.putShort(0x10, -900)
                buffer.putShort(0x12, -700)
                buffer.putFloat(0x14, 70.0f)
                buffer.putShort(0x18, -100)
                buffer.putShort(0x1a, -50)
                buffer.putFloat(0x1c, 20.0f)
            }
        }

        return buffer.array()
    }

    /**
     * Get Echo parameters for a given level
     */
    private fun getEchoParams(level: EchoLevel, eccentricPct: Int): EchoParams {
        val params = EchoParams(
            eccentricPct = eccentricPct,
            concentricPct = 50, // constant
            smoothing = 0.1f,
            floor = 0.0f,
            negLimit = -100.0f,
            gain = 1.0f,
            cap = 50.0f
        )

        return when (level) {
            EchoLevel.HARD -> params.copy(gain = 1.0f, cap = 50.0f)
            EchoLevel.HARDER -> params.copy(gain = 1.25f, cap = 40.0f)
            EchoLevel.HARDEST -> params.copy(gain = 1.667f, cap = 30.0f)
            EchoLevel.EPIC -> params.copy(gain = 3.333f, cap = 15.0f)
        }
    }

    /**
     * Build the START command (4 bytes)
     */
    fun buildStartCommand(): ByteArray {
        return byteArrayOf(0x03, 0x00, 0x00, 0x00)
    }

    /**
     * Build the STOP command (4 bytes)
     */
    fun buildStopCommand(): ByteArray {
        return byteArrayOf(0x05, 0x00, 0x00, 0x00)
    }

    /**
     * Build a color scheme command using predefined schemes
     */
    fun buildColorSchemeCommand(schemeIndex: Int): ByteArray {
        val schemes = ColorSchemes.ALL
        val scheme = schemes.getOrElse(schemeIndex) { schemes[0] }
        return buildColorScheme(scheme.brightness, scheme.colors)
    }

}

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

/**
 * RGB Color data class
 */
data class RGBColor(
    val r: Int,
    val g: Int,
    val b: Int
) {
    init {
        require(r in 0..255) { "Red value must be 0-255" }
        require(g in 0..255) { "Green value must be 0-255" }
        require(b in 0..255) { "Blue value must be 0-255" }
    }
}

/**
 * Predefined color schemes
 */
object ColorSchemes {
    val BLUE = ColorScheme(
        name = "Blue",
        brightness = 0.4f,
        colors = listOf(
            RGBColor(0x00, 0xA8, 0xDD),
            RGBColor(0x00, 0xCF, 0xFC),
            RGBColor(0x5D, 0xDF, 0xFC)
        )
    )

    val GREEN = ColorScheme(
        name = "Green",
        brightness = 0.4f,
        colors = listOf(
            RGBColor(0x7D, 0xC1, 0x47),
            RGBColor(0xA1, 0xD8, 0x6A),
            RGBColor(0xBA, 0xE0, 0x94)
        )
    )

    val TEAL = ColorScheme(
        name = "Teal",
        brightness = 0.4f,
        colors = listOf(
            RGBColor(0x3E, 0x9A, 0xB7),
            RGBColor(0x83, 0xBE, 0xD1),
            RGBColor(0xC2, 0xDF, 0xE8)
        )
    )

    val YELLOW = ColorScheme(
        name = "Yellow",
        brightness = 0.4f,
        colors = listOf(
            RGBColor(0xFF, 0x90, 0x51),
            RGBColor(0xFF, 0xD6, 0x47),
            RGBColor(0xFF, 0xB7, 0x00)
        )
    )

    val PINK = ColorScheme(
        name = "Pink",
        brightness = 0.4f,
        colors = listOf(
            RGBColor(0xFF, 0x00, 0x4C),
            RGBColor(0xFF, 0x23, 0x8C),
            RGBColor(0xFF, 0x8C, 0x8C)
        )
    )

    val RED = ColorScheme(
        name = "Red",
        brightness = 0.4f,
        colors = listOf(
            RGBColor(0xFF, 0x00, 0x00),
            RGBColor(0xFF, 0x55, 0x55),
            RGBColor(0xFF, 0xAA, 0xAA)
        )
    )

    val PURPLE = ColorScheme(
        name = "Purple",
        brightness = 0.4f,
        colors = listOf(
            RGBColor(0x88, 0x00, 0xFF),
            RGBColor(0xAA, 0x55, 0xFF),
            RGBColor(0xDD, 0xAA, 0xFF)
        )
    )

    val ALL = listOf(BLUE, GREEN, TEAL, YELLOW, PINK, RED, PURPLE)
}

/**
 * Color scheme data class
 */
data class ColorScheme(
    val name: String,
    val brightness: Float,
    val colors: List<RGBColor>
)

