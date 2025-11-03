package com.example.vitruvianredux.presentation

import com.example.vitruvianredux.data.ble.RepNotification
import com.example.vitruvianredux.domain.model.RepCount
import com.example.vitruvianredux.domain.model.WorkoutMode
import com.example.vitruvianredux.domain.model.WorkoutParameters
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * Comprehensive tests for rep tracking based on machine's rep notification counters.
 * These tests simulate the actual behavior of the Vitruvian machine.
 */
class RepTrackingTest {

    private lateinit var repTracker: RepCounterTracker
    
    @Before
    fun setup() {
        repTracker = RepCounterTracker()
    }
    
    @Test
    fun `test Old School mode - 3 warmup + 5 working reps`() {
        val params = WorkoutParameters(
            workoutType = WorkoutMode.OldSchool.toWorkoutType(),
            reps = 5,
            weightPerCableKg = 10f,
            progressionRegressionKg = 0f,
            warmupReps = 3
        )
        
        repTracker.startWorkout(params)
        
        // First notification initializes the counter (skipped)
        repTracker.processRepNotification(simulateRepComplete())
        assertEquals(0, repTracker.getRepCount().warmupReps)
        
        // Simulate 3 warmup reps (need 4 notifications total including initialization)
        repeat(3) { repTracker.processRepNotification(simulateRepComplete()) }
        assertEquals(3, repTracker.getRepCount().warmupReps)
        assertEquals(0, repTracker.getRepCount().workingReps)
        assertEquals(true, repTracker.getRepCount().isWarmupComplete)
        
        // Simulate 5 working reps
        repeat(5) { repTracker.processRepNotification(simulateRepComplete()) }
        assertEquals(3, repTracker.getRepCount().warmupReps)
        assertEquals(5, repTracker.getRepCount().workingReps)
        assertEquals(true, repTracker.isWorkoutComplete())
    }
    
    @Test
    fun `test Pump mode - 3 warmup + 10 working reps`() {
        val params = WorkoutParameters(
            workoutType = WorkoutMode.Pump.toWorkoutType(),
            reps = 10,
            weightPerCableKg = 5f,
            progressionRegressionKg = 0f,
            warmupReps = 3
        )
        
        repTracker.startWorkout(params)
        
        // First notification initializes counter
        repTracker.processRepNotification(simulateRepComplete())
        
        // Warmup (3 more notifications)
        repeat(3) { repTracker.processRepNotification(simulateRepComplete()) }
        assertEquals(3, repTracker.getRepCount().warmupReps)
        
        // Working reps
        repeat(10) { repTracker.processRepNotification(simulateRepComplete()) }
        assertEquals(10, repTracker.getRepCount().workingReps)
        assertEquals(true, repTracker.isWorkoutComplete())
    }
    
    @Test
    fun `test TUT mode - slower reps tracked correctly`() {
        val params = WorkoutParameters(
            workoutType = WorkoutMode.TUT.toWorkoutType(),
            reps = 6,
            weightPerCableKg = 8f,
            progressionRegressionKg = 0f,
            warmupReps = 3
        )
        
        repTracker.startWorkout(params)
        
        // First notification initializes counter
        repTracker.processRepNotification(simulateRepComplete())
        
        // Warmup
        repeat(3) { repTracker.processRepNotification(simulateRepComplete()) }
        
        // Working reps
        repeat(6) { repTracker.processRepNotification(simulateRepComplete()) }
        assertEquals(6, repTracker.getRepCount().workingReps)
        assertEquals(true, repTracker.isWorkoutComplete())
    }
    
    @Test
    fun `test TUT Beast mode - ultra-slow reps tracked correctly`() {
        val params = WorkoutParameters(
            workoutType = WorkoutMode.TUTBeast.toWorkoutType(),
            reps = 4,
            weightPerCableKg = 10f,
            progressionRegressionKg = 0f,
            warmupReps = 3
        )
        
        repTracker.startWorkout(params)
        
        // First notification initializes counter
        repTracker.processRepNotification(simulateRepComplete())
        
        // Warmup
        repeat(3) { repTracker.processRepNotification(simulateRepComplete()) }
        
        // Working reps (slower but still counted the same way)
        repeat(4) { repTracker.processRepNotification(simulateRepComplete()) }
        assertEquals(4, repTracker.getRepCount().workingReps)
        assertEquals(true, repTracker.isWorkoutComplete())
    }
    
    @Test
    fun `test Eccentric Only mode - 3 warmup + 8 working reps`() {
        val params = WorkoutParameters(
            workoutType = WorkoutMode.EccentricOnly.toWorkoutType(),
            reps = 8,
            weightPerCableKg = 12f,
            progressionRegressionKg = 0f,
            warmupReps = 3
        )
        
        repTracker.startWorkout(params)
        
        // First notification initializes counter
        repTracker.processRepNotification(simulateRepComplete())
        
        // Warmup
        repeat(3) { repTracker.processRepNotification(simulateRepComplete()) }
        assertEquals(3, repTracker.getRepCount().warmupReps)
        
        // Working reps
        repeat(8) { repTracker.processRepNotification(simulateRepComplete()) }
        assertEquals(8, repTracker.getRepCount().workingReps)
        assertEquals(true, repTracker.isWorkoutComplete())
    }
    
    @Test
    fun `test Just Lift mode - unlimited reps`() {
        val params = WorkoutParameters(
            workoutType = WorkoutMode.OldSchool.toWorkoutType(),
            reps = 0,
            weightPerCableKg = 10f,
            progressionRegressionKg = 0f,
            isJustLift = true,
            warmupReps = 3
        )
        
        repTracker.startWorkout(params)
        
        // First notification initializes counter
        repTracker.processRepNotification(simulateRepComplete())
        
        // Warmup
        repeat(3) { repTracker.processRepNotification(simulateRepComplete()) }
        assertEquals(3, repTracker.getRepCount().warmupReps)
        
        // Working reps - can do any number in Just Lift mode
        repeat(15) { repTracker.processRepNotification(simulateRepComplete()) }
        assertEquals(15, repTracker.getRepCount().workingReps)
        // Just Lift never completes automatically
        assertEquals(false, repTracker.isWorkoutComplete())
    }
    
    @Test
    fun `test counter wraparound at u16 max`() {
        val params = WorkoutParameters(
            workoutType = WorkoutMode.OldSchool.toWorkoutType(),
            reps = 5,
            weightPerCableKg = 10f,
            progressionRegressionKg = 0f,
            warmupReps = 3
        )
        
        repTracker.startWorkout(params)
        
        // Simulate counter near wraparound
        var counter = 0xFFFD
        
        // First notification initializes counter
        repTracker.processRepNotification(RepNotification(
            topCounter = counter,
            completeCounter = counter,
            rawData = byteArrayOf(),
            timestamp = System.currentTimeMillis()
        ))
        
        // 3 warmup reps crossing wraparound
        repeat(3) {
            counter = (counter + 1) and 0xFFFF
            repTracker.processRepNotification(RepNotification(
                topCounter = counter,
                completeCounter = counter,
                rawData = byteArrayOf(),
                timestamp = System.currentTimeMillis()
            ))
        }
        assertEquals(3, repTracker.getRepCount().warmupReps)
        
        // 5 working reps after wraparound
        repeat(5) {
            counter = (counter + 1) and 0xFFFF
            repTracker.processRepNotification(RepNotification(
                topCounter = counter,
                completeCounter = counter,
                rawData = byteArrayOf(),
                timestamp = System.currentTimeMillis()
            ))
        }
        assertEquals(5, repTracker.getRepCount().workingReps)
        assertEquals(true, repTracker.isWorkoutComplete())
    }
    
    @Test
    fun `test stop at top - completes on second-to-last rep`() {
        val params = WorkoutParameters(
            workoutType = WorkoutMode.OldSchool.toWorkoutType(),
            reps = 5,
            weightPerCableKg = 10f,
            progressionRegressionKg = 0f,
            stopAtTop = true,
            warmupReps = 3
        )
        
        repTracker.startWorkout(params)
        
        // First notification initializes counter
        repTracker.processRepNotification(simulateRepComplete())
        
        // Warmup
        repeat(3) { repTracker.processRepNotification(simulateRepComplete()) }
        
        // Working reps - should complete at top of 5th rep (4th complete)
        repeat(4) { repTracker.processRepNotification(simulateRepComplete()) }
        assertEquals(4, repTracker.getRepCount().workingReps)
        
        // Simulate reaching top of 5th rep
        repTracker.processTopReached()
        assertEquals(true, repTracker.shouldStopAtTop())
    }
    
    @Test
    fun `test progression with warmup calibration`() {
        val params = WorkoutParameters(
            workoutType = WorkoutMode.OldSchool.toWorkoutType(),
            reps = 5,
            weightPerCableKg = 10f,
            progressionRegressionKg = 2f, // 2kg increase per rep
            warmupReps = 3
        )
        
        repTracker.startWorkout(params)
        
        // First notification initializes counter
        repTracker.processRepNotification(simulateRepComplete())
        
        // Warmup at base weight (10kg)
        repeat(3) { 
            repTracker.processRepNotification(simulateRepComplete())
            assertEquals(10f, repTracker.getCurrentWeight(), 0.01f)
        }
        
        // Working reps with progression
        for (repNum in 1..5) {
            val expectedWeight = 10f + ((repNum - 1) * 2f)
            assertEquals(expectedWeight, repTracker.getCurrentWeight(), 0.01f)
            repTracker.processRepNotification(simulateRepComplete())
        }
        
        assertEquals(5, repTracker.getRepCount().workingReps)
    }
    
    private var simulatedCounter = 0
    
    private fun simulateRepComplete(): RepNotification {
        simulatedCounter++
        return RepNotification(
            topCounter = simulatedCounter,
            completeCounter = simulatedCounter,
            rawData = byteArrayOf(),
            timestamp = System.currentTimeMillis()
        )
    }
}

/**
 * Simplified rep counter tracker for testing.
 * Mimics the logic in MainViewModel.handleRepNotification()
 */
class RepCounterTracker {
    private var lastTopCounter: Int? = null
    private var lastRepCounter: Int? = null
    private var repCount = RepCount()
    private var params: WorkoutParameters? = null
    private var currentRepWeight = 0f
    
    fun startWorkout(parameters: WorkoutParameters) {
        params = parameters
        lastTopCounter = null
        lastRepCounter = null
        repCount = RepCount()
        currentRepWeight = parameters.weightPerCableKg
    }
    
    fun processRepNotification(notification: RepNotification) {
        val topCounter = notification.topCounter
        val completeCounter = notification.completeCounter
        
        // Track top of range
        if (lastTopCounter == null) {
            lastTopCounter = topCounter
        } else {
            val topDelta = calculateDelta(lastTopCounter!!, topCounter)
            if (topDelta > 0) {
                lastTopCounter = topCounter
            }
        }
        
        // Track rep complete
        if (lastRepCounter == null) {
            lastRepCounter = completeCounter
            return
        }
        
        val delta = calculateDelta(lastRepCounter!!, completeCounter)
        if (delta > 0) {
            lastRepCounter = completeCounter
            
            val currentWarmup = repCount.warmupReps
            val currentWorking = repCount.workingReps
            val totalReps = currentWarmup + currentWorking + 1
            
            if (totalReps <= (params?.warmupReps ?: 3)) {
                // Warmup rep
                val newWarmup = currentWarmup + 1
                repCount = repCount.copy(
                    warmupReps = newWarmup,
                    isWarmupComplete = newWarmup >= (params?.warmupReps ?: 3)
                )
            } else {
                // Working rep
                val newWorking = currentWorking + 1
                repCount = repCount.copy(workingReps = newWorking)
                
                // Update weight for next rep with progression
                params?.let { p ->
                    currentRepWeight = p.weightPerCableKg + (newWorking * p.progressionRegressionKg)
                }
            }
        }
    }
    
    fun processTopReached() {
        // Simulates reaching top of range for stop-at-top logic
    }
    
    fun shouldStopAtTop(): Boolean {
        return params?.stopAtTop == true &&
               params?.isJustLift == false &&
               (params?.reps ?: 0) > 0 &&
               repCount.workingReps == (params?.reps ?: 0) - 1
    }
    
    fun isWorkoutComplete(): Boolean {
        return if (params?.isJustLift == true) {
            false // Just Lift never completes
        } else {
            repCount.workingReps >= (params?.reps ?: 0)
        }
    }
    
    fun getRepCount() = repCount
    
    fun getCurrentWeight() = currentRepWeight
    
    private fun calculateDelta(last: Int, current: Int): Int {
        return if (current >= last) {
            current - last
        } else {
            // Wraparound
            0xFFFF - last + current + 1
        }
    }
}
