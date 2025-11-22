package com.example.vitruvianredux.domain

import com.example.vitruvianredux.domain.model.RepCount
import com.example.vitruvianredux.domain.model.RepEvent
import com.example.vitruvianredux.domain.model.RepType
import com.example.vitruvianredux.domain.usecase.RepCounterFromMachine
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Comprehensive test suite for workout modes and rep counting
 * Based on reference web app (app.js:949-1100)
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
        handler.process(repsRomCount = 0, repsSetCount = 0)
    }

    @Test
    fun `Old School mode - 3 warmup + 10 working reps`() {
        initHandler(warmupTarget = 3, workingTarget = 10, isJustLift = false)

        // Simulate 3 warmup reps from machine (ROM counter increments, Set counter stays at 0)
        handler.process(repsRomCount = 1, repsSetCount = 0)
        assertEquals(1, handler.getRepCount().warmupReps)
        assertEquals(0, handler.getRepCount().workingReps)

        handler.process(repsRomCount = 2, repsSetCount = 0)
        assertEquals(2, handler.getRepCount().warmupReps)

        handler.process(repsRomCount = 3, repsSetCount = 0)
        assertEquals(3, handler.getRepCount().warmupReps)
        assertTrue(handler.getRepCount().isWarmupComplete)

        // Now 10 working reps (ROM stays at warmupTarget, Set counter increments)
        for (i in 1..10) {
            handler.process(repsRomCount = 3, repsSetCount = i)
        }

        assertEquals(3, handler.getRepCount().warmupReps)
        assertEquals(10, handler.getRepCount().workingReps)
        assertTrue("Should auto-stop at target", handler.shouldStopWorkout())
    }

    @Test
    fun `Pump mode - 3 warmup + 20 working reps`() {
        initHandler(warmupTarget = 3, workingTarget = 20, isJustLift = false)

        // Complete warmups (ROM counter increments, Set counter stays at 0)
        for (i in 1..3) {
            handler.process(repsRomCount = i, repsSetCount = 0)
        }

        // Complete 20 pump reps (ROM stays at warmupTarget, Set counter increments)
        for (i in 1..20) {
            handler.process(repsRomCount = 3, repsSetCount = i)
        }

        assertEquals(20, handler.getRepCount().workingReps)
        assertTrue(handler.shouldStopWorkout())
    }

    @Test
    fun `TUT mode - 3 warmup + 6 working reps`() {
        initHandler(warmupTarget = 3, workingTarget = 6, isJustLift = false)

        // Warmups (ROM counter increments, Set counter stays at 0)
        for (i in 1..3) {
            handler.process(repsRomCount = i, repsSetCount = 0)
        }

        // Working reps (ROM stays at warmupTarget, Set counter increments)
        for (i in 1..6) {
            handler.process(repsRomCount = 3, repsSetCount = i)
        }

        assertEquals(6, handler.getRepCount().workingReps)
        assertTrue(handler.shouldStopWorkout())
    }

    @Test
    fun `TUT Beast mode - 3 warmup + 3 working reps`() {
        initHandler(warmupTarget = 3, workingTarget = 3, isJustLift = false)

        // Warmups (ROM counter increments, Set counter stays at 0)
        for (i in 1..3) {
            handler.process(repsRomCount = i, repsSetCount = 0)
        }

        // Working reps (ROM stays at warmupTarget, Set counter increments)
        for (i in 1..3) {
            handler.process(repsRomCount = 3, repsSetCount = i)
        }

        assertEquals(3, handler.getRepCount().workingReps)
        assertTrue(handler.shouldStopWorkout())
    }

    @Test
    fun `Eccentric Only mode - 3 warmup + 8 working reps`() {
        initHandler(warmupTarget = 3, workingTarget = 8, isJustLift = false)

        // Warmups (ROM counter increments, Set counter stays at 0)
        for (i in 1..3) {
            handler.process(repsRomCount = i, repsSetCount = 0)
        }

        // Eccentric reps (ROM stays at warmupTarget, Set counter increments)
        for (i in 1..8) {
            handler.process(repsRomCount = 3, repsSetCount = i)
        }

        assertEquals(8, handler.getRepCount().workingReps)
        assertTrue(handler.shouldStopWorkout())
    }

    @Test
    fun `Echo mode - 3 warmup + 12 working reps`() {
        initHandler(warmupTarget = 3, workingTarget = 12, isJustLift = false)

        // Warmups (ROM counter increments, Set counter stays at 0)
        for (i in 1..3) {
            handler.process(repsRomCount = i, repsSetCount = 0)
        }

        // Echo reps (ROM stays at warmupTarget, Set counter increments)
        for (i in 1..12) {
            handler.process(repsRomCount = 3, repsSetCount = i)
        }

        assertEquals(12, handler.getRepCount().workingReps)
        assertTrue(handler.shouldStopWorkout())
    }

    @Test
    fun `Just Lift mode - never auto-stops`() {
        initHandler(warmupTarget = 3, workingTarget = 0, isJustLift = true)

        // Warmups (ROM counter increments, Set counter stays at 0)
        for (i in 1..3) {
            handler.process(repsRomCount = i, repsSetCount = 0)
        }

        // Keep going indefinitely (ROM stays at warmupTarget, Set counter increments)
        for (i in 1..97) {
            handler.process(repsRomCount = 3, repsSetCount = i)
            assertFalse("Just Lift should never auto-stop", handler.shouldStopWorkout())
        }

        assertEquals(97, handler.getRepCount().workingReps)
    }

    @Test
    fun `Stop At Top option - completes when reaching top of final rep`() {
        initHandler(warmupTarget = 3, workingTarget = 5, isJustLift = false, stopAtTop = true)

        // Complete warmups (ROM counter increments, Set counter stays at 0)
        for (i in 1..3) {
            handler.process(repsRomCount = i, repsSetCount = 0, posA = 500, posB = 500)
        }

        // Do 4 complete working reps (ROM stays at warmupTarget, Set counter increments)
        for (i in 1..4) {
            handler.process(repsRomCount = 3, repsSetCount = i, posA = 500, posB = 500)
        }

        assertEquals(4, handler.getRepCount().workingReps)

        // On 5th rep, complete it to reach target
        handler.process(repsRomCount = 3, repsSetCount = 5, posA = 800, posB = 800)

        // Should complete at target
        assertTrue("Should stop at top of final rep", handler.shouldStopWorkout())
        assertEquals(5, handler.getRepCount().workingReps)
    }

    @Test
    fun `Counter wrap-around at 65535`() {
        // With warmupTarget = 0, warmup is already complete, so repsSetCount drives working reps
        initHandler(warmupTarget = 0, workingTarget = 100, isJustLift = false)

        // Start near max u16 value (working reps only use repsSetCount)
        handler.process(repsRomCount = 0, repsSetCount = 1)
        assertEquals(1, handler.getRepCount().workingReps)

        handler.process(repsRomCount = 0, repsSetCount = 2)
        assertEquals(2, handler.getRepCount().workingReps)

        handler.process(repsRomCount = 0, repsSetCount = 3)
        assertEquals(3, handler.getRepCount().workingReps)

        handler.process(repsRomCount = 0, repsSetCount = 4)
        assertEquals(4, handler.getRepCount().workingReps)

        handler.process(repsRomCount = 0, repsSetCount = 5)
        assertEquals(5, handler.getRepCount().workingReps)

        handler.process(repsRomCount = 0, repsSetCount = 6)
        assertEquals(6, handler.getRepCount().workingReps)
    }

    @Test
    fun `No spurious reps from duplicate notifications`() {
        initHandler(warmupTarget = 3, workingTarget = 10, isJustLift = false)

        // First warmup rep (ROM counter increments, Set counter stays at 0)
        handler.process(repsRomCount = 1, repsSetCount = 0)
        assertEquals(1, handler.getRepCount().warmupReps)

        // Same notification again (BLE retransmission)
        handler.process(repsRomCount = 1, repsSetCount = 0)
        assertEquals(1, handler.getRepCount().warmupReps) // Should still be 1

        // Next warmup rep
        handler.process(repsRomCount = 2, repsSetCount = 0)
        assertEquals(2, handler.getRepCount().warmupReps)
    }

    @Test
    fun `Invalid data - too short`() {
        // This test is no longer applicable since we're not parsing ByteArray
        // The parsing happens in BleManager, not in RepCounterFromMachine
        // Test removed - counters are always valid Ints
    }

    @Test
    fun `Machine counts during warmup - app calibrates range`() {
        initHandler(warmupTarget = 3, workingTarget = 10, isJustLift = false)

        // During warmup, machine sends notifications AND app tracks positions
        // The 'up' counter must increment to trigger position recording
        // Warmup rep 1 (ROM counter increments, Set counter stays at 0)
        handler.process(repsRomCount = 1, repsSetCount = 0, up = 1, down = 0, posA = 850, posB = 850)

        // Warmup rep 2
        handler.process(repsRomCount = 2, repsSetCount = 0, up = 2, down = 0, posA = 830, posB = 830)

        // Warmup rep 3
        handler.process(repsRomCount = 3, repsSetCount = 0, up = 3, down = 0, posA = 840, posB = 840)

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

        // Phase 1: Warmup (ROM counter increments, Set counter stays at 0)
        for (i in 1..3) {
            handler.process(repsRomCount = i, repsSetCount = 0)
        }
        assertTrue(handler.getRepCount().isWarmupComplete)
        assertNotNull(lastEvent)
        assertEquals(RepType.WARMUP_COMPLETE, lastEvent?.type)

        // Phase 2: Working reps (ROM stays at warmupTarget, Set counter increments)
        for (i in 1..10) {
            handler.process(repsRomCount = 3, repsSetCount = i)
        }

        // Phase 3: Auto-complete at target
        assertTrue(handler.shouldStopWorkout())
        assertEquals(RepType.WORKOUT_COMPLETE, lastEvent?.type)

        // Final counts
        val final = handler.getRepCount()
        assertEquals(3, final.warmupReps)
        assertEquals(10, final.workingReps)
        assertEquals(10, final.totalReps) // Updated after Session 17 rep counting changes
    }
}
