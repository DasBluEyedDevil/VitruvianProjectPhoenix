package com.example.vitruvianredux.domain.usecase

import com.example.vitruvianredux.domain.model.RepEvent
import com.example.vitruvianredux.domain.model.RepType
import timber.log.Timber

/**
 * Rep detection engine ported from web app app
 * Tracks position data and detects rep transitions using rolling windows
 */
class RepDetectionEngine {
    
    // Rolling windows for position tracking
    private val topPositionsA = mutableListOf<Int>()
    private val bottomPositionsA = mutableListOf<Int>()
    private val topPositionsB = mutableListOf<Int>()
    private val bottomPositionsB = mutableListOf<Int>()
    
    // Discovered rep ranges (rolling averages)
    private var minRepPosA: Int? = null
    private var maxRepPosA: Int? = null
    private var minRepPosB: Int? = null
    private var maxRepPosB: Int? = null
    
    // Range uncertainty (for visualization)
    private var minRepPosARange: Pair<Int, Int>? = null
    private var maxRepPosARange: Pair<Int, Int>? = null
    private var minRepPosBRange: Pair<Int, Int>? = null
    private var maxRepPosBRange: Pair<Int, Int>? = null
    
    // Rep counters
    private var warmupReps = 0
    private var workingReps = 0
    private var warmupTarget = 3
    private var targetReps = 0
    
    // State tracking
    private var lastTopCounter: Int? = null
    private var isAtTop = false
    private var isAtBottom = true
    
    // Window size for rolling averages
    private val windowSize = 10
    
    // Position threshold for detecting movement
    private val positionChangeThreshold = 50
    
    /**
     * Process new position data and detect rep events
     * @param posA Cable A position (0-1000)
     * @param posB Cable B position (0-1000)
     * @param ticks Tick counter
     * @return RepEvent if a rep transition occurred, null otherwise
     */
    fun processPosition(posA: Int, posB: Int, ticks: Int): RepEvent? {
        // Update rolling windows
        updatePositionWindows(posA, posB)
        
        // Detect rep transitions
        return detectRepTransition(posA, posB, ticks)
    }
    
    private fun updatePositionWindows(posA: Int, posB: Int) {
        // Cable A
        if (posA > (maxRepPosA ?: 0)) {
            topPositionsA.add(posA)
            if (topPositionsA.size > windowSize) {
                topPositionsA.removeAt(0)
            }
            maxRepPosA = topPositionsA.average().toInt()
            maxRepPosARange = Pair(topPositionsA.minOrNull() ?: 0, topPositionsA.maxOrNull() ?: 0)
        }
        
        if (minRepPosA == null || posA < minRepPosA!!) {
            bottomPositionsA.add(posA)
            if (bottomPositionsA.size > windowSize) {
                bottomPositionsA.removeAt(0)
            }
            minRepPosA = bottomPositionsA.average().toInt()
            minRepPosARange = Pair(bottomPositionsA.minOrNull() ?: 0, bottomPositionsA.maxOrNull() ?: 0)
        }
        
        // Cable B
        if (posB > (maxRepPosB ?: 0)) {
            topPositionsB.add(posB)
            if (topPositionsB.size > windowSize) {
                topPositionsB.removeAt(0)
            }
            maxRepPosB = topPositionsB.average().toInt()
            maxRepPosBRange = Pair(topPositionsB.minOrNull() ?: 0, topPositionsB.maxOrNull() ?: 0)
        }
        
        if (minRepPosB == null || posB < minRepPosB!!) {
            bottomPositionsB.add(posB)
            if (bottomPositionsB.size > windowSize) {
                bottomPositionsB.removeAt(0)
            }
            minRepPosB = bottomPositionsB.average().toInt()
            minRepPosBRange = Pair(bottomPositionsB.minOrNull() ?: 0, bottomPositionsB.maxOrNull() ?: 0)
        }
    }
    
    private fun detectRepTransition(posA: Int, posB: Int, @Suppress("UNUSED_PARAMETER") ticks: Int): RepEvent? {
        val avgPos = (posA + posB) / 2
        val maxPos = maxRepPosA ?: 800
        val minPos = minRepPosA ?: 200
        
        // Detect at top position
        val atTop = avgPos >= (maxPos - positionChangeThreshold)
        
        // Detect at bottom position
        val atBottom = avgPos <= (minPos + positionChangeThreshold)
        
        // Detect transition from bottom to top (rep start)
        if (atTop && !isAtTop && isAtBottom) {
            isAtTop = true
            isAtBottom = false
            
            // Count as warmup or working rep
            return if (warmupReps < warmupTarget) {
                warmupReps++
                Timber.d("Warmup rep completed: $warmupReps/$warmupTarget")
                RepEvent(
                    type = RepType.WARMUP_COMPLETED,
                    warmupCount = warmupReps,
                    workingCount = workingReps,
                    timestamp = System.currentTimeMillis()
                )
            } else {
                workingReps++
                Timber.d("Working rep completed: $workingReps/$targetReps")
                RepEvent(
                    type = RepType.WORKING_COMPLETED,
                    warmupCount = warmupReps,
                    workingCount = workingReps,
                    timestamp = System.currentTimeMillis()
                )
            }
        }
        
        // Detect transition from top to bottom
        if (atBottom && !isAtBottom && isAtTop) {
            isAtBottom = true
            isAtTop = false
        }
        
        return null
    }
    
    /**
     * Check if target reps have been reached (for auto-stop in Just Lift mode)
     */
    fun shouldAutoStop(isJustLift: Boolean): Boolean {
        if (!isJustLift) return false
        return workingReps >= targetReps
    }
    
    /**
     * Set warmup target
     */
    fun setWarmupTarget(target: Int) {
        warmupTarget = target
    }
    
    /**
     * Set target working reps
     */
    fun setTargetReps(target: Int) {
        targetReps = target
    }
    
    /**
     * Get current rep counts
     */
    fun getRepCounts(): Pair<Int, Int> {
        return Pair(warmupReps, workingReps)
    }
    
    /**
     * Get rep ranges for visualization
     */
    fun getRepRanges(): RepRanges {
        return RepRanges(
            minPosA = minRepPosA ?: 0,
            maxPosA = maxRepPosA ?: 1000,
            minPosB = minRepPosB ?: 0,
            maxPosB = maxRepPosB ?: 1000,
            minRangeA = minRepPosARange,
            maxRangeA = maxRepPosARange,
            minRangeB = minRepPosBRange,
            maxRangeB = maxRepPosBRange
        )
    }
    
    /**
     * Reset all rep tracking
     */
    fun reset() {
        topPositionsA.clear()
        bottomPositionsA.clear()
        topPositionsB.clear()
        bottomPositionsB.clear()
        
        minRepPosA = null
        maxRepPosA = null
        minRepPosB = null
        maxRepPosB = null
        
        minRepPosARange = null
        maxRepPosARange = null
        minRepPosBRange = null
        maxRepPosBRange = null
        
        warmupReps = 0
        workingReps = 0
        
        lastTopCounter = null
        isAtTop = false
        isAtBottom = true
        
        Timber.d("Rep detection engine reset")
    }
}

/**
 * Rep ranges data class for visualization
 */
data class RepRanges(
    val minPosA: Int,
    val maxPosA: Int,
    val minPosB: Int,
    val maxPosB: Int,
    val minRangeA: Pair<Int, Int>?,
    val maxRangeA: Pair<Int, Int>?,
    val minRangeB: Pair<Int, Int>?,
    val maxRangeB: Pair<Int, Int>?
)
