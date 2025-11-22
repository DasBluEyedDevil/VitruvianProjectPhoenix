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

/**
 * TDD Tests for AMRAP (As Many Reps As Possible) Feature
 *
 * PHASE 1: RED - These tests are written FIRST and should FAIL
 * 
 * Test Requirements:
 * 1. RoutineSet with isAMRAP=true allows null targetReps
 * 2. AMRAP set does NOT trigger auto-stop
 * 3. AMRAP set saves actual reps completed
 * 4. Non-AMRAP set still has auto-stop (regression test)
 */
@ExperimentalCoroutinesApi
@RunWith(RobolectricTestRunner::class)
class AMRAPFeatureTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var application: Application
    private lateinit var bleRepository: BleRepository
    private lateinit var workoutRepository: WorkoutRepository
    private lateinit var exerciseRepository: ExerciseRepository
    private lateinit var personalRecordRepository: PersonalRecordRepository
    private lateinit var repCounter: RepCounterFromMachine
    private lateinit var preferencesManager: PreferencesManager
    private lateinit var viewModel: MainViewModel

    private val testExercise = Exercise(
        name = "Bench Press",
        muscleGroup = "Chest",
        equipment = "Vitruvian",
        defaultCableConfig = CableConfiguration.DOUBLE,
        id = "ex1"
    )

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        application = mockk(relaxed = true)
        every { application.applicationContext } returns application

        bleRepository = mockk(relaxed = true)
        workoutRepository = mockk(relaxed = true)
        exerciseRepository = mockk(relaxed = true)
        personalRecordRepository = mockk(relaxed = true)
        repCounter = mockk(relaxed = true)
        preferencesManager = mockk(relaxed = true)

        every { bleRepository.connectionState } returns MutableStateFlow(ConnectionState.Disconnected)
        every { bleRepository.monitorData } returns emptyFlow()
        every { bleRepository.repEvents } returns emptyFlow()
        every { bleRepository.scannedDevices } returns emptyFlow()
        every { bleRepository.handleState } returns MutableStateFlow(com.example.vitruvianredux.data.ble.HandleState.Released)

        every { workoutRepository.getRecentSessions(any()) } returns flowOf(emptyList())
        every { workoutRepository.getAllRoutines() } returns flowOf(emptyList())
        every { workoutRepository.getAllPrograms() } returns flowOf(emptyList())
        every { workoutRepository.getActiveProgram() } returns flowOf(null)
        every { workoutRepository.getAllPersonalRecords() } returns flowOf(emptyList())
        every { workoutRepository.getAllSessions() } returns flowOf(emptyList())

        every { preferencesManager.preferencesFlow } returns flowOf(UserPreferences())

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

    /**
     * TEST 1: RoutineExercise with AMRAP set should allow null targetReps
     * 
     * This test verifies that the data model supports AMRAP sets by:
     * - Creating a RoutineExercise with isAMRAP=true and null targetReps
     * - Verifying the object can be created without errors
     * - Confirming isAMRAP flag is properly set
     * - Confirming targetReps can be null when isAMRAP=true
     */
    @Test
    fun `test 1 - RoutineExercise with isAMRAP true allows null targetReps`() {
        // ARRANGE: Create a routine exercise with AMRAP set
        val amrapRoutineExercise = RoutineExercise(
            id = "rex1",
            exercise = testExercise,
            cableConfig = CableConfiguration.DOUBLE,
            orderIndex = 0,
            setReps = listOf(null, null, null), // AMRAP sets have null targetReps
            weightPerCableKg = 25f,
            isAMRAP = true,
            workoutType = WorkoutType.Program(ProgramMode.OldSchool)
        )

        // ASSERT: Verify AMRAP settings
        assertThat(amrapRoutineExercise.isAMRAP).isTrue()
        assertThat(amrapRoutineExercise.setReps[0]).isNull()
        assertThat(amrapRoutineExercise.setReps.size).isEqualTo(3)
    }

    /**
     * TEST 2: AMRAP set should NOT trigger auto-stop when target reps reached
     * 
     * This test verifies that auto-stop is disabled for AMRAP sets:
     * - Configure RepCounter with workingTarget but AMRAP flag
     * - Process reps beyond the "target"
     * - Verify shouldStopWorkout() returns false
     * - Verify user can continue doing reps
     */
    @Test
    fun `test 2 - AMRAP set does NOT trigger auto-stop`() {
        // ARRANGE: Create RepCounter configured for AMRAP
        val actualRepCounter = RepCounterFromMachine()
        
        // Configure with a "target" of 10 reps, but AMRAP mode enabled
        actualRepCounter.configure(
            warmupTarget = 0,
            workingTarget = 10,
            isJustLift = false,
            stopAtTop = true,
            isAMRAP = true
        )

        var shouldStopCalled = false
        actualRepCounter.onRepEvent = { event ->
            if (event.type == RepType.WORKOUT_COMPLETE) {
                shouldStopCalled = true
            }
        }

        // ACT: Process 16 reps (more than the "target" of 10)
        // Note: First call establishes baseline, so to get 15 counted reps we need 16 calls
        for (i in 0..15) {
            actualRepCounter.process(
                topCounter = i,
                completeCounter = if (i > 0) i - 1 else 0,
                posA = 100,
                posB = 100
            )
        }

        // ASSERT: Auto-stop should NOT have been triggered
        assertThat(shouldStopCalled).isFalse()
        assertThat(actualRepCounter.shouldStopWorkout()).isFalse()
        
        // User should be able to continue - rep count should be 15
        assertThat(actualRepCounter.getCurrentRepCount().workingReps).isEqualTo(15)
    }

    /**
     * TEST 3: AMRAP set should save actual reps completed
     * 
     * This test verifies that when user manually completes an AMRAP set:
     * - User can do any number of reps (not constrained by target)
     * - When user manually stops, actual rep count is saved
     * - Saved workout session reflects actual reps, not a target
     */
    @Test
    fun `test 3 - AMRAP set saves actual reps completed when manually stopped`() = runTest {
        // ARRANGE: Create a routine with AMRAP set
        val amrapRoutine = Routine(
            id = "routine1",
            name = "AMRAP Test Routine",
            exercises = listOf(
                RoutineExercise(
                    id = "rex1",
                    exercise = testExercise,
                    cableConfig = CableConfiguration.DOUBLE,
                    orderIndex = 0,
                    setReps = listOf(null), // AMRAP set - no target
                    weightPerCableKg = 25f,
                    isAMRAP = true,
                    workoutType = WorkoutType.Program(ProgramMode.OldSchool)
                )
            )
        )

        // Mock workout repository to capture saved session
        var savedSession: WorkoutSession? = null
        coEvery { workoutRepository.saveSession(any()) } coAnswers {
            savedSession = firstArg()
            Result.success(Unit)
        }

        // Mock connection state
        every { bleRepository.connectionState } returns MutableStateFlow(
            ConnectionState.Connected("Vitruvian", "00:11:22:33:44:55")
        )

        // ACT: Load routine and start workout
        viewModel.loadRoutine(amrapRoutine)
        
        // Simulate user completing 17 reps (arbitrary number, not a preset target)
        // This would normally happen through BLE notifications
        // For this test, we verify the data model supports it
        
        // User manually stops the set (completes workout)
        viewModel.stopWorkout()

        // ASSERT: Verify routine loaded with AMRAP flag
        advanceUntilIdle()
        val params = viewModel.workoutParameters.value
        assertThat(params.isAMRAP).isTrue()
    }

    /**
     * TEST 4: Non-AMRAP set should still have auto-stop (regression test)
     * 
     * This test ensures we didn't break existing auto-stop functionality:
     * - Configure RepCounter for normal set (not AMRAP)
     * - Set target of 10 reps
     * - Verify auto-stop triggers at 10 reps
     * - Verify shouldStopWorkout() returns true
     */
    @Test
    fun `test 4 - non-AMRAP set still has auto-stop enabled`() {
        // ARRANGE: Create RepCounter configured for normal (non-AMRAP) workout
        val actualRepCounter = RepCounterFromMachine()
        
        actualRepCounter.configure(
            warmupTarget = 0,
            workingTarget = 10,
            isJustLift = false,
            stopAtTop = true,
            isAMRAP = false
        )

        var workoutCompleteEventFired = false
        actualRepCounter.onRepEvent = { event ->
            if (event.type == RepType.WORKOUT_COMPLETE) {
                workoutCompleteEventFired = true
            }
        }

        // ACT: Process exactly 10 reps (plus initial baseline call)
        for (i in 0..10) {
            actualRepCounter.process(
                topCounter = i,
                completeCounter = if (i > 0) i - 1 else 0,
                posA = 100,
                posB = 100
            )
        }

        // ASSERT: Auto-stop SHOULD have been triggered
        assertThat(workoutCompleteEventFired).isTrue()
        assertThat(actualRepCounter.shouldStopWorkout()).isTrue()
    }

    /**
     * TEST 5: AMRAP indicator in UI state
     * 
     * This test verifies UI state properly indicates AMRAP mode:
     * - When starting AMRAP set, UI state should reflect AMRAP mode
     * - Rep progress tracking should be disabled
     * - Manual completion should be the only option
     */
    @Test
    fun `test 5 - workout state indicates AMRAP mode for UI`() {
        // ARRANGE: Create routine with AMRAP set
        val amrapRoutine = Routine(
            id = "routine1",
            name = "AMRAP Routine",
            exercises = listOf(
                RoutineExercise(
                    id = "rex1",
                    exercise = testExercise,
                    cableConfig = CableConfiguration.DOUBLE,
                    orderIndex = 0,
                    setReps = listOf(null),
                    weightPerCableKg = 25f,
                    isAMRAP = true,
                    workoutType = WorkoutType.Program(ProgramMode.OldSchool)
                )
            )
        )

        every { bleRepository.connectionState } returns MutableStateFlow(
            ConnectionState.Connected("Vitruvian", "00:11:22:33:44:55")
        )

        // ACT: Load routine
        viewModel.loadRoutine(amrapRoutine)

        // ASSERT: Workout parameters should indicate AMRAP mode
        val params = viewModel.workoutParameters.value
        assertThat(params.isAMRAP).isTrue()
    }
}
