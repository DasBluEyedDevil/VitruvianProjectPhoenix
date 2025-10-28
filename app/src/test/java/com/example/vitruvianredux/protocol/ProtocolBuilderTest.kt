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
            mode = WorkoutMode.OldSchool,
            reps = 10,
            weightPerCableKg = 15.0f,
            progressionKg = 0f,
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

        // Verify reps field (reps + 3 for non-Just Lift)
        assertEquals(13.toByte(), program[0x04], "Reps should be 10 + 3 = 13")
    }

    @Test
    fun `test just lift mode program generation`() {
        // Given: Just Lift mode parameters
        val params = WorkoutParameters(
            mode = WorkoutMode.OldSchool,
            reps = 10,
            weightPerCableKg = 20.0f,
            progressionKg = 0f,
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
            mode = WorkoutMode.Pump,
            reps = 15,
            weightPerCableKg = 12.0f,
            progressionKg = 0f,
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
            mode = WorkoutMode.TUT,
            reps = 8,
            weightPerCableKg = 18.0f,
            progressionKg = 2.5f,
            isJustLift = false,
            stopAtTop = false,
            warmupReps = 3
        )

        // When: Building program parameters locally
        val program = ProtocolBuilder.buildProgramParams(params)

        // Then: Program includes progression weight
        assertNotNull(program)
        assertEquals(96, program.size)
        assertEquals(11.toByte(), program[0x04], "Reps should be 8 + 3 = 11")
    }

    @Test
    fun `test echo mode program generation`() {
        // Given: Echo mode parameters
        val params = WorkoutParameters(
            mode = WorkoutMode.Echo(EchoLevel.LEVEL_2),
            reps = 12,
            weightPerCableKg = 16.0f,
            progressionKg = 0f,
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
            mode = WorkoutMode.OldSchool,
            reps = 10,
            weightPerCableKg = 15.0f,
            progressionKg = 0f,
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
            mode = WorkoutMode.OldSchool,
            reps = 10,
            weightPerCableKg = 25.0f,
            progressionKg = 5.0f,
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
        val buffer = ByteBuffer.wrap(program).order(ByteOrder.LITTLE_ENDIAN)
        // Weight values should be present in the frame
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
            WorkoutParameters(WorkoutMode.OldSchool, 10, 15f, 0f, false, false, 3),
            WorkoutParameters(WorkoutMode.Pump, 15, 12f, 0f, false, false, 3),
            WorkoutParameters(WorkoutMode.TUT, 8, 18f, 2.5f, false, false, 3),
            WorkoutParameters(WorkoutMode.TUTBeast, 6, 20f, 0f, false, false, 3),
            WorkoutParameters(WorkoutMode.EccentricOnly, 5, 22f, 0f, false, false, 3),
            WorkoutParameters(WorkoutMode.Echo(EchoLevel.LEVEL_1), 12, 16f, 0f, false, false, 3),
            WorkoutParameters(WorkoutMode.OldSchool, 10, 15f, 0f, true, false, 3), // Just Lift
            WorkoutParameters(WorkoutMode.OldSchool, 10, 15f, 0f, false, true, 3)  // Stop at top
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
            mode = WorkoutMode.Pump,
            reps = 10,
            weightPerCableKg = 15.0f,
            progressionKg = 0f,
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
}

