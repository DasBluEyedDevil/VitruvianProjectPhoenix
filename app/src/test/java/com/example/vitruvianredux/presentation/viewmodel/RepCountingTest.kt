package com.example.vitruvianredux.presentation.viewmodel

import com.example.vitruvianredux.data.ble.RepNotification
import org.junit.Test
import org.junit.Assert.*

/**
 * Unit tests for rep counting using hardware counters
 * Tests the logic that processes machine's own rep counters from notifications
 */
class RepCountingTest {
    
    /**
     * Test counter delta calculation with normal increment
     */
    @Test
    fun testCounterDelta_normalIncrement() {
        val last = 5
        val current = 8
        val delta = calculateCounterDelta(last, current)
        assertEquals(3, delta)
    }
    
    /**
     * Test counter delta calculation with wraparound
     */
    @Test
    fun testCounterDelta_wraparound() {
        val last = 0xFFFE // 65534
        val current = 3
        val delta = calculateCounterDelta(last, current)
        assertEquals(5, delta) // 65534 -> 65535 -> 0 -> 1 -> 2 -> 3 = 5 increments
    }
    
    /**
     * Test counter delta when counters are equal (no change)
     */
    @Test
    fun testCounterDelta_noChange() {
        val last = 100
        val current = 100
        val delta = calculateCounterDelta(last, current)
        assertEquals(0, delta)
    }
    
    /**
     * Simulate a full workout with 3 warmup reps + 5 working reps
     * This tests the complete workflow that should match what the machine sends
     */
    @Test
    fun testFullWorkout_3warmup_5working() {
        var warmupReps = 0
        var workingReps = 0
        val warmupTarget = 3
        val workingTarget = 5
        
        var lastTopCounter: Int? = null
        var lastRepCounter: Int? = null
        
        // Simulate machine notifications for 8 total reps (3 warmup + 5 working)
        // NOTE: First notification establishes baseline, so we need 9 notifications for 8 counted reps
        val notifications = listOf(
            // Baseline (not counted as a rep)
            RepNotification(topCounter = 0, completeCounter = 0, rawData = byteArrayOf(), timestamp = 0L),
            // Warmup rep 1
            RepNotification(topCounter = 1, completeCounter = 1, rawData = byteArrayOf(), timestamp = 0L),
            // Warmup rep 2  
            RepNotification(topCounter = 2, completeCounter = 2, rawData = byteArrayOf(), timestamp = 0L),
            // Warmup rep 3
            RepNotification(topCounter = 3, completeCounter = 3, rawData = byteArrayOf(), timestamp = 0L),
            // Working rep 1
            RepNotification(topCounter = 4, completeCounter = 4, rawData = byteArrayOf(), timestamp = 0L),
            // Working rep 2
            RepNotification(topCounter = 5, completeCounter = 5, rawData = byteArrayOf(), timestamp = 0L),
            // Working rep 3
            RepNotification(topCounter = 6, completeCounter = 6, rawData = byteArrayOf(), timestamp = 0L),
            // Working rep 4
            RepNotification(topCounter = 7, completeCounter = 7, rawData = byteArrayOf(), timestamp = 0L),
            // Working rep 5
            RepNotification(topCounter = 8, completeCounter = 8, rawData = byteArrayOf(), timestamp = 0L),
        )
        
        for (notification in notifications) {
            val topCounter = notification.topCounter
            val completeCounter = notification.completeCounter
            
            // Track top of range
            if (lastTopCounter == null) {
                lastTopCounter = topCounter
            } else {
                val topDelta = calculateCounterDelta(lastTopCounter, topCounter)
                if (topDelta > 0) {
                    lastTopCounter = topCounter
                }
            }
            
            // Track rep complete
            if (lastRepCounter == null) {
                lastRepCounter = completeCounter
                continue
            }
            
            val delta = calculateCounterDelta(lastRepCounter, completeCounter)
            if (delta > 0) {
                lastRepCounter = completeCounter
                
                // Increment counters
                val totalReps = warmupReps + workingReps + 1
                
                if (totalReps <= warmupTarget) {
                    // Still in warmup
                    warmupReps++
                    println("Warmup rep $warmupReps/$warmupTarget complete")
                } else {
                    // Working reps
                    workingReps++
                    println("Working rep $workingReps/$workingTarget complete")
                }
            }
        }
        
        // Verify counts
        assertEquals("Should have completed all warmup reps", warmupTarget, warmupReps)
        assertEquals("Should have completed all working reps", workingTarget, workingReps)
    }
    
    /**
     * Test that we correctly identify when to stop at top of final rep
     */
    @Test
    fun testStopAtTopOfFinalRep() {
        var warmupReps = 3 // Already completed warmups
        var workingReps = 4 // Completed 4 working reps
        val workingTarget = 5
        val stopAtTop = true
        
        var lastTopCounter = 10
        
        // Simulate reaching top of rep 5
        val topNotification = RepNotification(topCounter = 11, completeCounter = 10, rawData = byteArrayOf(), timestamp = 0L)
        
        val topDelta = calculateCounterDelta(lastTopCounter, topNotification.topCounter)
        
        assertTrue("Top counter should have incremented", topDelta > 0)
        
        // Check if we should stop
        val shouldStop = stopAtTop && workingReps == workingTarget - 1
        
        assertTrue("Should stop at top of final rep", shouldStop)
    }
    
    /**
     * Helper function matching ViewModel's counter delta calculation
     */
    private fun calculateCounterDelta(last: Int, current: Int): Int {
        return if (current >= last) {
            current - last
        } else {
            // Wraparound
            0xFFFF - last + current + 1
        }
    }
}
