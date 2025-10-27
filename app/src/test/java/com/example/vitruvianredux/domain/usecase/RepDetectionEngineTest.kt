package com.example.vitruvianredux.domain.usecase

import com.example.vitruvianredux.domain.model.RepType
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*

class RepDetectionEngineTest {
    
    private lateinit var engine: RepDetectionEngine
    
    @Before
    fun setup() {
        engine = RepDetectionEngine()
        engine.setTargetReps(5) // 5 working reps for tests
    }
    
    /**
     * Simulate a full rep cycle: bottom -> top -> bottom
     * This mimics what happens when you pull the handles
     */
    private fun simulateRep(bottomPos: Int, topPos: Int, startTick: Int): List<Any?> {
        val events = mutableListOf<Any?>()
        val steps = 20 // Steps per rep
        
        println("  Starting position: $bottomPos")
        println("  Target position: $topPos")
        
        // Going up from bottom to top
        for (i in 0..steps) {
            val pos = bottomPos + ((topPos - bottomPos) * i / steps)
            val tick = startTick + i
            if (i == 0 || i == steps / 2 || i == steps) {
                println("  [Up $i/$steps] pos=$pos")
            }
            events.add(engine.processPosition(pos, pos, tick))
        }
        
        // Going down from top to bottom
        for (i in 0..steps) {
            val pos = topPos - ((topPos - bottomPos) * i / steps)
            val tick = startTick + steps + i
            if (i == 0 || i == steps / 2 || i == steps) {
                println("  [Down $i/$steps] pos=$pos")
            }
            events.add(engine.processPosition(pos, pos, tick))
        }
        
        return events
    }
    
    @Test
    fun `test three warmup reps calibrate correctly`() {
        var tick = 0
        
        // Warmup Rep 1: 200-350
        println("=== WARMUP REP 1 ===")
        val rep1Events = simulateRep(200, 350, tick)
        println("Rep 1 events: ${rep1Events.filterNotNull().size} non-null events")
        rep1Events.filterNotNull().forEach { println("  Event: $it") }
        tick += 50
        val warmup1 = rep1Events.filterNotNull().firstOrNull()
        assertNotNull("Should detect warmup rep 1", warmup1)
        assertEquals(RepType.WARMUP_COMPLETED, (warmup1 as? com.example.vitruvianredux.domain.model.RepEvent)?.type)
        
        // Warmup Rep 2: 180-380 (slightly different)
        println("=== WARMUP REP 2 ===")
        val rep2Events = simulateRep(180, 380, tick)
        tick += 50
        val warmup2 = rep2Events.filterNotNull().firstOrNull()
        assertNotNull("Should detect warmup rep 2", warmup2)
        assertEquals(RepType.WARMUP_COMPLETED, (warmup2 as? com.example.vitruvianredux.domain.model.RepEvent)?.type)
        
        // Warmup Rep 3: 190-370
        println("=== WARMUP REP 3 ===")
        val rep3Events = simulateRep(190, 370, tick)
        tick += 50
        val warmup3 = rep3Events.filterNotNull().firstOrNull()
        assertNotNull("Should detect warmup rep 3", warmup3)
        assertEquals(RepType.WARMUP_COMPLETED, (warmup3 as? com.example.vitruvianredux.domain.model.RepEvent)?.type)
        
        // Working Rep 1: Should use averaged range ~(190-367)
        println("=== WORKING REP 1 (should use locked calibration) ===")
        val work1Events = simulateRep(190, 365, tick)
        tick += 50
        val working1 = work1Events.filterNotNull().firstOrNull()
        assertNotNull("Should detect working rep 1", working1)
        assertEquals(RepType.WORKING_COMPLETED, (working1 as? com.example.vitruvianredux.domain.model.RepEvent)?.type)
        
        // Working Rep 2: Use same range
        println("=== WORKING REP 2 ===")
        val work2Events = simulateRep(190, 365, tick)
        val working2 = work2Events.filterNotNull().firstOrNull()
        assertNotNull("Should detect working rep 2", working2)
        assertEquals(RepType.WORKING_COMPLETED, (working2 as? com.example.vitruvianredux.domain.model.RepEvent)?.type)
    }
    
    @Test
    fun `test warmup reps count correctly`() {
        var tick = 0
        var warmupCount = 0
        
        // Simulate 3 warmup reps
        for (repNum in 1..3) {
            println("=== WARMUP REP $repNum ===")
            val events = simulateRep(200, 350, tick)
            tick += 50
            
            val repEvent = events.filterNotNull().firstOrNull() as? com.example.vitruvianredux.domain.model.RepEvent
            assertNotNull("Should detect warmup rep $repNum", repEvent)
            assertEquals("Rep $repNum should be warmup", RepType.WARMUP_COMPLETED, repEvent?.type)
            
            warmupCount++
            assertEquals("Warmup count should be $warmupCount", warmupCount, repEvent?.warmupCount)
        }
    }
    
    @Test
    fun `test working reps count after warmup`() {
        var tick = 0
        
        // Do 3 warmup reps
        for (i in 1..3) {
            simulateRep(200, 350, tick)
            tick += 50
        }
        
        // Now do working reps
        var workingCount = 0
        for (repNum in 1..5) {
            println("=== WORKING REP $repNum ===")
            val events = simulateRep(200, 350, tick)
            tick += 50
            
            val repEvent = events.filterNotNull().firstOrNull() as? com.example.vitruvianredux.domain.model.RepEvent
            assertNotNull("Should detect working rep $repNum", repEvent)
            assertEquals("Rep $repNum should be working", RepType.WORKING_COMPLETED, repEvent?.type)
            
            workingCount++
            assertEquals("Working count should be $workingCount", workingCount, repEvent?.workingCount)
        }
    }
}
