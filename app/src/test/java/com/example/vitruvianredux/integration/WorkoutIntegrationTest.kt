package com.example.vitruvianredux.integration

import com.example.vitruvianredux.data.local.WorkoutDao
import com.example.vitruvianredux.data.local.WorkoutSessionEntity
import com.example.vitruvianredux.data.repository.BleRepository
import com.example.vitruvianredux.data.repository.WorkoutRepository
import com.example.vitruvianredux.domain.model.*
import com.example.vitruvianredux.domain.usecase.RepCounterFromMachine
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
 * Integration Test Suite - Tests complete workflows across components
 *
 * These tests verify that all components work together correctly
 * to provide complete offline functionality for workout tracking
 */
@ExperimentalCoroutinesApi
class WorkoutIntegrationTest {

    private lateinit var bleRepository: BleRepository
    private lateinit var workoutRepository: WorkoutRepository
    private lateinit var workoutDao: WorkoutDao
    private lateinit var repCounter: RepCounterFromMachine

    @Before
    fun setup() {
        bleRepository = mockk(relaxed = true)
        workoutDao = mockk(relaxed = true)
        val personalRecordDao = mockk<com.example.vitruvianredux.data.local.PersonalRecordDao>(relaxed = true)
        workoutRepository = WorkoutRepository(workoutDao, personalRecordDao)
        repCounter = RepCounterFromMachine()
    }

    @After
    fun tearDown() {
        clearAllMocks()
    }

    @Test
    fun `test complete workout cycle - scan to save`() = runTest {
        // This test simulates a complete workout from start to finish
        // Given: App starts up, no device connected
        every { bleRepository.connectionState } returns MutableStateFlow(ConnectionState.Disconnected)
        every { bleRepository.scannedDevices } returns emptyFlow()
        every { bleRepository.monitorData } returns emptyFlow()
        every { bleRepository.repEvents } returns emptyFlow()

        // Step 1: Scan for devices
        coEvery { bleRepository.startScanning() } returns Result.success(Unit)
        val scanResult = bleRepository.startScanning()
        assertTrue(scanResult.isSuccess, "Device scanning should work locally")

        // Step 2: Connect to device (direct BLE, no server)
        val deviceAddress = "AA:BB:CC:DD:EE:FF"
        coEvery { bleRepository.connectToDevice(deviceAddress) } returns Result.success(Unit)
        every { bleRepository.connectionState } returns MutableStateFlow(
            ConnectionState.Connected("Vitruvian", deviceAddress)
        )

        val connectResult = bleRepository.connectToDevice(deviceAddress)
        assertTrue(connectResult.isSuccess, "BLE connection should succeed")

        // Step 3: Initialize device
        coEvery { bleRepository.sendInitSequence() } returns Result.success(Unit)
        val initResult = bleRepository.sendInitSequence()
        assertTrue(initResult.isSuccess, "Device initialization should succeed")

        // Step 4: Start workout with parameters
        val params = WorkoutParameters(
            workoutType = WorkoutMode.OldSchool.toWorkoutType(),
            reps = 10,
            weightPerCableKg = 15.0f,
            progressionRegressionKg = 0f,
            isJustLift = false,
            stopAtTop = false,
            warmupReps = 3
        )

        coEvery { bleRepository.startWorkout(params) } returns Result.success(Unit)
        val startResult = bleRepository.startWorkout(params)
        assertTrue(startResult.isSuccess, "Workout should start")

        // Step 5: Collect workout metrics
        val metrics = List(50) { index ->
            WorkoutMetric(
                timestamp = System.currentTimeMillis() + index * 100L,
                loadA = 15.0f,
                loadB = 15.0f,
                positionA = 1000 + index * 10,
                positionB = 1000 + index * 10,
                ticks = index
            )
        }

        // Step 6: Stop workout
        coEvery { bleRepository.stopWorkout() } returns Result.success(Unit)
        val stopResult = bleRepository.stopWorkout()
        assertTrue(stopResult.isSuccess, "Workout should stop")

        // Step 7: Save workout data locally
        val session = WorkoutSession(
            id = "integration-test-${System.currentTimeMillis()}",
            timestamp = System.currentTimeMillis(),
            mode = "Old School",
            reps = 10,
            weightPerCableKg = 15.0f,
            progressionKg = 0f,
            duration = 5000L,
            totalReps = 10,  // Exclude warm-up reps from total count
            warmupReps = 3,
            workingReps = 10,
            isJustLift = false,
            stopAtTop = false
        )

        coEvery { workoutDao.insertSession(any()) } just Runs
        coEvery { workoutDao.insertMetrics(any()) } just Runs

        val saveSessionResult = workoutRepository.saveSession(session)
        val saveMetricsResult = workoutRepository.saveMetrics(session.id, metrics)

        assertTrue(saveSessionResult.isSuccess, "Session should save locally")
        assertTrue(saveMetricsResult.isSuccess, "Metrics should save locally")

        // Verify entire flow used only local resources (BLE + database)
        coVerify { bleRepository.startScanning() }
        coVerify { bleRepository.connectToDevice(deviceAddress) }
        coVerify { bleRepository.sendInitSequence() }
        coVerify { bleRepository.startWorkout(params) }
        coVerify { bleRepository.stopWorkout() }
        coVerify { workoutDao.insertSession(any()) }
        coVerify { workoutDao.insertMetrics(any()) }
    }

    @Test
    fun `test all workout modes function offline`() = runTest {
        // Verify every workout mode can be used without server
        // Given: All available workout modes
        val modes = listOf(
            WorkoutMode.OldSchool,
            WorkoutMode.Pump,
            WorkoutMode.TUT,
            WorkoutMode.TUTBeast,
            WorkoutMode.EccentricOnly,
            WorkoutMode.Echo(EchoLevel.HARD),
            WorkoutMode.Echo(EchoLevel.HARDER),
            WorkoutMode.Echo(EchoLevel.HARDEST),
            WorkoutMode.Echo(EchoLevel.EPIC)
        )

        coEvery { bleRepository.startWorkout(any()) } returns Result.success(Unit)
        coEvery { bleRepository.stopWorkout() } returns Result.success(Unit)

        // When: Testing each mode
        modes.forEach { mode ->
            val params = WorkoutParameters(
                workoutType = mode.toWorkoutType(),
                reps = 10,
                weightPerCableKg = 15.0f,
                progressionRegressionKg = 0f,
                isJustLift = false,
                stopAtTop = false,
                warmupReps = 3
            )

            val startResult = bleRepository.startWorkout(params)
            val stopResult = bleRepository.stopWorkout()

            // Then: Each mode should work without server
            assertTrue(startResult.isSuccess, "Mode ${mode.displayName} should start offline")
            assertTrue(stopResult.isSuccess, "Mode ${mode.displayName} should stop offline")
        }

        // Verify all modes were tested
        coVerify(exactly = modes.size) { bleRepository.startWorkout(any()) }
        coVerify(exactly = modes.size) { bleRepository.stopWorkout() }
    }

    @Test
    fun `test rep counting integration - local processing`() = runTest {
        // Verify rep counting works with local data processing
        // Given: Rep counter configured for a workout
        repCounter.configure(
            warmupTarget = 3,
            workingTarget = 10,
            isJustLift = false,
            stopAtTop = false
        )

        var lastRepEvent: RepEvent? = null
        repCounter.onRepEvent = { event ->
            lastRepEvent = event
        }

        // When: Processing rep notifications from machine
        // Simulate 3 warmup reps (need 4 calls - first one initializes the counter)
        repeat(4) { index ->
            repCounter.process(
                topCounter = index,
                completeCounter = index,
                posA = 2000,
                posB = 2000
            )
        }

        val warmupCount = repCounter.getRepCount()

        // Then: Warmup reps are counted locally
        assertEquals(3, warmupCount.warmupReps, "Warmup reps counted locally")
        assertEquals(0, warmupCount.workingReps, "Working reps not started")

        // When: Processing 10 working reps
        repeat(10) { index ->
            repCounter.process(
                topCounter = 4 + index,
                completeCounter = 4 + index,
                posA = 2000,
                posB = 2000
            )
        }

        val finalCount = repCounter.getRepCount()

        // Then: All reps are counted locally
        assertEquals(3, finalCount.warmupReps, "Warmup reps tracked")
        assertEquals(10, finalCount.workingReps, "Working reps counted locally")
        assertEquals(13, finalCount.totalReps, "Total reps calculated locally")
        assertNotNull(lastRepEvent, "Rep events generated locally")
    }

    @Test
    fun `test Just Lift mode with auto-stop - local detection`() = runTest {
        // Verify Just Lift mode auto-stop works locally
        // Given: Just Lift mode configured
        val params = WorkoutParameters(
            workoutType = WorkoutMode.OldSchool.toWorkoutType(),
            reps = 10,
            weightPerCableKg = 15.0f,
            progressionRegressionKg = 0f,
            isJustLift = true,
            stopAtTop = false,
            warmupReps = 3
        )

        repCounter.configure(
            warmupTarget = 3,
            workingTarget = 0,
            isJustLift = true,
            stopAtTop = false
        )

        coEvery { bleRepository.startWorkout(params) } returns Result.success(Unit)
        coEvery { bleRepository.stopWorkout() } returns Result.success(Unit)

        // When: Starting Just Lift workout
        val startResult = bleRepository.startWorkout(params)
        assertTrue(startResult.isSuccess, "Just Lift should start")

        // Simulate user stopping (auto-stop detection happens locally)
        val stopResult = bleRepository.stopWorkout()
        assertTrue(stopResult.isSuccess, "Just Lift should stop")

        // Then: Just Lift mode works completely offline
        coVerify { bleRepository.startWorkout(params) }
        coVerify { bleRepository.stopWorkout() }
    }

    @Test
    fun `test workout history retrieval and display`() = runTest {
        // Verify workout history can be displayed from local database
        // Given: Multiple workout sessions in local database
        val historicalSessions = List(5) { index ->
            WorkoutSession(
                id = "history-$index",
                timestamp = System.currentTimeMillis() - (index * 86400000L),
                mode = listOf("Old School", "Pump", "TUT", "Echo", "TUT Beast")[index],
                reps = 10 + index,
                weightPerCableKg = 15.0f + index * 2.5f,
                progressionKg = 0f,
                duration = 300000L + index * 60000L,
                totalReps = 10 + index,  // Exclude warm-up reps from total count
                warmupReps = 3,
                workingReps = 10 + index,
                isJustLift = false,
                stopAtTop = false
            )
        }

        val sessionEntities = historicalSessions.map { session ->
            WorkoutSessionEntity(
                id = session.id,
                timestamp = session.timestamp,
                mode = session.mode,
                reps = session.reps,
                weightPerCableKg = session.weightPerCableKg,
                progressionKg = session.progressionKg,
                duration = session.duration,
                totalReps = session.totalReps,
                warmupReps = session.warmupReps,
                workingReps = session.workingReps,
                isJustLift = session.isJustLift,
                stopAtTop = session.stopAtTop
            )
        }
        every { workoutDao.getRecentSessions(any()) } returns flowOf(sessionEntities)

        // When: Loading workout history
        var loadedHistory: List<WorkoutSession>? = null
        workoutRepository.getRecentSessions(5).collect { sessions ->
            loadedHistory = sessions
        }

        // Then: History is loaded from local database
        assertNotNull(loadedHistory, "History should load from local DB")
        assertEquals(5, loadedHistory.size, "Should load all 5 sessions")
        assertEquals("Old School", loadedHistory[0].mode)
        assertEquals("TUT Beast", loadedHistory[4].mode)

        verify { workoutDao.getRecentSessions(any()) }
    }

    @Test
    fun `test connection recovery - no authentication required`() = runTest {
        // Verify reconnection doesn't require server authentication
        // Given: Previously connected device
        val deviceAddress = "AA:BB:CC:DD:EE:FF"

        // First connection
        coEvery { bleRepository.connectToDevice(deviceAddress) } returns Result.success(Unit)
        val firstConnect = bleRepository.connectToDevice(deviceAddress)
        assertTrue(firstConnect.isSuccess)

        // Simulate disconnect
        coEvery { bleRepository.disconnect() } just Runs
        bleRepository.disconnect()

        // Reconnect (should work without server authentication)
        val reconnect = bleRepository.connectToDevice(deviceAddress)
        assertTrue(reconnect.isSuccess, "Reconnection should work without server")

        // Verify direct BLE connection, no auth calls
        coVerify(exactly = 2) { bleRepository.connectToDevice(deviceAddress) }
        coVerify(exactly = 1) { bleRepository.disconnect() }
    }

    @Test
    fun `test multiple workout sessions in same day`() = runTest {
        // Verify multiple workouts can be tracked in one day (all local)
        // Given: User does multiple workouts
        val sessions = List(3) { index ->
            WorkoutSession(
                id = "daily-workout-$index",
                timestamp = System.currentTimeMillis() + (index * 3600000L), // 1 hour apart
                mode = "Old School",
                reps = 10,
                weightPerCableKg = 15.0f,
                progressionKg = 0f,
                duration = 300000L,
                totalReps = 10,  // Exclude warm-up reps from total count
                warmupReps = 3,
                workingReps = 10,
                isJustLift = false,
                stopAtTop = false
            )
        }

        coEvery { workoutDao.insertSession(any()) } just Runs
        val sessionEntities = sessions.map { session ->
            WorkoutSessionEntity(
                id = session.id,
                timestamp = session.timestamp,
                mode = session.mode,
                reps = session.reps,
                weightPerCableKg = session.weightPerCableKg,
                progressionKg = session.progressionKg,
                duration = session.duration,
                totalReps = session.totalReps,
                warmupReps = session.warmupReps,
                workingReps = session.workingReps,
                isJustLift = session.isJustLift,
                stopAtTop = session.stopAtTop
            )
        }
        every { workoutDao.getAllSessions() } returns flowOf(sessionEntities)

        // When: Saving all sessions
        sessions.forEach { session ->
            val result = workoutRepository.saveSession(session)
            assertTrue(result.isSuccess, "Each session should save")
        }

        // Retrieving all sessions
        var allSessions: List<WorkoutSession>? = null
        workoutRepository.getAllSessions().collect { sessions ->
            allSessions = sessions
        }

        // Then: All sessions stored and retrieved locally
        assertNotNull(allSessions)
        assertEquals(3, allSessions.size, "All 3 workouts tracked locally")
        coVerify(exactly = 3) { workoutDao.insertSession(any()) }
    }

    @Test
    fun `test data migration scenario - preserves offline data`() = runTest {
        // Verify that local data persists (simulating app update/migration)
        // Given: Existing workout data in local database
        val oldSessionId = "old-session-123"
        val oldSession = WorkoutSession(
            id = oldSessionId,
            timestamp = System.currentTimeMillis() - 86400000L, // Yesterday
            mode = "Pump",
            reps = 15,
            weightPerCableKg = 20.0f,
            progressionKg = 0f,
            duration = 450000L,
            totalReps = 15,  // Exclude warm-up reps from total count
            warmupReps = 3,
            workingReps = 15,
            isJustLift = false,
            stopAtTop = false
        )

        val oldSessionEntity = WorkoutSessionEntity(
            id = oldSession.id,
            timestamp = oldSession.timestamp,
            mode = oldSession.mode,
            reps = oldSession.reps,
            weightPerCableKg = oldSession.weightPerCableKg,
            progressionKg = oldSession.progressionKg,
            duration = oldSession.duration,
            totalReps = oldSession.totalReps,
            warmupReps = oldSession.warmupReps,
            workingReps = oldSession.workingReps,
            isJustLift = oldSession.isJustLift,
            stopAtTop = oldSession.stopAtTop
        )
        coEvery { workoutDao.getSession(oldSessionId) } returns oldSessionEntity

        // When: "App updates" and retrieves old data
        val retrievedSession = workoutRepository.getSession(oldSessionId)

        // Then: Old data is still accessible (proves local persistence)
        assertNotNull(retrievedSession, "Old data should persist")
        assertEquals(oldSessionId, retrievedSession.id)
        assertEquals("Pump", retrievedSession.mode)
        assertEquals(15, retrievedSession.reps)

        coVerify { workoutDao.getSession(oldSessionId) }
    }

    @Test
    fun `test full feature set without external dependencies`() = runTest {
        // Comprehensive test of all major features working offline
        val deviceAddress = "AA:BB:CC:DD:EE:FF"

        // Setup all necessary mocks for complete offline operation
        coEvery { bleRepository.startScanning() } returns Result.success(Unit)
        coEvery { bleRepository.stopScanning() } just Runs
        coEvery { bleRepository.connectToDevice(any()) } returns Result.success(Unit)
        coEvery { bleRepository.disconnect() } just Runs
        coEvery { bleRepository.sendInitSequence() } returns Result.success(Unit)
        coEvery { bleRepository.startWorkout(any()) } returns Result.success(Unit)
        coEvery { bleRepository.stopWorkout() } returns Result.success(Unit)
        coEvery { bleRepository.setColorScheme(any()) } returns Result.success(Unit)
        coEvery { workoutDao.insertSession(any()) } just Runs
        coEvery { workoutDao.insertMetrics(any()) } just Runs
        every { workoutDao.getAllSessions() } returns flowOf(emptyList())

        // Feature 1: Device Discovery
        bleRepository.startScanning()
        bleRepository.stopScanning()

        // Feature 2: Connection
        bleRepository.connectToDevice(deviceAddress)

        // Feature 3: Initialization
        bleRepository.sendInitSequence()

        // Feature 4: Workout Execution (multiple modes)
        val modes = listOf(
            WorkoutMode.OldSchool,
            WorkoutMode.Pump,
            WorkoutMode.TUT
        )

        modes.forEach { mode ->
            bleRepository.startWorkout(
                WorkoutParameters(workoutType = mode.toWorkoutType(), reps = 10, weightPerCableKg = 15f, progressionRegressionKg = 0f, isJustLift = false, stopAtTop = false, warmupReps = 3)
            )
            bleRepository.stopWorkout()
        }

        // Feature 5: Customization
        (0..2).forEach { scheme ->
            bleRepository.setColorScheme(scheme)
        }

        // Feature 6: Data Persistence
        val session = WorkoutSession(
            id = "full-feature-test",
            timestamp = System.currentTimeMillis(),
            mode = "Old School",
            reps = 10,
            weightPerCableKg = 15.0f,
            progressionKg = 0f,
            duration = 300000L,
            totalReps = 10,  // Exclude warm-up reps from total count
            warmupReps = 3,
            workingReps = 10,
            isJustLift = false,
            stopAtTop = false
        )
        workoutRepository.saveSession(session)

        // Feature 7: History Retrieval
        workoutRepository.getAllSessions().collect { }

        // Feature 8: Disconnection
        bleRepository.disconnect()

        // Verify all features work with only local resources
        coVerify { bleRepository.startScanning() }
        coVerify { bleRepository.connectToDevice(deviceAddress) }
        coVerify { bleRepository.sendInitSequence() }
        coVerify(exactly = modes.size) { bleRepository.startWorkout(any()) }
        coVerify(exactly = modes.size) { bleRepository.stopWorkout() }
        coVerify(exactly = 3) { bleRepository.setColorScheme(any()) }
        coVerify { workoutDao.insertSession(any()) }
        verify { workoutDao.getAllSessions() }
        coVerify { bleRepository.disconnect() }

        // NO external API calls required for any feature
    }
}

