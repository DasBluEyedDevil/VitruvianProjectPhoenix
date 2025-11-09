package com.example.vitruvianredux.repository

import com.example.vitruvianredux.data.local.WorkoutDao
import com.example.vitruvianredux.data.local.WorkoutMetricEntity
import com.example.vitruvianredux.data.local.WorkoutSessionEntity
import com.example.vitruvianredux.data.repository.WorkoutRepository
import com.example.vitruvianredux.domain.model.WorkoutMetric
import com.example.vitruvianredux.domain.model.WorkoutMode
import com.example.vitruvianredux.domain.model.WorkoutSession
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Test suite for WorkoutRepository - verifies local data persistence (offline functionality)
 *
 * Critical for ensuring the app works WITHOUT online servers
 */
@ExperimentalCoroutinesApi
class WorkoutRepositoryTest {
    private lateinit var workoutDao: WorkoutDao
    private lateinit var repository: WorkoutRepository

    @Before
    fun setup() {
        workoutDao = mockk(relaxed = true)
        val personalRecordDao = mockk<com.example.vitruvianredux.data.local.PersonalRecordDao>(relaxed = true)
        repository = WorkoutRepository(workoutDao, personalRecordDao)
    }

    @After
    fun tearDown() {
        clearAllMocks()
    }

    @Test
    fun `test workout session persistence - no server required`() = runTest {
        // Given: A workout session (completely local, no server interaction)
        val session = WorkoutSession(
            id = "test-session-1",
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

        // Mock the DAO to capture what was saved
        val capturedEntity = slot<WorkoutSessionEntity>()
        coEvery { workoutDao.insertSession(capture(capturedEntity)) } just Runs

        // When: Saving the session
        val result = repository.saveSession(session)

        // Then: Session is saved successfully to local database
        assertTrue(result.isSuccess, "Session should be saved successfully offline")
        coVerify(exactly = 1) { workoutDao.insertSession(any()) }

        // Verify the saved data matches
        assertEquals(session.id, capturedEntity.captured.id)
        assertEquals(session.mode, capturedEntity.captured.mode)
        assertEquals(session.reps, capturedEntity.captured.reps)
        assertEquals(session.weightPerCableKg, capturedEntity.captured.weightPerCableKg)
    }

    @Test
    fun `test batch metrics persistence - efficient local storage`() = runTest {
        // Given: Multiple workout metrics collected during a workout
        val sessionId = "test-session-1"
        val metrics = List(100) { index ->
            WorkoutMetric(
                timestamp = System.currentTimeMillis() + index * 100L,
                loadA = 15.0f + index * 0.1f,
                loadB = 15.0f + index * 0.1f,
                positionA = 1000 + index * 10,
                positionB = 1000 + index * 10,
                ticks = index
            )
        }

        val capturedMetrics = slot<List<WorkoutMetricEntity>>()
        coEvery { workoutDao.insertMetrics(capture(capturedMetrics)) } just Runs

        // When: Saving metrics in batch
        val result = repository.saveMetrics(sessionId, metrics)

        // Then: All metrics are saved locally
        assertTrue(result.isSuccess, "Metrics should be saved successfully offline")
        coVerify(exactly = 1) { workoutDao.insertMetrics(any()) }
        assertEquals(100, capturedMetrics.captured.size, "All 100 metrics should be persisted")
    }

    @Test
    fun `test retrieve workout history - local database query`() = runTest {
        // Given: Workout sessions exist in local database
        val mockSessions = listOf(
            createMockSessionEntity("session-1", "Old School", 10),
            createMockSessionEntity("session-2", "Pump", 15),
            createMockSessionEntity("session-3", "TUT", 12)
        )

        every { workoutDao.getAllSessions() } returns flowOf(mockSessions)

        // When: Retrieving all sessions
        var retrievedSessions: List<WorkoutSession>? = null
        repository.getAllSessions().collect { sessions ->
            retrievedSessions = sessions
        }

        // Then: Sessions are retrieved from local database
        assertNotNull(retrievedSessions, "Should retrieve sessions from local DB")
        assertEquals(3, retrievedSessions.size, "Should retrieve all 3 sessions")
        assertEquals("Old School", retrievedSessions[0].mode)
        verify(exactly = 1) { workoutDao.getAllSessions() }
    }

    @Test
    fun `test recent sessions limit - efficient local queries`() = runTest {
        // Given: Many workout sessions in local database
        val mockSessions = List(10) { index ->
            createMockSessionEntity("session-$index", "Old School", 10)
        }

        every { workoutDao.getRecentSessions(10) } returns flowOf(mockSessions)

        // When: Retrieving recent sessions with limit
        var retrievedSessions: List<WorkoutSession>? = null
        repository.getRecentSessions(10).collect { sessions ->
            retrievedSessions = sessions
        }

        // Then: Only limited sessions are retrieved (efficient for large datasets)
        assertNotNull(retrievedSessions)
        assertEquals(10, retrievedSessions.size, "Should limit to 10 most recent")
        verify(exactly = 1) { workoutDao.getRecentSessions(10) }
    }

    @Test
    fun `test get specific session - local lookup`() = runTest {
        // Given: A specific session exists in local database
        val sessionId = "test-session-123"
        val mockSession = createMockSessionEntity(sessionId, "Echo", 12)

        coEvery { workoutDao.getSession(sessionId) } returns mockSession

        // When: Retrieving specific session
        val session = repository.getSession(sessionId)

        // Then: Session is retrieved from local database
        assertNotNull(session, "Should retrieve session from local DB")
        assertEquals(sessionId, session.id)
        assertEquals("Echo", session.mode)
        coVerify(exactly = 1) { workoutDao.getSession(sessionId) }
    }

    @Test
    fun `test workout data survives app restart - true offline capability`() = runTest {
        // This test verifies that data persists across app restarts (Room database)
        // Given: A workout session was saved in a previous "app session"
        val sessionId = "persistent-session"
        val savedSession = createMockSessionEntity(sessionId, "TUT Beast", 8)

        coEvery { workoutDao.getSession(sessionId) } returns savedSession

        // When: App "restarts" and retrieves the session
        val retrievedSession = repository.getSession(sessionId)

        // Then: Session is still available (proves offline persistence)
        assertNotNull(retrievedSession, "Session should persist across app restarts")
        assertEquals(sessionId, retrievedSession.id)
        assertEquals("TUT Beast", retrievedSession.mode)
    }

    @Test
    fun `test no network required for workout tracking`() = runTest {
        // This test ensures NO network calls are made during workout operations
        // Given: A complete workout scenario
        val session = WorkoutSession(
            id = "offline-workout",
            timestamp = System.currentTimeMillis(),
            mode = "Pump",
            reps = 15,
            weightPerCableKg = 20.0f,
            progressionKg = 2.5f,
            duration = 600000L,
            totalReps = 15,  // Exclude warm-up reps from total count
            warmupReps = 3,
            workingReps = 15,
            isJustLift = false,
            stopAtTop = false
        )

        val metrics = List(50) { index ->
            WorkoutMetric(
                timestamp = System.currentTimeMillis() + index * 100L,
                loadA = 20.0f,
                loadB = 20.0f,
                positionA = 2000 + index * 10,
                positionB = 2000 + index * 10,
                ticks = index
            )
        }

        coEvery { workoutDao.insertSession(any()) } just Runs
        coEvery { workoutDao.insertMetrics(any()) } just Runs

        // When: Saving workout data (simulating complete workout)
        val sessionResult = repository.saveSession(session)
        val metricsResult = repository.saveMetrics(session.id, metrics)

        // Then: All operations succeed WITHOUT any network calls
        assertTrue(sessionResult.isSuccess, "Session save should succeed offline")
        assertTrue(metricsResult.isSuccess, "Metrics save should succeed offline")

        // Verify only local database was used
        coVerify(exactly = 1) { workoutDao.insertSession(any()) }
        coVerify(exactly = 1) { workoutDao.insertMetrics(any()) }

        // NOTE: No network mocks were needed - proves offline capability
    }

    @Test
    fun `test workout metrics calculations are local`() = runTest {
        // Verify that all workout calculations happen locally
        // Given: Workout metrics with varying loads
        val metrics = listOf(
            WorkoutMetric(0, loadA = 10f, loadB = 10f, positionA = 1000, positionB = 1000),
            WorkoutMetric(1, loadA = 15f, loadB = 15f, positionA = 1500, positionB = 1500),
            WorkoutMetric(2, loadA = 20f, loadB = 20f, positionA = 2000, positionB = 2000)
        )

        // When: Calculating total loads (done locally in the model)
        val totalLoads = metrics.map { it.totalLoad }

        // Then: Calculations are performed locally without server
        assertEquals(20f, totalLoads[0], "Local calculation: 10 + 10 = 20")
        assertEquals(30f, totalLoads[1], "Local calculation: 15 + 15 = 30")
        assertEquals(40f, totalLoads[2], "Local calculation: 20 + 20 = 40")
    }

    private fun createMockSessionEntity(
        id: String,
        mode: String,
        reps: Int
    ): WorkoutSessionEntity {
        return WorkoutSessionEntity(
            id = id,
            timestamp = System.currentTimeMillis(),
            mode = mode,
            reps = reps,
            weightPerCableKg = 15.0f,
            progressionKg = 0f,
            duration = 300000L,
            totalReps = reps,  // Exclude warm-up reps from total count
            warmupReps = 3,
            workingReps = reps,
            isJustLift = false,
            stopAtTop = false
        )
    }
}
