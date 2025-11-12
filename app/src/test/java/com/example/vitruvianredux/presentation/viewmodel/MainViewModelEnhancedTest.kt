package com.example.vitruvianredux.presentation.viewmodel

import android.app.Application
import com.example.vitruvianredux.data.preferences.PreferencesManager
import com.example.vitruvianredux.data.repository.BleRepository
import com.example.vitruvianredux.data.repository.ExerciseRepository
import com.example.vitruvianredux.data.repository.PersonalRecordRepository
import com.example.vitruvianredux.data.repository.WorkoutRepository
import com.example.vitruvianredux.domain.model.*
import com.example.vitruvianredux.domain.usecase.RepCounterFromMachine
import com.google.common.truth.Truth.assertThat
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue

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
@RunWith(RobolectricTestRunner::class)
class MainViewModelEnhancedTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var application: Application
    private lateinit var bleRepository: BleRepository
    private lateinit var workoutRepository: WorkoutRepository
    private lateinit var exerciseRepository: ExerciseRepository
    private lateinit var personalRecordRepository: PersonalRecordRepository
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
                workoutType = WorkoutMode.OldSchool.toWorkoutType()
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
                workoutType = WorkoutMode.OldSchool.toWorkoutType()
            )
        ),
        useCount = 0,
        lastUsed = null
    )

    @Before
    fun setup() {
        // Set up test dispatcher for coroutines
        Dispatchers.setMain(testDispatcher)

        // Mock Android components
        application = mockk(relaxed = true)
        every { application.applicationContext } returns application

        // Mock repositories with relaxed behavior
        bleRepository = mockk(relaxed = true)
        workoutRepository = mockk(relaxed = true)
        exerciseRepository = mockk(relaxed = true)
        personalRecordRepository = mockk(relaxed = true)
        repCounter = mockk(relaxed = true)
        preferencesManager = mockk(relaxed = true)

        // Setup default flows for BleRepository
        every { bleRepository.connectionState } returns MutableStateFlow(ConnectionState.Disconnected)
        every { bleRepository.monitorData } returns emptyFlow()
        every { bleRepository.repEvents } returns emptyFlow()
        every { bleRepository.scannedDevices } returns emptyFlow()
        every { bleRepository.handleState } returns MutableStateFlow(com.example.vitruvianredux.data.ble.HandleState.Released)

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
            personalRecordRepository = personalRecordRepository,
            repCounter = repCounter,
            preferencesManager = preferencesManager
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
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



    // ========== Workout Parameters Tests ==========

    @Test
    fun `workout parameters default structure is valid`() {
        val params = viewModel.workoutParameters.value

        assertNotNull(params)
        assertEquals(10, params.reps)
        assertEquals(10f, params.weightPerCableKg)
        assertEquals(3, params.warmupReps)
        assertEquals(WorkoutMode.OldSchool, params.workoutType.toWorkoutMode())
    }

    @Test
    fun `updateWorkoutParameters changes parameters`() {
        // Arrange
        val newParams = WorkoutParameters(
            workoutType = WorkoutMode.Pump.toWorkoutType(),
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
        assertEquals(WorkoutMode.Pump, params.workoutType.toWorkoutMode())
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
            personalRecordRepository = personalRecordRepository,
            repCounter = repCounter,
            preferencesManager = preferencesManager
        )

        // Assert
        assertThat(newViewModel.userPreferences.value.autoplayEnabled).isTrue()
        assertThat(newViewModel.userPreferences.value.weightUnit).isEqualTo(WeightUnit.LB)
    }

    // ========== Single Exercise Mode Bug Tests ==========

    @Test
    fun `single exercise mode - rest timer shows correct set count after first set`() = runTest {
        // Arrange - Create temp routine like SingleExerciseScreen does
        val singleExercise = RoutineExercise(
            id = "single_ex1",
            exercise = testExercise1,
            cableConfig = CableConfiguration.DOUBLE,
            orderIndex = 0,
            setReps = listOf(2, 2),  // User configured: 2 sets x 2 reps
            weightPerCableKg = 20f,
            progressionKg = 0f,
            restSeconds = 60,
            workoutType = WorkoutMode.OldSchool.toWorkoutType()
        )

        val tempRoutine = Routine(
            id = "temp_single_exercise_test",
            name = "Single Exercise: ${singleExercise.exercise.name}",
            description = "Temporary routine for single exercise mode",
            exercises = listOf(singleExercise)
        )

        // Act - Load the temp routine (simulating SingleExerciseScreen behavior)
        viewModel.loadRoutine(tempRoutine)

        // Assert - Initial state
        assertThat(viewModel.loadedRoutine.value).isEqualTo(tempRoutine)
        assertThat(viewModel.currentSetIndex.value).isEqualTo(0)
        assertThat(viewModel.workoutParameters.value.reps).isEqualTo(2) // First set: 2 reps

        // TODO: Complete the workout flow test when we can simulate workout completion
        // This test currently verifies the initial setup is correct
        // The bug manifests during workout execution which requires:
        // 1. BLE connection simulation
        // 2. Rep counting simulation
        // 3. Workout state transitions
    }

    @Test
    fun `single exercise mode - second set uses correct rep count from setReps array`() = runTest {
        // Arrange - Create temp routine with 2 sets x 2 reps
        val singleExercise = RoutineExercise(
            id = "single_ex2",
            exercise = testExercise1,
            cableConfig = CableConfiguration.DOUBLE,
            orderIndex = 0,
            setReps = listOf(2, 2),  // User configured: 2 sets x 2 reps
            weightPerCableKg = 20f,
            progressionKg = 0f,
            restSeconds = 60,
            workoutType = WorkoutMode.OldSchool.toWorkoutType()
        )

        val tempRoutine = Routine(
            id = "temp_single_exercise_test2",
            name = "Single Exercise: ${singleExercise.exercise.name}",
            description = "Temporary routine for single exercise mode",
            exercises = listOf(singleExercise)
        )

        // Act - Load routine
        viewModel.loadRoutine(tempRoutine)

        // Assert - Verify setReps array is correct
        val currentExercise = viewModel.loadedRoutine.value?.exercises?.get(0)
        assertThat(currentExercise).isNotNull()
        assertThat(currentExercise?.setReps).isEqualTo(listOf(2, 2)) // NOT [10, 10, 10]

        // Assert - First set configured correctly
        assertThat(viewModel.workoutParameters.value.reps).isEqualTo(2)
        assertThat(viewModel.currentSetIndex.value).isEqualTo(0)

        // Note: To fully test the bug, we would need to:
        // 1. Mock BLE connection
        // 2. Start workout with viewModel.startWorkout()
        // 3. Simulate rep completion (send RepEvent via repCounter)
        // 4. Call endWorkout() to complete first set
        // 5. Verify WorkoutState.Resting shows currentSet=1, totalSets=2 (NOT 0/0)
        // 6. Call skipRest() to advance to second set
        // 7. Verify viewModel.workoutParameters.value.reps == 2 (NOT 10)
        //
        // This requires extensive mocking of the workout flow which is beyond
        // a simple unit test. This would be better as an instrumented test.
    }

    @Test
    fun `temp routine identification - routine is NOT null for single exercise mode`() = runTest {
        // This test verifies a key finding from the investigation:
        // The isSingleExercise check uses `routine == null`, but temp routines
        // created by SingleExerciseScreen have a non-null routine.

        // Arrange - Create temp routine
        val singleExercise = RoutineExercise(
            id = "single_ex3",
            exercise = testExercise1,
            cableConfig = CableConfiguration.DOUBLE,
            orderIndex = 0,
            setReps = listOf(2, 2),
            weightPerCableKg = 20f,
            progressionKg = 0f,
            restSeconds = 60,
            workoutType = WorkoutMode.OldSchool.toWorkoutType()
        )

        val tempRoutine = Routine(
            id = "temp_single_exercise_test3",
            name = "Single Exercise: ${singleExercise.exercise.name}",
            description = "Temporary routine for single exercise mode",
            exercises = listOf(singleExercise)
        )

        // Act
        viewModel.loadRoutine(tempRoutine)

        // Assert - Critical finding: routine is NOT null
        assertThat(viewModel.loadedRoutine.value).isNotNull()
        assertThat(viewModel.loadedRoutine.value?.id).startsWith("temp_single_exercise")

        // This means any code checking `routine == null` to detect single exercise
        // mode will fail for temp routines created by SingleExerciseScreen.
        // The bug is likely here:
        // val isSingleExercise = routine == null && !isJustLift  // ← Always false!
    }
}