package com.example.vitruvianredux.domain

import com.example.vitruvianredux.domain.model.RepCount
import com.example.vitruvianredux.domain.model.RepEvent
import com.example.vitruvianredux.domain.model.RepType
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Comprehensive test suite for workout modes and rep counting
 * Based on reference web app (app:949-1100)
 * 
 * The Vitruvian machine sends rep notifications via characteristic:
 * UUID: 8308f2a6-0875-4a94-a86f-5c5c5e1b068a
 * 
 * Format: Array of u16 values (little-endian)
 * - u16[0] = top counter (increments when reaching top of movement)
 * - u16[1] = unknown  
 * - u16[2] = complete counter (increments when rep is complete at bottom)
 *
 * KEY INSIGHT: The machine itself counts the reps! We just track the counters.
 */
class WorkoutModeTest {

    private lateinit var handler: RepCounterFromMachine
    
    @Before
    fun setup() {
        handler = RepCounterFromMachine()
    }
    
    /**
     * Helper to initialize handler and set baseline counter
     */
    private fun initHandler(warmupTarget: Int = 3, workingTarget: Int = 10, isJustLift: Boolean = false, stopAtTop: Boolean = false) {
        handler.configure(warmupTarget, workingTarget, isJustLift, stopAtTop)
        // Send initial notification to establish baseline
        handler.process(createNotification(0, 0))
    }

    /**
     * Helper to create rep notification matching hardware format
     */
    private fun createNotification(topCounter: Int, completeCounter: Int): ByteArray {
        return ByteBuffer.allocate(6).apply {
            order(ByteOrder.LITTLE_ENDIAN)
            putShort(topCounter.toShort())
            putShort(0) // u16[1] - unknown
            putShort(completeCounter.toShort())
        }.array()
    }

    @Test
    fun `Old School mode - 3 warmup + 10 working reps`() {
        initHandler(warmupTarget = 3, workingTarget = 10, isJustLift = false)
        
        // Simulate 3 warmup reps from machine
        handler.process(createNotification(topCounter = 1, completeCounter = 1))
        assertEquals(1, handler.getRepCount().warmupReps)
        assertEquals(0, handler.getRepCount().workingReps)
        
        handler.process(createNotification(topCounter = 2, completeCounter = 2))
        assertEquals(2, handler.getRepCount().warmupReps)
        
        handler.process(createNotification(topCounter = 3, completeCounter = 3))
        assertEquals(3, handler.getRepCount().warmupReps)
        assertTrue(handler.getRepCount().isWarmupComplete)
        
        // Now 10 working reps
        for (i in 4..13) {
            handler.process(createNotification(topCounter = i, completeCounter = i))
        }
        
        assertEquals(3, handler.getRepCount().warmupReps)
        assertEquals(10, handler.getRepCount().workingReps)
        assertTrue("Should auto-stop at target", handler.shouldStopWorkout())
    }

    @Test
    fun `Pump mode - 3 warmup + 20 working reps`() {
        initHandler(warmupTarget = 3, workingTarget = 20, isJustLift = false)
        
        // Complete warmups
        for (i in 1..3) {
            handler.process(createNotification(i, i))
        }
        
        // Complete 20 pump reps
        for (i in 4..23) {
            handler.process(createNotification(i, i))
        }
        
        assertEquals(20, handler.getRepCount().workingReps)
        assertTrue(handler.shouldStopWorkout())
    }

    @Test
    fun `TUT mode - 3 warmup + 6 working reps`() {
        initHandler(warmupTarget = 3, workingTarget = 6, isJustLift = false)
        
        // Warmups
        for (i in 1..3) {
            handler.process(createNotification(i, i))
        }
        
        // Working reps (TUT typically has fewer reps but longer time under tension)
        for (i in 4..9) {
            handler.process(createNotification(i, i))
        }
        
        assertEquals(6, handler.getRepCount().workingReps)
        assertTrue(handler.shouldStopWorkout())
    }

    @Test
    fun `TUT Beast mode - 3 warmup + 3 working reps`() {
        initHandler(warmupTarget = 3, workingTarget = 3, isJustLift = false)
        
        // Warmups
        for (i in 1..3) {
            handler.process(createNotification(i, i))
        }
        
        // Working reps (Beast mode is very low reps with maximum intensity)
        for (i in 4..6) {
            handler.process(createNotification(i, i))
        }
        
        assertEquals(3, handler.getRepCount().workingReps)
        assertTrue(handler.shouldStopWorkout())
    }

    @Test
    fun `Eccentric Only mode - 3 warmup + 8 working reps`() {
        initHandler(warmupTarget = 3, workingTarget = 8, isJustLift = false)
        
        // Warmups
        for (i in 1..3) {
            handler.process(createNotification(i, i))
        }
        
        // Eccentric reps
        for (i in 4..11) {
            handler.process(createNotification(i, i))
        }
        
        assertEquals(8, handler.getRepCount().workingReps)
        assertTrue(handler.shouldStopWorkout())
    }

    @Test
    fun `Echo mode - 3 warmup + 12 working reps`() {
        initHandler(warmupTarget = 3, workingTarget = 12, isJustLift = false)
        
        // Warmups
        for (i in 1..3) {
            handler.process(createNotification(i, i))
        }
        
        // Echo reps
        for (i in 4..15) {
            handler.process(createNotification(i, i))
        }
        
        assertEquals(12, handler.getRepCount().workingReps)
        assertTrue(handler.shouldStopWorkout())
    }

    @Test
    fun `Just Lift mode - never auto-stops`() {
        initHandler(warmupTarget = 3, workingTarget = 0, isJustLift = true)
        
        // Warmups
        for (i in 1..3) {
            handler.process(createNotification(i, i))
        }
        
        // Keep going indefinitely
        for (i in 4..100) {
            handler.process(createNotification(i, i))
            assertFalse("Just Lift should never auto-stop", handler.shouldStopWorkout())
        }
        
        assertEquals(97, handler.getRepCount().workingReps) // 100 - 3 warmup
    }

    @Test
    fun `Stop At Top option - completes when reaching top of final rep`() {
        initHandler(warmupTarget = 3, workingTarget = 5, isJustLift = false, stopAtTop = true)
        
        // Complete warmups (both top and bottom counters increment together)
        for (i in 1..3) {
            handler.process(createNotification(topCounter = i, completeCounter = i), posA = 500, posB = 500)
        }
        
        // Do 4 complete working reps
        for (i in 4..7) {
            handler.process(createNotification(topCounter = i, completeCounter = i), posA = 500, posB = 500)
        }
        
        assertEquals(4, handler.getRepCount().workingReps)
        
        // On 5th rep, top counter increments (reached top of movement)
        // but bottom counter hasn't incremented yet (not at bottom)
        handler.process(createNotification(topCounter = 8, completeCounter = 7), posA = 800, posB = 800)
        
        // Should complete at top, not bottom
        assertTrue("Should stop at top of final rep", handler.shouldStopWorkout())
        assertEquals(4, handler.getRepCount().workingReps) // Still 4 since bottom didn't increment
    }

    @Test
    fun `Counter wrap-around at 65535`() {
        initHandler(warmupTarget = 0, workingTarget = 100, isJustLift = false)
        
        // Start near max u16 value
        handler.process(createNotification(65533, 65533))
        assertEquals(1, handler.getRepCount().workingReps)
        
        handler.process(createNotification(65534, 65534))
        assertEquals(2, handler.getRepCount().workingReps)
        
        handler.process(createNotification(65535, 65535))
        assertEquals(3, handler.getRepCount().workingReps)
        
        // Wrap to 0
        handler.process(createNotification(0, 0))
        assertEquals(4, handler.getRepCount().workingReps)
        
        // Continue after wrap
        handler.process(createNotification(1, 1))
        assertEquals(5, handler.getRepCount().workingReps)
        
        handler.process(createNotification(2, 2))
        assertEquals(6, handler.getRepCount().workingReps)
    }

    @Test
    fun `No spurious reps from duplicate notifications`() {
        initHandler(warmupTarget = 3, workingTarget = 10, isJustLift = false)
        
        // First rep
        handler.process(createNotification(1, 1))
        assertEquals(1, handler.getRepCount().warmupReps)
        
        // Same notification again (BLE retransmission)
        handler.process(createNotification(1, 1))
        assertEquals(1, handler.getRepCount().warmupReps) // Should still be 1
        
        // Next rep
        handler.process(createNotification(2, 2))
        assertEquals(2, handler.getRepCount().warmupReps)
    }

    @Test
    fun `Invalid data - too short`() {
        val shortData = ByteArray(4) // Only 4 bytes instead of 6
        handler.process(shortData)
        
        // Should not crash and should not count a rep
        assertEquals(0, handler.getRepCount().warmupReps)
        assertEquals(0, handler.getRepCount().workingReps)
    }

    @Test
    fun `Machine counts during warmup - app calibrates range`() {
        initHandler(warmupTarget = 3, workingTarget = 10, isJustLift = false)
        
        // During warmup, machine sends notifications AND app tracks positions
        // Warmup rep 1
        handler.process(createNotification(1, 1), posA = 850, posB = 850)
        
        // Warmup rep 2
        handler.process(createNotification(2, 2), posA = 830, posB = 830)
        
        // Warmup rep 3
        handler.process(createNotification(3, 3), posA = 840, posB = 840)
        
        // Range should be calibrated from warmup (average ~840)
        val range = handler.getCalibratedTopPosition()
        assertNotNull(range)
        assertTrue("Top should be around 840", range!! in 830..850)
    }

    @Test
    fun `Countdown before workout start - no rep counting`() {
        // During countdown, position data flows but no rep notifications
        // This is a UI concern - handler only processes notifications from machine
        
        initHandler(warmupTarget = 3, workingTarget = 10, isJustLift = false)
        
        // No notifications (beyond init) = no reps counted  
        assertEquals(0, handler.getRepCount().warmupReps)
        assertEquals(0, handler.getRepCount().workingReps)
    }

    @Test
    fun `Complete workflow - Old School mode end-to-end`() {
        initHandler(warmupTarget = 3, workingTarget = 10, isJustLift = false)
        
        var lastEvent: RepEvent? = null
        handler.onRepEvent = { event -> lastEvent = event }
        
        // Phase 1: Warmup
        for (i in 1..3) {
            handler.process(createNotification(i, i))
        }
        assertTrue(handler.getRepCount().isWarmupComplete)
        assertNotNull(lastEvent)
        assertEquals(RepType.WARMUP_COMPLETE, lastEvent?.type)
        
        // Phase 2: Working reps
        for (i in 4..13) {
            handler.process(createNotification(i, i))
        }
        
        // Phase 3: Auto-complete at target
        assertTrue(handler.shouldStopWorkout())
        assertEquals(RepType.WORKOUT_COMPLETE, lastEvent?.type)
        
        // Final counts
        val final = handler.getRepCount()
        assertEquals(3, final.warmupReps)
        assertEquals(10, final.workingReps)
        assertEquals(13, final.totalReps)
    }
}

/**
 * Handles rep counting based on notifications from the Vitruvian machine
 * Ported from reference web app app:949-1100
 * 
 * KEY DIFFERENCE FROM OLD APPROACH:
 * - OLD: RepDetectionEngine tried to detect reps from position data
 * - NEW: Just track the counters the machine sends us!
 */
class RepCounterFromMachine {
    
    private var warmupReps = 0
    private var workingReps = 0
    private var warmupTarget = 3
    private var workingTarget = 0
    private var isJustLift = false
    private var stopAtTop = false
    private var shouldStop = false // Flag set when workout should stop
    
    private var lastTopCounter: Int? = null
    private var lastCompleteCounter: Int? = null
    
    // Position tracking for range calibration (used for UI, not rep detection)
    private val topPositionsA = mutableListOf<Int>()
    private val topPositionsB = mutableListOf<Int>()
    
    var onRepEvent: ((RepEvent) -> Unit)? = null
    
    fun configure(
        warmupTarget: Int,
        workingTarget: Int,
        isJustLift: Boolean,
        stopAtTop: Boolean = false
    ) {
        this.warmupTarget = warmupTarget
        this.workingTarget = workingTarget
        this.isJustLift = isJustLift
        this.stopAtTop = stopAtTop
    }
    
    fun reset() {
        warmupReps = 0
        workingReps = 0
        shouldStop = false
        lastTopCounter = null
        lastCompleteCounter = null
        topPositionsA.clear()
        topPositionsB.clear()
    }
    
    fun process(data: ByteArray, posA: Int = 0, posB: Int = 0) {
        if (data.size < 6) return
        
        val buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        val topCounter = buffer.getShort(0).toInt() and 0xFFFF
        val completeCounter = buffer.getShort(4).toInt() and 0xFFFF
        
        // Track top counter (for stopAtTop and range calibration)
        if (lastTopCounter != null) {
            val topDelta = calculateDelta(lastTopCounter!!, topCounter)
            if (topDelta > 0 && posA > 0) {
                // Record top position for range calibration
                topPositionsA.add(posA)
                topPositionsB.add(posB)
                val windowSize = if (warmupReps < warmupTarget) 2 else 3
                if (topPositionsA.size > windowSize) {
                    topPositionsA.removeAt(0)
                    topPositionsB.removeAt(0)
                }
                
                // Check for stop at top of final rep
                if (stopAtTop && !isJustLift && workingTarget > 0 && workingReps == workingTarget - 1) {
                    shouldStop = true
                    onRepEvent?.invoke(RepEvent(
                        type = RepType.WORKOUT_COMPLETE,
                        warmupCount = warmupReps,
                        workingCount = workingReps
                    ))
                }
            }
        }
        lastTopCounter = topCounter
        
        // Track complete counter (this is the actual rep count)
        if (lastCompleteCounter != null) {
            val delta = calculateDelta(lastCompleteCounter!!, completeCounter)
            if (delta > 0) {
                val totalReps = warmupReps + workingReps + 1
                
                if (totalReps <= warmupTarget) {
                    // Warmup phase
                    warmupReps++
                    onRepEvent?.invoke(RepEvent(
                        type = RepType.WARMUP_COMPLETED,
                        warmupCount = warmupReps,
                        workingCount = 0
                    ))
                    
                    if (warmupReps == warmupTarget) {
                        onRepEvent?.invoke(RepEvent(
                            type = RepType.WARMUP_COMPLETE,
                            warmupCount = warmupReps,
                            workingCount = 0
                        ))
                    }
                } else {
                    // Working reps phase
                    workingReps++
                    onRepEvent?.invoke(RepEvent(
                        type = RepType.WORKING_COMPLETED,
                        warmupCount = warmupReps,
                        workingCount = workingReps
                    ))
                    
                    // Auto-complete at target (unless Just Lift or stopAtTop)
                    if (!stopAtTop && !isJustLift && workingTarget > 0 && workingReps >= workingTarget) {
                        shouldStop = true
                        onRepEvent?.invoke(RepEvent(
                            type = RepType.WORKOUT_COMPLETE,
                            warmupCount = warmupReps,
                            workingCount = workingReps
                        ))
                    }
                }
            }
        }
        lastCompleteCounter = completeCounter
    }
    
    private fun calculateDelta(last: Int, current: Int): Int {
        return if (current >= last) {
            current - last
        } else {
            // Handle wrap-around at 65535
            0xFFFF - last + current + 1
        }
    }
    
    fun getRepCount() = RepCount(
        warmupReps = warmupReps,
        workingReps = workingReps,
        totalReps = warmupReps + workingReps,
        isWarmupComplete = warmupReps >= warmupTarget
    )
    
    fun shouldStopWorkout(): Boolean {
        return shouldStop
    }
    
    fun getCalibratedTopPosition(): Int? {
        return if (topPositionsA.isNotEmpty()) {
            topPositionsA.average().toInt()
        } else null
    }
}
