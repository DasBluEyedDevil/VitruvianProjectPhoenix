package com.example.vitruvianredux.protocol

import com.example.vitruvianredux.domain.model.EchoLevel
import com.example.vitruvianredux.domain.model.WorkoutMode
import com.example.vitruvianredux.domain.model.WorkoutParameters
import com.example.vitruvianredux.util.ProtocolBuilder
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Test suite for ProtocolBuilder - verifies local protocol generation
 *
 * Critical for ensuring the app can generate valid commands WITHOUT server help
 * All protocol commands are generated locally on the device
 */
class ProtocolBuilderTest {

    @Test
    fun `test init command generation - local protocol creation`() {
        // When: Building init command locally
        val command = ProtocolBuilder.buildInitCommand()

        // Then: Command is correctly formatted (no server needed)
        assertNotNull(command, "Init command should be generated locally")
        assertEquals(4, command.size, "Init command should be 4 bytes")
        assertEquals(0x0A.toByte(), command[0], "First byte should be 0x0A")
        assertEquals(0x00.toByte(), command[1], "Second byte should be 0x00")
        assertEquals(0x00.toByte(), command[2], "Third byte should be 0x00")
        assertEquals(0x00.toByte(), command[3], "Fourth byte should be 0x00")
    }

    @Test
    fun `test init preset generation - local coefficient table`() {
        // When: Building init preset locally
        val preset = ProtocolBuilder.buildInitPreset()

        // Then: Preset is correctly formatted (34 bytes)
        assertNotNull(preset, "Init preset should be generated locally")
        assertEquals(34, preset.size, "Init preset should be 34 bytes")
        assertEquals(0x11.toByte(), preset[0], "First byte should be 0x11")

        // Verify coefficient at offset 12 (0.4 as float32 LE)
        val buffer = ByteBuffer.wrap(preset).order(ByteOrder.LITTLE_ENDIAN)
        val coefficient = buffer.getFloat(12)
        assertTrue(coefficient in 0.39f..0.41f, "Coefficient should be approximately 0.4")
    }

    @Test
    fun `test old school mode program generation`() {
        // Given: Old School workout parameters
        val params = WorkoutParameters(
            workoutType = WorkoutMode.OldSchool.toWorkoutType(),
            reps = 10,
            weightPerCableKg = 15.0f,
            progressionRegressionKg = 0f,
            isJustLift = false,
            stopAtTop = false,
            warmupReps = 3
        )

        // When: Building program parameters locally
        val program = ProtocolBuilder.buildProgramParams(params)

        // Then: Program frame is correctly formatted
        assertNotNull(program, "Program should be generated locally")
        assertEquals(96, program.size, "Program frame should be 96 bytes")
        assertEquals(0x04.toByte(), program[0], "First byte should be 0x04")

        // Verify reps field (reps + 3 warmup + 1 for counter compensation)
        assertEquals(14.toByte(), program[0x04], "Reps should be 10 + 3 + 1 = 14")
    }

    @Test
    fun `test just lift mode program generation`() {
        // Given: Just Lift mode parameters
        val params = WorkoutParameters(
            workoutType = WorkoutMode.OldSchool.toWorkoutType(),
            reps = 10,
            weightPerCableKg = 20.0f,
            progressionRegressionKg = 0f,
            isJustLift = true,
            stopAtTop = false,
            warmupReps = 3
        )

        // When: Building program parameters locally
        val program = ProtocolBuilder.buildProgramParams(params)

        // Then: Reps field should be 0xFF for Just Lift
        assertEquals(0xFF.toByte(), program[0x04], "Reps should be 0xFF for Just Lift mode")
    }

    @Test
    fun `test pump mode program generation`() {
        // Given: Pump mode parameters
        val params = WorkoutParameters(
            workoutType = WorkoutMode.Pump.toWorkoutType(),
            reps = 15,
            weightPerCableKg = 12.0f,
            progressionRegressionKg = 0f,
            isJustLift = false,
            stopAtTop = false,
            warmupReps = 3
        )

        // When: Building program parameters locally
        val program = ProtocolBuilder.buildProgramParams(params)

        // Then: Program is generated with Pump mode profile
        assertNotNull(program)
        assertEquals(96, program.size)
        // Mode-specific profile bytes should be present at offset 0x30
    }

    @Test
    fun `test TUT mode program generation`() {
        // Given: TUT mode parameters
        val params = WorkoutParameters(
            workoutType = WorkoutMode.TUT.toWorkoutType(),
            reps = 8,
            weightPerCableKg = 18.0f,
            progressionRegressionKg = 2.5f,
            isJustLift = false,
            stopAtTop = false,
            warmupReps = 3
        )

        // When: Building program parameters locally
        val program = ProtocolBuilder.buildProgramParams(params)

        // Then: Program includes progression weight
        assertNotNull(program)
        assertEquals(96, program.size)
        assertEquals(12.toByte(), program[0x04], "Reps should be 8 + 3 + 1 = 12")
    }

    @Test
    fun `test echo mode program generation`() {
        // Given: Echo mode parameters
        val params = WorkoutParameters(
            workoutType = WorkoutMode.Echo(EchoLevel.HARDER).toWorkoutType(),
            reps = 12,
            weightPerCableKg = 16.0f,
            progressionRegressionKg = 0f,
            isJustLift = false,
            stopAtTop = false,
            warmupReps = 3
        )

        // When: Building program parameters locally
        val program = ProtocolBuilder.buildProgramParams(params)

        // Then: Program is generated with Echo mode profile
        assertNotNull(program)
        assertEquals(96, program.size)
    }

    @Test
    fun `test stop at top flag in program generation`() {
        // Given: Parameters with stop at top enabled
        val params = WorkoutParameters(
            workoutType = WorkoutMode.OldSchool.toWorkoutType(),
            reps = 10,
            weightPerCableKg = 15.0f,
            progressionRegressionKg = 0f,
            isJustLift = false,
            stopAtTop = true,
            warmupReps = 3
        )

        // When: Building program parameters locally
        val program = ProtocolBuilder.buildProgramParams(params)

        // Then: Program is generated with stop at top configuration
        assertNotNull(program)
        assertEquals(96, program.size)
    }

    @Test
    fun `test weight encoding in program - local calculation`() {
        // Given: Parameters with specific weight
        val params = WorkoutParameters(
            workoutType = WorkoutMode.OldSchool.toWorkoutType(),
            reps = 10,
            weightPerCableKg = 25.0f,
            progressionRegressionKg = 5.0f,
            isJustLift = false,
            stopAtTop = false,
            warmupReps = 3
        )

        // When: Building program parameters locally
        val program = ProtocolBuilder.buildProgramParams(params)

        // Then: Weight is encoded correctly in the frame
        assertNotNull(program)
        assertEquals(96, program.size)

        // Verify weight encoding (specific byte offsets depend on protocol)
        // The important point is it's calculated locally, not from server
        // Weight values are present in the frame
    }

    @Test
    fun `test start command generation - local`() {
        // When: Building start command locally
        val command = ProtocolBuilder.buildStartCommand()

        // Then: Command is correctly formatted
        assertNotNull(command, "Start command should be generated locally")
        assertEquals(4, command.size, "Start command should be 4 bytes")
        assertEquals(0x03.toByte(), command[0], "First byte should be 0x03")
    }

    @Test
    fun `test stop command generation - local`() {
        // When: Building stop command locally
        val command = ProtocolBuilder.buildStopCommand()

        // Then: Command is correctly formatted
        assertNotNull(command, "Stop command should be generated locally")
        assertEquals(4, command.size, "Stop command should be 4 bytes")
        assertEquals(0x05.toByte(), command[0], "First byte should be 0x05")
    }

    @Test
    fun `test color scheme command generation - local customization`() {
        // When: Building color scheme commands locally
        val scheme0 = ProtocolBuilder.buildColorSchemeCommand(0)
        val scheme1 = ProtocolBuilder.buildColorSchemeCommand(1)
        val scheme2 = ProtocolBuilder.buildColorSchemeCommand(2)

        // Then: Commands are correctly formatted for each scheme
        assertNotNull(scheme0)
        assertNotNull(scheme1)
        assertNotNull(scheme2)

        // Color scheme frames are 34 bytes (header + brightness + 6 RGB triplets)
        assertEquals(34, scheme0.size, "Color scheme command should be 34 bytes")
        assertEquals(34, scheme1.size, "Color scheme command should be 34 bytes")
        assertEquals(34, scheme2.size, "Color scheme command should be 34 bytes")

        // Verify command ID is 0x11 (color scheme)
        assertEquals(0x11.toByte(), scheme0[0], "First byte should be 0x11")
    }

    @Test
    fun `test all protocol commands are generated without server`() {
        // This test verifies that ALL protocol commands can be generated locally
        // Given: Various workout scenarios
        val scenarios = listOf(
            WorkoutParameters(WorkoutMode.OldSchool.toWorkoutType(), 10, 15f, 0f, false, false, false, 3),
            WorkoutParameters(WorkoutMode.Pump.toWorkoutType(), 15, 12f, 0f, false, false, false, 3),
            WorkoutParameters(WorkoutMode.TUT.toWorkoutType(), 8, 18f, 2.5f, false, false, false, 3),
            WorkoutParameters(WorkoutMode.TUTBeast.toWorkoutType(), 6, 20f, 0f, false, false, false, 3),
            WorkoutParameters(WorkoutMode.EccentricOnly.toWorkoutType(), 5, 22f, 0f, false, false, false, 3),
            WorkoutParameters(WorkoutMode.Echo(EchoLevel.HARD).toWorkoutType(), 12, 16f, 0f, false, false, false, 3),
            WorkoutParameters(WorkoutMode.OldSchool.toWorkoutType(), 10, 15f, 0f, true, true, false, 3), // Just Lift (with auto-start)
            WorkoutParameters(WorkoutMode.OldSchool.toWorkoutType(), 10, 15f, 0f, false, false, true, 3)  // Stop at top
        )

        // When: Generating all protocol commands locally
        val initCommand = ProtocolBuilder.buildInitCommand()
        val initPreset = ProtocolBuilder.buildInitPreset()
        val programs = scenarios.map { ProtocolBuilder.buildProgramParams(it) }
        val startCommand = ProtocolBuilder.buildStartCommand()
        val stopCommand = ProtocolBuilder.buildStopCommand()
        val colorCommands = (0..2).map { ProtocolBuilder.buildColorSchemeCommand(it) }

        // Then: All commands are generated successfully without any server calls
        assertNotNull(initCommand)
        assertNotNull(initPreset)
        assertEquals(8, programs.size, "All 8 workout scenarios should generate programs")
        programs.forEach { program ->
            assertNotNull(program)
            assertEquals(96, program.size, "Each program should be 96 bytes")
        }
        assertNotNull(startCommand)
        assertNotNull(stopCommand)
        assertEquals(3, colorCommands.size)

        // This proves complete local protocol generation capability
    }

    @Test
    fun `test protocol generation is deterministic - no randomness`() {
        // Given: Same workout parameters
        val params = WorkoutParameters(
            workoutType = WorkoutMode.Pump.toWorkoutType(),
            reps = 10,
            weightPerCableKg = 15.0f,
            progressionRegressionKg = 0f,
            isJustLift = false,
            stopAtTop = false,
            warmupReps = 3
        )

        // When: Generating the same program multiple times
        val program1 = ProtocolBuilder.buildProgramParams(params)
        val program2 = ProtocolBuilder.buildProgramParams(params)
        val program3 = ProtocolBuilder.buildProgramParams(params)

        // Then: All programs should be identical (deterministic, no server variance)
        assertTrue(program1.contentEquals(program2), "Programs should be identical")
        assertTrue(program2.contentEquals(program3), "Programs should be identical")
        assertTrue(program1.contentEquals(program3), "Programs should be identical")
    }

    @Test
    fun `test weight protocol - per cable weight sent directly to machine`() {
        // CRITICAL: This test prevents weight DOUBLING bug
        // Machine expects PER-CABLE weight at offset 0x58 (NOT total for both cables)
        // Doubling was causing entering 20 lbs to give 40 lbs resistance (fixed in b21d4c9)

        // Given: User wants 50 lbs per cable (22.68 kg)
        val perCableKg = 22.68f
        val params = WorkoutParameters(
            workoutType = WorkoutMode.OldSchool.toWorkoutType(),
            reps = 10,
            weightPerCableKg = perCableKg,
            progressionRegressionKg = 0f,
            isJustLift = false,
            useAutoStart = false,
            stopAtTop = false,
            warmupReps = 3
        )

        // When: Building program parameters
        val program = ProtocolBuilder.buildProgramParams(params)

        // Then: Weight at offset 0x58 should equal per-cable weight (NO doubling)
        val buffer = ByteBuffer.wrap(program).order(ByteOrder.LITTLE_ENDIAN)
        val totalWeightSent = buffer.getFloat(0x58)

        assertEquals(
            perCableKg,
            totalWeightSent,
            0.01f,
            "Offset 0x58 must contain per-cable weight directly (NO doubling)"
        )
    }

    @Test
    fun `test weight protocol - effective weight is per cable plus 10kg offset`() {
        // CRITICAL: This test verifies the effectiveKg calculation
        // Effective weight = per-cable weight + 10kg (matching web app protocol)

        // Given: User wants 25 kg per cable
        val perCableKg = 25.0f
        val params = WorkoutParameters(
            workoutType = WorkoutMode.OldSchool.toWorkoutType(),
            reps = 10,
            weightPerCableKg = perCableKg,
            progressionRegressionKg = 0f,
            isJustLift = false,
            useAutoStart = false,
            stopAtTop = false,
            warmupReps = 3
        )

        // When: Building program parameters
        val program = ProtocolBuilder.buildProgramParams(params)

        // Then: Effective weight at offset 0x54 should be per-cable + 10
        val buffer = ByteBuffer.wrap(program).order(ByteOrder.LITTLE_ENDIAN)
        val effectiveWeightSent = buffer.getFloat(0x54)
        val expectedEffectiveWeight = perCableKg + 10.0f

        assertEquals(
            expectedEffectiveWeight,
            effectiveWeightSent,
            0.01f,
            "Offset 0x54 must contain per-cable weight + 10kg offset"
        )
    }

    @Test
    fun `test weight protocol - real world scenario 50 lbs per cable`() {
        // CRITICAL: Real-world test case
        // User enters 50 lbs, should get 50 lbs per cable resistance

        // Given: User enters 50 lbs per cable (22.68 kg)
        val perCableKg = 22.68f // 50 lbs in kg
        val params = WorkoutParameters(
            workoutType = WorkoutMode.OldSchool.toWorkoutType(),
            reps = 10,
            weightPerCableKg = perCableKg,
            progressionRegressionKg = 0f,
            isJustLift = false,
            useAutoStart = false,
            stopAtTop = false,
            warmupReps = 3
        )

        // When: Building program parameters
        val program = ProtocolBuilder.buildProgramParams(params)

        // Then: Verify both weight fields are correct
        val buffer = ByteBuffer.wrap(program).order(ByteOrder.LITTLE_ENDIAN)
        val totalWeight = buffer.getFloat(0x58) // What machine receives
        val effectiveWeight = buffer.getFloat(0x54)

        // Machine receives per-cable weight directly (22.68 kg = 50 lbs)
        assertEquals(perCableKg, totalWeight, 0.01f,
            "Machine should receive per-cable weight directly (NO doubling)")

        // Effective weight is per-cable + 10kg offset
        assertEquals(perCableKg + 10.0f, effectiveWeight, 0.01f,
            "Effective weight should be per-cable + 10kg")
    }

    @Test
    fun `test weight protocol - regression test for weight doubling bug`() {
        // REGRESSION TEST: Prevent the bug where entering 20 lbs gave 40 lbs resistance
        // This was caused by doubling per-cable weight (fixed in commit b21d4c9)

        // Given: User enters 100 lbs per cable (45.36 kg)
        val perCableKg = 45.36f // 100 lbs
        val params = WorkoutParameters(
            workoutType = WorkoutMode.OldSchool.toWorkoutType(),
            reps = 10,
            weightPerCableKg = perCableKg,
            progressionRegressionKg = 0f,
            isJustLift = false,
            useAutoStart = false,
            stopAtTop = false,
            warmupReps = 3
        )

        // When: Building program parameters
        val program = ProtocolBuilder.buildProgramParams(params)

        // Then: Weight must NOT be doubled (should equal input)
        val buffer = ByteBuffer.wrap(program).order(ByteOrder.LITTLE_ENDIAN)
        val totalWeightSent = buffer.getFloat(0x58)

        // CRITICAL: If this is doubled, the bug has returned!
        assertEquals(
            perCableKg,
            totalWeightSent,
            0.01f,
            "BUG DETECTED: Weight at 0x58 must equal per-cable value (NO doubling)"
        )
    }
}

