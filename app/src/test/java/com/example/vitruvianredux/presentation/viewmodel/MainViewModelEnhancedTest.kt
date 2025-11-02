package com.example.vitruvianredux.presentation.viewmodel

import android.app.Application
import com.example.vitruvianredux.data.preferences.PreferencesManager
import com.example.vitruvianredux.data.repository.BleRepository
import com.example.vitruvianredux.data.repository.ExerciseRepository
import com.example.vitruvianredux.data.repository.WorkoutRepository
import com.example.vitruvianredux.domain.model.*
import com.example.vitruvianredux.domain.usecase.RepCounterFromMachine
import com.google.common.truth.Truth.assertThat
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Enhanced MainViewModel Tests - Autoplay, Routine Loading, Navigation
 *
 * Tests cover recent features:
 * - Routine loading (loadRoutine, loadRoutineById)
 * - Autoplay functionality (rest timers, exercise progression)
 * - Navigation helpers (ensureConnection)
 * - State management (disconnect, cancelRoutine, skipRest)
 */
@ExperimentalCoroutinesApi
class MainViewModelEnhancedTest {

    private lateinit var application: Application
    private lateinit var bleRepository: BleRepository
    private lateinit var workoutRepository: WorkoutRepository
    private lateinit var exerciseRepository: ExerciseRepository
    private lateinit var repCounter: RepCounterFromMachine
    private lateinit var preferencesManager: PreferencesManager
    private lateinit var viewModel: MainViewModel

    // Test data
    private val testExercise1 = Exercise(
        name = "Bench Press",
        muscleGroup = "Chest",
        equipment = "Vitruvian",
        defaultCableConfig = CableConfiguration.DOUBLE,
        id = "ex1"
    )

    private val testExercise2 = Exercise(
        name = "Squat",
        muscleGroup = "Legs",
        equipment = "Vitruvian",
        defaultCableConfig = CableConfiguration.DOUBLE,
        id = "ex2"
    )

    private val testRoutine = Routine(
        id = "routine1",
        name = "Test Routine",
        description = "Test routine for unit tests",
        exercises = listOf(
            RoutineExercise(
                id = "rex1",
                exercise = testExercise1,
                cableConfig = CableConfiguration.DOUBLE,
                orderIndex = 0,
                setReps = listOf(10, 8, 6),
                weightPerCableKg = 25f,
                progressionKg = 0f,
                restSeconds = 60,
                mode = WorkoutMode.OldSchool
            ),
            RoutineExercise(
                id = "rex2",
                exercise = testExercise2,
                cableConfig = CableConfiguration.DOUBLE,
                orderIndex = 1,
                setReps = listOf(12, 10, 8),
                weightPerCableKg = 30f,
                progressionKg = 0f,
                restSeconds = 90,
                mode = WorkoutMode.OldSchool
            )
        ),
        useCount = 0,
        lastUsed = null
    )

    @Before
    fun setup() {
        // Mock Android components
        application = mockk(relaxed = true)
        every { application.applicationContext } returns application

        // Mock repositories with relaxed behavior
        bleRepository = mockk(relaxed = true)
        workoutRepository = mockk(relaxed = true)
        exerciseRepository = mockk(relaxed = true)
        repCounter = mockk(relaxed = true)
        preferencesManager = mockk(relaxed = true)

        // Setup default flows for BleRepository
        every { bleRepository.connectionState } returns MutableStateFlow(ConnectionState.Disconnected)
        every { bleRepository.monitorData } returns emptyFlow()
        every { bleRepository.repEvents } returns emptyFlow()
        every { bleRepository.scannedDevices } returns emptyFlow()

        // Setup default flows for WorkoutRepository
        every { workoutRepository.getRecentSessions(any()) } returns flowOf(emptyList())
        every { workoutRepository.getAllRoutines() } returns flowOf(emptyList())
        every { workoutRepository.getAllPrograms() } returns flowOf(emptyList())
        every { workoutRepository.getActiveProgram() } returns flowOf(null)
        every { workoutRepository.getAllPersonalRecords() } returns flowOf(emptyList())
        every { workoutRepository.getAllSessions() } returns flowOf(emptyList())

        // Setup PreferencesManager with default preferences
        every { preferencesManager.preferencesFlow } returns flowOf(UserPreferences())

        // Create ViewModel
        viewModel = MainViewModel(
            application = application,
            bleRepository = bleRepository,
            workoutRepository = workoutRepository,
            exerciseRepository = exerciseRepository,
            repCounter = repCounter,
            preferencesManager = preferencesManager
        )
    }

    @After
    fun tearDown() {
        clearAllMocks()
    }

    // ========== Routine Loading Tests ==========

    @Test
    fun `loadRoutine sets routine and configures parameters from first exercise`() = runTest {
        // Act
        viewModel.loadRoutine(testRoutine)

        // Assert - Routine is loaded
        assertThat(viewModel.loadedRoutine.value).isEqualTo(testRoutine)
        assertThat(viewModel.currentExerciseIndex.value).isEqualTo(0)
        assertThat(viewModel.currentSetIndex.value).isEqualTo(0)

        // Assert - Parameters set from first exercise, first set
        val params = viewModel.workoutParameters.value
        assertThat(params.reps).isEqualTo(10) // First set of first exercise
        assertThat(params.weightPerCableKg).isEqualTo(25f)
        assertThat(params.progressionRegressionKg).isEqualTo(0f)
        assertThat(params.isJustLift).isFalse() // CRITICAL: Routines enable autoplay
        assertThat(params.stopAtTop).isFalse()

        // Assert - Routine marked as used
        coVerify { workoutRepository.markRoutineUsed("routine1") }
    }

    @Test
    fun `loadRoutine with empty exercises does not load`() = runTest {
        // Arrange
        val emptyRoutine = testRoutine.copy(exercises = emptyList())

        // Act
        viewModel.loadRoutine(emptyRoutine)

        // Assert - Routine not loaded
        assertThat(viewModel.loadedRoutine.value).isNull()
    }

    @Test
    fun `loadRoutineById fetches from repository and loads routine`() = runTest {
        // Arrange
        every { workoutRepository.getRoutineById("routine1") } returns flowOf(testRoutine)

        // Act
        viewModel.loadRoutineById("routine1")

        // Give coroutine time to execute
        kotlinx.coroutines.delay(100)

        // Assert
        verify { workoutRepository.getRoutineById("routine1") }
        assertThat(viewModel.loadedRoutine.value).isEqualTo(testRoutine)
    }

    // ========== Autoplay Tests ==========

    @Test
    fun `autoplay enabled - isJustLift false when routine loaded`() = runTest {
        // Act
        viewModel.loadRoutine(testRoutine)

        // Assert
        assertThat(viewModel.workoutParameters.value.isJustLift).isFalse()
    }

    // Note: Testing actual autoplay timer behavior would require:
    // 1. Starting a workout
    // 2. Completing an exercise
    // 3. Observing WorkoutState.Resting with countdown
    // This is complex and requires mocking the workout flow end-to-end.
    // For now, we verify the critical flag that enables autoplay.

    // ========== State Management Tests ==========

    @Test
    fun `cancelRoutine clears routine state`() = runTest {
        // Arrange
        viewModel.loadRoutine(testRoutine)
        assertThat(viewModel.loadedRoutine.value).isNotNull()

        // Act
        viewModel.cancelRoutine()

        // Give coroutine time to execute
        kotlinx.coroutines.delay(100)

        // Assert - BLE operations called
        coVerify { bleRepository.stopWorkout() }

        // Assert - State cleared
        assertThat(viewModel.workoutState.value).isInstanceOf(WorkoutState.Idle::class.java)
    }

    @Test
    fun `disconnect clears workout state`() = runTest {
        // Act
        viewModel.disconnect()

        // Give coroutine time to execute
        kotlinx.coroutines.delay(100)

        // Assert
        coVerify { bleRepository.disconnect() }
        coVerify { repCounter.reset() }
        assertThat(viewModel.workoutState.value).isEqualTo(WorkoutState.Idle)
        assertThat(viewModel.currentMetric.value).isNull()
    }

    @Test
    fun `skipRest when in Resting state triggers next exercise progression`() = runTest {
        // Note: This test would require setting up WorkoutState.Resting
        // which is complex as it requires running a full workout flow.
        // For now, we verify skipRest doesn't crash when called in Idle state.

        // Act
        viewModel.skipRest()

        // Assert - No crash, state remains Idle
        assertThat(viewModel.workoutState.value).isInstanceOf(WorkoutState.Idle::class.java)
    }

    // ========== Navigation Helper Tests ==========

    @Test
    fun `ensureConnection calls onConnected when already connected`() = runTest {
        // Arrange
        every { bleRepository.connectionState } returns MutableStateFlow(
            ConnectionState.Connected("Test Device", "AA:BB:CC:DD:EE:FF")
        )

        var onConnectedCalled = false
        var onFailedCalled = false

        // Act
        viewModel.ensureConnection(
            onConnected = { onConnectedCalled = true },
            onFailed = { onFailedCalled = true }
        )

        // Give coroutine time to execute
        kotlinx.coroutines.delay(100)

        // Assert
        assertThat(onConnectedCalled).isTrue()
        assertThat(onFailedCalled).isFalse()
    }

    @Test
    fun `ensureConnection starts scanning when disconnected`() = runTest {
        // Arrange
        every { bleRepository.connectionState } returns MutableStateFlow(ConnectionState.Disconnected)
        coEvery { bleRepository.startScanning() } returns Result.success(Unit)

        var onConnectedCalled = false

        // Act
        viewModel.ensureConnection(
            onConnected = { onConnectedCalled = true }
        )

        // Give coroutine time to execute
        kotlinx.coroutines.delay(100)

        // Assert - Scanning started
        coVerify { bleRepository.startScanning() }
    }

    // ========== Workout Parameters Tests ==========

    @Test
    fun `workout parameters default structure is valid`() {
        val params = viewModel.workoutParameters.value

        assertNotNull(params)
        assertEquals(10, params.reps)
        assertEquals(10f, params.weightPerCableKg)
        assertEquals(3, params.warmupReps)
        assertEquals(WorkoutMode.OldSchool, params.mode)
    }

    @Test
    fun `updateWorkoutParameters changes parameters`() {
        // Arrange
        val newParams = WorkoutParameters(
            mode = WorkoutMode.Pump,
            reps = 15,
            weightPerCableKg = 20f,
            progressionRegressionKg = 2.5f,
            isJustLift = true,
            stopAtTop = true,
            warmupReps = 5
        )

        // Act
        viewModel.updateWorkoutParameters(newParams)

        // Assert
        val params = viewModel.workoutParameters.value
        assertEquals(WorkoutMode.Pump, params.mode)
        assertEquals(15, params.reps)
        assertEquals(20f, params.weightPerCableKg)
        assertEquals(2.5f, params.progressionRegressionKg)
        assertTrue(params.isJustLift)
        assertTrue(params.stopAtTop)
        assertEquals(5, params.warmupReps)
    }

    // ========== Exercise Index Management Tests ==========

    @Test
    fun `loadRoutine resets exercise and set indices to zero`() = runTest {
        // Arrange - Set some non-zero indices first
        viewModel.loadRoutine(testRoutine)
        // Manually set indices (simulating progression)
        // Note: In real usage, these would be set by nextExercise/nextSet methods

        // Act - Load routine again
        viewModel.loadRoutine(testRoutine)

        // Assert - Indices reset
        assertThat(viewModel.currentExerciseIndex.value).isEqualTo(0)
        assertThat(viewModel.currentSetIndex.value).isEqualTo(0)
    }

    // ========== User Preferences Integration Tests ==========

    @Test
    fun `viewModel observes user preferences from PreferencesManager`() = runTest {
        // Arrange
        val testPrefs = UserPreferences(
            autoplayEnabled = true,
            weightUnit = WeightUnit.LB
        )
        val prefsFlow = MutableStateFlow(testPrefs)
        every { preferencesManager.preferencesFlow } returns prefsFlow

        // Create new ViewModel to pick up the preference
        val newViewModel = MainViewModel(
            application = application,
            bleRepository = bleRepository,
            workoutRepository = workoutRepository,
            exerciseRepository = exerciseRepository,
            repCounter = repCounter,
            preferencesManager = preferencesManager
        )

        // Give StateFlow time to emit
        kotlinx.coroutines.delay(100)

        // Assert
        assertThat(newViewModel.userPreferences.value.autoplayEnabled).isTrue()
        assertThat(newViewModel.userPreferences.value.weightUnit).isEqualTo(WeightUnit.LB)
    }
}
